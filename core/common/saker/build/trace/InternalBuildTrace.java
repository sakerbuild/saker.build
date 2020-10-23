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
package saker.build.trace;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import saker.build.exception.PropertyComputationFailedException;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.task.TaskContext;
import saker.build.task.TaskContextReference;
import saker.build.task.TaskDirectoryPathContext;
import saker.build.task.TaskExecutionResult;
import saker.build.task.TaskFactory;
import saker.build.task.TaskInvocationConfiguration;
import saker.build.task.delta.BuildDelta;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.util.exc.ExceptionView;
import testing.saker.build.flag.TestFlag;

public interface InternalBuildTrace extends Closeable {
	public static InternalBuildTrace current() {
		return InternalBuildTraceImpl.current();
	}

	public static InternalBuildTrace currentOrNull() {
		return InternalBuildTraceImpl.currentOrNull();
	}

	@Override
	public default void close() throws IOException {
	}

	public default void startBuild(SakerEnvironmentImpl environment, ExecutionContextImpl executioncontext) {
	}

	public default void initialize() {
	}

	public default void initializeDone(ExecutionContextImpl executioncontext) {
	}

	public default void startExecute() {
	}

	public default void endExecute(boolean successful) {
	}

	public default InternalTaskBuildTrace taskBuildTrace(TaskIdentifier taskid, TaskFactory<?> taskfactory,
			TaskDirectoryPathContext taskDirectoryContext, TaskInvocationConfiguration capabilityConfig) {
		return NullInternalBuildTrace.INSTANCE;
	}

	public default void ignoredException(TaskIdentifier taskid, ExceptionView e) {
	}

	public default void serializationException(String stacktrace) {
	}

	public default void serializationWarning(String message) {
	}

	public default void taskUpToDate(TaskExecutionResult<?> prevexecresult, TaskInvocationConfiguration capabilities) {
	}

	public default void upToDateTaskStandardOutput(TaskExecutionResult<?> prevexecresult,
			UnsyncByteArrayOutputStream baos) {
	}

	public default void startBuildCluster(SakerEnvironmentImpl environment, Path mirrordir) {
	}

	public default void endBuildCluster() {
	}

	public default void setValues(@RMISerialize Map<?, ?> values, String category) {
	}

	public default void addValues(@RMISerialize Map<?, ?> values, String category) {
	}

	public default <T> void environmentPropertyAccessed(SakerEnvironmentImpl environment,
			EnvironmentProperty<T> property, T value, PropertyComputationFailedException e) {
	}

	public default void openTargetConfigurationFile(ScriptParsingOptions parsingoptions, SakerFile file) {
	}

	@RMIWrap(NullInternalBuildTrace.NullInternalBuildTraceRMIWrapper.class)
	public static final class NullInternalBuildTrace implements InternalBuildTrace, InternalTaskBuildTrace {
		public static final NullInternalBuildTrace INSTANCE = new NullInternalBuildTrace();

		private NullInternalBuildTrace() {
		}

		public static class NullInternalBuildTraceRMIWrapper implements RMIWrapper {

			public NullInternalBuildTraceRMIWrapper() {
			}

			public NullInternalBuildTraceRMIWrapper(Object traceobj) {
			}

			@Override
			public void writeWrapped(RMIObjectOutput out) throws IOException {
			}

			@Override
			public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			}

			@Override
			public Object resolveWrapped() {
				return INSTANCE;
			}

			@Override
			public Object getWrappedObject() {
				return INSTANCE;
			}
		}

		@Override
		public void setValues(Map<?, ?> values, String category) {
		}

		@Override
		public void addValues(Map<?, ?> values, String category) {
		}

		@Override
		public void openTargetConfigurationFile(ScriptParsingOptions parsingoptions, SakerFile file) {
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	public interface InternalTaskBuildTrace {
		public static InternalTaskBuildTrace current() {
			try {
				TaskContextReference currentref = TaskContextReference.currentReference();
				if (currentref == null) {
					return NullInternalBuildTrace.INSTANCE;
				}
				InternalTaskBuildTrace bt = currentref.getTaskBuildTrace();
				if (bt != null) {
					return bt;
				}
			} catch (Exception e) {
				// this should never happen, but handle just in case as we may not throw
				if (TestFlag.ENABLED) {
					e.printStackTrace();
				}
			}
			return NullInternalBuildTrace.INSTANCE;
		}

		public static InternalTaskBuildTrace currentOrNull() {
			try {
				TaskContextReference currentref = TaskContextReference.currentReference();
				if (currentref == null) {
					return null;
				}
				InternalTaskBuildTrace bt = currentref.getTaskBuildTrace();
				if (bt != null) {
					return bt;
				}
			} catch (Exception e) {
				// this should never happen, but handle just in case as we may not throw
				if (TestFlag.ENABLED) {
					e.printStackTrace();
				}
			}
			return null;
		}

		public default void setStandardOutDisplayIdentifier(String displayid) {
		}

		public default void deltas(Set<? extends BuildDelta> deltas) {
		}

		public default void closeStandardIO(UnsyncByteArrayOutputStream stdout, UnsyncByteArrayOutputStream stderr) {
		}

		public default void close(TaskContext taskcontext, TaskExecutionResult<?> taskresult) {
		}

		public default void startTaskExecution() {
		}

		public default void endTaskExecution() {
		}

		public default InternalTaskBuildTrace startInnerTask(@RMISerialize TaskFactory<?> innertaskfactory) {
			return NullInternalBuildTrace.INSTANCE;
		}

		public default void endInnerTask() {
		}

		public default void setThrownException(@RMISerialize Throwable e) {
		}

		public default void setDisplayInformation(String timelinelabel, String title) {
		}

		public default void classifyTask(String classification) {
		}

		public default void reportOutputArtifact(SakerPath path, int embedflags) {
		}

		public default void setValues(@RMISerialize Map<?, ?> values, String category) {
		}

		public default void addValues(@RMISerialize Map<?, ?> values, String category) {
		}

		public default void omitInnerTask() {
		}

		public default void openTargetConfigurationFile(ScriptParsingOptions parsingoptions, SakerFile file) {
		}
	}

}
