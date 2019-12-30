package saker.build.runtime.project;

import java.io.Closeable;
import java.io.IOException;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMIDefaultOnFailure;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;

public interface ProjectCacheHandle extends Closeable {
	public void reset();

	public void clean();

	@RMIForbidden
	@RMIDefaultOnFailure
	public default SakerProjectCache toProject() {
		return null;
	}

	@Override
	public default void close() throws IOException {
	}
}
