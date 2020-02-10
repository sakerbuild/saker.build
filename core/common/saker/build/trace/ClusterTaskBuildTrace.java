package saker.build.trace;

import java.util.UUID;

import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.trace.InternalBuildTrace.InternalTaskBuildTrace;
import saker.build.util.exc.ExceptionView;

@SuppressWarnings("unused")
public interface ClusterTaskBuildTrace extends InternalTaskBuildTrace {
	public default void startClusterTaskExecution(long nanos, @RMISerialize UUID executionEnvironmentUUID) {
	}

	public default void endClusterTaskExecution(long nanos) {
	}

	public default void startClusterInnerTask(Object innertaskidentity, long nanos,
			@RMISerialize UUID executionEnvironmentUUID, String innertaskclassname) {
	}

	public default void endClusterInnerTask(Object innertaskidentity, long nanos) {
	}

	public default void setThrownException(@RMISerialize ExceptionView e) {
	}

	public default void setClusterInnerTaskThrownException(Object innertaskidentity, @RMISerialize ExceptionView e) {
	}

	public default void setClusterInnerTaskTitle(Object innertaskidentity, String title) {
	}

	public default void setClusterInnerTaskTimelineLabel(Object innertaskidentity, String label) {
	}
}