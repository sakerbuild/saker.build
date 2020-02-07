package saker.build.trace;

import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import saker.build.file.SakerFile;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.runtime.execution.ExecutionParametersImpl.BuildInformation;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.task.TaskContext;
import saker.build.task.TaskDirectoryPathContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskExecutionResult;
import saker.build.task.TaskExecutionResult.FileDependencies;
import saker.build.task.TaskExecutionResult.TaskDependencies;
import saker.build.task.TaskFactory;
import saker.build.task.TaskInvocationConfiguration;
import saker.build.task.delta.BuildDelta;
import saker.build.task.delta.FileChangeDelta;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.UnsyncBufferedOutputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.util.exc.ExceptionView;
import testing.saker.build.flag.TestFlag;

public class InternalBuildTraceImpl implements InternalBuildTrace {
	public static final int MAGIC = 0x45a8f96a;
	public static final int FORMAT_VERSION = 1;

	public static final byte TYPE_BYTE = 1;
	public static final byte TYPE_SHORT = 2;
	public static final byte TYPE_INT = 3;
	public static final byte TYPE_LONG = 4;
	public static final byte TYPE_OBJECT = 5;
	public static final byte TYPE_ARRAY = 6;
	public static final byte TYPE_STRING = 7;
	public static final byte TYPE_NULL = 8;
	public static final byte TYPE_ARRAY_NULL_BOUNDED = 9;
	public static final byte TYPE_OBJECT_EMPTY_BOUNDED = 10;
	public static final byte TYPE_BYTE_ARRAY = 11;
	public static final byte TYPE_BOOLEAN_TRUE = 12;
	public static final byte TYPE_BOOLEAN_FALSE = 13;

	private static final InheritableThreadLocal<WeakReference<InternalBuildTraceImpl>> baseReferenceThreadLocal = new InheritableThreadLocal<>();

	private static final AtomicIntegerFieldUpdater<InternalBuildTraceImpl> AIFU_eventCounter = AtomicIntegerFieldUpdater
			.newUpdater(InternalBuildTraceImpl.class, "eventCounter");
	@SuppressWarnings("unused")
	private volatile int eventCounter;

	private static final AtomicIntegerFieldUpdater<InternalBuildTraceImpl> AIFU_traceTaskIdCounter = AtomicIntegerFieldUpdater
			.newUpdater(InternalBuildTraceImpl.class, "traceTaskIdCounter");
	@SuppressWarnings("unused")
	private volatile int traceTaskIdCounter;

	private final ProviderHolderPathKey buildTraceOutputPathKey;
	private final transient WeakReference<InternalBuildTraceImpl> baseReference;

	private EnvironmentInformation localEnvironmentInformation;
	private Map<String, String> executionUserParameters;
	private long buildTimeDateMillis;
	private long startNanos;
	private long initNanos;
	private long startExecutionNanos;
	private long endExecutionNanos;
	private long endNanos;

	private ConcurrentHashMap<TaskIdentifier, TaskBuildTraceImpl> taskBuildTraces = new ConcurrentHashMap<>();
	private SakerPath workingDirectoryPath;
	private SakerPath buildDirectoryPath;

	private NavigableMap<String, PathKey> rootsPathKey = new TreeMap<>();
	/**
	 * Maps {@link EnvironmentInformation#machineFileProviderUUID} to informations.
	 */
	private NavigableMap<UUID, EnvironmentInformation> environmentInformations = new ConcurrentSkipListMap<>();

	private ConcurrentPrependAccumulator<ExceptionView> ignoredExceptions = new ConcurrentPrependAccumulator<>();
	private BuildInformation buildInformation;

	public InternalBuildTraceImpl(ProviderHolderPathKey buildtraceoutput) {
		this.buildTraceOutputPathKey = buildtraceoutput;
		this.baseReference = new WeakReference<>(this);
	}

	@Override
	public InternalTaskBuildTrace taskBuildTrace(TaskIdentifier taskid, TaskFactory<?> taskfactory,
			TaskDirectoryPathContext taskDirectoryContext, TaskInvocationConfiguration capabilityConfig) {
		TaskBuildTraceImpl trace = getTaskTraceForTaskId(taskid);
		trace.init(taskfactory, taskDirectoryContext, capabilityConfig);
		return trace;
	}

	private TaskBuildTraceImpl getTaskTraceForTaskId(TaskIdentifier taskid) {
		return taskBuildTraces.computeIfAbsent(taskid, x -> new TaskBuildTraceImpl());
	}

	@Override
	public void taskUpToDate(TaskExecutionResult<?> prevexecresult) {
		TaskBuildTraceImpl trace = getTaskTraceForTaskId(prevexecresult.getTaskIdentifier());
		trace.upToDate(prevexecresult);
	}

	@Override
	public void ignoredException(TaskIdentifier taskid, ExceptionView e) {
		if (e == null) {
			return;
		}
		if (taskid == null) {
			ignoredExceptions.add(e);
			return;
		}
		TaskBuildTraceImpl trace = getTaskTraceForTaskId(taskid);
		trace.ignoredException(e);
	}

	@Override
	public void startBuild(SakerEnvironmentImpl environment, ExecutionContextImpl executioncontext) {
		baseReferenceThreadLocal.set(baseReference);

		this.startNanos = System.nanoTime();
		this.buildTimeDateMillis = executioncontext.getBuildTimeMillis();

		localEnvironmentInformation = new EnvironmentInformation(environment);
		environmentInformations.put(localEnvironmentInformation.machineFileProviderUUID, localEnvironmentInformation);

		BuildInformation buildinfo = executioncontext.getExecutionParameters().getBuildInfo();
		this.buildInformation = buildinfo;
	}

	@Override
	public void initializeDone(ExecutionContextImpl executioncontext) {
		this.executionUserParameters = executioncontext.getUserParameters();
		this.workingDirectoryPath = executioncontext.getWorkingDirectoryPath();
		this.buildDirectoryPath = executioncontext.getBuildDirectoryPath();
		for (Entry<String, SakerFileProvider> entry : executioncontext.getPathConfiguration().getRootFileProviders()
				.entrySet()) {
			rootsPathKey.put(entry.getKey(),
					SakerPathFiles.getPathKey(entry.getValue(), SakerPath.valueOf(entry.getKey())));
		}
	}

	@Override
	public void initialize() {
		this.initNanos = System.nanoTime();
	}

	@Override
	public void startExecute() {
		this.startExecutionNanos = System.nanoTime();
	}

	@Override
	public void endExecute() {
		this.endExecutionNanos = System.nanoTime();
	}

	@Override
	public void close() throws IOException {
		this.endNanos = System.nanoTime();
		baseReference.clear();
		SakerPath outpath = buildTraceOutputPathKey.getPath();
		SakerFileProvider fp = buildTraceOutputPathKey.getFileProvider();
		fp.createDirectories(outpath.getParent());
		try (ByteSink fileos = fp.openOutput(outpath);
				DataOutputStream os = new DataOutputStream(
						new UnsyncBufferedOutputStream(ByteSink.toOutputStream(fileos), 1024 * 32))) {
			os.writeInt(MAGIC);
			os.writeInt(FORMAT_VERSION);

			writeFieldName(os, "date");
			writeLong(os, buildTimeDateMillis);
			writeFieldName(os, "duration");
			writeLong(os, (endNanos - startNanos) / 1_000_000);

			writeFieldName(os, "phase_setup");
			writeLong(os, (initNanos - startNanos) / 1_000_000);
			writeFieldName(os, "phase_init");
			writeLong(os, (startExecutionNanos - initNanos) / 1_000_000);
			writeFieldName(os, "phase_execute");
			writeLong(os, (endExecutionNanos - startExecutionNanos) / 1_000_000);
			writeFieldName(os, "phase_finalize");
			writeLong(os, (endNanos - endExecutionNanos) / 1_000_000);

			writeFieldName(os, "exec_user_params");
			writeObject(os, executionUserParameters);

			writeFieldName(os, "working_dir");
			writeTypedObject(os, Objects.toString(workingDirectoryPath, null));
			writeFieldName(os, "build_dir");
			writeTypedObject(os, Objects.toString(buildDirectoryPath, null));

			writeFieldName(os, "execution_environment_uuid");
			writeString(os, localEnvironmentInformation.buildEnvironmentUUID.toString());

			writeFieldName(os, "environments");
			os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
			for (Entry<UUID, EnvironmentInformation> entry : environmentInformations.entrySet()) {
				EnvironmentInformation envinfo = entry.getValue();

				os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);

				writeFieldName(os, "machine_uuid");
				writeString(os, envinfo.machineFileProviderUUID.toString());

				writeFieldName(os, "env_uuid");
				writeString(os, envinfo.buildEnvironmentUUID.toString());

				writeFieldName(os, "user_params");
				writeObject(os, envinfo.environmentUserParameters);

				writeFieldName(os, "os_name");
				writeString(os, envinfo.osName);
				writeFieldName(os, "os_version");
				writeString(os, envinfo.osVersion);
				writeFieldName(os, "os_arch");
				writeString(os, envinfo.osArch);
				writeFieldName(os, "java_version");
				writeString(os, envinfo.javaVersion);
				writeFieldName(os, "java_vm_vendor");
				writeString(os, envinfo.javaVmVendor);
				writeFieldName(os, "java_vm_name");
				writeString(os, envinfo.javaVmName);
				writeFieldName(os, "processors");
				writeInt(os, envinfo.availableProcessors);

				if (!ObjectUtils.isNullOrEmpty(envinfo.computerName)) {
					writeFieldName(os, "computer_name");
					writeString(os, envinfo.computerName);
				}

				writeFieldName(os, "");
			}
			writeNull(os);

			if (this.buildInformation != null) {
				NavigableMap<String, UUID> connmachines = this.buildInformation.getConnectedMachineNames();
				if (!ObjectUtils.isNullOrEmpty(connmachines)) {
					writeFieldName(os, "connections");
					os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
					for (Entry<String, UUID> entry : connmachines.entrySet()) {
						writeFieldName(os, entry.getKey());
						writeString(os, entry.getValue().toString());
					}
					writeFieldName(os, "");
				}
			}

			writeFieldName(os, "scripts");
			os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
			for (TaskBuildTraceImpl ttrace : taskBuildTraces.values()) {
				if (ttrace.traceInfo == null) {
					continue;
				}
				for (Entry<SakerPath, ByteArrayRegion> entry : ttrace.traceInfo.readScriptContents.entrySet()) {
					writeFieldName(os, entry.getKey().toString());
					writeByteArray(os, entry.getValue());
				}
			}
			writeFieldName(os, "");

			writeFieldName(os, "path_config");
			os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
			for (Entry<String, PathKey> entry : rootsPathKey.entrySet()) {
				writeFieldName(os, entry.getKey());

				os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);

				writeFieldName(os, "file_provider");
				writeString(os, entry.getValue().getFileProviderKey().getUUID().toString());
				writeFieldName(os, "path");
				writeString(os, entry.getValue().getPath().toString());

				writeFieldName(os, "");
			}
			writeFieldName(os, "");

			writeFieldName(os, "tasks");
			os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
			for (Entry<TaskIdentifier, TaskBuildTraceImpl> entry : taskBuildTraces.entrySet()) {
				TaskBuildTraceImpl ttrace = entry.getValue();
				os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);

				writeFieldName(os, "trace_id");
				writeInt(os, ttrace.taskTraceId);

				writeFieldName(os, "start");
				writeLong(os, (ttrace.startNanos - this.startNanos) / 1_000_000);
				writeFieldName(os, "end");
				writeLong(os, (ttrace.endNanos - this.startNanos) / 1_000_000);

				writeFieldName(os, "task_class");
				writeString(os, ttrace.taskClass.getName());

				if (ttrace.traceInfo != null) {
					if (ttrace.traceInfo.standardOutDisplayIdentifier != null) {
						writeFieldName(os, "display");
						writeTypedObject(os, ttrace.traceInfo.standardOutDisplayIdentifier);
					}

					if (ttrace.traceInfo.computationTokenCount > 0) {
						writeFieldName(os, "cpu_tokens");
						writeInt(os, ttrace.traceInfo.computationTokenCount);
					}
					if (ttrace.traceInfo.cacheable) {
						writeFieldName(os, "cacheable");
						writeBoolean(os, true);
					}
					if (ttrace.traceInfo.innerTasksComputationals) {
						writeFieldName(os, "inner_task_computational");
						writeBoolean(os, true);
					}
					if (ttrace.traceInfo.remoteDispatchable) {
						writeFieldName(os, "remote_dispatchable");
						writeBoolean(os, true);
					}
					if (ttrace.traceInfo.shortTask) {
						writeFieldName(os, "short_task");
						writeBoolean(os, true);
					}
					if (!ttrace.traceInfo.standardOutBytes.isEmpty()) {
						writeFieldName(os, "stdout");
						writeByteArray(os, ttrace.traceInfo.standardOutBytes);
					}
					if (!ttrace.traceInfo.standardErrBytes.isEmpty()) {
						writeFieldName(os, "stderr");
						writeByteArray(os, ttrace.traceInfo.standardErrBytes);
					}

					if (!ObjectUtils.isNullOrEmpty(ttrace.traceInfo.frontendClassifications)) {
						writeFieldName(os, "classification_frontend");
						os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
						for (TaskIdentifier workertaskid : ttrace.traceInfo.frontendClassifications) {
							TaskBuildTraceImpl workertrace = taskBuildTraces.get(workertaskid);
							if (workertrace == null) {
								continue;
							}
							writeInt(os, workertrace.taskTraceId);
						}
						writeNull(os);
					}
				}

				if (!Objects.equals(workingDirectoryPath, ttrace.workingDirectory)) {
					writeFieldName(os, "working_dir");
					writeString(os, Objects.toString(ttrace.workingDirectory, null));
				}
				if (!Objects.equals(buildDirectoryPath, ttrace.buildDirectory)) {
					writeFieldName(os, "build_dir");
					writeString(os, Objects.toString(ttrace.buildDirectory, null));
				}

				if (!ObjectUtils.isNullOrEmpty(ttrace.deltas)) {
					writeFieldName(os, "deltas");
					os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
					for (BuildDelta d : ttrace.deltas) {
						switch (d.getType()) {
							case INPUT_FILE_ADDITION:
							case INPUT_FILE_CHANGE:
							case OUTPUT_FILE_CHANGE: {
								os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
								writeFieldName(os, "type");
								writeString(os, d.getType().name());

								writeFieldName(os, "file");
								writeString(os, ((FileChangeDelta) d).getFilePath().toString());

								writeFieldName(os, "");
								break;
							}
							default: {
								writeString(os, d.getType().name());
								break;
							}
						}
					}
					writeNull(os);
				}

				if (ttrace.taskDependencies != null) {
					TreeSet<SakerPath> indeps = new TreeSet<>();
					TreeSet<SakerPath> outdeps = new TreeSet<>();
					for (FileDependencies fdep : ttrace.taskDependencies.getTaggedFileDependencies().values()) {
						indeps.addAll(fdep.getInputFileDependencies().navigableKeySet());
						outdeps.addAll(fdep.getOutputFileDependencies().navigableKeySet());
					}
					if (!indeps.isEmpty()) {
						writeFieldName(os, "input_files");
						os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
						for (SakerPath f : indeps) {
							writeString(os, f.toString());
						}
						writeNull(os);
					}
					if (!outdeps.isEmpty()) {
						writeFieldName(os, "output_files");
						os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
						for (SakerPath f : outdeps) {
							writeString(os, f.toString());
						}
						writeNull(os);
					}
				}

				writeFieldName(os, "");
			}
			writeNull(os);
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			//don't throw this one, be non-intrusive
			e.printStackTrace();
		}
	}

	public static InternalBuildTrace current() {
		InternalBuildTrace bt = ObjectUtils.getReference(baseReferenceThreadLocal.get());
		if (bt != null) {
			return bt;
		}
		return NullInternalBuildTrace.INSTANCE;
	}

	@Override
	public String toString() {
		return "InternalBuildTraceImpl[" + buildTraceOutputPathKey + "]";
	}

	private static void writeNull(DataOutputStream os) throws IOException {
		os.writeByte(TYPE_NULL);
	}

	private static void writeByteArray(DataOutputStream os, ByteArrayRegion bytes) throws IOException {
		os.writeByte(TYPE_BYTE_ARRAY);
		os.writeInt(bytes.getLength());
		bytes.writeTo((OutputStream) os);
	}

	private static void writeArray(DataOutputStream os, Object o) throws IOException {
		if (o == null) {
			writeNull(os);
			return;
		}
		int len = Array.getLength(o);
		os.writeByte(TYPE_ARRAY);
		os.writeInt(len);
		for (int i = 0; i < len; i++) {
			writeTypedObject(os, Array.get(o, i));
		}
	}

	private static void writeArrayCollection(DataOutputStream os, Collection<?> coll) throws IOException {
		if (coll == null) {
			writeNull(os);
			return;
		}
		int len = coll.size();
		os.writeByte(TYPE_ARRAY);
		os.writeInt(len);
		for (Object elem : coll) {
			writeTypedObject(os, elem);
		}
	}

	private static void writeArrayNullBounded(DataOutputStream os, Iterable<?> coll) throws IOException {
		if (coll == null) {
			writeNull(os);
			return;
		}
		os.writeByte(TYPE_ARRAY);
		for (Object elem : coll) {
			writeTypedObject(os, elem);
		}
		writeNull(os);
	}

	private static void writeObject(DataOutputStream os, Map<String, ?> o) throws IOException {
		if (o == null) {
			writeNull(os);
			return;
		}
		os.writeByte(TYPE_OBJECT);
		os.writeInt(o.size());
		for (Entry<String, ?> entry : o.entrySet()) {
			writeFieldName(os, entry.getKey());
			writeTypedObject(os, entry.getValue());
		}
	}

	private static void writeObjectNullBounded(DataOutputStream os, Map<String, ?> o) throws IOException {
		if (o == null) {
			writeNull(os);
			return;
		}
		os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
		for (Entry<String, ?> entry : o.entrySet()) {
			writeFieldName(os, entry.getKey());
			writeTypedObject(os, entry.getValue());
		}
		writeNull(os);
	}

	@SuppressWarnings("unchecked")
	private static void writeTypedObject(DataOutputStream os, Object val) throws IOException {
		if (val == null) {
			writeNull(os);
		} else if (val instanceof CharSequence) {
			writeString(os, (CharSequence) val);
		} else if (val instanceof Byte) {
			writeByte(os, (byte) val);
		} else if (val instanceof Short) {
			writeShort(os, (short) val);
		} else if (val instanceof Integer) {
			writeInt(os, (int) val);
		} else if (val instanceof Long) {
			writeLong(os, (long) val);
		} else if (val.getClass().isArray()) {
			writeArray(os, val);
		} else if (val instanceof Collection<?>) {
			writeArrayCollection(os, (Collection<?>) val);
		} else if (val instanceof Map<?, ?>) {
			writeObject(os, (Map<String, ?>) val);
		} else {
			//unrecognized type
			os.writeByte(TYPE_OBJECT);
			os.writeInt(0);
		}
	}

	private static void writeString(DataOutputStream os, CharSequence s) throws IOException {
		if (s == null) {
			writeNull(os);
			return;
		}
		os.writeByte(TYPE_STRING);
		writeStringImpl(os, s);
	}

	private static void writeLong(DataOutputStream os, long v) throws IOException {
		os.writeByte(TYPE_LONG);
		os.writeLong(v);
	}

	private static void writeInt(DataOutputStream os, int v) throws IOException {
		os.writeByte(TYPE_INT);
		os.writeInt(v);
	}

	private static void writeShort(DataOutputStream os, short v) throws IOException {
		os.writeByte(TYPE_SHORT);
		os.writeShort(v);
	}

	private static void writeByte(DataOutputStream os, byte v) throws IOException {
		os.writeByte(TYPE_BYTE);
		os.writeByte(v);
	}

	private static void writeBoolean(DataOutputStream os, boolean bool) throws IOException {
		os.writeByte(bool ? TYPE_BOOLEAN_TRUE : TYPE_BOOLEAN_FALSE);
	}

	private static void writeFieldName(DataOutputStream os, CharSequence s) throws IOException {
		writeStringImpl(os, s);
	}

	private static void writeStringImpl(DataOutputStream os, CharSequence s) throws IOException {
		int len = s.length();
		os.writeInt(len);
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			os.writeChar(c);
		}
	}

	private final class TaskBuildTraceImpl implements InternalTaskBuildTrace {
		protected final int eventId = AIFU_eventCounter.incrementAndGet(InternalBuildTraceImpl.this);
		protected final int taskTraceId = AIFU_traceTaskIdCounter.incrementAndGet(InternalBuildTraceImpl.this);
		protected long startNanos;
		protected long endNanos;
		protected Class<? extends TaskFactory<?>> taskClass;

		protected TaskBuildTraceInfo traceInfo;

		protected Set<BuildDelta> deltas = new LinkedHashSet<>();
		protected SakerPath workingDirectory;
		protected SakerPath buildDirectory;

		protected TaskDependencies taskDependencies;

		public TaskBuildTraceImpl() {
		}

		@SuppressWarnings("unchecked")
		public void init(TaskFactory<?> taskfactory, TaskDirectoryPathContext taskDirectoryContext,
				TaskInvocationConfiguration capabilityConfig) {
			this.taskClass = (Class<? extends TaskFactory<?>>) taskfactory.getClass();

			this.traceInfo = new TaskBuildTraceInfo();
			this.traceInfo.setCapabilityConfig(capabilityConfig);

			this.workingDirectory = taskDirectoryContext.getTaskWorkingDirectoryPath();
			this.buildDirectory = taskDirectoryContext.getTaskBuildDirectoryPath();
		}

		public void ignoredException(ExceptionView e) {
			// TODO Auto-generated method stub
		}

		@Override
		public void startTaskExecution() {
			this.startNanos = System.nanoTime();
		}

		@Override
		public void endTaskExecution() {
			this.endNanos = System.nanoTime();
		}

		@Override
		public void classifyFrontendTask(TaskIdentifier workertaskid) {
			traceInfo.frontendClassifications.add(workertaskid);
		}

		@SuppressWarnings("unchecked")
		public void upToDate(TaskExecutionResult<?> taskresult) {
			this.startNanos = System.nanoTime();
			this.endNanos = this.startNanos;
			this.deltas = Collections.emptySet();
			this.collectDependencies(taskresult);
			this.traceInfo = (TaskBuildTraceInfo) taskresult.getBuildTraceInfo();
			this.taskClass = (Class<? extends TaskFactory<?>>) taskresult.getFactory().getClass();
		}

		@Override
		public void setStandardOutDisplayIdentifier(String displayid) {
			traceInfo.standardOutDisplayIdentifier = displayid;
		}

		@Override
		public void deltas(Set<? extends BuildDelta> deltas) {
			this.deltas.addAll(deltas);
		}

		@Override
		public ByteSource openTargetConfigurationReadingInput(ScriptParsingOptions parsingoptions, SakerFile file)
				throws IOException {
			ByteArrayRegion filebytes = file.getBytes();
			traceInfo.readScriptContents.put(parsingoptions.getScriptPath(), filebytes);
			return new UnsyncByteArrayInputStream(filebytes);
		}

		@Override
		public void closeStandardIO(UnsyncByteArrayOutputStream stdout, UnsyncByteArrayOutputStream stderr) {
			traceInfo.standardOutBytes = stdout.toByteArrayRegion();
			traceInfo.standardErrBytes = stderr.toByteArrayRegion();
		}

		@Override
		public void close(TaskContext taskcontext, TaskExecutionResult<?> taskresult) {
			this.endNanos = System.nanoTime();
			taskresult.setBuildTraceInfo(traceInfo);
			collectDependencies(taskresult);
		}

		protected void collectDependencies(TaskExecutionResult<?> taskresult) {
			this.taskDependencies = taskresult.getDependencies();
		}
	}

	private static final class TaskBuildTraceInfo implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected String standardOutDisplayIdentifier;
		protected NavigableMap<SakerPath, ByteArrayRegion> readScriptContents = new ConcurrentSkipListMap<>();

		protected ByteArrayRegion standardOutBytes = ByteArrayRegion.EMPTY;
		protected ByteArrayRegion standardErrBytes = ByteArrayRegion.EMPTY;

		protected boolean shortTask;
		protected boolean remoteDispatchable;
		protected boolean cacheable;
		protected boolean innerTasksComputationals;
		protected int computationTokenCount;
		protected TaskExecutionEnvironmentSelector environmentSelector;

		protected Set<TaskIdentifier> frontendClassifications = ConcurrentHashMap.newKeySet();

		/**
		 * For {@link Externalizable}.
		 */
		public TaskBuildTraceInfo() {
		}

		public void setCapabilityConfig(TaskInvocationConfiguration capabilityConfig) {
			shortTask = capabilityConfig.isShortTask();
			remoteDispatchable = capabilityConfig.isRemoteDispatchable();
			cacheable = capabilityConfig.isCacheable();
			innerTasksComputationals = capabilityConfig.isInnerTasksComputationals();
			computationTokenCount = capabilityConfig.getComputationTokenCount();
			environmentSelector = capabilityConfig.getEnvironmentSelector();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			try {
				out.writeObject(standardOutDisplayIdentifier);
				SerialUtils.writeExternalMap(out, readScriptContents);

				out.writeObject(standardOutBytes);
				out.writeObject(standardErrBytes);

				out.writeBoolean(shortTask);
				out.writeBoolean(remoteDispatchable);
				out.writeBoolean(cacheable);
				out.writeBoolean(innerTasksComputationals);
				out.writeInt(computationTokenCount);
				out.writeObject(environmentSelector);
				SerialUtils.writeExternalCollection(out, frontendClassifications);
			} catch (Exception e) {
				//ignore
				if (TestFlag.ENABLED) {
					e.printStackTrace();
				}
			}
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			try {
				standardOutDisplayIdentifier = (String) in.readObject();
				readScriptContents = SerialUtils.readExternalSortedImmutableNavigableMap(in);

				standardOutBytes = (ByteArrayRegion) in.readObject();
				standardErrBytes = (ByteArrayRegion) in.readObject();

				shortTask = in.readBoolean();
				remoteDispatchable = in.readBoolean();
				cacheable = in.readBoolean();
				innerTasksComputationals = in.readBoolean();
				computationTokenCount = in.readInt();
				environmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();
				frontendClassifications = SerialUtils.readExternalImmutableHashSet(in);
			} catch (Exception e) {
				//ignore
				if (TestFlag.ENABLED) {
					e.printStackTrace();
				}
			}
		}
	}

	private static class EnvironmentInformation implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected UUID machineFileProviderUUID;
		protected UUID buildEnvironmentUUID;
		protected Map<String, String> environmentUserParameters;

		protected String osName;
		protected String osVersion;
		protected String osArch;
		protected String javaVersion;
		protected String javaVmVendor;
		protected String javaVmName;

		protected int availableProcessors;

		protected String computerName;

		public EnvironmentInformation(SakerEnvironmentImpl environment) {
			this.environmentUserParameters = environment.getUserParameters();
			this.machineFileProviderUUID = LocalFileProvider.getProviderKeyStatic().getUUID();
			this.buildEnvironmentUUID = environment.getEnvironmentIdentifier();
			this.availableProcessors = Runtime.getRuntime().availableProcessors();
			this.osName = System.getProperty("os.name");
			this.osVersion = System.getProperty("os.version");
			this.osArch = System.getProperty("os.arch");
			this.javaVersion = System.getProperty("java.version");
			this.javaVmVendor = System.getProperty("java.vm.vendor");
			this.javaVmName = System.getProperty("java.vm.name");
			//from it is present in windows
			this.computerName = System.getenv("COMPUTERNAME");
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(machineFileProviderUUID);
			out.writeObject(buildEnvironmentUUID);
			SerialUtils.writeExternalMap(out, environmentUserParameters);
			out.writeObject(osName);
			out.writeObject(osVersion);
			out.writeObject(osArch);
			out.writeObject(javaVersion);
			out.writeObject(javaVmVendor);
			out.writeObject(javaVmName);
			out.writeInt(availableProcessors);
			out.writeObject(computerName);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			machineFileProviderUUID = (UUID) in.readObject();
			buildEnvironmentUUID = (UUID) in.readObject();
			environmentUserParameters = SerialUtils.readExternalImmutableNavigableMap(in);
			osName = (String) in.readObject();
			osVersion = (String) in.readObject();
			osArch = (String) in.readObject();
			javaVersion = (String) in.readObject();
			javaVmVendor = (String) in.readObject();
			javaVmName = (String) in.readObject();
			availableProcessors = in.readInt();
			computerName = (String) in.readObject();
		}
	}
}
