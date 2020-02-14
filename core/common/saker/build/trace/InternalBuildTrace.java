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
import java.util.Set;

import saker.build.file.SakerFile;
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
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.util.exc.ExceptionView;

public interface InternalBuildTrace extends Closeable {
	public static InternalBuildTrace current() {
		return InternalBuildTraceImpl.current();
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

	public default void taskUpToDate(TaskExecutionResult<?> prevexecresult, TaskInvocationConfiguration capabilities) {
	}

	public default void upToDateTaskStandardOutput(TaskExecutionResult<?> prevexecresult,
			UnsyncByteArrayOutputStream baos) {
	}

	public default void startBuildCluster(SakerEnvironmentImpl environment, Path mirrordir) {
	}

	@RMIWrap(NullInternalBuildTrace.NullInternalBuildTraceRMIWrapper.class)
	public static final class NullInternalBuildTrace implements InternalBuildTrace, InternalTaskBuildTrace {
		public static final NullInternalBuildTrace INSTANCE = new NullInternalBuildTrace();

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
			}
			return NullInternalBuildTrace.INSTANCE;
		}

		public default void setStandardOutDisplayIdentifier(String displayid) {
		}

		public default void deltas(Set<? extends BuildDelta> deltas) {
		}

		public default void closeStandardIO(UnsyncByteArrayOutputStream stdout, UnsyncByteArrayOutputStream stderr) {
		}

		public default void close(TaskContext taskcontext, TaskExecutionResult<?> taskresult) {
		}

		public default ByteSource openTargetConfigurationReadingInput(ScriptParsingOptions parsingoptions,
				SakerFile file) throws IOException {
			return file.openByteSource();
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
	}

}
