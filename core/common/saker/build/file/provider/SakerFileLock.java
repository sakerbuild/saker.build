package saker.build.file.provider;

import java.io.Closeable;
import java.io.IOException;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMIExceptionRethrow;
import saker.build.thirdparty.saker.util.io.RemoteIOException;

public interface SakerFileLock extends Closeable {
	@RMIExceptionRethrow(RemoteIOException.class)
	public void lock() throws IOException, IllegalStateException;

	@RMIExceptionRethrow(RemoteIOException.class)
	public boolean tryLock() throws IOException, IllegalStateException;

	@RMIExceptionRethrow(RemoteIOException.class)
	public void release() throws IOException, IllegalStateException;
}
