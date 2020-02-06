package saker.build.trace;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;

import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.task.TaskContext;
import saker.build.task.TaskDirectoryPathContext;
import saker.build.task.TaskExecutionResult;
import saker.build.task.TaskFactory;
import saker.build.task.TaskInvocationConfiguration;
import saker.build.task.delta.BuildDelta;
import saker.build.task.identifier.TaskIdentifier;

public interface InternalBuildTrace extends Closeable {
	public static final InternalBuildTrace NULL_INSTANCE = new InternalBuildTrace() {
	};

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

	public interface InternalTaskBuildTrace {
		public static final InternalTaskBuildTrace NULL_INSTANCE = new InternalTaskBuildTrace() {
		};

		public default void setStandardOutDisplayIdentifier(String displayid) {
		}

		public default void deltas(Set<? extends BuildDelta> deltas) {
		}

		public default void close(TaskContext taskcontext, TaskExecutionResult<?> taskresult) {
		}
	}

}
