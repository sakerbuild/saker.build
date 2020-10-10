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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.NullContentDescriptor;
import saker.build.file.path.PathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SimplePathKey;
import saker.build.ide.configuration.IDEConfiguration;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.task.dependencies.CommonTaskOutputChangeDetector;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.task.event.TaskExecutionEvent;
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.task.exception.TaskIdentifierConflictException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.trace.InternalBuildTraceImpl;
import testing.saker.build.flag.TestFlag;

public class TaskExecutionResult<R> implements TaskResultHolder<R>, Externalizable {
	private static final long serialVersionUID = 1L;

	public static final class FileDependencies implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected NavigableMap<SakerPath, ContentDescriptor> inputFileDependencies;
		protected NavigableMap<SakerPath, ContentDescriptor> outputFileDependencies;

		protected Set<FileCollectionStrategy> additionDependencies;

		public FileDependencies() {
		}

		public NavigableMap<SakerPath, ContentDescriptor> getInputFileDependencies() {
			return inputFileDependencies;
		}

		public NavigableMap<SakerPath, ContentDescriptor> getOutputFileDependencies() {
			return outputFileDependencies;
		}

		public void setInputFileDependencies(NavigableMap<SakerPath, ContentDescriptor> inputFileDependencies) {
			this.inputFileDependencies = inputFileDependencies;
		}

		public void setOutputFileDependencies(NavigableMap<SakerPath, ContentDescriptor> outputFileDependencies) {
			this.outputFileDependencies = outputFileDependencies;
		}

		public Set<FileCollectionStrategy> getAdditionDependencies() {
			return additionDependencies;
		}

		public void setAdditionDependencies(Set<FileCollectionStrategy> additionDependencies) {
			this.additionDependencies = additionDependencies;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			writeContentDescriptorMap(out, inputFileDependencies);
			writeContentDescriptorMap(out, outputFileDependencies);
			SerialUtils.writeExternalCollection(out, additionDependencies);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			inputFileDependencies = readContentDescriptorMap(in);
			outputFileDependencies = readContentDescriptorMap(in);
			additionDependencies = SerialUtils.readExternalImmutableHashSet(in);
		}

	}

	public static final class CreatedTaskDependency implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected TaskFactory<?> factory;
		protected TaskExecutionParameters taskParameters;

		/**
		 * For {@link Externalizable}.
		 */
		public CreatedTaskDependency() {
		}

		public CreatedTaskDependency(TaskFactory<?> factory, TaskExecutionParameters taskparameters) {
			this.factory = factory;
			this.taskParameters = taskparameters;
		}

		public TaskFactory<?> getFactory() {
			return factory;
		}

		public TaskExecutionParameters getTaskParameters() {
			return taskParameters;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(factory);
			out.writeObject(taskParameters);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			factory = (TaskFactory<?>) in.readObject();
			taskParameters = (TaskExecutionParameters) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((factory == null) ? 0 : factory.hashCode());
			result = prime * result + ((taskParameters == null) ? 0 : taskParameters.hashCode());
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
			CreatedTaskDependency other = (CreatedTaskDependency) obj;
			if (factory == null) {
				if (other.factory != null)
					return false;
			} else if (!factory.equals(other.factory))
				return false;
			if (taskParameters == null) {
				if (other.taskParameters != null)
					return false;
			} else if (!taskParameters.equals(other.taskParameters))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "CreatedTaskDependency [" + (factory != null ? "factory=" + factory + ", " : "")
					+ (taskParameters != null ? "taskParameters=" + taskParameters : "") + "]";
		}
	}

	private static final class DependencyMultiTaskOutputChangeDetector
			implements TaskOutputChangeDetector, Externalizable {
		private static final long serialVersionUID = 1L;

		private Set<TaskOutputChangeDetector> detectors;

		/**
		 * For {@link Externalizable}.
		 */
		public DependencyMultiTaskOutputChangeDetector() {
		}

		public DependencyMultiTaskOutputChangeDetector(TaskOutputChangeDetector item) {
			detectors = ConcurrentHashMap.newKeySet();
			detectors.add(item);
		}

		public boolean add(TaskOutputChangeDetector detector) {
			return detectors.add(detector);
		}

		@Override
		public boolean isChanged(Object taskoutput) {
			for (TaskOutputChangeDetector detector : detectors) {
				if (detector.isChanged(taskoutput)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			SerialUtils.writeExternalCollection(out, detectors);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			detectors = SerialUtils.readExternalImmutableHashSet(in);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + detectors + "]";
		}

	}

	public static class ReportedTaskDependency implements Externalizable {
		private static final long serialVersionUID = 1L;

		private static final AtomicReferenceFieldUpdater<TaskExecutionResult.ReportedTaskDependency, TaskOutputChangeDetector> ARFU_outputChangeDetector = AtomicReferenceFieldUpdater
				.newUpdater(TaskExecutionResult.ReportedTaskDependency.class, TaskOutputChangeDetector.class,
						"outputChangeDetector");

		protected TaskDependencies dependencies;
		protected volatile TaskOutputChangeDetector outputChangeDetector;
		protected boolean finishedRetrieval;

		/**
		 * For {@link Externalizable}.
		 */
		public ReportedTaskDependency() {
		}

		public ReportedTaskDependency(TaskDependencies dependencies, TaskOutputChangeDetector outputChangeDetector,
				boolean finishedRetrieval) {
			this.dependencies = dependencies;
			this.outputChangeDetector = outputChangeDetector;
			this.finishedRetrieval = finishedRetrieval;
		}

		public TaskDependencies getDependencies() {
			return dependencies;
		}

		public TaskOutputChangeDetector getOutputChangeDetector() {
			return outputChangeDetector;
		}

		public boolean isFinishedRetrieval() {
			return finishedRetrieval;
		}

		public void addOutputChangeDetector(TaskOutputChangeDetector outputChangeDetector) {
			if (outputChangeDetector == CommonTaskOutputChangeDetector.ALWAYS) {
				//can replace any previous task change detector, as this one will always return true
				this.outputChangeDetector = CommonTaskOutputChangeDetector.ALWAYS;
				return;
			}
			while (true) {
				TaskOutputChangeDetector val = this.outputChangeDetector;
				if (val == null) {
					if (ARFU_outputChangeDetector.compareAndSet(this, null, outputChangeDetector)) {
						return;
					}
				} else {
					if (val == CommonTaskOutputChangeDetector.ALWAYS) {
						//current change detector always returns true, adding another won't change the result
						return;
					}
					if (val.getClass() == DependencyMultiTaskOutputChangeDetector.class) {
						((DependencyMultiTaskOutputChangeDetector) val).add(outputChangeDetector);
						return;
					}
					if (val.equals(outputChangeDetector)) {
						//the detectors are the same
						return;
					}
					DependencyMultiTaskOutputChangeDetector nval = new DependencyMultiTaskOutputChangeDetector(val);
					nval.add(outputChangeDetector);
					if (ARFU_outputChangeDetector.compareAndSet(this, val, nval)) {
						return;
					}
				}
			}
		}

		public void overwriteOutputChangeDetector(TaskOutputChangeDetector outputchangedetector) {
			this.outputChangeDetector = outputchangedetector;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(dependencies);
			out.writeObject(outputChangeDetector);
			out.writeBoolean(finishedRetrieval);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			dependencies = (TaskDependencies) in.readObject();
			try {
				outputChangeDetector = (TaskOutputChangeDetector) in.readObject();
			} catch (IOException | ClassNotFoundException e) {
				if (TestFlag.ENABLED) {
					System.err.println(getClass().getSimpleName() + " readExternal: " + e);
				}
				InternalBuildTraceImpl.serializationException(e);
				//if we fail to load the output change detector, then always assume it as changed
				outputChangeDetector = CommonTaskOutputChangeDetector.ALWAYS;
			}
			finishedRetrieval = in.readBoolean();
		}
	}

	public static final class TaskDependencies implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected static enum NullFileDependencyTag {
			INSTANCE;

			public static <T> T nullize(T obj) {
				if (obj == INSTANCE) {
					return null;
				}
				return obj;
			}

			public static Object denullize(Object obj) {
				if (obj == null) {
					return INSTANCE;
				}
				return obj;
			}
		}

		protected Map<TaskIdentifier, CreatedTaskDependency> createdTaskIds;
		protected Map<Object, FileDependencies> taggedFileDependencies;
		protected Map<TaskIdentifier, ReportedTaskDependency> dependentTasks;
		protected Map<? extends EnvironmentProperty<?>, ?> environmentPropertyDependencies;
		protected Map<? extends EnvironmentProperty<?>, ?> environmentPropertyQualifierDependencies;

		protected Map<? extends ExecutionProperty<?>, ?> executionPropertyDependencies;

		protected Object buildModificationStamp;
		protected TaskOutputChangeDetector selfOutputChangeDetector;
		protected Iterable<? extends TaskExecutionEvent> events;

		/**
		 * For {@link Externalizable}.
		 */
		public TaskDependencies() {
		}

		public TaskDependencies(Iterable<? extends TaskExecutionEvent> events) {
			this.events = events;
			this.taggedFileDependencies = new ConcurrentHashMap<>();
			this.createdTaskIds = new ConcurrentHashMap<>();
			this.dependentTasks = new ConcurrentHashMap<>();
		}

		public void setSelfOutputChangeDetector(TaskOutputChangeDetector selfOutputChangeDetector) {
			this.selfOutputChangeDetector = selfOutputChangeDetector;
		}

		public TaskOutputChangeDetector getSelfOutputChangeDetector() {
			return selfOutputChangeDetector;
		}

		public Map<Object, FileDependencies> getTaggedFileDependencies() {
			return taggedFileDependencies;
		}

		public void setTaggedFileDependencies(Map<Object, FileDependencies> taggedFileDependencies) {
			this.taggedFileDependencies = taggedFileDependencies;
		}

		public Map<? extends EnvironmentProperty<?>, ?> getEnvironmentPropertyDependenciesWithQualifiers() {
			if (ObjectUtils.isNullOrEmpty(this.environmentPropertyQualifierDependencies)) {
				return this.environmentPropertyDependencies;
			}
			if (ObjectUtils.isNullOrEmpty(this.environmentPropertyDependencies)) {
				return this.environmentPropertyQualifierDependencies;
			}
			HashMap<EnvironmentProperty<?>, Object> result = new HashMap<>(this.environmentPropertyDependencies);
			result.putAll(this.environmentPropertyQualifierDependencies);
			return result;
		}

		public Map<? extends EnvironmentProperty<?>, ?> getEnvironmentPropertyDependencies() {
			return environmentPropertyDependencies;
		}

		public Map<? extends ExecutionProperty<?>, ?> getExecutionPropertyDependencies() {
			return executionPropertyDependencies;
		}

		public Map<TaskIdentifier, ReportedTaskDependency> getTaskDependencies() {
			return dependentTasks;
		}

		public Map<TaskIdentifier, CreatedTaskDependency> getDirectlyCreatedTaskIds() {
			return createdTaskIds;
		}

		public void setDirectlyCreatedTaskIds(Map<TaskIdentifier, CreatedTaskDependency> createdTaskIds) {
			this.createdTaskIds = createdTaskIds;
		}

		public void setEnvironmentPropertyQualifiersDependencies(
				Map<? extends EnvironmentProperty<?>, ?> environmentPropertyQualifiersDependencies) {
			this.environmentPropertyQualifierDependencies = environmentPropertyQualifiersDependencies;
		}

		public Map<? extends EnvironmentProperty<?>, ?> getEnvironmentPropertyQualifierDependencies() {
			return environmentPropertyQualifierDependencies;
		}

		public Object getBuildModificationStamp() {
			return buildModificationStamp;
		}

		public void setEnvironmentPropertyDependencies(
				Map<? extends EnvironmentProperty<?>, ?> environmentPropertyDependencies) {
			this.environmentPropertyDependencies = environmentPropertyDependencies;
		}

		public void setExecutionPropertyDependencies(
				Map<? extends ExecutionProperty<?>, ?> executionPropertyDependencies) {
			this.executionPropertyDependencies = executionPropertyDependencies;
		}

		public void addCreatedTask(TaskIdentifier taskid, TaskFactory<?> factory,
				TaskExecutionParameters taskparameters) {
			CreatedTaskDependency added = new CreatedTaskDependency(factory, taskparameters);
			CreatedTaskDependency prev = createdTaskIds.put(taskid, added);
			if (prev != null) {
				if (!Objects.equals(prev, added)) {
					throw new TaskIdentifierConflictException(
							"Different created tasks for task id: " + added + " and " + prev, taskid);
				}
			}
		}

		public boolean isDirectlyCreated(TaskIdentifier taskid) {
			return createdTaskIds.containsKey(taskid);
		}

		public boolean addTaskDependency(TaskIdentifier taskid, TaskDependencies taskdeps, boolean finishedretrieval) {
			TaskOutputChangeDetector outputchangedetector = null;
			return addTaskDependency(taskid, taskdeps, finishedretrieval, outputchangedetector);
		}

		public boolean addTaskDependency(TaskIdentifier taskid, TaskDependencies taskdeps, boolean finishedretrieval,
				TaskOutputChangeDetector outputchangedetector) {
			ReportedTaskDependency prev = dependentTasks.putIfAbsent(taskid,
					new ReportedTaskDependency(taskdeps, outputchangedetector, finishedretrieval));
			if (prev != null) {
				if (prev.dependencies != taskdeps) {
					throw new TaskIdentifierConflictException(
							"Task dependency for task id is already present with different values.", taskid);
				}
				return false;
			}
			return true;
		}

		public void addTaskOutputChangeDetector(TaskIdentifier taskid, TaskOutputChangeDetector outputchangedetector) {
			ReportedTaskDependency deps = dependentTasks.get(taskid);
			if (deps == null) {
				throw new IllegalTaskOperationException("Task dependency is not yet reported.", taskid);
			}
			deps.addOutputChangeDetector(outputchangedetector);
		}

		public Iterable<? extends TaskExecutionEvent> getEvents() {
			return events;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(buildModificationStamp);
			out.writeObject(selfOutputChangeDetector);

			//TODO read and write the tagged dependencies in a safe way
			SerialUtils.writeExternalMap(out, taggedFileDependencies);
			SerialUtils.writeExternalMap(out, environmentPropertyDependencies);
			SerialUtils.writeExternalMap(out, environmentPropertyQualifierDependencies);
			SerialUtils.writeExternalMap(out, executionPropertyDependencies);

			SerialUtils.writeExternalMap(out, createdTaskIds);
			SerialUtils.writeExternalMap(out, dependentTasks);

			SerialUtils.writeExternalIterable(out, events);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			buildModificationStamp = in.readObject();
			try {
				selfOutputChangeDetector = (TaskOutputChangeDetector) in.readObject();
			} catch (IOException | ClassNotFoundException e) {
				if (TestFlag.ENABLED) {
					System.err.println(getClass().getSimpleName() + " readExternal: " + e);
				}
				InternalBuildTraceImpl.serializationException(e);
				selfOutputChangeDetector = CommonTaskOutputChangeDetector.ALWAYS;
			}

			taggedFileDependencies = SerialUtils.readExternalImmutableHashMap(in);
			environmentPropertyDependencies = SerialUtils.readExternalImmutableHashMap(in);
			environmentPropertyQualifierDependencies = SerialUtils.readExternalImmutableHashMap(in);
			executionPropertyDependencies = SerialUtils.readExternalImmutableHashMap(in);

			createdTaskIds = SerialUtils.readExternalImmutableHashMap(in);
			dependentTasks = SerialUtils.readExternalImmutableHashMap(in);

			events = SerialUtils.readExternalIterable(in);
		}
	}

	protected TaskIdentifier taskId;
	protected TaskFactory<R> factory;
	protected R output;
	protected List<Throwable> abortExceptions;
	protected Throwable failCauseException;
	protected Map<Object, Object> taggedOutputs;
	protected NavigableMap<String, Object> reportedMetaDatas;
	protected transient boolean outputLoadFailed = false;
	protected boolean rootTask;

	protected Set<TaskIdentifier> createdByTaskIds;

	protected TaskDependencies dependencies;
	protected TaskExecutionParameters parameters;

	protected Collection<IDEConfiguration> ideConfigurations;

	protected SakerPath executionBuildDirectory;
	protected SakerPath executionWorkingDirectory;
	protected SimplePathKey executionBuildDirectoryPathKey;

	protected List<String> printedLines;

	protected Object buildTraceInfo;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskExecutionResult() {
	}

	public TaskExecutionResult(TaskIdentifier taskId, TaskFactory<R> factory, TaskExecutionParameters parameters,
			SakerPath executionWorkingDirectory, SakerPath executionBuildDirectory,
			Iterable<? extends TaskExecutionEvent> events, PathKey executionBuildDirectoryPathKey) {
		this.taskId = taskId;
		this.factory = factory;
		this.parameters = parameters;
		this.ideConfigurations = new ArrayList<>();

		this.createdByTaskIds = ConcurrentHashMap.newKeySet();
		this.taggedOutputs = new ConcurrentHashMap<>();
		this.reportedMetaDatas = new ConcurrentSkipListMap<>();

		this.dependencies = new TaskDependencies(events);

		this.executionBuildDirectory = executionBuildDirectory;
		this.executionWorkingDirectory = executionWorkingDirectory;
		this.executionBuildDirectoryPathKey = executionBuildDirectoryPathKey == null ? null
				: new SimplePathKey(executionBuildDirectoryPathKey);
	}

	public SakerPath getExecutionBuildDirectory() {
		return executionBuildDirectory;
	}

	public SakerPath getExecutionWorkingDirectory() {
		return executionWorkingDirectory;
	}

	public SimplePathKey getExecutionBuildDirectoryPathKey() {
		return executionBuildDirectoryPathKey;
	}

	public boolean isRootTask() {
		return rootTask;
	}

	public void setRootTask(boolean rootTask) {
		this.rootTask = rootTask;
	}

	public TaskExecutionParameters getParameters() {
		return parameters;
	}

	@Override
	public TaskDependencies getDependencies() {
		return dependencies;
	}

//	public void addCreatedByTask(TaskIdentifier taskid) throws AssertionError {
//		if (TestFlag.ENABLED) {
//			if (taskid.equals(taskId)) {
//				throw new AssertionError(taskid);
//			}
//		}
//		createdByTaskIds.add(taskid);
//	}

	public void setCreatedByTaskIds(Set<TaskIdentifier> createdByTaskIds) {
		if (TestFlag.ENABLED) {
			if (createdByTaskIds.contains(taskId)) {
				throw new AssertionError(taskId);
			}
		}
		this.createdByTaskIds = createdByTaskIds;
	}

	public Set<TaskIdentifier> getCreatedByTaskIds() {
		return createdByTaskIds;
	}

	@Override
	public TaskIdentifier getTaskIdentifier() {
		return taskId;
	}

	public TaskFactory<R> getFactory() {
		return factory;
	}

	@Override
	public R getOutput() {
		return output;
	}

	public boolean isSuccessfulExecution() {
		return ObjectUtils.isNullOrEmpty(abortExceptions) && failCauseException == null;
	}

	@Override
	public List<Throwable> getAbortExceptions() {
		return abortExceptions;
	}

	@Override
	public Throwable getFailCauseException() {
		return failCauseException;
	}

	public boolean isOutputLoadFailed() {
		return outputLoadFailed;
	}

	public boolean shouldConsiderOutputLoadFailed() {
		return outputLoadFailed
//				|| failCauseException != null
		;
	}

	public void setOutput(R result, Object buildchangemodificationstamp) {
		this.output = result;
		this.dependencies.buildModificationStamp = buildchangemodificationstamp;
	}

	public void setFailedOutput(Throwable cause, List<Throwable> abortExceptions, Object buildchangemodificationstamp) {
		this.failCauseException = cause;
		this.abortExceptions = abortExceptions;
		this.dependencies.buildModificationStamp = buildchangemodificationstamp;
	}

	public void setPrintedLines(List<String> printedLines) {
		this.printedLines = printedLines;
	}

	public List<String> getPrintedLines() {
		return printedLines;
	}

	public void addIDEConfiguration(IDEConfiguration config) {
		synchronized (ideConfigurations) {
			this.ideConfigurations.add(config);
		}
	}

	public void addIDEConfigurations(Collection<? extends IDEConfiguration> configs) {
		synchronized (ideConfigurations) {
			this.ideConfigurations.addAll(configs);
		}
	}

	public Collection<IDEConfiguration> getIDEConfigurations() {
		synchronized (ideConfigurations) {
			return new ArrayList<>(ideConfigurations);
		}
	}

	public void addTaggedOutput(Object tag, Object value) {
		taggedOutputs.put(tag, value);
	}

	public void addTaggedOutputs(Map<?, ?> outputs) {
		taggedOutputs.putAll(outputs);
	}

	public Object getTaggedOutput(Object tag) {
		return taggedOutputs.get(tag);
	}

	public Map<Object, Object> getTaggedOutputs() {
		return taggedOutputs;
	}

	//doc: returns the previously reported metadata with the same id
	public Object setMetaData(String id, Object value) {
		return reportedMetaDatas.put(id, value);
	}

	public void addMetaDatas(Map<String, Object> datas) {
		reportedMetaDatas.putAll(datas);
	}

	public Object getMetaData(String id) {
		return reportedMetaDatas.get(id);
	}

	public NavigableMap<String, Object> getMetaDatas() {
		return reportedMetaDatas;
	}

	public Object getBuildTraceInfo() {
		return buildTraceInfo;
	}

	public void setBuildTraceInfo(Object buildTraceInfo) {
		this.buildTraceInfo = buildTraceInfo;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskId);
		out.writeBoolean(rootTask);
		out.writeObject(factory);
		out.writeObject(executionBuildDirectory);
		out.writeObject(executionWorkingDirectory);
		out.writeObject(executionBuildDirectoryPathKey);
		try {
			out.writeObject(output);
		} catch (Exception e) {
			if (TestFlag.ENABLED) {
				System.err.println(getClass().getSimpleName() + " writeExternal output: " + e);
			}
			InternalBuildTraceImpl.serializationException(e);
		}
		try {
			SerialUtils.writeExternalCollection(out, abortExceptions);
		} catch (Exception e) {
			if (TestFlag.ENABLED) {
				System.err.println(getClass().getSimpleName() + " writeExternal abortExceptions: " + e);
			}
			InternalBuildTraceImpl.serializationException(e);
		}
		try {
			out.writeObject(failCauseException);
		} catch (Exception e) {
			if (TestFlag.ENABLED) {
				System.err.println(getClass().getSimpleName() + " writeExternal failCauseException: " + e);
			}
			InternalBuildTraceImpl.serializationException(e);
		}
		writeTaggedOutputsMap(out, taggedOutputs);
		SerialUtils.writeExternalMap(out, reportedMetaDatas);
		out.writeObject(dependencies);
		out.writeObject(parameters);

		SerialUtils.writeExternalCollection(out, createdByTaskIds);
		SerialUtils.writeExternalCollection(out, ideConfigurations);

		SerialUtils.writeExternalCollection(out, printedLines);
		try {
			out.writeObject(buildTraceInfo);
		} catch (Exception e) {
			if (TestFlag.ENABLED) {
				System.err.println(getClass().getSimpleName() + " writeExternal buildTraceInfo: " + e);
			}
			InternalBuildTraceImpl.serializationException(e);
		}
	}

	//XXX replace print stack traces with something that the user can better examine

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskId = (TaskIdentifier) in.readObject();
		rootTask = in.readBoolean();
		factory = (TaskFactory<R>) in.readObject();
		executionBuildDirectory = (SakerPath) in.readObject();
		executionWorkingDirectory = (SakerPath) in.readObject();
		executionBuildDirectoryPathKey = (SimplePathKey) in.readObject();
		try {
			output = (R) in.readObject();
		} catch (IOException | ClassNotFoundException e) {
			if (TestFlag.ENABLED) {
				System.err.println(getClass().getSimpleName() + " readExternal output: " + e);
			}
			InternalBuildTraceImpl.serializationException(e);
			outputLoadFailed = true;
		}
		try {
			abortExceptions = SerialUtils.readExternalImmutableList(in);
		} catch (IOException | ClassNotFoundException e) {
			if (TestFlag.ENABLED) {
				System.err.println(getClass().getSimpleName() + " readExternal abortExceptions: " + e);
			}
			InternalBuildTraceImpl.serializationException(e);
			outputLoadFailed = true;
		}
		try {
			failCauseException = (Throwable) in.readObject();
		} catch (IOException | ClassNotFoundException e) {
			if (TestFlag.ENABLED) {
				System.err.println(getClass().getSimpleName() + " readExternal failCauseException: " + e);
			}
			InternalBuildTraceImpl.serializationException(e);
			outputLoadFailed = true;
		}
		taggedOutputs = readTaggedOutputsMap(in);
		reportedMetaDatas = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		dependencies = (TaskDependencies) in.readObject();
		parameters = (TaskExecutionParameters) in.readObject();

		//TODO the created by task ids collection should be read as immutable
		createdByTaskIds = SerialUtils.readExternalCollection(ConcurrentHashMap.newKeySet(), in);
		ideConfigurations = SerialUtils.readExternalImmutableList(in);
		printedLines = SerialUtils.readExternalImmutableList(in);

		try {
			this.buildTraceInfo = in.readObject();
		} catch (Exception e) {
			if (TestFlag.ENABLED) {
				System.err.println(getClass().getSimpleName() + " readExternal buildTraceInfo: " + e);
			}
			InternalBuildTraceImpl.serializationException(e);
		}
	}

	private static void writeTaggedOutputsMap(ObjectOutput out, Map<Object, Object> taggedoutputs) throws IOException {
		SerialUtils.writeExternalCollection(out, taggedoutputs.entrySet(), (o, entry) -> {
			try {
				o.writeObject(entry.getKey());
				o.writeObject(entry.getValue());
			} catch (Exception e) {
				if (TestFlag.ENABLED) {
					System.err.println(TaskExecutionResult.class.getSimpleName() + " writeTaggedOutputsMap: " + e);
				}
				InternalBuildTraceImpl.serializationException(e);
			}
		});
	}

	private static Map<Object, Object> readTaggedOutputsMap(ObjectInput in) throws IOException, ClassNotFoundException {
		List<Entry<Object, Object>> entrieslist = SerialUtils.readExternalCollection(new ArrayList<>(), in, i -> {
			Object key = i.readObject();
			Object value = i.readObject();
			return ImmutableUtils.makeImmutableMapEntry(key, value);
		});
		Map<Object, Object> readmap = new HashMap<>(entrieslist.size());
		for (Entry<Object, Object> entry : entrieslist) {
			readmap.put(entry.getKey(), entry.getValue());
		}
		return ImmutableUtils.unmodifiableMap(readmap);
	}

	private static NavigableMap<SakerPath, ContentDescriptor> readContentDescriptorMap(ObjectInput in)
			throws ClassNotFoundException, IOException {
		return SerialUtils.readExternalSortedImmutableNavigableMap(in, SerialUtils::readExternalObject, i -> {
			ContentDescriptor content;
			try {
				content = (ContentDescriptor) in.readObject();
			} catch (IOException | ClassNotFoundException | ClassCastException e) {
				if (TestFlag.ENABLED) {
					System.err.println(TaskExecutionResult.class.getSimpleName() + " readContentDescriptorMap: " + e);
				}
				InternalBuildTraceImpl.serializationException(e);
				content = NullContentDescriptor.getInstance();
			}
			return content;
		});
	}

	private static void writeContentDescriptorMap(ObjectOutput out, Map<SakerPath, ContentDescriptor> map)
			throws IOException {
		SerialUtils.writeExternalMap(out, map, ObjectOutput::writeObject, (o, v) -> {
			try {
				o.writeObject(v);
			} catch (IOException e) {
				if (TestFlag.ENABLED) {
					System.err.println(TaskExecutionResult.class.getSimpleName() + " writeContentDescriptorMap: " + e);
				}
				InternalBuildTraceImpl.serializationException(e);
			}
		});
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (factory != null ? "factory=" + factory : "") + "]";
	}

}
