/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.meta;

import saker.apiextract.api.ExcludeApi;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerFileProvider;
import saker.build.util.config.ReferencePolicy;

/**
 * Class for containing system property names that can configure the Saker runtime.
 * <p>
 * These properties specify settings for the runtime that cannot be changed during the execution of the program. <br>
 * For environment and build execution specific configuration, refer to the appropriate parameter classes.
 * 
 * @see System#getProperties()
 */
public class PropertyNames {
	/**
	 * Property name for {@link ReferencePolicy} to determine what kind of references should be created for efficient
	 * caching.
	 * <p>
	 * Allowed values are <code>weak</code> and <code>soft</code>. Character case is ignored. Default value is
	 * <code>soft</code>.
	 * 
	 * @see ReferencePolicy
	 */
	public static final String PROPERTY_SAKER_REFERENCE_POLICY = "saker.reference.policy";

	/**
	 * Property name for specifying the extraction directory for embedded native dynamic libraries.
	 * <p>
	 * The native libraries have to be extracted to the local filesystem before they can be loaded to the JVM process.
	 * The directory specified will be used to extract these libraries.
	 * <p>
	 * Default value is determined from the system property <code>java.io.tmpdir</code>, the temp directory for the
	 * process.
	 * 
	 * @see System#getProperties()
	 */
	public static final String PROPERTY_SAKER_OSNATIVE_LIBRARYPATH = "saker.osnative.librarypath";

	/**
	 * Property name for {@link SakerFileProvider} to specify the directory where the cache files are stored for remote
	 * file providers.
	 * <p>
	 * When a build is run over the network, some of the files can reside on other computers. Caching the files on a
	 * remote host can improve performance, as unmodified files will not have to be transferred over the network again.
	 * <p>
	 * The default value for this feature is to use the temporary directory specified by <code>java.io.tmpdir</code>.
	 * <p>
	 * To disable cache for all remote files, set this property to an empty string.
	 * 
	 * @see System#getProperties()
	 * @deprecated This property is not currently in use.
	 */
	@Deprecated
	@ExcludeApi
	public static final String PROPERTY_SAKER_FILES_REMOTE_CACHE_DIRECTORY = "saker.files.remote.cache.directory";

	/**
	 * Property name for {@link SakerFileProvider} to specify a list of file provider identifiers, to disable remote
	 * caching for them.
	 * <p>
	 * This property is a comma separated concatenation of root file provider keys. Adding a provider key to the list
	 * disables any file caching for that file provider.
	 * <p>
	 * TODO document where can the provider keys be found
	 * 
	 * @see #PROPERTY_SAKER_FILES_REMOTE_CACHE_DIRECTORY
	 * @see LocalFileProvider#getProviderKey()
	 * @deprecated This property is not currently in use.
	 */
	@Deprecated
	@ExcludeApi
	public static final String PROPERTY_SAKER_FILES_DISABLE_REMOTE_CACHE = "saker.files.disable.remote.cache";

	/**
	 * Property name for to set the maximum amount of allocateable computation tokens for this JVM.
	 * <p>
	 * Increasing this value might cause the system to trash if too many tasks are running at the same time. Decreasing
	 * this value might cause the CPU to be underutilized.
	 * <p>
	 * The default value is the current logical processor count multiplied by 1.5, and rounded down. It is at least two.
	 * Effectively, this formula is used to calculate it:
	 * 
	 * <pre>
	 * Math.max(Runtime.getRuntime().availableProcessors() * 3 / 2, 2)
	 * </pre>
	 */
	public static final String PROPERTY_SAKER_COMPUTATION_TOKEN_COUNT = "saker.computation.token.count";

	/**
	 * Property name for specifying the default storage directory for build system environments.
	 * <p>
	 * The specified storage directory will be used when not specified otherwise when creating build environments.
	 */
	public static final String PROPERTY_DEFAULT_STORAGE_DIRECTORY = "saker.build.storage.directory.default";

	/**
	 * Property name for signalling that RMI statistics should be collected and displayed for measurement purposes.
	 * <p>
	 * If this property is set, any RMI connection that is opened should collect statistics of their requests. This
	 * property name is just a hint that should allow better measurements and analysis. Some opened RMI connections may
	 * not support this property.
	 * 
	 * @since saker.build 0.8.15
	 */
	public static final String PROPERTY_COLLECT_RMI_STATISTICS = "saker.build.rmi.statistics.collect";

	/**
	 * Gets the JVM level property with the given name.
	 * <p>
	 * This method will retrieve {@link System#getProperty(String)} for the specified name and return it if
	 * non-<code>null</code>.
	 * <p>
	 * Then {@link System#getenv(String)} will be retrieved with the <code>'.'</code> characters replaced to
	 * <code>'_'</code> in the property name for compatibility. If non-<code>null</code>, that will be returned.
	 * <p>
	 * If the above fails, <code>null</code> is returned.
	 * 
	 * @param propertyname
	 *            The property name.
	 * @return The property value or <code>null</code> if not defined.
	 */
	public static String getProperty(String propertyname) {
		String res = System.getProperty(propertyname);
		if (res != null) {
			return res;
		}
		//replace the dots to underscores in environment variables for compatibility
		res = System.getenv(propertyname.replace('.', '_'));
		if (res != null) {
			return res;
		}
		return null;
	}

	private PropertyNames() {
		throw new UnsupportedOperationException();
	}
}
