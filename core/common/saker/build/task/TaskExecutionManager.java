/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.task;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import saker.build.cache.BuildCacheAccessor;
import saker.build.cache.BuildCacheAccessor.CachedTaskFinishedDependency;
import saker.build.cache.BuildCacheAccessor.TaskCacheEntry;
import saker.build.cache.BuildCacheException;
import saker.build.cache.BuildDataCache.DataEntry.FieldEntry;
import saker.build.exception.FileMirroringUnavailableException;
import saker.build.exception.InvalidFileTypeException;
import saker.build.exception.InvalidPathFormatException;
import saker.build.exception.TaskIdentifierExceptionView;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.ProviderPathSakerDirectory;
import saker.build.file.ProviderPathSakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.SakerFileBase;
import saker.build.file.SecondaryStreamException;
import saker.build.file.content.ContentDatabase;
import saker.build.file.content.ContentDatabase.ContentHandle;
import saker.build.file.content.ContentDatabase.ContentUpdater;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.DirectoryContentDescriptor;
import saker.build.file.content.NonExistentContentDescriptor;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.ide.configuration.IDEConfiguration;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.runtime.execution.ExecutionContextImpl.StandardIOLock;
import saker.build.runtime.execution.ExecutionDirectoryContext;
import saker.build.runtime.execution.ExecutionProgressMonitor;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.runtime.execution.FileDataComputer;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.execution.SecretInputReader;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.scripting.ScriptInformationProvider;
import saker.build.scripting.ScriptPosition;
import saker.build.task.InnerTaskExecutionParameters.CoordinatorDuplicationPredicate;
import saker.build.task.TaskExecutionManager.ManagerTaskFutureImpl.FutureState;
import saker.build.task.TaskExecutionResult.CreatedTaskDependency;
import saker.build.task.TaskExecutionResult.FileDependencies;
import saker.build.task.TaskExecutionResult.ReportedTaskDependency;
import saker.build.task.TaskExecutionResult.TaskDependencies;
import saker.build.task.TaskExecutionResult.TaskDependencies.NullFileDependencyTag;
import saker.build.task.TaskInvocationManager.SelectionResult;
import saker.build.task.TaskInvocationManager.TaskInvocationResult;
import saker.build.task.cluster.TaskInvoker;
import saker.build.task.delta.BuildDelta;
import saker.build.task.delta.DeltaType;
import saker.build.task.delta.FileChangeDelta;
import saker.build.task.delta.impl.EnvironmentDependencyDeltaImpl;
import saker.build.task.delta.impl.ExecutionDependencyDeltaImpl;
import saker.build.task.delta.impl.FileChangeDeltaImpl;
import saker.build.task.delta.impl.NewTaskDeltaImpl;
import saker.build.task.delta.impl.OutputLoadFailedDeltaImpl;
import saker.build.task.delta.impl.TaskChangeDeltaImpl;
import saker.build.task.dependencies.CommonTaskOutputChangeDetector;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.task.event.TaskExecutionEvent;
import saker.build.task.event.TaskExecutionEventKind;
import saker.build.task.event.TaskIdTaskEvent;
import saker.build.task.exception.ExceptionAccessInternal;
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.task.exception.InnerTaskExecutionException;
import saker.build.task.exception.InvalidTaskInvocationConfigurationException;
import saker.build.task.exception.MultiTaskExecutionFailedException;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;
import saker.build.task.exception.TaskException;
import saker.build.task.exception.TaskExecutionDeadlockedException;
import saker.build.task.exception.TaskExecutionException;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.exception.TaskIdentifierConflictException;
import saker.build.task.exception.TaskResultWaitingFailedException;
import saker.build.task.exception.TaskResultWaitingInterruptedException;
import saker.build.task.exception.TaskStandardIOLockIllegalStateException;
import saker.build.task.exception.TaskThreadManipulationException;
import saker.build.task.identifier.BuildFileTargetTaskIdentifier;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ConcurrentAppendAccumulator;
import saker.build.thirdparty.saker.util.ConcurrentEntryMergeSorter;
import saker.build.thirdparty.saker.util.ConcurrentEntryMergeSorter.MatchingKeyPolicy;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.EntryAccumulator;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.PartitionedEntryAccumulatorArray;
import saker.build.thirdparty.saker.util.PeekableIterable;
import saker.build.thirdparty.saker.util.TransformingNavigableMap;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.function.ThrowingRunnable;
import saker.build.thirdparty.saker.util.function.TriConsumer;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.PriorityMultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.thread.BooleanLatch;
import saker.build.thirdparty.saker.util.thread.ParallelExecutionException;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ParallelRunner;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;
import saker.build.trace.InternalBuildTrace;
import saker.build.trace.InternalBuildTrace.InternalTaskBuildTrace;
import saker.build.util.exc.ExceptionView;
import testing.saker.build.flag.TestFlag;

public final class TaskExecutionManager {
	private static final TaskExecutionParameters DEFAULT_EXECUTION_PARAMETERS = new TaskExecutionParameters();
	private static final InnerTaskExecutionParameters DEFAULT_INNER_TASK_EXECUTION_PARAMETERS = new InnerTaskExecutionParameters();

	public static final char PRINTED_LINE_VARIABLES_MARKER_CHAR = '!';
	public static final String PRINTED_LINE_VARIABLES_MARKER_CHAR_STR = "!";
	public static final String PRINTED_LINE_VAR_LOG_TASK_SCRIPT_POSITION = "LOG_TASK_SCRIPT_POSITION";

	public static final ThreadLocal<TaskThreadInfo> THREADLOCAL_TASK_THREAD = new ThreadLocal<>();

	public static final class TaskThreadInfo {
		public final TaskContext taskContext;
		public final boolean innerTask;

		public TaskThreadInfo(TaskContext taskContext, boolean innerTask) {
			this.taskContext = taskContext;
			this.innerTask = innerTask;
		}

		@Override
		public String toString() {
			return "TaskThreadInfo[" + (taskContext != null ? "taskContext=" + taskContext + ", " : "") + "innerTask="
					+ innerTask + "]";
		}
	}

	public static class SpawnedResultTask {
		private TaskIdentifier taskIdentifier;
		private Map<TaskIdentifier, SpawnedResultTask> children = new ConcurrentHashMap<>();
		private Supplier<?> resultSupplier;

		public SpawnedResultTask(TaskIdentifier taskIdentifier) {
			this.taskIdentifier = taskIdentifier;
		}

		protected void addChild(TaskIdentifier taskid, SpawnedResultTask spawned) {
			SpawnedResultTask prev = children.putIfAbsent(taskid, spawned);
			if (prev != null && prev != spawned) {
				throw new AssertionError("Already present with different spawned.");
			}
		}

		public TaskIdentifier getTaskIdentifier() {
			return taskIdentifier;
		}

		public Map<TaskIdentifier, SpawnedResultTask> getChildren() {
			return children;
		}

		public Object getResult() throws TaskExecutionException {
			return resultSupplier.get();
		}

		public void executionFinished(TaskResultHolder<?> resultholder) {
			if (isTaskResultFailed(resultholder)) {
				Throwable exc = resultholder.getFailCauseException();
				List<? extends Throwable> abortexceptions = resultholder.getAbortExceptions();
				executionFailed(exc, abortexceptions);
			} else {
				this.resultSupplier = Functionals.valSupplier(resultholder.getOutput());
			}
		}

		public void executionFailed(Throwable exc, List<? extends Throwable> abortexceptions) {
			this.resultSupplier = () -> {
				throw createFailException(taskIdentifier, exc, abortexceptions);
			};
		}

		@Override
		public String toString() {
			return "SpawnedResultTask[" + (taskIdentifier != null ? "taskIdentifier=" + taskIdentifier : "") + "]";
		}
	}

	public static final class TaskResultCollectionImpl implements TaskResultCollection {
		private final ConcurrentHashMap<TaskIdentifier, SpawnedResultTask> spawnedTasks;
		private Set<IDEConfiguration> ideConfigurations;

		private TaskResultCollectionImpl(ConcurrentHashMap<TaskIdentifier, SpawnedResultTask> spawnedtasks,
				Set<IDEConfiguration> runIdeConfigurations) {
			this.spawnedTasks = spawnedtasks;
			this.ideConfigurations = runIdeConfigurations;
		}

		@Override
		public Object getTaskResult(TaskIdentifier taskid) throws TaskExecutionException, IllegalArgumentException {
			Objects.requireNonNull(taskid, "task identifier");
			SpawnedResultTask res = spawnedTasks.get(taskid);
			if (res == null) {
				throw new IllegalArgumentException("Task was not run with id: " + taskid);
			}
			return res.getResult();
		}

		@Override
		public Set<? extends TaskIdentifier> getTaskIds() {
			return ImmutableUtils.unmodifiableSet(spawnedTasks.keySet());
		}

		@Override
		public Collection<? extends IDEConfiguration> getIDEConfigurations() {
			return ideConfigurations;
		}

		public Map<TaskIdentifier, SpawnedResultTask> getSpawnedTasks() {
			return spawnedTasks;
		}
	}

	private static class FileDependencyCollector {
		protected ConcurrentEntryMergeSorter<SakerPath, ContentDescriptor> inputDependencies = new ConcurrentEntryMergeSorter<>();
		protected ConcurrentEntryMergeSorter<SakerPath, ContentDescriptor> outputDependencies = new ConcurrentEntryMergeSorter<>();
		protected Set<FileCollectionStrategy> additionDependencies = ConcurrentHashMap.newKeySet();

		protected ConcurrentSkipListMap<SakerPath, ContentDescriptor> singleReportedInputDependencies = new ConcurrentSkipListMap<>();
		protected ConcurrentSkipListMap<SakerPath, ContentDescriptor> singleReportedOutputDependencies = new ConcurrentSkipListMap<>();

		public FileDependencyCollector() {
		}
	}

	private static final class UserTaskResultDependencyHandle implements TaskResultDependencyHandle, Cloneable {
		private TaskDependencyFutureImpl<?> depFuture;

		UserTaskResultDependencyHandle(TaskDependencyFutureImpl<?> depfuture) {
			this.depFuture = depfuture;
		}

		@Override
		public TaskResultDependencyHandle clone() {
			try {
				UserTaskResultDependencyHandle result = (UserTaskResultDependencyHandle) super.clone();
				result.depFuture = depFuture.clone();
				return result;
			} catch (CloneNotSupportedException e) {
				throw new AssertionError(e);
			}
		}

		@Override
		public Object get() throws RuntimeException {
			return depFuture.get();
		}

		@Override
		public void setTaskOutputChangeDetector(TaskOutputChangeDetector outputchangedetector)
				throws IllegalStateException, NullPointerException {
			depFuture.setTaskOutputChangeDetector(outputchangedetector);
		}
	}

	protected static final class TaskContextRMIWrapper implements RMIWrapper {
		private TaskContext context;

		public TaskContextRMIWrapper() {
		}

		public TaskContextRMIWrapper(TaskContext context) {
			this.context = context;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			TaskContext context = this.context;
			InternalTaskContext internalcontext = (InternalTaskContext) context;

			out.writeRemoteObject(context);
			out.writeRemoteObject(context.getTaskUtilities());
			out.writeObject(internalcontext.internalGetBuildTrace());
			out.writeSerializedObject(context.getTaskId());

			out.writeObject(context.getTaskWorkingDirectory());
			out.writeObject(context.getTaskWorkingDirectoryPath());
			out.writeObject(context.getTaskBuildDirectory());
			out.writeObject(context.getTaskBuildDirectoryPath());
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			TaskContext context = (TaskContext) in.readObject();
			TaskExecutionUtilities utilities = (TaskExecutionUtilities) in.readObject();
			InternalTaskBuildTrace buildtrace = (InternalTaskBuildTrace) in.readObject();
			TaskIdentifier taskid = (TaskIdentifier) in.readObject();

			SakerDirectory workingdir = (SakerDirectory) in.readObject();
			SakerPath workingdirpath = (SakerPath) in.readObject();
			SakerDirectory builddir = (SakerDirectory) in.readObject();
			SakerPath builddirpath = (SakerPath) in.readObject();

			this.context = new InternalCachingForwardingTaskContext(context, utilities, buildtrace, taskid, workingdir,
					workingdirpath, builddir, builddirpath);
		}

		@Override
		public Object resolveWrapped() {
			return context;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}
	}

	private static final class InternalCachingForwardingTaskContext extends InternalForwardingTaskContext {

		private final InternalTaskBuildTrace buildTrace;
		private final TaskIdentifier taskId;
		private final SakerDirectory workingDir;
		private final SakerPath workingDirPath;
		private final SakerDirectory buildDir;
		private final SakerPath buildDirPath;

		public InternalCachingForwardingTaskContext(TaskContext taskContext, TaskExecutionUtilities utilities,
				InternalTaskBuildTrace buildTrace, TaskIdentifier taskId, SakerDirectory workingdir,
				SakerPath workingdirpath, SakerDirectory builddir, SakerPath builddirpath) {
			super(taskContext, utilities);
			this.buildTrace = buildTrace;
			this.taskId = taskId;
			this.workingDir = workingdir;
			this.workingDirPath = workingdirpath;
			this.buildDir = builddir;
			this.buildDirPath = builddirpath;
		}

		@Override
		public TaskIdentifier getTaskId() {
			return taskId;
		}

		@Override
		public SakerDirectory getTaskWorkingDirectory() {
			return workingDir;
		}

		@Override
		public SakerPath getTaskWorkingDirectoryPath() {
			return workingDirPath;
		}

		@Override
		public SakerDirectory getTaskBuildDirectory() {
			return buildDir;
		}

		@Override
		public SakerPath getTaskBuildDirectoryPath() {
			return buildDirPath;
		}

		@Override
		public TaskContext internalGetTaskContextIdentity() {
			return taskContext;
		}

		@Override
		public InternalTaskBuildTrace internalGetBuildTrace() {
			return buildTrace;
		}

	}

	//XXX issue a warning if a file is reported multiple times as output
	@RMIWrap(TaskContextRMIWrapper.class)
	protected static final class TaskExecutorContext<R>
			implements TaskContext, TaskExecutionUtilities, InternalTaskContext {

		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<TaskExecutionManager.TaskExecutorContext, TaskOutputChangeDetector> ARFU_reportedOutputChangeDetector = AtomicReferenceFieldUpdater
				.newUpdater(TaskExecutionManager.TaskExecutorContext.class, TaskOutputChangeDetector.class,
						"reportedOutputChangeDetector");
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<TaskExecutionManager.TaskExecutorContext, Throwable[]> ARFU_abortExceptions = AtomicReferenceFieldUpdater
				.newUpdater(TaskExecutionManager.TaskExecutorContext.class, Throwable[].class, "abortExceptions");

		@SuppressWarnings("rawtypes")
		private static final AtomicIntegerFieldUpdater<TaskExecutionManager.TaskExecutorContext> AIFU_finishCounter = AtomicIntegerFieldUpdater
				.newUpdater(TaskExecutionManager.TaskExecutorContext.class, "finishCounter");
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<TaskExecutionManager.TaskExecutorContext, BooleanLatch> ARFU_finishLatch = AtomicReferenceFieldUpdater
				.newUpdater(TaskExecutionManager.TaskExecutorContext.class, BooleanLatch.class, "finishLatch");

		//cached lambdas for less object instantiation
		private static final Function<? super TaskExecutorContext<?>, SakerDirectory> METHOD_REFERENCE_COMPUTE_TASK_WORKING_DIRECTORY = TaskExecutorContext::computeTaskWorkingDirectory;
		private static final Function<? super TaskExecutorContext<?>, SakerDirectory> METHOD_REFERENCE_COMPUTE_TASK_BUILD_DIRECTORY = TaskExecutorContext::computeTaskBuildDirectory;
		private static final Function<? super ExecutionProgressMonitor, TaskProgressMonitor> METHOD_REFERENCE_EXECUTION_PROGRESS_MONITOR_START_TASK_PROGRESS = ExecutionProgressMonitor::startTaskProgress;
		private static final Function<? super TaskExecutorContext<?>, Boolean> METHOD_REFERENCE_CREATE_STANDARD_STREAMS = TaskExecutorContext::createStandardStreams;

		protected final TaskExecutionManager executionManager;
		protected final ExecutionContextImpl executionContext;
		protected final TaskExecutionResult<?> prevTaskResult;
		protected final TaskExecutionResult<R> taskResult;
		protected final TaskIdentifier taskId;
		protected final TaskInvocationConfiguration capabilityConfig;

		protected final SimpleTaskDirectoryPathContext taskDirectoryContext;

		protected final Object prevTaskOutput;

		protected final ManagerTaskFutureImpl<R> future;

		protected final DependencyDelta deltas;

		/**
		 * An array containing all the exceptions that were reported via {@link #abortExecution(Throwable)}.
		 * <p>
		 * This is a {@link Throwable} array instead of some concurrent collection, or collector, because we expect zero
		 * or at most one abort exceptions. <br>
		 * Having this as simple an array is most likely a benefit performance-wise, as most tasks won't use this, and
		 * those who do, will only probably report a single exception. If they report more, then the cost of allocating
		 * a new array each time is negligible.
		 */
		protected volatile Throwable[] abortExceptions;
		protected volatile TaskOutputChangeDetector reportedOutputChangeDetector;

		protected final transient TaskDependencies resultDependencies;

		protected final Map<EnvironmentProperty<?>, Optional<?>> environmentPropertyDependencies = new ConcurrentHashMap<>();
		protected final Map<ExecutionProperty<?>, Optional<?>> executionPropertyDependencies = new ConcurrentHashMap<>();

		/**
		 * The number of running operations on this task context.
		 * <p>
		 * Negative if the task is finished.
		 * 
		 * @see #runOnUnfinished(Runnable)
		 * @see #runOnUnfinished(Supplier)
		 */
		private volatile int finishCounter;

		private volatile BooleanLatch finishLatch;

		private final ConcurrentMap<Object, FileDependencyCollector> taggedFileDependencyCollectors = new ConcurrentHashMap<>();

		private UnsyncByteArrayOutputStream stdOut;
		private UnsyncByteArrayOutputStream stdErr;
		private IdentifierByteSink<?> identifiedStdOut;
		private ByteSource stdIn;
		private Lock streamFlushingLock;
		private final LazySupplier<Boolean> streamCreationLazysupplier = LazySupplier.of(this,
				METHOD_REFERENCE_CREATE_STANDARD_STREAMS);
		private volatile int flushedStdOutOffset = 0;

		/**
		 * A lock that synchronizes access to {@link #acquireStandardIOLock()}.
		 */
		//Note: There could be reasons to make this lock reentrant, like recursively writing/reading via
		//the standard IO, but its considered generally bad practice, so unless there's a real use-case
		//we disallow it for now by using an exclusive lock
		private final Lock executionStdIOLockAcquireLock = ThreadUtils.newExclusiveLock();
		private volatile StandardIOLock acquiredExecutionStdIOLock;

		private transient final Set<TaskIdentifier> afterFileDeltaAccessedDirectlyCreatedTaskIds = ConcurrentHashMap
				.newKeySet();
		private transient volatile boolean fileDeltasAccessed = false;

		private transient final ConcurrentHashMap<TaskIdentifier, ManagerTaskFutureImpl<?>> currentlyWaitingForTaskFutures = new ConcurrentHashMap<>();

		protected final ConcurrentAppendAccumulator<String> printedLines = new ConcurrentAppendAccumulator<>();

		private transient final Map<TaskIdentifier, ConcurrentPrependAccumulator<TaskDependencyFutureImpl<?>>> unaddedTaskOutputDetectorTaskDependencyFutures = new ConcurrentHashMap<>();

		private final LazySupplier<TaskProgressMonitor> taskProgressMonitor;
		protected final ConcurrentAppendAccumulator<TaskExecutionEvent> events;

		private final LazySupplier<SakerDirectory> workingDirectorySupplier = LazySupplier.of(this,
				METHOD_REFERENCE_COMPUTE_TASK_WORKING_DIRECTORY);
		private final Supplier<SakerDirectory> buildDirectorySupplier;

		private final SecretInputReader secretInputReader;

		protected final ConcurrentPrependAccumulator<ManagerInnerTaskResults<?>> innerTasks = new ConcurrentPrependAccumulator<>();

		protected final InternalTaskBuildTrace taskBuildTrace;

		public TaskExecutorContext(TaskExecutionManager executionManager, TaskIdentifier taskid,
				ExecutionContextImpl executioncontext, TaskExecutionResult<?> prevTaskResult,
				TaskExecutionResult<R> taskResult, DependencyDelta deltas, TaskInvocationConfiguration capabalities,
				ManagerTaskFutureImpl<R> future, SimpleTaskDirectoryPathContext taskDirectoryContext,
				ConcurrentAppendAccumulator<TaskExecutionEvent> events) {
			this.executionManager = executionManager;
			this.taskId = taskid;
			this.executionContext = executioncontext;
			this.prevTaskResult = prevTaskResult;
			this.taskResult = taskResult;
			this.future = future;
			this.capabilityConfig = capabalities;
			this.events = events;
			this.resultDependencies = taskResult.getDependencies();
			this.deltas = deltas;

			this.taskDirectoryContext = taskDirectoryContext;

			this.taskBuildTrace = executionManager.buildTrace.taskBuildTrace(taskid, taskResult.getFactory(),
					taskDirectoryContext, capabilityConfig);
			this.taskBuildTrace.deltas(deltas.nonFileDeltas);
			if (deltas.isFileDeltasCalculated()) {
				this.taskBuildTrace.deltas(deltas.allFileDeltas.getFileDeltas());
			}

			if (executionManager.buildSakerDirectory == null) {
				buildDirectorySupplier = Functionals.nullSupplier();
			} else {
				buildDirectorySupplier = LazySupplier.of(this, METHOD_REFERENCE_COMPUTE_TASK_BUILD_DIRECTORY);
			}

			this.prevTaskOutput = prevTaskResult == null ? null : prevTaskResult.getOutput();

			this.taskProgressMonitor = LazySupplier.of(executionManager.progressMonitor,
					METHOD_REFERENCE_EXECUTION_PROGRESS_MONITOR_START_TASK_PROGRESS);

			SecretInputReader secretreader = executioncontext.getSecretInputReader();
			if (secretreader != null) {
				secretInputReader = new SecretInputReader() {
					@Override
					public String readSecret(String titleinfo, String message, String prompt, String secretidentifier) {
						return TaskExecutorContext.this.readTaskSecret(secretreader, titleinfo, message, prompt,
								secretidentifier);
					}
				};
			} else {
				secretInputReader = null;
			}
		}

		private void ensureStandardStreams() {
			streamCreationLazysupplier.get();
		}

		//returns Boolean so it can be used as a Function method reference
		private Boolean createStandardStreams() {
			this.streamFlushingLock = ThreadUtils.newExclusiveLock();
			this.stdOut = new UnsyncByteArrayOutputStream();
			this.stdErr = new UnsyncByteArrayOutputStream();

			this.identifiedStdOut = new IdentifierByteSink<UnsyncByteArrayOutputStream>(stdOut, streamFlushingLock) {
				@Override
				protected void writtenBytes() {
					final Lock stdioacquirelock = executionStdIOLockAcquireLock;
					stdioacquirelock.lock();
					try {
						final Lock flushlock = realSinkFlushingLock;
						flushlock.lock();
						try {
							int offset = flushedStdOutOffset;
							UnsyncByteArrayOutputStream stdout = realSink;
							int size = stdout.size();
							if (offset >= size) {
								return;
							}
							StandardIOLock lock = acquiredExecutionStdIOLock;
							ExecutionContextImpl executioncontext = executionContext;
							if (lock != null) {
								//write all bytes
								try {
									stdout.writeTo(executioncontext.getStdOutSink(), offset, size - offset);
								} catch (IOException e) {
								}
								flushedStdOutOffset = size;
							} else {
								//write until last end of line
								int eolidx = Math.max(stdout.lastIndexOf((byte) '\n'), stdout.lastIndexOf((byte) '\r'));
								if (eolidx > offset) {
									//try lock, as we dont want to block the task by waiting on output
									//if we dont acquire the lock, then the message will be written out 
									//    at the next flushing try
									//    or when the task wants to read input
									//    or when the task finishes
									//either way is fine.
									try (StandardIOLock l = executioncontext.tryAcquireStdIOLock()) {
										if (l != null) {
											flushedStdOutOffset = eolidx + 1;
											stdout.writeTo(executioncontext.getStdOutSink(), offset,
													eolidx - offset + 1);
										}
									} catch (IOException e) {
									}
								}
							}
						} finally {
							flushlock.unlock();
						}
					} finally {
						stdioacquirelock.unlock();
					}
				}
			};
			this.stdIn = new ByteSource() {
				@Override
				public int read(ByteRegion buffer) throws IOException {
					final Lock stdioacquirelock = executionStdIOLockAcquireLock;
					stdioacquirelock.lock();
					try {
						if (acquiredExecutionStdIOLock == null) {
							throw new TaskStandardIOLockIllegalStateException("Standard IO lock was not acquired.");
						}
						UnsyncByteArrayOutputStream stdout = stdOut;
						final Lock flushlock = streamFlushingLock;
						flushlock.lock();
						try {
							int outsize = stdout.size();
							int offset = flushedStdOutOffset;
							if (offset < outsize) {
								stdout.writeTo(executionContext.getStdOutSink(), offset, outsize - offset);
								flushedStdOutOffset = outsize;
							}
						} finally {
							flushlock.unlock();
						}
						ByteSource realin = executionContext.getStandardIn();
						return realin.read(buffer);
					} finally {
						stdioacquirelock.unlock();
					}
				}
			};
			return Boolean.TRUE;
		}

		protected SakerDirectory computeTaskBuildDirectory() {
			SakerPath builddir = this.taskDirectoryContext.getRelativeTaskBuildDirectoryPath();
			if (builddir == null) {
				return null;
			}
			return resolveDirectoryAtRelativePathCreate(executionManager.buildSakerDirectory, builddir);
		}

		@Override
		public TaskContext getTaskContext() {
			return this;
		}

		@Override
		public void invalidate(PathKey pathkey) {
			executionContext.invalidate(pathkey);
		}

		@Override
		public ContentDescriptor invalidateGetContentDescriptor(ProviderHolderPathKey pathkey) {
			return executionContext.invalidateGetContentDescriptor(pathkey);
		}

		@Override
		public void reportIgnoredException(ExceptionView e) throws NullPointerException {
			if (e == null) {
				e = TaskIdentifierExceptionView.create(new NullPointerException("ignored exception"));
			}
			executionContext.reportIgnoredException(taskId, e);
		}

		@Override
		public TaskProgressMonitor getProgressMonitor() {
			TaskProgressMonitor monitor = taskProgressMonitor.get();
			return new TaskProgressMonitor() {
				@Override
				public boolean isCancelled() {
					try {
						return executionManager.cancelledByInterruption || monitor.isCancelled();
					} catch (Exception e) {
						//we may never throw exceptions.
						try {
							reportIgnoredException(e);
						} catch (Exception ignored) {
						}
						return false;
					}
				}
			};
		}

		@Override
		public TaskExecutionUtilities getTaskUtilities() {
			return this;
		}

		private static void withPrintedLines(IdentifierByteSink<?> identifiedstdout, String line,
				Consumer<? super String> consumer) {
			String strid = identifiedstdout.getStringIdentifier();
			StringBuilder sb = new StringBuilder();
			int sbresetlen = strid.length();
			int llen = line.length();
			sb.append(strid);
			int lastoffset = 0;
			for (int i = 0; i < llen; i++) {
				char c = line.charAt(i);
				if (c == '\r') {
					if (i + 1 < llen) {
						char nc = line.charAt(i + 1);
						if (nc == '\n') {
							//\r\n sequence
							if (sb.length() == 0 && line.charAt(lastoffset) == PRINTED_LINE_VARIABLES_MARKER_CHAR) {
								sb.append('\\');
							}
							sb.append(line, lastoffset, i);
							++i;
							lastoffset = i + 1;
							consumer.accept(sb.toString());
							sb.setLength(sbresetlen);
						} else {
							//just \r
							if (sb.length() == 0 && line.charAt(lastoffset) == PRINTED_LINE_VARIABLES_MARKER_CHAR) {
								sb.append('\\');
							}
							sb.append(line, lastoffset, i);
							lastoffset = i + 1;
							consumer.accept(sb.toString());
							sb.setLength(sbresetlen);
						}
					}
				} else if (c == '\n') {
					//just \n
					if (sb.length() == 0 && line.charAt(lastoffset) == PRINTED_LINE_VARIABLES_MARKER_CHAR) {
						sb.append('\\');
					}
					sb.append(line, lastoffset, i);
					lastoffset = i + 1;
					consumer.accept(sb.toString());
					sb.setLength(sbresetlen);
				}
			}
			if (lastoffset < llen) {
				sb.append(line, lastoffset, llen);
				consumer.accept(sb.toString());
			}
		}

		@Override
		public void replayPrintln(String line) throws NullPointerException {
			Objects.requireNonNull(line, "line");
			ensureStandardStreams();
			runOnUnfinished(() -> {
				IdentifierByteSink<?> identifiedout = getStandardIdentifiedOutStreamsEnsured();
				withPrintedLines(identifiedout, line, printedLines::add);
			});
		}

		@Override
		public void println(String line) {
			Objects.requireNonNull(line, "line");
			ensureStandardStreams();
			runOnUnfinished(() -> {
				IdentifierByteSink<?> identifiedout = getStandardIdentifiedOutStreamsEnsured();
				withPrintedLines(identifiedout, line, printedLines::add);
				try {
					identifiedout.write(ByteArrayRegion.wrap(line.getBytes(StandardCharsets.UTF_8)));
					identifiedout.write('\n');
				} catch (IOException e) {
					reportIgnoredException(e);
				}
			});
		}

		@Override
		public TaskContext internalGetTaskContextIdentity() {
			return this;
		}

		@Override
		public void internalPrintlnVerboseVariables(String line) {
			ensureStandardStreams();
			runOnUnfinished(() -> {
				IdentifierByteSink<?> identifiedout = getStandardIdentifiedOutStreamsEnsured();
				try {
					identifiedout.write(ByteArrayRegion.wrap(future.processPrintedLineVariables(executionManager, line)
							.getBytes(StandardCharsets.UTF_8)));
					identifiedout.write('\n');
				} catch (IOException e) {
					reportIgnoredException(e);
				}
			});
		}

		@Override
		public void internalPrintlnVariables(String line) {
			ensureStandardStreams();
			runOnUnfinished(() -> {
				IdentifierByteSink<?> identifiedout = getStandardIdentifiedOutStreamsEnsured();
				String strid = identifiedout.getStringIdentifier();
				StringBuilder sb = new StringBuilder();
				sb.append(PRINTED_LINE_VARIABLES_MARKER_CHAR);
				sb.append(strid);

				int sbresetlen = strid.length() + 1;
				int llen = line.length();
				int lastoffset = 0;
				for (int i = 0; i < llen; i++) {
					char c = line.charAt(i);
					if (c == '\r') {
						if (i + 1 < llen) {
							char nc = line.charAt(i + 1);
							if (nc == '\n') {
								//\r\n sequence
								sb.append(line, lastoffset, i);
								++i;
								lastoffset = i + 1;
								printedLines.add(sb.toString());
								sb.setLength(sbresetlen);
							} else {
								//just \r
								sb.append(line, lastoffset, i);
								lastoffset = i + 1;
								printedLines.add(sb.toString());
								sb.setLength(sbresetlen);
							}
						}
					} else if (c == '\n') {
						//just \n
						sb.append(line, lastoffset, i);
						lastoffset = i + 1;
						printedLines.add(sb.toString());
						sb.setLength(sbresetlen);
					}
				}
				if (lastoffset < llen) {
					sb.append(line, lastoffset, llen);
					printedLines.add(sb.toString());
				}
				try {
					identifiedout.write(ByteArrayRegion.wrap(future.processPrintedLineVariables(executionManager, line)
							.getBytes(StandardCharsets.UTF_8)));
					identifiedout.write('\n');
				} catch (IOException e) {
					reportIgnoredException(e);
				}
			});
		}

		public Map<TaskIdentifier, ManagerTaskFutureImpl<?>> getCurrentlyWaitingForTaskFutures() {
			return currentlyWaitingForTaskFutures;
		}

		protected SimpleTaskDirectoryPathContext getTaskDirectoryContext() {
			return taskDirectoryContext;
		}

		protected <T> T runOnUnfinished(Supplier<T> function) {
			incrementFinishCounter();
			try {
				return function.get();
			} finally {
				decrementFinishCounter();
			}
		}

		protected void runOnUnfinished(Runnable function) {
			incrementFinishCounter();
			try {
				function.run();
			} finally {
				decrementFinishCounter();
			}
		}

		private void incrementFinishCounter() {
			while (true) {
				int i = this.finishCounter;
				if (i < 0) {
					throw new IllegalTaskOperationException("Task is already finished.", taskId);
				}
				if (AIFU_finishCounter.compareAndSet(this, i, i + 1)) {
					break;
				}
				//try again
			}
		}

		private void decrementFinishCounter() {
			int c = AIFU_finishCounter.decrementAndGet(this);
			if (c == 0) {
				BooleanLatch fsync = finishLatch;
				if (fsync != null) {
					fsync.signal();
				}
			}
		}

		@Override
		public void setStandardOutDisplayIdentifier(String displayid) {
			ensureStandardStreams();
			this.taskBuildTrace.setStandardOutDisplayIdentifier(displayid);
			IdentifierByteSink<?> identifiedout = getStandardIdentifiedOutStreamsEnsured();
			identifiedout.setIdentifier(displayid);
		}

		@Override
		public ByteSink getStandardOut() {
			ensureStandardStreams();
			IdentifierByteSink<?> result = getStandardIdentifiedOutStreamsEnsured();
			return result;
		}

		private IdentifierByteSink<?> getStandardIdentifiedOutStreamsEnsured() {
			IdentifierByteSink<?> result = identifiedStdOut;
			if (result == null) {
				//the task already finished, therefore the streams couldn't be created
				throw new IllegalTaskOperationException("Task is already finished.", taskId);
			}
			return result;
		}

		@Override
		public ByteSink getStandardErr() {
			ensureStandardStreams();
			ByteSink result = stdErr;
			if (result == null) {
				//the task already finished, therefore the streams couldn't be created
				throw new IllegalTaskOperationException("Task is already finished.", taskId);
			}
			return result;
		}

		@Override
		public ByteSource getStandardIn() {
			ensureStandardStreams();
			ByteSource result = stdIn;
			if (result == null) {
				//the task already finished, therefore the streams couldn't be created
				throw new IllegalTaskOperationException("Task is already finished.", taskId);
			}
			return result;
		}

		@Override
		public void acquireStandardIOLock() throws InterruptedException, TaskStandardIOLockIllegalStateException {
			ensureStandardStreams();
			runOnUnfinished(() -> {
				try {
					final Lock stdioacquirelock = executionStdIOLockAcquireLock;
					stdioacquirelock.lockInterruptibly();
					try {
						acquireStandardIOLockLocked();
					} finally {
						stdioacquirelock.unlock();
					}
				} catch (InterruptedException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			});
		}

		@Override
		public void releaseStandardIOLock() throws TaskStandardIOLockIllegalStateException {
			runOnUnfinished(() -> {
				final Lock stdioacquirelock = executionStdIOLockAcquireLock;
				stdioacquirelock.lock();
				try {
					releaseStandardIOLockLocked();
				} finally {
					stdioacquirelock.unlock();
				}
			});
		}

		private void acquireStandardIOLockLocked() throws InterruptedException {
			StandardIOLock lock = acquiredExecutionStdIOLock;
			if (lock != null) {
				throw new TaskStandardIOLockIllegalStateException("Already acquired lock.");
			}
			acquiredExecutionStdIOLock = executionContext.acquireStdIOLock();
		}

		private void releaseStandardIOLockLocked() {
			StandardIOLock lock = acquiredExecutionStdIOLock;
			if (lock == null) {
				throw new TaskStandardIOLockIllegalStateException("Lock not acquired.");
			}
			try {
				IdentifierByteSink<?> identifiedout = identifiedStdOut;
				if (identifiedout != null) {
					//safety check
					identifiedout.finishLastLine();
				}
			} finally {
				lock.close();
				acquiredExecutionStdIOLock = null;
			}
		}

		private String readTaskSecret(SecretInputReader secretreader, String titleinfo, String message, String prompt,
				String secretidentifier) {
			return runOnUnfinished(() -> {
				return secretreader.readSecret(titleinfo, message, prompt, secretidentifier);
			});
		}

		@Override
		public SecretInputReader getSecretReader() {
			return secretInputReader;
		}

		protected TaskInvocationConfiguration getCapabilityConfiguration() {
			return capabilityConfig;
		}

		protected boolean hasShortTaskCapability() {
			return getCapabilityConfiguration().isShort();
		}

		protected boolean isUsesComputationTokens() {
			return getCapabilityConfiguration().getRequestedComputationTokenCount() > 0;
		}

		protected boolean isInnerTasksComputationals() {
			return getCapabilityConfiguration().isInnerTasksComputationals();
		}

		protected boolean isRemoteDispatchable() {
			return getCapabilityConfiguration().isRemoteDispatchable();
		}

		@Override
		public ExecutionContext getExecutionContext() {
			//no need to ensure not finished
			return executionContext;
		}

		@Override
		public Set<? extends BuildDelta> getNonFileDeltas() {
			return deltas.nonFileDeltas;
		}

		@Override
		public TaskFileDeltas getFileDeltas() {
			return runOnUnfinished(() -> {
				if (prevTaskResult == null) {
					return EmptyTaskDeltas.INSTANCE;
				}
				ensureFileDeltasCollected();
				fileDeltasAccessed = true;
				//return deltas for every kind
				return deltas.allFileDeltas;
			});
		}

		@Override
		public TaskFileDeltas getFileDeltas(DeltaType deltatype) {
			Objects.requireNonNull(deltatype, "delta type");
			return runOnUnfinished(() -> {
				if (prevTaskResult == null) {
					return EmptyTaskDeltas.INSTANCE;
				}
				ensureFileDeltasCollected();
				fileDeltasAccessed = true;
				TypedDeltas result = deltas.deltaTypedBuildDeltas.get(deltatype);
				if (result == null) {
					return EmptyTaskDeltas.INSTANCE;
				}
				return result;
			});
		}

		private void ensureFileDeltasCollected() {
			if (prevTaskResult == null) {
				return;
			}
			deltas.ensureFileDeltasCollected(executionManager, this, prevTaskResult.getDependencies());
		}

		@Override
		public NavigableMap<SakerPath, SakerFile> getPreviousInputDependencies(Object tag) {
			return runOnUnfinished(() -> {
				if (prevTaskResult == null) {
					return Collections.emptyNavigableMap();
				}
				ensureFileDeltasCollected();
				fileDeltasAccessed = true;
				TaggedFileDependencyDelta fdelta = deltas.getTaggedFileDeltaIfExists(tag);
				if (fdelta == null) {
					return Collections.emptyNavigableMap();
				}
				return ImmutableUtils.unmodifiableNavigableMap(fdelta.inputDependencies);
			});
		}

		@Override
		public NavigableMap<SakerPath, SakerFile> getPreviousOutputDependencies(Object tag) {
			return runOnUnfinished(() -> {
				if (prevTaskResult == null) {
					return Collections.emptyNavigableMap();
				}
				ensureFileDeltasCollected();
				fileDeltasAccessed = true;
				TaggedFileDependencyDelta fdelta = deltas.getTaggedFileDeltaIfExists(tag);
				if (fdelta == null) {
					return Collections.emptyNavigableMap();
				}
				return ImmutableUtils.unmodifiableNavigableMap(fdelta.outputDependencies);
			});
		}

		@Override
		public NavigableMap<SakerPath, SakerFile> getPreviousFileAdditionDependency(FileCollectionStrategy dependency) {
			Objects.requireNonNull(dependency, "dependency");
			return runOnUnfinished(() -> {
				return getPreviousFileAdditionDependencyImpl(dependency);
			});
		}

		private NavigableMap<SakerPath, SakerFile> getPreviousFileAdditionDependencyImpl(
				FileCollectionStrategy dependency) {
			if (prevTaskResult == null) {
				return null;
			}
			//XXX we can skip calculation of the file deltas, if the addition dependency was not present previously. we need the tag though for efficient lookup.
			ensureFileDeltasCollected();
			fileDeltasAccessed = true;
			NavigableMap<SakerPath, ? extends SakerFile> got = deltas.fileAdditionDependencies.get(dependency);
			if (got == null) {
				return null;
			}
			return ImmutableUtils.unmodifiableNavigableMap(got);
		}

		@Override
		public TaskIdentifier getTaskId() {
			//no need to ensure not finished
			return taskId;
		}

		@Override
		public <T> TaskFuture<T> startTask(TaskIdentifier taskid, TaskFactory<T> taskfactory,
				TaskExecutionParameters parameters) throws TaskIdentifierConflictException {
			requireCalledOnTaskThread(this, false);
			return internalStartTaskOnTaskThread(taskid, taskfactory, parameters);
		}

		@Override
		public <T> TaskFuture<T> internalStartTaskOnTaskThread(TaskIdentifier taskid, TaskFactory<T> taskfactory,
				TaskExecutionParameters parameters) {
			requireValidTaskIdForTaskStarting(taskid);
			Objects.requireNonNull(taskfactory, "taskfactory");
			return runOnUnfinished(() -> {
				if (fileDeltasAccessed) {
					afterFileDeltaAccessedDirectlyCreatedTaskIds.add(taskid);
				}
				events.add(new TaskIdTaskEvent(TaskExecutionEventKind.START_TASK, taskid));
				ManagerTaskFutureImpl<T> future = executionManager.startImpl(taskfactory, taskid, executionContext,
						this.taskResult, parameters == null ? DEFAULT_EXECUTION_PARAMETERS : parameters, this);
				return new UserTaskFuture<>(future, this);
			});
		}

		@Override
		public <T> InnerTaskResults<T> startInnerTask(TaskFactory<T> taskfactory,
				InnerTaskExecutionParameters parameters) {
			Objects.requireNonNull(taskfactory, "task factory");
			return runOnUnfinished(() -> {
				return executionManager.startInnerImpl(taskfactory, parameters, this);
			});
		}

		private void requireValidTaskIdForTaskStarting(TaskIdentifier taskid) {
			Objects.requireNonNull(taskid, "taskid");
			if (taskid.equals(this.taskId)) {
				throw new IllegalTaskOperationException("Cannot start a task with the same identifier as the caller.",
						taskid);
			}
		}

		@Override
		public <T> TaskFuture<T> startTaskFuture(TaskIdentifier taskid, TaskFactory<T> taskfactory) {
			return startTask(taskid, taskfactory, DEFAULT_EXECUTION_PARAMETERS);
		}

		@Override
		public <T> T runTaskResult(TaskIdentifier taskid, TaskFactory<T> taskfactory,
				TaskExecutionParameters parameters) throws TaskIdentifierConflictException {
			requireCalledOnTaskThread(this, false);
			return internalRunTaskResultOnTaskThread(taskid, taskfactory, parameters);
		}

		@Override
		public <T> T internalRunTaskResultOnTaskThread(TaskIdentifier taskid, TaskFactory<T> taskfactory,
				TaskExecutionParameters parameters) {
			requireValidTaskIdForTaskStarting(taskid);
			Objects.requireNonNull(taskfactory, "taskfactory");
			return runOnUnfinished(() -> {
				if (fileDeltasAccessed) {
					afterFileDeltaAccessedDirectlyCreatedTaskIds.add(taskid);
				}
				events.add(new TaskIdTaskEvent(TaskExecutionEventKind.START_TASK, taskid));
				ManagerTaskFutureImpl<T> future = executionManager.executeImpl(taskfactory, taskid, executionContext,
						this.taskResult, parameters == null ? DEFAULT_EXECUTION_PARAMETERS : parameters, this);
				events.add(new TaskIdTaskEvent(TaskExecutionEventKind.WAITED_TASK, taskid));

				//we need to wait as if others started the task, and we don't run it, then the execute call won't wait for it
				TaskResultHolder<T> result = future.getWithoutAncestorWaiting(this);

				addTaskOutputChangeDetector(taskid, CommonTaskOutputChangeDetector.ALWAYS);
				return getOutputOrThrow(result);
			});
		}

		@Override
		public <T> TaskFuture<T> runTaskFuture(TaskIdentifier taskid, TaskFactory<T> taskfactory,
				TaskExecutionParameters parameters) throws TaskIdentifierConflictException {
			//XXX can we allow this on an inner task thread?
			requireCalledOnTaskThread(this, false);
			return internalRunTaskFutureOnTaskThread(taskid, taskfactory, parameters);
		}

		@Override
		public <T> TaskFuture<T> internalRunTaskFutureOnTaskThread(TaskIdentifier taskid, TaskFactory<T> taskfactory,
				TaskExecutionParameters parameters) {
			requireValidTaskIdForTaskStarting(taskid);
			Objects.requireNonNull(taskfactory, "taskfactory");
			return runOnUnfinished(() -> {
				if (fileDeltasAccessed) {
					afterFileDeltaAccessedDirectlyCreatedTaskIds.add(taskid);
				}
				events.add(new TaskIdTaskEvent(TaskExecutionEventKind.START_TASK, taskid));
				ManagerTaskFutureImpl<T> future = executionManager.executeImpl(taskfactory, taskid, executionContext,
						this.taskResult, parameters == null ? DEFAULT_EXECUTION_PARAMETERS : parameters, this);
				events.add(new TaskIdTaskEvent(TaskExecutionEventKind.WAITED_TASK, taskid));

				//don't throw here if the task execution failed. it is delayed until get() is called on the future

				//no need to wait the result, or report it as dependency.
				//dependency will be reported when any property of the run task is accessed
				return new UserTaskFuture<>(future, this);
			});
		}

		@Override
		public SakerPath getTaskWorkingDirectoryPath() {
			return taskDirectoryContext.getTaskWorkingDirectoryPath();
		}

		@Override
		public SakerPath getTaskBuildDirectoryPath() {
			return taskDirectoryContext.getTaskBuildDirectoryPath();
		}

		@Override
		public SakerDirectory getTaskWorkingDirectory() {
			return workingDirectorySupplier.get();
		}

		protected SakerDirectory computeTaskWorkingDirectory() {
			return resolveDirectoryAtPathCreate(taskDirectoryContext.getTaskWorkingDirectoryPath());
		}

		@Override
		public SakerDirectory getTaskBuildDirectory() {
			return buildDirectorySupplier.get();
		}

		@Override
		public <T> T getPreviousTaskOutput(Class<T> type) {
			Objects.requireNonNull(type, "type");
			Object result = prevTaskOutput;
			if (result == null) {
				return null;
			}
			if (type.isInstance(result)) {
				@SuppressWarnings("unchecked")
				T tres = (T) result;
				return tres;
			}
			return null;
		}

		@Override
		public <T> T getPreviousTaskOutput(Object tag, Class<T> type) {
			Objects.requireNonNull(tag, "tag");
			Objects.requireNonNull(type, "type");
			if (prevTaskResult == null) {
				return null;
			}
			Object result = prevTaskResult.getTaggedOutput(tag);
			if (result == null) {
				return null;
			}
			if (type.isInstance(result)) {
				@SuppressWarnings("unchecked")
				T tres = (T) result;
				return tres;
			}
			return null;
		}

		@Override
		public void setTaskOutput(Object tag, Object value) throws NullPointerException {
			Objects.requireNonNull(tag, "tag");
			Objects.requireNonNull(value, "value");
			runOnUnfinished(() -> {
				taskResult.addTaggedOutput(tag, value);
			});
		}

		@Override
		public void setMetaData(String metadataid, Object value) {
			Objects.requireNonNull(metadataid, "metadataid");
			Objects.requireNonNull(value, "value");
			runOnUnfinished(() -> {
				Object prev = taskResult.setMetaData(metadataid, value);
				if (prev != null) {
					SakerLog.warning().println("Metadata with id: " + metadataid + " was reported multiple times.");
				}
			});
		}

		@Override
		public <T> void reportEnvironmentDependency(EnvironmentProperty<T> environmentproperty, T expectedvalue) {
			Objects.requireNonNull(environmentproperty, "property");
			runOnUnfinished(() -> {
				this.environmentPropertyDependencies.compute(environmentproperty, (k, prev) -> {
					if (prev == null) {
						return Optional.ofNullable(expectedvalue);
					}
					if (!Objects.equals(prev.orElse(null), expectedvalue)) {
						throw new IllegalTaskOperationException("Reported multiple environment dependency for: "
								+ environmentproperty + " with " + expectedvalue + " and " + prev, taskId);
					}
					//no change
					return prev;
				});
			});
		}

		@Override
		public <T> void reportExecutionDependency(ExecutionProperty<T> executionproperty, T expectedvalue) {
			Objects.requireNonNull(executionproperty, "property");
			runOnUnfinished(() -> {
				this.executionPropertyDependencies.compute(executionproperty, (k, prev) -> {
					if (prev == null) {
						return Optional.ofNullable(expectedvalue);
					}
					if (!Objects.equals(prev.orElse(null), expectedvalue)) {
						throw new IllegalTaskOperationException("Reported multiple execution dependency for: "
								+ executionproperty + " with " + expectedvalue + " and " + prev, taskId);
					}
					//no change
					return prev;
				});
			});
		}

		private static TreeMap<SakerPath, ContentDescriptor> denullizeContentDescriptors(
				NavigableMap<SakerPath, ? extends ContentDescriptor> pathcontents) {
			return new TreeMap<>(
					new TransformingNavigableMap<SakerPath, ContentDescriptor, SakerPath, ContentDescriptor>(
							pathcontents) {
						@Override
						protected Entry<SakerPath, ContentDescriptor> transformEntry(SakerPath key,
								ContentDescriptor value) {
							return ImmutableUtils.makeImmutableMapEntry(key, denullizeContentDescriptor(value));
						}
					});
		}

		private static ContentDescriptor denullizeContentDescriptor(ContentDescriptor content) {
			if (content == null) {
				content = NonExistentContentDescriptor.INSTANCE;
			}
			return content;
		}

		@Override
		public void reportInputFileDependency(Object tag, SakerPath path, ContentDescriptor expectedcontent) {
			Objects.requireNonNull(path, "path");
			ContentDescriptor denullized = denullizeContentDescriptor(expectedcontent);
			runOnUnfinished(() -> {
				FileDependencyCollector fdeps = taggedFileDependencyCollectors
						.computeIfAbsent(NullFileDependencyTag.denullize(tag), x -> new FileDependencyCollector());
				fdeps.singleReportedInputDependencies.putIfAbsent(path, denullized);
			});
		}

		@Override
		public void reportInputFileDependency(Object tag,
				NavigableMap<SakerPath, ? extends ContentDescriptor> pathcontents) {
			ObjectUtils.requireNaturalOrder(pathcontents);
			runOnUnfinished(() -> {
				FileDependencyCollector fdeps = taggedFileDependencyCollectors
						.computeIfAbsent(NullFileDependencyTag.denullize(tag), x -> new FileDependencyCollector());

				NavigableMap<SakerPath, ContentDescriptor> denullizedpathcontents = denullizeContentDescriptors(
						pathcontents);

				fdeps.inputDependencies.add(denullizedpathcontents);
			});
		}

		@Override
		public void reportOutputFileDependency(Object tag, SakerPath path, ContentDescriptor expectedcontent) {
			Objects.requireNonNull(path, "path");
			ContentDescriptor denullized = denullizeContentDescriptor(expectedcontent);
			runOnUnfinished(() -> {
				FileDependencyCollector fdeps = taggedFileDependencyCollectors
						.computeIfAbsent(NullFileDependencyTag.denullize(tag), x -> new FileDependencyCollector());
				fdeps.singleReportedOutputDependencies.put(path, denullized);
			});
		}

		@Override
		public void reportOutputFileDependency(Object tag,
				NavigableMap<SakerPath, ? extends ContentDescriptor> pathcontents) {
			ObjectUtils.requireNaturalOrder(pathcontents);
			Objects.requireNonNull(pathcontents, "pathcontents");
			runOnUnfinished(() -> {
				FileDependencyCollector fdeps = taggedFileDependencyCollectors
						.computeIfAbsent(NullFileDependencyTag.denullize(tag), x -> new FileDependencyCollector());

				NavigableMap<SakerPath, ContentDescriptor> denullizedpathcontents = denullizeContentDescriptors(
						pathcontents);

				fdeps.outputDependencies.add(denullizedpathcontents);
			});
		}

		@Override
		public void reportInputFileAdditionDependency(Object tag, FileCollectionStrategy dependency) {
			Objects.requireNonNull(dependency, "dependency");
			runOnUnfinished(() -> {
				FileDependencyCollector fdeps = taggedFileDependencyCollectors
						.computeIfAbsent(NullFileDependencyTag.denullize(tag), x -> new FileDependencyCollector());
				fdeps.additionDependencies.add(dependency);
			});
		}

		@Override
		public Path mirror(SakerFile file, DirectoryVisitPredicate synchpredicate)
				throws IOException, FileMirroringUnavailableException {
			return executionContext.mirror(file, synchpredicate);
		}

		@Override
		public <T> T computeFileContentData(SakerFile file, FileDataComputer<T> computer) throws IOException {
			return executionContext.computeFileContentData(file, computer);
		}

		protected void executionFinished() throws InterruptedException, InnerTaskExecutionException {
			boolean interrupted = false;
			try {
				BooleanLatch fsync = null;
				//join all inner tasks
				//try to set the flag to finished, to ensure that no more inner tasks are started
				//if successful, great, we can finish the task
				//if not, then try to join the pending inner tasks again, and repeat
				InnerTaskExecutionException innerexc = null;
				do {
					for (ManagerInnerTaskResults<?> h; (h = innerTasks.take()) != null;) {
						while (true) {
							try {
								try {
									h.waitFinishCancelOptionally();
									while (true) {
										//retrieve all results of the tasks
										InnerTaskResultHolder<?> n = h.internalGetNextOnExecutionFinish();
										if (n == null) {
											break;
										}
										try {
											//do not retrieve the full result, only if there was an exception
											Throwable e = n.getExceptionIfAny();
											if (e != null) {
												innerexc = IOUtils.addExc(innerexc, new InnerTaskExecutionException(e));
											}
										} catch (RuntimeException e) {
											//handle any runtime exceptions here instead of in the enclosing try
											//because the result itself should be handled differently than the overall inner task access failures
											innerexc = IOUtils.addExc(innerexc, new InnerTaskExecutionException(
													"Failed to retrieve inner task result.", e));
										}
									}
								} catch (InterruptedException ie) {
									//interrupt the inner task and all the remainings
									//propagate each interrupt to the tasks (it used to propagate only the first one)
									interrupted = true;
									h.interrupt();
									for (ManagerInnerTaskResults<?> ith : innerTasks) {
										ith.interrupt();
									}
									//try to wait for this one again
									continue;
								}
							} catch (RuntimeException e) {
								//if the waiting fails somewhy, e.g. due to RMI failures
								innerexc = IOUtils.addExc(innerexc,
										new InnerTaskExecutionException("Failed to retrieve inner task result.", e));
							}
							break;
						}
					}
					if (AIFU_finishCounter.compareAndSet(this, 0, -1)) {
						break;
					}
					//there is still an operation pending on the task context
					if (fsync == null) {
						fsync = BooleanLatch.newBooleanLatch();
						if (!ARFU_finishLatch.compareAndSet(this, null, fsync)) {
							//shouldnt ever happen, but just in case
							fsync = this.finishLatch;
						}
					}
					if (AIFU_finishCounter.compareAndSet(this, 0, -1)) {
						break;
					}
					fsync.await();
				} while (!AIFU_finishCounter.compareAndSet(this, 0, -1));
				IOUtils.throwExc(innerexc);
			} finally {
				final Lock stdioacquirelock = executionStdIOLockAcquireLock;
				stdioacquirelock.lock();
				try {
					StandardIOLock iolock = acquiredExecutionStdIOLock;
					if (iolock != null) {
						SakerLog.warning().out(this).println("Standard IO lock wasn't released by task: " + taskId);
						iolock.close();
					}
				} finally {
					stdioacquirelock.unlock();
				}
				if (interrupted) {
					Thread.currentThread().interrupt();
				}
				//always set the flag, as we can prematurely throw exceptions
				finishCounter = -1;
			}
		}

		@SuppressWarnings("try")
		protected void flushStdStreamsFinalizeExecution() {
			Boolean streamscreated = streamCreationLazysupplier.getIfComputedPrevent();
			if (streamscreated != null) {
				//the streams have been created, flush them
				final Lock identifiedlock = identifiedStdOut.accessLock;
				identifiedlock.lock();
				try {
					final Lock flushlock = streamFlushingLock;
					flushlock.lock();
					try {
						identifiedStdOut.finishLastLineLockedSinkLocked();
						int outoffset = flushedStdOutOffset;
						int outsize = stdOut.size();
						boolean hasout = outoffset < outsize;
						boolean haserr = !stdErr.isEmpty();
						if (hasout || haserr) {
							//had output
							//flushing is done on a separate thread, so it doesnt block other things
							executionManager.generalExecutionThreadWorkPool.offer(() -> {
								try (StandardIOLock l = executionContext.acquireStdIOLock()) {
									if (hasout) {
										try {
											stdOut.writeTo(executionContext.getStdOutSink(), outoffset,
													outsize - outoffset);
										} catch (IOException e) {
											//ignore exception
										}
									}
									if (haserr) {
										try {
											stdErr.writeTo(executionContext.getStdErrSink());
										} catch (IOException e) {
											//ignore exception
										}
									}
								}
							});
						}
					} finally {
						flushlock.unlock();
					}
				} finally {
					identifiedlock.unlock();
				}
			}
			this.taskBuildTrace.closeStandardIO(stdOut, stdErr);
			this.taskBuildTrace.close(this, taskResult);
		}

		protected void finishExecutionDependencies() {
			if (!taggedFileDependencyCollectors.isEmpty()) {
				Map<Object, FileDependencies> taggedfiledeps = new HashMap<>();
				for (Entry<Object, FileDependencyCollector> entry : taggedFileDependencyCollectors.entrySet()) {
					FileDependencyCollector depcollector = entry.getValue();
					FileDependencies fdeps = new FileDependencies();

					depcollector.inputDependencies.add(depcollector.singleReportedInputDependencies);
					if (depcollector.inputDependencies.isAnyIterableAdded()) {
						executionManager.generalExecutionThreadWorkPool.offer(() -> {
							NavigableMap<SakerPath, ContentDescriptor> depspathmap = depcollector.inputDependencies
									.createImmutableNavigableMap(MatchingKeyPolicy.CHOOSE_EARLIEST);
							fdeps.setInputFileDependencies(depspathmap);
						});
					} else {
						fdeps.setInputFileDependencies(Collections.emptyNavigableMap());
					}

					depcollector.outputDependencies.add(depcollector.singleReportedOutputDependencies);
					if (depcollector.outputDependencies.isAnyIterableAdded()) {
						executionManager.generalExecutionThreadWorkPool.offer(() -> {
							NavigableMap<SakerPath, ContentDescriptor> depspathmap = depcollector.outputDependencies
									.createImmutableNavigableMap(MatchingKeyPolicy.CHOOSE_LATEST);
							fdeps.setOutputFileDependencies(depspathmap);
						});
					} else {
						fdeps.setOutputFileDependencies(Collections.emptyNavigableMap());
					}

					fdeps.setAdditionDependencies(depcollector.additionDependencies);
					taggedfiledeps.put(entry.getKey(), fdeps);
				}

				this.resultDependencies.setTaggedFileDependencies(ImmutableUtils.unmodifiableMap(taggedfiledeps));
			}
			//create a new map, as the one we use is synchronized map
			Map<EnvironmentProperty<?>, Object> envpropdeps = new HashMap<>(environmentPropertyDependencies);
			replaceOptionalValuesWithTheirValues(envpropdeps);
			resultDependencies.setEnvironmentPropertyDependencies(envpropdeps);

			HashMap<ExecutionProperty<?>, Object> execpropdeps = new HashMap<>(executionPropertyDependencies);
			replaceOptionalValuesWithTheirValues(execpropdeps);
			resultDependencies.setExecutionPropertyDependencies(execpropdeps);

			for (ConcurrentPrependAccumulator<TaskDependencyFutureImpl<?>> deplist : unaddedTaskOutputDetectorTaskDependencyFutures
					.values()) {
				Iterator<TaskDependencyFutureImpl<?>> it = deplist.clearAndIterator();
				while (it.hasNext()) {
					TaskDependencyFutureImpl<?> depresult = it.next();
					ConcurrentPrependAccumulator<TaskOutputChangeDetector> changedetectors = depresult.outputChangeDetectors;
					TaskIdentifier depresulttaskid = depresult.getTaskIdentifier();
					if (changedetectors == null) {
						//the output change detector was not set.
						addTaskOutputChangeDetector(depresulttaskid, CommonTaskOutputChangeDetector.ALWAYS);
					} else {
						Iterator<TaskOutputChangeDetector> detectorit = changedetectors.clearAndIterator();
						while (detectorit.hasNext()) {
							TaskOutputChangeDetector changedetector = detectorit.next();
							addTaskOutputChangeDetector(depresult.getTaskIdentifier(), changedetector);
						}
					}
				}
			}
			unaddedTaskOutputDetectorTaskDependencyFutures.clear();
		}

		protected static void replaceOptionalValuesWithTheirValues(Map<?, Object> envpropdeps) {
			for (Entry<?, Object> entry : envpropdeps.entrySet()) {
				Optional<?> optval = (Optional<?>) entry.getValue();
				entry.setValue(optval.orElse(null));
			}
		}

		@Override
		public void reportSelfTaskOutputChangeDetector(TaskOutputChangeDetector changedetector) {
			Objects.requireNonNull(changedetector, "changedetector");
			runOnUnfinished(() -> {
				if (!ARFU_reportedOutputChangeDetector.compareAndSet(this, null, changedetector)) {
					throw new IllegalTaskOperationException("Task already reported output change detector.", taskId);
				}
			});
		}

		protected void reportFinishedTaskDependency(TaskIdentifier taskid, TaskResultHolder<?> result) {
			this.resultDependencies.addTaskDependency(taskid, result.getDependencies(), true);
		}

		protected void reportWaitedTaskDependency(TaskIdentifier taskid, TaskResultHolder<?> result) {
			Objects.requireNonNull(result, "result");
			boolean filedeltasaccessed = fileDeltasAccessed;

			boolean newlyadded = this.resultDependencies.addTaskDependency(taskid, result.getDependencies(), false);
			if (newlyadded && filedeltasaccessed) {
				//if the task was newly added, and the file deltas are already accessed by the task,
				//    then warn the user about possible delta inconsistencies
				//    only warn if the dependent task was not created by this task (or its descendants)
				if (!executionManager.isTaskCreatedByTransitivelyRuntime(taskid,
						afterFileDeltaAccessedDirectlyCreatedTaskIds)) {
					//XXX add more information about this warning
					SakerLog.warning().verbose().println(
							"Task dependency reported for task which was created before accessing file deltas. ("
									+ taskid + ")");
					if (TestFlag.ENABLED) {
						throw new AssertionError(
								"Task dependency reported for task which was created before accessing file deltas. ("
										+ taskid + ")");
					}
				}
			}
		}

		protected void addTaskOutputChangeDetector(TaskIdentifier taskid,
				TaskOutputChangeDetector outputchangedetector) {
			this.resultDependencies.addTaskOutputChangeDetector(taskid, outputchangedetector);
		}

		protected TaskExecutionManager getExecutionManager() {
			return executionManager;
		}

		@Override
		public TaskFuture<?> getTaskFuture(TaskIdentifier taskid) {
			Objects.requireNonNull(taskid, "taskid");
			if (this.taskId.equals(taskid)) {
				throw new IllegalArgumentException("Cannot get future for the caller task itself.");
			}
			//we don't really need to ensure not finished, but we do it anyway so the context is no longer used to retrieve futures
			ManagerTaskFutureImpl<?> realfuture = executionManager.getOrCreateTaskFuture(taskid);
			return runOnUnfinished(() -> {
				return new UserTaskFuture<>(realfuture, this);
			});
		}

		@Override
		public TaskResultDependencyHandle getTaskResultDependencyHandle(TaskIdentifier taskid)
				throws NullPointerException, IllegalArgumentException {
			//we don't really need to ensure not finished, but we do it anyway so the context is no longer used to retrieve futures
			ManagerTaskFutureImpl<?> realfuture = executionManager.getOrCreateTaskFuture(taskid);
			return runOnUnfinished(() -> {
				TaskDependencyFutureImpl<?> depfuture = new TaskDependencyFutureImpl<>(this, realfuture);
				return new UserTaskResultDependencyHandle(depfuture);
			});
		}

		@Override
		public void abortExecution(Throwable cause) throws NullPointerException {
			Objects.requireNonNull(cause, "cause");
			while (true) {
				Throwable[] excs = this.abortExceptions;
				Throwable[] narray;
				if (excs == null) {
					narray = new Throwable[] { cause };
				} else {
					narray = ArrayUtils.appended(excs, cause);
				}
				if (ARFU_abortExceptions.compareAndSet(this, excs, narray)) {
					break;
				}
				//try again
			}
		}

		@Override
		public void reportIDEConfiguration(IDEConfiguration configuration) {
			Objects.requireNonNull(configuration, "configuration");

			runOnUnfinished(() -> {
				taskResult.addIDEConfiguration(configuration);
				executionManager.runIdeConfigurations.add(configuration);
			});
		}

		@Override
		public String toString() {
			return taskId.toString();
		}

		protected void waitingForTask(ManagerTaskFutureImpl<?> future) {
			this.currentlyWaitingForTaskFutures.put(future.getTaskIdentifier(), future);
			this.future.unparkWaitingThreads(executionManager, ManagerTaskFutureImpl.EVENT_WAITING_FOR_TASK_CHANGED);
		}

		protected void waitedForTask(ManagerTaskFutureImpl<?> future) {
			TaskIdentifier taskid = future.getTaskIdentifier();
			this.currentlyWaitingForTaskFutures.remove(taskid, future);
			this.future.unparkWaitingThreads(executionManager, ManagerTaskFutureImpl.EVENT_WAITING_FOR_TASK_CHANGED);
		}

		@Override
		public void reportInputFileDependency(Object tag, SakerFile file) {
			SakerPath path = SakerPathFiles.requireAbsolutePath(file);
			reportInputFileDependency(tag, path, file.getContentDescriptor());
		}

		@Override
		public void reportInputFileDependency(Object tag, Iterable<? extends SakerFile> files) {
			Objects.requireNonNull(files, "files");
			for (SakerFile file : files) {
				SakerPath path = SakerPathFiles.requireAbsolutePath(file);
				reportInputFileDependency(tag, path, file.getContentDescriptor());
			}
		}

		@Override
		public NavigableMap<SakerPath, SakerFile> collectFiles(FileCollectionStrategy fileadditiondependency) {
			Objects.requireNonNull(fileadditiondependency, "file addition dependency");
			return collectFilesImpl(fileadditiondependency);
		}

		@Override
		public NavigableMap<SakerPath, SakerFile> collectFilesReportInputFileAndAdditionDependency(Object tag,
				FileCollectionStrategy fileadditiondependency) throws NullPointerException {
			Objects.requireNonNull(fileadditiondependency, "file addition dependency");

			NavigableMap<SakerPath, SakerFile> files = collectFilesReportAdditionDependencyImpl(tag,
					fileadditiondependency);
			NavigableMap<SakerPath, ContentDescriptor> contentsmap = SakerPathFiles.toFileContentMap(files);

			reportInputFileDependency(tag, contentsmap);

			return files;
		}

		@Override
		public NavigableMap<SakerPath, SakerFile> collectFilesReportInputFileAndAdditionDependency(Object tag,
				Iterable<? extends FileCollectionStrategy> fileadditiondependencies) throws NullPointerException {
			Objects.requireNonNull(fileadditiondependencies, "file addition dependencies");
			NavigableMap<SakerPath, SakerFile> result = collectFilesReportAdditionDependencyImpl(tag,
					fileadditiondependencies);
			NavigableMap<SakerPath, ContentDescriptor> contentsmap = SakerPathFiles.toFileContentMap(result);
			reportInputFileDependency(tag, contentsmap);
			return result;
		}

		@Override
		public NavigableMap<SakerPath, SakerFile> collectFilesReportAdditionDependency(Object tag,
				FileCollectionStrategy fileadditiondependency) throws NullPointerException {
			Objects.requireNonNull(fileadditiondependency, "file addition dependency");

			return collectFilesReportAdditionDependencyImpl(tag, fileadditiondependency);
		}

		@Override
		public NavigableMap<SakerPath, SakerFile> collectFilesReportAdditionDependency(Object tag,
				Iterable<? extends FileCollectionStrategy> fileadditiondependencies) throws NullPointerException {
			Objects.requireNonNull(fileadditiondependencies, "file addition dependencies");
			NavigableMap<SakerPath, SakerFile> result = collectFilesReportAdditionDependencyImpl(tag,
					fileadditiondependencies);
			return result;
		}

		private NavigableMap<SakerPath, SakerFile> collectFilesImpl(FileCollectionStrategy fileadditiondependency) {
			NavigableMap<SakerPath, SakerFile> prev = getPreviousFileAdditionDependencyImpl(fileadditiondependency);
			if (prev != null) {
				return prev;
			}
			return fileadditiondependency.collectFiles(executionContext, this);
		}

		private NavigableMap<SakerPath, SakerFile> collectFilesReportAdditionDependencyImpl(Object tag,
				FileCollectionStrategy fileadditiondependency) {
			NavigableMap<SakerPath, SakerFile> files = collectFilesImpl(fileadditiondependency);
			reportInputFileAdditionDependency(tag, fileadditiondependency);
			return files;
		}

		private NavigableMap<SakerPath, SakerFile> collectFilesReportAdditionDependencyImpl(Object tag,
				Iterable<? extends FileCollectionStrategy> fileadditiondependencies) {
			Iterator<? extends FileCollectionStrategy> it = fileadditiondependencies.iterator();
			if (!it.hasNext()) {
				return Collections.emptyNavigableMap();
			}
			FileCollectionStrategy additiondep = it.next();
			if (!it.hasNext()) {
				return collectFilesReportAdditionDependency(tag, additiondep);
			}
			ConcurrentEntryMergeSorter<SakerPath, SakerFile> sorter = new ConcurrentEntryMergeSorter<>();
			while (true) {
				Objects.requireNonNull(additiondep, "file addition dependency");

				NavigableMap<SakerPath, SakerFile> files = collectFilesReportAdditionDependencyImpl(tag, additiondep);
				sorter.add(files);

				if (!it.hasNext()) {
					break;
				}
				additiondep = it.next();
			}
			NavigableMap<SakerPath, SakerFile> result = sorter
					.createImmutableNavigableMap(MatchingKeyPolicy.CHOOSE_LATEST);
			return result;
		}

		@Override
		public void reportOutputFileDependency(Object tag, SakerFile file) {
			SakerPath path = SakerPathFiles.requireAbsolutePath(file);
			reportOutputFileDependency(tag, path, file.getContentDescriptor());
		}

		@Override
		public void reportOutputFileDependency(Object tag, Iterable<? extends SakerFile> files) {
			Objects.requireNonNull(files, "files");
			for (SakerFile file : files) {
				SakerPath path = SakerPathFiles.requireAbsolutePath(file);
				reportOutputFileDependency(tag, path, file.getContentDescriptor());
			}
		}

		@Override
		public <T> T getReportEnvironmentDependency(EnvironmentProperty<T> property) {
			Objects.requireNonNull(property, "property");
			T val = executionContext.getEnvironment().getEnvironmentPropertyCurrentValue(property);
			this.reportEnvironmentDependency(property, val);
			return val;
		}

		@Override
		public <T> T getReportExecutionDependency(ExecutionProperty<T> property) throws NullPointerException {
			Objects.requireNonNull(property, "property");
			T val = executionContext.getExecutionPropertyCurrentValue(property);
			this.reportExecutionDependency(property, val);
			return val;
		}

		@Override
		public SakerFile resolveAtPath(SakerPath path) {
			Objects.requireNonNull(path, "path");
			if (path.isRelative()) {
				return resolveAtRelativePath(getTaskWorkingDirectory(), path);
			}
			return SakerPathFiles.resolveAtAbsolutePath(executionContext, path);
		}

		@Override
		public SakerFile resolveFileAtPath(SakerPath path) throws NullPointerException {
			Objects.requireNonNull(path, "path");
			if (path.isRelative()) {
				return resolveFileAtRelativePath(getTaskWorkingDirectory(), path);
			}
			return SakerPathFiles.resolveFileAtAbsolutePath(executionContext, path);
		}

		@Override
		public SakerDirectory resolveDirectoryAtPath(SakerPath path) {
			Objects.requireNonNull(path, "path");
			if (path.isRelative()) {
				return resolveDirectoryAtRelativePath(getTaskWorkingDirectory(), path);
			}
			return SakerPathFiles.resolveDirectoryAtAbsolutePath(executionContext, path);
		}

		@Override
		public SakerDirectory resolveDirectoryAtPathCreate(SakerPath path) {
			Objects.requireNonNull(path, "path");
			if (path.isRelative()) {
				return resolveDirectoryAtRelativePathCreate(getTaskWorkingDirectory(), path);
			}
			return SakerPathFiles.resolveDirectoryAtAbsolutePathCreate(executionContext, path);
		}

		@Override
		public SakerDirectory resolveDirectoryAtPathCreateIfAbsent(SakerPath path) {
			Objects.requireNonNull(path, "path");
			if (path.isRelative()) {
				return resolveDirectoryAtRelativePathCreateIfAbsent(getTaskWorkingDirectory(), path);
			}
			return SakerPathFiles.resolveDirectoryAtAbsolutePathCreateIfAbsent(executionContext, path);
		}

		@Override
		public SakerFile resolveAtPath(SakerDirectory basedir, SakerPath path) throws NullPointerException {
			Objects.requireNonNull(path, "path");
			if (path.isRelative()) {
				Objects.requireNonNull(basedir, "base directory");
				return SakerPathFiles.resolveAtRelativePath(basedir, path);
			}
			return SakerPathFiles.resolveAtAbsolutePath(executionContext, path);
		}

		@Override
		public SakerFile resolveFileAtPath(SakerDirectory basedir, SakerPath path) throws NullPointerException {
			Objects.requireNonNull(path, "path");
			if (path.isRelative()) {
				Objects.requireNonNull(basedir, "base directory");
				return SakerPathFiles.resolveFileAtRelativePath(basedir, path);
			}
			return SakerPathFiles.resolveFileAtAbsolutePath(executionContext, path);
		}

		@Override
		public SakerDirectory resolveDirectoryAtPath(SakerDirectory basedir, SakerPath path)
				throws NullPointerException {
			Objects.requireNonNull(path, "path");
			if (path.isRelative()) {
				Objects.requireNonNull(basedir, "base directory");
				return SakerPathFiles.resolveDirectoryAtRelativePath(basedir, path);
			}
			return SakerPathFiles.resolveDirectoryAtAbsolutePath(executionContext, path);
		}

		@Override
		public SakerDirectory resolveDirectoryAtPathCreate(SakerDirectory basedir, SakerPath path)
				throws NullPointerException {
			Objects.requireNonNull(path, "path");
			if (path.isRelative()) {
				Objects.requireNonNull(basedir, "base directory");
				return SakerPathFiles.resolveDirectoryAtRelativePathCreate(basedir, path);
			}
			return SakerPathFiles.resolveDirectoryAtAbsolutePathCreate(executionContext, path);
		}

		@Override
		public SakerDirectory resolveDirectoryAtPathCreateIfAbsent(SakerDirectory basedir, SakerPath path)
				throws NullPointerException {
			Objects.requireNonNull(path, "path");
			if (path.isRelative()) {
				Objects.requireNonNull(basedir, "base directory");
				return SakerPathFiles.resolveDirectoryAtRelativePathCreateIfAbsent(basedir, path);
			}
			return SakerPathFiles.resolveDirectoryAtAbsolutePathCreateIfAbsent(executionContext, path);
		}

		@Override
		public SakerFile resolveAtAbsolutePath(SakerPath path) throws NullPointerException, InvalidPathFormatException {
			return SakerPathFiles.resolveAtAbsolutePath(executionContext, path);
		}

		@Override
		public SakerFile resolveFileAtAbsolutePath(SakerPath path)
				throws NullPointerException, InvalidPathFormatException {
			return SakerPathFiles.resolveFileAtAbsolutePath(executionContext, path);
		}

		@Override
		public SakerDirectory resolveDirectoryAtAbsolutePath(SakerPath path)
				throws NullPointerException, InvalidPathFormatException {
			return SakerPathFiles.resolveDirectoryAtAbsolutePath(executionContext, path);
		}

		@Override
		public SakerDirectory resolveDirectoryAtAbsolutePathCreate(SakerPath path)
				throws NullPointerException, InvalidPathFormatException {
			return SakerPathFiles.resolveDirectoryAtAbsolutePathCreate(executionContext, path);
		}

		@Override
		public SakerDirectory resolveDirectoryAtAbsolutePathCreateIfAbsent(SakerPath path)
				throws NullPointerException, InvalidPathFormatException {
			return SakerPathFiles.resolveDirectoryAtAbsolutePathCreateIfAbsent(executionContext, path);
		}

		@Override
		public void writeTo(SakerFile file, OutputStream os) throws IOException {
			Objects.requireNonNull(file, "file");
			Objects.requireNonNull(os, "output");
			file.writeTo(os);
		}

		@Override
		public void writeTo(SakerFile file, ByteSink os) throws IOException, NullPointerException {
			Objects.requireNonNull(file, "file");
			Objects.requireNonNull(os, "output");
			file.writeTo(os);
		}

		@Override
		public InputStream openInputStream(SakerFile file) throws IOException {
			Objects.requireNonNull(file, "file");
			return file.openInputStream();
		}

		@Override
		public ByteSource openByteSource(SakerFile file) throws IOException, NullPointerException {
			Objects.requireNonNull(file, "file");
			return file.openByteSource();
		}

		@Override
		public ByteArrayRegion getBytes(SakerFile file) throws IOException {
			Objects.requireNonNull(file, "file");
			return file.getBytes();
		}

		@Override
		public String getContent(SakerFile file) throws IOException {
			Objects.requireNonNull(file, "file");
			return file.getContent();
		}

		@Override
		public SakerFile createProviderPathFile(String name, ProviderHolderPathKey pathkey) throws IOException {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(pathkey, "path key");

			SakerPath path = pathkey.getPath();
			SakerFileProvider fp = pathkey.getFileProvider();
			FileEntry attrs = fp.getFileAttributes(path);
			if (attrs.isDirectory()) {
				return createProviderPathDirectoryImpl(name, path, fp);
			}

			return createProviderPathFileImpl(name, pathkey);
		}

		private SakerFile createProviderPathDirectoryImpl(String name, SakerPath path, SakerFileProvider fp) {
			ContentDatabase contentdb = executionContext.getContentDatabase();
			return new ProviderPathSakerDirectory(contentdb, name, fp, path);
		}

		private SakerFile createProviderPathFileImpl(String name, ProviderHolderPathKey pathkey) {
			ContentDatabase contentdb = executionContext.getContentDatabase();

			ContentHandle contenthandle = contentdb.getContentHandle(pathkey);
			return new ProviderPathSakerFile(name, pathkey, contenthandle);
		}

		@Override
		public SakerFile internalCreateProviderPathFile(String name, ProviderHolderPathKey pathkey, boolean directory)
				throws NullPointerException, IOException {
			if (directory) {
				SakerPath path = pathkey.getPath();
				SakerFileProvider fp = pathkey.getFileProvider();
				return createProviderPathDirectoryImpl(name, path, fp);
			}
			return createProviderPathFileImpl(name, pathkey);
		}

		@Override
		public SakerFile createProviderPathFile(String name, ProviderHolderPathKey pathkey,
				ContentDescriptor currentpathcontentdescriptor) throws IOException {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(pathkey, "path key");
			Objects.requireNonNull(currentpathcontentdescriptor, "content descriptor");

			ContentDatabase contentdb = executionContext.getContentDatabase();
			ContentHandle contenthandle = contentdb.discover(pathkey, currentpathcontentdescriptor);
			return new ProviderPathSakerFile(name, pathkey, contenthandle);
		}

		@Override
		public SakerFile createProviderPathFileWithPosixFilePermissions(String name, ProviderHolderPathKey pathkey,
				ContentDescriptor currentpathcontentdescriptor, Set<PosixFilePermission> permissions)
				throws IOException, NullPointerException, InvalidFileTypeException {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(pathkey, "path key");
			Objects.requireNonNull(currentpathcontentdescriptor, "content descriptor");

			ContentDatabase contentdb = executionContext.getContentDatabase();
			ContentHandle contenthandle = contentdb.discoverWithPosixFilePermissions(pathkey,
					currentpathcontentdescriptor, permissions);
			return new ProviderPathSakerFile(name, pathkey, contenthandle);
		}

		@Override
		public void invalidate(Iterable<? extends PathKey> pathkeys) {
			Objects.requireNonNull(pathkeys, "path keys");
			for (PathKey pk : pathkeys) {
				this.executionContext.invalidate(pk);
			}
		}

		@Override
		public void invalidateWithPosixFilePermissions(ProviderHolderPathKey pathkey)
				throws NullPointerException, IOException {
			Objects.requireNonNull(pathkey, "path key");
			this.executionContext.invalidateWithPosixFilePermissions(pathkey);
		}

		@Override
		public ContentDescriptor synchronize(ProviderHolderPathKey source, ProviderHolderPathKey target, int syncflag)
				throws IOException {
			Objects.requireNonNull(source, "source path key");
			Objects.requireNonNull(target, "target path key");

			ContentDatabase contentdb = this.executionContext.getContentDatabase();
			if (source.equals(target)) {
				//no need to synchronize, they are the same file
				return contentdb.getContentDescriptor(source);
			}
			Set<PosixFilePermission> posixFilePermissions;
			ContentDescriptor contentdescriptor;
			if (((syncflag
					& SYNCHRONIZE_FLAG_COPY_ASSOCIATED_POSIX_FILE_PERMISSIONS) == SYNCHRONIZE_FLAG_COPY_ASSOCIATED_POSIX_FILE_PERMISSIONS)) {
				ContentHandle sourcecontenthandle = contentdb.getContentHandle(source);
				contentdescriptor = sourcecontenthandle.getContent();
				posixFilePermissions = sourcecontenthandle.getPosixFilePermissions();
			} else {
				posixFilePermissions = null;
				contentdescriptor = contentdb.getContentDescriptor(source);
			}
			int ensureopflag = 0;
			if (((syncflag & SYNCHRONIZE_FLAG_NO_OVERWRITE_DIRECTORY) == SYNCHRONIZE_FLAG_NO_OVERWRITE_DIRECTORY)) {
				ensureopflag |= SakerFileProvider.OPERATION_FLAG_NO_RECURSIVE_DIRECTORY_DELETE;
			}
			if (((syncflag
					& SYNCHRONIZE_FLAG_DELETE_INTERMEDIATE_FILES) == SYNCHRONIZE_FLAG_DELETE_INTERMEDIATE_FILES)) {
				ensureopflag |= SakerFileProvider.OPERATION_FLAG_DELETE_INTERMEDIATE_FILES;
			}
			final int finalensureopflag = ensureopflag;
			contentdb.synchronize(target, contentdescriptor, new ContentUpdater() {
				@Override
				public void update() throws IOException {
					SakerFileProvider targetfp = target.getFileProvider();
					SakerPath targetpath = target.getPath();
					if (DirectoryContentDescriptor.INSTANCE.equals(contentdescriptor)) {
						targetfp.ensureWriteRequest(targetpath, FileEntry.TYPE_DIRECTORY, finalensureopflag);
						return;
					}

					targetfp.ensureWriteRequest(targetpath, FileEntry.TYPE_FILE, finalensureopflag);
					try (ByteSink out = targetfp.openOutput(targetpath)) {
						source.getFileProvider().writeTo(source.getPath(), out);
					}
					if (posixFilePermissions != null) {
						//TODO set this in a single call when writing or opening the contents
						targetfp.setPosixFilePermissions(targetpath, posixFilePermissions);
					}
				}

				@Override
				public boolean updateWithStream(ByteSink os) throws IOException, SecondaryStreamException {
					if (DirectoryContentDescriptor.INSTANCE.equals(contentdescriptor)) {
						return ContentUpdater.super.updateWithStream(os);
					}
					SakerFileProvider targetfp = target.getFileProvider();
					SakerPath targetpath = target.getPath();
					targetfp.ensureWriteRequest(targetpath, FileEntry.TYPE_FILE, finalensureopflag);
					try (OutputStream fout = ByteSink.toOutputStream(targetfp.openOutput(targetpath));
							PriorityMultiplexOutputStream multiplexos = new PriorityMultiplexOutputStream(fout,
									StreamUtils.closeProtectedOutputStream(fout))) {
						source.getFileProvider().writeTo(source.getPath(), multiplexos);
						IOException sec = multiplexos.getSecondaryException();
						if (sec != null) {
							throw new SecondaryStreamException(sec);
						}
					}
					if (posixFilePermissions != null) {
						//TODO set this in a single call when writing or opening the contents
						targetfp.setPosixFilePermissions(targetpath, posixFilePermissions);
					}
					return true;
				}

				@Override
				public Set<PosixFilePermission> getPosixFilePermissions() {
					return posixFilePermissions;
				}
			});
			return contentdescriptor;
		}

		@Override
		public void addSynchronizeInvalidatedProviderPathFileToDirectory(SakerDirectory directory,
				ProviderHolderPathKey pathkey, String filename) throws IOException {
			Objects.requireNonNull(directory, "directory");
			Objects.requireNonNull(pathkey, "path key");
			Objects.requireNonNull(filename, "file name");

			invalidate(pathkey);
			SakerFile file = createProviderPathFile(filename, pathkey);
			directory.add(file);
			file.synchronize();
		}

		@Override
		public void internalAddSynchronizeInvalidatedProviderPathFileToDirectory(SakerDirectory directory,
				ProviderHolderPathKey pathkey, String filename, boolean isdirectory) throws IOException {
			invalidate(pathkey);
			SakerFile file = internalCreateProviderPathFile(filename, pathkey, isdirectory);
			directory.add(file);
			file.synchronize();
		}

		@Override
		public Path mirrorDirectoryAtPath(SakerPath path, DirectoryVisitPredicate synchpredicate)
				throws IOException, InvalidFileTypeException, FileNotFoundException {
			SakerFile f = resolveAtPath(path);
			if (f == null) {
				throw new FileNotFoundException(path.toString());
			}
			if (!(f instanceof SakerDirectory)) {
				throw new InvalidFileTypeException("Not a directory at path: " + path);
			}
			return mirror(f, synchpredicate);
		}

		@Override
		public Path mirrorFileAtPath(SakerPath path)
				throws IOException, InvalidFileTypeException, FileNotFoundException, NullPointerException {
			SakerFile f = resolveAtPath(path);
			if (f == null) {
				throw new FileNotFoundException(path.toString());
			}
			if (f instanceof SakerDirectory) {
				throw new InvalidFileTypeException("File is a directory at path: " + path);
			}
			return mirror(f, null);
		}

		@Override
		public MirroredFileContents mirrorFileAtPathContents(SakerPath path)
				throws IOException, InvalidFileTypeException, FileNotFoundException, NullPointerException {
			SakerFile f = resolveAtPath(path);
			if (f == null) {
				throw new FileNotFoundException(path.toString());
			}
			if (f instanceof SakerDirectory) {
				throw new InvalidFileTypeException("File is a directory at path: " + path);
			}
			ContentDescriptor filecontents = f.getContentDescriptor();
			Path mirrorpath = executionContext.mirror(
					path.isRelative() ? getTaskWorkingDirectoryPath().resolve(path) : path, f, null, filecontents);
			return new MirroredFileContents(mirrorpath, filecontents);
		}

		@Override
		public Entry<SakerPath, ScriptPosition> internalGetOriginatingBuildFile() {
			return this.future.internalGetOriginatingBuildFile(executionManager);
		}

		@Override
		public PathSakerFileContents internalGetPathSakerFileContents(SakerPath path) {
			Objects.requireNonNull(path, "path");
			SakerFile file = resolveAtPath(path);
			if (file == null) {
				return null;
			}
			SakerPath abspath;
			if (path.isRelative()) {
				abspath = getTaskBuildDirectoryPath().resolve(path);
			} else {
				abspath = path;
			}
			return new PathSakerFileContents(file, abspath, file.getContentDescriptor());
		}

		@Override
		public InternalTaskBuildTrace internalGetBuildTrace() {
			return taskBuildTrace;
		}
	}

	private boolean isTaskCreatedByTransitivelyRuntime(TaskIdentifier taskid,
			Set<? extends TaskIdentifier> currentdirectlycreatedtasks) {
		if (currentdirectlycreatedtasks.isEmpty()) {
			return false;
		}
		if (currentdirectlycreatedtasks.contains(taskid)) {
			return true;
		}
		return isTaskCreatedByTransitivelyRuntimeImpl(taskid, currentdirectlycreatedtasks, new HashSet<>());
	}

	private boolean isTaskCreatedByTransitivelyRuntimeImpl(TaskIdentifier taskid,
			Set<? extends TaskIdentifier> currentdirectlycreatedtasks, Set<TaskIdentifier> checkedtasks) {
		for (TaskIdentifier createdtaskid : currentdirectlycreatedtasks) {
			if (checkedtasks.add(createdtaskid)) {
				TaskExecutionResult<?> res = resultTaskIdTaskResults.get(createdtaskid);
				if (res == null) {
					//the created task have not finished yet
					continue;
				}
				if (isTaskCreatedByTransitivelyRuntimeImpl(taskid,
						res.getDependencies().getDirectlyCreatedTaskIds().keySet(), checkedtasks)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isTransitivelyNonFinishedDependentOnImpl(TaskDependencies dependencies,
			TaskIdentifier taskid, Set<TaskIdentifier> visiteds) {
		Map<TaskIdentifier, ReportedTaskDependency> deps = dependencies.getTaskDependencies();
		ReportedTaskDependency reporteddep = deps.get(taskid);
		if (reporteddep != null) {
			if (!reporteddep.isFinishedRetrieval()) {
				return true;
			}
		}
		for (Entry<TaskIdentifier, ReportedTaskDependency> entry : deps.entrySet()) {
			if (visiteds.add(entry.getKey())) {
				if (isTransitivelyNonFinishedDependentOn(entry.getValue().getDependencies(), taskid)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isTransitivelyNonFinishedDependentOn(TaskDependencies dependencies, TaskIdentifier taskid) {
		return isTransitivelyNonFinishedDependentOnImpl(dependencies, taskid, new HashSet<>());
	}

	private static boolean isFinishedRetrievalAllowedBasedOnEventsImpl(TaskIdentifier retrievetaskid,
			TaskIdentifier limittaskid, TaskDependencies dependencies, Set<TaskIdentifier> visiteds) {
		Iterable<? extends TaskExecutionEvent> events = dependencies.getEvents();
		for (TaskExecutionEvent ev : events) {
			switch (ev.getKind()) {
				case WAITED_TASK: {
					TaskIdTaskEvent tidev = (TaskIdTaskEvent) ev;
					TaskIdentifier evtaskid = tidev.getTaskId();
					if (retrievetaskid.equals(evtaskid)) {
						//found the waited task in the events
						return true;
					}
					//add to the visiteds to avoid checking it multiple times
					if (visiteds.add(evtaskid)) {
						TaskDependencies subdeps = dependencies.getTaskDependencies().get(evtaskid).getDependencies();
						if (isTransitivelyNonFinishedDependentOnImpl(subdeps, retrievetaskid, visiteds)) {
							//the event waited task has a transitive dependency on the retrieve task id
							return true;
						}
					}
					break;
				}
				case START_TASK: {
					TaskIdTaskEvent tidev = (TaskIdTaskEvent) ev;
					TaskIdentifier evtaskid = tidev.getTaskId();
					if (evtaskid.equals(limittaskid)) {
						//do not visit more events after the waiter task has been started
						return false;
					}
					break;
				}
				case FINISH_RETRIEVED_TASK: {
					TaskIdTaskEvent tidev = (TaskIdTaskEvent) ev;
					TaskIdentifier evtaskid = tidev.getTaskId();
					if (retrievetaskid.equals(evtaskid)) {
						//do not visit more events after the limit task has been started
						return false;
					}
					break;
				}
				default: {
					//other event, ignore
					break;
				}
			}
		}
		return false;
	}

	private static class SimpleTaskResultHolder<R> implements TaskResultHolder<R> {
		private final TaskIdentifier taskId;
		private final R output;
		private final List<? extends Throwable> abortExceptions;
		private final Throwable failCause;
		private final TaskDependencies dependencies;

		public SimpleTaskResultHolder(TaskIdentifier taskId, R output, List<? extends Throwable> abortExceptions,
				Throwable failCause, TaskDependencies dependencies) {
			this.taskId = taskId;
			this.output = output;
			this.abortExceptions = abortExceptions;
			this.failCause = failCause;
			this.dependencies = dependencies;
		}

		@Override
		public TaskIdentifier getTaskIdentifier() {
			return taskId;
		}

		@Override
		public R getOutput() {
			return output;
		}

		@Override
		public List<? extends Throwable> getAbortExceptions() {
			return abortExceptions;
		}

		@Override
		public Throwable getFailCauseException() {
			return failCause;
		}

		@Override
		public TaskDependencies getDependencies() {
			return dependencies;
		}
	}

	private static class WaiterThreadHandle extends WeakReference<Thread> {
		/**
		 * Initial state, the handle hasn't been used yet anywhere. It hasn't been added to futures for waiting.
		 */
		static final int STATE_INITIAL = 0;
		/**
		 * The handle is waiting for notification, and has been added to the waiting thread collection of the relevant
		 * futures.
		 */
		static final int STATE_WAITING = 1;
		/**
		 * The handle has been notified and should re-check the condition.
		 */
		static final int STATE_NOTIFIED = 2;
		/**
		 * The handle is finished, no longer needs notification.
		 */
		static final int STATE_FINISHED = 3;

		static final AtomicIntegerFieldUpdater<TaskExecutionManager.WaiterThreadHandle> AIFU_state = AtomicIntegerFieldUpdater
				.newUpdater(TaskExecutionManager.WaiterThreadHandle.class, "state");
		public volatile int state = STATE_INITIAL;

		protected final int triggerEvents;

		public WaiterThreadHandle(int triggerEvents) {
			super(Thread.currentThread());
			this.triggerEvents = triggerEvents;
		}

		/**
		 * Notifies and unparks the thread handle.
		 * <p>
		 * The method always unparks the associated thread, unless the state is {@link #STATE_FINISHED}.
		 * <p>
		 * The method sets the state to {@link #STATE_NOTIFIED}, and handles the waiting thread count adjustments.
		 * 
		 * @param execmanager
		 *            The execution manager.
		 * @return <code>false</code> if the thread handle is finished, and can be released.
		 */
		public boolean unparkNotify(TaskExecutionManager execmanager) {
			while (true) {
				int s = this.state;
				switch (s) {
					case STATE_INITIAL: {
						if (AIFU_state.compareAndSet(this, STATE_INITIAL, STATE_NOTIFIED)) {
							LockSupport.unpark(get());
							return true;
						}
						break;
					}
					case STATE_WAITING: {
						execmanager.removeWaitingThreadCount(1);
						if (AIFU_state.compareAndSet(this, STATE_WAITING, STATE_NOTIFIED)) {
							LockSupport.unpark(get());
							return true;
						}
						//failed to set notified thread state, add back the previously removed waiting thread num
						execmanager.addWaitingThreadCount(1);
						break;
					}
					case STATE_NOTIFIED: {
						//the thread is already notified, unpark it again, so it will recheck the condition
						//(at this point, the state might get changed from STATE_NOTIFIED, but that's fine
						// as the condition will always get checked when the thread is unparked)
						LockSupport.unpark(get());
						return true;
					}
					case STATE_FINISHED: {
						//no need to unpark, already finished
						return false;
					}
					default: {
						throw new AssertionError(s);
					}
				}
			}
		}

		/**
		 * Sets the {@link #state} to {@link #STATE_FINISHED}, and removes/adds a waiting thread count if needed based
		 * on the state.
		 * <p>
		 * This method is to be called when the {@link #state} is {@link #STATE_WAITING}.
		 * 
		 * @param wasabouttoaddwaitingthreadcount
		 *            <code>true</code> if the caller code was about to add a waiting thread count.
		 */
		public void finish(TaskExecutionManager execmanager, boolean wasabouttoaddwaitingthreadcount) {
			while (true) {
				int s = this.state;
				switch (s) {
					case STATE_WAITING: {
						if (AIFU_state.compareAndSet(this, STATE_WAITING, STATE_FINISHED)) {
							if (!wasabouttoaddwaitingthreadcount) {
								//the thread was in a waiting state, remove this from the waiting thread count
								execmanager.removeWaitingThreadCount(1);
							}
							return;
						}
						break;
					}
					case STATE_NOTIFIED: {
						if (AIFU_state.compareAndSet(this, STATE_NOTIFIED, STATE_FINISHED)) {
							if (wasabouttoaddwaitingthreadcount) {
								//the thread got notified again from a WAITING state, and the notifier
								//already removed this waiting thread count
								//re-add it so we balance it out
								execmanager.addWaitingThreadCount(1);
							}
							return;
						}
						break;
					}
					/*
					case STATE_INITIAL: // shouldn't be called, the STATE_FINISHED should be directly set
					case STATE_FINISHED: // shouldn't call finish twice on a single handle
					*/
					default: {
						throw new AssertionError(s);
					}
				}
			}
		}

		@Override
		public String toString() {
			return "WaiterThreadHandle[0x" + Long.toHexString(System.identityHashCode(this)) + " - " + (get() + ", ")
					+ "state=" + state + ", triggerEvents=0x" + Long.toHexString(triggerEvents) + "]";
		}

	}

	protected static final class TaskDependencyFutureImpl<R> implements TaskDependencyFuture<R>,
			InternalTaskDependencyFuture<R>, TaskResultDependencyHandle, InternalTaskResultDependencyHandle, Cloneable {
		private final TaskExecutorContext<?> context;
		private final ManagerTaskFutureImpl<R> taskFuture;

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private static final AtomicReferenceFieldUpdater<TaskExecutionManager.TaskDependencyFutureImpl<?>, ConcurrentPrependAccumulator<TaskOutputChangeDetector>> ARFU_outputChangeDetectors = (AtomicReferenceFieldUpdater) AtomicReferenceFieldUpdater
				.newUpdater(TaskExecutionManager.TaskDependencyFutureImpl.class, ConcurrentPrependAccumulator.class,
						"outputChangeDetectors");

		private volatile ConcurrentPrependAccumulator<TaskOutputChangeDetector> outputChangeDetectors;

		public TaskDependencyFutureImpl(TaskExecutorContext<?> context, ManagerTaskFutureImpl<R> taskFuture) {
			this.context = context;
			this.taskFuture = taskFuture;
		}

		@Override
		public TaskDependencyFutureImpl<R> clone() {
			try {
				@SuppressWarnings("unchecked")
				TaskDependencyFutureImpl<R> result = (TaskDependencyFutureImpl<R>) super.clone();
				result.outputChangeDetectors = null;
				return result;
			} catch (CloneNotSupportedException e) {
				throw new AssertionError(e);
			}
		}

		@Override
		public R get() {
			requireCalledOnTaskThread(context, false);
			return internalGetOnTaskThread();
		}

		@Override
		public R internalGetOnTaskThread() {
			TaskResultHolder<R> result = taskFuture.getWaitWithoutOutputChangeDetector(context);
			context.events.add(new TaskIdTaskEvent(TaskExecutionEventKind.WAITED_TASK, getTaskIdentifier()));
			if (this.outputChangeDetectors == null) {
				context.unaddedTaskOutputDetectorTaskDependencyFutures
						.computeIfAbsent(getTaskIdentifier(), x -> new ConcurrentPrependAccumulator<>()).add(this);
			}
			return getOutputOrThrow(result);
		}

		@Override
		public R getFinished() throws TaskExecutionFailedException, IllegalTaskOperationException {
			TaskResultHolder<R> result = taskFuture.getFinishedWithoutOutputChangeDetector(context);
			context.events.add(new TaskIdTaskEvent(TaskExecutionEventKind.FINISH_RETRIEVED_TASK, getTaskIdentifier()));
			if (this.outputChangeDetectors == null) {
				context.unaddedTaskOutputDetectorTaskDependencyFutures
						.computeIfAbsent(getTaskIdentifier(), x -> new ConcurrentPrependAccumulator<>()).add(this);
			}
			return getOutputOrThrow(result);
		}

		@Override
		public Object getModificationStamp() throws IllegalTaskOperationException {
			return taskFuture.getModificationStamp(context);
		}

		@Override
		public void setTaskOutputChangeDetector(TaskOutputChangeDetector outputchangedetector)
				throws IllegalStateException, NullPointerException {
			Objects.requireNonNull(outputchangedetector, "change detector");
			FutureState futstate = taskFuture.getFutureState();
			//TODO ensure that the task future is already finished in relation with the parents
			if (futstate.state != ManagerTaskFutureImpl.STATE_RESULT_READY) {
				throw new IllegalStateException("Task is not yet finished.");
			}
			ConcurrentPrependAccumulator<TaskOutputChangeDetector> changedetectors = this.outputChangeDetectors;
			if (changedetectors == null) {
				changedetectors = new ConcurrentPrependAccumulator<>();
				if (!ARFU_outputChangeDetectors.compareAndSet(this, null, changedetectors)) {
					changedetectors = this.outputChangeDetectors;
				}
			}
			changedetectors.add(outputchangedetector);
			TaskIdentifier taskid = getTaskIdentifier();
			context.reportFinishedTaskDependency(taskid, futstate.getTaskResult());
			context.unaddedTaskOutputDetectorTaskDependencyFutures
					.computeIfAbsent(taskid, x -> new ConcurrentPrependAccumulator<>()).add(this);
		}

		@Override
		public TaskIdentifier getTaskIdentifier() {
			return taskFuture.taskId;
		}

		@Override
		public TaskContext getTaskContext() {
			return context;
		}
	}

	private static class UserTaskFuture<R> implements TaskFuture<R>, InternalTaskFuture<R> {
		private ManagerTaskFutureImpl<R> realFuture;
		private TaskExecutorContext<?> taskContext;

		public UserTaskFuture(ManagerTaskFutureImpl<R> realFuture, TaskExecutorContext<?> taskContext) {
			this.realFuture = realFuture;
			this.taskContext = taskContext;
		}

		@Override
		public R get()
				throws TaskResultWaitingFailedException, TaskExecutionFailedException, IllegalTaskOperationException {
			requireCalledOnTaskThread(taskContext, false);
			return internalGetOnTaskThread();
		}

		@Override
		public R internalGetOnTaskThread() {
			TaskResultHolder<R> result = realFuture.get(taskContext);
			taskContext.events.add(new TaskIdTaskEvent(TaskExecutionEventKind.WAITED_TASK, getTaskIdentifier()));
			return getOutputOrThrow(result);
		}

		@Override
		public R getFinished() throws TaskExecutionFailedException, IllegalTaskOperationException {
			TaskResultHolder<R> result = realFuture.getFinished(taskContext);
			taskContext.events
					.add(new TaskIdTaskEvent(TaskExecutionEventKind.FINISH_RETRIEVED_TASK, getTaskIdentifier()));
			return getOutputOrThrow(result);
		}

		@Override
		public TaskDependencyFuture<R> asDependencyFuture() {
			return realFuture.getAsDependencyFuture(taskContext);
		}

		@Override
		public TaskIdentifier getTaskIdentifier() {
			return realFuture.getTaskIdentifier();
		}

		@Override
		public Object getModificationStamp() throws IllegalTaskOperationException {
			return realFuture.getModificationStamp(taskContext);
		}

		@Override
		public TaskContext getTaskContext() {
			return taskContext;
		}

		@Override
		public int hashCode() {
			return getTaskIdentifier().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			UserTaskFuture<?> other = (UserTaskFuture<?>) obj;
			if (!getTaskIdentifier().equals(other.getTaskIdentifier())) {
				return false;
			}
			return true;
		}

	}

	protected static final class ManagerTaskFutureImpl<R> {
		protected static final int STATE_RESULT_DEADLOCKED = 1 << 0;
		protected static final int STATE_UNSTARTED = 1 << 1;
		protected static final int STATE_INITIALIZING = 1 << 2;
		protected static final int STATE_EXECUTING = 1 << 3;
		protected static final int STATE_RESULT_READY = 1 << 4;

		protected static final int EVENT_WAITING_FOR_TASK_CHANGED = 1 << 5;
		protected static final int EVENT_ADD_ANCESTOR = 1 << 6;

		protected static final int EVENT_MASK_ALL_STATES = STATE_EXECUTING | STATE_INITIALIZING
				| STATE_RESULT_DEADLOCKED | STATE_RESULT_READY | STATE_UNSTARTED;

		//suppress the static method warnings on the member functions which are overridden in subclasses
		@SuppressWarnings("static-method")
		protected static class FutureState {
			protected final int state;

			public FutureState(int state) {
				this.state = state;
			}

			protected TaskFactory<?> getFactory() {
				return null;
			}

			protected TaskInvocationConfiguration getInvocationConfiguration() {
				return null;
			}

			protected TaskResultHolder<?> getTaskResult() {
				return null;
			}

			protected boolean isSuccessfulFinish() {
				return false;
			}

			public TaskDependencies getDependencies() {
				return null;
			}

			public Object getModificationStamp() {
				throw new UnsupportedOperationException();
			}

			@Override
			public String toString() {
				return "FutureState[state=" + state + "]";
			}
		}

		protected static class FactoryFutureState extends FutureState {
			protected final TaskFactory<?> factory;
			protected final TaskInvocationConfiguration invocationConfiguration;

			public FactoryFutureState(int state, TaskFactory<?> factory,
					TaskInvocationConfiguration invocationConfiguration) {
				super(state);
				this.factory = factory;
				this.invocationConfiguration = invocationConfiguration;
			}

			@Override
			protected TaskFactory<?> getFactory() {
				return factory;
			}

			@Override
			protected TaskInvocationConfiguration getInvocationConfiguration() {
				return invocationConfiguration;
			}

			@Override
			public String toString() {
				return getClass().getSimpleName() + "[state=" + state + ", factory=" + factory + "]";
			}
		}

		protected static class DeadlockedFutureState<R> extends FactoryFutureState implements TaskResultHolder<R> {
			private TaskIdentifier taskId;

			public DeadlockedFutureState(TaskFactory<?> factory, TaskInvocationConfiguration invocationConfiguration,
					TaskIdentifier taskId) {
				super(STATE_RESULT_DEADLOCKED, factory, invocationConfiguration);
				this.taskId = taskId;
			}

			@Override
			protected boolean isSuccessfulFinish() {
				return false;
			}

			@Override
			protected TaskResultHolder<?> getTaskResult() {
				return this;
			}

			@Override
			public TaskIdentifier getTaskIdentifier() {
				return taskId;
			}

			@Override
			public R getOutput() {
				return null;
			}

			@Override
			public List<? extends Throwable> getAbortExceptions() {
				return null;
			}

			@Override
			public Throwable getFailCauseException() {
				return ExceptionAccessInternal.createTaskExecutionDeadlockedException(taskId);
			}

		}

		protected static class UnchangedInitializingFutureState extends FactoryFutureState {
			private final TaskDependencies dependencies;

			public UnchangedInitializingFutureState(TaskFactory<?> factory,
					TaskInvocationConfiguration invocationConfiguration, TaskDependencies dependencies) {
				super(STATE_INITIALIZING, factory, invocationConfiguration);
				this.dependencies = dependencies;
			}

			@Override
			public TaskDependencies getDependencies() {
				return dependencies;
			}

			@Override
			public String toString() {
				return getClass().getSimpleName() + "["
						+ (dependencies != null ? "dependencies=" + dependencies + ", " : "")
						+ (factory != null ? "factory=" + factory : "") + "]";
			}
		}

		protected static class ExecutingFutureState extends FactoryFutureState {
			protected final TaskExecutorContext<?> taskContext;
			protected final Object modificationStamp;

			public ExecutingFutureState(TaskFactory<?> factory, TaskInvocationConfiguration invocationConfiguration,
					TaskExecutorContext<?> taskContext, Object modificationstamp) {
				super(STATE_EXECUTING, factory, invocationConfiguration);
				this.taskContext = taskContext;
				this.modificationStamp = modificationstamp;
			}

			@Override
			public TaskDependencies getDependencies() {
				return taskContext.resultDependencies;
			}

			@Override
			public final Object getModificationStamp() {
				return modificationStamp;
			}

			@Override
			public String toString() {
				return getClass().getSimpleName() + "["
						+ (taskContext != null ? "taskContext=" + taskContext + ", " : "")
						+ (modificationStamp != null ? "modificationStamp=" + modificationStamp + ", " : "")
						+ (factory != null ? "factory=" + factory + ", " : "") + "state=" + state + "]";
			}

		}

		protected static class FailedFutureState extends FactoryFutureState {
			protected final TaskResultHolder<?> resultHolder;
			protected final Object modificationStamp;

			public FailedFutureState(TaskFactory<?> factory, TaskInvocationConfiguration invocationConfiguration,
					TaskResultHolder<?> resultHolder, Object modificationStamp) {
				super(STATE_RESULT_READY, factory, invocationConfiguration);
				this.resultHolder = resultHolder;
				this.modificationStamp = modificationStamp;
			}

			@Override
			protected TaskResultHolder<?> getTaskResult() throws TaskExecutionFailedException {
				return resultHolder;
//				throw createFailException(resultHolder.getTaskIdentifier(), resultHolder.getFailCauseException(),
//						resultHolder.getAbortExceptions());
			}

			@Override
			protected boolean isSuccessfulFinish() {
				//aborting or returing a value is considered to be a successful finish
				return resultHolder.getFailCauseException() == null;
			}

			@Override
			public final Object getModificationStamp() {
				return modificationStamp;
			}

			@Override
			public TaskDependencies getDependencies() {
				return resultHolder.getDependencies();
			}

			@Override
			public String toString() {
				return "FailedFutureState[taskId=" + resultHolder.getTaskIdentifier() + "]";
			}
		}

		protected static class FinishedFutureState extends FactoryFutureState {
			protected final TaskResultHolder<?> taskResult;
			protected final Object modificationStamp;

			public FinishedFutureState(TaskFactory<?> factory, TaskInvocationConfiguration invocationConfiguration,
					TaskResultHolder<?> executionresult) {
				super(STATE_RESULT_READY, factory, invocationConfiguration);
				this.taskResult = executionresult;
				this.modificationStamp = executionresult.getDependencies().getBuildModificationStamp();
			}

			@Override
			protected TaskResultHolder<?> getTaskResult() throws TaskExecutionFailedException {
				return taskResult;
			}

			@Override
			protected boolean isSuccessfulFinish() {
				return true;
			}

			@Override
			public TaskDependencies getDependencies() {
				return taskResult.getDependencies();
			}

			@Override
			public final Object getModificationStamp() {
				return modificationStamp;
			}

			@Override
			public String toString() {
				return "SuccessfulFutureState[taskResult=" + taskResult + "]";
			}
		}

		private static final FutureState FUTURE_STATE_UNSTARTED = new FutureState(STATE_UNSTARTED);

		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<TaskExecutionManager.ManagerTaskFutureImpl, FutureState> ARFU_futureState = AtomicReferenceFieldUpdater
				.newUpdater(TaskExecutionManager.ManagerTaskFutureImpl.class, FutureState.class, "futureState");

		protected volatile FutureState futureState;

		protected final TaskIdentifier taskId;
		protected final ConcurrentLinkedQueue<WaiterThreadHandle> waitingThreads = new ConcurrentLinkedQueue<>();

		private final Lock createdByTaskIdsLock = ThreadUtils.newExclusiveLock();
		private volatile Set<TaskIdentifier> createdByTaskIds;

		protected final ConcurrentPrependAccumulator<ManagerTaskFutureImpl<?>> ancestors = new ConcurrentPrependAccumulator<>();

		public ManagerTaskFutureImpl(TaskIdentifier taskId) {
			this.taskId = taskId;
			this.futureState = FUTURE_STATE_UNSTARTED;
		}

		protected boolean isResultReady() {
			return this.futureState.state == STATE_RESULT_READY;
		}

		protected Object getModificationStamp(TaskExecutorContext<?> context) throws IllegalTaskOperationException {
			FutureState fs = this.futureState;
			int fsstate = fs.state;
			if (fsstate != ManagerTaskFutureImpl.STATE_RESULT_READY) {
				throw new IllegalTaskOperationException("Task has not yet finished.", getTaskIdentifier());
			}
			if (!isFinishedRetrievalAllowed(context)) {
				throw new IllegalTaskOperationException("No ancestor has dependency for task.", taskId);
			}
			return fs.getModificationStamp();
		}

		protected void addAncestorFuture(ManagerTaskFutureImpl<?> ancestorfuture, TaskExecutionManager execmanager) {
			if (ancestorfuture == null) {
				return;
			}
			ancestors.add(ancestorfuture);
			unparkWaitingThreads(execmanager, EVENT_ADD_ANCESTOR);
		}

		@SuppressWarnings("unchecked")
		protected TaskFactory<R> getFactory() {
			return (TaskFactory<R>) futureState.getFactory();
		}

		@SuppressWarnings("unchecked")
		protected TaskResultHolder<R> getTaskResult() throws TaskExecutionFailedException {
			return (TaskResultHolder<R>) futureState.getTaskResult();
		}

		protected boolean initializeExecution(TaskFactory<R> factory,
				TaskInvocationConfiguration invocationConfiguration, TaskExecutionManager execmanager) {
			FutureState s = this.futureState;
			if (s.state != STATE_UNSTARTED) {
				return false;
			}
			FutureState nstate = new FactoryFutureState(STATE_INITIALIZING, factory, invocationConfiguration);
			boolean set = ARFU_futureState.compareAndSet(this, s, nstate);
			if (set) {
				unparkWaitingThreads(execmanager, STATE_INITIALIZING);
			}
			return set;
		}

		protected void updateInitialization(TaskDependencies dependencies) {
			FutureState s = this.futureState;
			if (s.state != STATE_INITIALIZING) {
				throw new AssertionError("Invalid state: " + s);
			}
			FutureState nstate = new UnchangedInitializingFutureState(s.getFactory(), s.getInvocationConfiguration(),
					dependencies);
			if (!ARFU_futureState.compareAndSet(this, s, nstate)) {
				throw new AssertionError("Failed to update future state: " + s + " - " + this.futureState);
			}
			//do not unpark threads
		}

		protected boolean startExecution(TaskExecutionManager execmanager, TaskExecutorContext<?> taskcontext) {
			FutureState s = this.futureState;
			if (s.state != STATE_INITIALIZING) {
				return false;
			}
			FutureState nstate = new ExecutingFutureState(s.getFactory(), s.getInvocationConfiguration(), taskcontext,
					execmanager.buildUUID);
			boolean set = ARFU_futureState.compareAndSet(this, s, nstate);
			if (set) {
				unparkWaitingThreads(execmanager, STATE_EXECUTING);
			}
			return set;
		}

		protected void failed(TaskExecutionManager execmanager, Throwable failexception, List<Throwable> abortexception,
				TaskDependencies taskdependencies) throws IllegalStateException {
			FutureState s = this.futureState;
			SimpleTaskResultHolder<Object> taskresultholder = new SimpleTaskResultHolder<>(taskId, null, abortexception,
					failexception, taskdependencies);
			setResultState(execmanager, s, new FailedFutureState(s.getFactory(), s.getInvocationConfiguration(),
					taskresultholder, taskdependencies.getBuildModificationStamp()));
		}

		protected void finished(TaskExecutionManager execmanager, TaskExecutionResult<R> taskresult)
				throws IllegalStateException {
			finishCreatedBy(taskresult);
			FutureState s = this.futureState;
			setResultState(execmanager, s,
					new FinishedFutureState(s.getFactory(), s.getInvocationConfiguration(), taskresult));
		}

		private void setResultState(TaskExecutionManager execmanager, FutureState s, FutureState nstate) {
			if (s.state != STATE_EXECUTING && s.state != STATE_INITIALIZING) {
				if (s.state == STATE_RESULT_DEADLOCKED) {
					//deadlocked but okay
					return;
				}
				throw new AssertionError("Failed to set state for " + taskId + " (" + s + ") " + nstate);
			}
			boolean set = ARFU_futureState.compareAndSet(this, s, nstate);
			if (!set) {
				if (this.futureState.state == STATE_RESULT_DEADLOCKED) {
					//deadlocked, but its okay
					return;
				}
				throw new AssertionError("Failed to set state for " + taskId + " (" + this.futureState + ") " + nstate);
			}
			unparkWaitingThreadsForResult(execmanager);
		}

		protected TaskResultHolder<R> getWaitWithoutOutputChangeDetector(TaskExecutorContext<?> realcontext)
				throws TaskResultWaitingFailedException, TaskExecutionFailedException, IllegalTaskOperationException {
			TaskResultHolder<R> taskresult = getWaitTaskExecutionResultWithoutOutputChangeDetector(realcontext);
			return taskresult;
		}

		protected TaskResultHolder<R> getFinishedWithoutOutputChangeDetector(TaskExecutorContext<?> realcontext)
				throws TaskResultWaitingFailedException, TaskExecutionFailedException, IllegalTaskOperationException {
			FutureState s = this.futureState;
			if (s.state != STATE_RESULT_READY) {
				throw new IllegalTaskOperationException("Task has not yet finished.", taskId);
			}

			if (!isFinishedRetrievalAllowed(realcontext)) {
				throw new IllegalTaskOperationException("No ancestor has dependency for task.", taskId);
			}
			//at least one ancestor has a dependency installed to the requested task, therefore we can return with the result, as the ancestor waiting rules are not violated
			@SuppressWarnings("unchecked")
			TaskExecutionResult<R> taskresult = (TaskExecutionResult<R>) s.getTaskResult();
			realcontext.reportFinishedTaskDependency(taskId, taskresult);
			return taskresult;
		}

		protected TaskResultHolder<R> getWaitTaskExecutionResultWithoutOutputChangeDetector(
				TaskExecutorContext<?> realcontext) {
			return getWaitTaskResult(realcontext);
		}

		private static boolean isFinishedRetrievalForAncestorsAllowed(TaskIdentifier retrievetaskid,
				Set<ManagerTaskFutureImpl<?>> checkedancestors, Set<TaskIdentifier> visiteds,
				Iterable<? extends ManagerTaskFutureImpl<?>> ancestors, TaskIdentifier limittaskid) {
			for (ManagerTaskFutureImpl<?> ancestorfuture : ancestors) {
				if (!checkedancestors.add(ancestorfuture)) {
					continue;
				}
				FutureState as = ancestorfuture.getFutureState();
				TaskDependencies asdeps = as.getDependencies();
				if (asdeps == null) {
					//shouldn't really happen, but check it anyway
					//XXX should we continue here? the other checking function should still be called even though the dependencies is not available
					continue;
				}
				if (isFinishedRetrievalAllowedBasedOnEventsImpl(retrievetaskid, limittaskid, asdeps, visiteds)) {
					return true;
				}
				if (isFinishedRetrievalForAncestorsAllowed(retrievetaskid, checkedancestors, visiteds,
						ancestorfuture.ancestors, ancestorfuture.taskId)) {
					return true;
				}
			}
			return false;
		}

		private static boolean isFinishedRetrievalForAncestorsAllowedForDeltas(TaskIdentifier retrievetaskid,
				Set<TransitivelyCreatedTask> checkedancestors, Set<TaskIdentifier> visiteds,
				Iterable<? extends TransitivelyCreatedTask> ancestors, TaskIdentifier limittaskid) {
			for (TransitivelyCreatedTask ancestorfuture : ancestors) {
				if (!checkedancestors.add(ancestorfuture)) {
					continue;
				}
				TaskDependencies asdeps = ancestorfuture.getDependencies();
				if (asdeps == null) {
					//shouldn't really happen, but check it anyway
					//XXX should we continue here? the other checking function should still be called even though the dependencies is not available
					continue;
				}
				if (isFinishedRetrievalAllowedBasedOnEventsImpl(retrievetaskid, limittaskid, asdeps, visiteds)) {
					return true;
				}
				if (isFinishedRetrievalForAncestorsAllowedForDeltas(retrievetaskid, checkedancestors, visiteds,
						ancestorfuture.getCreators().values(), ancestorfuture.getTaskId())) {
					return true;
				}
			}
			return false;
		}

		protected boolean isFinishedRetrievalAllowedForDeltas(TransitivelyCreatedTask task) {
			Set<TaskIdentifier> visiteds = new HashSet<>();
			if (isFinishedRetrievalAllowedBasedOnEventsImpl(this.taskId, null, task.getDependencies(), visiteds)) {
				return true;
			}
			Set<TransitivelyCreatedTask> checkedancestors = new HashSet<>();
			return isFinishedRetrievalForAncestorsAllowedForDeltas(this.taskId, checkedancestors, visiteds,
					task.getCreators().values(), task.getTaskId());
		}

		protected boolean isFinishedRetrievalAllowedForDeltas(TaskIdentifier contexttaskid,
				TaskDependencies contextdependencies, ManagerTaskFutureImpl<?> contextfuture) {
			Iterable<? extends ManagerTaskFutureImpl<?>> contextancestors = contextfuture.ancestors;
			return isFinishedRetrievalAllowedForDeltas(contexttaskid, contextdependencies, contextancestors);
		}

		protected boolean isFinishedRetrievalAllowedForDeltas(TaskIdentifier contexttaskid,
				ManagerTaskFutureImpl<?> contextfuture) {
			Iterable<? extends ManagerTaskFutureImpl<?>> contextancestors = contextfuture.ancestors;
			return isFinishedRetrievalAllowedForDeltas(contexttaskid, contextancestors);
		}

		protected boolean isFinishedRetrievalAllowedForDeltas(TaskIdentifier contexttaskid,
				TaskDependencies contextdependencies, Iterable<? extends ManagerTaskFutureImpl<?>> contextancestors) {
			Set<TaskIdentifier> visiteds = new HashSet<>();
			if (isFinishedRetrievalAllowedBasedOnEventsImpl(this.taskId, null, contextdependencies, visiteds)) {
				return true;
			}
			Set<ManagerTaskFutureImpl<?>> checkedancestors = new HashSet<>();
			return isFinishedRetrievalForAncestorsAllowed(this.taskId, checkedancestors, visiteds, contextancestors,
					contexttaskid);
		}

		protected boolean isFinishedRetrievalAllowedForDeltas(TaskIdentifier contexttaskid,
				Iterable<? extends ManagerTaskFutureImpl<?>> contextancestors) {
			Set<TaskIdentifier> visiteds = new HashSet<>();
			Set<ManagerTaskFutureImpl<?>> checkedancestors = new HashSet<>();
			return isFinishedRetrievalForAncestorsAllowed(this.taskId, checkedancestors, visiteds, contextancestors,
					contexttaskid);
		}

		protected boolean isFinishedRetrievalAllowed(TaskExecutorContext<?> realcontext) {
			Set<TaskIdentifier> visiteds = new HashSet<>();
			if (isFinishedRetrievalAllowedBasedOnEventsImpl(this.taskId, null, realcontext.resultDependencies,
					visiteds)) {
				return true;
			}
			Set<ManagerTaskFutureImpl<?>> checkedancestors = new HashSet<>();
			return isFinishedRetrievalForAncestorsAllowed(this.taskId, checkedancestors, visiteds,
					realcontext.future.ancestors, realcontext.taskId);
		}

		protected TaskResultHolder<R> getFinished(TaskExecutorContext<?> realcontext)
				throws TaskExecutionFailedException, IllegalTaskOperationException {
			TaskResultHolder<R> result = getFinishedWithoutOutputChangeDetector(realcontext);
			realcontext.addTaskOutputChangeDetector(taskId, CommonTaskOutputChangeDetector.ALWAYS);
			return result;
		}

		protected TaskResultHolder<R> get(TaskExecutorContext<?> realcontext)
				throws TaskResultWaitingFailedException, TaskExecutionFailedException, IllegalTaskOperationException {
			TaskResultHolder<R> taskresult = getWaitTaskResult(realcontext);
			realcontext.addTaskOutputChangeDetector(taskId, CommonTaskOutputChangeDetector.ALWAYS);
			return taskresult;
		}

		protected TaskDependencyFuture<R> getAsDependencyFuture(TaskExecutorContext<?> realcontext) {
			return new TaskDependencyFutureImpl<>(realcontext, this);
		}

		protected TaskIdentifier getTaskIdentifier() {
			return taskId;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + taskId + "]";
		}

		protected int getState() {
			return this.futureState.state;
		}

		protected FutureState getFutureState() {
			return this.futureState;
		}

		protected void storeCreatedBy(TaskIdentifier taskid) {
			Lock lock = createdByTaskIdsLock;
			lock.lock();
			try {
				Set<TaskIdentifier> ids = createdByTaskIds;
				if (ids == null) {
					ids = ConcurrentHashMap.newKeySet();
					createdByTaskIds = ids;
				}
				ids.add(taskid);
			} finally {
				lock.unlock();
			}
		}

		private void finishCreatedBy(TaskExecutionResult<R> taskresult) {
			Lock lock = createdByTaskIdsLock;
			lock.lock();
			try {
				Set<TaskIdentifier> createdbystore = this.createdByTaskIds;
				Set<TaskIdentifier> taskcreatedbys = taskresult.getCreatedByTaskIds();
				if (createdbystore != null) {
					taskcreatedbys.addAll(createdbystore);
				}
				this.createdByTaskIds = taskcreatedbys;
			} finally {
				lock.unlock();
			}
		}

		private TaskResultHolder<R> getWaitTaskResult(TaskExecutorContext<?> context) {
			return context.runOnUnfinished(() -> {
				return getWaitTaskResultImpl(context);
			});
		}

		private TaskResultHolder<R> getWaitTaskResultImpl(TaskExecutorContext<?> context) {
			FutureState s = checkGetWaitingPreconditions(context);
			context.waitingForTask(this);
			TaskResultHolder<R> result;
			try {
				result = waitResultWithStateWithAncestors(context, s);
			} finally {
				context.waitedForTask(this);
			}
			context.reportWaitedTaskDependency(taskId, result);
			return result;
		}

		protected TaskResultHolder<R> getWithoutAncestorWaiting(TaskExecutorContext<?> context) {
			FutureState s = checkGetWaitingPreconditions(context);
			TaskResultHolder<R> result;
			if (s.state != STATE_RESULT_READY) {
				context.waitingForTask(this);
				try {
					result = waitResultWithoutAncestors(context.getExecutionManager(), s);
				} finally {
					context.waitedForTask(this);
				}
			} else {
				result = getTaskResult();
			}
			context.reportWaitedTaskDependency(taskId, result);
			return result;
		}

		private FutureState checkGetWaitingPreconditions(TaskExecutorContext<?> context) {
			if (context.acquiredExecutionStdIOLock != null) {
				throw new IllegalTaskOperationException("Cannot wait for tasks while the standard IO lock is acquired.",
						context.taskId);
			}
			if (context.future == this) {
				throw new IllegalTaskOperationException("Tasks cannot wait on themselves. (" + taskId + ")",
						context.getTaskId());
			}
			FutureState s = this.futureState;
			if (context.hasShortTaskCapability()) {
				//calling get in a short task
				//we can only allow get if the waited task itself is short as well
				if (s.state == STATE_UNSTARTED) {
					throw new IllegalTaskOperationException(
							"A short capable task can only wait for already started tasks. (Waiting for: " + taskId
									+ ")",
							context.getTaskId());
				}
				TaskInvocationConfiguration invocationconfig = s.getInvocationConfiguration();
				if (invocationconfig == null) {
					throw new AssertionError("Internal build system consistency error.");
				}
				if (!invocationconfig.isShort()) {
					throw new IllegalTaskOperationException(
							"A short capable task can only wait for other short capable tasks. (Waiting for: " + taskId
									+ ")",
							context.getTaskId());
				}
			}
			if (context.isUsesComputationTokens()) {
				throw new IllegalTaskOperationException(
						"A task which reports computation tokens cannot wait for other tasks. (Waiting for: " + taskId
								+ ")",
						context.getTaskId());
			}
			if (context.isInnerTasksComputationals()) {
				throw new IllegalTaskOperationException(
						"A task with computational inner tasks cannot wait for other tasks. (Waiting for: " + taskId
								+ ")",
						context.getTaskId());
			}
//			if (context.isRemoteDispatchable()) {
//				//XXX this restriction can be lifted when task waiting is confined to the task main thread
//				throw new IllegalTaskOperationException(
//						"A remote dispatchable task cannot wait for other tasks. (Waiting for: " + taskId + ")",
//						context.getTaskId());
//			}
			return s;
		}

//		protected TaskExecutionResult<R> waitResult(TaskExecutionManager execmanager)
//				throws TaskResultWaitingFailedException, TaskExecutionFailedException {
//			FutureState s = this.futureState;
//			return waitResultWithState(execmanager, s);
//		}

		protected TaskResultHolder<R> getResultIfReady()
				throws TaskResultWaitingFailedException, TaskExecutionFailedException {
			FutureState s = this.futureState;
			switch (s.state) {
				case STATE_RESULT_READY: {
					return getTaskResult();
				}
				case STATE_RESULT_DEADLOCKED: {
					throw ExceptionAccessInternal.createTaskExecutionDeadlockedException(taskId);
				}
				case STATE_INITIALIZING:
				case STATE_UNSTARTED:
				case STATE_EXECUTING: {
					return null;
				}
				default: {
					throw new AssertionError("Invalid state: " + s);
				}
			}
		}

//		protected TaskExecutionResult<R> waitResultIfExecuting(TaskExecutionManager execmanager)
//				throws TaskResultWaitingFailedException, TaskExecutionFailedException {
//			FutureState s = this.futureState;
//			return waitResultIfExecutingWithState(execmanager, s);
//		}
//
//		protected TaskExecutionResult<R> waitResultIfExecutingWithState(TaskExecutionManager execmanager, FutureState s) throws AssertionError {
//			switch (s.state) {
//				case STATE_EXECUTING:
//					waitResultPark(execmanager);
//					return getTaskResult();
//				case STATE_RESULT_READY: {
//					return getTaskResult();
//				}
//				case STATE_INITIALIZING:
//				case STATE_UNSTARTED: {
//					return null;
//				}
//				case STATE_RESULT_DEADLOCKED: {
//					throw ExceptionAccessInternal.createTaskExecutionDeadlockedException(taskId);
//				}
//				default: {
//					throw new AssertionError("Invalid state: " + s);
//				}
//			}
//		}

		private TaskResultHolder<R> waitResultWithoutAncestors(TaskExecutionManager execmanager, FutureState s)
				throws TaskResultWaitingFailedException, TaskExecutionFailedException {
			switch (s.state) {
				case STATE_EXECUTING: {
					waitResultPark(execmanager);
					return getTaskResult();
				}
				case STATE_INITIALIZING: {
					waitResultPark(execmanager);
					return getTaskResult();
				}
				case STATE_UNSTARTED: {
					waitResultPark(execmanager);
					return getTaskResult();
				}
				case STATE_RESULT_READY: {
					return getTaskResult();
				}
				case STATE_RESULT_DEADLOCKED: {
					throw ExceptionAccessInternal.createTaskExecutionDeadlockedException(taskId);
				}
				default: {
					throw new AssertionError("Invalid state: " + s);
				}
			}
		}

		private TaskResultHolder<R> waitResultWithStateWithAncestors(TaskExecutorContext<?> taskcontext,
				FutureState s) {
			switch (s.state) {
				case STATE_EXECUTING: {
					waitResultPark(taskcontext.getExecutionManager());
					return getTaskResultForContextWaitAncestors(taskcontext);
				}
				case STATE_INITIALIZING: {
					waitResultPark(taskcontext.getExecutionManager());
					return getTaskResultForContextWaitAncestors(taskcontext);
				}
				case STATE_UNSTARTED: {
					waitResultPark(taskcontext.getExecutionManager());
					return getTaskResultForContextWaitAncestors(taskcontext);
				}
				case STATE_RESULT_READY: {
					return getTaskResultForContextWaitAncestors(taskcontext);
				}
				case STATE_RESULT_DEADLOCKED: {
					throw ExceptionAccessInternal.createTaskExecutionDeadlockedException(taskId);
				}
				default: {
					throw new AssertionError("Invalid state: " + s);
				}
			}
		}

		/**
		 * A snapshot of the ancestors of a task future.
		 */
		private static final class AncestorWaitAncestorState {
			/**
			 * The first element in {@link #ancestorIterable}
			 * 
			 * @see PeekableIterable#peek()
			 */
			public ManagerTaskFutureImpl<?> firstAncestor;
			/**
			 * The iterable of the direct ancestors of the future.
			 * 
			 * @see ManagerTaskFutureImpl#ancestors
			 */
			public PeekableIterable<ManagerTaskFutureImpl<?>> ancestorIterable;

			public AncestorWaitAncestorState(PeekableIterable<ManagerTaskFutureImpl<?>> ancestorIterable) {
				this.ancestorIterable = ancestorIterable;
				this.firstAncestor = ancestorIterable.peek();
			}

			public AncestorWaitAncestorState(ManagerTaskFutureImpl<?> future) {
				this(future.ancestors.iterable());
			}
		}

		private TaskResultHolder<R> getTaskResultForContextWaitAncestors(TaskExecutorContext<?> callertaskcontext) {
			//this function is only called after the task has been waited for
			//   at least one of them has ancestors, as a root task cannot wait for an other, because they can't be running in the same execution
			ManagerTaskFutureImpl<?> callerfuture = callertaskcontext.future;
			if (TestFlag.ENABLED) {
				if (ancestors.isEmpty() && callerfuture.ancestors.isEmpty()) {
					throw new AssertionError("Neither caller nor waiter have ancestors.");
				}
			}

			PeekableIterable<ManagerTaskFutureImpl<?>> ancestorsiterable = ancestors.iterable();
			if (ancestorsiterable.isEmpty()) {
				//this is a root task, because no ancestors, and the state is already RESULT_READY, it is waitable without checking ancestors
				return getTaskResult();
			}
			PeekableIterable<ManagerTaskFutureImpl<?>> callerancestorsiterable = callerfuture.ancestors.iterable();

			//a collection of all known futures and their current ancestor states
			Map<ManagerTaskFutureImpl<?>, AncestorWaitAncestorState> ancestorstates = new HashMap<>();
			AncestorWaitAncestorState callerancestorstate = new AncestorWaitAncestorState(callerancestorsiterable);
			AncestorWaitAncestorState thisancestorstate = new AncestorWaitAncestorState(ancestorsiterable);

			ancestorstates.put(this, thisancestorstate);
			ancestorstates.put(callerfuture, callerancestorstate);

			//contains ALL ancestors (parents, grandparents, etc...) of the caller, and the caller as well.
			Set<ManagerTaskFutureImpl<?>> callerancestors;
			if (!callerancestorsiterable.isEmpty()) {
				callerancestors = callerfuture.getAllAncestors(callerancestorstate, ancestorstates);
				if (callerancestors.contains(this)) {
					//the waited is a parent of the caller, it is waitable
					return getTaskResult();
				}
			} else {
				callerancestors = new HashSet<>();
				callerancestors.add(callerfuture);
			}
			//iterate over the direct ancestors of the WAITED task
			//this basically checks is 0 length waiter paths exist. if so, then we're done.
			for (ManagerTaskFutureImpl<?> a : ancestorsiterable) {
				//the waited is a direct child of an ancestor of the waiter
				//there exists a task A which directly created the waited task W
				//    the waiter task X can wait for W as they have a common ancestor
				//    and W doesn't have any intermediate ancestors on the way to A
				if (callerancestors.contains(a)) {
					//caller and waited have a direct common ancestor
					//no need to wait for anything
					return getTaskResult();
				}
			}

			//a set of futures on which we are waiting
			//if any of the futures becomes RESULT_READY then we need to recheck the condition
			//any ancestor addition to any of the futures also cause a recheck 
			Set<ManagerTaskFutureImpl<?>> ancestorwaitfutures = new HashSet<>();
			//to be notified about caller future ancestor addition
			ancestorwaitfutures.add(callerfuture);
			ancestorwaitfutures.add(this);

			//collection of ancestor paths
			//each path is a set of futures 
			//   each future in a path need to be in RESULT_READY state for the task result to be retrievable
			//   at least one path needs to be satisfied
			Set<Set<ManagerTaskFutureImpl<?>>> ancestorwaiterpaths = new HashSet<>();
			collectAncestorWaiterPaths(callerancestors, thisancestorstate, ancestorstates, wp -> {
				ancestorwaiterpaths.add(wp);
				ancestorwaitfutures.addAll(wp);
			});

			WaiterThreadHandle threadhandle = new WaiterThreadHandle(STATE_RESULT_READY | EVENT_ADD_ANCESTOR);
			BooleanSupplier conditionchecker = new BooleanSupplier() {
				private final Set<ManagerTaskFutureImpl<?>> recollectBuffer = new HashSet<>();
				private boolean first = true;

				@Override
				public boolean getAsBoolean() {
					//don't need to check ancestor changes in the first run, as that is directly
					//called at the start of waitCondition function
					//this also prevents that we add the thread handle to a single future.waitingThreads
					//collection multiple times in the waitCondition function as well as part of the ancestor discovery below
					if (!first) {
						//loop until there are no more changes detected, as adding waiter threads
						//    can cause race conditions and missing of updates
						while (true) {
							//update all ancestor states with the fresh ancestor iterables, so we have an up to date snapshot
							boolean hadanyancestorchange = false;
							for (Entry<ManagerTaskFutureImpl<?>, AncestorWaitAncestorState> entry : ancestorstates
									.entrySet()) {
								PeekableIterable<ManagerTaskFutureImpl<?>> niterable = entry.getKey().ancestors
										.iterable();
								AncestorWaitAncestorState state = entry.getValue();
								ManagerTaskFutureImpl<?> currentfirst = niterable.peek();
								if (currentfirst != state.firstAncestor) {
									state.firstAncestor = currentfirst;
									state.ancestorIterable = niterable;
									hadanyancestorchange = true;
								}
							}

							if (!hadanyancestorchange) {
								//no ancestors changed whatsoever, so this is probably a RESULT_READY notification (or this is not the first loop)
								break;
							}
							recollectBuffer.clear();
							//recollect all ancestors
							callerfuture.collectAllAncestors(callerancestorstate, ancestorstates, recollectBuffer);
							callerancestors.addAll(recollectBuffer);

							//same checking as above
							for (ManagerTaskFutureImpl<?> aa : thisancestorstate.ancestorIterable) {
								if (callerancestors.contains(aa)) {
									return true;
								}
							}
							//some ancestor was added, collect the waiter paths again, and add us to the waiting threads of the newly found futures
							collectAncestorWaiterPaths(callerancestors, thisancestorstate, ancestorstates, wp -> {
								if (ancestorwaiterpaths.add(wp)) {
									for (ManagerTaskFutureImpl<?> f : wp) {
										if (ancestorwaitfutures.add(f)) {
											f.waitingThreads.add(threadhandle);
										}
									}
								}
							});
						}
					} else {
						first = false;
					}

					return isAtLeastOneWaitherPathResultReady();
				}

				private boolean isAtLeastOneWaitherPathResultReady() {
					for (Iterable<ManagerTaskFutureImpl<?>> waiterpath : ancestorwaiterpaths) {
						if (isAllFutureResultReady(waiterpath)) {
							return true;
						}
					}
					return false;
				}
			};
			waitCondition(callertaskcontext.getExecutionManager(), threadhandle, ancestorwaitfutures, conditionchecker);
			return getTaskResult();
		}

		private static boolean isAllFutureResultReady(Iterable<? extends ManagerTaskFutureImpl<?>> futures) {
			for (ManagerTaskFutureImpl<?> fut : futures) {
				if (!fut.isResultReady()) {
					return false;
				}
			}
			return true;
		}

		/**
		 * @param callerancestors
		 *            ALL of the ancestors of the caller task
		 * @param ancestorstate
		 *            The ancestor state of the WAITED task
		 * @param ancestorstates
		 *            The holder of current ancestor states
		 * @param result
		 *            The result consumer
		 */
		private static void collectAncestorWaiterPaths(Set<ManagerTaskFutureImpl<?>> callerancestors,
				AncestorWaitAncestorState ancestorstate,
				Map<ManagerTaskFutureImpl<?>, AncestorWaitAncestorState> ancestorstates,
				Consumer<? super Set<ManagerTaskFutureImpl<?>>> result) {
			//calls the result consumer for each ancestor path found
			for (ManagerTaskFutureImpl<?> a : ancestorstate.ancestorIterable) {
				//each path starts with a direct ancestor
				LinkedHashSet<ManagerTaskFutureImpl<?>> waiterpath = new LinkedHashSet<>();
				waiterpath.add(a);
				AncestorWaitAncestorState aancestorstate = ancestorstates.computeIfAbsent(a,
						AncestorWaitAncestorState::new);
				collectAncestorWaiterPathsImpl(callerancestors, waiterpath, aancestorstate, ancestorstates, result);
			}
		}

		/**
		 * Collects ancestor waiter paths.
		 * <p>
		 * The method works by recursively going up the parent chain of the WAITED task, and when any common parent is
		 * found with the CALLER task, then a waiter path is found. (The path won't contain the common ancestor)
		 * 
		 * @param callerancestors
		 *            ALL of the ancestors of the caller task
		 * @param waiterpath
		 *            The waiter path so far, containing the currently processed ancestor
		 * @param ancestorstate
		 *            The currently processed ancestor (some parent of the WAITED task)
		 * @param ancestorstates
		 *            The holder of current ancestor states
		 * @param result
		 *            The result consumer
		 */
		private static void collectAncestorWaiterPathsImpl(Set<ManagerTaskFutureImpl<?>> callerancestors,
				LinkedHashSet<ManagerTaskFutureImpl<?>> waiterpath, AncestorWaitAncestorState ancestorstate,
				Map<ManagerTaskFutureImpl<?>, AncestorWaitAncestorState> ancestorstates,
				Consumer<? super Set<ManagerTaskFutureImpl<?>>> result) {
			Iterable<ManagerTaskFutureImpl<?>> ancestorsiterable = ancestorstate.ancestorIterable;
			{
				Iterator<ManagerTaskFutureImpl<?>> it = ancestorsiterable.iterator();
				if (!it.hasNext()) {
					result.accept(waiterpath);
					return;
				}
				if (ObjectUtils.containsAny(callerancestors, it)) {
					//at least one of the ancestors are in the callerancestors
					//this means that we are not required to wait for the ancestors of the common ancestor
					//so we can just use this path
					result.accept(waiterpath);
					return;
				}
			}
			Iterator<ManagerTaskFutureImpl<?>> it = ancestorsiterable.iterator();

			//handling of the first is done as the last step of this method
			//so we can copy the parameter waiter path without this element
			ManagerTaskFutureImpl<?> first = it.next();

			outer_loop:
			while (it.hasNext()) {
				ManagerTaskFutureImpl<?> n = it.next();
				LinkedHashSet<ManagerTaskFutureImpl<?>> npath = new LinkedHashSet<>(waiterpath);
				while (!npath.add(n)) {
					//failed to add ancestor to the waiter path, as it already contains it
					//(possible circular creation)
					//use an inner loop to advance to the next, to create fewer set copies
					if (!it.hasNext()) {
						break outer_loop;
					}
					n = it.next();
				}
				AncestorWaitAncestorState nancestorstate = ancestorstates.computeIfAbsent(n,
						AncestorWaitAncestorState::new);
				collectAncestorWaiterPathsImpl(callerancestors, npath, nancestorstate, ancestorstates, result);
			}
			if (waiterpath.add(first)) {
				AncestorWaitAncestorState fancestorstate = ancestorstates.computeIfAbsent(first,
						AncestorWaitAncestorState::new);
				collectAncestorWaiterPathsImpl(callerancestors, waiterpath, fancestorstate, ancestorstates, result);
			}
		}

		private Set<ManagerTaskFutureImpl<?>> getAllAncestors(AncestorWaitAncestorState ancestorstate,
				Map<ManagerTaskFutureImpl<?>, AncestorWaitAncestorState> ancestorstates) {
			Set<ManagerTaskFutureImpl<?>> result = new HashSet<>();
			collectAllAncestors(ancestorstate, ancestorstates, result);
			return result;
		}

		private boolean collectAllAncestors(AncestorWaitAncestorState ancestorstate,
				Map<ManagerTaskFutureImpl<?>, AncestorWaitAncestorState> ancestorstates,
				Set<ManagerTaskFutureImpl<?>> result) {
			if (!result.add(this)) {
				return false;
			}
			for (ManagerTaskFutureImpl<?> f : ancestorstate.ancestorIterable) {
				AncestorWaitAncestorState fas = ancestorstates.computeIfAbsent(f, AncestorWaitAncestorState::new);
				f.collectAllAncestors(fas, ancestorstates, result);
			}
			return true;
		}

		private void waitResultPark(TaskExecutionManager execmanager) {
			waitCondition(execmanager, new WaiterThreadHandle(STATE_RESULT_READY), ImmutableUtils.singletonSet(this),
					this::isResultReady);
		}

		public static void addWaitingFutureInCondition(ManagerTaskFutureImpl<?> fut, WaiterThreadHandle waiter) {
			fut.waitingThreads.add(waiter);
		}

		public static void waitCondition(TaskExecutionManager execmanager, WaiterThreadHandle waiter,
				Iterable<? extends ManagerTaskFutureImpl<?>> futures, BooleanSupplier condition) {
			if (TestFlag.ENABLED) {
				if (Thread.currentThread() != waiter.get()) {
					throw new AssertionError("thread identity mismatch.");
				}
				if (waiter.state != WaiterThreadHandle.STATE_INITIAL) {
					throw new AssertionError("invalid waiter thread state.");
				}
			}
			//check the condition
			//(here we can ignore the exceptions from the condition evaluation
			//as the handle hasn't been used anywhere. it can be reused by the caller if needed
			if (condition.getAsBoolean()) {
				//set the state directly, as the handle hadn't been used anywere so far
				waiter.state = WaiterThreadHandle.STATE_FINISHED;
				return;
			}
			//add the current thread as a waiter for the futures
			Iterator<? extends ManagerTaskFutureImpl<?>> initit = futures.iterator();
			if (!initit.hasNext()) {
				throw new IllegalArgumentException("No futures.");
			}

			//we no longer need this iterable, let it be garbage collected
			futures = null;

			//store the first future in case we need it in the thrown exception
			ManagerTaskFutureImpl<?> firstfuture = initit.next();
			for (ManagerTaskFutureImpl<?> fut = firstfuture;; fut = initit.next()) {
				fut.waitingThreads.add(waiter);
				if (!initit.hasNext()) {
					break;
				}
			}

			// we don't need to check the condition here again, as we will check it again before we park the thread

			Thread currentthread = waiter.get();
			//go ahead with locking, deadlock detecting, and others
			while (true) {
				final boolean addwaitingthread;
				state_updater_loop:
				while (true) {
					int s = waiter.state;
					switch (s) {
						case WaiterThreadHandle.STATE_INITIAL: {
							if (WaiterThreadHandle.AIFU_state.compareAndSet(waiter, WaiterThreadHandle.STATE_INITIAL,
									WaiterThreadHandle.STATE_WAITING)) {
								addwaitingthread = true;
								break state_updater_loop;
							}
							break;
						}
						case WaiterThreadHandle.STATE_WAITING: {
							//already waiting, but was unparked
							//could be because of deadlock, or was just woken up
							//will check the condition below again
							addwaitingthread = false;
							break state_updater_loop;
						}
						case WaiterThreadHandle.STATE_NOTIFIED: {
							//the handle was notified. check condition below, and set back the state to waiting if fails
							if (WaiterThreadHandle.AIFU_state.compareAndSet(waiter, WaiterThreadHandle.STATE_NOTIFIED,
									WaiterThreadHandle.STATE_WAITING)) {
								addwaitingthread = true;
								break state_updater_loop;
							}
							break;
						}
						/*
						case WaiterThreadHandle.STATE_FINISHED: //can't happen
						*/
						default: {
							throw new AssertionError(s);
						}
					}
				}

				//we are in WAITING state here
				WaitingThreadCounter wtc;
				if (addwaitingthread) {
					//check the condition again before adding the waiting thread count
					//we don't want to increase the waiting thread count before checking the condition, as that could
					//cause a deadlock to be detected in other threads even if the condition is satisfied

					boolean condval;
					try {
						condval = condition.getAsBoolean();
					} catch (Throwable e) {
						// always finish so the handle is not left in an inconsistent state
						waiter.finish(execmanager, true);
						throw e;
					}
					if (condval) {
						waiter.finish(execmanager, true);
						return;
					}
					//add the waiting thread count
					//we don't need to check the condition again,
					//    if something related to the condition changed, then we get notified
					//    and the waiting thread count is decreased before the notification
					//    which prevents the deadlock.
					//    we check that notification right away in the next loop
					wtc = execmanager.addWaitingThreadCount(1);
				} else {
					wtc = execmanager.waitingThreadCounter;
					//simply check the condition
					//we need to check the condition AFTER we've retrieved the waiting thread count
					//    this is in order to avoid the concurrency issue when this thread gets unscheduled
					//    just before retrieving the waiting thread count, then another thread notifies this and exists
					//    and we get the WTC. this would cause us to forget to check the condition, and use a WTC 
					//    without the condition check
					boolean condval;
					try {
						condval = condition.getAsBoolean();
					} catch (Throwable e) {
						// always finish so the handle is not left in an inconsistent state
						waiter.finish(execmanager, false);
						throw e;
					}
					if (condval) {
						waiter.finish(execmanager, false);
						return;
					}
				}
				if (wtc == WAITING_THREAD_COUNTER_DEADLOCKED) {
					waiter.finish(execmanager, false);
					throw ExceptionAccessInternal
							.createTaskExecutionDeadlockedException(firstfuture.getTaskIdentifier());
				}
				int activecount = wtc.runningThreadCount;
				if (TestFlag.ENABLED) {
					if (wtc.waitingThreadCount > activecount) {
						throw new AssertionError(
								"Inconsistent thread counts: " + wtc.waitingThreadCount + " - " + activecount);
					}
				}
				if (wtc.waitingThreadCount == activecount) {
					if (execmanager.deadlocked(wtc)) {
						waiter.finish(execmanager, false);
						throw ExceptionAccessInternal
								.createTaskExecutionDeadlockedException(firstfuture.getTaskIdentifier());
					}
					//else the deadlock application didnt go through due to transient operations, 
					//    continue and check again later
				}
				if (currentthread.isInterrupted()) {
					waiter.finish(execmanager, false);
					throw ExceptionAccessInternal
							.createTaskResultWaitingInterruptedException(firstfuture.getTaskIdentifier());
				}
				//park the thread and recheck
				LockSupport.park();
			}
		}

		protected void deadlocked() {
			for (FutureState s = this.futureState; s.state == STATE_UNSTARTED || s.state == STATE_EXECUTING
					|| s.state == STATE_INITIALIZING; s = this.futureState) {
				if (ARFU_futureState.compareAndSet(this, s,
						new DeadlockedFutureState<>(s.getFactory(), s.getInvocationConfiguration(), this.taskId))) {
					ConcurrentLinkedQueue<WaiterThreadHandle> threadqueue = waitingThreads;
					for (WaiterThreadHandle t; (t = threadqueue.poll()) != null;) {
						LockSupport.unpark(t.get());
					}
					break;
				}
			}
		}

		protected void unparkWaitingThreads(TaskExecutionManager execmanager, int event) {
			for (Iterator<WaiterThreadHandle> it = waitingThreads.iterator(); it.hasNext();) {
				WaiterThreadHandle t = it.next();
				if ((t.triggerEvents & event) == 0) {
					continue;
				}
				if (!t.unparkNotify(execmanager)) {
					//thread handle finished
					it.remove();
				}
			}
		}

		protected void unparkWaitingThreadsForResult(TaskExecutionManager execmanager) {
			for (Iterator<WaiterThreadHandle> it = waitingThreads.iterator(); it.hasNext();) {
				WaiterThreadHandle t = it.next();
				int triggerevents = t.triggerEvents;
				if ((triggerevents & STATE_RESULT_READY) == 0) {
					//the thread is not interested in the RESULT_READY event
					continue;
				}
				if (!t.unparkNotify(execmanager) || triggerevents == STATE_RESULT_READY) {
					//thread handle finished
					//  OR
					//only interested in the RESULT_READY event, so it can be removed
					it.remove();
				}
			}
		}

		protected boolean unparkOneWaitingThread(TaskExecutionManager execmanager) {
			for (Iterator<WaiterThreadHandle> it = waitingThreads.iterator(); it.hasNext();) {
				WaiterThreadHandle t = it.next();
				if (!t.unparkNotify(execmanager)) {
					//thread handle finished, continue attempting to unpark the next one
					it.remove();
				} else {
					return true;
				}
			}
			return false;
		}

		public Entry<SakerPath, ScriptPosition> internalGetOriginatingBuildFile(TaskExecutionManager manager) {
			Entry<SakerPath, ScriptPosition> presentposition = manager.taskIdScriptPositionsCache.get(this.taskId);
			if (presentposition != null) {
				return presentposition;
			}
			Entry<SakerPath, ScriptPosition> result = null;
			if (this.taskId instanceof BuildFileTargetTaskIdentifier) {
				result = ImmutableUtils
						.makeImmutableMapEntry(((BuildFileTargetTaskIdentifier) this.taskId).getFilePath(), null);
			} else {
				for (ManagerTaskFutureImpl<?> ancestorfuture : ancestors) {
					Entry<SakerPath, ScriptPosition> ancestorresult = ancestorfuture
							.internalGetOriginatingBuildFile(manager);
					if (ancestorresult == null) {
						continue;
					}
					ScriptPosition thisscriptpos = manager.executionContext
							.internalGetTaskScriptPosition(ancestorresult.getKey(), this.taskId);
					if (thisscriptpos != null) {
						result = ImmutableUtils.makeImmutableMapEntry(ancestorresult.getKey(), thisscriptpos);
					} else {
						result = ancestorresult;
					}
					break;
				}
			}
			if (result == null) {
				return null;
			}
			Entry<SakerPath, ScriptPosition> prev = manager.taskIdScriptPositionsCache.putIfAbsent(taskId, result);
			if (prev != null) {
				return prev;
			}
			return result;
		}

		private String processPrintedLineVariables(TaskExecutionManager manager, String line) {
			return processPrintedLineVariables(manager, line, 0);
		}

		private String processPrintedLineVariables(TaskExecutionManager manager, String line, int idx) {
			StringBuilder sb = new StringBuilder(line.length() * 3 / 2);
			while (true) {
				int markeridx = line.indexOf(PRINTED_LINE_VARIABLES_MARKER_CHAR, idx);
				if (markeridx < 0) {
					sb.append(line, idx, line.length());
					break;
				}
				if (markeridx > 0 && line.charAt(markeridx - 1) == '\\') {
					//escaped marker char
					sb.append(line, idx, markeridx - 1);
					sb.append(PRINTED_LINE_VARIABLES_MARKER_CHAR);
					idx = markeridx + 1;
					continue;
				}
				//append until the first marker
				sb.append(line, idx, markeridx);
				if (line.startsWith(PRINTED_LINE_VAR_LOG_TASK_SCRIPT_POSITION, markeridx + 1)) {
					Entry<SakerPath, ScriptPosition> origins = internalGetOriginatingBuildFile(manager);
					if (origins != null) {
						SakerPath path = origins.getKey();
						int lineidx = -1;
						int lineStart = -1;
						int lineEnd = -1;
						ScriptPosition scriptpos = origins.getValue();
						if (scriptpos != null) {
							lineidx = scriptpos.getLine();
							lineStart = scriptpos.getLinePosition();
							lineEnd = lineStart + scriptpos.getLength();
						}

						SakerPath workingdir = manager.executionContext.getExecutionWorkingDirectoryPath();
						if (path.startsWith(workingdir)) {
							sb.append(path.subPath(workingdir.getNameCount()));
						} else {
							sb.append(path);
						}
						if (lineidx >= 0) {
							sb.append(':');
							sb.append(lineidx + 1);
							if (lineStart >= 0) {
								sb.append(':');
								sb.append(lineStart + 1);
								sb.append('-');
								sb.append(lineEnd);
							}
						}
						sb.append(": ");
					}
					idx = markeridx + PRINTED_LINE_VAR_LOG_TASK_SCRIPT_POSITION.length() + 1;
					continue;
				}
				idx = markeridx + 1;
				continue;
			}
			return sb.toString();
		}
	}

	private static final Set<DeltaType> FILE_CHANGE_DELTA_TYPES = EnumSet.of(DeltaType.INPUT_FILE_ADDITION,
			DeltaType.INPUT_FILE_CHANGE, DeltaType.OUTPUT_FILE_CHANGE);

	private static final WaitingThreadCounter WAITING_THREAD_COUNTER_DEADLOCKED = new WaitingThreadCounter(0, 0);
	private static final WaitingThreadCounter WAITING_THREAD_COUNTER_ZERO = new WaitingThreadCounter(0, 0);

	/**
	 * Holder object that contains the currently running and waiting thread counts.
	 */
	protected static final class WaitingThreadCounter {
		public final int runningThreadCount;
		public final int waitingThreadCount;

		public WaitingThreadCounter(int runningThreadCount, int waitingThreadCount) {
			this.runningThreadCount = runningThreadCount;
			this.waitingThreadCount = waitingThreadCount;
			if (TestFlag.ENABLED
					&& (this.waitingThreadCount > this.runningThreadCount || this.runningThreadCount < 0)) {
				//the waitingThreadCount can be less than 0 in edge cases when the removal happens before the addition
				throw new AssertionError(this);
			}
		}

		public WaitingThreadCounter addWaiting(int count) {
			return new WaitingThreadCounter(this.runningThreadCount, this.waitingThreadCount + count);
		}

		public WaitingThreadCounter removeWaiting(int count) {
			return new WaitingThreadCounter(this.runningThreadCount, this.waitingThreadCount - count);
		}

		public WaitingThreadCounter addRunning(int count) {
			return new WaitingThreadCounter(this.runningThreadCount + count, this.waitingThreadCount);
		}

		public WaitingThreadCounter removeRunning(int count) {
			return new WaitingThreadCounter(this.runningThreadCount - count, this.waitingThreadCount);
		}

		public WaitingThreadCounter addRunningAndWaiting(int count) {
			return new WaitingThreadCounter(this.runningThreadCount + count, this.waitingThreadCount + count);
		}

		public WaitingThreadCounter removeRunningAndWaiting(int count) {
			return new WaitingThreadCounter(this.runningThreadCount - count, this.waitingThreadCount - count);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + runningThreadCount;
			result = prime * result + waitingThreadCount;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			WaitingThreadCounter other = (WaitingThreadCounter) obj;
			if (runningThreadCount != other.runningThreadCount)
				return false;
			if (waitingThreadCount != other.waitingThreadCount)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "WaitingThreadCounter[runningThreadCount=" + runningThreadCount + ", waitingThreadCount="
					+ waitingThreadCount + "]";
		}
	}

	private static final AtomicReferenceFieldUpdater<TaskExecutionManager, WaitingThreadCounter> ARFU_waitingThreadCounter = AtomicReferenceFieldUpdater
			.newUpdater(TaskExecutionManager.class, WaitingThreadCounter.class, "waitingThreadCounter");

	private volatile WaitingThreadCounter waitingThreadCounter = WAITING_THREAD_COUNTER_ZERO;

	private final ConcurrentHashMap<TaskIdentifier, ManagerTaskFutureImpl<?>> taskIdFutures;
	private final ConcurrentHashMap<TaskIdentifier, Entry<SakerPath, ScriptPosition>> taskIdScriptPositionsCache = new ConcurrentHashMap<>();

	private final Map<TaskIdentifier, TaskExecutionResult<?>> taskIdTaskResults;

	private final ConcurrentHashMap<TaskIdentifier, TaskExecutionResult<?>> cacheableTaskResults = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<TaskIdentifier, TaskExecutionResult<?>> resultTaskIdTaskResults;
	private Map<TaskIdentifier, TaskExecutionResult<?>> abandonedTaskIdResults = Collections.emptyMap();
	private final ConcurrentHashMap<TaskIdentifier, Boolean> checkedHasAnyDeltas = new ConcurrentHashMap<>();

	protected UUID buildUUID;

	private ThreadGroup executionThreadGroup;
	protected ThreadWorkPool generalExecutionThreadWorkPool;
	private final ConcurrentPrependAccumulator<TaskExecutionThread> taskThreads = new ConcurrentPrependAccumulator<>();

	private final BuildTaskResultDatabase initTaskResults;

	private boolean executedAnyTask = false;

	private BuildCacheAccessor buildCache;

	protected ExecutionProgressMonitor progressMonitor;

	private volatile boolean cancelledByInterruption = false;

	private SakerPath buildDirectoryPath;
	private SakerDirectory buildSakerDirectory;

	private ConcurrentHashMap<TaskIdentifier, SpawnedResultTask> runSpawnedTasks = new ConcurrentHashMap<>();
	private Set<IDEConfiguration> runIdeConfigurations = ConcurrentHashMap.newKeySet();

	private TaskInvocationManager invocationManager;

	private final ParallelRunner fileDeltaParallelRunner = ThreadUtils.parallelRunner().setNamePrefix("File-delta-");

	protected ExecutionContextImpl executionContext;

	protected InternalBuildTrace buildTrace;

	protected ConcurrentPrependAccumulator<Entry<TaskIdentifier, TaskException>> taskRunningFailureExceptions = new ConcurrentPrependAccumulator<>();

	public TaskExecutionManager(BuildTaskResultDatabase taskresults) {
		this.initTaskResults = taskresults;
		this.taskIdTaskResults = taskresults == null ? Collections.emptyMap()
				: new HashMap<>(taskresults.getTaskIdTaskResults());
		this.resultTaskIdTaskResults = new ConcurrentHashMap<>(Math.max(64, taskIdTaskResults.size() * 4 / 3));
		this.taskIdFutures = new ConcurrentHashMap<>(Math.max(64, taskIdTaskResults.size() * 4 / 3));
	}

	public BuildTaskResultDatabase getTaskResults(
			NavigableMap<SakerPath, ScriptInformationProvider> scriptInformationProviders) {
		if (initTaskResults != null && !executedAnyTask) {
			return initTaskResults;
		}
		return BuildTaskResultDatabase.create(resultTaskIdTaskResults, abandonedTaskIdResults, cacheableTaskResults,
				scriptInformationProviders);
	}

	public TaskResultCollectionImpl getResultCollection() {
		return new TaskResultCollectionImpl(runSpawnedTasks, runIdeConfigurations);
	}

	public Map<TaskIdentifier, TaskExecutionResult<?>> getResultTaskIdTaskResults() {
		return resultTaskIdTaskResults;
	}

	public void execute(TaskFactory<?> factory, TaskIdentifier taskid, ExecutionContextImpl executioncontext,
			Collection<? extends TaskInvoker> taskinvokers, BuildCacheAccessor buildcache,
			InternalBuildTrace buildtrace) throws MultiTaskExecutionFailedException {
		Objects.requireNonNull(taskid, "taskid");
		Objects.requireNonNull(factory, "factory");
		Objects.requireNonNull(executioncontext, "execution context");

		this.executionContext = executioncontext;

		this.progressMonitor = executioncontext.getProgressMonitor();
		buildUUID = UUID.randomUUID();
		executionThreadGroup = new ThreadGroup("Task execution: " + taskid);
		ThreadGroup clusterInteractionThreadGroup = new ThreadGroup("Cluster management");
		//XXX maybe set executionThreadGroup to daemon thread group?
		this.buildCache = buildcache;
		this.buildTrace = buildtrace;

		buildDirectoryPath = executioncontext.getBuildDirectoryPath();
		buildSakerDirectory = executioncontext.getExecutionBuildDirectory();

		boolean interrupted = false;
		boolean failedexecution = false;
		TaskInvocationManager invocationmanager = null;
		try {
			invocationmanager = createTaskInvocationManager(executioncontext, taskinvokers,
					clusterInteractionThreadGroup);
			this.invocationManager = invocationmanager;

			MultiTaskExecutionFailedException texc = null;
			try (ThreadWorkPool execpool = ThreadUtils.newFixedWorkPool(new ThreadGroup("Task worker: " + taskid),
					"tasks-worker-")) {
				generalExecutionThreadWorkPool = execpool;

				runSpawnedTasks.put(taskid, new SpawnedResultTask(taskid));

				parallelRunnerStrategy(taskid, createTaskThreadName(factory)).execute(() -> {
					TaskResultHolder<?> taskres = executeImpl(factory, taskid, executioncontext, null,
							DEFAULT_EXECUTION_PARAMETERS, null).getTaskResult();
					if (taskres instanceof TaskExecutionResult<?>) {
						//if output may not be a TaskExecutionResult if it failed
						((TaskExecutionResult<?>) taskres).setRootTask(true);
					}
					//throw an exception if the execution fails
//					getOutputOrThrow(taskres);
				});
				for (TaskExecutionThread t; (t = taskThreads.take()) != null;) {
					while (true) {
						try {
							t.join();
							Throwable exc = t.getException();
							if (exc != null) {
								if (texc == null) {
									texc = ExceptionAccessInternal.createMultiTaskExecutionFailedException(taskid);
								}
								if (exc instanceof TaskException) {
									ExceptionAccessInternal.addMultiTaskExecutionFailedCause(texc,
											t.getTaskIdentifier(), (TaskException) exc);
								} else {
									//this should never happen, as we only throw TaskExecutionException from the executor threads, but include this nonetheless
									ExceptionAccessInternal.addMultiTaskExecutionFailedCause(texc,
											t.getTaskIdentifier(),
											new TaskThreadManipulationException(
													"Unexpected exception type caught from task executor thread.",
													exc));
								}
							}
							break;
						} catch (InterruptedException e) {
							if (!interrupted) {
								interrupted = true;
								cancelledByInterruption = true;
							}
							//try to join again
						}
					}
				}
			} catch (ParallelExecutionException e) {
				if (texc == null) {
					texc = ExceptionAccessInternal.createMultiTaskExecutionFailedException(taskid);
				}
				texc.addSuppressed(e);
			}
			{
				Entry<TaskIdentifier, TaskException> e = taskRunningFailureExceptions.take();
				if (e != null) {
					if (texc == null) {
						texc = ExceptionAccessInternal.createMultiTaskExecutionFailedException(taskid);
					}
					do {
						ExceptionAccessInternal.addMultiTaskExecutionFailedCause(texc, e.getKey(), e.getValue());
					} while ((e = taskRunningFailureExceptions.take()) != null);
				}
			}
			if (texc != null) {
				failedexecution = true;
				throw texc;
			}
			if (TestFlag.ENABLED) {
				WaitingThreadCounter wtc = waitingThreadCounter;
				if (!WAITING_THREAD_COUNTER_ZERO.equals(wtc)) {
					throw new AssertionError(wtc);
				}
			}
		} finally {
			if (!failedexecution) {
				//only abandon tasks if the execution was successful
				Map<TaskIdentifier, TaskExecutionResult<?>> reusedtasks = new HashMap<>();
				//put in the unexecuted tasks
				for (Entry<TaskIdentifier, TaskExecutionResult<?>> entry : taskIdTaskResults.entrySet()) {
					TaskIdentifier entrytaskid = entry.getKey();
					TaskExecutionResult<?> prevtaskres = entry.getValue();
					TaskExecutionResult<?> prevpresent = resultTaskIdTaskResults.putIfAbsent(entrytaskid, prevtaskres);

					if (prevpresent == null) {
						reusedtasks.put(entrytaskid, prevtaskres);
					}
				}
				abandonedTaskIdResults = new HashMap<>();
				if (!reusedtasks.isEmpty()) {
					boolean hadabandoned;
					do {
						hadabandoned = false;
						for (Iterator<Entry<TaskIdentifier, TaskExecutionResult<?>>> it = reusedtasks.entrySet()
								.iterator(); it.hasNext();) {
							Entry<TaskIdentifier, TaskExecutionResult<?>> entry = it.next();
							TaskExecutionResult<?> reusedtaskres = entry.getValue();
							if (reusedtaskres.isRootTask()) {
								//do not try to abandon a root task
								it.remove();
								continue;
							}
							if (reusedtaskres.getCreatedByTaskIds().isEmpty()) {
								//a non root task is not created by any task
								//it can be abandoned
								it.remove();
								hadabandoned = true;

								TaskIdentifier reusedtaskid = entry.getKey();
								resultTaskIdTaskResults.remove(reusedtaskid);
								abandonedTaskIdResults.put(reusedtaskid, reusedtaskres);

								if (TestFlag.ENABLED) {
									TestFlag.metric().taskAbandoned(reusedtaskid);
								}

								for (TaskIdentifier directlycreatedtaskid : reusedtaskres.getDependencies()
										.getDirectlyCreatedTaskIds().keySet()) {
									TaskExecutionResult<?> createdres = resultTaskIdTaskResults
											.get(directlycreatedtaskid);
									if (createdres == null) {
										continue;
									}
									createdres.getCreatedByTaskIds().remove(reusedtaskid);
								}
							}
						}
					} while (hadabandoned && !reusedtasks.isEmpty());
				}
			} else {
				//the execution failed
				for (Entry<TaskIdentifier, TaskExecutionResult<?>> entry : taskIdTaskResults.entrySet()) {
					TaskIdentifier entrytaskid = entry.getKey();
					TaskExecutionResult<?> prevtaskres = entry.getValue();
					resultTaskIdTaskResults.putIfAbsent(entrytaskid, prevtaskres);
				}
			}
			if (invocationmanager != null) {
				try {
					invocationmanager.close();
				} catch (Exception e) {
					executioncontext.reportIgnoredException(ExceptionView.create(e));
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}

		}
	}

	public EnvironmentSelectionResult testEnvironmentSelection(TaskExecutionEnvironmentSelector environmentselector,
			Set<UUID> allowedenvironmentids) {
		Supplier<? extends SelectionResult> selectionresultsupplier = invocationManager.selectInvoker(null, null,
				allowedenvironmentids, environmentselector, true);
		SelectionResult selectionresult = selectionresultsupplier.get();
		return new EnvironmentSelectionResult(selectionresult.getQualifierEnvironmentProperties());
	}

	private static TaskInvocationManager createTaskInvocationManager(ExecutionContextImpl executioncontext,
			Collection<? extends TaskInvoker> taskinvokers, ThreadGroup clusterInteractionThreadGroup) {
		return new TaskInvocationManager(executioncontext, taskinvokers, clusterInteractionThreadGroup);
	}

	//this method can be called even if the count is 0
	protected WaitingThreadCounter removeWaitingThreadCount(int count) {
		while (true) {
			WaitingThreadCounter wtc = this.waitingThreadCounter;
			if (wtc == WAITING_THREAD_COUNTER_DEADLOCKED) {
				return wtc;
			}
			WaitingThreadCounter nwtc = wtc.removeWaiting(count);
			if (ARFU_waitingThreadCounter.compareAndSet(this, wtc, nwtc)) {
				return nwtc;
			}
			//continue
		}
	}

	//this method shouldn't be called with 0 count
	protected WaitingThreadCounter addWaitingThreadCount(int count) {
		if (TestFlag.ENABLED) {
			if (count == 0) {
				throw new AssertionError("waiting thread count mustn't be 0");
			}
		}
		while (true) {
			WaitingThreadCounter wtc = this.waitingThreadCounter;
			if (wtc == WAITING_THREAD_COUNTER_DEADLOCKED) {
				return wtc;
			}
			WaitingThreadCounter nwtc = wtc.addWaiting(count);
			if (ARFU_waitingThreadCounter.compareAndSet(this, wtc, nwtc)) {
				return nwtc;
			}
			//continue
		}
	}

	//this method can be called even if the count is 0
	protected WaitingThreadCounter removeRunningThreadCount(int count) {
		while (true) {
			WaitingThreadCounter wtc = this.waitingThreadCounter;
			if (wtc == WAITING_THREAD_COUNTER_DEADLOCKED) {
				return wtc;
			}
			WaitingThreadCounter nwtc = wtc.removeRunning(count);
			if (ARFU_waitingThreadCounter.compareAndSet(this, wtc, nwtc)) {
				if (nwtc.runningThreadCount == nwtc.waitingThreadCount && nwtc.runningThreadCount > 0) {
					//possible deadlock. unpark a waiter that should detect it
					//  (only in case this was not the last running thread that was removed
					for (ManagerTaskFutureImpl<?> future : taskIdFutures.values()) {
						if (future.unparkOneWaitingThread(this)) {
							//a thread was unparked. it should detect the deadlock if any
							break;
						}
					}
				}
				return nwtc;
			}
			//continue
		}
	}

	//this method shouldn't be called with 0 count
	protected WaitingThreadCounter addRunningThreadCount(int count) {
		if (TestFlag.ENABLED) {
			if (count == 0) {
				throw new AssertionError("running thread count mustn't be 0");
			}
		}
		while (true) {
			WaitingThreadCounter wtc = this.waitingThreadCounter;
			if (wtc == WAITING_THREAD_COUNTER_DEADLOCKED) {
				//this shouldn't happen
				//as if we attempt to add a new running thread, then the execution mustn't have deadlocked, as there must be
				//at least one thread running, which causes this new running thread to be spawned.
				//(Sidenote: this can happen however, if a task ignores the deadlocked exception, and attempts to continue execution.)
				throw new IllegalStateException(
						"Attempting to add running thread to already deadlocked build execuion.");
			}
			WaitingThreadCounter nwtc = wtc.addRunning(count);
			if (ARFU_waitingThreadCounter.compareAndSet(this, wtc, nwtc)) {
				return nwtc;
			}
			//continue
		}
	}

	protected boolean deadlocked(WaitingThreadCounter expectedwaitingthreadcount) {
		if (!ARFU_waitingThreadCounter.compareAndSet(this, expectedwaitingthreadcount,
				WAITING_THREAD_COUNTER_DEADLOCKED)) {
			return false;
		}
		for (ManagerTaskFutureImpl<?> future : taskIdFutures.values()) {
			future.deadlocked();
		}
		return true;
	}

	protected ManagerTaskFutureImpl<?> getOrCreateTaskFuture(TaskIdentifier taskid) {
		return TaskExecutionManager.this.taskIdFutures.computeIfAbsent(taskid, ManagerTaskFutureImpl::new);
	}

	public static void requireCalledOnTaskThread(TaskContext callertaskcontext, boolean allowinnertask) {
		TaskThreadInfo info = THREADLOCAL_TASK_THREAD.get();
		if (info == null || ((InternalTaskContext) info.taskContext)
				.internalGetTaskContextIdentity() != ((InternalTaskContext) callertaskcontext)
						.internalGetTaskContextIdentity()) {
			throw new IllegalTaskOperationException("Method can be called only on the main task thread.",
					callertaskcontext.getTaskId());
		}
		if (!allowinnertask && info.innerTask) {
			throw new IllegalTaskOperationException(
					"Method can be called only on the main task thread, not on inner task thread.",
					callertaskcontext.getTaskId());
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private boolean initializeExecutionWithTaskFuture(TaskIdentifier taskid, TaskFactory<?> factory,
			TaskIdentifier createdby, ManagerTaskFutureImpl<?>[] outfuture, ManagerTaskFutureImpl<?> ancestorfuture,
			TaskInvocationConfiguration invocationConfiguration) {
		try {
			//we could use get() and putIfAbsent() instead of computeIfAbsent, but that would require
			// 2 calls into the concurrent hash map.
			// it is not profiled, but may be more efficient
			// the code that used get and putIfAbsent is left below. It may be removed in the future
			ManagerTaskFutureImpl<?> f = taskIdFutures.computeIfAbsent(taskid, ManagerTaskFutureImpl::new);
			outfuture[0] = f;
			if (!f.initializeExecution((TaskFactory) factory, invocationConfiguration, this)) {
				checkSameFactories(taskid, f.getFactory(), factory);
				return false;
			}
			return true;
//			ManagerTaskFutureImpl<?> future = taskIdFutures.get(taskid);
//			if (future == null) {
//				ManagerTaskFutureImpl<?> putfuture = new ManagerTaskFutureImpl<>(taskid, factory);
//				ManagerTaskFutureImpl<?> prev = taskIdFutures.putIfAbsent(taskid, putfuture);
//				if (prev != null) {
//					outfuture[0] = prev;
//					if (!prev.initializeExecution((TaskFactory) factory, this)) {
//						checkSameFactories(taskid, prev.getFactory(), factory);
//						return false;
//					}
//					return true;
//				}
//				outfuture[0] = putfuture;
//				return true;
//			}
//			outfuture[0] = future;
//			if (!future.initializeExecution((TaskFactory) factory, this)) {
//				checkSameFactories(taskid, future.getFactory(), factory);
//				return false;
//			}
//			//startexecution succeeded
//			return true;
		} finally {
			if (createdby != null) {
				recordCreatedBy(outfuture[0], createdby);
			}
			outfuture[0].addAncestorFuture(ancestorfuture, this);
		}
	}

	private static void checkSameFactories(TaskIdentifier taskid, TaskFactory<?> factoryfromfuture,
			TaskFactory<?> factoryforstartingtask) {
		//the factory from the future should not ever be null, as if it was, then we should've succeeded the initialization
		//anyway, call the equals on the starting task factory as we know that that is never null.
		if (!factoryforstartingtask.equals(factoryfromfuture)) {
			throw new TaskIdentifierConflictException(
					"Different factories: " + factoryfromfuture + " and " + factoryforstartingtask, taskid);
		}
	}

	private static class TaskExecutionThread extends Thread {
		protected TaskIdentifier taskIdentifier;
		private ThrowingRunnable targetRunnable;
		private Throwable exception;

		public TaskExecutionThread(ThreadGroup group, ThrowingRunnable target, TaskIdentifier taskIdentifier,
				String name) {
			super(group, null, name);
			this.taskIdentifier = taskIdentifier;
			this.targetRunnable = target;
		}

		public TaskIdentifier getTaskIdentifier() {
			return taskIdentifier;
		}

		public Throwable getException() {
			return exception;
		}

		@Override
		public void run() {
			try {
				try {
					targetRunnable.run();
				} catch (TaskException e) {
					throw e;
				} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError
						| AssertionError | Exception e) {
					throw ExceptionAccessInternal.createTaskExecutionFailedException(
							"Unexpected exception caught during task execution.", e, taskIdentifier);
				}
			} catch (Throwable e) {
				exception = e;
			}
		}
	}

	private void offerTaskRunnable(ThrowingRunnable run, TaskIdentifier taskid, String name) {
		TaskExecutionThread excthread = new TaskExecutionThread(executionThreadGroup, run, taskid, name);
		//start the thread before adding to the collector
		//else there is a race condition when the consumer could join the thread before it is started
		excthread.start();
		taskThreads.add(excthread);
	}

	protected <R> ManagerTaskFutureImpl<R> executeImpl(TaskFactory<R> factory, TaskIdentifier taskid,
			ExecutionContextImpl context, TaskExecutionResult<?> createdby, TaskExecutionParameters parameters,
			TaskExecutorContext<?> currentexecutorcontext) {
		TaskInvocationConfiguration capabilities = getTaskInvocationConfiguration(factory);
		if (currentexecutorcontext != null && currentexecutorcontext.hasShortTaskCapability()) {
			//a short task can only run short tasks
			if (!capabilities.isShort()) {
				throw new IllegalTaskOperationException(
						"A short capable task can only wait for other short capable tasks. (Waiting for: " + taskid
								+ ")",
						currentexecutorcontext.getTaskId());
			}
		}
		ManagerTaskFutureImpl<?> ancestorfuture = currentexecutorcontext == null ? null : currentexecutorcontext.future;

		SimpleTaskDirectoryPathContext taskdircontext = currentexecutorcontext == null
				? new SimpleTaskDirectoryPathContext(this, context.getWorkingDirectoryPath(), SakerPath.EMPTY)
				: currentexecutorcontext.getTaskDirectoryContext();
		return executeWithStrategyImpl(factory, taskid, context, executeStrategy(), createdby, parameters,
				taskdircontext, capabilities, ancestorfuture);
	}

	//TODO don't call this method too often but cache its return value
	private static TaskInvocationConfiguration getTaskInvocationConfiguration(TaskFactory<?> factory) {
		TaskInvocationConfiguration config = factory.getInvocationConfiguration();
		if (config == null) {
			throw new InvalidTaskInvocationConfigurationException(
					"Task returned null invocation configuration: " + factory.getClass().getName());
		}
		//perform validations
		if (config.isShort()) {
			int cptokencount = config.getRequestedComputationTokenCount();
			boolean remotedispatch = config.isRemoteDispatchable();
			boolean innertaskcomputational = config.isInnerTasksComputationals();
			if (remotedispatch) {
				throw new InvalidTaskInvocationConfigurationException(
						"Short tasks cannot be remote dispatchable for " + factory.getClass().getName());
			}
			if (cptokencount > 0) {
				throw new InvalidTaskInvocationConfigurationException(
						"Short tasks cannot use computation tokens for " + factory.getClass().getName());
			}
			if (innertaskcomputational) {
				throw new InvalidTaskInvocationConfigurationException(
						"Short tasks cannot have computational inner tasks for " + factory.getClass().getName());
			}
		}

		return config;
	}

	//suppress unused TaskContextReference
	@SuppressWarnings("try")
	private <R> InnerTaskResults<R> startInnerImpl(TaskFactory<R> factory, InnerTaskExecutionParameters parameters,
			TaskExecutorContext<?> taskcontext) {
		if (parameters == null) {
			parameters = DEFAULT_INNER_TASK_EXECUTION_PARAMETERS;
		}
		TaskInvocationConfiguration configuration = getTaskInvocationConfiguration(factory);
		if (configuration.isCacheable()) {
			throw new InvalidTaskInvocationConfigurationException("Inner tasks may not be cacheable.");
		}
		int computationtokencount = configuration.getRequestedComputationTokenCount();
		if (computationtokencount > 0) {
			if (!taskcontext.isInnerTasksComputationals()) {
				throw new InvalidTaskInvocationConfigurationException(
						"Enclosing task hasn't declared computational inner task capability for inner task ("
								+ factory.getClass().getName() + ") with computation tokens.");
			}
		}
		int dupfactor = parameters.getClusterDuplicateFactor();
		if (dupfactor != 0) {
			if (!configuration.isRemoteDispatchable()) {
				throw new InvalidTaskInvocationConfigurationException(
						"Cluster duplicatable inner task (" + factory.getClass().getName()
								+ ") did not report remote dispatchable capability for non-zero duplication factor. ("
								+ dupfactor + ")");
			}
		}
		TaskDuplicationPredicate duplicationpredicate = parameters.getDuplicationPredicate();
		if (duplicationpredicate instanceof CoordinatorDuplicationPredicate) {
			TaskDuplicationPredicate actualpredicate = ((CoordinatorDuplicationPredicate) duplicationpredicate)
					.getPredicate();
			//wrap into an other predicate, so it doesn't overwrite RMI transfer, and always written as a remote
			duplicationpredicate = () -> actualpredicate.shouldInvokeOnceMore();
		}
		Set<UUID> allowedenvironmentids = parameters.getAllowedClusterEnvironmentIdentifiers();
		if (allowedenvironmentids != null && allowedenvironmentids.isEmpty()) {
			throw new InvalidTaskInvocationConfigurationException(
					"Allowed cluster environment identifiers are non-null and empty for inner task "
							+ factory.getClass().getName());
		}
		Supplier<? extends TaskInvocationManager.SelectionResult> invokerselectionresult = invocationManager
				.selectInvoker(taskcontext.taskId, configuration, null, allowedenvironmentids);

		if (configuration.isShort()) {
			if (dupfactor != 0) {
				throw new InvalidTaskInvocationConfigurationException(
						"Cluster duplication factor must be 0 for short inner tasks. (" + factory.getClass().getName()
								+ ")");
			}
			if (duplicationpredicate != null) {
				throw new InvalidTaskInvocationConfigurationException(
						"Cannot specify duplication predicate for short inner tasks. (" + factory.getClass().getName()
								+ ")");
			}
			InnerTaskContext innertaskcontext = null;
			try {
				//the inner task is short. it cannot be remote dispatchable
				//    it can only be invoked on the coordinator
				//get the environment selection result to have any environment errors thrown
				invokerselectionresult.get();
				Task<? extends R> task;
				try {
					task = factory.createTask(taskcontext.executionContext);
				} catch (Exception | LinkageError | ServiceConfigurationError | OutOfMemoryError | AssertionError
						| StackOverflowError e) {
					return new CompletedInnerTaskResults<>(new FailedInnerTaskOptionalResult<>(e));
				}
				if (task == null) {
					throw new NullPointerException(
							"Inner task factory created null task: " + factory.getClass().getName());
				}
				R res;
				innertaskcontext = InnerTaskContext.startInnerTask(taskcontext, factory,
						configuration.getRequestedComputationTokenCount());
				try (TaskContextReference contextref = TaskContextReference.createForInnerTask(innertaskcontext)) {
					InternalTaskBuildTrace btrace = innertaskcontext.internalGetBuildTrace();
					try {
						res = task.run(innertaskcontext);
					} catch (Throwable e) {
						btrace.setThrownException(e);
						throw e;
					} finally {
						btrace.setAbortExceptions(innertaskcontext.getAbortExceptions());
						btrace.endInnerTask();
					}
				}
				return new CompletedInnerTaskResults<>(InnerTaskInvocationManager.createInnerTaskResultHolder(res,
						innertaskcontext.getAbortExceptions()));
			} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError | AssertionError
					| Exception e) {
				if (innertaskcontext != null) {
					Throwable[] abortexceptions = innertaskcontext.getAbortExceptions();
					if (abortexceptions != null) {
						for (Throwable abortexc : abortexceptions) {
							e.addSuppressed(abortexc);
						}
					}
				}
				return new CompletedInnerTaskResults<>(new FailedInnerTaskOptionalResult<>(e));
			}
		}
		boolean duplicationcancellable = parameters.isDuplicationCancellable();
		ManagerInnerTaskResults<R> result = invocationManager.invokeInnerTaskRunning(factory,
				invokerselectionresult.get(), dupfactor, duplicationcancellable, taskcontext, duplicationpredicate,
				configuration, allowedenvironmentids, parameters.getMaxEnvironmentFactor());
		taskcontext.innerTasks.add(result);
		return result;
	}

	public interface ManagerInnerTaskResults<R> extends InnerTaskResults<R>, InternalInnerTaskResults<R> {
		public void waitFinishCancelOptionally() throws InterruptedException, RMIRuntimeException;

		public void interrupt();

		public InnerTaskResultHolder<R> internalGetNextOnExecutionFinish() throws InterruptedException;
	}

	static class CompletedInnerTaskResults<T> implements InnerTaskResults<T> {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<TaskExecutionManager.CompletedInnerTaskResults, InnerTaskResultHolder> ARFU_result = AtomicReferenceFieldUpdater
				.newUpdater(TaskExecutionManager.CompletedInnerTaskResults.class, InnerTaskResultHolder.class,
						"result");
		@SuppressWarnings("unused")
		private volatile InnerTaskResultHolder<T> result;

		public CompletedInnerTaskResults(InnerTaskResultHolder<T> result) {
			this.result = result;
		}

		@SuppressWarnings("unchecked")
		@Override
		public InnerTaskResultHolder<T> getNext() {
			return ARFU_result.getAndSet(this, null);
		}

		@Override
		public void cancelDuplicationOptionally() {
		}
	}

	protected <R> ManagerTaskFutureImpl<R> startImpl(TaskFactory<R> factory, TaskIdentifier taskid,
			ExecutionContextImpl context, TaskExecutionResult<?> createdby, TaskExecutionParameters parameters,
			TaskExecutorContext<?> currentexecutorcontext) {
		TaskInvocationConfiguration capabilities = getTaskInvocationConfiguration(factory);
		Executor strategy = getExecutionStrategyForFactory(taskid, capabilities, createTaskThreadName(factory));

		ManagerTaskFutureImpl<?> ancestorfuture = currentexecutorcontext == null ? null : currentexecutorcontext.future;

		SimpleTaskDirectoryPathContext taskdircontext = currentexecutorcontext == null
				? new SimpleTaskDirectoryPathContext(this, context.getWorkingDirectoryPath(),
						context.getBuildDirectoryPath())
				: currentexecutorcontext.getTaskDirectoryContext();

		return executeWithStrategyImpl(factory, taskid, context, strategy, createdby, parameters, taskdircontext,
				capabilities, ancestorfuture);
	}

	private static String createTaskThreadName(TaskFactory<?> factory) {
		return "Task: " + factory.getClass().getName();
	}

	private Executor getExecutionStrategyForFactory(TaskIdentifier taskid, TaskInvocationConfiguration capabalities,
			String name) {
		if (capabalities.isShort()) {
			return executeStrategy();
		}
		return parallelRunnerStrategy(taskid, name);
	}

	private static Executor executeStrategy() {
		return Runnable::run;
	}

	private Executor parallelRunnerStrategy(TaskIdentifier taskid, String name) {
		return r -> {
			addRunningThreadCount(1);
			offerTaskRunnable(() -> {
				try {
					r.run();
				} finally {
					removeRunningThreadCount(1);
				}
			}, taskid, name);
		};
	}

	private <R> ManagerTaskFutureImpl<R> executeWithStrategyImpl(TaskFactory<R> factory, TaskIdentifier taskid,
			ExecutionContextImpl context, Executor executionstrategy, TaskExecutionResult<?> createdby,
			TaskExecutionParameters parameters, SimpleTaskDirectoryPathContext currenttaskdirectorycontext,
			TaskInvocationConfiguration capabilities, ManagerTaskFutureImpl<?> ancestorfuture) {
		SpawnedResultTask spawnedtask = getCreateSpawnedTask(taskid);
		if (createdby != null) {
			createdby.getDependencies().addCreatedTask(taskid, factory, parameters);
			SpawnedResultTask runspawned = runSpawnedTasks.get(createdby.getTaskIdentifier());
			runspawned.addChild(taskid, spawnedtask);
		}
		@SuppressWarnings("rawtypes")
		ManagerTaskFutureImpl[] outfuture = { null };
		boolean starting = initializeExecutionWithTaskFuture(taskid, factory,
				createdby == null ? null : createdby.getTaskIdentifier(), outfuture, ancestorfuture, capabilities);
		@SuppressWarnings("unchecked")
		ManagerTaskFutureImpl<R> future = outfuture[0];

		if (!starting) {
			return future;
		}

		executeExecutionWithStrategy(factory, taskid, context, executionstrategy, future, parameters,
				currenttaskdirectorycontext, capabilities, ancestorfuture, spawnedtask);
		return future;
	}

	private SpawnedResultTask getCreateSpawnedTask(TaskIdentifier taskid) {
		return runSpawnedTasks.computeIfAbsent(taskid, SpawnedResultTask::new);
	}

	private <R> void executeExecutionWithStrategy(TaskFactory<R> factory, TaskIdentifier taskid,
			ExecutionContextImpl context, Executor executionstrategy, ManagerTaskFutureImpl<R> future,
			TaskExecutionParameters parameters, SimpleTaskDirectoryPathContext currenttaskdirectorycontext,
			TaskInvocationConfiguration capabilities, ManagerTaskFutureImpl<?> ancestorfuture,
			SpawnedResultTask spawnedtask) {
		executionstrategy.execute(() -> {
			TaskExecutionResult<?> prevexecresult = getPreviousExecutionResult(taskid);

			SimpleTaskDirectoryPathContext taskdircontext = getTaskDirectoryPathContext(context, parameters,
					currenttaskdirectorycontext);
			if (prevexecresult != null) {
				TaskDependencies prevexecdependencies = prevexecresult.getDependencies();
				//check the factory change before the invoker selection to avoid unnecessary computing some environment properties
				boolean taskfactorychanged = !factory.equals(prevexecresult.getFactory());
				Supplier<? extends TaskInvocationManager.SelectionResult> invokerselectionresult = invocationManager
						.selectInvoker(taskid, capabilities,
								taskfactorychanged ? null
										: prevexecdependencies.getEnvironmentPropertyDependenciesWithQualifiers(),
								null);

				TaskIdDependencyCollector collector = new TaskIdDependencyCollector(taskid, future,
						prevexecdependencies, taskdircontext, context);
				DependencyDelta deltas;
				if (Boolean.FALSE.equals(checkedHasAnyDeltas.get(taskid))) {
					deltas = DependencyDelta.EMPTY_DELTA;
				} else {
					try {
						deltas = collectDependencyDeltasImpl(prevexecresult, context, taskdircontext,
								invokerselectionresult, taskfactorychanged, taskid, collector);
						collector.finishDependencyCollection(deltas);
					} catch (TaskExecutionDeadlockedException e) {
						TaskExecutionDeadlockedException te = ExceptionAccessInternal
								.createTaskExecutionDeadlockedException(taskid);
						te.addSuppressed(e);
						throw te;
					} catch (TaskResultWaitingInterruptedException e) {
						throw e;
					} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError
							| AssertionError | Exception e) {
						throw new AssertionError("Failed to collect deltas for task: " + taskid, e);
					}
				}

				if (deltas.isEmpty()) {
					//no deltas for the task, nothing changed for it

					@SuppressWarnings("unchecked")
					TaskExecutionResult<R> prevexecres_r = (TaskExecutionResult<R>) prevexecresult;

					this.buildTrace.taskUpToDate(prevexecresult, capabilities);

					//TODO use the all transitive map
					startUnchangedTaskSubTasks(prevexecres_r, context, currenttaskdirectorycontext, future,
							collector.getAllTransitiveCreatedTaskIds().keySet(), parameters, spawnedtask);
					return;
				}
				//there are some deltas, run the taks
				executeTaskRunning(prevexecresult, taskid, context, future, factory, deltas, parameters, capabilities,
						invokerselectionresult, taskdircontext, spawnedtask);
			} else {
				//no previous result is present, this is a new task
				executeNewTaskRunning(taskid, context, future, factory, parameters, taskdircontext, capabilities,
						spawnedtask);
			}
		});
	}

	private SimpleTaskDirectoryPathContext getTaskDirectoryPathContext(ExecutionContextImpl context,
			TaskExecutionParameters parameters, SimpleTaskDirectoryPathContext currenttaskdirectorycontext) {
		return new SimpleTaskDirectoryPathContext(this,
				getExecutionWorkingDirectoryPath(context, parameters, currenttaskdirectorycontext),
				getExecutionBuildDirectoryPath(parameters,
						currenttaskdirectorycontext.getRelativeTaskBuildDirectoryPath()));
	}

	private static int getApproximateStringsLength(Iterable<String> strings) {
		int c = 0;
		for (String s : strings) {
			c += s.length();
		}
		return c;
	}

	//suppress unused lock in try warning
	@SuppressWarnings("try")
	private void printLinesOfExecutionResult(ExecutionContextImpl context, TaskExecutionResult<?> prevexecresult,
			ManagerTaskFutureImpl<?> future) {
		List<String> prevprintedlines = prevexecresult.getPrintedLines();
		if (ObjectUtils.isNullOrEmpty(prevprintedlines)) {
			this.buildTrace.upToDateTaskStandardOutput(prevexecresult, null);
			return;
		}
		//buffer the lines into a byte buffer and write them in a single call.
		//    this is beneficial to avoid unnecessary multiple synchronization and implicit RMI calls 
		try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream(
				Math.max(getApproximateStringsLength(prevprintedlines) * 3 / 2, 128))) {
			for (String s : prevprintedlines) {
				if (s.isEmpty()) {
					baos.write('\n');
					continue;
				}
				if (s.charAt(0) != PRINTED_LINE_VARIABLES_MARKER_CHAR) {
					byte[] sbytes = s.getBytes(StandardCharsets.UTF_8);
					int offset = 0;
					int len = sbytes.length;
					if (s.startsWith("\\" + PRINTED_LINE_VARIABLES_MARKER_CHAR)) {
						//escaped variables message. remove the preceeding \
						offset = 1;
						len--;
					}
					baos.write(sbytes, offset, len);
					baos.write('\n');
					continue;
				}
				//specially handled printed line that contains variables

				String processedline = future.processPrintedLineVariables(this, s, 1);
				baos.write(processedline.getBytes(StandardCharsets.UTF_8));
				baos.write('\n');
//				future.internalGetOriginatingBuildFile(this, context)
			}
			this.buildTrace.upToDateTaskStandardOutput(prevexecresult, baos);
			try (StandardIOLock lock = context.acquireStdIOLock()) {
				ByteSink stdout = context.getStdOutSink();
				stdout.write(baos.toByteArrayRegion());
				if (TestFlag.ENABLED) {
					TestFlag.metric().taskLinesPrinted(prevexecresult.getTaskIdentifier(), prevprintedlines);
				}
			} catch (InterruptedException | IOException ignored) {
				//if an interruption or IOException happens it is fine to omit printing the remaining lines
				//it should not really happen, but the exception is ignoreable
				context.reportIgnoredException(prevexecresult.getTaskIdentifier(),
						TaskIdentifierExceptionView.create(ignored));
			}
		}
	}

	private <R> void startUnchangedTaskWithInitializedFuture(ExecutionContextImpl context,
			ManagerTaskFutureImpl<R> future, TaskExecutionParameters parameters,
			SimpleTaskDirectoryPathContext currenttaskdirectorycontext, ManagerTaskFutureImpl<?> ancestorfuture) {
		TaskFactory<R> factory = future.getFactory();
		TaskIdentifier taskid = future.getTaskIdentifier();
		//TODO we shouldnt lookup here, but pass as parameter
		SpawnedResultTask spawnedtask = runSpawnedTasks.get(taskid);
		executeExecutionWithStrategy(factory, taskid, context,
				parallelRunnerStrategy(taskid, createTaskThreadName(factory)), future, parameters,
				currenttaskdirectorycontext, getTaskInvocationConfiguration(factory), ancestorfuture, spawnedtask);
	}

	private <R> void startUnchangedTaskSubTasks(TaskExecutionResult<R> prevexecresult,
			ExecutionContextImpl executioncontext, SimpleTaskDirectoryPathContext currenttaskdirectorycontext,
			ManagerTaskFutureImpl<R> future, Set<TaskIdentifier> allTransitiveCreatedTaskIds,
			TaskExecutionParameters parameters, SpawnedResultTask spawnedtask) {

		//we have to instantiate the futures first so when the tasks are started,
		//   it is seen for them, that their possible dependent tasks are running and can be waited for

		//TODO if a created task is also dependency, then it can be just directly added to the results 
		//     instead of going through the delta checking again

		runIdeConfigurations.addAll(prevexecresult.getIDEConfigurations());

		TaskIdentifier taskid = prevexecresult.getTaskIdentifier();

		putTaskToResults(taskid, prevexecresult);
		SimpleTaskDirectoryPathContext taskdircontext = getTaskDirectoryPathContext(executioncontext, parameters,
				currenttaskdirectorycontext);

		TaskDependencies dependencies = prevexecresult.getDependencies();
		Map<TaskIdentifier, ReportedTaskDependency> taskiddependencies = dependencies.getTaskDependencies();

		future.updateInitialization(dependencies);

		Map<ManagerTaskFutureImpl<?>, CreatedTaskDependency> launchfutures = new IdentityHashMap<>();
		Map<TaskIdentifier, CreatedTaskDependency> directlycreatedtasks = dependencies.getDirectlyCreatedTaskIds();
		for (Entry<TaskIdentifier, CreatedTaskDependency> entry : directlycreatedtasks.entrySet()) {
			TaskIdentifier deptaskid = entry.getKey();
			CreatedTaskDependency taskdep = entry.getValue();

			SpawnedResultTask createdspawn = getCreateSpawnedTask(deptaskid);
			spawnedtask.addChild(deptaskid, createdspawn);

			@SuppressWarnings("rawtypes")
			TaskFactory subtaskfactory = taskdep.getFactory();
			@SuppressWarnings("rawtypes")
			ManagerTaskFutureImpl[] outfuture = { null };
			boolean starting = initializeExecutionWithTaskFuture(deptaskid, subtaskfactory, taskid, outfuture, future,
					getTaskInvocationConfiguration(subtaskfactory));
			if (!starting) {
				continue;
			}
			ManagerTaskFutureImpl<?> depfuture = outfuture[0];

//			if (taskiddependencies.containsKey(deptaskid)) {
//				//we depend on the currently created task
//				//we determined, that there are no deltas for the task in during delta detection, therefore just starting the unchanged tasks for it is fine
//				TaskExecutionParameters depparams = taskdep.getTaskParameters();
//				startUnchangedTaskSubTasks(getPreviousExecutionResult(deptaskid), context, taskdircontext, depfuture, allTransitiveCreatedTaskIds, depparams);
//			} else {
			//we don't depend on the created task, so launch it
			launchfutures.put(depfuture, taskdep);
//			}
		}

		for (Entry<ManagerTaskFutureImpl<?>, CreatedTaskDependency> entry : launchfutures.entrySet()) {
			ManagerTaskFutureImpl<?> depfuture = entry.getKey();
			CreatedTaskDependency taskdep = entry.getValue();
			TaskExecutionParameters deptaskparams = taskdep.getTaskParameters();
			startUnchangedTaskWithInitializedFuture(executioncontext, depfuture, deptaskparams, taskdircontext, future);
		}
		for (TaskIdentifier deptaskid : taskiddependencies.keySet()) {
			//we need to wait the tasks that we depend on
			//because if we don't the the tasks that depend on this can be notified early without side effects completing
			ManagerTaskFutureImpl<?> depfuture = getOrCreateTaskFuture(deptaskid);
			depfuture.waitResultPark(this);
		}

		if (isTaskResultFailed(prevexecresult)) {
			List<Throwable> abortexceptions = prevexecresult.getAbortExceptions();
			future.finished(this, prevexecresult);
			spawnedtask.executionFinished(prevexecresult);
			//print the lines before throwing the exception, but after setting the failure
			printLinesOfExecutionResult(executioncontext, prevexecresult, future);
			taskRunningFailureExceptions.add(ImmutableUtils.makeImmutableMapEntry(taskid,
					createFailException(taskid, prevexecresult.getFailCauseException(), abortexceptions)));
			return;
//			throw createFailException(taskid, prevexecresult.getFailCauseException(), abortexceptions);
		}

		future.finished(this, prevexecresult);
		spawnedtask.executionFinished(prevexecresult);

		printLinesOfExecutionResult(executioncontext, prevexecresult, future);
	}

	private TaskExecutionResult<?> getPreviousExecutionResult(TaskIdentifier taskid) {
		return taskIdTaskResults.get(taskid);
	}

	private static class TaggedFileDependencyDelta {
		protected NavigableMap<SakerPath, SakerFile> inputDependencies = Collections.emptyNavigableMap();
		protected NavigableMap<SakerPath, SakerFile> outputDependencies = Collections.emptyNavigableMap();

		public TaggedFileDependencyDelta() {
		}

		public void setInputDependencies(NavigableMap<SakerPath, SakerFile> inputDependencies) {
			this.inputDependencies = inputDependencies;
		}

		public void setOutputDependencies(NavigableMap<SakerPath, SakerFile> outputDependencies) {
			this.outputDependencies = outputDependencies;
		}
	}

	private enum EmptyTaskDeltas implements TaskFileDeltas {
		INSTANCE;

		@Override
		public Set<? extends FileChangeDelta> getFileDeltasWithTag(Object tag) {
			return Collections.emptySet();
		}

		@Override
		public Set<? extends FileChangeDelta> getFileDeltas() {
			return Collections.emptySet();
		}

		@Override
		public FileChangeDelta getAnyFileDeltaWithTag(Object tag) {
			return null;
		}

		@Override
		public boolean hasFileDeltaWithTag(Object tag) {
			return false;
		}

		@Override
		public boolean isEmpty() {
			return true;
		}
	}

	@RMIWrap(TypedDeltas.TypeDeltasRMIWrapper.class)
	private static final class TypedDeltas implements TaskFileDeltas {
		private Set<FileChangeDelta> deltas;
		private Map<Object, Set<FileChangeDelta>> taggedFileDeltas;

		public TypedDeltas() {
			this.deltas = new HashSet<>();
			this.taggedFileDeltas = new HashMap<>();
		}

		public void clear() {
			deltas.clear();
			taggedFileDeltas.clear();
		}

		@Override
		public Set<? extends FileChangeDelta> getFileDeltas() {
			return ImmutableUtils.unmodifiableSet(deltas);
		}

		@Override
		public Set<? extends FileChangeDelta> getFileDeltasWithTag(Object tag) {
			Set<FileChangeDelta> got = taggedFileDeltas.get(tag);
			if (got == null) {
				return Collections.emptySet();
			}
			return ImmutableUtils.unmodifiableSet(got);
		}

		@Override
		public boolean isEmpty() {
			return deltas.isEmpty();
		}

		@Override
		public boolean hasFileDeltaWithTag(Object tag) {
			Set<FileChangeDelta> got = taggedFileDeltas.get(tag);
			if (got == null) {
				return false;
			}
			return !got.isEmpty();
		}

		@Override
		public FileChangeDelta getAnyFileDeltaWithTag(Object tag) {
			Set<FileChangeDelta> got = taggedFileDeltas.get(tag);
			if (ObjectUtils.isNullOrEmpty(got)) {
				return null;
			}
			return got.iterator().next();
		}

		public boolean addDelta(FileChangeDelta delta) {
			if (deltas.add(delta)) {
				taggedFileDeltas.computeIfAbsent(delta.getTag(), Functionals.hashSetComputer()).add(delta);
				return true;
			}
			return false;
		}

		protected static final class TypeDeltasRMIWrapper implements RMIWrapper {
			private TypedDeltas deltas;

			@SuppressWarnings("unused")
			public TypeDeltasRMIWrapper() {
			}

			@SuppressWarnings("unused")
			public TypeDeltasRMIWrapper(TypedDeltas deltas) {
				this.deltas = deltas;
			}

			@Override
			public void writeWrapped(RMIObjectOutput out) throws IOException {
				SerialUtils.writeExternalMap(out, deltas.taggedFileDeltas, RMIObjectOutput::writeSerializedObject,
						SerialUtils::writeExternalCollection);
			}

			@Override
			public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
				Map<Object, Set<FileChangeDelta>> deltasmap = SerialUtils.readExternalMap(new HashMap<>(), in,
						SerialUtils::readExternalObject, SerialUtils::readExternalImmutableHashSet);
				deltas = new TypedDeltas();
				deltas.taggedFileDeltas = deltasmap;
				for (Set<FileChangeDelta> fcdeltas : deltasmap.values()) {
					deltas.deltas.addAll(fcdeltas);
				}
			}

			@Override
			public Object resolveWrapped() {
				return deltas;
			}

			@Override
			public Object getWrappedObject() {
				throw new UnsupportedOperationException();
			}

		}
	}

	private static class FoundDependencyDeltaException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public static final FoundDependencyDeltaException INSTANCE = new FoundDependencyDeltaException();

		public FoundDependencyDeltaException() {
			super(null, null, false, false);
		}
	}

	private static class DependencyDelta {
		protected static final DependencyDelta EMPTY_DELTA = new DependencyDelta();
		protected static final DependencyDelta NEW_TASK_DELTA;
		static {
			NEW_TASK_DELTA = new DependencyDelta();
			NEW_TASK_DELTA.addNonFileDelta(new NewTaskDeltaImpl());
			NEW_TASK_DELTA.fileDeltasCalculated = true;

			EMPTY_DELTA.fileDeltasCalculated = true;
		}

		protected final Set<BuildDelta> nonFileDeltas = new HashSet<>();

		protected volatile boolean fileDeltasCalculated = false;
		private final Object fileDeltasCalculateLock = new Object();

		protected final TypedDeltas allFileDeltas = new TypedDeltas();
		protected final Map<DeltaType, TypedDeltas> deltaTypedBuildDeltas = new EnumMap<>(DeltaType.class);

		protected final Map<Object, TaggedFileDependencyDelta> taggedFileDeltas = new HashMap<>();
		protected final Map<FileCollectionStrategy, NavigableMap<SakerPath, ? extends SakerFile>> fileAdditionDependencies = new ConcurrentHashMap<>();

		public DependencyDelta() {
		}

		public TaggedFileDependencyDelta getTaggedFileDelta(Object tag) {
			return taggedFileDeltas.computeIfAbsent(tag, t -> new TaggedFileDependencyDelta());
		}

		public TaggedFileDependencyDelta getTaggedFileDeltaIfExists(Object tag) {
			return taggedFileDeltas.get(tag);
		}

		public boolean isEmpty() {
			return nonFileDeltas.isEmpty() && allFileDeltas.isEmpty();
		}

		public void resetFileDeltasCalculated() {
			this.fileDeltasCalculated = false;
			allFileDeltas.clear();
			taggedFileDeltas.clear();
			fileAdditionDependencies.clear();
			for (Iterator<DeltaType> it = deltaTypedBuildDeltas.keySet().iterator(); it.hasNext();) {
				DeltaType dt = it.next();
				if (FILE_CHANGE_DELTA_TYPES.contains(dt)) {
					it.remove();
				}
			}
		}

		public void addFileDeltas(Iterable<? extends FileChangeDelta> deltas) {
			for (FileChangeDelta delta : deltas) {
				addFileDelta(delta);
			}
		}

		public void addFileDelta(FileChangeDelta delta) {
			if (allFileDeltas.addDelta(delta)) {
				deltaTypedBuildDeltas.computeIfAbsent(delta.getType(), x -> new TypedDeltas()).addDelta(delta);
			}
		}

		public void addNonFileDelta(BuildDelta delta) {
			nonFileDeltas.add(delta);
		}

		public NavigableSet<SakerPath> collectAllChangedPaths() {
			NavigableSet<SakerPath> result = new TreeSet<>();
			for (DeltaType deltatype : FILE_CHANGE_DELTA_TYPES) {
				TypedDeltas typeddeltas = deltaTypedBuildDeltas.get(deltatype);
				if (typeddeltas == null) {
					continue;
				}
				for (Set<FileChangeDelta> taggedfilechanges : typeddeltas.taggedFileDeltas.values()) {
					for (FileChangeDelta fcd : taggedfilechanges) {
						result.add(fcd.getFilePath());
					}
				}
			}
			return result;
		}

		@Override
		public String toString() {
			return "DependencyDelta [" + nonFileDeltas + allFileDeltas.deltas + "]";
		}

		public void collectFileDependencyDeltasDuringDeltaDiscovery(TaskExecutionManager taskExecutionManager,
				ExecutionContextImpl context, TaskDependencies dependencies,
				SimpleTaskDirectoryContext directorycontext, TaskIdentifier taskid) {
			fileDeltasCalculated = true;
			taskExecutionManager.collectFileDependencyDeltas(context, dependencies, this, directorycontext);
		}

		public void ensureFileDeltasCollected(TaskExecutionManager taskExecutionManager,
				TaskExecutorContext<?> executorcontext, TaskDependencies dependencies) {
			boolean calced = fileDeltasCalculated;
			if (calced) {
				return;
			}
			synchronized (fileDeltasCalculateLock) {
				calced = fileDeltasCalculated;
				if (calced) {
					return;
				}
				SimpleTaskDirectoryContext simpledirectorycontext = new SimpleTaskDirectoryContext(
						executorcontext.getTaskWorkingDirectory(), executorcontext.getTaskWorkingDirectoryPath(),
						executorcontext.getTaskBuildDirectory(), executorcontext.getTaskBuildDirectoryPath());
				taskExecutionManager.collectFileDependencyDeltas(executorcontext.executionContext, dependencies, this,
						simpledirectorycontext);
				fileDeltasCalculated = true;

				executorcontext.taskBuildTrace.deltas(this.allFileDeltas.getFileDeltas());
			}
		}

		public boolean isFileDeltasCalculated() {
			return fileDeltasCalculated;
		}
	}

	private static class AnyDependencyFinderDependencyDelta extends DependencyDelta {
		@Override
		public void addNonFileDelta(BuildDelta delta) {
			throw FoundDependencyDeltaException.INSTANCE;
		}

		@Override
		public void addFileDelta(FileChangeDelta delta) {
			throw FoundDependencyDeltaException.INSTANCE;
		}
	}

	private static class TransitivelyCreatedTask {
		private SimpleTaskDirectoryPathContext directoryPathContext;
		private TaskDependencies dependencies;
		private final Map<TaskIdentifier, TransitivelyCreatedTask> creators = new HashMap<>();
		private TaskIdentifier taskId;

		public TransitivelyCreatedTask(TaskIdentifier taskid, SimpleTaskDirectoryPathContext directoryPathContext,
				TaskDependencies depenencies) {
			this.taskId = taskid;
			this.directoryPathContext = directoryPathContext;
			this.dependencies = depenencies;
		}

		public SimpleTaskDirectoryPathContext getDirectoryPathContext() {
			return directoryPathContext;
		}

		public void addCreator(TransitivelyCreatedTask creatordependencies) {
			creators.put(creatordependencies.getTaskId(), creatordependencies);
		}

		public Map<TaskIdentifier, TransitivelyCreatedTask> getCreators() {
			return creators;
		}

		public TaskDependencies getDependencies() {
			return dependencies;
		}

		public TaskIdentifier getTaskId() {
			return taskId;
		}

		@Override
		public String toString() {
			return "TransitivelyCreatedTask[" + taskId + "]";
		}
	}

	private class TaskIdDependencyCollector {
//		protected Map<TaskIdentifier, DependencyDelta> deltas = new HashMap<>();
		private final Map<TaskIdentifier, Boolean> taskIdsHasDeltas = new HashMap<>();
		private final TaskIdentifier baseIdentifier;
		protected final LazySupplier<Map<TaskIdentifier, TransitivelyCreatedTask>> allTransitiveCreatedTaskIds;
		private final ManagerTaskFutureImpl<?> baseFuture;
		private final TaskDependencies baseDependencies;

		private final Map<TaskIdentifier, Set<ManagerTaskFutureImpl<?>>> finishedRetrievalFutures = new HashMap<>();

		public TaskIdDependencyCollector(TaskIdentifier baseIdentifier, ManagerTaskFutureImpl<?> basefuture,
				TaskDependencies dependencies, SimpleTaskDirectoryPathContext basedirectorypathcontext,
				ExecutionContextImpl context) {
			this.baseIdentifier = baseIdentifier;
			this.baseFuture = basefuture;
			this.baseDependencies = dependencies;
			allTransitiveCreatedTaskIds = LazySupplier.of(() -> {
				TransitivelyCreatedTask basetranscreated = new TransitivelyCreatedTask(baseIdentifier,
						basedirectorypathcontext, dependencies);
				Map<TaskIdentifier, TransitivelyCreatedTask> res = getAllTransitiveCreatedTaskIdsBasedOnPreviousResult(
						basedirectorypathcontext, context, basetranscreated);
				res.put(baseIdentifier, basetranscreated);
				return res;
			});
		}

		public Map<TaskIdentifier, TransitivelyCreatedTask> getAllTransitiveCreatedTaskIds() {
			return allTransitiveCreatedTaskIds.get();
		}

		public boolean hasAnyDelta(TaskIdentifier taskid, ExecutionContextImpl context,
				TransitivelyCreatedTask transitivecreatedtask, TaskFactory<?> factory,
				TaskExecutionResult<?> prevexecresult) {
			if (Boolean.TRUE.equals(checkedHasAnyDeltas.get(taskid))) {
				return true;
			}
			if (taskid.equals(baseIdentifier)) {
				return false;
			}
			Boolean gothasdelta = taskIdsHasDeltas.get(taskid);
			if (gothasdelta != null) {
				return gothasdelta;
			}
			if (prevexecresult == null || prevexecresult.shouldConsiderOutputLoadFailed()) {
				gothasdelta = true;
			} else {
				//XXX we should store the selected invokers for later when the tasks with the id is actually invoked
				TaskDependencies prevexecdependencies = prevexecresult.getDependencies();
				try {
					TaskInvocationConfiguration configuration = getTaskInvocationConfiguration(factory);
					boolean taskfactorychanged = !factory.equals(prevexecresult.getFactory());
					Supplier<? extends TaskInvocationManager.SelectionResult> invokerselectionresult = invocationManager
							.selectInvoker(taskid, configuration,
									taskfactorychanged ? null
											: prevexecdependencies.getEnvironmentPropertyDependenciesWithQualifiers(),
									null);

					if (!ObjectUtils.isNullOrEmpty(invokerselectionresult.get().getModifiedEnvironmentProperties())) {
						gothasdelta = true;
					} else {
						try {
							collectDependencyDeltasImpl(new AnyDependencyFinderDependencyDelta(), prevexecresult,
									context, transitivecreatedtask.getDirectoryPathContext(), this,
									invokerselectionresult, taskid, taskfactorychanged);
							gothasdelta = false;
						} catch (FoundDependencyDeltaException e) {
							gothasdelta = true;
						}
					}
				} catch (TaskEnvironmentSelectionFailedException e) {
					//if the invoker selection failed for a task, it must have some deltas
					gothasdelta = true;
				}
			}
//			Boolean checkedprev = checkedHasAnyDeltas.putIfAbsent(taskid, gothasdelta);
//			if (Boolean.TRUE.equals(checkedprev)) {
//				//some concurrent delta checker detected that the task has delta
//				gothasdelta = true;
//			}
			taskIdsHasDeltas.putIfAbsent(taskid, gothasdelta);
			return gothasdelta;
		}

		public void addFinishedRetrievalTest(TaskIdentifier taskid, ManagerTaskFutureImpl<?> future) {
			finishedRetrievalFutures.computeIfAbsent(taskid, Functionals.hashSetComputer()).add(future);
		}

		public void finishDependencyCollection(DependencyDelta deltas) {
			if (!deltas.isEmpty()) {
				//no need to analyze finish retrieved tasks, as we already have a delta, so the task will be rerun anyway
				return;
			}
			Set<ManagerTaskFutureImpl<?>> basefinishedretrievalfutures = finishedRetrievalFutures
					.remove(baseIdentifier);
			if (basefinishedretrievalfutures != null) {
				for (ManagerTaskFutureImpl<?> depfuture : basefinishedretrievalfutures) {
					if (!depfuture.isFinishedRetrievalAllowedForDeltas(baseIdentifier, baseDependencies, baseFuture)) {
						deltas.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
						deltas.resetFileDeltasCalculated();
						return;
					}
				}
			}
			if (!finishedRetrievalFutures.isEmpty()) {
				Map<TaskIdentifier, TransitivelyCreatedTask> transitivecreateds = getAllTransitiveCreatedTaskIds();
				for (Entry<TaskIdentifier, Set<ManagerTaskFutureImpl<?>>> entry : finishedRetrievalFutures.entrySet()) {
					TaskIdentifier taskid = entry.getKey();
					Set<ManagerTaskFutureImpl<?>> futures = entry.getValue();
					TransitivelyCreatedTask transcreate = transitivecreateds.get(taskid);
					for (ManagerTaskFutureImpl<?> fut : futures) {
						if (!fut.isFinishedRetrievalAllowedForDeltas(transcreate)) {
							//if we can't retrieve the finished result of future using only the transitively created tasks, check if we can using the base task
							if (!fut.isFinishedRetrievalAllowedForDeltas(taskid, baseDependencies, baseFuture)) {
								deltas.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
								deltas.resetFileDeltasCalculated();
								return;
							}
						}
					}
				}
			}
			for (Entry<TaskIdentifier, Boolean> entry : taskIdsHasDeltas.entrySet()) {
				Boolean changed = entry.getValue();
				Boolean prev = checkedHasAnyDeltas.putIfAbsent(entry.getKey(), changed);
				if (Boolean.TRUE.equals(prev)) {
					//the task changed meanwhile by some other detection
					deltas.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
					deltas.resetFileDeltasCalculated();
					return;
				}
			}
		}
	}

//	private DependencyDelta collectDependencyDeltas(TaskExecutionResult<?> prevexecresult, ExecutionContext context, TaskExecutionParameters parameters,
//			SimpleTaskDirectoryContext currenttaskdirectorycontext, TaskInvokerSelector.GuidedSelectionResult guidedinvokerselection, TaskFactory<?> factory,
//			TaskIdentifier taskid) {
//		TaskIdDependencyCollector collector = new TaskIdDependencyCollector(prevexecresult.getTaskIdentifier(), prevexecresult.getDependencies());
//		return collectDependencyDeltasImpl(prevexecresult, context, parameters, currenttaskdirectorycontext, guidedinvokerselection, factory, taskid,
//				collector);
//	}
//
//	private void collectDependencyDeltas(DependencyDelta result, TaskExecutionResult<?> prevexecresult, ExecutionContext context,
//			TaskExecutionParameters parameters, SimpleTaskDirectoryContext currenttaskdirectorycontext, TaskIdDependencyCollector depcollector,
//			TaskInvokerSelector.GuidedSelectionResult guidedinvokerselection, TaskIdentifier taskid, TaskFactory<?> factory) {
//		collectDependencyDeltasImpl(result, prevexecresult, context, parameters, currenttaskdirectorycontext, depcollector, guidedinvokerselection, taskid,
//				factory);
//	}

	private static class SimpleTaskDirectoryContext implements TaskDirectoryContext, TaskDirectoryPathContext {
		private SakerDirectory workingDirectory;
		private SakerPath taskWorkingDirectoryPath;
		private SakerDirectory buildDirectory;
		private SakerPath taskBuildDirectoryPath;

		public SimpleTaskDirectoryContext(SakerDirectory workingDirectory, SakerPath taskWorkingDirectoryPath,
				SakerDirectory buildDirectory, SakerPath taskBuildDirectoryPath) {
			this.workingDirectory = workingDirectory;
			this.taskWorkingDirectoryPath = taskWorkingDirectoryPath;
			this.buildDirectory = buildDirectory;
			this.taskBuildDirectoryPath = taskBuildDirectoryPath;
		}

		@Override
		public SakerDirectory getTaskWorkingDirectory() {
			return workingDirectory;
		}

		@Override
		public SakerDirectory getTaskBuildDirectory() {
			return buildDirectory;
		}

		@Override
		public SakerPath getTaskWorkingDirectoryPath() {
			return taskWorkingDirectoryPath;
		}

		@Override
		public SakerPath getTaskBuildDirectoryPath() {
			return taskBuildDirectoryPath;
		}
	}

	private static class SimpleTaskDirectoryPathContext implements TaskDirectoryPathContext {
		protected final SakerPath relativeTaskBuildDirectoryPath;
		protected final SakerPath absoluteTaskWorkingDirectoryPath;
		protected final SakerPath absoluteTaskBuildDirectoryPath;

		public SimpleTaskDirectoryPathContext(TaskExecutionManager executionManager,
				SakerPath absoluteTaskWorkingDirectoryPath, SakerPath relativeTaskBuildDirectoryPath) {
			SakerPathFiles.requireAbsolutePath(absoluteTaskWorkingDirectoryPath, "task working directory path");
			SakerPathFiles.requireRelativePath(relativeTaskBuildDirectoryPath, "task build directory path");

			this.relativeTaskBuildDirectoryPath = relativeTaskBuildDirectoryPath;
			this.absoluteTaskWorkingDirectoryPath = absoluteTaskWorkingDirectoryPath;
			if (executionManager.buildDirectoryPath != null) {
				this.absoluteTaskBuildDirectoryPath = executionManager.buildDirectoryPath
						.resolve(relativeTaskBuildDirectoryPath);
			} else {
				this.absoluteTaskBuildDirectoryPath = null;
			}
		}

		@Override
		public SakerPath getTaskBuildDirectoryPath() {
			return absoluteTaskBuildDirectoryPath;
		}

		public SakerPath getRelativeTaskBuildDirectoryPath() {
			return relativeTaskBuildDirectoryPath;
		}

		public SakerPath getAbsoluteTaskBuildDirectoryPath() {
			return absoluteTaskBuildDirectoryPath;
		}

		@Override
		public SakerPath getTaskWorkingDirectoryPath() {
			return absoluteTaskWorkingDirectoryPath;
		}

		@Override
		public String toString() {
			return "SimpleTaskDirectoryContext ["
					+ (relativeTaskBuildDirectoryPath != null
							? "taskBuildDirectoryPath=" + relativeTaskBuildDirectoryPath + ", "
							: "")
					+ (absoluteTaskWorkingDirectoryPath != null
							? "taskWorkingDirectoryPath=" + absoluteTaskWorkingDirectoryPath
							: "")
					+ "]";
		}

	}

	protected void collectDependencyDeltasImpl(DependencyDelta result, TaskExecutionResult<?> prevexecresult,
			ExecutionContextImpl executioncontext, SimpleTaskDirectoryPathContext taskdircontext,
			TaskIdDependencyCollector depcollector,
			Supplier<? extends TaskInvocationManager.SelectionResult> guidedinvokerselectionsupplier,
			TaskIdentifier taskid, boolean factorychanged) {
		TaskDependencies dependencies = prevexecresult.getDependencies();

		if (factorychanged) {
			result.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
			return;
		}
		if (!Objects.equals(prevexecresult.getExecutionWorkingDirectory(), taskdircontext.getTaskWorkingDirectoryPath())
				|| !Objects.equals(prevexecresult.getExecutionBuildDirectory(),
						taskdircontext.getAbsoluteTaskBuildDirectoryPath())) {
			//as the build directory or working directory related paths can be part of the results of the task
			//consider the tasks changed if the working or build directory paths have changed
			result.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
			return;
		}

		if (prevexecresult.shouldConsiderOutputLoadFailed()) {
			result.addNonFileDelta(OutputLoadFailedDeltaImpl.INSTANCE);
		}

		Set<? extends EnvironmentProperty<?>> modifiedenvpropertydeps;
		try {
			modifiedenvpropertydeps = guidedinvokerselectionsupplier.get().getModifiedEnvironmentProperties();
		} catch (TaskEnvironmentSelectionFailedException e) {
			result.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
			return;
		}
		if (!ObjectUtils.isNullOrEmpty(modifiedenvpropertydeps)) {
			for (EnvironmentProperty<?> property : modifiedenvpropertydeps) {
				result.addNonFileDelta(new EnvironmentDependencyDeltaImpl<>(property));
			}
		}

		//no tasks have changed, we expect the task to wait for the same tasks as it did during previous execution
		collectTaskChangeDeltas(result, executioncontext, depcollector, dependencies, taskid);

		if (!result.nonFileDeltas.contains(TaskChangeDeltaImpl.INSTANCE)) {
			//collect the execution property deltas after task change detection
			//    as waiting for a task might cause the properties to be different
			//e.g. a dependent task modifies a local file that we depend on using an execution property
			//file dependency collecting should be after determining the task dependencies.
			//    as waiting for a task might cause different files to be present

			//TODO if there were any previous deltas, we can delay the execution property detection
			//this can be done as if the task doesn't check the deltas, we unnecessarily calculate these.
			for (Entry<? extends ExecutionProperty<?>, ?> entry : dependencies.getExecutionPropertyDependencies()
					.entrySet()) {
				ExecutionProperty<?> property = entry.getKey();
				try {
					if (Objects.equals(executioncontext.getExecutionPropertyCurrentValue(property), entry.getValue())) {
						//it didnt change
						continue;
					}
				} catch (Exception e) {
				}
				//the delta changed
				result.addNonFileDelta(new ExecutionDependencyDeltaImpl<>(property));
			}

			//this has to be called last as this function depends on not adding any deltas after this call
			if (result.isEmpty()) {
				SimpleTaskDirectoryContext simpledirectorycontext = createTaskDirectoryContextForDeltaCollection(
						executioncontext, taskdircontext);

				result.collectFileDependencyDeltasDuringDeltaDiscovery(this, executioncontext, dependencies,
						simpledirectorycontext, taskid);
			}
		}
	}

	private SakerDirectory getTaskWorkingDirectoryForDeltaCollection(ExecutionContextImpl executioncontext,
			SimpleTaskDirectoryPathContext taskdircontext) {
		//do not use resolveDirectoryCreate methods, to not create and overwrite the files during delta discovery.
		SakerPath taskworkingdirectorypath = taskdircontext.getTaskWorkingDirectoryPath();
		SakerDirectory workingDirectory = SakerPathFiles.resolveDirectoryAtAbsolutePath(executioncontext,
				taskworkingdirectorypath);
		return workingDirectory;
	}

	private SimpleTaskDirectoryContext createTaskDirectoryContextForDeltaCollection(
			ExecutionContextImpl executioncontext, SimpleTaskDirectoryPathContext taskdircontext) {
		//do not use resolveDirectoryCreate methods, to not create and overwrite the files during delta discovery.
		//this code is same as in getTaskWorkingDirectoryForDeltaCollection
		SakerPath taskworkingdirectorypath = taskdircontext.getTaskWorkingDirectoryPath();
		SakerDirectory workingDirectory = SakerPathFiles.resolveDirectoryAtAbsolutePath(executioncontext,
				taskworkingdirectorypath);
		//the working directory may be null in this case
		SakerDirectory buildDirectory = this.buildSakerDirectory == null ? null
				: SakerPathFiles.resolveDirectoryAtRelativePath(this.buildSakerDirectory,
						taskdircontext.getRelativeTaskBuildDirectoryPath());
		SimpleTaskDirectoryContext simpledirectorycontext = new SimpleTaskDirectoryContext(workingDirectory,
				taskworkingdirectorypath, buildDirectory, taskdircontext.getTaskBuildDirectoryPath());
		return simpledirectorycontext;
	}

	private static boolean isTaskOutputChanged(TaskResultHolder<?> taskresult,
			ReportedTaskDependency reporteddependency, ExecutionContextImpl executioncontext) {
		TaskDependencies dependencies = reporteddependency.getDependencies();
		Object depmodstamp = dependencies.getBuildModificationStamp();
		TaskOutputChangeDetector dependencyselfchangedetector = dependencies.getSelfOutputChangeDetector();
		TaskOutputChangeDetector reportedchangedetector = reporteddependency.getOutputChangeDetector();
		return isTaskOutputChanged(taskresult, depmodstamp, dependencyselfchangedetector, reportedchangedetector,
				executioncontext);
	}

	private static boolean isTaskOutputChanged(TaskResultHolder<?> taskresult, Object depmodstamp,
			TaskOutputChangeDetector dependencyselfchangedetector, TaskOutputChangeDetector reportedchangedetector,
			ExecutionContextImpl executioncontext) {
		try {
			Object taskmodstamp = taskresult.getDependencies().getBuildModificationStamp();
			if (Objects.equals(taskmodstamp, depmodstamp)) {
				//the task was not run
				return false;
			}
			//the dependent task was run
			if (isTaskResultFailed(taskresult)) {
				//the task failed, and we consider that to be an output change
				//but only, if the dependent task re-run. if it hasn't rerun, then it is not changed
				return true;
			}
			Object taskoutput = taskresult.getOutput();
			if (dependencyselfchangedetector != null && !dependencyselfchangedetector.isChanged(taskoutput)) {
				//the change detector from the dependent task reported that the output has not changed
				//    it is semantically the same as the last output
				return false;
			}
			if (reportedchangedetector != null && !reportedchangedetector.isChanged(taskoutput)) {
				//the reported change detector determined that the task output have not semantically changed
				return false;
			}
			return true;
		} catch (RuntimeException e) {
			//catch any runtime exceptions thrown from the output change detectors, and return true in that case
			//XXX include task id and script trace?
			executioncontext.reportIgnoredException(ExceptionView.create(e));
			return true;
		}
	}

	private void collectTaskChangeDeltas(DependencyDelta result, ExecutionContextImpl context,
			TaskIdDependencyCollector depcollector, TaskDependencies dependencies, TaskIdentifier taskid) {
		Map<TaskIdentifier, ReportedTaskDependency> dependenttasks = dependencies.getTaskDependencies();
		if (dependenttasks.isEmpty()) {
			return;
		}
		class FutureWaiter {
			protected final ReportedTaskDependency dependency;
			protected final Set<ManagerTaskFutureImpl<?>> creatorFutures = new HashSet<>();

			public FutureWaiter(ReportedTaskDependency dependency) {
				this.dependency = dependency;
			}
		}

		Map<ManagerTaskFutureImpl<?>, FutureWaiter> checkfinishfutures = new HashMap<>();
		Set<ManagerTaskFutureImpl<?>> waitfutures = new HashSet<>();

		for (Entry<TaskIdentifier, ReportedTaskDependency> entry : dependenttasks.entrySet()) {
			TaskIdentifier deptaskid = entry.getKey();
			ReportedTaskDependency deptaskdeps = entry.getValue();
			ManagerTaskFutureImpl<?> future = getOrCreateTaskFuture(deptaskid);
			//task execution failed exceptions are propagated as an exception means that the build should be aborted
			TaskResultHolder<?> deptaskres = future.getResultIfReady();
			boolean finishedretrieval = deptaskdeps.isFinishedRetrieval();
			if (finishedretrieval) {
				//the task was retrieved in a finished state
				//check if it is still available for retrieval after waiting for the other tasks
				depcollector.addFinishedRetrievalTest(taskid, future);
			}
			if (deptaskres != null) {
				if (isTaskOutputChanged(deptaskres, deptaskdeps, executionContext)) {
					result.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
					return;
				}
				continue;
			}
			TaskExecutionResult<?> deptaskprevexecresult = getPreviousExecutionResult(deptaskid);
			if (deptaskprevexecresult == null || deptaskprevexecresult.shouldConsiderOutputLoadFailed()) {
				//failed to load the previous execution result for the task
				result.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
				return;
			}
			//the dependent task has not yet been spawned
			TransitivelyCreatedTask transitivecreatedtask = depcollector.getAllTransitiveCreatedTaskIds()
					.get(deptaskid);
			if (transitivecreatedtask != null) {
				//we might've created the task we're referencing
				//collect the deltas for it
				TaskFactory<?> depfuturefac = future.getFactory();
				if (depfuturefac == null) {
					depfuturefac = deptaskprevexecresult.getFactory();
				}
				if (depcollector.hasAnyDelta(deptaskid, context, transitivecreatedtask, depfuturefac,
						deptaskprevexecresult)) {
					result.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
					return;
				}
				continue;
			}
			FutureWaiter waiter = new FutureWaiter(deptaskdeps);
			collectTaskCreators(deptaskid, deptaskprevexecresult, waiter.creatorFutures);
			if (waiter.creatorFutures.isEmpty()) {
				//no creator task found, so we don't try to wait for the task
				result.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
				return;
			}
			checkfinishfutures.put(future, waiter);
			waitfutures.add(future);
			waitfutures.addAll(waiter.creatorFutures);

		}
		if (!checkfinishfutures.isEmpty()) {
			//XXX we should validate if we can still have deadlocks with this method
			//    possible scenario:
			//    all of the tasks are in the initialized state, and are collecting the dependencies.
			//    as all of them are pending, the condition is not satisfied
			//    can a circular deadlock occur in this case?
			WaiterThreadHandle threadhandle = new WaiterThreadHandle(
					ManagerTaskFutureImpl.EVENT_MASK_ALL_STATES | ManagerTaskFutureImpl.EVENT_WAITING_FOR_TASK_CHANGED);
			ManagerTaskFutureImpl.waitCondition(this, threadhandle, waitfutures, () -> {
				for (Iterator<Entry<ManagerTaskFutureImpl<?>, FutureWaiter>> it = checkfinishfutures.entrySet()
						.iterator(); it.hasNext();) {
					Entry<? extends ManagerTaskFutureImpl<?>, FutureWaiter> entry = it.next();
					ManagerTaskFutureImpl<?> fut = entry.getKey();
					FutureWaiter waiter = entry.getValue();
					ManagerTaskFutureImpl.FutureState futstate = fut.getFutureState();
					switch (futstate.state) {
						case ManagerTaskFutureImpl.STATE_EXECUTING: {
							if (isExecutingTaskShouldBeConsideredChanged(threadhandle, fut, futstate)) {
								result.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
								return true;
							}
							break;
						}
						case ManagerTaskFutureImpl.STATE_RESULT_READY: {
							if (!futstate.isSuccessfulFinish()
									|| isTaskOutputChanged(fut.getTaskResult(), waiter.dependency, executionContext)) {
								result.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
								return true;
							}
							//no need to check the state of this again, as it has a non changed result
							it.remove();
							break;
						}
						case ManagerTaskFutureImpl.STATE_UNSTARTED: {
							//the task is not yet started. check creator tasks
							if (waiter.creatorFutures.isEmpty()) {
								//all creator tasks have finished previously, and the dependent task have not yet started
								result.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
								return true;
							}
							boolean hasexecutingcreator = false;
							for (Iterator<ManagerTaskFutureImpl<?>> creatorit = waiter.creatorFutures
									.iterator(); creatorit.hasNext();) {
								ManagerTaskFutureImpl<?> creatorfut = creatorit.next();
								ManagerTaskFutureImpl.FutureState creatorfutstate = creatorfut.getFutureState();
								switch (creatorfutstate.state) {
									case ManagerTaskFutureImpl.STATE_EXECUTING: {
										if (isExecutingTaskShouldBeConsideredChanged(threadhandle, creatorfut,
												creatorfutstate)) {
											result.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
											return true;
										}
										break;
									}
									case ManagerTaskFutureImpl.STATE_RESULT_READY: {
										creatorit.remove();
										//no need to check again
										break;
									}
									case ManagerTaskFutureImpl.STATE_UNSTARTED: {
										break;
									}
									case ManagerTaskFutureImpl.STATE_INITIALIZING: {
										break;
									}
									case ManagerTaskFutureImpl.STATE_RESULT_DEADLOCKED: {
										//exception will be thrown
										return false;
									}
									default: {
										throw new AssertionError("Unknown task state: " + creatorfutstate.state);
									}
								}
							}
							if (waiter.creatorFutures.isEmpty()
									&& fut.getState() == ManagerTaskFutureImpl.STATE_UNSTARTED) {
								//there is no more executing commands or
								//all of the possible creator tasks have finished 
								//    requery the state of the dependent future
								//        as it might've changed while we checked the futures
								//    the dependent task is still in unstarted state so it won't be possibly started
								//    consider the task changed
								result.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
								return true;
							}
							if (!hasexecutingcreator) {
								//no creator is executing. so we can be sure that the task is not recreated by its previous commands
								//    we need to check the states again, because concurrent modifications can happen to them
								if (!hasNotUnstartedFuture(waiter.creatorFutures)
										&& fut.getState() == ManagerTaskFutureImpl.STATE_UNSTARTED) {
									//the states were not concurrently modified, no creator task was started meanwhile
									//this means that we can assume that the task was not recreated
									result.addNonFileDelta(TaskChangeDeltaImpl.INSTANCE);
									return true;
								}
							}
							//if we get here, then there must be at least one executing creator task
							break;
						}
						case ManagerTaskFutureImpl.STATE_INITIALIZING: {
							break;
						}
						case ManagerTaskFutureImpl.STATE_RESULT_DEADLOCKED: {
							//exception will be thrown
							return false;
						}
						default: {
							throw new AssertionError("Unknown task state: " + futstate.state);
						}
					}
				}
				if (checkfinishfutures.isEmpty()) {
					//all futures finished successfully and unchanged
					return true;
				}

				//there are still futures to wait for
				return false;
			});
		}
	}

	private static boolean hasNotUnstartedFuture(Iterable<? extends ManagerTaskFutureImpl<?>> futures) {
		for (ManagerTaskFutureImpl<?> fut : futures) {
			if (fut.getState() != ManagerTaskFutureImpl.STATE_UNSTARTED) {
				return true;
			}
		}
		return false;
	}

	private boolean isExecutingTaskShouldBeConsideredChanged(WaiterThreadHandle threadhandle,
			ManagerTaskFutureImpl<?> future, ManagerTaskFutureImpl.FutureState futstate) {
		//an executing task should be considered changed from a delta checking standpoint in the following scenarios:
		//    1) the task is waiting for an other task which it has not waited previously
		//    2) the task is waiting for an other task which should be considered changed
		//    3) the task has waited for a task that it has not waited for previously
		//    TODO check more scenarios
		TaskExecutorContext<?> taskcontext = ((ManagerTaskFutureImpl.ExecutingFutureState) futstate).taskContext;
		TaskExecutionResult<?> prevtaskres = taskcontext.prevTaskResult;
		if (prevtaskres == null) {
			return true;
		}
		Map<TaskIdentifier, ReportedTaskDependency> prevtaskdependencies = prevtaskres.getDependencies()
				.getTaskDependencies();
		if (!prevtaskdependencies.keySet().containsAll(taskcontext.resultDependencies.getTaskDependencies().keySet())) {
			//scenario 3)
			return true;
		}
		for (Entry<TaskIdentifier, ManagerTaskFutureImpl<?>> entry : taskcontext.getCurrentlyWaitingForTaskFutures()
				.entrySet()) {
			TaskIdentifier currentlywaitingtaskid = entry.getKey();
			if (!prevtaskdependencies.containsKey(currentlywaitingtaskid)) {
				//scenario 1)
				return true;
			}
			ManagerTaskFutureImpl<?> waitedfut = entry.getValue();
			FutureState waitedfutstate = waitedfut.getFutureState();
			if (waitedfutstate.state == ManagerTaskFutureImpl.STATE_EXECUTING) {
				//scenario 2)
				if (isExecutingTaskShouldBeConsideredChanged(threadhandle, waitedfut, waitedfutstate)) {
					return true;
				}
			} else {
				//else any changes in the waited task should be checked by us
				ManagerTaskFutureImpl.addWaitingFutureInCondition(waitedfut, threadhandle);
			}
			//TODO should we handle unstarted state of the waited task?
		}
		return false;
	}

	private DependencyDelta collectDependencyDeltasImpl(TaskExecutionResult<?> prevexecresult,
			ExecutionContextImpl context, SimpleTaskDirectoryPathContext taskdircontext,
			Supplier<? extends TaskInvocationManager.SelectionResult> guidedinvokerselection, boolean factorychanged,
			TaskIdentifier taskid, TaskIdDependencyCollector depcollector) {
		DependencyDelta result = new DependencyDelta();
		collectDependencyDeltasImpl(result, prevexecresult, context, taskdircontext, depcollector,
				guidedinvokerselection, taskid, factorychanged);
		return result;
	}

	private void collectTaskCreators(TaskIdentifier deptaskid, TaskExecutionResult<?> deptaskprevexecres,
			Collection<ManagerTaskFutureImpl<?>> resultfutures) {
		for (TaskIdentifier creatortaskid : deptaskprevexecres.getCreatedByTaskIds()) {
			collectTaskCreatorsImpl(creatortaskid, getPreviousExecutionResult(creatortaskid), resultfutures);
		}
	}

	private void collectTaskCreatorsImpl(TaskIdentifier deptaskid, TaskExecutionResult<?> deptaskprevexecres,
			Collection<ManagerTaskFutureImpl<?>> resultfutures) {
		ManagerTaskFutureImpl<?> fut = getOrCreateTaskFuture(deptaskid);
		if (resultfutures.add(fut)) {
			if (deptaskprevexecres != null) {
				for (TaskIdentifier creatortaskid : deptaskprevexecres.getCreatedByTaskIds()) {
					collectTaskCreatorsImpl(creatortaskid, getPreviousExecutionResult(creatortaskid), resultfutures);
				}
			}
		}
	}

	private Map<TaskIdentifier, TransitivelyCreatedTask> getAllTransitiveCreatedTaskIdsBasedOnPreviousResult(
			SimpleTaskDirectoryPathContext basedirectorypathcontext, ExecutionContextImpl context,
			TransitivelyCreatedTask basetranscreated) {
		Map<TaskIdentifier, TransitivelyCreatedTask> result = new HashMap<>();
		collectAllTransitiveCreatedTaskIdsBasedOnPreviousResult(result, basedirectorypathcontext, context,
				basetranscreated);
		return result;
	}

	private void collectAllTransitiveCreatedTaskIdsBasedOnPreviousResult(
			Map<TaskIdentifier, TransitivelyCreatedTask> result,
			SimpleTaskDirectoryPathContext basedirectorypathcontext, ExecutionContextImpl context,
			TransitivelyCreatedTask parenttranscreated) {
		TaskDependencies dependencies = parenttranscreated.getDependencies();
		for (Entry<TaskIdentifier, CreatedTaskDependency> entry : dependencies.getDirectlyCreatedTaskIds().entrySet()) {
			TaskIdentifier createdtaskid = entry.getKey();
			TransitivelyCreatedTask present = result.get(createdtaskid);
			if (present != null) {
				present.addCreator(parenttranscreated);
				continue;
			}
			SimpleTaskDirectoryPathContext createdtaskdirectorycontext = getTaskDirectoryPathContext(context,
					entry.getValue().getTaskParameters(), basedirectorypathcontext);
			TaskExecutionResult<?> prevexcres = getPreviousExecutionResult(createdtaskid);
			TaskDependencies createddependencies = prevexcres == null ? null : prevexcres.getDependencies();

			TransitivelyCreatedTask transcreated = new TransitivelyCreatedTask(createdtaskid,
					createdtaskdirectorycontext, createddependencies);
			transcreated.addCreator(parenttranscreated);
			result.put(createdtaskid, transcreated);
			if (prevexcres != null) {
				collectAllTransitiveCreatedTaskIdsBasedOnPreviousResult(result, createdtaskdirectorycontext, context,
						transcreated);
			}
		}
	}

	protected void collectFileDependencyDeltas(ExecutionContextImpl executioncontext, TaskDependencies dependencies,
			DependencyDelta result, SimpleTaskDirectoryContext directorycontext) {
		Map<Object, FileDependencies> taggedfiledeps = dependencies.getTaggedFileDependencies();
		if (taggedfiledeps.isEmpty()) {
			return;
		}
		ConcurrentPrependAccumulator<Runnable> deltarunnables = new ConcurrentPrependAccumulator<>();
		ConcurrentPrependAccumulator<FileChangeDelta> adddeltas = new ConcurrentPrependAccumulator<>();

		for (Entry<Object, FileDependencies> fdepentry : taggedfiledeps.entrySet()) {
			Object filedeptag = NullFileDependencyTag.nullize(fdepentry.getKey());
			FileDependencies filedependencies = fdepentry.getValue();
			TaggedFileDependencyDelta taggedfiledelta = result.getTaggedFileDelta(filedeptag);

			NavigableMap<SakerPath, ContentDescriptor> inputdeps = filedependencies.getInputFileDependencies();
			NavigableMap<SakerPath, ContentDescriptor> inputdependencies = collectDependenciesMap(inputdeps,
					directorycontext);
			if (!inputdeps.isEmpty()) {
				deltarunnables.add(() -> {
					EntryAccumulator<SakerPath, SakerFile> collectedfiles = PartitionedEntryAccumulatorArray
							.create(inputdependencies.size());
					forEachSakerFile(inputdependencies, executioncontext, directorycontext.getTaskWorkingDirectory(),
							(path, file, expectedcontent) -> {
								if (file == null) {
									if (expectedcontent.isChanged(NonExistentContentDescriptor.INSTANCE)) {
										adddeltas.add(new FileChangeDeltaImpl(DeltaType.INPUT_FILE_CHANGE, filedeptag,
												path, null));
									}
								} else {
									collectedfiles.put(path, file);
									ContentDescriptor currentcontent = file.getContentDescriptor();
									if (expectedcontent.isChanged(currentcontent)) {
										adddeltas.add(new FileChangeDeltaImpl(DeltaType.INPUT_FILE_CHANGE, filedeptag,
												path, file));
									}
								}
							});
					taggedfiledelta.setInputDependencies(ObjectUtils.createImmutableNavigableMapFromSortedIterator(
							collectedfiles.iterator(), collectedfiles.size()));
				});
			}

			NavigableMap<SakerPath, ContentDescriptor> outputdeps = filedependencies.getOutputFileDependencies();
			if (!outputdeps.isEmpty()) {
				deltarunnables.add(() -> {
					NavigableMap<SakerPath, ContentDescriptor> outputdependencies = collectDependenciesMap(outputdeps,
							directorycontext);
					EntryAccumulator<SakerPath, SakerFile> collectedfiles = PartitionedEntryAccumulatorArray
							.create(outputdependencies.size());
					forEachSakerFile(outputdependencies, executioncontext, directorycontext.getTaskWorkingDirectory(),
							(path, file, expectedcontent) -> {
								if (file == null) {
									if (expectedcontent.isChanged(NonExistentContentDescriptor.INSTANCE)) {
										adddeltas.add(new FileChangeDeltaImpl(DeltaType.OUTPUT_FILE_CHANGE, filedeptag,
												path, null));
									}
								} else {
									collectedfiles.put(path, file);
									ContentDescriptor currentcontent = file.getContentDescriptor();
									if (expectedcontent.isChanged(currentcontent)) {
										adddeltas.add(new FileChangeDeltaImpl(DeltaType.OUTPUT_FILE_CHANGE, filedeptag,
												path, file));
									}
								}
							});
					taggedfiledelta.setOutputDependencies(ObjectUtils.createImmutableNavigableMapFromSortedIterator(
							collectedfiles.iterator(), collectedfiles.size()));
				});
			}

			Set<FileCollectionStrategy> additiondeps = filedependencies.getAdditionDependencies();
			if (!additiondeps.isEmpty()) {
				for (FileCollectionStrategy additiondep : additiondeps) {
					deltarunnables.add(() -> {
						NavigableMap<SakerPath, ? extends SakerFile> files = result.fileAdditionDependencies
								.computeIfAbsent(additiondep, ad -> {
									NavigableMap<SakerPath, ? extends SakerFile> collectedfiles;
									try {
										collectedfiles = ad.collectFiles(executioncontext, directorycontext);
									} catch (RuntimeException e) {
										//XXX include more information in the FileCollectionStrategy failure? 
										executioncontext.reportIgnoredException(ExceptionView.create(e));
										//catch any exception that the collection strategy might throw
										//use an empty navigable map in that case
										return Collections.emptyNavigableMap();
									}
									if (!SakerPathFiles.hasRelativePath(collectedfiles)) {
										return collectedfiles;
									}
									TreeMap<SakerPath, SakerFile> collectedmap = new TreeMap<>(
											SakerPathFiles.getPathSubMapAbsolutes(collectedfiles));
									NavigableMap<SakerPath, ? extends SakerFile> collectedrelatives = SakerPathFiles
											.getPathSubMapRelatives(collectedfiles);
									if (!collectedrelatives.isEmpty()) {
										SakerPath workingdirpath = directorycontext.getTaskWorkingDirectoryPath();
										for (Entry<SakerPath, ? extends SakerFile> entry : collectedrelatives
												.entrySet()) {
											collectedmap.put(workingdirpath.resolve(entry.getKey()), entry.getValue());
										}
									}
									return collectedmap;
								});
						if (!files.isEmpty()) {
							NavigableMap<SakerPath, ContentDescriptor> inputcheckmap = inputdependencies
									.subMap(files.firstKey(), true, files.lastKey(), true);
							ObjectUtils.iterateSortedMapEntries(files, inputcheckmap, (path, addfile, inputdepfile) -> {
								if (inputdepfile == null) {
									//the file was not reported as input dependency, so it is considered an addition
									adddeltas.add(new FileChangeDeltaImpl(DeltaType.INPUT_FILE_ADDITION, filedeptag,
											path, addfile));
								}
							});
						}
					});
				}
			}
		}
		fileDeltaParallelRunner.runRunnables(deltarunnables.clearAndIterable());
		result.addFileDeltas(adddeltas);
	}

	private static NavigableMap<SakerPath, ContentDescriptor> collectDependenciesMap(
			NavigableMap<SakerPath, ContentDescriptor> deps, TaskDirectoryPathContext directorycontext) {
		if (deps.isEmpty()) {
			return Collections.emptyNavigableMap();
		}
		NavigableMap<SakerPath, ContentDescriptor> relatives = SakerPathFiles.getPathSubMapRelatives(deps);
		if (relatives.isEmpty()) {
			return deps;
		}
		SakerPath taskworkingdir = directorycontext.getTaskWorkingDirectoryPath();
		NavigableMap<SakerPath, ContentDescriptor> result = new TreeMap<>(SakerPathFiles.getPathSubMapAbsolutes(deps));
		//XXX if all relatives are forward relative, we could be more efficient
		//XXX handle somehow the collisions?
		for (Entry<SakerPath, ContentDescriptor> entry : relatives.entrySet()) {
			result.put(taskworkingdir.resolve(entry.getKey()), entry.getValue());
		}
		return result;
	}

	//TODO write tests for handling relative dependencies

	private <R> void executeTaskRunning(TaskExecutionResult<?> previousExecutionResult, TaskIdentifier taskid,
			ExecutionContextImpl executioncontext, ManagerTaskFutureImpl<R> future, TaskFactory<R> factory,
			DependencyDelta deltas, TaskExecutionParameters parameters, TaskInvocationConfiguration capabilities,
			Supplier<? extends TaskInvocationManager.SelectionResult> invokerselectionresultsupplier,
			SimpleTaskDirectoryPathContext taskdircontext, SpawnedResultTask spawnedtask) {
		executedAnyTask = true;
		//TODO allow tasks to be able to run even when no deltas have changed for it
		if (TestFlag.ENABLED) {
			if (deltas.isEmpty()) {
				throw new AssertionError("Running task: " + taskid + " -- " + factory + " with empty deltas.");
			}
			TestFlag.metric().runningTask(taskid, factory, deltas.nonFileDeltas);
		}
		ConcurrentAppendAccumulator<TaskExecutionEvent> events = new ConcurrentAppendAccumulator<>();
		SakerPath absbuilddir = taskdircontext.getAbsoluteTaskBuildDirectoryPath();
		TaskExecutionResult<R> executionresult = new TaskExecutionResult<>(taskid, factory, parameters,
				taskdircontext.getTaskWorkingDirectoryPath(), absbuilddir, events,
				absbuilddir == null ? null : executioncontext.getPathConfiguration().getPathKey(absbuilddir));
		if (previousExecutionResult != null) {
			executionresult.setCreatedByTaskIds(previousExecutionResult.getCreatedByTaskIds());
			if (previousExecutionResult.isRootTask()) {
				//do not lose the root task flag
				executionresult.setRootTask(true);
			}
		}
		//TODO handle failure of task invoker selection
		TaskInvocationManager.SelectionResult invokerselectionresult = invokerselectionresultsupplier.get();
		TaskDependencies executiondependencies = executionresult.getDependencies();
		executiondependencies.setEnvironmentPropertyQualifiersDependencies(
				invokerselectionresult.getQualifierEnvironmentProperties());
		TaskExecutorContext<R> taskcontext = new TaskExecutorContext<>(this, taskid, executioncontext,
				previousExecutionResult, executionresult, deltas, capabilities, future, taskdircontext, events);
		boolean startset = future.startExecution(this, taskcontext);
		if (TestFlag.ENABLED && !startset) {
			throw new AssertionError("Failed to start execution on future.");
		}

		Throwable taskrunningexception = null;
		try {
			R result = null;
			try {
				TaskInvocationResult<R> taskinvocationresult = invocationManager.invokeTaskRunning(factory,
						capabilities, invokerselectionresult, taskcontext);
				taskrunningexception = taskinvocationresult.getThrownException();
				result = ObjectUtils.getOptional(taskinvocationresult.getResult());
			} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError | AssertionError
					| Exception e) {
				//catch some common errors, and exceptions
				//more specific Error types are not caught as they signal a more serious error
				taskrunningexception = e;
			} finally {
				try {
					taskcontext.executionFinished();
				} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError
						| AssertionError | Exception e) {
					taskrunningexception = IOUtils.addExc(taskrunningexception, e);
				}
			}
			Iterator<String> printedlinesit = taskcontext.printedLines.clearAndIterator();
			List<String> setlines;
			if (printedlinesit.hasNext()) {
				setlines = new ArrayList<>();
				ObjectUtils.addAll(setlines, printedlinesit);
			} else {
				setlines = Collections.emptyList();
			}
			if (TestFlag.ENABLED) {
				TestFlag.metric().taskLinesPrinted(taskid, setlines);
			}
			executionresult.setPrintedLines(setlines);
			if (previousExecutionResult != null) {
				Set<TaskIdentifier> prevdirectlycreatedtaskids = new HashSet<>(
						previousExecutionResult.getDependencies().getDirectlyCreatedTaskIds().keySet());
				prevdirectlycreatedtaskids.removeAll(executiondependencies.getDirectlyCreatedTaskIds().keySet());
				recordTasksNotRecreated(taskid, prevdirectlycreatedtaskids);
			}
			try {
				List<Throwable> abortexceptions = ImmutableUtils.makeImmutableList(taskcontext.abortExceptions);
				if (taskrunningexception != null) {
					//do not put the execution result to the task results, as it has failed via an exception, but not abortion
					//the previous execution result will be added when the build is finished

					executionresult.setFailedOutput(taskrunningexception, abortexceptions, result, buildUUID);
					spawnedtask.executionFailed(taskrunningexception, abortexceptions);
					future.failed(this, taskrunningexception, abortexceptions, taskcontext.resultDependencies);
					taskRunningFailureExceptions.add(ImmutableUtils.makeImmutableMapEntry(taskid,
							createFailException(taskid, taskrunningexception, abortexceptions)));
					return;
				}

				executiondependencies.setSelfOutputChangeDetector(taskcontext.reportedOutputChangeDetector);

				boolean hasabortedexception = !ObjectUtils.isNullOrEmpty(abortexceptions);
				if (hasabortedexception) {
					executionresult.setFailedOutput(taskrunningexception, abortexceptions, result, buildUUID);
				} else {
					executionresult.setOutput(result, buildUUID);
				}
				spawnedtask.executionFinished(executionresult);
				putTaskToResults(taskid, executionresult);

				future.finished(this, executionresult);

				if (TestFlag.ENABLED) {
					//call this after the future.finished() call
					TestFlag.metric().taskFinished(taskid, factory, result, executionresult.getTaggedOutputs(),
							executionresult.getMetaDatas());
				}

				if (hasabortedexception) {
					taskRunningFailureExceptions.add(ImmutableUtils.makeImmutableMapEntry(taskid,
							createFailException(taskid, taskrunningexception, abortexceptions)));
					return;
				}

				if (capabilities.isCacheable()) {
					cacheableTaskResults.putIfAbsent(taskid, executionresult);
				}
			} finally {
				taskcontext.finishExecutionDependencies();
			}
		} finally {
			taskcontext.flushStdStreamsFinalizeExecution();
		}
	}

	private <R> boolean executeCachedNewTaskEntryRetrieve(TaskCacheEntry cached, TaskIdentifier taskid,
			ExecutionContextImpl executioncontext, ManagerTaskFutureImpl<R> future, TaskFactory<R> factory,
			TaskExecutionParameters parameters, SimpleTaskDirectoryPathContext taskdircontext,
			SpawnedResultTask spawnedtask) {
		Map<Object, FileDependencies> taggedfiledeps;
		Map<CachedTaskFinishedDependency, TaskResultHolder<?>> resmappedtaskdeps = Collections.emptyMap();

		Object cachedmodificationstamp;
		R result;
		Map<?, ?> taggedouts;
		TaskOutputChangeDetector selfoutputdetector;
		Collection<? extends IDEConfiguration> ideconfigs;
		Iterable<? extends TaskExecutionEvent> events;
		List<String> setlines;
		Map<TaskIdentifier, CreatedTaskDependency> createdtasks;

		ConcurrentEntryMergeSorter<SakerPath, ContentDescriptor> outputfiles = new ConcurrentEntryMergeSorter<>();

		try {
			TaskFactory<?> cachedfactory = cached.getFactory();
			if (!factory.equals(cachedfactory)) {
				//the task factory associated to the cache entry is different
				return false;
			}
			List<? extends CachedTaskFinishedDependency> taskdeps = cached.getTaskDependencies();

			if (!taskdeps.isEmpty()) {
				resmappedtaskdeps = new HashMap<>();
				for (CachedTaskFinishedDependency dep : taskdeps) {
					TaskIdentifier deptaskid = dep.getTaskIdentifier();
					ManagerTaskFutureImpl<?> depfut = taskIdFutures.get(deptaskid);
					if (depfut == null) {
						//the dependent task has not yet spawned, not useable
						return false;
					}
					FutureState futstate = depfut.getFutureState();
					if (futstate.state != ManagerTaskFutureImpl.STATE_RESULT_READY || !futstate.isSuccessfulFinish()) {
						//the task result should be available by now, as cacheable tasks may only depend on finished tasks
						return false;
					}
					TaskResultHolder<?> deptaskres = futstate.getTaskResult();
					if (isTaskResultFailed(deptaskres)) {
						//we expect the task dependencies to all succeed.
						//this one failed, we don't use the cache entry
						return false;
					}
					if (isTaskOutputChanged(deptaskres, dep.getDependencyBuildModificationStamp(),
							dep.getDependencyTaskSelfOutputChangeDetector(), dep.getReportedOutputChangeDetector(),
							executioncontext)) {
						//the dependent task has changed, not reuseable
						return false;
					}
					if (!depfut.isFinishedRetrievalAllowedForDeltas(taskid, future)) {
						//the dependent task is no longer finish retriveable
						return false;
					}
					resmappedtaskdeps.put(dep, deptaskres);
				}
			}
			Map<? extends EnvironmentProperty<?>, ?> envdeps = cached
					.getEnvironmentPropertyDependenciesWithQualifiers();
			if (SakerEnvironmentImpl.hasAnyEnvironmentPropertyDifference(executioncontext.getEnvironment(), envdeps)) {
				//environment properties have been modified, cache entry not reuseable
				return false;
			}
			Map<? extends ExecutionProperty<?>, ?> execdeps = cached.getExecutionPropertyDependencies();
			if (executioncontext.hasAnyExecutionPropertyDifference(execdeps)) {
				//execution properties have been modified, cache entry not reusable.
				return false;
			}

			taggedfiledeps = cached.getTaggedFileDependencies();
			if (!taggedfiledeps.isEmpty()) {
				//XXX make these delta discoveries more efficient, by paralellizing or other methods?
				//XXX there are shared code in these file detections and the delta collecting method
				try {
					Map<FileCollectionStrategy, NavigableMap<SakerPath, ? extends SakerFile>> collectedfileadditiondependencies = new HashMap<>();
					for (FileDependencies fdep : taggedfiledeps.values()) {
						Set<FileCollectionStrategy> additiondeps = fdep.getAdditionDependencies();
						NavigableMap<SakerPath, ContentDescriptor> inputdeps = fdep.getInputFileDependencies();
						NavigableMap<SakerPath, ContentDescriptor> outputdeps = fdep.getOutputFileDependencies();
						NavigableMap<SakerPath, ContentDescriptor> inputdependencies = collectDependenciesMap(inputdeps,
								taskdircontext);
						if (!inputdeps.isEmpty()) {
							//use equality in content descriptor checking so they provide a stronger match for cached task retrieval
							//handle the common task content descriptors specially, as they can't be compared using equality for the input dependencies

							forEachSakerFile(inputdependencies, executioncontext,
									getTaskWorkingDirectoryForDeltaCollection(executioncontext, taskdircontext),
									(path, file, expectedcontent) -> {
										if (file == null) {
											if (!CommonTaskContentDescriptors.NOT_PRESENT.equals(expectedcontent)
													&& !NonExistentContentDescriptor.INSTANCE.equals(expectedcontent)) {
												throw FoundDependencyDeltaException.INSTANCE;
											}
										} else {
											if (!CommonTaskContentDescriptors.PRESENT.equals(expectedcontent)) {
												ContentDescriptor currentcontent = file.getContentDescriptor();
												if (!Objects.equals(currentcontent, expectedcontent)) {
													throw FoundDependencyDeltaException.INSTANCE;
												}
											}
										}
									});
						}
						if (!additiondeps.isEmpty()) {
							SimpleTaskDirectoryContext directorycontext = createTaskDirectoryContextForDeltaCollection(
									executioncontext, taskdircontext);
							for (FileCollectionStrategy additiondep : additiondeps) {
								NavigableMap<SakerPath, ? extends SakerFile> files = collectedfileadditiondependencies
										.computeIfAbsent(additiondep, ad -> {
											FileCollectionStrategy collectstrategy = ad;
											NavigableMap<SakerPath, ? extends SakerFile> collectedfiles;
											try {
												collectedfiles = collectstrategy.collectFiles(executioncontext,
														directorycontext);
											} catch (RuntimeException e) {
												//XXX include script trace and task id?
												executioncontext.reportIgnoredException(ExceptionView.create(e));
												//catch any exception that the collection strategy might throw
												//use an empty navigable map in that case
												return Collections.emptyNavigableMap();
											}
											if (!SakerPathFiles.hasRelativePath(collectedfiles)) {
												return collectedfiles;
											}
											TreeMap<SakerPath, SakerFile> collectedmap = new TreeMap<>(
													SakerPathFiles.getPathSubMapAbsolutes(collectedfiles));
											NavigableMap<SakerPath, ? extends SakerFile> collectedrelatives = SakerPathFiles
													.getPathSubMapRelatives(collectedfiles);
											if (!collectedrelatives.isEmpty()) {
												SakerPath workingdirpath = directorycontext
														.getTaskWorkingDirectoryPath();
												for (Entry<SakerPath, ? extends SakerFile> entry : collectedrelatives
														.entrySet()) {
													collectedmap.put(workingdirpath.resolve(entry.getKey()),
															entry.getValue());
												}
											}
											return collectedmap;
										});
								if (!files.isEmpty()) {
									NavigableMap<SakerPath, ContentDescriptor> inputcheckmap = inputdependencies
											.subMap(files.firstKey(), true, files.lastKey(), true);
									ObjectUtils.iterateSortedMapEntries(files, inputcheckmap,
											(path, addfile, inputdepfile) -> {
												if (inputdepfile == null) {
													//the file was not reported as input dependency, so it is considered an addition
													throw FoundDependencyDeltaException.INSTANCE;
												}
											});
								}
							}
						}
						if (!outputdeps.isEmpty()) {
							outputfiles.add(outputdeps);
						}
					}
				} catch (FoundDependencyDeltaException e) {
					return false;
				}
			}

			cachedmodificationstamp = cached.getModificationStamp();
			//safa cast as we already ensured that the task factories equal
			@SuppressWarnings("unchecked")
			R taskoutput = (R) cached.getTaskOutput();
			result = taskoutput;
			taggedouts = cached.getTaggedOutputs();
			selfoutputdetector = cached.getSelfOutputChangeDetector();
			ideconfigs = cached.getIDEConfigurations();
			events = cached.getEvents();
			setlines = cached.getPrintedLines();
			createdtasks = cached.getDirectoryCreatedTasks();
		} catch (BuildCacheException | IOException | ClassNotFoundException e) {
			//failed to retrieve all information related to the cache entry, do not use it.
			executioncontext.reportIgnoredException(taskid, TaskIdentifierExceptionView.create(e));
			return false;
		}
		//any exception happening after this point should be considered a failure in regards with the task execution
		//   as retrieving the results from the cache is not atomic, the other tasks may see this task in an intermediate state if an exception happens
		executedAnyTask = true;

		TaskExecutionResult<R> executionresult;
		TaskDependencies execresdeps;
		try {
			//any runtime exception in this block is an internal implementation error.

			SakerPath absbuilddir = taskdircontext.getAbsoluteTaskBuildDirectoryPath();
			ExecutionPathConfiguration pathconfig = executioncontext.getPathConfiguration();
			executionresult = new TaskExecutionResult<>(taskid, factory, parameters,
					taskdircontext.getTaskWorkingDirectoryPath(), absbuilddir, events,
					absbuilddir == null ? null : pathconfig.getPathKey(absbuilddir));

			execresdeps = executionresult.getDependencies();
			execresdeps.setSelfOutputChangeDetector(selfoutputdetector);
			execresdeps.setTaggedFileDependencies(taggedfiledeps);
			execresdeps.setDirectlyCreatedTaskIds(createdtasks);
			for (Entry<CachedTaskFinishedDependency, TaskResultHolder<?>> entry : resmappedtaskdeps.entrySet()) {
				CachedTaskFinishedDependency finishdep = entry.getKey();
				execresdeps.addTaskDependency(finishdep.getTaskIdentifier(), entry.getValue().getDependencies(), true,
						finishdep.getReportedOutputChangeDetector());
			}
		} catch (RuntimeException e) {
			throw new AssertionError("Failed to handle build cached task result: " + taskid, e);
		}
		try {
			future.updateInitialization(execresdeps);
			if (outputfiles.isAnyIterableAdded()) {
				//TODO error handle any output file synchronization. how should that be done?
				NavigableMap<String, ? extends SakerDirectory> executionrootnames = executioncontext
						.getRootDirectories();
				NavigableMap<SakerPath, BuildCacheSakerFile> outsynchfiles = new TreeMap<>();
				//TODO bulk file content cache field entry lookup
				Iterator<? extends Entry<? extends SakerPath, ? extends ContentDescriptor>> mergedit = outputfiles
						.iterator();
				while (mergedit.hasNext()) {
					Entry<? extends SakerPath, ? extends ContentDescriptor> fileentry = mergedit.next();

					SakerPath resultfilepath = fileentry.getKey();
					if (resultfilepath.isRelative()) {
						resultfilepath = taskdircontext.getTaskWorkingDirectoryPath().resolve(resultfilepath);
					}
					if (!executionrootnames.containsKey(resultfilepath.getRoot())) {
						return false;
					}
					ContentDescriptor contentdescriptor = fileentry.getValue();
					BuildCacheSakerFile cachedfile = new BuildCacheSakerFile(resultfilepath.getFileName(),
							cached.getFileFieldEntry(resultfilepath), contentdescriptor);
					outsynchfiles.put(resultfilepath, cachedfile);
				}
				//TODO improve the performance of the following loop. Parallel synchronization, incremental directory lookup, etc... 
				for (Entry<SakerPath, BuildCacheSakerFile> syncentry : outsynchfiles.entrySet()) {
					SakerPath path = syncentry.getKey();
					SakerPath dirpath = path.getParent();
					BuildCacheSakerFile syncfile = syncentry.getValue();
					SakerPathFiles.resolveDirectoryAtAbsolutePathCreate(executioncontext, dirpath).add(syncfile);
					syncfile.synchronize();
				}
			}
		} catch (BuildCacheException | IOException e) {
			throw ExceptionAccessInternal
					.createTaskExecutionFailedException("Failed to retrieve task results from build cache.", e, taskid);
		}
		//any exception happening after this point should be considered a fatal implementation error
		//exceptions happening after this point signals an internal implementation error
		//after the files have been retrieved and synchronized from the cache, the remaining task result handling should be strictly error-free
		try {
			if (!createdtasks.isEmpty()) {
				//XXX some code is duplicated here and in the unchanged task starter
				for (Entry<TaskIdentifier, CreatedTaskDependency> entry : createdtasks.entrySet()) {
					TaskIdentifier deptaskid = entry.getKey();
					CreatedTaskDependency taskdep = entry.getValue();

					SpawnedResultTask createdspawn = getCreateSpawnedTask(deptaskid);
					spawnedtask.addChild(deptaskid, createdspawn);

					@SuppressWarnings("rawtypes")
					TaskFactory subtaskfactory = taskdep.getFactory();
					@SuppressWarnings("rawtypes")
					ManagerTaskFutureImpl[] outfuture = { null };
					boolean starting = initializeExecutionWithTaskFuture(deptaskid, subtaskfactory, taskid, outfuture,
							future, getTaskInvocationConfiguration(subtaskfactory));
					if (!starting) {
						continue;
					}
					ManagerTaskFutureImpl<?> depfuture = outfuture[0];
					TaskExecutionParameters deptaskparams = taskdep.getTaskParameters();
					//XXX inline this function
					startUnchangedTaskWithInitializedFuture(executioncontext, depfuture, deptaskparams, taskdircontext,
							future);
				}
			}

			executionresult.setPrintedLines(setlines);
			executionresult.addIDEConfigurations(ideconfigs);
			executionresult.setOutput(result, cachedmodificationstamp);
			executionresult.addTaggedOutputs(taggedouts);

			spawnedtask.executionFinished(executionresult);
			putTaskToResults(taskid, executionresult);

			future.finished(this, executionresult);

			printLinesOfExecutionResult(executioncontext, executionresult, future);

			runIdeConfigurations.addAll(ideconfigs);

			if (TestFlag.ENABLED) {
				//delay the test notifications until we can surely return true
				TestFlag.metric().taskFinished(taskid, factory, result, executionresult.getTaggedOutputs(),
						executionresult.getMetaDatas());
				TestFlag.metric().taskRetrievedFromCache(taskid);
			}
			return true;

		} catch (RuntimeException e) {
			throw new AssertionError("Failed to handle build cache task result for task: " + taskid, e);
		}
	}

	private static class BuildCacheSakerFile extends SakerFileBase {
		private FieldEntry cacheField;
		private ContentDescriptor contentDescriptor;

		public BuildCacheSakerFile(String name, FieldEntry cacheField, ContentDescriptor contentDescriptor)
				throws NullPointerException, InvalidPathFormatException {
			super(name);
			this.cacheField = cacheField;
			this.contentDescriptor = contentDescriptor;
		}

		@Override
		public ContentDescriptor getContentDescriptor() {
			return contentDescriptor;
		}

		@Override
		public void writeToStreamImpl(OutputStream os) throws IOException, NullPointerException {
			cacheField.writeDataTo(ByteSink.valueOf(os));
		}

		@Override
		public ByteArrayRegion getBytesImpl() throws IOException {
			return cacheField.getData();
		}

	}

	private <R> boolean executeCachedNewTaskRetrieve(TaskIdentifier taskid, ExecutionContextImpl executioncontext,
			ManagerTaskFutureImpl<R> future, TaskFactory<R> factory, TaskExecutionParameters parameters,
			SimpleTaskDirectoryPathContext taskdircontext, SpawnedResultTask spawnedtask) {
		Collection<? extends TaskCacheEntry> cachedtasks = Collections.emptyList();
		try {
			cachedtasks = buildCache.lookupTask(taskid);
			for (TaskCacheEntry cached : cachedtasks) {
				if (executeCachedNewTaskEntryRetrieve(cached, taskid, executioncontext, future, factory, parameters,
						taskdircontext, spawnedtask)) {
					return true;
				}
			}
		} catch (IOException | BuildCacheException e) {
			executioncontext.reportIgnoredException(taskid, TaskIdentifierExceptionView.create(e));
		} finally {
			IOException closeexc = IOUtils.closeExc(cachedtasks);
			if (closeexc != null) {
				executioncontext.reportIgnoredException(taskid, TaskIdentifierExceptionView.create(closeexc));
			}
		}
		return false;
	}

	private void putTaskToResults(TaskIdentifier taskid, TaskExecutionResult<?> executionresult) throws AssertionError {
		TaskExecutionResult<?> prevputres = resultTaskIdTaskResults.putIfAbsent(taskid, executionresult);
		if (prevputres != null) {
			throw new AssertionError("Task with id is present multiple times. (" + taskid + ")");
		}
	}

	protected static <R> R getOutputOrThrow(TaskResultHolder<R> result) throws TaskExecutionFailedException {
		List<? extends Throwable> abortexc = result.getAbortExceptions();
		Throwable failexc = result.getFailCauseException();
		if (failexc != null || !ObjectUtils.isNullOrEmpty(abortexc)) {
			throw createFailException(result.getTaskIdentifier(), failexc, abortexc);
		}
		return result.getOutput();
	}

	protected static boolean isTaskResultFailed(TaskResultHolder<?> result) {
		return result.getFailCauseException() != null || !ObjectUtils.isNullOrEmpty(result.getAbortExceptions());
	}

	protected static TaskExecutionFailedException createFailException(TaskIdentifier taskid,
			Throwable taskrunningexception, List<? extends Throwable> abortexceptions) {
		Iterator<? extends Throwable> it;
		if (taskrunningexception == null) {
			if (ObjectUtils.isNullOrEmpty(abortexceptions)) {
				throw new AssertionError("Internal error: There must be a task exception, or aborted exceptions.");
			}
			it = abortexceptions.iterator();
			taskrunningexception = it.next();
		} else {
			if (ObjectUtils.isNullOrEmpty(abortexceptions)) {
				it = Collections.emptyIterator();
			} else {
				it = abortexceptions.iterator();
			}
		}
		TaskExecutionFailedException e = ExceptionAccessInternal
				.createTaskExecutionFailedException("Task execution failed.", taskrunningexception, taskid);
		while (it.hasNext()) {
			e.addSuppressed(it.next());
		}
		return e;
	}

	private <R> void executeNewTaskRunning(TaskIdentifier taskid, ExecutionContextImpl executioncontext,
			ManagerTaskFutureImpl<R> future, TaskFactory<R> factory, TaskExecutionParameters parameters,
			SimpleTaskDirectoryPathContext taskdircontext, TaskInvocationConfiguration capabilities,
			SpawnedResultTask spawnedtask) {
		boolean cachecapable = capabilities.isCacheable();
		if (cachecapable && buildCache != null) {
			//cacheable task, try to retrieve cached result
			if (executeCachedNewTaskRetrieve(taskid, executioncontext, future, factory, parameters, taskdircontext,
					spawnedtask)) {
				return;
			}
		}
		Supplier<? extends TaskInvocationManager.SelectionResult> invokerselectionresult = invocationManager
				.selectInvoker(taskid, capabilities, null, null);

		executeTaskRunning(null, taskid, executioncontext, future, factory, DependencyDelta.NEW_TASK_DELTA, parameters,
				capabilities, invokerselectionresult, taskdircontext, spawnedtask);
	}

	private static SakerPath getExecutionWorkingDirectoryPath(ExecutionContextImpl context,
			TaskExecutionParameters parameters, SimpleTaskDirectoryPathContext currentdirectorycontext) {
		SakerPath paramworkingdir = parameters.getWorkingDirectory();
		return getExecutionWorkingDirectoryPath(context, currentdirectorycontext, paramworkingdir);
	}

	private static SakerPath getExecutionWorkingDirectoryPath(ExecutionContextImpl context,
			SimpleTaskDirectoryPathContext currentdirectorycontext, SakerPath workingdirpath) {
		if (workingdirpath != null) {
			if (workingdirpath.isRelative()) {
				if (currentdirectorycontext == null) {
					return context.getWorkingDirectoryPath().resolve(workingdirpath);
				}
				return currentdirectorycontext.getTaskWorkingDirectoryPath().resolve(workingdirpath);
			}
			return workingdirpath;
		}
		return currentdirectorycontext.getTaskWorkingDirectoryPath();
	}

	private static SakerPath getExecutionBuildDirectoryPath(TaskExecutionParameters parameters,
			SakerPath currentbuilddirectory) {
		SakerPath paramdir = parameters.getBuildDirectory();
		if (paramdir == null) {
			return currentbuilddirectory;
		}
		return paramdir;
	}

	private void recordTasksNotRecreated(TaskIdentifier previouslycreatortaskid,
			Set<TaskIdentifier> notrecreatedtasks) {
		if (notrecreatedtasks.isEmpty()) {
			return;
		}
		for (TaskIdentifier prevcreatedtask : notrecreatedtasks) {
			TaskExecutionResult<?> createdprevexec = getPreviousExecutionResult(prevcreatedtask);
			if (createdprevexec != null) {
				createdprevexec.getCreatedByTaskIds().remove(previouslycreatortaskid);
			}
		}
	}

	private void recordCreatedBy(ManagerTaskFutureImpl<?> createdfuture, TaskIdentifier createdbytaskid) {
		createdfuture.storeCreatedBy(createdbytaskid);
	}

	//XXX the forEachSakerFile methods should be modified and put into task execution utils

	public static void forEachSakerFile(SortedSet<? extends SakerPath> paths, TaskContext taskcontext,
			BiConsumer<? super SakerPath, ? super SakerFile> fileconsumer) throws NullPointerException {
		forEachSakerFile(paths, taskcontext.getExecutionContext(), taskcontext.getTaskWorkingDirectory(), fileconsumer);
	}

	public static void forEachSakerFile(SortedSet<? extends SakerPath> paths,
			ExecutionDirectoryContext executiondirectorycontext, SakerDirectory workingdirectory,
			BiConsumer<? super SakerPath, ? super SakerFile> fileconsumer) throws NullPointerException {
		Objects.requireNonNull(fileconsumer, "file consumer");
		ObjectUtils.requireNaturalOrder(paths);

		if (paths.isEmpty()) {
			return;
		}
		Iterator<? extends SakerPath> it = paths.iterator();
		SakerDirectory abscurrentdir = null;
		SakerPath abscurrentpath = null;
		while (it.hasNext()) {
			SakerPath next = it.next();
			if (!next.isRelative()) {
				SakerPath parent = next.getParent();
				if (parent != null) {
					abscurrentdir = SakerPathFiles.resolveDirectoryAtAbsolutePath(executiondirectorycontext, parent);
					abscurrentpath = parent;
					if (abscurrentdir == null) {
						fileconsumer.accept(next, null);
					} else {
						fileconsumer.accept(next, abscurrentdir.get(next.getFileName()));
					}
				} else {
					//has no parent
					fileconsumer.accept(next, SakerPathFiles.resolveAtAbsolutePath(executiondirectorycontext, next));
				}
				break;
			}
			//XXX make a more efficient implementation instead of a simple getAtPath call for relative paths?
			fileconsumer.accept(next, SakerPathFiles.resolveAtRelativePath(workingdirectory, next));
		}
		//handle absolute paths
		while (it.hasNext()) {
			SakerPath next = it.next();
			cached_dir:
			if (abscurrentdir != null) {
				if (!next.getRoot().equals(abscurrentpath.getRoot())) {
					abscurrentdir = null;
					break cached_dir;
				}
				while (!next.startsWith(abscurrentpath)) {
					abscurrentpath = abscurrentpath.getParent();
					if (abscurrentpath == null) {
						abscurrentdir = null;
						break cached_dir;
					}
					abscurrentdir = abscurrentdir.getParent();
					if (abscurrentdir == null) {
						break cached_dir;
					}
				}
				//is a subpath
				int ndirnc = next.getNameCount() - 1;
				for (int i = abscurrentpath.getNameCount(); i < ndirnc; i++) {
					abscurrentdir = abscurrentdir.getDirectory(next.getName(i));
					if (abscurrentdir == null) {
						break cached_dir;
					}
				}
				//we located next cache dir
				abscurrentpath = next.getParent();
				fileconsumer.accept(next, abscurrentdir.get(next.getFileName()));
				continue;
			}
			SakerPath parent = next.getParent();
			if (parent != null) {
				abscurrentdir = SakerPathFiles.resolveDirectoryAtAbsolutePath(executiondirectorycontext, parent);
				if (abscurrentdir == null) {
					fileconsumer.accept(next, null);
				} else {
					abscurrentpath = parent;
					fileconsumer.accept(next, abscurrentdir.get(next.getFileName()));
				}
			} else {
				//has no parent, probably just a root path
				fileconsumer.accept(next, SakerPathFiles.resolveAtAbsolutePath(executiondirectorycontext, next));
			}
		}
	}

	public static <V> void forEachSakerFile(SortedMap<? extends SakerPath, V> paths, TaskContext taskcontext,
			TriConsumer<? super SakerPath, ? super SakerFile, ? super V> fileconsumer) throws NullPointerException {
		forEachSakerFile(paths, taskcontext.getExecutionContext(), taskcontext.getTaskWorkingDirectory(), fileconsumer);
	}

	public static <V> void forEachSakerFile(SortedMap<? extends SakerPath, V> paths,
			ExecutionDirectoryContext executiondirectorycontext, SakerDirectory workingdirectory,
			TriConsumer<? super SakerPath, ? super SakerFile, ? super V> fileconsumer) throws NullPointerException {
		Objects.requireNonNull(fileconsumer, "file consumer");
		ObjectUtils.requireNaturalOrder(paths);

		if (paths.isEmpty()) {
			return;
		}
		Iterator<? extends Entry<? extends SakerPath, V>> it = paths.entrySet().iterator();
		SakerDirectory abscurrentdir = null;
		SakerPath abscurrentpath = null;
		while (it.hasNext()) {
			Entry<? extends SakerPath, V> entry = it.next();
			SakerPath next = entry.getKey();
			if (!next.isRelative()) {
				SakerPath parent = next.getParent();
				if (parent != null) {
					abscurrentdir = SakerPathFiles.resolveDirectoryAtAbsolutePath(executiondirectorycontext, parent);
					abscurrentpath = parent;
					if (abscurrentdir == null) {
						fileconsumer.accept(next, null, entry.getValue());
					} else {
						fileconsumer.accept(next, abscurrentdir.get(next.getFileName()), entry.getValue());
					}
				} else {
					//has no parent
					fileconsumer.accept(next, SakerPathFiles.resolveAtAbsolutePath(executiondirectorycontext, next),
							entry.getValue());
				}
				break;
			}
			//XXX make a more efficient implementation instead of a simple getAtPath call for relative paths?
			fileconsumer.accept(next, SakerPathFiles.resolveAtRelativePath(workingdirectory, next), entry.getValue());
		}
		//handle absolute paths
		while (it.hasNext()) {
			Entry<? extends SakerPath, V> entry = it.next();
			SakerPath next = entry.getKey();
			cached_dir:
			if (abscurrentdir != null) {
				if (!next.getRoot().equals(abscurrentpath.getRoot())) {
					abscurrentdir = null;
					break cached_dir;
				}
				while (!next.startsWith(abscurrentpath)) {
					abscurrentpath = abscurrentpath.getParent();
					if (abscurrentpath == null) {
						abscurrentdir = null;
						break cached_dir;
					}
					abscurrentdir = abscurrentdir.getParent();
					if (abscurrentdir == null) {
						break cached_dir;
					}
				}
				//is a subpath
				int ndirnc = next.getNameCount() - 1;
				for (int i = abscurrentpath.getNameCount(); i < ndirnc; i++) {
					abscurrentdir = abscurrentdir.getDirectory(next.getName(i));
					if (abscurrentdir == null) {
						break cached_dir;
					}
				}
				//we located next cache dir
				abscurrentpath = next.getParent();
				fileconsumer.accept(next, abscurrentdir.get(next.getFileName()), entry.getValue());
				continue;
			}
			SakerPath parent = next.getParent();
			if (parent != null) {
				abscurrentdir = SakerPathFiles.resolveDirectoryAtAbsolutePath(executiondirectorycontext, parent);
				if (abscurrentdir == null) {
					fileconsumer.accept(next, null, entry.getValue());
				} else {
					abscurrentpath = parent;
					fileconsumer.accept(next, abscurrentdir.get(next.getFileName()), entry.getValue());
				}
			} else {
				//has no parent, probably just a root path
				fileconsumer.accept(next, SakerPathFiles.resolveAtAbsolutePath(executiondirectorycontext, next),
						entry.getValue());
			}
		}
	}
}
