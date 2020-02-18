package saker.build.trace;

import java.util.Map;
import java.util.UUID;

import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.trace.InternalBuildTraceImpl.EnvironmentInformation;

@SuppressWarnings("unused")
public interface ClusterInternalBuildTrace extends InternalBuildTrace {
	public default void startBuildCluster(@RMISerialize EnvironmentInformation envinfo, long nanos) {
	}

	public default void setClusterValues(@RMISerialize UUID environmentid, @RMISerialize Map<String, ?> values,
			String category) {
	}
}
