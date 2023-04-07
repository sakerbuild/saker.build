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
package saker.build.runtime.environment;

import java.io.Closeable;
import java.io.Externalizable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintStream;
import java.io.Serializable;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import saker.build.exception.PropertyComputationFailedException;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.meta.PropertyNames;
import saker.build.meta.Versions;
import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.runtime.project.ProjectCacheHandle;
import saker.build.runtime.project.SakerProjectCache;
import saker.build.task.BuildTaskResultDatabase;
import saker.build.task.TaskContext;
import saker.build.task.TaskContextReference;
import saker.build.task.TaskExecutionManager.TaskResultCollectionImpl;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.BuildTargetBootstrapperTaskFactory;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.classloader.SingleClassLoaderResolver;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.DynamicFilterByteSink;
import saker.build.thirdparty.saker.util.io.DynamicFilterByteSource;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.ref.StrongWeakReference;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.trace.InternalBuildTrace;
import saker.build.util.cache.CacheKey;
import saker.build.util.cache.SakerDataCache;
import saker.build.util.config.JVMSynchronizationObjects;
import saker.build.util.exc.ExceptionView;
import saker.build.util.java.JavaTools;

public final class SakerEnvironmentImpl implements Closeable {
	public static Path getDefaultStorageDirectory() {
		String storagedirprop = PropertyNames.getProperty(PropertyNames.PROPERTY_DEFAULT_STORAGE_DIRECTORY);
		if (storagedirprop != null) {
			try {
				return Paths.get(storagedirprop).toAbsolutePath().normalize();
			} catch (InvalidPathException e) {
				throw new IllegalArgumentException(
						"Failed to parse default storage directory path property: " + storagedirprop, e);
			}
		}
		return Paths.get(System.getProperty("user.home")).resolve(STORAGE_DIRECTORY_NAME).toAbsolutePath().normalize();
	}

	private static final String STORAGE_DIRECTORY_NAME = ".saker";

	private final UUID environmentUUID = UUID.randomUUID();
	private final Path storageDirectoryPath;
	private final Path sakerJarPath;

	private int threadFactor;
	private ThreadGroup environmentThreadGroup;

	private PrintStream realStdOut;
	private PrintStream realStdErr;
	private InputStream realStdIn;

	private DynamicFilterByteSink stdOut;
	private DynamicFilterByteSink stdErr;
	private DynamicFilterByteSource stdIn;

	private PrintStream stdOutPrint;
	private PrintStream stdErrPrint;

	private SakerDataCache dataCache;

	private volatile boolean closed = false;
	//this Lock should be reentrant, as some closing mechanisms may be reentrant (SakerProjectCache - SakerExecutionCache)
	private final Lock runningExecutionsLock = new ReentrantLock();
	private final Condition runningExecutionsEmptyCondition = runningExecutionsLock.newCondition();
	/**
	 * Access while locked on {@link #runningExecutionsLock}.
	 */
	private Set<ExecutionKey> runningExecutions = new HashSet<>();

	private ClassLoaderResolverRegistry classLoaderRegistry;

	private final Map<EnvironmentProperty<?>, PropertyComputationResultSupplier> checkedEnvironmentProperties = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<EnvironmentProperty<?>, StrongWeakReference<Lock>> environmentPropertyCalculateLocks = new ConcurrentHashMap<>();

	private ClassPathLoadManager classPathManager;
	private RepositoryManager repositoryManager;

	private Map<String, String> userParameters;

	public SakerEnvironmentImpl(EnvironmentParameters params) {
		Objects.requireNonNull(params, "params");
		classLoaderRegistry = createEnvironmentBaseClassLoaderResolverRegistry();
		ThreadGroup parentthreadgroup = params.getEnvironmentThreadGroupParent();
		if (parentthreadgroup == null) {
			parentthreadgroup = Thread.currentThread().getThreadGroup();
		}
		this.userParameters = params.getUserParameters();
		environmentThreadGroup = new ThreadGroup(parentthreadgroup, "Environment group");
		sakerJarPath = params.getSakerJar();

		Path storagedirparam = params.getStorageDirectory();
		if (storagedirparam == null) {
			storagedirparam = getDefaultStorageDirectory();
		}
		storageDirectoryPath = storagedirparam;

		threadFactor = params.getThreadFactorDefaulted();

		//the subdirectories for the appropriate locations are used by the managers
		this.classPathManager = new ClassPathLoadManager(environmentThreadGroup, storageDirectoryPath);
		this.repositoryManager = new RepositoryManager(storageDirectoryPath, classPathManager, sakerJarPath);

		dataCache = new SakerDataCache(environmentThreadGroup);
	}

	public static ClassLoaderResolverRegistry createEnvironmentBaseClassLoaderResolverRegistry() {
		ClassLoaderResolverRegistry result = new ClassLoaderResolverRegistry(new SingleClassLoaderResolver(
				"saker.env.classes_" + Versions.VERSION_STRING_FULL, SakerEnvironmentImpl.class.getClassLoader()));
		result.register("jdk.tools", JavaTools.getJDKToolsClassLoaderResolver());
		return result;
	}

	public UUID getEnvironmentIdentifier() {
		return environmentUUID;
	}

	public BuildTaskExecutionResult run(TaskIdentifier taskid, TaskFactory<?> task,
			ExecutionParametersImpl parameters) {
		return this.run(taskid, task, parameters, (ProjectCacheHandle) null);
	}

	public BuildTaskExecutionResult run(TaskIdentifier taskid, TaskFactory<?> task, ExecutionParametersImpl parameters,
			ProjectCacheHandle projecthandle) {
		//XXX warn user if tries to invoke a build target task factory?
		return run(taskid, task, parameters, projecthandle == null ? null : projecthandle.toProject());
	}

	public BuildTaskExecutionResult run(SakerPath buildfilepath, String targetname, ExecutionParametersImpl parameters,
			ProjectCacheHandle projecthandle) {
		if (buildfilepath.isRelative()) {
			buildfilepath = parameters.getPathConfiguration().getWorkingDirectory().resolve(buildfilepath);
		}
		BuildTargetBootstrapperTaskFactory task = new BuildTargetBootstrapperTaskFactory(buildfilepath, targetname,
				Collections.emptyNavigableMap(), null, SakerPath.EMPTY);
		return run(BuildTargetBootstrapperTaskFactory.getTaskIdentifier(task), task, parameters, projecthandle);
	}

	private BuildTaskExecutionResult run(TaskIdentifier taskid, TaskFactory<?> task, ExecutionParametersImpl parameters,
			SakerProjectCache project) {
		Objects.requireNonNull(task, "task");
		throwRemoteTask(task);
		if (project != null && project.getEnvironment() != this) {
			throw new IllegalArgumentException("Trying to run build with project bound to a different environment.");
		}

		ConcurrentPrependAccumulator<Throwable> uncaughtexceptions = new ConcurrentPrependAccumulator<>();

		ThreadGroup executionthreadgroup = new ThreadGroup(environmentThreadGroup, "Saker build execution") {
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				super.uncaughtException(t, e);
				uncaughtexceptions.add(e);
			}
		};
		//daemon thread group, auto-destroys if no threads are running
		executionthreadgroup.setDaemon(true);

		if (closed) {
			throw new IllegalStateException("Closed.");
		}

		TaskRunnerThread<?> thread = new TaskRunnerThread<>(executionthreadgroup, this, parameters, project, taskid,
				task, uncaughtexceptions::clearAndIterable);

		thread.start();
		boolean interrupted = false;
		while (true) {
			//we MUST wait the thread, even if we're interrupted
			try {
				thread.join();
				break;
			} catch (InterruptedException ie) {
				executionthreadgroup.interrupt();
				interrupted = true;
				continue;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
		BuildTaskExecutionResult res = thread.result;
		if (res == null) {
			res = initializationFailedBuildResultCreator(
					new AssertionError("Internal error, failed to retrieve result from executor thread."))
							.apply(uncaughtexceptions.clearAndIterable());
		}
		return res;
	}

	public Map<String, String> getUserParameters() {
		return userParameters;
	}

	public ThreadGroup getEnvironmentThreadGroup() {
		return environmentThreadGroup;
	}

	public ClassPathLoadManager getClassPathManager() {
		return classPathManager;
	}

	public RepositoryManager getRepositoryManager() {
		return repositoryManager;
	}

	public void redirectStandardIO() {
		synchronized (JVMSynchronizationObjects.getStandardIOLock()) {
			if (realStdOut != null) {
				throw new IllegalStateException("Standard IO is already redirected.");
			}
			realStdOut = System.out;
			realStdErr = System.err;
			realStdIn = System.in;

			stdOut = new DynamicFilterByteSink(contextFunctionProvider(TaskContext::getStandardOut),
					ByteSink.valueOf(realStdOut));
			stdErr = new DynamicFilterByteSink(contextFunctionProvider(TaskContext::getStandardErr),
					ByteSink.valueOf(realStdErr));
			stdIn = new DynamicFilterByteSource(contextFunctionProvider(TaskContext::getStandardIn),
					ByteSource.valueOf(realStdIn));

			stdOutPrint = new PrintStream(stdOut);
			stdErrPrint = new PrintStream(stdErr);

			System.setErr(stdErrPrint);
			System.setOut(stdOutPrint);
			System.setIn(stdIn);
		}
	}

	public void unredirectStandardIO() {
		synchronized (JVMSynchronizationObjects.getStandardIOLock()) {
			if (realStdOut == null) {
				//was not redirected, or already unredirected via close() or something, return silently
				return;
			}
			unredirectStandardIOLockedImpl();
		}
	}

	private static boolean isInterruptedRootCause(Throwable e) {
		while (e != null) {
			if (e instanceof InterruptedException || e instanceof InterruptedIOException) {
				return true;
			}
			e = e.getCause();
		}
		return false;
	}

	private static boolean isRecomputeEnvironmentProperty(PropertyComputationResultSupplier result) {
		if (result == null) {
			return true;
		}
		if (isInterruptedRootCause(result.getException())) {
			//the exception was due to some execution interruption
			//recompute the environment property value in this case
			//so an interruption from a build execution doesn't leave a failed value in the cache
			return true;
		}
		return false;
	}

	public <T> T getEnvironmentPropertyCurrentValue(SakerEnvironment useenvironment,
			EnvironmentProperty<T> environmentproperty) {
		return getEnvironmentPropertyCurrentValue(useenvironment, environmentproperty, null);
	}

	public <T> T getEnvironmentPropertyCurrentValue(SakerEnvironment useenvironment,
			EnvironmentProperty<T> environmentproperty, InternalBuildTrace btrace) {
		Objects.requireNonNull(environmentproperty, "property");

		Map<EnvironmentProperty<?>, PropertyComputationResultSupplier> cachemap = checkedEnvironmentProperties;
		PropertyComputationResultSupplier result = cachemap.get(environmentproperty);
		if (isRecomputeEnvironmentProperty(result)) {
			Lock lock = getEnvironmentPropertyCalculateLock(environmentproperty);
			lock.lock();
			try {
				result = cachemap.get(environmentproperty);
				if (isRecomputeEnvironmentProperty(result)) {
					TransitivePropertyDependencyCollectionForwardingSakerEnvironment collectingenvironment = new TransitivePropertyDependencyCollectionForwardingSakerEnvironment(
							useenvironment);
					PropertyComputationResultSupplier nresult;
					try {
						T cval = environmentproperty.getCurrentValue(collectingenvironment);
						nresult = new PropertyComputationSuccessfulSupplier(cval);
					} catch (Exception e) {
						nresult = new PropertyComputationFailedThrowingSupplier(e);
					}
					nresult.transitiveDependentProperties = collectingenvironment.transitiveDependentProperties;
					if (result == null) {
						cachemap.putIfAbsent(environmentproperty, nresult);
					} else {
						cachemap.replace(environmentproperty, result, nresult);
					}
					result = nresult;
				}
			} finally {
				lock.unlock();
			}
		}
		if (btrace == null) {
			@SuppressWarnings("unchecked")
			T resultval = (T) result.get();
			return resultval;
		}
		return getComputedPropertyValueReportBuildTrace(result, environmentproperty, btrace);
	}

	@SuppressWarnings("unchecked") // result val assignment
	private <T> void reportComputedPropertyTransitiveBuildTrace(PropertyComputationResultSupplier result,
			EnvironmentProperty<T> environmentproperty, InternalBuildTrace btrace,
			Set<EnvironmentProperty<?>> collectedproperties) {
		if (!collectedproperties.add(environmentproperty)) {
			//already reported this environment property
			return;
		}
		if (result == null) {
			btrace.ignoredException(null,
					ExceptionView.create(new AssertionError(
							"Computed environment property result not found for build trace reporting: "
									+ environmentproperty)));
			return;
		}

		T resultval = null;
		PropertyComputationFailedException exc = null;
		try {
			resultval = (T) result.get();
		} catch (PropertyComputationFailedException e) {
			exc = e;
		}
		try {
			btrace.environmentPropertyAccessed(environmentproperty, resultval, exc);
		} catch (Exception e) {
			//no exceptions!
		}

		Set<EnvironmentProperty<?>> props = result.transitiveDependentProperties;
		if (props != null) {
			for (EnvironmentProperty<?> ep : props) {
				PropertyComputationResultSupplier epres = checkedEnvironmentProperties.get(ep);
				try {
					reportComputedPropertyTransitiveBuildTrace(epres, ep, btrace, collectedproperties);
				} catch (Exception e) {
					//no exceptions!
				}
			}
		}
	}

	@SuppressWarnings("unchecked") // result cast
	private <T> T getComputedPropertyValueReportBuildTrace(PropertyComputationResultSupplier result,
			EnvironmentProperty<T> environmentproperty, InternalBuildTrace btrace) {
		try {
			reportComputedPropertyTransitiveBuildTrace(result, environmentproperty, btrace, new HashSet<>());
		} catch (Exception e) {
			//no exceptions!
		}
		return (T) result.get();
	}

	private Lock getEnvironmentPropertyCalculateLock(EnvironmentProperty<?> environmentproperty) {
		StrongWeakReference<Lock> ref = environmentPropertyCalculateLocks.compute(environmentproperty, (k, v) -> {
			if (v != null && v.makeStrong()) {
				return v;
			}
			return new StrongWeakReference<>(ThreadUtils.newExclusiveLock());
		});
		Lock result = ref.get();
		ref.makeWeak();
		return result;
	}

	//doc: the returned map will throw PropertyComputationFailedException if the associated property failed to compute
	public static Map<? extends EnvironmentProperty<?>, ?> getEnvironmentPropertyDifferences(
			SakerEnvironment useenvironment, Map<? extends EnvironmentProperty<?>, ?> testproperties) {
		if (ObjectUtils.isNullOrEmpty(testproperties)) {
			return Collections.emptyMap();
		}
		Map<EnvironmentProperty<?>, Supplier<?>> result = new HashMap<>();
		for (Entry<? extends EnvironmentProperty<?>, ?> entry : testproperties.entrySet()) {
			EnvironmentProperty<?> property = entry.getKey();
			try {
				Object currentval = useenvironment.getEnvironmentPropertyCurrentValue(property);
				Object expectedval = entry.getValue();
				if (!Objects.equals(expectedval, currentval)) {
					result.put(property, Functionals.valSupplier(currentval));
				}
			} catch (RuntimeException e) {
				result.put(property, new PropertyComputationFailedThrowingSupplier(e));
			}
		}
		return new EnvironmentPropertyDifferenceMap(result);
	}

	public static boolean hasAnyEnvironmentPropertyDifference(SakerEnvironment useenvironment,
			Map<? extends EnvironmentProperty<?>, ?> testproperties) {
		if (ObjectUtils.isNullOrEmpty(testproperties)) {
			return false;
		}
		for (Entry<? extends EnvironmentProperty<?>, ?> entry : testproperties.entrySet()) {
			EnvironmentProperty<?> property = entry.getKey();
			try {
				Object currentval = useenvironment.getEnvironmentPropertyCurrentValue(property);
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

	public void invalidateEnvironmentPropertiesWaitExecutions(
			Collection<? super EnvironmentProperty<?>> environmentproperties) throws InterruptedException {
		final Lock lock = runningExecutionsLock;
		lock.lockInterruptibly();
		try {
			waitRunningExceptionsEmptyLocked();
			checkedEnvironmentProperties.keySet().removeAll(environmentproperties);
		} finally {
			lock.unlock();
		}
	}

	public void invalidateEnvironmentPropertiesWaitExecutions(
			Predicate<? super EnvironmentProperty<?>> environmentpropertypredicate) throws InterruptedException {
		final Lock lock = runningExecutionsLock;
		lock.lockInterruptibly();
		try {
			waitRunningExceptionsEmptyLocked();
			checkedEnvironmentProperties.keySet().removeIf(environmentpropertypredicate);
		} finally {
			lock.unlock();
		}
	}

	public void clearCachedDatasWaitExecutions() throws InterruptedException {
		final Lock lock = runningExecutionsLock;
		lock.lockInterruptibly();
		try {
			waitRunningExceptionsEmptyLocked();
			//replace with a new data cache
			SakerDataCache dc = dataCache;
			try {
				this.dataCache = new SakerDataCache(environmentThreadGroup);
			} finally {
				dc.close();
			}
		} finally {
			lock.unlock();
		}
	}

	public void invalidateCachedDatasWaitExecutions(Collection<? super CacheKey<?, ?>> cachekeys)
			throws InterruptedException {
		invalidateCachedDatasWaitExecutions((Predicate<? super CacheKey<?, ?>>) cachekeys::contains);
	}

	public void invalidateCachedDataWaitExecutions(CacheKey<?, ?> cachekey) throws InterruptedException {
		final Lock lock = runningExecutionsLock;
		lock.lockInterruptibly();
		try {
			waitRunningExceptionsEmptyLocked();
			dataCache.invalidate(cachekey);
		} finally {
			lock.unlock();
		}
	}

	public void invalidateCachedDatasWaitExecutions(Predicate<? super CacheKey<?, ?>> predicate)
			throws InterruptedException {
		final Lock lock = runningExecutionsLock;
		lock.lockInterruptibly();
		try {
			waitRunningExceptionsEmptyLocked();
			dataCache.invalidateIf(predicate);
		} finally {
			lock.unlock();
		}
	}

	public ClassLoaderResolverRegistry getClassLoaderResolverRegistry() {
		return classLoaderRegistry;
	}

	public <DataType, ResourceType> DataType getCachedData(CacheKey<DataType, ResourceType> key) throws Exception {
		return dataCache.get(key);
	}

	public int getThreadFactor() {
		return threadFactor;
	}

	public Path getStorageDirectoryPath() {
		return storageDirectoryPath;
	}

	public Path getEnvironmentJarPath() {
		return sakerJarPath;
	}

	public Path getStorageDirectory(String identifier) throws IOException {
		Path storagedir = storageDirectoryPath;
		if (storagedir == null) {
			throw new FileNotFoundException("Storage directory is not specified.");
		}
		Path result = storagedir.resolve(identifier);
		LocalFileProvider.getInstance().createDirectories(result);
		return result;
	}

	public Object getStartExecutionKey() throws IllegalStateException {
		ExecutionKey key = new ExecutionKey();
		final Lock lock = runningExecutionsLock;
		lock.lock();
		try {
			if (closed) {
				throw new IllegalStateException("Environment closed.");
			}
			runningExecutions.add(key);
		} finally {
			lock.unlock();
		}
		return key;
	}

	public void executionFinished(Object executionkey) {
		if (executionkey == null) {
			//can happen if the execution wasn't properly started
			return;
		}
		ExecutionKey realkey = (ExecutionKey) executionkey;
		final Lock lock = runningExecutionsLock;
		lock.lock();
		try {
			if (runningExecutions.remove(realkey) && runningExecutions.isEmpty()) {
				runningExecutionsEmptyCondition.signalAll();
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void close() throws IOException {
		closed = true;
		waitRunningExceptionsEmptyUninterruptibly();
		IOException exc = null;
		exc = IOUtils.closeExc(exc, dataCache);

		exc = IOUtils.closeExc(exc, repositoryManager);
		exc = IOUtils.closeExc(exc, classPathManager);

		unredirectStandardIO();
		try {
			environmentThreadGroup.destroy();
		} catch (IllegalThreadStateException e) {
			if (!environmentThreadGroup.isDestroyed()) {
				ThreadUtils.dumpThreadGroupStackTraces(System.err, environmentThreadGroup);
				exc = IOUtils.addExc(exc, new IOException("Failed to destroy Environment ThreadGroup.", e));
			}
		}
		IOUtils.throwExc(exc);
	}

	private void waitRunningExceptionsEmptyLocked() throws InterruptedException {
		while (!runningExecutions.isEmpty()) {
			runningExecutionsEmptyCondition.await();
		}
	}

	private void waitRunningExceptionsEmptyUninterruptibly() {
		final Lock lock = runningExecutionsLock;
		lock.lock();
		try {
			while (!runningExecutions.isEmpty()) {
				runningExecutionsEmptyCondition.awaitUninterruptibly();
			}
		} finally {
			lock.unlock();
		}
	}

	private static final class TransitivePropertyDependencyCollectionForwardingSakerEnvironment
			extends ForwardingSakerEnvironment {
		protected final Set<EnvironmentProperty<?>> transitiveDependentProperties = ConcurrentHashMap.newKeySet();

		private TransitivePropertyDependencyCollectionForwardingSakerEnvironment(SakerEnvironment environment) {
			super(environment);
		}

		@Override
		public <T> T internalGetEnvironmentPropertyCurrentValue(SakerEnvironment environment,
				EnvironmentProperty<T> environmentproperty, InternalBuildTrace btrace) {
			transitiveDependentProperties.add(environmentproperty);
			return super.internalGetEnvironmentPropertyCurrentValue(environment, environmentproperty, btrace);
		}
	}

	private static final class EnvironmentPropertyDifferenceMap extends AbstractMap<EnvironmentProperty<?>, Object>
			implements Serializable {
		private static final long serialVersionUID = 1L;

		private final Map<EnvironmentProperty<?>, Supplier<?>> result;

		private EnvironmentPropertyDifferenceMap(Map<EnvironmentProperty<?>, Supplier<?>> result) {
			this.result = result;
		}

		@Override
		public Object get(Object key) {
			Supplier<?> val = result.get(key);
			if (val != null) {
				return val.get();
			}
			return null;
		}

		@Override
		public int size() {
			return result.size();
		}

		@Override
		public boolean isEmpty() {
			return result.isEmpty();
		}

		@Override
		public boolean containsKey(Object key) {
			return result.containsKey(key);
		}

		@Override
		public Set<EnvironmentProperty<?>> keySet() {
			return result.keySet();
		}

		@Override
		public Object getOrDefault(Object key, Object defaultValue) {
			Supplier<?> val = result.get(key);
			if (val != null) {
				return val.get();
			}
			return defaultValue;
		}

		@Override
		public Set<Entry<EnvironmentProperty<?>, Object>> entrySet() {
			return new AbstractSet<Map.Entry<EnvironmentProperty<?>, Object>>() {

				@Override
				public Iterator<Entry<EnvironmentProperty<?>, Object>> iterator() {
					Iterator<Entry<EnvironmentProperty<?>, Supplier<?>>> it = result.entrySet().iterator();
					return new Iterator<Map.Entry<EnvironmentProperty<?>, Object>>() {

						@Override
						public Entry<EnvironmentProperty<?>, Object> next() {
							Entry<EnvironmentProperty<?>, Supplier<?>> entry = it.next();
							return new PropertyComputerThrowingEntry(entry);
						}

						@Override
						public boolean hasNext() {
							return it.hasNext();
						}
					};
				}

				@Override
				public boolean isEmpty() {
					return result.isEmpty();
				}

				@Override
				public int size() {
					return result.size();
				}
			};
		}

	}

	private static final class PropertyComputerThrowingEntry implements Entry<EnvironmentProperty<?>, Object> {
		private final Entry<EnvironmentProperty<?>, Supplier<?>> entry;

		private PropertyComputerThrowingEntry(Entry<EnvironmentProperty<?>, Supplier<?>> entry) {
			this.entry = entry;
		}

		@Override
		public EnvironmentProperty<?> getKey() {
			return entry.getKey();
		}

		@Override
		public Object getValue() {
			return entry.getValue().get();
		}

		@Override
		public Object setValue(Object value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((entry == null) ? 0 : entry.hashCode());
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
			PropertyComputerThrowingEntry other = (PropertyComputerThrowingEntry) obj;
			if (entry == null) {
				if (other.entry != null)
					return false;
			} else if (!entry.equals(other.entry))
				return false;
			return true;
		}
	}

	private static abstract class PropertyComputationResultSupplier implements Supplier<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		protected transient Set<EnvironmentProperty<?>> transitiveDependentProperties;

		public PropertyComputationResultSupplier() {
		}

		public Throwable getException() {
			return null;
		}
	}

	private static final class PropertyComputationSuccessfulSupplier extends PropertyComputationResultSupplier {
		private static final long serialVersionUID = 1L;

		private Object value;

		/**
		 * For {@link Externalizable}.
		 */
		public PropertyComputationSuccessfulSupplier() {
		}

		public PropertyComputationSuccessfulSupplier(Object value) {
			this.value = value;
		}

		@Override
		public Object get() {
			return value;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(value);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((value == null) ? 0 : value.hashCode());
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
			PropertyComputationSuccessfulSupplier other = (PropertyComputationSuccessfulSupplier) obj;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + value + "]";
		}

	}

	private static final class PropertyComputationFailedThrowingSupplier extends PropertyComputationResultSupplier {
		private static final long serialVersionUID = 1L;

		private Throwable e;

		/**
		 * For {@link Externalizable}.
		 */
		public PropertyComputationFailedThrowingSupplier() {
		}

		PropertyComputationFailedThrowingSupplier(Throwable e) {
			this.e = e;
		}

		@Override
		public Throwable getException() {
			return e;
		}

		@Override
		public Object get() {
			throw new PropertyComputationFailedException(e);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(e);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			e = (Throwable) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((e == null) ? 0 : e.hashCode());
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
			PropertyComputationFailedThrowingSupplier other = (PropertyComputationFailedThrowingSupplier) obj;
			if (e == null) {
				if (other.e != null)
					return false;
			} else if (!e.equals(other.e))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + e + "]";
		}
	}

	private static final class ExecutionKey {
		public ExecutionKey() {
		}
	}

	private static final class TaskRunnerThread<R> extends Thread {
		private static final AtomicInteger contextThreadCounter = new AtomicInteger(0);

		protected final SakerEnvironmentImpl environment;
		protected final ExecutionParametersImpl parameters;
		protected final SakerProjectCache project;
		private final TaskFactory<R> taskFactory;
		private final TaskIdentifier taskId;

		protected BuildTaskExecutionResult result;

		protected Supplier<Iterable<? extends Throwable>> uncaughtSupplier;

		public TaskRunnerThread(ThreadGroup group, SakerEnvironmentImpl environment, ExecutionParametersImpl parameters,
				SakerProjectCache project, TaskIdentifier taskid, TaskFactory<R> task,
				Supplier<Iterable<? extends Throwable>> uncaughtSupplier) {
			super(group, "Execution context-" + contextThreadCounter.incrementAndGet());
			setContextClassLoader(null);
			this.taskId = taskid;
			this.taskFactory = task;
			this.environment = environment;
			this.parameters = parameters;
			this.project = project;
			this.uncaughtSupplier = uncaughtSupplier;
		}

		protected Function<Iterable<? extends Throwable>, BuildTaskExecutionResult> runWithContext(
				ExecutionContextImpl context) {
			Throwable exc = null;
			try {
				context.executeTask(taskId, taskFactory);
			} catch (Throwable e) {
				exc = e;
			}
			BuildTaskResultDatabase results = context.getResultDatabase();
			TaskResultCollectionImpl taskresults = context.getResultCollection();
			if (exc != null) {
				Throwable fexc = exc;
				return uncaughtexceptions -> {
					for (Throwable ue : uncaughtexceptions) {
						fexc.addSuppressed(ue);
					}
					return BuildTaskExecutionResultImpl.createFailed(results, taskresults, fexc, taskId);
				};
			}
			return uncaughtexceptions -> {
				Iterator<? extends Throwable> it = uncaughtexceptions.iterator();
				if (!it.hasNext()) {
					return BuildTaskExecutionResultImpl.createSuccessful(results, taskresults, taskId);
				}
				RuntimeException uncaughtholder = new RuntimeException("Uncaught exceptions during build execution.");
				do {
					uncaughtholder.addSuppressed(it.next());
				} while (it.hasNext());
				return BuildTaskExecutionResultImpl.createSuccessful(results, taskresults, taskId, uncaughtholder);
			};
		}

		@Override
		public void run() {
			Throwable initexc = null;
			ExecutionContextImpl context;
			try {
				context = new ExecutionContextImpl(environment, parameters);
			} catch (Exception e) {
				result = initializationFailedBuildResultCreator(e).apply(uncaughtSupplier.get());
				return;
			} catch (Throwable e) {
				result = initializationFailedBuildResultCreator(e).apply(uncaughtSupplier.get());
				throw e;
			}
			try {
				run_block:
				{
					try {
						context.initialize(project);
					} catch (Exception e) {
						initexc = IOUtils.addExc(initexc, e);
						//don't run
						break run_block;
					} catch (Throwable e) {
						initexc = IOUtils.addExc(initexc, e);
						throw e;
					}
					result = runWithContext(context).apply(uncaughtSupplier.get());
				}
			} finally {
				try {
					try {
						if (initexc != null) {
							try {
								result = initializationFailedBuildResultCreator(initexc).apply(uncaughtSupplier.get());
							} catch (Exception e) {
								//dont propagate the exception any further
								initexc.addSuppressed(e);
								result = BuildTaskExecutionResultImpl.createInitializationFailed(
										new AssertionError("Build result creation failure. (" + e.getClass() + ")"));
							}
						}
					} finally {
						//if somewhy the build result creation fails, we still need to close the context.
						context.close();
					}
				} catch (Exception e) {
					//the closing failed
					initexc = IOUtils.addExc(initexc, e);
					try {
						result = initializationFailedBuildResultCreator(initexc).apply(uncaughtSupplier.get());
					} catch (Exception ex) {
						//dont propagate the exception any further
						if (result == null) {
							result = BuildTaskExecutionResultImpl.createInitializationFailed(
									new AssertionError("Build result creation failure. (" + ex.getClass() + ")"));
						}
					}
				} catch (Throwable e) {
					//the closing failed
					initexc = IOUtils.addExc(initexc, e);
					try {
						result = initializationFailedBuildResultCreator(initexc).apply(uncaughtSupplier.get());
					} catch (Exception ex) {
						//dont propagate the exception any further
						if (result == null) {
							result = BuildTaskExecutionResultImpl.createInitializationFailed(
									new AssertionError("Build result creation failure. (" + ex.getClass() + ")"));
						}
					}
					throw e;
				}
			}
		}

	}

	protected static Function<Iterable<? extends Throwable>, BuildTaskExecutionResult> initializationFailedBuildResultCreator(
			Throwable e) {
		return uncaughtexcs -> {
			for (Throwable ue : uncaughtexcs) {
				e.addSuppressed(ue);
			}
			return BuildTaskExecutionResultImpl.createInitializationFailed(e);
		};
	}

	private static void throwRemoteTask(Object executable) throws IllegalArgumentException {
		if (RMIConnection.isRemoteObject(executable)) {
			throw new IllegalArgumentException("Task is an RMI object. Task objects need to be locally created.");
		}
	}

	private static <T> Supplier<T> contextFunctionProvider(Function<? super TaskContext, ? extends T> contextfunction) {
		return () -> {
			TaskContext context = TaskContextReference.current();
			if (context == null) {
				return null;
			}
			return contextfunction.apply(context);
		};
	}

	/**
	 * Caller should lock on {@link JVMSynchronizationObjects#getStandardIOLock()}.
	 */
	private void unredirectStandardIOLockedImpl() {
		//XXX we should only set the i/o when it is actually the ones we replaced?
		System.setIn(realStdIn);
		System.setOut(realStdOut);
		System.setErr(realStdErr);

		//close the replaced ones after setting back the originals, so the possible exception is printed to the original streams
		IOUtils.closePrint(stdIn, stdOutPrint, stdErrPrint);

		realStdOut = null;
		realStdErr = null;
		realStdIn = null;
		stdOut = null;
		stdErr = null;
		stdIn = null;
		stdOutPrint = null;
		stdErrPrint = null;
	}

}
