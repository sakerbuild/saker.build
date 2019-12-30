package saker.build.runtime.params;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileProviderKey;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Configuration class containing the path configurations for a build execution.
 * <p>
 * An instance of this class can be created via the provided {@linkplain #builder(SakerPath) builder}.
 * <p>
 * The class contains the working directory of the execution and any file providers that are used for accessing the
 * files. The file providers may be any {@link SakerFileProvider} implementations.
 * <p>
 * The class maps arbitrary root names to their respective file providers to access them through. The root names follow
 * the rules specified in {@link SakerPath}.
 */
@RMIWrap(ExecutionPathConfiguration.ConfigurationRMIWrapper.class)
public final class ExecutionPathConfiguration {

	/**
	 * Builder class for {@link ExecutionPathConfiguration}.
	 * <p>
	 * The builder is single use, it cannot be reused after calling {@link #build()}.
	 */
	public static final class Builder {
		protected SakerPath workingDirectory;
		protected NavigableMap<String, SakerFileProvider> rootFileProviders = new TreeMap<>();

		protected Builder(SakerPath workingDirectory) throws InvalidPathFormatException {
			SakerPathFiles.requireAbsolutePath(workingDirectory);
			this.workingDirectory = workingDirectory;
		}

		protected Builder(ExecutionPathConfiguration pathconfiguration, SakerPath workingDirectory) {
			SakerPathFiles.requireAbsolutePath(workingDirectory);
			this.workingDirectory = workingDirectory;
			this.rootFileProviders.putAll(pathconfiguration.rootFileProviders);
		}

		/**
		 * Adds a file provider for the specified root.
		 * <p>
		 * This method overwrites any previous roots added with the specified name.
		 * 
		 * @param root
		 *            The root name.
		 * @param fileprovider
		 *            The file provider to add for the root.
		 * @return <code>this</code>
		 * @throws InvalidPathFormatException
		 *             If the root is not a valid root name.
		 * @throws IllegalArgumentException
		 *             If the file provider doesn't have a root with the specified name.
		 */
		public Builder addRootProvider(String root, SakerFileProvider fileprovider)
				throws InvalidPathFormatException, IllegalArgumentException {
			String normalizedroot = SakerPath.normalizeRoot(root);
			try {
				if (!fileprovider.getRoots().contains(normalizedroot)) {
					throw new IllegalArgumentException(
							"File provide does not have root: " + root + " available: " + fileprovider.getRoots());
				}
			} catch (IOException | RMIRuntimeException e) {
				throw new IllegalArgumentException("Failed to retrieve roots from file provider: " + fileprovider, e);
			}
			//overwrite previous
			rootFileProviders.put(normalizedroot, fileprovider);
			return this;
		}

		/**
		 * Adds all roots of the file provider to this configuration.
		 * <p>
		 * This method overwrites any previous roots added with the specified name.
		 * 
		 * @param fileprovider
		 *            The file provider.
		 * @return <code>this</code>
		 * @throws IOException
		 *             In case of I/O error.
		 */
		public Builder addAllRoots(SakerFileProvider fileprovider) throws IOException {
			for (String r : fileprovider.getRoots()) {
				rootFileProviders.put(r, fileprovider);
			}
			return this;
		}

		/**
		 * Adds all roots in the argument path configuration to this configuration.
		 * <p>
		 * This method overwrites any previous roots added with the specified name.
		 * 
		 * @param sourceconfiguration
		 *            The configuration to copy the roots from.
		 * @return <code>this</code>
		 */
		public Builder addAll(ExecutionPathConfiguration sourceconfiguration) {
			//overwrite all previous
			this.rootFileProviders.putAll(sourceconfiguration.rootFileProviders);
			return this;
		}

		/**
		 * Build the {@link ExecutionPathConfiguration}.
		 * <p>
		 * The builder is no longer useable if this call succeeds.
		 * 
		 * @return The constructed path configuration.
		 * @throws AmbiguousPathConfigurationException
		 *             If the created configuration is ambiguous.
		 * @throws IllegalStateException
		 *             If the builder was already used.
		 */
		public ExecutionPathConfiguration build() throws AmbiguousPathConfigurationException, IllegalStateException {
			NavigableMap<String, SakerFileProvider> rootfileproviders = rootFileProviders;
			if (rootfileproviders == null) {
				throw new IllegalStateException("Builder already used.");
			}
			checkWorkingDirectoryInRoots(workingDirectory, rootfileproviders.keySet());

			ExecutionPathConfiguration result = new ExecutionPathConfiguration(this.workingDirectory);
			result.rootFileProviders = rootfileproviders;
			for (Entry<String, SakerFileProvider> entry : result.rootFileProviders.entrySet()) {
				result.addCachedProviderKeys(entry.getValue());
			}
			Map<FileProviderKey, NavigableMap<SakerPath, SakerPath>> rootpathkeys = new HashMap<>();
			for (String root : result.rootFileProviders.keySet()) {
				SakerPath rootpath = SakerPath.valueOf(root);
				ProviderHolderPathKey pk = result.getPathKey(rootpath);
				SakerPath pkpath = pk.getPath();
				SakerPath prev = rootpathkeys.computeIfAbsent(pk.getFileProviderKey(), Functionals.treeMapComputer())
						.put(pkpath, rootpath);
				if (prev != null) {
					//the same path is defined by multiple providers
					throw new AmbiguousPathConfigurationException(
							pkpath + " is accessible through multile paths: " + rootpath + " and " + prev);
				}
			}
			if (!rootpathkeys.isEmpty()) {
				for (Entry<FileProviderKey, NavigableMap<SakerPath, SakerPath>> entry : rootpathkeys.entrySet()) {
					NavigableMap<SakerPath, SakerPath> pathkeys = entry.getValue();
					Iterator<Entry<SakerPath, SakerPath>> it = pathkeys.entrySet().iterator();
					Entry<SakerPath, SakerPath> prev = it.next();
					while (it.hasNext()) {
						Entry<SakerPath, SakerPath> c = it.next();

						if (c.getKey().startsWith(prev.getKey())) {
							SakerPath rel = prev.getKey().relativize(c.getKey());
							throw new AmbiguousPathConfigurationException(
									c.getKey() + " is accessible through multile paths: " + c.getValue() + " and "
											+ prev.getValue().resolve(rel));
						}

						prev = c;
					}
				}
			}
			this.rootFileProviders = null;
			return result;
		}
	}

	protected SakerPath workingDirectory;
	protected NavigableMap<String, SakerFileProvider> rootFileProviders = new TreeMap<>();

	protected transient Map<FileProviderKey, SakerFileProvider> keyProviders = new HashMap<>();

	//lazily computed
	private transient Map<RootFileProviderKey, NavigableMap<SakerPath, String>> keyRootPathRoots = null;

	/**
	 * For RMI serialization.
	 */
	private ExecutionPathConfiguration() {
	}

	private ExecutionPathConfiguration(SakerPath workingDirectory) {
		this.workingDirectory = workingDirectory;
	}

	/**
	 * Creates a new configuration builder with the specified working directory.
	 * 
	 * @param workingdirectory
	 *            The working directory to use for the configuration.
	 * @return The builder.
	 * @throws InvalidPathFormatException
	 *             If the working directory is not absolute.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static Builder builder(SakerPath workingdirectory) throws InvalidPathFormatException, NullPointerException {
		return new Builder(workingdirectory);
	}

	/**
	 * Creates a new configuration builder that copies the file providers from the given configuration and uses the
	 * specified working directory.
	 * 
	 * @param pathconfiguration
	 *            The path configuration to copy.
	 * @param workingdirectory
	 *            The working directory to use for the configuration.
	 * @return The builder.
	 * @throws InvalidPathFormatException
	 *             If the working directory is not absolute.
	 * @throws NullPointerException
	 *             If any of the argument is <code>null</code>.
	 */
	public static Builder builder(ExecutionPathConfiguration pathconfiguration, SakerPath workingdirectory)
			throws InvalidPathFormatException, NullPointerException {
		Objects.requireNonNull(pathconfiguration, "path configuration");
		return new Builder(pathconfiguration, workingdirectory);
	}

	/**
	 * Creates a new configuration by copying an existing one and replacing the working directory.
	 * 
	 * @param copyconfig
	 *            The configuration to copy.
	 * @param workingdirectory
	 *            The working directory to use for the new configuration.
	 * @return The created configuration.
	 * @throws InvalidPathFormatException
	 *             If the working directory is not absolute.
	 */
	public static ExecutionPathConfiguration copy(ExecutionPathConfiguration copyconfig, SakerPath workingdirectory)
			throws InvalidPathFormatException {
		SakerPathFiles.requireAbsolutePath(workingdirectory);

		ExecutionPathConfiguration result = new ExecutionPathConfiguration(workingdirectory);
		checkWorkingDirectoryInRoots(workingdirectory, copyconfig.rootFileProviders.keySet());
		result.rootFileProviders = copyconfig.rootFileProviders;
		result.keyProviders = copyconfig.keyProviders;
		return result;
	}

	/**
	 * Creates a path configuration that includes all roots from the {@linkplain LocalFileProvider local file provider}
	 * and uses the specified working directory.
	 * 
	 * @param workingdirectory
	 *            The working directory to use.
	 * @return The created path configuration.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public static ExecutionPathConfiguration local(SakerPath workingdirectory) throws IOException {
		LocalFileProvider fp = LocalFileProvider.getInstance();
		return forProvider(workingdirectory, fp);
	}

	/**
	 * Creates a new path configuration that includes all roots from the specified file provider and uses the given
	 * working directory.
	 * 
	 * @param workingdirectory
	 *            The working directory to use.
	 * @param fp
	 *            The file provider to include the roots from.
	 * @return The created path configuration.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws InvalidPathFormatException
	 *             If the working directory is not absolute.
	 */
	public static ExecutionPathConfiguration forProvider(SakerPath workingdirectory, SakerFileProvider fp)
			throws IOException, InvalidPathFormatException {
		SakerPathFiles.requireAbsolutePath(workingdirectory);
		ExecutionPathConfiguration result = new ExecutionPathConfiguration(workingdirectory);
		Set<String> roots = fp.getRoots();
		checkWorkingDirectoryInRoots(workingdirectory, roots);
		for (String root : roots) {
			result.rootFileProviders.put(root, fp);
			result.addCachedProviderKeys(fp);
		}
		return result;
	}

	/**
	 * Gets the working directory.
	 * 
	 * @return The absolute path to the working directory.
	 */
	public SakerPath getWorkingDirectory() {
		return workingDirectory;
	}

	/**
	 * Gets the path key for the working directory.
	 * 
	 * @return The path key.
	 */
	public ProviderHolderPathKey getWorkingDirectoryPathKey() {
		return getPathKey(workingDirectory);
	}

	/**
	 * Gets the roots and corresponding file providers of this configuration.
	 * 
	 * @return An unmodifiable map of root file providers.
	 */
	public NavigableMap<String, SakerFileProvider> getRootFileProviders() {
		return ImmutableUtils.unmodifiableNavigableMap(rootFileProviders);
	}

	/**
	 * Gets the root names this configuration is configured for.
	 * 
	 * @return An unmodifiable set of root names used by this configuration.
	 */
	public NavigableSet<String> getRootNames() {
		return ImmutableUtils.unmodifiableNavigableSet(rootFileProviders.navigableKeySet());
	}

	/**
	 * Checks if this configuration has a file provider corresponding for the given root name.
	 * <p>
	 * The root name is not validated if it has a valid format.
	 * 
	 * @param rootname
	 *            The root name.
	 * @return <code>true</code> if this configuration has a file provider for the root name.
	 */
	public boolean hasRoot(String rootname) {
		return rootFileProviders.containsKey(rootname);
	}

	/**
	 * Checks if this configuration contains a file provider for the given file provider key.
	 * 
	 * @param key
	 *            The file provider key.
	 * @return <code>true</code> if this configuration has a file provider corresponding to the given key.
	 */
	public boolean hasFileProvider(FileProviderKey key) {
		return keyProviders.containsKey(key);
	}

	/**
	 * Gets the file provider corresponding to the specified file provider key.
	 * <p>
	 * It is not required that the argument key is a root file provider key.
	 * 
	 * @param key
	 *            The file provider key.
	 * @return The file provider for the given key.
	 * @throws IllegalArgumentException
	 *             If this configuration has no file provider for the key.
	 * @see #hasFileProvider(FileProviderKey)
	 * @see #getFileProviderIfPresent(FileProviderKey)
	 */
	public SakerFileProvider getFileProvider(FileProviderKey key) throws IllegalArgumentException {
		SakerFileProvider result = keyProviders.get(key);
		if (result == null) {
			throw new IllegalArgumentException(
					"File provider is not present for key: " + key + " available: " + keyProviders.keySet());
		}
		return result;
	}

	/**
	 * Gets the file provider which the path can be used for to access files.
	 * <p>
	 * This method requires the argument to be absolute, it won't be resolved against the
	 * {@linkplain #getWorkingDirectory() working directory}.
	 * 
	 * @param path
	 *            The path.
	 * @return The file provider for the path.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @throws IllegalArgumentException
	 *             If no file provider was found for the path.
	 * @see #getFileProviderIfPresent(SakerPath)
	 * @see #hasRoot(String)
	 */
	public SakerFileProvider getFileProvider(SakerPath path)
			throws InvalidPathFormatException, IllegalArgumentException {
		SakerPathFiles.requireAbsolutePath(path);
		return getRootFileProvider(path.getRoot());
	}

	/**
	 * Gets the file provider corresponding to the specified file provider key if present.
	 * <p>
	 * It is not required that the argument key is a root file provider key.
	 * 
	 * @param key
	 *            The file provider key.
	 * @return The file provider for the given key or <code>null</code> if not found in this configuration.
	 * @see #hasFileProvider(FileProviderKey)
	 * @see #getFileProvider(FileProviderKey)
	 */
	public SakerFileProvider getFileProviderIfPresent(FileProviderKey key) {
		return keyProviders.get(key);
	}

	/**
	 * Gets the file provider which the path can be used for to access files if present.
	 * <p>
	 * This method requires the argument to be absolute, it won't be resolved against the
	 * {@linkplain #getWorkingDirectory() working directory}.
	 * 
	 * @param path
	 *            The path.
	 * @return The file provider for the path or <code>null</code> if not found in this configuration.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see #getFileProvider(SakerPath)
	 * @see #hasRoot(String)
	 */
	public SakerFileProvider getFileProviderIfPresent(SakerPath path) throws InvalidPathFormatException {
		SakerPathFiles.requireAbsolutePath(path);
		return rootFileProviders.get(path.getRoot());
	}

	/**
	 * Gets the file provider for the specified root name.
	 * <p>
	 * The root name is not validated if it has a valid format.
	 * 
	 * @param root
	 *            The root name.
	 * @return The file provider which was configured for the specified root.
	 * @throws IllegalArgumentException
	 *             If no file provider was found for the root.
	 * @see #hasRoot(String)
	 */
	public SakerFileProvider getRootFileProvider(String root) throws IllegalArgumentException {
		SakerFileProvider result = rootFileProviders.get(root);
		if (result == null) {
			throw new IllegalArgumentException("No root provider found with root: " + root);
		}
		return result;
	}

	/**
	 * Gets the file provider for the specified root name if present .
	 * <p>
	 * The root name is not validated if it has a valid format.
	 * 
	 * @param root
	 *            The root name.
	 * @return The file provider or <code>null</code> if no file provider was configured for the specified root.
	 * @see #hasRoot(String)
	 */
	public SakerFileProvider getRootFileProviderIfPresent(String root) {
		return rootFileProviders.get(root);
	}

	/**
	 * Attempts to determine the corresponding path on the {@linkplain LocalFileProvider local file system} for the
	 * specified path.
	 * <p>
	 * This method will try to uniquely identify the local file system path corresponding to the argument path.
	 * <p>
	 * This method determines the {@linkplain PathKey path key} for the argument path, and checks if the file provider
	 * for it is the same as the local file system. If they equal, then a localized {@linkplain Path path} will be
	 * returned for it.
	 * <p>
	 * The argument path must be absolute.
	 * <p>
	 * The inverse operation is {@link #toExecutionPath(Path)}.
	 * 
	 * @param path
	 *            The path to localize.
	 * @return The local path or <code>null</code> if the path doesn't denote a local path.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see #toExecutionPath(Path)
	 */
	public Path toLocalPath(SakerPath path) throws InvalidPathFormatException {
		SakerPathFiles.requireAbsolutePath(path);
		SakerFileProvider rootprovider = getFileProviderIfPresent(path);
		if (rootprovider == null) {
			return null;
		}
		for (SakerFileProvider w; (w = rootprovider.getWrappedProvider()) != null;) {
			path = rootprovider.resolveWrappedPath(path);
			rootprovider = w;
		}
		if (LocalFileProvider.getProviderKeyStatic().equals(rootprovider.getProviderKey())) {
			try {
				return LocalFileProvider.toRealPath(path);
			} catch (InvalidPathException e) {
			}
		}
		return null;
	}

	/**
	 * Attempts to determine the corresponding execution path for the specified {@linkplain LocalFileProvider local file
	 * system} path.
	 * <p>
	 * This method will try to uniquely identify the path used during execution for a specified local file system path.
	 * <p>
	 * This method returns non-<code>null</code> if and only if there exists a path configured by this configuration for
	 * accessing the specified argument. If the specified argument path cannot be accessed using file providers in this
	 * path configuration, <code>null</code> will be returned.
	 * <p>
	 * The argument path must be absolute.
	 * <p>
	 * The inverse operation is {@link #toLocalPath(SakerPath)}.
	 * 
	 * @param path
	 *            The path.
	 * @return The execution path which can be used to access the same path as the argument or <code>null</code> if
	 *             there is no such path.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see #toLocalPath(SakerPath)
	 */
	public SakerPath toExecutionPath(Path path) throws InvalidPathFormatException {
		SakerPathFiles.requireAbsolutePath(path);
		NavigableMap<SakerPath, String> rootpathkeys = getKeyRootPathRoots()
				.get(LocalFileProvider.getProviderKeyStatic());
		if (rootpathkeys == null) {
			return null;
		}
		SakerPath spath = SakerPath.valueOf(path);
		Entry<SakerPath, String> entry = SakerPathFiles.getPathOrParentEntry(rootpathkeys, spath);
		if (entry != null) {
			SakerPath rpkpath = entry.getKey();
			if (spath.startsWith(rpkpath)) {
				String root = entry.getValue();
				return spath.subPath(root, rpkpath.getNameCount());
			}
		}
		return null;
	}

	/**
	 * Attempts to determine the corresponding execution path for the specified path key.
	 * <p>
	 * This method will try to uniquely identify the path used during execution for a specified path key.
	 * <p>
	 * This method returns non-<code>null</code> if and only if there exists a path configured by this configuration for
	 * accessing the specified argument. If the specified argument path cannot be accessed using file providers in this
	 * path configuration, <code>null</code> will be returned.
	 * <p>
	 * The inverse operation is {@link #getPathKey(SakerPath)}.
	 * 
	 * @param path
	 *            The path.
	 * @return The execution path which can be used to access the same path as the argument or <code>null</code> if
	 *             there is no such path.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see #toLocalPath(SakerPath)
	 */
	public SakerPath toExecutionPath(PathKey path) throws InvalidPathFormatException {
		NavigableMap<SakerPath, String> rootpathkeys = getKeyRootPathRoots().get(path.getFileProviderKey());
		if (rootpathkeys == null) {
			return null;
		}
		SakerPath spath = path.getPath();
		Entry<SakerPath, String> entry = SakerPathFiles.getPathOrParentEntry(rootpathkeys, spath);
		if (entry != null) {
			SakerPath rpkpath = entry.getKey();
			if (spath.startsWith(rpkpath)) {
				String root = entry.getValue();
				return spath.subPath(root, rpkpath.getNameCount());
			}
		}
		return null;
	}

	/**
	 * Gets the {@linkplain ProviderHolderPathKey path key} for a path, resolving against the working directory if
	 * needed.
	 * 
	 * @param path
	 *            The path.
	 * @return The path key.
	 * @throws IllegalArgumentException
	 *             If the configuration contains no root for the specified path.
	 */
	public ProviderHolderPathKey getPathKey(SakerPath path) throws IllegalArgumentException {
		path = this.workingDirectory.tryResolve(path);
		SakerFileProvider rootprovider = getFileProvider(path);
		return SakerPathFiles.getPathKey(rootprovider, path);
	}

	/**
	 * Checks if the root file providers in this configuration is the same as in the argument configuration.
	 * 
	 * @param other
	 *            The path configuration to compare the file providers with.
	 * @return <code>true</code> if the root configurations are the same.
	 */
	public boolean isSameProviderConfiguration(ExecutionPathConfiguration other) {
		return ObjectUtils.mapOrderedEquals(this.rootFileProviders, other.rootFileProviders,
				SakerPathFiles::isSameProvider);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + rootFileProviders.keySet().hashCode();
		result = prime * result + workingDirectory.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ExecutionPathConfiguration other = (ExecutionPathConfiguration) obj;
		if (workingDirectory == null) {
			if (other.workingDirectory != null)
				return false;
		} else if (!workingDirectory.equals(other.workingDirectory))
			return false;
		if (!isSameProviderConfiguration(other)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ (workingDirectory != null ? "workingDirectory=" + workingDirectory + ", " : "")
				+ (rootFileProviders != null ? "rootFileProviders=" + rootFileProviders : "") + "]";
	}

	protected static final class ConfigurationRMIWrapper implements RMIWrapper {
		private ExecutionPathConfiguration configuration;

		public ConfigurationRMIWrapper() {
		}

		public ConfigurationRMIWrapper(ExecutionPathConfiguration pathConfiguration) {
			this.configuration = pathConfiguration;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeObject(configuration.workingDirectory);
			SerialUtils.writeExternalMap(out, configuration.rootFileProviders, ObjectOutput::writeUTF,
					RMIObjectOutput::writeRemoteObject);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			configuration = new ExecutionPathConfiguration();
			configuration.workingDirectory = (SakerPath) in.readObject();
			NavigableMap<String, SakerFileProvider> rfileproviders = SerialUtils
					.readExternalSortedImmutableNavigableMap(in, ObjectInput::readUTF, SerialUtils::readExternalObject);
			for (Entry<String, SakerFileProvider> entry : rfileproviders.entrySet()) {
				SakerFileProvider fp = entry.getValue();
				configuration.rootFileProviders.putIfAbsent(entry.getKey(), fp);
				configuration.addCachedProviderKeys(fp);
			}
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object resolveWrapped() {
			return configuration;
		}

	}

	private Map<RootFileProviderKey, NavigableMap<SakerPath, String>> getKeyRootPathRoots() {
		Map<RootFileProviderKey, NavigableMap<SakerPath, String>> result = this.keyRootPathRoots;
		if (result == null) {
			result = new HashMap<>();
			for (Entry<String, SakerFileProvider> entry : rootFileProviders.entrySet()) {
				String rootname = entry.getKey();
				ProviderHolderPathKey pk = getPathKey(SakerPath.valueOf(rootname));
				result.computeIfAbsent(pk.getFileProviderKey(), Functionals.treeMapComputer()).put(pk.getPath(),
						rootname);
			}
			this.keyRootPathRoots = result;
		}
		return result;
	}

	private static void checkWorkingDirectoryInRoots(SakerPath workingdirectory, Set<String> roots) {
		if (!roots.contains(workingdirectory.getRoot())) {
			throw new InvalidBuildConfigurationException(
					"Working directory root is not present among the configuration roots: " + workingdirectory.getRoot()
							+ " not in " + roots);
		}
	}

	private void addCachedProviderKeys(SakerFileProvider fp) {
		for (; fp != null; fp = fp.getWrappedProvider()) {
			FileProviderKey key = fp.getProviderKey();
			keyProviders.putIfAbsent(key, fp);
		}
	}
}
