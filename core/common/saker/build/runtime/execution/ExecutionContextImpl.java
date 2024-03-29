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
package saker.build.runtime.execution;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

import saker.build.cache.BuildCacheAccessor;
import saker.build.cache.BuildDataCache;
import saker.build.exception.FileMirroringUnavailableException;
import saker.build.exception.MissingConfigurationException;
import saker.build.exception.PropertyComputationFailedException;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.ProviderPathSakerDirectory;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDatabase;
import saker.build.file.content.ContentDatabaseImpl;
import saker.build.file.content.ContentDatabaseImpl.PathProtectionSettings;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SimplePathKey;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.ForwardingSakerEnvironment;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.params.DatabaseConfiguration;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.runtime.params.InvalidBuildConfigurationException;
import saker.build.runtime.project.SakerExecutionCache;
import saker.build.runtime.project.SakerExecutionCache.RepositoryBuildSharedObjectLookup;
import saker.build.runtime.project.SakerProjectCache;
import saker.build.runtime.repository.BuildRepository;
import saker.build.runtime.repository.RepositoryBuildEnvironment;
import saker.build.runtime.repository.RepositoryOperationException;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.scripting.ScriptInformationProvider;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.scripting.ScriptPosition;
import saker.build.scripting.TargetConfigurationReadingResult;
import saker.build.task.BuildTaskResultDatabase;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskExecutionManager;
import saker.build.task.TaskExecutionManager.TaskResultCollectionImpl;
import saker.build.task.TaskExecutionResult;
import saker.build.task.TaskFactory;
import saker.build.task.cluster.TaskInvoker;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ConcurrentAppendAccumulator;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.ref.StrongWeakReference;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeMapSerializeKeyRemoteValueWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeMapWrapper;
import saker.build.thirdparty.saker.util.thread.BooleanLatch;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.trace.InternalBuildTrace;
import saker.build.trace.InternalBuildTrace.NullInternalBuildTrace;
import saker.build.trace.InternalBuildTraceImpl;
import saker.build.util.exc.ExceptionView;
import saker.build.util.property.ScriptParsingConfigurationExecutionProperty;
import testing.saker.build.flag.TestFlag;

@RMIWrap(ExecutionContextImpl.ExecutionContextRMIWrapper.class)
public final class ExecutionContextImpl implements ExecutionContext, InternalExecutionContext, AutoCloseable {
	private final SakerEnvironmentImpl environment;

	private final ExecutionParametersImpl executionParameters;
	private final long buildTimeDateMillis;

	private SakerDirectory workingSakerDirectory;
	private SakerDirectory buildSakerDirectory;
	private SakerPath buildDirectoryPath;

	private final ExecutionScriptConfiguration scriptConfiguration;
	private final ExecutionRepositoryConfiguration repositoryConfiguration;
	private final ExecutionPathConfiguration pathConfiguration;
	private NavigableMap<String, SakerDirectory> rootDirectories;

	private ContentDatabaseImpl contentDatabase;
	private BuildCacheAccessor buildCacheAccessor;

	private SakerEnvironment executionEnvironment;

	private FileMirrorHandler mirrorHandler;

	private SakerProjectCache project;

	private ExecutionProgressMonitor progressMonitor;

	private Map<String, ? extends BuildRepository> loadedBuildRepositories;
	private Map<String, ? extends RepositoryBuildEnvironment> loadedBuildRepositoryEnvironments;

	//a Semaphore acting as an exclusive lock
	//we need a Semaphore for this, as the lock is acquired and released on different threads
	private final Semaphore stdIOLockSemaphore = new Semaphore(1);

	private final ByteSink stdOutSink;
	private final ByteSink stdErrSink;
	private final ByteSource stdInSource;

	private Object environmentExecutionKey;

	private Map<ExecutionProperty<?>, Supplier<?>> checkedExecutionProperties = new ConcurrentHashMap<>();

	private BuildTaskResultDatabase results;
	private TaskResultCollectionImpl resultCollection;
	private final Lock executionLock = ThreadUtils.newExclusiveLock();

	private ConcurrentMap<SakerPath, TargetConfigurationReadingResult> scriptCache = new ConcurrentSkipListMap<>();
	private ConcurrentMap<SakerPath, Lock> scriptLoadLock = new ConcurrentSkipListMap<>();

	private FileContentDataComputeHandler fileComputeDataHandler;

	private Map<ExecutionScriptConfiguration.ScriptProviderLocation, ScriptAccessorClassPathData> loadedScriptProviderLocators;

	private SakerExecutionCache ownedExecutionCache = null;
	private ConcurrentHashMap<TaskIdentifier, ConcurrentAppendAccumulator<ExceptionView>> ignoredExceptionViews = new ConcurrentHashMap<>();
	private ConcurrentAppendAccumulator<ExceptionView> nonTaskIgnoredExceptionViews = new ConcurrentAppendAccumulator<>();

	private SecretInputReader secretInputReader;
	private TaskExecutionManager tasExecutionManager;

	private final InternalBuildTrace buildTrace;

	public ExecutionContextImpl(SakerEnvironmentImpl environment, ExecutionParametersImpl parameters) throws Exception {
		this.environment = environment;
		this.buildTimeDateMillis = System.currentTimeMillis();

		parameters = new ExecutionParametersImpl(parameters);
		parameters.defaultize();

		Collection<? extends TaskInvoker> taskinvokers = parameters.getTaskInvokers();
		if (!ObjectUtils.isNullOrEmpty(taskinvokers)) {
			for (TaskInvoker tif : taskinvokers) {
				if (environment.getEnvironmentIdentifier().equals(tif.getEnvironmentIdentifier())) {
					throw new InvalidBuildConfigurationException(
							"The build environment that is used to execute the build is configured as a cluster too. "
									+ "Hint: Don't connect the build daemon to itself as a build cluster.");
				}
			}
		}

		this.executionParameters = parameters;

		ProviderHolderPathKey buildtraceoutputpath = parameters.getBuildTraceOutputPathKey();
		if (buildtraceoutputpath != null) {
			InternalBuildTraceImpl ibt = new InternalBuildTraceImpl(buildtraceoutputpath);
			if (parameters.isBuildTraceEmbedArtifacts()) {
				ibt.setEmbedArtifacts(true);
			}
			this.buildTrace = ibt;
			this.buildTrace.startBuild(environment, this);
		} else {
			this.buildTrace = NullInternalBuildTrace.INSTANCE;
		}

		ByteSink pstdout = parameters.getStandardOutput();
		ByteSink pstderr = parameters.getErrorOutput();
		ByteSource pstdin = parameters.getStandardInput();
		stdOutSink = pstdout == null ? StreamUtils.nullByteSink() : StreamUtils.lockedByteSink(pstdout);
		stdErrSink = pstderr == null ? StreamUtils.nullByteSink() : StreamUtils.lockedByteSink(pstderr);
		stdInSource = pstdin == null ? StreamUtils.nullByteSource() : StreamUtils.lockedByteSource(pstdin);
		secretInputReader = parameters.getSecretInputReader();

		this.progressMonitor = parameters.getProgressMonitor();

		ExecutionPathConfiguration pathconfiguration = parameters.getPathConfiguration();
		ExecutionScriptConfiguration scriptconfiguration = parameters.getScriptConfiguration();
		if (scriptconfiguration == null) {
			scriptconfiguration = ExecutionScriptConfiguration.getDefault();
		}
		this.pathConfiguration = pathconfiguration;
		this.scriptConfiguration = scriptconfiguration;
		this.repositoryConfiguration = parameters.getRepositoryConfiguration();
	}

	public void initialize(SakerProjectCache project) throws Exception {
		this.buildTrace.initialize();

		final SakerPath workingdirectorysakerpath = pathConfiguration.getWorkingDirectory();
		final SakerPath paramsmirrordirectory = executionParameters.getMirrorDirectory();
		SakerPath builddirectoryabssakerpath = executionParameters.getBuildDirectory();

		if (builddirectoryabssakerpath != null) {
			builddirectoryabssakerpath = workingdirectorysakerpath.tryResolve(builddirectoryabssakerpath);
			if (workingdirectorysakerpath.startsWith(builddirectoryabssakerpath)) {
				throw new InvalidBuildConfigurationException("The working directory is inside the build directory. ("
						+ workingdirectorysakerpath + " is under " + builddirectoryabssakerpath + ")");
			}
		}

		SakerExecutionCache useexecutioncache;
		ClassLoaderResolverRegistry dbclregistry = new ClassLoaderResolverRegistry(
				environment.getClassLoaderResolverRegistry());
		this.project = project;
		if (project != null) {
			project.executionStarting(this.progressMonitor, builddirectoryabssakerpath, executionParameters,
					dbclregistry);
			useexecutioncache = project.getExecutionCache();
		} else {
			ownedExecutionCache = new SakerExecutionCache(environment);
			ownedExecutionCache.set(pathConfiguration, repositoryConfiguration, scriptConfiguration,
					executionParameters.getUserParameters(), LocalFileProvider.getInstance(), dbclregistry, false,
					null);
			useexecutioncache = ownedExecutionCache;
		}
		this.loadedBuildRepositories = useexecutioncache.getLoadedBuildRepositories();
		this.loadedBuildRepositoryEnvironments = useexecutioncache.getLoadedRepositoryBuildEnvironments();
		this.loadedScriptProviderLocators = useexecutioncache.getLoadedScriptProviderLocators();

		this.executionEnvironment = useexecutioncache.getRecordingEnvironment();
		this.executionEnvironment = new ExecutionCachingEnvironmentPropertyForwardingSakerEnvironment(
				this.executionEnvironment);
		if (isRecordsBuildTrace()) {
			this.executionEnvironment = new BuildTraceRecordingForwardingSakerEnvironment(this.executionEnvironment,
					buildTrace);
		}

		Path mirrordirpath = null;
		if (paramsmirrordirectory != null) {
			//throws InvalidPathException if the path is invalid
			mirrordirpath = LocalFileProvider.toRealPath(paramsmirrordirectory);
		}
		Path buildlocalpath = null;
		if (builddirectoryabssakerpath != null) {
			if (mirrordirpath == null) {
				buildlocalpath = pathConfiguration.toLocalPath(builddirectoryabssakerpath);
				if (buildlocalpath != null) {
					mirrordirpath = buildlocalpath.resolve("mirror");
				}
			}
		}
		if (mirrordirpath != null) {
			Path workdirlocalpath = pathConfiguration.toLocalPath(workingdirectorysakerpath);
			if (workdirlocalpath != null) {
				if (workdirlocalpath.startsWith(mirrordirpath)) {
					throw new InvalidBuildConfigurationException(
							"The working directory is inside the mirror directory. (" + workdirlocalpath + " is under "
									+ mirrordirpath + ")");
				}
			}
			if (buildlocalpath != null) {
				if (buildlocalpath.startsWith(mirrordirpath)) {
					throw new InvalidBuildConfigurationException("The build directory is inside the mirror directory. ("
							+ buildlocalpath + " is under " + mirrordirpath + ")");
				}
			}
		}

		FileContentDataComputeHandler filecomputehandler = null;

		if (project != null) {
			this.contentDatabase = project.getExecutionContentDatabase();
			filecomputehandler = project.getCachedFileContentDataComputeHandler();
		} else {
			DatabaseConfiguration dbconfiguration = executionParameters.getDatabaseConfiguration();
			if (builddirectoryabssakerpath == null) {
				this.contentDatabase = new ContentDatabaseImpl(dbconfiguration, pathConfiguration);
			} else {
				this.contentDatabase = new ContentDatabaseImpl(dbconfiguration, pathConfiguration, dbclregistry,
						pathConfiguration
								.getPathKey(builddirectoryabssakerpath.resolve(ContentDatabaseImpl.FILENAME_DATABASE)));
			}
		}
		if (filecomputehandler != null) {
			fileComputeDataHandler = filecomputehandler;
		} else {
			fileComputeDataHandler = new FileContentDataComputeHandler();
		}

		NavigableMap<String, SakerDirectory> rootdirs = new TreeMap<>();

		for (Entry<String, ? extends SakerFileProvider> entry : pathConfiguration.getRootFileProviders().entrySet()) {
			SakerPath rootsakerpath = SakerPath.valueOf(entry.getKey());
			String root = rootsakerpath.getRoot();
			SakerFileProvider fp = entry.getValue();
			SakerDirectory sakerdir = null;
			if (project != null) {
				sakerdir = project.getCachedRootDirectory(rootsakerpath, fp);
			}
			if (sakerdir == null) {
				sakerdir = ProviderPathSakerDirectory.createRoot(this.contentDatabase, fp, rootsakerpath);
			}
			rootdirs.put(SakerPath.normalizeRoot(root), sakerdir);
		}
		this.rootDirectories = ImmutableUtils.unmodifiableNavigableMap(rootdirs);
		this.workingSakerDirectory = getDirectoryCreate(workingdirectorysakerpath);
		if (workingSakerDirectory == null) {
			throw new FileNotFoundException("Working directory not found: " + workingdirectorysakerpath);
		}

		mirrorHandler = new FileMirrorHandler(mirrordirpath, pathConfiguration, this.contentDatabase);

		if (builddirectoryabssakerpath != null) {
			this.buildSakerDirectory = getDirectoryCreate(builddirectoryabssakerpath);
			this.buildDirectoryPath = builddirectoryabssakerpath;
			if (this.buildSakerDirectory == null) {
				throw new FileNotFoundException("Build directory not found: " + builddirectoryabssakerpath);
			}
		} else {
			this.buildSakerDirectory = null;
			this.buildDirectoryPath = null;
		}

		PathProtectionSettings pathprotectionsettings = null;
		Collection<? extends PathKey> writeenableddirs = executionParameters.getProtectionWriteEnabledDirectories();
		if (writeenableddirs != null) {
			Map<RootFileProviderKey, NavigableSet<SakerPath>> fpkeywriteenabledirs = new HashMap<>();
			for (PathKey pathkey : writeenableddirs) {
				fpkeywriteenabledirs.computeIfAbsent(pathkey.getFileProviderKey(), Functionals.treeSetComputer())
						.add(pathkey.getPath());
			}
			if (builddirectoryabssakerpath != null) {
				PathKey pathkey = pathConfiguration.getPathKey(builddirectoryabssakerpath);
				fpkeywriteenabledirs.computeIfAbsent(pathkey.getFileProviderKey(), Functionals.treeSetComputer())
						.add(pathkey.getPath());
			}
			if (mirrordirpath != null) {
				SimplePathKey pathkey = new SimplePathKey(SakerPath.valueOf(mirrordirpath),
						LocalFileProvider.getProviderKeyStatic());
				fpkeywriteenabledirs.computeIfAbsent(pathkey.getFileProviderKey(), Functionals.treeSetComputer())
						.add(pathkey.getPath());
			}
			BuildUserPromptHandler prompter = executionParameters.getUserPrompHandler();
			if (prompter == null) {
				prompter = new StandardIOPromptHandler(this, stdOutSink, stdInSource);
			}
			pathprotectionsettings = new PathProtectionSettings(fpkeywriteenabledirs, prompter);
		}
		this.contentDatabase.setProtectionSettings(pathprotectionsettings);

		this.environmentExecutionKey = environment.getStartExecutionKey();

		this.buildTrace.initializeDone(this);
	}

	@Override
	public long getBuildTimeMillis() {
		return buildTimeDateMillis;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getExecutionPropertyCurrentValue(ExecutionProperty<T> executionproperty) {
		Objects.requireNonNull(executionproperty, "property");

		Map<ExecutionProperty<?>, Supplier<?>> cachemap = checkedExecutionProperties;
		@SuppressWarnings("unchecked")
		Supplier<T> result = (Supplier<T>) cachemap.computeIfAbsent(executionproperty,
				x -> new ComputingPropertyResult<>());
		if (result instanceof ComputingPropertyResult) {
			if (((ComputingPropertyResult<?>) result).startCompute()) {
				//we compute the property value
				//  else some other thread computes the value, we just wait for it in the .get() call
				ComputingPropertyResult<T> computingresult = (ComputingPropertyResult<T>) result;
				try {
					T cval = executionproperty.getCurrentValue(this);
					result = Functionals.valSupplier(cval);
				} catch (Exception e) {
					result = new FailedPropertyResult<>(e);
				} catch (Throwable e) {
					//serious error, throw the exception, but set the result, so it is properly set in the cache
					result = new FailedPropertyResult<>(e);
					throw e;
				} finally {
					//always set the result, so the computing supplier is signalled, and other threads aren't deadlocked
					computingresult.setResult(result);
					//replace in the map, so the synchronization mechanism is avoided in future calls 
					//(they are no longer needed, as the property is calculated)
					cachemap.replace(executionproperty, computingresult, result);
				}
			}
		}

		return result.get();
	}

	public boolean hasAnyExecutionPropertyDifference(Map<? extends ExecutionProperty<?>, ?> testproperties) {
		if (testproperties.isEmpty()) {
			return false;
		}
		for (Entry<? extends ExecutionProperty<?>, ?> entry : testproperties.entrySet()) {
			ExecutionProperty<?> property = entry.getKey();
			try {
				Object currentval = getExecutionPropertyCurrentValue(property);
				Object expectedval = entry.getValue();
				if (!Objects.equals(expectedval, currentval)) {
					return true;
				}
			} catch (RuntimeException e) {
				//if any exceptions is thrown from the computing of the property
				return true;
			}
		}
		return false;
	}

	@Override
	public Map<String, ? extends BuildRepository> getLoadedRepositories() {
		return loadedBuildRepositories;
	}

	@Override
	public RepositoryBuildSharedObjectLookup internalGetSharedObjectProvider(String repoid) {
		RepositoryBuildEnvironment buildenv = loadedBuildRepositoryEnvironments.get(repoid);
		if (TestFlag.ENABLED && buildenv == null) {
			throw new AssertionError("build environment is null for: " + repoid);
		}
		if (buildenv == null) {
			//it shouldn't be null, but check just in case
			return k -> null;
		}
		return buildenv::getSharedObject;
	}

	public SecretInputReader getSecretInputReader() {
		return secretInputReader;
	}

	public ExecutionProgressMonitor getProgressMonitor() {
		return progressMonitor;
	}

	public void executeTask(TaskIdentifier taskid, TaskFactory<?> taskfactory) {
		executionLock.lock();
		try {
			if (results != null) {
				throw new IllegalStateException("Execution context was already used to execute a task.");
			}

			BuildTaskResultDatabase prevresults = contentDatabase.getTaskResultDatabase();
			TaskExecutionManager manager = new TaskExecutionManager(prevresults);
			tasExecutionManager = manager;

			Collection<? extends TaskInvoker> taskinvokers = executionParameters.getTaskInvokers();
			BuildDataCache builddatacache = executionParameters.getBuildDataCache();
			if (builddatacache != null) {
				buildCacheAccessor = new BuildCacheAccessor(builddatacache, contentDatabase.getClassLoaderResolver());
			}

			try {
				this.buildTrace.startExecute();
				try {
					manager.execute(taskfactory, taskid, this, taskinvokers, buildCacheAccessor, buildTrace);
					this.buildTrace.endExecute(true);
				} catch (Throwable e) {
					this.buildTrace.endExecute(false);
					throw e;
				}
			} finally {
				NavigableMap<SakerPath, ScriptInformationProvider> scriptinfoproviders = prevresults == null
						? new TreeMap<>()
						: new TreeMap<>(prevresults.getScriptInformationProviders());
				for (Entry<SakerPath, TargetConfigurationReadingResult> entry : this.scriptCache.entrySet()) {
					ScriptInformationProvider infoprovider = entry.getValue().getInformationProvider();
					if (infoprovider != null) {
						scriptinfoproviders.put(entry.getKey(), infoprovider);
					}
				}
				results = manager.getTaskResults(scriptinfoproviders);
				resultCollection = manager.getResultCollection();
				SakerPath wdir = getWorkingDirectoryPath();
				for (ConcurrentAppendAccumulator<ExceptionView> eviews : ignoredExceptionViews.values()) {
					//print the exceptions as a temporary solution
					for (ExceptionView ev : eviews) {
						SakerLog.printFormatException(ev, wdir);
						//TODO add ignored exceptions to the results, but not to the content database 
					}
				}
				for (ExceptionView ev : nonTaskIgnoredExceptionViews) {
					SakerLog.printFormatException(ev, wdir);
				}
				contentDatabase.setTaskResults(results);
			}
		} finally {
			executionLock.unlock();
		}
	}

	public BuildTaskResultDatabase getResultDatabase() {
		return results;
	}

	public TaskResultCollectionImpl getResultCollection() {
		return resultCollection;
	}

	@Override
	public ExecutionPathConfiguration getPathConfiguration() {
		return pathConfiguration;
	}

	public DatabaseConfiguration getDatabaseConfiguretion() {
		return contentDatabase.getDatabaseConfiguration();
	}

	@Override
	public ExecutionRepositoryConfiguration getRepositoryConfiguration() {
		return repositoryConfiguration;
	}

	@Override
	public ExecutionScriptConfiguration getScriptConfiguration() {
		return scriptConfiguration;
	}

	public SakerFileProvider getRootFileProvider(String root) {
		return pathConfiguration.getRootFileProvider(root);
	}

	public ByteSource getStandardIn() {
		return stdInSource;
	}

	@Override
	public SakerDirectory getExecutionWorkingDirectory() {
		return workingSakerDirectory;
	}

	@Override
	public SakerDirectory getExecutionBuildDirectory() {
		return buildSakerDirectory;
	}

	@Override
	public SakerPath getExecutionBuildDirectoryPath() {
		return buildDirectoryPath;
	}

	@Override
	public SakerPath getExecutionWorkingDirectoryPath() {
		return pathConfiguration.getWorkingDirectory();
	}

	@Override
	public NavigableMap<String, ? extends SakerDirectory> getRootDirectories() {
		return rootDirectories;
	}

	@Override
	public SakerEnvironment getEnvironment() {
		return executionEnvironment;
	}

	public SakerEnvironmentImpl getRealEnvironment() {
		return environment;
	}

	public ByteSink getStdOutSink() {
		return stdOutSink;
	}

	public ByteSink getStdErrSink() {
		return stdErrSink;
	}

	public SakerPath getWorkingDirectoryPath() {
		return pathConfiguration.getWorkingDirectory();
	}

	public SakerPath getBuildDirectoryPath() {
		return buildDirectoryPath;
	}

	public ExecutionParametersImpl getExecutionParameters() {
		return executionParameters;
	}

	@Override
	public Map<String, String> getUserParameters() {
		return executionParameters.getUserParameters();
	}

	@Override
	public boolean isIDEConfigurationRequired() {
		return executionParameters.isIDEConfigurationRequired();
	}

	public Path mirror(SakerFile file, DirectoryVisitPredicate synchpredicate)
			throws IOException, FileMirroringUnavailableException {
		return mirrorHandler.mirror(file, synchpredicate, this);
	}

	public Path mirror(SakerPath filepath, SakerFile file, DirectoryVisitPredicate synchpredicate)
			throws IOException, FileMirroringUnavailableException {
		ContentDescriptor filecontents = file.getContentDescriptor();
		return mirror(filepath, file, synchpredicate, filecontents);
	}

	public Path mirror(SakerPath filepath, SakerFile file, DirectoryVisitPredicate synchpredicate,
			ContentDescriptor filecontents) throws IOException {
		return mirrorHandler.mirror(filepath, file, synchpredicate, filecontents);
	}

	@Override
	public Path toMirrorPath(SakerPath path) throws FileMirroringUnavailableException {
		return mirrorHandler.toMirrorPath(path);
	}

	@Override
	public SakerPath toUnmirrorPath(Path path) {
		return mirrorHandler.toUnmirrorPath(path);
	}

	@Override
	public Path getMirrorDirectory() {
		return mirrorHandler.getMirrorDirectory();
	}

	public ContentDatabase getContentDatabase() {
		return contentDatabase;
	}

	public void invalidate(PathKey pathkey) {
		contentDatabase.invalidate(pathkey);
	}

	public ContentDescriptor invalidateGetContentDescriptor(ProviderHolderPathKey pathkey) {
		return contentDatabase.invalidateGetContentDescriptor(pathkey);
	}

	public void invalidateWithPosixFilePermissions(ProviderHolderPathKey pathkey) throws IOException {
		contentDatabase.invalidateWithPosixFilePermissions(pathkey);
	}

	@Override
	public void close() throws IOException, RepositoryOperationException {
		executionLock.lock();
		try {
			//XXX might paralellize this
			IOException ioexc = null;
			ContentDatabaseImpl contentdb = contentDatabase;
			try {
				if (contentdb != null) {
					contentdb.setProtectionSettings(null);
				}
				Map<TaskIdentifier, TaskExecutionResult<?>> cacheabletasks;
				if (results == null || !executionParameters.isPublishCachedTasks() || buildCacheAccessor == null) {
					cacheabletasks = Collections.emptyMap();
				} else {
					cacheabletasks = results.takeCacheableTaskIdResults();
				}
				if (project != null) {
					this.project.executionFinished(this, pathConfiguration, environmentExecutionKey, buildDirectoryPath,
							fileComputeDataHandler, cacheabletasks, buildCacheAccessor, buildTrace);
				} else {
					if (contentdb != null) {
						try {
							contentdb.handleAbandonedTasksOffline(pathConfiguration, buildDirectoryPath);
						} finally {
							ioexc = IOUtils.closeExc(ioexc, contentdb);
						}
						if (!cacheabletasks.isEmpty()) {
							buildCacheAccessor.publishCachedTasks(cacheabletasks, contentdb, this.pathConfiguration);
						}
					}
				}
			} finally {
				if (this.project == null) {
					try {
						environment.executionFinished(environmentExecutionKey);
					} finally {
						ioexc = IOUtils.closeExc(ioexc, ownedExecutionCache, buildTrace);
					}
				}
			}
			IOUtils.throwExc(ioexc);
		} finally {
			executionLock.unlock();
		}
	}

	public <T> T computeFileContentData(SakerFile file, FileDataComputer<T> computer) throws IOException {
		return fileComputeDataHandler.computeFileContentData(file, computer);
	}

	@Override
	public TargetConfiguration getTargetConfiguration(TaskContext taskcontext, SakerFile file)
			throws IOException, ScriptParsingFailedException {
		Objects.requireNonNull(file, "file");
		Objects.requireNonNull(taskcontext, "taskcontext");
		SakerPath path = SakerPathFiles.requireAbsolutePath(file);
		return getTargetConfiguration(taskcontext, file, path);
	}

	public StandardIOLock acquireStdIOLock() throws InterruptedException {
		stdIOLockSemaphore.acquire();
		return returnLockedStandardIO();
	}

	public StandardIOLock tryAcquireStdIOLock() {
		if (stdIOLockSemaphore.tryAcquire()) {
			return returnLockedStandardIO();
		}
		return null;
	}

	@Override
	public ScriptAccessProvider getLoadedScriptAccessProvider(ScriptProviderLocation location) {
		ScriptAccessorClassPathData data = loadedScriptProviderLocators.get(location);
		if (data == null) {
			return null;
		}
		return data.getScriptAccessor();
	}

	public void reportIgnoredException(TaskIdentifier reportertask, ExceptionView exception) {
		ignoredExceptionViews.computeIfAbsent(reportertask, x -> new ConcurrentAppendAccumulator<>()).add(exception);
		this.buildTrace.ignoredException(reportertask, exception);
	}

	public void reportIgnoredException(ExceptionView exception) {
		nonTaskIgnoredExceptionViews.add(exception);
		this.buildTrace.ignoredException(null, exception);
	}

	public void reportIgnoredException(Throwable exception) {
		reportIgnoredException(ExceptionView.create(exception));
	}

	@Override
	public ContentDescriptor getContentDescriptor(ProviderHolderPathKey pathkey) {
		return contentDatabase.getContentDescriptor(pathkey);
	}

	@Override
	public EnvironmentSelectionResult testEnvironmentSelection(TaskExecutionEnvironmentSelector environmentselector,
			Set<UUID> allowedenvironmentids) throws NullPointerException {
		Objects.requireNonNull(environmentselector, "environment selector");
		return tasExecutionManager.testEnvironmentSelection(environmentselector, allowedenvironmentids);
	}

	@Override
	public FilePathContents internalGetFilePathContents(SakerFile file) {
		return new FilePathContents(file.getSakerPath(), file.getContentDescriptor());
	}

	@Override
	public InternalBuildTrace internalGetBuildTrace() {
		return this.buildTrace;
	}

	@Override
	public boolean isRecordsBuildTrace() {
		return this.buildTrace != NullInternalBuildTrace.INSTANCE;
	}

	private ScriptInformationProvider internalGetScriptInformationProviderForTaskScriptPosition(
			SakerPath buildfilepath) {
		TargetConfigurationReadingResult res = scriptCache.get(buildfilepath);
		if (res != null) {
			return res.getInformationProvider();
		}
		if (contentDatabase != null) {
			BuildTaskResultDatabase prevresults = contentDatabase.getTaskResultDatabase();
			if (prevresults != null) {
				return prevresults.getScriptInformationProviders().get(buildfilepath);
			}
		}
		return null;
	}

	public ScriptPosition internalGetTaskScriptPosition(SakerPath buildfilepath, TaskIdentifier taskid) {
		ScriptInformationProvider infoprovider = internalGetScriptInformationProviderForTaskScriptPosition(
				buildfilepath);
		if (infoprovider == null) {
			return null;
		}
		return infoprovider.getScriptPosition(taskid);
	}

	public interface StandardIOLock extends Closeable {
		@Override
		public void close();
	}

	private TargetConfiguration getTargetConfiguration(TaskContext taskcontext, SakerFile file, SakerPath path)
			throws IOException, ScriptParsingFailedException {
		ScriptParsingConfigurationExecutionProperty.PropertyValue parsingconfig = taskcontext.getTaskUtilities()
				.getReportExecutionDependency(new ScriptParsingConfigurationExecutionProperty(path));
		if (parsingconfig == null) {
			throw new MissingConfigurationException("No script configuration defined for path: " + path);
		}
		ScriptParsingOptions parseoptions = parsingconfig.getParsingOptions();

		TargetConfigurationReadingResult present = scriptCache.get(path);
		if (present != null) {
			return present.getTargetConfiguration();
		}
		Lock lock = scriptLoadLock.computeIfAbsent(path, x -> ThreadUtils.newExclusiveLock());
		IOUtils.lockIO(lock, "Interrupted while acquiring script load lock.");
		try {
			present = scriptCache.get(path);
			if (present != null) {
				return present.getTargetConfiguration();
			}
			ScriptAccessProvider scriptaccessor = parsingconfig.getAccessProvider();

			TargetConfigurationReadingResult readresult;
			try {
				buildTrace.openTargetConfigurationFile(parseoptions, file);
				readresult = computeFileContentData(file,
						new TargetConfigurationReadingFileDataComputer(scriptaccessor, parseoptions));
			} catch (IOException e) {
				Throwable cause = e.getCause();
				if (cause instanceof ScriptParsingFailedException) {
					throw (ScriptParsingFailedException) cause;
				}
				throw e;
			}
			TargetConfigurationReadingResult prev = scriptCache.putIfAbsent(path, readresult);
			if (prev != null) {
				return prev.getTargetConfiguration();
			}
			return readresult.getTargetConfiguration();
		} finally {
			lock.unlock();
		}
	}

	private StandardIOLock returnLockedStandardIO() {
		return new SemaphoreClosingStandardIOLock(stdIOLockSemaphore);
	}

	private static final class SemaphoreClosingStandardIOLock implements StandardIOLock {
		private static final AtomicReferenceFieldUpdater<ExecutionContextImpl.SemaphoreClosingStandardIOLock, Semaphore> ARFU_semaphore = AtomicReferenceFieldUpdater
				.newUpdater(ExecutionContextImpl.SemaphoreClosingStandardIOLock.class, Semaphore.class, "semaphore");

		@SuppressWarnings("unused")
		private volatile Semaphore semaphore;

		private SemaphoreClosingStandardIOLock(Semaphore semaphore) {
			this.semaphore = semaphore;
		}

		@Override
		public void close() {
			Semaphore s = ARFU_semaphore.getAndSet(this, null);
			if (s != null) {
				s.release();
			}
		}
	}

	private SakerDirectory getDirectoryCreate(SakerPath path) {
		String rootname = path.getRoot();
		SakerDirectory rootdir = this.rootDirectories.get(rootname);
		if (rootdir == null) {
			throw new IllegalArgumentException("Root not found: " + rootname + " for path: " + path);
		}
		return getDirectoryCreateRootImpl(rootdir, path);
	}

	private static SakerDirectory getDirectoryCreateRootImpl(SakerDirectory basedir, SakerPath path) {
		for (Iterator<String> it = path.nameIterator(); it.hasNext();) {
			String p = it.next();
			basedir = basedir.getDirectoryCreate(p);
		}
		return basedir;
	}

	protected static final class ExecutionContextRMIWrapper implements RMIWrapper {
		private ExecutionContext context;

		public ExecutionContextRMIWrapper() {
		}

		public ExecutionContextRMIWrapper(ExecutionContext context) {
			this.context = context;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			ExecutionContext context = this.context;
			out.writeRemoteObject(context);
			out.writeRemoteObject(context.getLocalFileProvider());
			out.writeObject(context.getPathConfiguration());
			out.writeObject(context.getRepositoryConfiguration());
			out.writeObject(context.getScriptConfiguration());
			out.writeWrappedObject(context.getUserParameters(), RMITreeMapWrapper.class);
			out.writeWrappedObject(context.getRootDirectories(), RMITreeMapSerializeKeyRemoteValueWrapper.class);
			out.writeObject(((InternalExecutionContext) context).internalGetBuildTrace());

			out.writeObject(context.getExecutionWorkingDirectory());
			out.writeObject(context.getExecutionWorkingDirectoryPath());
			out.writeObject(context.getExecutionBuildDirectory());
			out.writeObject(context.getExecutionBuildDirectoryPath());

			//XXX maybe compress these two booleans into a single smaller integer with flags?
			out.writeBoolean(context.isIDEConfigurationRequired());
			out.writeBoolean(context.isRecordsBuildTrace());
			out.writeLong(context.getBuildTimeMillis());
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			ExecutionContext context = (ExecutionContext) in.readObject();
			SakerFileProvider localfp = (SakerFileProvider) in.readObject();
			ExecutionPathConfiguration pathconfig = (ExecutionPathConfiguration) in.readObject();
			ExecutionRepositoryConfiguration repoconfig = (ExecutionRepositoryConfiguration) in.readObject();
			ExecutionScriptConfiguration scriptconfig = (ExecutionScriptConfiguration) in.readObject();

			@SuppressWarnings("unchecked")
			Map<String, String> userparams = (Map<String, String>) in.readObject();

			@SuppressWarnings("unchecked")
			NavigableMap<String, ? extends SakerDirectory> rootdirs = (NavigableMap<String, ? extends SakerDirectory>) in
					.readObject();

			InternalBuildTrace buildtrace = (InternalBuildTrace) in.readObject();

			SakerDirectory workingdir = (SakerDirectory) in.readObject();
			SakerPath workingdirpath = (SakerPath) in.readObject();
			SakerDirectory builddir = (SakerDirectory) in.readObject();
			SakerPath builddirpath = (SakerPath) in.readObject();

			boolean ideconfigrequired = in.readBoolean();
			boolean recordsbuildtrace = in.readBoolean();
			long buildmillis = in.readLong();

			this.context = new InternalCachingForwardingExecutionContext(context, localfp, pathconfig, repoconfig,
					scriptconfig, userparams, buildtrace, ideconfigrequired, recordsbuildtrace, buildmillis, rootdirs,
					workingdir, workingdirpath, builddir, builddirpath);
		}

		@Override
		public Object resolveWrapped() {
			return context;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}
	}

	private static final class InternalCachingForwardingExecutionContext extends ForwardingExecutionContext
			implements InternalExecutionContext {
		private final SakerFileProvider localFileProvider;
		private final ExecutionPathConfiguration pathConfig;
		private final ExecutionRepositoryConfiguration repoConfig;
		private final ExecutionScriptConfiguration scriptConfig;
		private final Map<String, String> userParams;
		private final InternalBuildTrace buildTrace;
		private final boolean ideConfigRequired;
		private final boolean recordsBuildTrace;
		private final long buildTimeMillis;
		private final NavigableMap<String, ? extends SakerDirectory> rootDirectories;
		private final SakerDirectory workingDir;
		private final SakerPath workingDirPath;
		private final SakerDirectory buildDir;
		private final SakerPath buildDirPath;

		public InternalCachingForwardingExecutionContext(ExecutionContext executonContext, SakerFileProvider localfp,
				ExecutionPathConfiguration pathconfig, ExecutionRepositoryConfiguration repoconfig,
				ExecutionScriptConfiguration scriptconfig, Map<String, String> userparams,
				InternalBuildTrace buildtrace, boolean ideconfigrequired, boolean recordsbuildtrace, long buildmillis,
				NavigableMap<String, ? extends SakerDirectory> rootDirectories, SakerDirectory workingdir,
				SakerPath workingdirpath, SakerDirectory builddir, SakerPath builddirpath) {
			super(executonContext);
			this.localFileProvider = localfp;
			this.pathConfig = pathconfig;
			this.repoConfig = repoconfig;
			this.scriptConfig = scriptconfig;
			this.userParams = userparams;
			this.buildTrace = buildtrace;
			this.ideConfigRequired = ideconfigrequired;
			this.recordsBuildTrace = recordsbuildtrace;
			this.buildTimeMillis = buildmillis;
			this.rootDirectories = rootDirectories;
			this.workingDir = workingdir;
			this.workingDirPath = workingdirpath;
			this.buildDir = builddir;
			this.buildDirPath = builddirpath;
		}

		@Override
		public SakerFileProvider getLocalFileProvider() {
			return localFileProvider;
		}

		@Override
		public ExecutionPathConfiguration getPathConfiguration() {
			return pathConfig;
		}

		@Override
		public ExecutionRepositoryConfiguration getRepositoryConfiguration() {
			return repoConfig;
		}

		@Override
		public ExecutionScriptConfiguration getScriptConfiguration() {
			return scriptConfig;
		}

		@Override
		public Map<String, String> getUserParameters() {
			return userParams;
		}

		@Override
		public FilePathContents internalGetFilePathContents(SakerFile file) {
			return ((InternalExecutionContext) executonContext).internalGetFilePathContents(file);
		}

		@Override
		public InternalBuildTrace internalGetBuildTrace() {
			return buildTrace;
		}

		@Override
		public RepositoryBuildSharedObjectLookup internalGetSharedObjectProvider(String repoid) {
			return ((InternalExecutionContext) executonContext).internalGetSharedObjectProvider(repoid);
		}

		@Override
		public boolean isIDEConfigurationRequired() {
			return ideConfigRequired;
		}

		@Override
		public boolean isRecordsBuildTrace() {
			return recordsBuildTrace;
		}

		@Override
		public long getBuildTimeMillis() {
			return buildTimeMillis;
		}

		@Override
		public NavigableMap<String, ? extends SakerDirectory> getRootDirectories() {
			return rootDirectories;
		}

		@Override
		public SakerDirectory getExecutionWorkingDirectory() {
			return workingDir;
		}

		@Override
		public SakerPath getExecutionWorkingDirectoryPath() {
			return workingDirPath;
		}

		@Override
		public SakerDirectory getExecutionBuildDirectory() {
			return buildDir;
		}

		@Override
		public SakerPath getExecutionBuildDirectoryPath() {
			return buildDirPath;
		}

	}

	private static final class FailedPropertyResult<T> implements Supplier<T> {
		private final Throwable exception;

		public FailedPropertyResult(Throwable exception) {
			this.exception = exception;
		}

		@Override
		public T get() throws PropertyComputationFailedException {
			throw new PropertyComputationFailedException(exception);
		}
	}

	private static final class ComputingPropertyResult<T> implements Supplier<T> {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<ExecutionContextImpl.ComputingPropertyResult, Thread> ARFU_computingThread = AtomicReferenceFieldUpdater
				.newUpdater(ExecutionContextImpl.ComputingPropertyResult.class, Thread.class, "computingThread");

		private final BooleanLatch latch = BooleanLatch.newBooleanLatch();
		private volatile Thread computingThread;
		private Supplier<T> result;

		public void setResult(Supplier<T> result) {
			this.result = result;
			latch.signal();
		}

		protected boolean startCompute() {
			return ARFU_computingThread.compareAndSet(this, null, Thread.currentThread());
		}

		@Override
		public T get() {
			if (Thread.currentThread() == computingThread) {
				//check reentrancy so we don't deadlock the computing thread in case of improper implementations
				throw new IllegalThreadStateException("Reentrant attempt for computing property.");
			}
			latch.awaitUninterruptibly();
			Supplier<T> res = this.result;
			return res.get();
		}
	}

	/**
	 * Caches the {@link EnvironmentProperty} values on an execution level.
	 * <p>
	 * This is necessary so the environment properties always have the same value during a single execution. (Even if
	 * they might get recalculated)
	 */
	private static class ExecutionCachingEnvironmentPropertyForwardingSakerEnvironment
			extends ForwardingSakerEnvironment {
		private final Map<EnvironmentProperty<?>, Supplier<?>> checkedEnvironmentProperties = new ConcurrentHashMap<>();

		public ExecutionCachingEnvironmentPropertyForwardingSakerEnvironment(SakerEnvironment environment) {
			super(environment);
		}

		@Override
		public <T> T internalGetEnvironmentPropertyCurrentValue(SakerEnvironment environment,
				EnvironmentProperty<T> environmentproperty, InternalBuildTrace btrace) {
			Map<EnvironmentProperty<?>, Supplier<?>> cachemap = checkedEnvironmentProperties;
			@SuppressWarnings("unchecked")
			Supplier<T> result = (Supplier<T>) cachemap.computeIfAbsent(environmentproperty,
					x -> new ComputingPropertyResult<>());
			if (result instanceof ComputingPropertyResult) {
				if (((ComputingPropertyResult<?>) result).startCompute()) {
					//we compute the property value
					//  else some other thread computes the value, we just wait for it in the .get() call
					ComputingPropertyResult<T> computingresult = (ComputingPropertyResult<T>) result;
					try {
						T resultval = super.internalGetEnvironmentPropertyCurrentValue(environment, environmentproperty,
								btrace);
						result = Functionals.valSupplier(resultval);
					} catch (PropertyComputationFailedException e) {
						result = new FailedPropertyResult<>(e.getCause());
					} catch (Throwable e) {
						//serious error, throw the exception, but set the result, so it is properly set in the cache
						result = new FailedPropertyResult<>(e);
						throw e;
					} finally {
						//always set the result, so the computing supplier is signalled, and other threads aren't deadlocked
						computingresult.setResult(result);
						//replace in the map, so the synchronization mechanism is avoided in future calls 
						//(they are no longer needed, as the property is calculated)
						cachemap.replace(environmentproperty, computingresult, result);
					}
				}
			}
			return result.get();
		}

	}

	private static final class BuildTraceRecordingForwardingSakerEnvironment
			extends ExecutionCachingEnvironmentPropertyForwardingSakerEnvironment {
		private final InternalBuildTrace btrace;

		public BuildTraceRecordingForwardingSakerEnvironment(SakerEnvironment environment, InternalBuildTrace btrace) {
			super(environment);
			this.btrace = btrace;
		}

		@Override
		public <T> T internalGetEnvironmentPropertyCurrentValue(SakerEnvironment environment,
				EnvironmentProperty<T> environmentproperty, InternalBuildTrace btrace) {
			//override the build trace for environment property computation
			//(the argument should be null, but check just in case)
			return super.internalGetEnvironmentPropertyCurrentValue(environment, environmentproperty,
					btrace == null ? this.btrace : btrace);
		}

	}

}
