package saker.build.file.provider;

import java.util.UUID;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;

/**
 * File provider key that represents a root file provider.
 * <p>
 * Root file providers have no indirection and will access the underlying filesystem directly.
 * <p>
 * There is an {@link UUID} generated automatically for every local root file provider which they are uniquely
 * identified by. This {@link UUID} is usually generated one time for every machine that ever runs the build system. (It
 * is generated the first time {@link LocalFileProvider} is used, and the provider key {@link UUID} is stored in the
 * default storage directory of the build system.)
 * <p>
 * Note that it is not required that all root file provider implementations are backed by {@link LocalFileProvider}.
 * 
 * @see LocalFileProvider
 */
public interface RootFileProviderKey extends FileProviderKey {
	/**
	 * Gets the unique identifier for this root file provider.
	 * 
	 * @return The identifier.
	 */
	@RMICacheResult
	public UUID getUUID();
}
