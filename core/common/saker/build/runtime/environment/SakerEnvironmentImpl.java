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
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.util.cache.CacheKey;
import saker.build.util.cache.SakerDataCache;
import saker.build.util.config.JVMSynchronizationObjects;
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
	private final Object runningExecutionsLock = new Object();
	private Set<ExecutionKey> runningExecutions = new HashSet<>();

	private ClassLoaderResolverRegistry classLoaderRegistry;

	private final Map<EnvironmentProperty<?>, Supplier<?>> checkedEnvironmentProperties = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<EnvironmentProperty<?>, Object> environmentPropertyCalculateLocks = new ConcurrentHashMap<>();

	private ClassPathLoadManager classPathManager;
	private RepositoryManager repositoryManager;

	private Map<String, String> userParameters;

	public SakerEnvironmentImpl(EnvironmentParameters params) {
		Objects.requireNonNull(params, "params");
		classLoaderRegistry = new ClassLoaderResolverRegistry(new SingleClassLoaderResolver(
				"saker.env.classes_" + Versions.VERSION_STRING_FULL, SakerEnvironmentImpl.class.getClassLoader()));
		classLoaderRegistry.register("jdk.tools", JavaTools.getJDKToolsClassLoaderResolver());
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

	public BuildTaskExecutionResult run(TaskIdentifier taskid, TaskFactory<?> task, ExecutionParametersImpl parameters,
			SakerProjectCache project) {
		Objects.requireNonNull(task, "task");
		throwRemoteTask(task);

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
		TaskRunnerThread<?> thread;
		synchronized (this) {
			if (closed) {
				throw new IllegalStateException("Closed.");
			}

			thread = new TaskRunnerThread<>(executionthreadgroup, this, parameters, project, taskid, task);
		}
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
		BuildTaskExecutionResultImpl res = thread.buildResult;
		if (res == null) {
			res = BuildTaskExecutionResultImpl.createInitializationFailed(
					new AssertionError("Internal error, failed to retrieve result from executor thread."));
		}
		for (Iterator<Throwable> it = uncaughtexceptions.clearAndIterator(); it.hasNext();) {
			res.addException(it.next());
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
				throw new IllegalStateException("Standard IO was not redirected.");
			}
			unredirectStandardIOImpl();
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T getEnvironmentPropertyCurrentValue(SakerEnvironment useenvironment,
			EnvironmentProperty<T> environmentproperty) {
		Objects.requireNonNull(environmentproperty, "property");
		Supplier<?> result = checkedEnvironmentProperties.get(environmentproperty);
		if (result != null) {
			return (T) result.get();
		}
		synchronized (environmentPropertyCalculateLocks.computeIfAbsent(environmentproperty,
				Functionals.objectComputer())) {
			result = checkedEnvironmentProperties.get(environmentproperty);
			if (result != null) {
				return (T) result.get();
			}
			try {
				T cval = environmentproperty.getCurrentValue(useenvironment);
				result = Functionals.valSupplier(cval);
			} catch (Exception e) {
				result = new PropertyComputationFailedThrowingSupplier(e);
			}
			checkedEnvironmentProperties.putIfAbsent(environmentproperty, result);
			return (T) result.get();
		}
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
		synchronized (runningExecutionsLock) {
			while (!runningExecutions.isEmpty()) {
				runningExecutionsLock.wait();
			}
			checkedEnvironmentProperties.keySet().removeAll(environmentproperties);
		}
	}

	public void invalidateEnvironmentPropertiesWaitExecutions(
			Predicate<? super EnvironmentProperty<?>> environmentpropertypredicate) throws InterruptedException {
		synchronized (runningExecutionsLock) {
			while (!runningExecutions.isEmpty()) {
				runningExecutionsLock.wait();
			}
			checkedEnvironmentProperties.keySet().removeIf(environmentpropertypredicate);
		}
	}

	public void clearCachedDatasWaitExecutions() throws InterruptedException {
		synchronized (runningExecutionsLock) {
			while (!runningExecutions.isEmpty()) {
				runningExecutionsLock.wait();
			}
			dataCache.clear();
		}
	}

	public void invalidateCachedDatasWaitExecutions(Collection<? super CacheKey<?, ?>> cachekeys)
			throws InterruptedException {
		invalidateCachedDatasWaitExecutions((Predicate<? super CacheKey<?, ?>>) cachekeys::contains);
	}

	public void invalidateCachedDatasWaitExecutions(Predicate<? super CacheKey<?, ?>> predicate)
			throws InterruptedException {
		synchronized (runningExecutionsLock) {
			while (!runningExecutions.isEmpty()) {
				runningExecutionsLock.wait();
			}
			dataCache.invalidateIf(predicate);
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
		synchronized (runningExecutionsLock) {
			if (closed) {
				throw new IllegalStateException("Environment closed.");
			}
			runningExecutions.add(key);
		}
		return key;
	}

	public void executionFinished(Object executionkey) {
		if (executionkey == null) {
			//can happen if the execution wasn't properly started
			return;
		}
		ExecutionKey realkey = (ExecutionKey) executionkey;
		synchronized (runningExecutionsLock) {
			if (runningExecutions.remove(realkey)) {
				runningExecutionsLock.notifyAll();
			}
		}
	}

	@Override
	public synchronized void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
		IOException exc = null;
		boolean interrupted = false;
		synchronized (runningExecutionsLock) {
			while (!runningExecutions.isEmpty()) {
				try {
					runningExecutionsLock.wait();
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
		}
		exc = IOUtils.closeExc(exc, dataCache);

		exc = IOUtils.closeExc(exc, repositoryManager);
		exc = IOUtils.closeExc(exc, classPathManager);

		unredirectStandardIOIfInstalled();
		try {
			environmentThreadGroup.destroy();
		} catch (IllegalThreadStateException e) {
			if (!environmentThreadGroup.isDestroyed()) {
				ThreadUtils.dumpThreadGroupStackTraces(System.out, environmentThreadGroup);
				exc = IOUtils.addExc(exc, new IOException("Failed to destroy Environment ThreadGroup.", e));
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
		IOUtils.throwExc(exc);
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

	private static final class PropertyComputationFailedThrowingSupplier implements Supplier<Object>, Externalizable {
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

	private static class ExecutionKey {
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

		protected BuildTaskExecutionResultImpl buildResult;

		public TaskRunnerThread(ThreadGroup group, SakerEnvironmentImpl environment, ExecutionParametersImpl parameters,
				SakerProjectCache project, TaskIdentifier taskid, TaskFactory<R> task) {
			super(group, "Execution context-" + contextThreadCounter.incrementAndGet());
			setContextClassLoader(null);
			this.taskId = taskid;
			this.taskFactory = task;
			this.environment = environment;
			this.parameters = parameters;
			this.project = project;
		}

		protected BuildTaskExecutionResultImpl runWithContext(ExecutionContextImpl context) {
			Throwable exc = null;
			try {
				context.executeTask(taskId, taskFactory);
			} catch (Throwable e) {
				exc = e;
			}
			BuildTaskResultDatabase results = context.getResultDatabase();
			TaskResultCollectionImpl taskresults = context.getResultCollection();
			if (exc != null) {
				return BuildTaskExecutionResultImpl.createFailed(results, taskresults, exc, taskId);
			}
			return BuildTaskExecutionResultImpl.createSuccessful(results, taskresults, taskId);
		}

		@Override
		public void run() {
			ExecutionContextImpl context;
			try {
				context = new ExecutionContextImpl(environment, parameters);
			} catch (Exception e) {
				buildResult = BuildTaskExecutionResultImpl.createInitializationFailed(e);
				return;
			} catch (Throwable e) {
				buildResult = BuildTaskExecutionResultImpl.createInitializationFailed(e);
				throw e;
			}
			try {
				run_block:
				{
					try {
						context.initialize(project);
					} catch (Exception e) {
						buildResult = BuildTaskExecutionResultImpl.createInitializationFailed(e);
						//don't run
						break run_block;
					}
					buildResult = runWithContext(context);
				}
			} finally {
				try {
					context.close();
				} catch (Exception e) {
					if (buildResult == null) {
						buildResult = BuildTaskExecutionResultImpl.createInitializationFailed(e);
					} else {
						buildResult.addException(e);
					}
				} catch (Throwable e) {
					if (buildResult == null) {
						buildResult = BuildTaskExecutionResultImpl.createInitializationFailed(e);
					} else {
						buildResult.addException(e);
					}
					throw e;
				}
			}
		}
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

	private void unredirectStandardIOIfInstalled() {
		synchronized (JVMSynchronizationObjects.getStandardIOLock()) {
			if (realStdOut != null) {
				unredirectStandardIOImpl();
			}
		}
	}

	private void unredirectStandardIOImpl() {
		synchronized (JVMSynchronizationObjects.getStandardIOLock()) {
			IOUtils.closePrint(stdIn, stdOutPrint, stdErrPrint);

			//XXX we should only set the i/o when it is actually the ones we replaced?

			System.setIn(realStdIn);
			System.setOut(realStdOut);
			System.setErr(realStdErr);
		}
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
