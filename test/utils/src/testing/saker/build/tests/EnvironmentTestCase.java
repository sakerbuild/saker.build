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
package testing.saker.build.tests;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.runtime.classpath.ClassPathLocation;
import saker.build.runtime.classpath.ClassPathServiceEnumerator;
import saker.build.runtime.environment.BuildTaskExecutionResult;
import saker.build.runtime.environment.BuildTaskExecutionResultImpl;
import saker.build.runtime.environment.EnvironmentParameters;
import saker.build.runtime.environment.RepositoryManager;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.runtime.execution.ScriptAccessorClassPathCacheKey;
import saker.build.runtime.execution.TargetConfiguration;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration.RepositoryConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.runtime.project.SakerProjectCache;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.SakerRepositoryFactory;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.scripting.TargetConfigurationReadingResult;
import saker.build.task.BuildTargetTaskResult;
import saker.build.task.TaskFactory;
import saker.build.task.TaskResultCollection;
import saker.build.task.cluster.TaskInvoker;
import saker.build.task.exception.InnerTaskExecutionException;
import saker.build.task.exception.MultiTaskExecutionFailedException;
import saker.build.task.exception.TaskException;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.BuildFileTargetTaskIdentifier;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.classloader.SingleClassLoaderResolver;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.ThrowingRunnable;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.ResourceCloser;
import saker.build.thirdparty.saker.util.thread.ExceptionThread;
import saker.build.util.rmi.SakerRMIHelper;
import testing.saker.SakerJavaTestingInvoker;
import testing.saker.SakerTestCase;
import testing.saker.api.TestMetric;
import testing.saker.build.flag.TestFlag;

public abstract class EnvironmentTestCase extends SakerTestCase {
	public static final String TEST_CLUSTER_NAME_ENV_PARAM = "test.cluster.name";
	public static final String DEFAULT_BUILD_FILE_NAME = "saker.build";
	private static final SakerPath DEFAULT_BUILD_FILEPATH = SakerPath.valueOf(DEFAULT_BUILD_FILE_NAME);

	public static final String WORKING_DIRECTORY_ROOT = "wd:";
	public static final String BUILD_DIRECTORY_ROOT = "bd:";

	public static final SakerPath PATH_WORKING_DIRECTORY = SakerPath.valueOf(WORKING_DIRECTORY_ROOT);
	public static final SakerPath PATH_BUILD_DIRECTORY = SakerPath.valueOf(BUILD_DIRECTORY_ROOT);

	private static final String TEST_CLASSLOADER_RESOLVER_ID = "saker.tests.cl";

	private static final ThreadGroup COMMON_THREAD_GROUP = new ThreadGroup("Common thread group");

	private static Map<Path, SakerEnvironmentImpl> commonEnvironments = new HashMap<>();
	static {
		//clear the static state of the class
		SakerJavaTestingInvoker.addCloseable(() -> {
			List<SakerEnvironmentImpl> envs = ImmutableUtils.makeImmutableList(commonEnvironments.values());
			commonEnvironments.clear();
			IOUtils.close(envs);
		});
	}
	private static final ConcurrentHashMap<RepositoryCacheEntry, Object> repositoryCacheLocks = new ConcurrentHashMap<>();
	private static final Map<RepositoryCacheEntry, SakerRepository> cachedRepositories = new ConcurrentHashMap<>();

	protected EnvironmentTestCaseConfiguration testConfiguration;
	protected SakerEnvironmentImpl environment;
	protected ExecutionParametersImpl parameters;
	protected MemoryFileProvider files;
	protected Map<String, LocalDaemonEnvironment> clusterEnvironments;

	protected ResourceCloser testResourceCloser;

	protected SakerProjectCache project;
	protected Map<String, String> testParameters;

	protected Path getBuildDirectory() {
		Path testcontetbasebuilddir = getTestingBaseBuildDirectory();
		if (testcontetbasebuilddir == null) {
			return null;
		}
		return resolveClassNamedDirectory(testcontetbasebuilddir);
	}

	protected Path getWorkingDirectory() {
		Path testcontentbaseworkingdir = getTestingBaseWorkingDirectory();
		if (testcontentbaseworkingdir == null) {
			return null;
		}
		return resolveClassNamedDirectory(testcontentbaseworkingdir);
	}

	private static final Path SAKER_JAR_PATH;
	private static final Path STORAGE_DIRECTORY_PATH;
	private static final Path CLUSTER_STORAGE_PATH;
	private static final Path TEST_CONTENT_BASE_WORKING_DIRECTORY;
	private static final Path TEST_CONTENT_BASE_BUILD_DIRECTORY;
	static {
		Map<String, String> parameters;
		try {
			parameters = SakerJavaTestingInvoker.getTestInvokerParameters();
		} catch (NoClassDefFoundError e) {
			throw new AssertionError("Failed to initialize EnvironmentTestCase class. "
					+ "Failed to retrieve task invoker parameters from SakerJavaTestInvoker class.", e);
		}
		if (parameters == null) {
			throw new AssertionError("Failed to initialize EnvironmentTestCase class. "
					+ "No test invoker parameters retrieved from SakerJavaTestInvoker. "
					+ "Was the test invoker initialized?");
		}
		SAKER_JAR_PATH = getPathTestInvokerParameter(parameters, "SakerJarPath", "saker_test.jar");
		TEST_CONTENT_BASE_BUILD_DIRECTORY = getPathTestInvokerParameter(parameters, "TestsBaseBuildDirectory",
				"test/resources/testbuilds");
		TEST_CONTENT_BASE_WORKING_DIRECTORY = getPathTestInvokerParameter(parameters, "TestsBaseWorkingDirectory",
				"test/resources/testcontents");
		STORAGE_DIRECTORY_PATH = getPathTestInvokerParameter(parameters, "StorageDirectoryPath",
				TEST_CONTENT_BASE_BUILD_DIRECTORY.resolve("test_storage"));
		CLUSTER_STORAGE_PATH = getPathTestInvokerParameter(parameters, "ClusterStoragePath",
				TEST_CONTENT_BASE_BUILD_DIRECTORY.resolve("cluster_test_storage"));
	}

	private static Path getPathTestInvokerParameter(Map<String, String> parameters, String paramname, Path defaultval) {
		if (!parameters.containsKey(paramname)) {
			return defaultval;
		}
		String val = parameters.get(paramname);
		if (val == null) {
			return defaultval;
		}
		try {
			return Paths.get(val).toAbsolutePath();
		} catch (Exception e) {
			throw new AssertionError(
					"Failed to parse test invoker parameter path for name: " + paramname + " with value: " + val, e);
		} catch (Throwable e) {
			e.addSuppressed(new AssertionError("Failed to retrieve test invoker parameter with name: " + paramname));
			throw e;
		}
	}

	private static Path getPathTestInvokerParameter(Map<String, String> parameters, String paramname,
			String defaultval) {
		String val = parameters.getOrDefault(paramname, defaultval);
		try {
			return Paths.get(val).toAbsolutePath();
		} catch (Exception e) {
			throw new AssertionError(
					"Failed to parse test invoker parameter path for name: " + paramname + " with value: " + val, e);
		} catch (Throwable e) {
			e.addSuppressed(new AssertionError("Failed to retrieve test invoker parameter with name: " + paramname));
			throw e;
		}
	}

	public static Path getTestingBaseWorkingDirectory() {
		return TEST_CONTENT_BASE_WORKING_DIRECTORY;
	}

	public static Path getTestingBaseBuildDirectory() {
		return TEST_CONTENT_BASE_BUILD_DIRECTORY;
	}

	public static Path getSakerJarPath() {
		return SAKER_JAR_PATH;
	}

	public static Path getStorageDirectoryPath() {
		return STORAGE_DIRECTORY_PATH;
	}

	protected Path getClusterStorageDirectory(String clustername) {
		return CLUSTER_STORAGE_PATH.resolve(clustername);
	}

	private Path resolveClassNamedDirectory(Path basecontentdir) {
		Path wdir = basecontentdir.resolve(this.getClass().getName()).toAbsolutePath();
		if (Files.isDirectory(wdir)) {
			return wdir;
		}
		wdir = basecontentdir.resolve(this.getClass().getName().replace('.', '/')).toAbsolutePath();
		return wdir;
	}

	@Override
	public final void runTest(Map<String, String> parameters) throws Throwable {
		//set a base metric so these are passed to the clusters as well, 
		//and classpath states are tracked properly
		CollectingTestMetric basemetric = new CollectingTestMetric();
		TestFlag.set(basemetric);
		this.testParameters = parameters;
		ThreadGroup threadgroup = new ThreadGroup("Test group: " + this.getClass().getName());
		ExceptionThread t = new ExceptionThread(threadgroup, (ThrowingRunnable) this::executeRunning,
				"Test thread: " + this.getClass().getName());
		t.start();
		while (true) {
			try {
				t.join();
				break;
			} catch (InterruptedException e) {
				t.interrupt();
			}
		}
		IOUtils.throwExc(t.getException());
	}

	/**
	 * Presents an opportunity to the subclasses to setup the execution parameters. The parameters have already been
	 * loaded with the default values.
	 * 
	 * @param params
	 *            The parameters to configure.
	 */
	protected void setupParameters(ExecutionParametersImpl params) {
	}

	protected boolean isRepositoriesCacheable() {
		return false;
	}

	public void executeRunning() throws Exception {
		for (EnvironmentTestCaseConfiguration testconfig : getTestConfigurations()) {
			System.out.println(" --- Running " + getClass().getName() + " with " + testconfig + " --- ");
			try (ResourceCloser rescloser = new ResourceCloser()) {
				testResourceCloser = rescloser;
				String envstoragedir = testconfig.getEnvironmentStorageDirectory();
				Map<String, String> envuserparameters = testconfig.getEnvironmentUserParameters();
				Path storagedir = Paths.get(envstoragedir == null ? "common" : envstoragedir);
				if (!storagedir.isAbsolute()) {
					storagedir = getStorageDirectoryPath().resolve(storagedir);
				}
				Boolean usecommonenv = testconfig.isUseCommonEnvironment();
				if (usecommonenv == null) {
					usecommonenv = envstoragedir != null && envuserparameters == null;
				}
				SakerEnvironmentImpl env;
				if (usecommonenv) {
					synchronized (commonEnvironments) {
						SakerEnvironmentImpl commonenv = commonEnvironments.get(storagedir);
						if (commonenv == null) {
							ThreadGroup subgroup = new ThreadGroup(COMMON_THREAD_GROUP,
									"Environment group (" + envstoragedir + ")");
							EnvironmentParameters commonparams = createDefaultEnvironmentParametersBuilder()
									.setUserParameters(envuserparameters).setEnvironmentThreadGroupParent(subgroup)
									.setStorageDirectory(storagedir).build();
							System.out
									.println("EnvironmentTestCase.executeRunning() Parameters for common environment: "
											+ envstoragedir + " -> " + commonparams);
							commonenv = new SakerEnvironmentImpl(commonparams);
							SakerJavaTestingInvoker.addCloseable(commonenv);
							commonEnvironments.put(storagedir, commonenv);
						} else {
							if (envuserparameters != null) {
								if (!commonenv.getUserParameters().equals(envuserparameters)) {
									throw new AssertionError(
											"Environment user parameter test case configuration mismatch: "
													+ commonenv.getUserParameters() + " - " + envuserparameters);
								}
							}
						}
						env = commonenv;
					}
				} else {
					env = new SakerEnvironmentImpl(createDefaultEnvironmentParametersBuilder()
							.setUserParameters(envuserparameters)
							.setStorageDirectory(getStorageDirectoryPath().resolve(getClass().getName())).build());
					rescloser.add(env);
				}
				OutputStream actulstdout = System.out;
				OutputStream actulstderr = System.err;
				env.redirectStandardIO();

				SingleClassLoaderResolver testclresolver = new SingleClassLoaderResolver(
						"tests.cl." + getClass().getName(), getClass().getClassLoader());
				try {
					env.getClassLoaderResolverRegistry().register(TEST_CLASSLOADER_RESOLVER_ID, testclresolver);

					ExecutionParametersImpl params = new ExecutionParametersImpl();
//					params.setStandardOutput(outStream);
//					params.setErrorOutput(errStream);
					params.setStandardOutput(ByteSink.valueOf(actulstdout));
					params.setErrorOutput(ByteSink.valueOf(actulstderr));

					params.setBuildDirectory(PATH_BUILD_DIRECTORY);
					params.setMirrorDirectory(getMirrorDirectory());

					params.setBuildTraceOutputPathKey(testconfig.getBuildTraceOutputPathKey());

					Path workingdirpath = getWorkingDirectory();
					Path builddirpath = getBuildDirectory();
					MemoryFileProvider memfiles = createMemoryFileProvider(
							ObjectUtils.newTreeSet(WORKING_DIRECTORY_ROOT, BUILD_DIRECTORY_ROOT),
							UUID.nameUUIDFromBytes(this.getClass().getName().getBytes(StandardCharsets.UTF_8)));
					if (builddirpath != null) {
						memfiles.addDirectoryTo(PATH_BUILD_DIRECTORY, builddirpath);
					}
					if (workingdirpath != null) {
						memfiles.addDirectoryTo(PATH_WORKING_DIRECTORY, workingdirpath);
					}

					ExecutionPathConfiguration.Builder pathsbuilder = ExecutionPathConfiguration
							.builder(PATH_WORKING_DIRECTORY);
					pathsbuilder.addAllRoots(memfiles);
					ExecutionPathConfiguration pathconfig = pathsbuilder.build();

					params.setPathConfiguration(pathconfig);
					setupParameters(params);
					Set<String> configclusternames = testconfig.getClusterNames();
					Map<String, LocalDaemonEnvironment> clusterenvironments = Collections.emptyNavigableMap();
					if (!ObjectUtils.isNullOrEmpty(configclusternames)) {
						Collection<TaskInvoker> taskinvokers = new ArrayList<>();
						ClassLoaderResolverRegistry daemonbaseregistry = RemoteDaemonConnection
								.createConnectionBaseClassLoaderResolver();
						daemonbaseregistry.register(TEST_CLASSLOADER_RESOLVER_ID, testclresolver);

						clusterenvironments = new LinkedHashMap<>();
						for (String clustername : configclusternames) {
							DaemonLaunchParameters.Builder clusterparamsbuilder = DaemonLaunchParameters.builder();
							clusterparamsbuilder.setActsAsCluster(true);
							clusterparamsbuilder.setActsAsServer(false);
							//choose a free port
							clusterparamsbuilder.setPort(0);
							clusterparamsbuilder
									.setStorageDirectory(SakerPath.valueOf(getClusterStorageDirectory(clustername)));
							clusterparamsbuilder.setClusterMirrorDirectory(
									SakerPath.valueOf(getClusterMirrorDirectory(clustername)));
							clusterparamsbuilder.setUserParameters(
									ImmutableUtils.singletonNavigableMap(TEST_CLUSTER_NAME_ENV_PARAM, clustername));
							DaemonLaunchParameters clusterdaemonparams = clusterparamsbuilder.build();

							LocalDaemonEnvironment clusterdaemon = new LocalDaemonEnvironment(getSakerJarPath(),
									clusterdaemonparams) {
								@Override
								protected SakerEnvironmentImpl createSakerEnvironment(EnvironmentParameters params) {
									SakerEnvironmentImpl daemonenv = new SakerEnvironmentImpl(params);
									daemonenv.getClassLoaderResolverRegistry().register(TEST_CLASSLOADER_RESOLVER_ID,
											testclresolver);
									return daemonenv;
								}
							};
							rescloser.add(clusterdaemon);
							clusterdaemon.setServerThreadGroup(COMMON_THREAD_GROUP);
							clusterdaemon.setConnectionBaseRMIOptions(SakerRMIHelper.createBaseRMIOptions()
									.classResolver(new ClassLoaderResolverRegistry(daemonbaseregistry)));
							clusterdaemon.start();
							InetSocketAddress daemonaddr = new InetSocketAddress(InetAddress.getLoopbackAddress(),
									clusterdaemon.getRuntimeLaunchConfiguration().getPort());
							RemoteDaemonConnection daemonconnection = RemoteDaemonConnection.connect(null, daemonaddr,
									SakerRMIHelper.createBaseRMIOptions()
											.classResolver(new ClassLoaderResolverRegistry(daemonbaseregistry)));
							rescloser.add(daemonconnection);

							TaskInvoker daemontaskinvoker = daemonconnection.getClusterTaskInvoker();
							assertNonNull(daemontaskinvoker);
							taskinvokers.add(daemontaskinvoker);

							clusterenvironments.put(clustername, clusterdaemon);
						}
						params.setTaskInvokers(taskinvokers);
					}
					params.defaultize();

					try (SakerProjectCache project = testconfig.isUseProject() ? new SakerProjectCache(env) : null) {
						if (project != null) {
							project.setFileWatchingEnabled(testconfig.isProjectFileWatchingEnabled());
						}
						this.testConfiguration = testconfig;
						this.environment = env;
						this.project = project;
						this.parameters = params;
						this.files = memfiles;
						this.clusterEnvironments = clusterenvironments;

						this.runTestImpl();
					} finally {
						this.testConfiguration = null;
						this.parameters = null;
						this.environment = null;
						this.project = null;
						this.files = null;
						this.clusterEnvironments = null;
					}
				} finally {
					env.unredirectStandardIO();
					env.getClassLoaderResolverRegistry().unregister(TEST_CLASSLOADER_RESOLVER_ID, testclresolver);
					try {
						env.clearCachedDatasWaitExecutions();
					} catch (InterruptedException e) {
						// reinterrupt
						Thread.currentThread().interrupt();
					} catch (Exception e) {
						//in some case this fails somewhy
						e.printStackTrace();
					}
				}
			} catch (AssertionError e) {
				throw e;
			} catch (Throwable e) {
				throw new AssertionError(e);
			}
		}
	}

	protected MemoryFileProvider createMemoryFileProvider(Set<String> roots, UUID filesuuid) {
		return new MemoryFileProvider(roots, filesuuid);
	}

	protected void clearClusterEnvironmentCachedProperties(String clustername) throws InterruptedException {
		LocalDaemonEnvironment cenv = ObjectUtils.getMapValue(this.clusterEnvironments, clustername);
		if (cenv == null) {
			throw fail("No cluster found for name: " + clustername);
		}
		SakerEnvironmentImpl env = cenv.getSakerEnvironment();
		if (env == null) {
			throw fail("No environment for cluster: " + clustername);
		}
		env.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
	}

	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		Set<EnvironmentTestCaseConfiguration> result = new LinkedHashSet<>();
		EnvironmentTestCaseConfiguration.Builder builder = EnvironmentTestCaseConfiguration.builder();

		result.add(builder.build());

		builder.setUseProject(true);
		result.add(builder.build());
		return result;
	}

	protected SakerPath getMirrorDirectory() {
		return SakerPath.valueOf(getBuildDirectory().resolve("mirror"));
	}

	protected Path getClusterMirrorDirectory(String clustername) {
		return getBuildDirectory().resolve("mirror").resolve(clustername);
	}

	protected static EnvironmentParameters.Builder createDefaultEnvironmentParametersBuilder() {
		return EnvironmentParameters.builder(getSakerJarPath())
				.setStorageDirectory(getStorageDirectoryPath().resolve("common"));
	}

	protected abstract void runTestImpl() throws Throwable;

	private TestMetric metric;

	protected TestMetric createMetric() {
		return null;
	}

	protected void clearMetric() {
		this.metric = null;
	}

	protected TestMetric getMetric() {
		return metric;
	}

	private static TaskResultCollection getTaskResultCollectionThrow(BuildTaskExecutionResult res) throws Throwable {
		Throwable exc = res.getException();
		if (exc != null) {
			//to signal the stack trace of the caller
			exc.addSuppressed(new RuntimeException("Caller stacktrace."));
			throw exc;
		}
		return res.getTaskResultCollection();
	}

	protected BuildTaskExecutionResult runTask(Supplier<? extends BuildTaskExecutionResult> runner) throws Throwable {
		TestMetric createMetric = createMetric();
		this.metric = createMetric;
		TestMetric prevmetric = TestFlag.metric();
		TestFlag.set(createMetric);
		try {
			BuildTaskExecutionResult res = runner.get();
			return res;
		} finally {
			TestFlag.set(prevmetric);
		}
	}

	protected TaskResultCollection runTask(TaskIdentifier taskid, TaskFactory<?> taskfactory) throws Throwable {
		return getTaskResultCollectionThrow(runTask(() -> runOnEnvironmentImpl(taskid, taskfactory)));
	}

	protected TaskResultCollection runTask(String taskid, TaskFactory<?> taskfactory) throws Throwable {
		return runTask(strTaskId(taskid), taskfactory);
	}

	protected TargetConfiguration parseTestTargetConfiguration(SakerPath scriptfilepath)
			throws IOException, ScriptParsingFailedException {
		return parseTestTargetConfigurationReadingResult(scriptfilepath).getTargetConfiguration();
	}

	protected TargetConfigurationReadingResult parseTestTargetConfigurationReadingResult(SakerPath scriptfilepath)
			throws IOException, ScriptParsingFailedException {
		scriptfilepath = PATH_WORKING_DIRECTORY.tryResolve(scriptfilepath);

		ExecutionScriptConfiguration scriptconfig = parameters.getScriptConfiguration();
		ScriptParsingOptions parseoptions = scriptconfig.getScriptParsingOptions(scriptfilepath);

		ProviderHolderPathKey pathkey = parameters.getPathConfiguration().getPathKey(scriptfilepath);
//		ScriptAccessProvider scriptaccessor = scriptconfig.getScriptAccessorProviderForPath(scriptfilepath);
		ScriptAccessProvider scriptaccessor;
		try {
			//TODO take script configuration into account
			scriptaccessor = environment
					.getCachedData(new ScriptAccessorClassPathCacheKey(ScriptProviderLocation.getBuiltin(),
							environment.getClassPathManager()))
					.getScriptAccessor();
//			scriptaccessor = ExecutionScriptConfiguration.getScriptAccessorProvider(environment, ScriptProviderLocation.getBuiltin());
		} catch (Exception e) {
			throw new ScriptParsingFailedException(e, Collections.emptySet());
		}
		try (ByteSource input = pathkey.getFileProvider().openInput(pathkey.getPath())) {
			return scriptaccessor.createConfigurationReader().readConfiguration(parseoptions, input);
		}
	}

	protected final TaskResultCollection runScriptTask(SakerPath buildfilepath) throws Throwable {
		if (buildfilepath == null) {
			buildfilepath = DEFAULT_BUILD_FILEPATH;
		}
		final SakerPath fbuildfilepath = buildfilepath;
		TaskResultCollection results = getTaskResultCollectionThrow(
				runTask(() -> runOnEnvironmentImpl(fbuildfilepath, null)));
		return results;
	}

	protected final CombinedTargetTaskResult runScriptTask(String targetname) throws Throwable {
		SakerPath buildfilepath = DEFAULT_BUILD_FILEPATH;
		return runScriptTask(targetname, buildfilepath);
	}

	protected CombinedTargetTaskResult runScriptTask(String targetname, SakerPath buildfilepath) throws Throwable {
		Objects.requireNonNull(targetname, "target name");
		Objects.requireNonNull(buildfilepath, "build file path");

		final SakerPath fbuildfilepath = parameters.getPathConfiguration().getWorkingDirectory()
				.tryResolve(buildfilepath);

		BuildTaskExecutionResult taskrunresult = runTask(() -> runOnEnvironmentImpl(fbuildfilepath, targetname));
		TaskResultCollection results = getTaskResultCollectionThrow(taskrunresult);
		TaskIdentifier taskid = new BuildFileTargetTaskIdentifier(targetname, fbuildfilepath);
		return new CombinedTargetTaskResult(taskrunresult, results,
				(BuildTargetTaskResult) results.getTaskResult(taskid));
	}

	protected static synchronized void addCloseable(AutoCloseable c) {
		SakerJavaTestingInvoker.addCloseable(c);
	}

	private BuildTaskExecutionResult runOnEnvironmentImpl(final SakerPath fbuildfilepath, String targetname) {
		try {
			cacheRepositories();
		} catch (IOException e) {
			return BuildTaskExecutionResultImpl.createInitializationFailed(e);
		}
		return environment.run(fbuildfilepath, targetname, parameters, project);
	}

	private void cacheRepositories() throws IOException {
		if (!isRepositoriesCacheable()) {
			return;
		}
		RepositoryManager repomanager = environment.getRepositoryManager();
		for (RepositoryConfig repoconfig : parameters.getRepositoryConfiguration().getRepositories()) {
			ClassPathLocation location = repoconfig.getClassPathLocation();
			ClassPathServiceEnumerator<? extends SakerRepositoryFactory> serviceenumerator = repoconfig
					.getRepositoryFactoryEnumerator();
			RepositoryCacheEntry cacheentry = new RepositoryCacheEntry(repomanager, location, serviceenumerator);
			synchronized (repositoryCacheLocks.computeIfAbsent(cacheentry, Functionals.objectComputer())) {
				if (!cachedRepositories.containsKey(cacheentry)) {
					SakerRepository repo = repomanager.loadRepository(location, serviceenumerator);
					SakerJavaTestingInvoker.addCloseable(() -> {
						synchronized (repositoryCacheLocks.computeIfAbsent(cacheentry, Functionals.objectComputer())) {
							cachedRepositories.remove(cacheentry, repo);
							repo.close();
						}
					});
					cachedRepositories.put(cacheentry, repo);
				}
			}
		}
	}

	private BuildTaskExecutionResult runOnEnvironmentImpl(TaskIdentifier taskid, TaskFactory<?> taskfactory) {
		try {
			cacheRepositories();
		} catch (IOException e) {
			return BuildTaskExecutionResultImpl.createInitializationFailed(e);
		}
		return environment.run(taskid, taskfactory, parameters, project);
	}

	public static class CombinedTargetTaskResult {
		public final BuildTaskExecutionResult executionResult;
		public final TaskResultCollection resultCollection;
		public final BuildTargetTaskResult targetResult;

		public CombinedTargetTaskResult(BuildTaskExecutionResult executionResult, TaskResultCollection resultCollection,
				BuildTargetTaskResult targetResult) {
			this.executionResult = executionResult;
			this.resultCollection = resultCollection;
			this.targetResult = targetResult;
		}

		public Object getTargetTaskResult(String name) {
			TaskIdentifier restaskid = targetResult.getTaskResultIdentifiers().get(name);
			if (restaskid == null) {
				throw new IllegalArgumentException("Task result with name not found: " + name);
			}
			return StructuredTaskResult.getActualTaskResult(restaskid, resultCollection);
		}
	}

	protected final void assertDirectoryRecursiveContents(SakerPath directory, Collection<String> filepaths)
			throws AssertionError {
		assertEquals(files.getDirectoryEntryNamesRecursive(directory), filepaths);
	}

	protected final void assertDirectoryRecursiveContents(String directory, Collection<String> filepaths)
			throws AssertionError {
		assertDirectoryRecursiveContents(SakerPath.valueOf(directory), filepaths);
	}

	protected final void assertDirectoryRecursiveContents(String directory, String... filepaths) throws AssertionError {
		assertDirectoryRecursiveContents(SakerPath.valueOf(directory), ObjectUtils.newTreeSet(filepaths));
	}

	protected final void assertDirectoryRecursiveContents(SakerPath directory, String... filepaths)
			throws AssertionError {
		assertDirectoryRecursiveContents(directory, ObjectUtils.newTreeSet(filepaths));
	}

	protected final void assertDirectoryRecursiveContents(String directory) throws AssertionError {
		assertEmptyDirectory(directory);
	}

	protected final void assertDirectoryRecursiveContents(SakerPath directory) throws AssertionError {
		assertEmptyDirectory(directory);
	}

	protected final void assertEmptyDirectory(String directory) throws AssertionError {
		assertDirectoryRecursiveContents(SakerPath.valueOf(directory), Collections.emptySet());
	}

	protected final void assertEmptyDirectory(SakerPath directory) throws AssertionError {
		assertDirectoryRecursiveContents(directory, Collections.emptySet());
	}

	protected final void assertFileContents(SakerPath filepath, String content) throws AssertionError {
		try {
			ByteArrayRegion bytes = files.getAllBytes(filepath);
			assertEquals(bytes.copy(), content.getBytes(StandardCharsets.UTF_8),
					"File contents doesnt equal got: \n" + bytes.toString() + "\nexpected:\n" + content);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	public static TaskIdentifier strTaskId(String taskid) {
		return new StringTaskIdentifier(taskid);
	}

	public static Set<? extends TaskIdentifier> strTaskIdSetOf(String... taskids) {
		HashSet<TaskIdentifier> result = new HashSet<>();
		for (String tid : taskids) {
			result.add(strTaskId(tid));
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public static <T extends Throwable> T assertTaskException(Class<T> exceptionclass, ExceptionAssertion runner)
			throws AssertionError {
		Throwable initialtaskexc = assertException(TaskException.class, runner);
		Set<Throwable> tested = ObjectUtils.newIdentityHashSet();

		for (Throwable taskexc = initialtaskexc; taskexc != null;) {
			if (!tested.add(taskexc)) {
				break;
			}
			if (exceptionclass.isInstance(taskexc)) {
				return (T) taskexc;
			}

			if (taskexc instanceof MultiTaskExecutionFailedException) {
				MultiTaskExecutionFailedException multiexc = (MultiTaskExecutionFailedException) taskexc;
				for (TaskException mexc : multiexc.getTaskExceptions().values()) {
					Throwable t = mexc;
					while (t != null) {
						if (!tested.add(t)) {
							break;
						}
						if (exceptionclass.isInstance(t)) {
							return (T) t;
						}
						if (t instanceof TaskExecutionFailedException || t instanceof InnerTaskExecutionException) {
							t = t.getCause();
						}
					}
				}
				continue;
			}
			taskexc = taskexc.getCause();
		}
		throw new AssertionError("Failed to catch exception: " + exceptionclass.getName() + " caught: "
				+ initialtaskexc.getClass().getName(), initialtaskexc);
	}

	public static Throwable assertTaskException(String exceptionclassname, ExceptionAssertion runner)
			throws AssertionError {
		TaskException initialtaskexc = assertException(TaskException.class, runner);

		Set<Throwable> tested = ObjectUtils.newIdentityHashSet();
		for (Throwable taskexc = initialtaskexc; taskexc != null;) {
			if (!tested.add(taskexc)) {
				break;
			}
			if (hasSuperTypeName(taskexc.getClass(), exceptionclassname)) {
				return taskexc;
			}

			if (taskexc instanceof MultiTaskExecutionFailedException) {
				MultiTaskExecutionFailedException multiexc = (MultiTaskExecutionFailedException) taskexc;
				for (TaskException mexc : multiexc.getTaskExceptions().values()) {
					Throwable t = mexc;
					while (t != null) {
						if (!tested.add(t)) {
							break;
						}

						if (hasSuperTypeName(t.getClass(), exceptionclassname)) {
							return t;
						}
						if (t instanceof TaskExecutionFailedException || t instanceof InnerTaskExecutionException) {
							t = t.getCause();
						}
					}
				}
				continue;
			}
			taskexc = taskexc.getCause();
		}

		throw new AssertionError(
				"Failed to catch exception: " + exceptionclassname + " caught: " + initialtaskexc.getClass().getName(),
				initialtaskexc);
	}

	private static boolean hasSuperTypeName(Class<?> clazz, String cname) {
		if (clazz == null) {
			return false;
		}
		if (clazz.getName().equals(cname)) {
			return true;
		}
		if (hasSuperTypeName(clazz.getSuperclass(), cname)) {
			return true;
		}
		for (Class<?> itf : clazz.getInterfaces()) {
			if (hasSuperTypeName(itf, cname)) {
				return true;
			}
		}
		return false;
	}

	private static class RepositoryCacheEntry {
		private final RepositoryManager manager;
		private final ClassPathLocation location;
		private final ClassPathServiceEnumerator<?> serviceEnumerator;

		public RepositoryCacheEntry(RepositoryManager manager, ClassPathLocation location,
				ClassPathServiceEnumerator<?> serviceEnumerator) {
			this.manager = manager;
			this.location = location;
			this.serviceEnumerator = serviceEnumerator;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((location == null) ? 0 : location.hashCode());
			result = prime * result + ((manager == null) ? 0 : manager.hashCode());
			result = prime * result + ((serviceEnumerator == null) ? 0 : serviceEnumerator.hashCode());
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
			RepositoryCacheEntry other = (RepositoryCacheEntry) obj;
			if (location == null) {
				if (other.location != null)
					return false;
			} else if (!location.equals(other.location))
				return false;
			if (manager == null) {
				if (other.manager != null)
					return false;
			} else if (!manager.equals(other.manager))
				return false;
			if (serviceEnumerator == null) {
				if (other.serviceEnumerator != null)
					return false;
			} else if (!serviceEnumerator.equals(other.serviceEnumerator))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "RepositoryCacheEntry[" + (manager != null ? "manager=" + manager + ", " : "")
					+ (location != null ? "location=" + location + ", " : "")
					+ (serviceEnumerator != null ? "serviceEnumerator=" + serviceEnumerator : "") + "]";
		}
	}

}
