package saker.build.file.path;

import saker.build.file.provider.SakerFileProvider;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;

/**
 * {@link PathKey} subinterface holding a reference to the associated root file provider.
 * 
 * @see SimpleProviderHolderPathKey
 */
public interface ProviderHolderPathKey extends PathKey {
	/**
	 * The file provider that is associated to the path key.
	 * 
	 * @return The file provider.
	 * @see #getFileProviderKey()
	 */
	@RMICacheResult
	public SakerFileProvider getFileProvider();
}
