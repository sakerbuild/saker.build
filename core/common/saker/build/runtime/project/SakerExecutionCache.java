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

import saker.build.file.provider.RootFileProviderKey;
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
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ConcurrentPrependEntryAccumulator;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.classloader.SingleClassLoaderResolver;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.ResourceCloser;
import saker.build.util.cache.CacheKey;

public class SakerExecutionCache implements Closeable {
	private final SakerEnvironmentImpl environment;
	private final SakerEnvironment recordingEnvironment;

	private RootFileProviderKey currentCoordinatorProviderKey;
	private ExecutionPathConfiguration currentPathConfiguration;
	private ExecutionPathConfiguration[] currentPathConfigurationReference;
	private ExecutionRepositoryConfiguration currentRepositoryConfiguration;
	private ExecutionScriptConfiguration currentScriptConfiguration;
	private Map<String, String> currentUserParameters;

	private Map<ExecutionScriptConfiguration.ScriptProviderLocation, ScriptAccessorClassPathData> loadedScriptProviderLocators = Collections
			.emptyMap();
	private Map<String, ? extends SakerRepository> loadedRepositories = Collections.emptyNavigableMap();
	private Map<String, BuildRepository> loadedBuildRepositories = Collections.emptyNavigableMap();

	private ClassLoaderResolverRegistry executionClassLoaderRegistry;
	private final ConcurrentPrependAccumulator<ClassLoaderResolver> trackedClassLoaderResolvers = new ConcurrentPrependAccumulator<>();

	private Set<EnvironmentProperty<?>> queriedEnvironmentProperties = ConcurrentHashMap.newKeySet();
	private Set<CacheKey<?, ?>> queriedCacheKeys = ConcurrentHashMap.newKeySet();

	public SakerExecutionCache(SakerEnvironmentImpl environment) {
		Objects.requireNonNull(environment, "environment");
		this.environment = environment;
		this.recordingEnvironment = new CacheRecordingForwardingSakerEnvironment(environment, this);
	}

	public synchronized SakerEnvironment getRecordingEnvironment() {
		return recordingEnvironment;
	}

	public synchronized SakerEnvironmentImpl getEnvironment() {
		return environment;
	}

	public synchronized void recordEnvironmentPropertyAccess(EnvironmentProperty<?> environmentproperty) {
		queriedEnvironmentProperties.add(environmentproperty);
	}

	public synchronized void recordCacheKeyAccess(CacheKey<?, ?> key) {
		queriedCacheKeys.add(key);
	}

	//doc: returns true if changed
	public synchronized boolean set(ExecutionPathConfiguration pathconfig,
			ExecutionRepositoryConfiguration repositoryconfig, ExecutionScriptConfiguration scriptconfig,
			Map<String, String> userparameters, RootFileProviderKey coordinatorproviderkey) throws Exception {
		if (userparameters == null) {
			userparameters = Collections.emptyMap();
		}
		if (currentPathConfiguration != null) {
			//we need to set this anyway
			currentPathConfigurationReference[0] = pathconfig;
			//some runtime exception can happen if the configurations still hold reference to RMI objects which have their connections closed
			//they won't equal in this case, consider the configuration changed.
			if (isConfigurationsEqual(pathconfig, repositoryconfig, scriptconfig, userparameters,
					coordinatorproviderkey)) {
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
					} catch (Exception e) {
						//the repository failed to detect changes
						//it is assumed that any further calls will fail, or creating it newly will fail too
						//clear the repositories, so the next build might succeed, but we dont expect it to without user intervention
						IOException clearexc = IOUtils.closeExc(this::clearCurrentConfiguration);
						IOUtils.addExc(e, clearexc);
						throw new RepositoryOperationException(
								"Failed to detect changes in build repository. (" + buildrepo + ")", e);
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
					} catch (Exception e) {
						//the repository failed to handle changes
						//it is assumed that any further calls will fail, or creating it newly will fail too
						//clear the repositories, so the next build might succeed, but we dont expect it to without user intervention
						IOException clearexc = IOUtils.closeExc(this::clearCurrentConfiguration);
						IOUtils.addExc(e, clearexc);
						throw new RepositoryOperationException(
								"Failed to handle changes in build repository. (" + buildrepo + ")", e);
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

		//close any resources which were loaded but are unused due to an exception
		//the resource closer is cleared if the loading is successful
		try (ResourceCloser closer = new ResourceCloser()) {
			Map<String, ? extends SakerRepository> loadedrepositories = loadRepositoriesForConfiguration(
					repositoryconfig, closer);
			Map<String, BuildRepository> loadedbuildrepositories = new TreeMap<>();
			ClassLoaderResolverRegistry executionclregistry = new ClassLoaderResolverRegistry(
					environment.getClassLoaderResolverRegistry());
			Collection<ClassLoaderResolver> trackedclresolvers = new ArrayList<>();

			if (!loadedrepositories.isEmpty()) {
				for (Entry<String, ? extends SakerRepository> entry : loadedrepositories.entrySet()) {
					String repoid = entry.getKey();
					SakerRepository repository = entry.getValue();
					ClassLoaderResolverRegistry reporegistry = new ClassLoaderResolverRegistry();
					RepositoryBuildEnvironment buildenv = new ExecutionCacheRepositoryBuildEnvironment(
							recordingEnvironment, reporegistry, userparameters, npathconfigref, repoid);

					BuildRepository buildrepo;
					try {
						buildrepo = repository.createBuildRepository(buildenv);
					} catch (Exception e) {
						throw new RepositoryOperationException(
								"Failed to create build repository. (" + repository + ")", e);
					}
					closer.add(buildrepo);
					loadedbuildrepositories.put(repoid, buildrepo);

					executionclregistry.register("repo/" + repoid, reporegistry);
					trackedclresolvers.add(reporegistry);
				}
			}

			Set<? extends ScriptProviderLocation> scriptproviderlocators = scriptconfig.getScriptProviderLocations();
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
						executionclregistry.register("scripting/" + cplocationid, scriptclresolver);

						trackedclresolvers.add(scriptclresolver);
					}
				}
			}

			closer.clearWithoutClosing();

			//assign everything at the end, so if any loading exception happens, the class doesn't stay in an inconsistent state
			currentCoordinatorProviderKey = coordinatorproviderkey;
			currentPathConfiguration = pathconfig;
			currentPathConfigurationReference = npathconfigref;
			currentRepositoryConfiguration = repositoryconfig;
			currentScriptConfiguration = scriptconfig;
			currentUserParameters = userparameters;
			executionClassLoaderRegistry = executionclregistry;
			trackedClassLoaderResolvers.addAll(trackedclresolvers);

			loadedRepositories = loadedrepositories;
			loadedBuildRepositories = ImmutableUtils.unmodifiableMap(loadedbuildrepositories);
			loadedScriptProviderLocators = ImmutableUtils.unmodifiableMap(loadedscriptlocators);
			return true;
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

	public synchronized Map<String, ? extends BuildRepository> getLoadedBuildRepositories() {
		return loadedBuildRepositories;
	}

	public synchronized ClassLoaderResolver getExecutionClassLoaderResolver() {
		return executionClassLoaderRegistry;
	}

	public synchronized Map<ExecutionScriptConfiguration.ScriptProviderLocation, ScriptAccessorClassPathData> getLoadedScriptProviderLocators() {
		return loadedScriptProviderLocators;
	}

	public synchronized void clear() throws IOException {
		clearCurrentConfiguration();
	}

	@Override
	public synchronized void close() throws IOException {
		clearCurrentConfiguration();
	}

	private boolean isConfigurationsEqual(ExecutionPathConfiguration pathconfig,
			ExecutionRepositoryConfiguration repositoryconfig, ExecutionScriptConfiguration scriptconfig,
			Map<String, String> userparameters, RootFileProviderKey coordinatorproviderkey) {
		return Objects.equals(currentCoordinatorProviderKey, coordinatorproviderkey)
				&& ObjectUtils.equalsExcCheck(pathconfig, currentPathConfiguration)
				&& ObjectUtils.equalsExcCheck(repositoryconfig, currentRepositoryConfiguration)
				&& ObjectUtils.equalsExcCheck(scriptconfig, currentScriptConfiguration)
				&& ObjectUtils.equalsExcCheck(userparameters, currentUserParameters);
	}

	private void clearCurrentConfiguration() throws IOException {
		currentPathConfiguration = null;
		currentPathConfigurationReference = null;
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
		exc = IOUtils.closeExc(exc, loadedRepositories.values());

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

		loadedBuildRepositories = Collections.emptyNavigableMap();
		loadedRepositories = Collections.emptyNavigableMap();
		loadedScriptProviderLocators = Collections.emptyMap();
		trackedClassLoaderResolvers.clear();
		executionClassLoaderRegistry = null;

		IOUtils.throwExc(exc);
	}

	private void clearCachedDatas() throws InterruptedException {
		//use ::remove lambda to avoid concurrency errors from calling clear() after the invalidation
		environment.invalidateCachedDatasWaitExecutions(queriedCacheKeys::remove);
		environment.invalidateEnvironmentPropertiesWaitExecutions(queriedEnvironmentProperties::remove);
	}

	private static class ExecutionCacheRepositoryBuildEnvironment implements RepositoryBuildEnvironment {
		private final SakerEnvironment sakerEnvironment;
		private final ClassLoaderResolverRegistry classLoaderRegistry;
		private final Map<String, String> userParameters;
		private final String identifier;
		private ExecutionPathConfiguration[] pathConfigurationReference;

		public ExecutionCacheRepositoryBuildEnvironment(SakerEnvironment sakerEnvironment,
				ClassLoaderResolverRegistry classLoaderRegistry, Map<String, String> userParameters,
				ExecutionPathConfiguration[] pathConfigurationReference, String identifier) {
			this.sakerEnvironment = sakerEnvironment;
			this.classLoaderRegistry = classLoaderRegistry;
			this.userParameters = userParameters;
			this.pathConfigurationReference = pathConfigurationReference;
			this.identifier = identifier;
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

	}

}
