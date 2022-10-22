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
package saker.build.runtime.project;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import saker.build.file.provider.FileProviderKey;
import saker.build.file.provider.SakerFileProvider;
import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.runtime.classpath.ClassPathLocation;
import saker.build.runtime.classpath.ClassPathServiceEnumerator;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.RepositoryManager;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ScriptAccessorClassPathCacheKey;
import saker.build.runtime.execution.ScriptAccessorClassPathData;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration.RepositoryConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.runtime.repository.BuildRepository;
import saker.build.runtime.repository.RepositoryBuildEnvironment;
import saker.build.runtime.repository.RepositoryOperationException;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.SakerRepositoryFactory;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ConcurrentPrependEntryAccumulator;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.classloader.SingleClassLoaderResolver;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.ResourceCloser;
import saker.build.util.cache.CacheKey;

public class SakerExecutionCache implements Closeable {
	private final SakerEnvironmentImpl environment;
	private final SakerEnvironment recordingEnvironment;

	private FileProviderKey currentCoordinatorProviderKey;
	private ExecutionPathConfiguration currentPathConfiguration;
	private ExecutionRepositoryConfiguration currentRepositoryConfiguration;
	private ExecutionScriptConfiguration currentScriptConfiguration;
	private Map<String, String> currentUserParameters;
	private boolean currentForCluster;

	private Map<ExecutionScriptConfiguration.ScriptProviderLocation, ScriptAccessorClassPathData> loadedScriptProviderLocators = Collections
			.emptyMap();
	private Map<String, ? extends SakerRepository> loadedRepositories = Collections.emptyNavigableMap();
	private Map<String, BuildRepository> loadedBuildRepositories = Collections.emptyNavigableMap();
	private Map<String, ExecutionCacheRepositoryBuildEnvironmentBase> loadedRepositoryBuildEnvironments = Collections
			.emptyNavigableMap();

	private final ConcurrentPrependAccumulator<ClassLoaderResolver> trackedClassLoaderResolvers = new ConcurrentPrependAccumulator<>();
	private Map<String, ClassLoaderResolver> registeredClassLoaderResolvers = Collections.emptyNavigableMap();

	private Set<EnvironmentProperty<?>> queriedEnvironmentProperties = ConcurrentHashMap.newKeySet();
	private Set<CacheKey<?, ?>> queriedCacheKeys = ConcurrentHashMap.newKeySet();

	public SakerExecutionCache(SakerEnvironmentImpl environment) {
		Objects.requireNonNull(environment, "environment");
		this.environment = environment;
		this.recordingEnvironment = new CacheRecordingForwardingSakerEnvironment(environment, this);
	}

	public SakerEnvironment getRecordingEnvironment() {
		return recordingEnvironment;
	}

	public SakerEnvironmentImpl getEnvironment() {
		return environment;
	}

	public void recordEnvironmentPropertyAccess(EnvironmentProperty<?> environmentproperty) {
		synchronized (SakerExecutionCache.this) {
			queriedEnvironmentProperties.add(environmentproperty);
		}
	}

	public void recordCacheKeyAccess(CacheKey<?, ?> key) {
		synchronized (SakerExecutionCache.this) {
			queriedCacheKeys.add(key);
		}
	}

	//doc: returns true if changed
	public boolean set(ExecutionPathConfiguration pathconfig, ExecutionRepositoryConfiguration repositoryconfig,
			ExecutionScriptConfiguration scriptconfig, Map<String, String> userparameters,
			SakerFileProvider coordinatorfileprovider, ClassLoaderResolverRegistry executionclregistry,
			boolean forcluster, RepositoryBuildSharedObjectProvider sharedObjectProvider)
			throws InterruptedException, IOException, Exception {
		synchronized (SakerExecutionCache.this) {
			FileProviderKey coordinatorproviderkey = coordinatorfileprovider.getProviderKey();
			if (userparameters == null) {
				userparameters = Collections.emptyMap();
			}
			if (currentPathConfiguration != null) {
				//some runtime exception can happen if the configurations still hold reference to RMI objects which have their connections closed
				//they won't equal in this case, consider the configuration changed.
				if (isConfigurationsEqual(pathconfig, repositoryconfig, scriptconfig, userparameters,
						coordinatorproviderkey, forcluster)) {
					registeredClassLoaderResolvers.forEach(executionclregistry::register);
					for (ExecutionCacheRepositoryBuildEnvironmentBase buildenv : loadedRepositoryBuildEnvironments
							.values()) {
						buildenv.resetSharedObjectLookup(sharedObjectProvider);
					}
					//nothing changed
					//XXX parallelize this?
					ConcurrentPrependEntryAccumulator<BuildRepository, Object> changesaccumulator = new ConcurrentPrependEntryAccumulator<>();
					for (Entry<String, ? extends BuildRepository> entry : loadedBuildRepositories.entrySet()) {
						BuildRepository buildrepo = entry.getValue();
						try {
							Object repochanges = buildrepo.detectChanges();
							if (repochanges != null) {
								changesaccumulator.add(buildrepo, repochanges);
							}
						} catch (Exception | LinkageError e) {
							//the repository failed to detect changes
							//it is assumed that any further calls will fail, or creating it newly will fail too
							//clear the repositories, so the next build might succeed, but we dont expect it to without user intervention

							//get the build repo string representation before the closing of it
							String buildrepostr = entry.getKey();
							try {
								buildrepostr += ": " + buildrepo.toString();
							} catch (Exception | LinkageError e2) {
								e.addSuppressed(e2);
							}

							IOException clearexc = IOUtils.closeExc(this::clearCurrentConfiguration);
							IOUtils.addExc(e, clearexc);
							throw new RepositoryOperationException(
									"Failed to detect changes in build repository. (" + buildrepostr + ")", e);
						}
					}
					if (changesaccumulator.isEmpty()) {
						//no changes detected
						return false;
					}
					clearCachedDatas();
					for (Entry<BuildRepository, Object> entry : changesaccumulator) {
						BuildRepository buildrepo = entry.getKey();
						try {
							buildrepo.handleChanges(entry.getValue());
						} catch (Exception | LinkageError e) {
							//the repository failed to handle changes
							//it is assumed that any further calls will fail, or creating it newly will fail too
							//clear the repositories, so the next build might succeed, but we dont expect it to without user intervention

							//get the build repo string representation before the closing of it
							String buildrepostr;
							try {
								buildrepostr = buildrepo.toString();
							} catch (Exception | LinkageError e2) {
								e.addSuppressed(e2);
								buildrepostr = buildrepo.getClass().getName();
							}

							IOException clearexc = IOUtils.closeExc(this::clearCurrentConfiguration);
							IOUtils.addExc(e, clearexc);
							throw new RepositoryOperationException(
									"Failed to handle changes in build repository. (" + buildrepostr + ")", e);
						}
					}
					return true;
				}
				//XXX only clear parts of the configuration if applicable

				// XXX: handle exception? should we fail loading if the closing failed? is printing stack trace sufficent?
				IOUtils.closePrint(this::clearCurrentConfiguration);
			}
			ExecutionPathConfiguration[] npathconfigref = new ExecutionPathConfiguration[] { pathconfig };
			//unmodifiableize
			userparameters = ImmutableUtils.makeImmutableNavigableMap(userparameters);

			TreeMap<String, ClassLoaderResolver> regclresolvers = new TreeMap<>();

			//close any resources which were loaded but are unused due to an exception
			//the resource closer is cleared if the loading is successful
			try (ResourceCloser closer = new ResourceCloser()) {
				Map<String, ? extends SakerRepository> loadedrepositories = loadRepositoriesForConfiguration(
						repositoryconfig, closer);
				Map<String, BuildRepository> loadedbuildrepositories = new TreeMap<>();
				Map<String, ExecutionCacheRepositoryBuildEnvironmentBase> loadedrepositorybuildenvironments = new TreeMap<>();
				Collection<ClassLoaderResolver> trackedclresolvers = new ArrayList<>();

				if (!loadedrepositories.isEmpty()) {
					for (Entry<String, ? extends SakerRepository> entry : loadedrepositories.entrySet()) {
						String repoid = entry.getKey();
						SakerRepository repository = entry.getValue();
						ClassLoaderResolverRegistry reporegistry = new ClassLoaderResolverRegistry();

						//register the cl resolver before initializing the repository 
						// so RMI requests can succeed during initialization
						String clresid = "repo/" + repoid;
						regclresolvers.put(clresid, reporegistry);
						executionclregistry.register(clresid, reporegistry);

						ExecutionCacheRepositoryBuildEnvironmentBase buildenv;
						if (forcluster) {
							Objects.requireNonNull(sharedObjectProvider, "shared object provider");
							buildenv = new ClusterExecutionCacheRepositoryBuildEnvironment(recordingEnvironment,
									reporegistry, userparameters, npathconfigref, repoid, coordinatorfileprovider,
									sharedObjectProvider);
						} else {
							buildenv = new ExecutionCacheRepositoryBuildEnvironment(recordingEnvironment, reporegistry,
									userparameters, npathconfigref, repoid, coordinatorfileprovider);
						}

						BuildRepository buildrepo;
						try {
							buildrepo = repository.createBuildRepository(buildenv);
						} catch (Exception e) {
							throw new RepositoryOperationException(
									"Failed to create build repository. (" + repository + ")", e);
						}
						closer.add(buildrepo);
						loadedbuildrepositories.put(repoid, buildrepo);
						loadedrepositorybuildenvironments.put(repoid, buildenv);

						trackedclresolvers.add(reporegistry);
					}
				}

				Set<? extends ScriptProviderLocation> scriptproviderlocators = scriptconfig
						.getScriptProviderLocations();
				Map<ScriptProviderLocation, ScriptAccessorClassPathData> loadedscriptlocators = new HashMap<>();
				if (!scriptproviderlocators.isEmpty()) {
					ClassPathLoadManager classpathmanager = environment.getClassPathManager();
					for (ScriptProviderLocation scriptproviderlocator : scriptproviderlocators) {
						ScriptAccessorClassPathData classpathdata = environment.getCachedData(
								new ScriptAccessorClassPathCacheKey(scriptproviderlocator, classpathmanager));
						loadedscriptlocators.put(scriptproviderlocator, classpathdata);
						ClassLoader scriptcl = classpathdata.getClassLoader();
						if (scriptcl != null) {
							ScriptAccessProvider scriptaccessor = classpathdata.getScriptAccessor();
							SingleClassLoaderResolver scriptclresolver = new SingleClassLoaderResolver(
									scriptaccessor.getScriptAccessorKey().toString(), scriptcl);

							ClassPathLocation cplocation = scriptproviderlocator.getClassPathLocation();
							String cplocationid = cplocation == null ? "no-cp-location" : cplocation.getIdentifier();
							String clresid = "scripting/" + cplocationid;
							regclresolvers.put(clresid, scriptclresolver);
							executionclregistry.register(clresid, scriptclresolver);

							trackedclresolvers.add(scriptclresolver);
						}
					}
				}

				closer.clearWithoutClosing();

				//assign everything at the end, so if any loading exception happens, the class doesn't stay in an inconsistent state
				currentCoordinatorProviderKey = coordinatorproviderkey;
				currentPathConfiguration = pathconfig;
				currentRepositoryConfiguration = repositoryconfig;
				currentScriptConfiguration = scriptconfig;
				currentUserParameters = userparameters;
				currentForCluster = forcluster;
				registeredClassLoaderResolvers = regclresolvers;
				trackedClassLoaderResolvers.addAll(trackedclresolvers);

				loadedRepositories = loadedrepositories;
				loadedBuildRepositories = ImmutableUtils.unmodifiableMap(loadedbuildrepositories);
				loadedRepositoryBuildEnvironments = ImmutableUtils.unmodifiableMap(loadedrepositorybuildenvironments);
				loadedScriptProviderLocators = ImmutableUtils.unmodifiableMap(loadedscriptlocators);
				return true;
			}
		}
	}

	private Map<String, ? extends SakerRepository> loadRepositoriesForConfiguration(
			ExecutionRepositoryConfiguration repositoryconfig, ResourceCloser closer) throws IOException {
		RepositoryManager repositorymanager = environment.getRepositoryManager();
		Collection<? extends RepositoryConfig> repos;
		if (repositoryconfig == null) {
			//no repositories
			return Collections.emptyNavigableMap();
		}
		repos = repositoryconfig.getRepositories();
		if (ObjectUtils.isNullOrEmpty(repos)) {
			//no repositories
			return Collections.emptyNavigableMap();
		}
		//XXX we could parallelize the classpath loading
		Map<String, SakerRepository> result = new TreeMap<>();
		try {
			for (RepositoryConfig repoconfig : repos) {
				String repoid = repoconfig.getRepositoryIdentifier();
				ClassPathLocation repolocation = repoconfig.getClassPathLocation();
				ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator = repoconfig
						.getRepositoryFactoryEnumerator();
				SakerRepository repo = loadRepositoryFromManager(repositorymanager, repolocation, enumerator);
				if (repo == null) {
					//the subclass decided to handle the exception, and returns null as this repository shouldn't be used
					continue;
				}
				closer.add(repo);
				SakerRepository prev = result.putIfAbsent(repoid, repo);
				if (prev != null) {
					throw new AssertionError("Multiple repositories found for identifier. "
							+ "This should not happen, as ExecutionRepositoryConfiguration should already handle this case.");
				}
			}
		} catch (Exception e) {
			//if an exception is thrown, close the previously loaded repositories
			IOException cexc = IOUtils.closeExc(result.values());
			IOUtils.addExc(e, cexc);
			throw e;
		}
		return result;
	}

	//can be overridden to handle the exception of loading the classpath
	//subclasses can return null to signal that the repository load failure should be ignored
	protected SakerRepository loadRepositoryFromManager(RepositoryManager repositorymanager,
			ClassPathLocation repolocation, ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator)
			throws IOException {
		return repositorymanager.loadRepository(repolocation, enumerator);
	}

	public Map<String, ? extends BuildRepository> getLoadedBuildRepositories() {
		synchronized (SakerExecutionCache.this) {
			return loadedBuildRepositories;
		}
	}

	public Map<String, ? extends RepositoryBuildEnvironment> getLoadedRepositoryBuildEnvironments() {
		synchronized (SakerExecutionCache.this) {
			return loadedRepositoryBuildEnvironments;
		}
	}

	public Map<ExecutionScriptConfiguration.ScriptProviderLocation, ScriptAccessorClassPathData> getLoadedScriptProviderLocators() {
		synchronized (SakerExecutionCache.this) {
			return loadedScriptProviderLocators;
		}
	}

	public void clear() throws IOException {
		synchronized (SakerExecutionCache.this) {
			clearCurrentConfiguration();
		}
	}

	@Override
	public void close() throws IOException {
		synchronized (SakerExecutionCache.this) {
			clearCurrentConfiguration();
		}
	}

	public interface RepositoryBuildSharedObjectProvider {
		public RepositoryBuildSharedObjectLookup getLookup(String repositoryid);
	}

	public interface RepositoryBuildSharedObjectLookup {
		@RMISerialize
		public Object getSharedObject(@RMISerialize Object key);
	}

	private boolean isConfigurationsEqual(ExecutionPathConfiguration pathconfig,
			ExecutionRepositoryConfiguration repositoryconfig, ExecutionScriptConfiguration scriptconfig,
			Map<String, String> userparameters, FileProviderKey coordinatorproviderkey, boolean forcluster) {
		return currentForCluster == forcluster && Objects.equals(currentCoordinatorProviderKey, coordinatorproviderkey)
				&& ObjectUtils.equalsExcCheck(pathconfig, currentPathConfiguration)
				&& ObjectUtils.equalsExcCheck(repositoryconfig, currentRepositoryConfiguration)
				&& ObjectUtils.equalsExcCheck(scriptconfig, currentScriptConfiguration)
				&& ObjectUtils.equalsExcCheck(userparameters, currentUserParameters);
	}

	private void clearCurrentConfiguration() throws IOException {
		currentPathConfiguration = null;
		currentRepositoryConfiguration = null;
		currentScriptConfiguration = null;
		currentUserParameters = null;

		try {
			clearCachedDatas();
		} catch (InterruptedException e) {
			//reinterrupt
			Thread.currentThread().interrupt();
		}

		IOException exc = null;
		exc = IOUtils.closeExc(exc, loadedBuildRepositories.values());
		loadedBuildRepositories = Collections.emptyNavigableMap();
		loadedRepositoryBuildEnvironments = Collections.emptyNavigableMap();

		exc = IOUtils.closeExc(exc, loadedRepositories.values());
		loadedRepositories = Collections.emptyNavigableMap();

		registeredClassLoaderResolvers = Collections.emptyNavigableMap();

		//clear the cached datas again, as there is a race condition between clearing, and closing the build repositories
		//they still might add cached datas to the environment asynchronously, or in the closing method (though they are discouraged to do so)
		try {
			clearCachedDatas();
		} catch (InterruptedException e) {
			//reinterrupt
			Thread.currentThread().interrupt();
		} catch (LinkageError e) {
			//something happened probably due to the classpaths were unloaded. ignoreable, but print the stack trace to leave trace of it.
			e.printStackTrace();
		}

		loadedScriptProviderLocators = Collections.emptyMap();
		trackedClassLoaderResolvers.clear();

		IOUtils.throwExc(exc);
	}

	private void clearCachedDatas() throws InterruptedException {
		//use ::remove lambda to avoid concurrency errors from calling clear() after the invalidation
		environment.invalidateCachedDatasWaitExecutions(queriedCacheKeys::remove);
		environment.invalidateEnvironmentPropertiesWaitExecutions(queriedEnvironmentProperties::remove);
	}

	private static class ExecutionCacheRepositoryBuildEnvironment extends ExecutionCacheRepositoryBuildEnvironmentBase {
		private final ConcurrentMap<Object, Object> sharedObjects = new ConcurrentHashMap<>();

		public ExecutionCacheRepositoryBuildEnvironment(SakerEnvironment sakerEnvironment,
				ClassLoaderResolverRegistry classLoaderRegistry, Map<String, String> userParameters,
				ExecutionPathConfiguration[] pathConfigurationReference, String identifier,
				SakerFileProvider localFileProvider) {
			super(sakerEnvironment, classLoaderRegistry, userParameters, pathConfigurationReference, identifier,
					localFileProvider);
		}

		@Override
		public void setSharedObject(Object key, Object value)
				throws NullPointerException, UnsupportedOperationException {
			Objects.requireNonNull(key, "key");
			if (value == null) {
				sharedObjects.remove(key);
			} else {
				sharedObjects.put(key, value);
			}
		}

		@Override
		public Object getSharedObject(Object key) throws NullPointerException {
			Objects.requireNonNull(key, "key");
			return sharedObjects.get(key);
		}

		@Override
		public boolean isRemoteCluster() {
			return false;
		}
	}

	private static class ClusterExecutionCacheRepositoryBuildEnvironment
			extends ExecutionCacheRepositoryBuildEnvironmentBase {
		private Supplier<RepositoryBuildSharedObjectLookup> sharedObjectProvider;

		public ClusterExecutionCacheRepositoryBuildEnvironment(SakerEnvironment sakerEnvironment,
				ClassLoaderResolverRegistry classLoaderRegistry, Map<String, String> userParameters,
				ExecutionPathConfiguration[] pathConfigurationReference, String identifier,
				SakerFileProvider localFileProvider, RepositoryBuildSharedObjectProvider sharedObjectProvider) {
			super(sakerEnvironment, classLoaderRegistry, userParameters, pathConfigurationReference, identifier,
					localFileProvider);
			this.sharedObjectProvider = LazySupplier.of(() -> sharedObjectProvider.getLookup(identifier));
		}

		@Override
		public void setSharedObject(Object key, Object value)
				throws NullPointerException, UnsupportedOperationException {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object getSharedObject(Object key) throws NullPointerException {
			Objects.requireNonNull(key, "key");
			return sharedObjectProvider.get().getSharedObject(key);
		}

		@Override
		public boolean isRemoteCluster() {
			return true;
		}

		@Override
		public void resetSharedObjectLookup(RepositoryBuildSharedObjectProvider sharedObjectProvider) {
			this.sharedObjectProvider = LazySupplier.of(() -> sharedObjectProvider.getLookup(identifier));
		}
	}

	private static abstract class ExecutionCacheRepositoryBuildEnvironmentBase implements RepositoryBuildEnvironment {
		protected final SakerEnvironment sakerEnvironment;
		protected final ClassLoaderResolverRegistry classLoaderRegistry;
		protected final Map<String, String> userParameters;
		protected final String identifier;
		protected ExecutionPathConfiguration[] pathConfigurationReference;
		protected SakerFileProvider localFileProvider;

		public ExecutionCacheRepositoryBuildEnvironmentBase(SakerEnvironment sakerEnvironment,
				ClassLoaderResolverRegistry classLoaderRegistry, Map<String, String> userParameters,
				ExecutionPathConfiguration[] pathConfigurationReference, String identifier,
				SakerFileProvider localFileProvider) {
			this.sakerEnvironment = sakerEnvironment;
			this.classLoaderRegistry = classLoaderRegistry;
			this.userParameters = userParameters;
			this.pathConfigurationReference = pathConfigurationReference;
			this.identifier = identifier;
			this.localFileProvider = localFileProvider;
		}

		public void resetSharedObjectLookup(RepositoryBuildSharedObjectProvider sharedObjectProvider) {
		}

		@Override
		public SakerEnvironment getSakerEnvironment() {
			return sakerEnvironment;
		}

		@Override
		public ClassLoaderResolverRegistry getClassLoaderResolverRegistry() {
			return classLoaderRegistry;
		}

		@Override
		public Map<String, String> getUserParameters() {
			return userParameters;
		}

		@Override
		public ExecutionPathConfiguration getPathConfiguration() {
			return pathConfigurationReference[0];
		}

		@Override
		public String getIdentifier() {
			return identifier;
		}

		@Override
		public SakerFileProvider getLocalFileProvider() {
			return localFileProvider;
		}
	}

}
