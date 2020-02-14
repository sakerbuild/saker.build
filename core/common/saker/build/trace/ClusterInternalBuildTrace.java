package saker.build.trace;

import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.trace.InternalBuildTraceImpl.EnvironmentInformation;

@SuppressWarnings("unused")
public interface ClusterInternalBuildTrace extends InternalBuildTrace {
	public default void startBuildCluster(@RMISerialize EnvironmentInformation envinfo, long nanos) {
	}
}
