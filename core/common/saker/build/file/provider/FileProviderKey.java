package saker.build.file.provider;

import java.io.Externalizable;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;

/**
 * Interface representing a key object that uniquely identifies a {@link SakerFileProvider} location.
 * <p>
 * Clients can be sure that if two file provider keys {@link #equals(Object) equal}, then they will execute actions on
 * the same files when they are given the same paths as parameters.
 * <p>
 * File provider keys should be serializable, preferably {@link Externalizable}.
 * 
 * @see RootFileProviderKey
 */
public interface FileProviderKey {
	@Override
	@RMICacheResult
	public int hashCode();

	/**
	 * Checks if this file provider key is the same as the parameter.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);

	@Override
	@RMICacheResult
	public String toString();
}
