package saker.build.trace;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;

import saker.build.file.SakerFile;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.task.InternalTaskContext;
import saker.build.task.TaskContext;
import saker.build.task.TaskContextReference;
import saker.build.task.TaskDirectoryPathContext;
import saker.build.task.TaskExecutionResult;
import saker.build.task.TaskFactory;
import saker.build.task.TaskInvocationConfiguration;
import saker.build.task.delta.BuildDelta;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.io.ByteSource;

public interface InternalBuildTrace extends Closeable {
	public static final InternalBuildTrace NULL_INSTANCE = NullInternalBuildTrace.INSTANCE;

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

	public default void endExecute() {
	}

	public default InternalTaskBuildTrace taskBuildTrace(TaskIdentifier taskid, TaskFactory<?> taskfactory,
			TaskDirectoryPathContext taskDirectoryContext, TaskInvocationConfiguration capabilityConfig) {
		return InternalTaskBuildTrace.NULL_INSTANCE;
	}

	public default void taskUpToDate(TaskExecutionResult<?> prevexecresult) {
	}

	@RMIWrap(NullInternalBuildTrace.NullInternalBuildTraceRMIWrapper.class)
	public static final class NullInternalBuildTrace implements InternalBuildTrace, InternalTaskBuildTrace {
		public static final NullInternalBuildTrace INSTANCE = new NullInternalBuildTrace();

		public static class NullInternalBuildTraceRMIWrapper implements RMIWrapper {
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
		public static final InternalTaskBuildTrace NULL_INSTANCE = NullInternalBuildTrace.INSTANCE;

		public static InternalTaskBuildTrace current() {
			try {
				InternalTaskContext tc = (InternalTaskContext) TaskContextReference.current();
				if (tc == null) {
					return InternalTaskBuildTrace.NULL_INSTANCE;
				}
				InternalTaskBuildTrace bt = tc.internalGetBuildTrace();
				if (bt != null) {
					return bt;
				}
			} catch (Exception e) {
				// this should never happen, but handle just in case as we may not throw
			}
			return InternalTaskBuildTrace.NULL_INSTANCE;
		}

		public default void setStandardOutDisplayIdentifier(String displayid) {
		}

		public default void deltas(Set<? extends BuildDelta> deltas) {
		}

		public default void close(TaskContext taskcontext, TaskExecutionResult<?> taskresult) {
		}

		public default ByteSource openTargetConfigurationReadingInput(ScriptParsingOptions parsingoptions,
				SakerFile file) throws IOException {
			return file.openByteSource();
		}
	}

}
