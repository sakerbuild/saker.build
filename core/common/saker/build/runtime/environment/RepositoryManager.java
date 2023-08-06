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
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.classpath.ClassPathEnumerationError;
import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.runtime.classpath.ClassPathLoadManager.ClassPathLock;
import saker.build.runtime.classpath.ClassPathLocation;
import saker.build.runtime.classpath.ClassPathServiceEnumerator;
import saker.build.runtime.repository.ForwardingSakerRepository;
import saker.build.runtime.repository.RepositoryEnvironment;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.SakerRepositoryFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;
import saker.build.thirdparty.saker.util.ref.StrongWeakReference;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.trace.BuildTrace;

public class RepositoryManager implements Closeable {
	private static final String STORAGE_SUBDIRECTORY_NAME = "repository";
	private final Path storageDirectoryPath;
	private final Path environmentJarPath;

	private ClassPathLoadManager classPathManager;

	private final ConcurrentNavigableMap<Path, LoadedClassPath> locationLoadedClassPaths = new ConcurrentSkipListMap<>();
	private final ConcurrentNavigableMap<Path, Lock> classPathDirectoryLoadedClassPathLocks = new ConcurrentSkipListMap<>();

	private volatile boolean closed = false;

	public RepositoryManager(Path storageDirectoryPath, ClassPathLoadManager classPathManager,
			Path environmentJarPath) {
		SakerPathFiles.requireAbsolutePath(storageDirectoryPath, "storage directory path");

		this.storageDirectoryPath = storageDirectoryPath;
		this.classPathManager = classPathManager;
		this.environmentJarPath = environmentJarPath;
	}

	public RepositoryManager(ClassPathLoadManager classPathManager, Path environmentJarPath) {
		this(classPathManager.getStorageDirectoryPath(), classPathManager, environmentJarPath);
	}

	public Path getStorageDirectoryPath() {
		return storageDirectoryPath;
	}

	public SakerRepository loadRepository(ClassPathLocation repolocation,
			ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator)
			throws IOException, ClassPathEnumerationError {
		LoadedClassPath loadedcp = loadClassPath(repolocation);
		return loadedcp.getRepository(enumerator, () -> classPathManager.loadClassPath(repolocation));
	}

	public SakerRepository loadDirectRepository(Path repositoryclasspathloaddirectory,
			ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator)
			throws IOException, ClassPathEnumerationError {
		LoadedClassPath loadedcp = loadDirectClassPath(repositoryclasspathloaddirectory);
		return loadedcp.getRepository(enumerator,
				() -> classPathManager.loadDirectClassPath(repositoryclasspathloaddirectory));
	}

	public Path getRepositoryStorageDirectory(ClassPathLocation location) throws IOException {
		return getRepositoryStorageDirectory(location.getIdentifier());
	}

	@Override
	public void close() throws IOException {
		closed = true;
		IOException exc = null;
		for (Entry<Path, Lock> entry : classPathDirectoryLoadedClassPathLocks.entrySet()) {
			Lock lock = entry.getValue();
			lock.lock();
			try {
				exc = IOUtils.closeExc(exc, locationLoadedClassPaths.remove(entry.getKey()));
			} finally {
				lock.unlock();
			}
		}
		exc = IOUtils.closeExc(locationLoadedClassPaths.values());
		locationLoadedClassPaths.clear();
		IOUtils.throwExc(exc);
	}

	private LoadedClassPath loadDirectClassPath(Path classpathdirectory) throws IOException {
		LoadedClassPath loadedcp;
		Lock lock = getClassPathLoadDirectoryLock(classpathdirectory);
		IOUtils.lockIO(lock, "Acquiring class path load lock interrupted.");
		try {
			checkClosed();
			loadedcp = locationLoadedClassPaths.get(classpathdirectory);
			if (loadedcp == null) {
				loadedcp = new LoadedClassPath(this);
				locationLoadedClassPaths.put(classpathdirectory, loadedcp);
			}

		} finally {
			lock.unlock();
		}
		return loadedcp;
	}

	private LoadedClassPath loadClassPath(ClassPathLocation repolocation) throws IOException {
		LoadedClassPath loadedcp;
		Path classpathdirectory = classPathManager.getClassPathLoadDirectoryPath(repolocation);
		Lock lock = getClassPathLoadDirectoryLock(classpathdirectory);
		IOUtils.lockIO(lock, "Acquiring class path load lock interrupted.");
		try {
			checkClosed();
			loadedcp = locationLoadedClassPaths.get(classpathdirectory);
			if (loadedcp == null) {
				loadedcp = new LoadedClassPath(this);
				locationLoadedClassPaths.put(classpathdirectory, loadedcp);
			}
		} finally {
			lock.unlock();
		}
		return loadedcp;
	}

	private Lock getClassPathLoadDirectoryLock(Path classpathdirectory) {
		return classPathDirectoryLoadedClassPathLocks.computeIfAbsent(classpathdirectory,
				x -> ThreadUtils.newExclusiveLock());
	}

	private void checkClosed() {
		if (closed) {
			throw new IllegalStateException("closed");
		}
	}

	private Path getStorageDirectory(String identifier) throws IOException {
		Path storagedir = storageDirectoryPath;
		Path result = storagedir.resolve(identifier);
		LocalFileProvider.getInstance().createDirectories(result);
		return result;
	}

	private Path getRepositoryStorageDirectory(String locationid) throws IOException {
		return getStorageDirectory(STORAGE_SUBDIRECTORY_NAME + "/storage/" + locationid);
	}

	private static class SimpleRepositoryEnvironment implements RepositoryEnvironment {
		protected final Path classPathPath;
		protected final Path repositoryStorageDirectory;
		protected final Path repositoryClassPathLoadDirectory;
		protected final Path environmentStorageDirectory;
		protected final Path environmentJarPath;

		public SimpleRepositoryEnvironment(Path classPathPath, Path repositoryStorageDirectory,
				Path repositoryClassPathLoadDirectory, Path environmentStorageDirectory, Path environmentJarPath) {
			this.classPathPath = classPathPath;
			this.repositoryStorageDirectory = repositoryStorageDirectory;
			this.repositoryClassPathLoadDirectory = repositoryClassPathLoadDirectory;
			this.environmentStorageDirectory = environmentStorageDirectory;
			this.environmentJarPath = environmentJarPath;
		}

		@Override
		public Path getRepositoryClassPath() {
			return classPathPath;
		}

		@Override
		public Path getRepositoryStorageDirectory() {
			return repositoryStorageDirectory;
		}

		@Override
		public Path getRepositoryClassPathLoadDirectory() {
			return repositoryClassPathLoadDirectory;
		}

		@Override
		public Path getEnvironmentStorageDirectory() {
			return environmentStorageDirectory;
		}

		@Override
		public Path getEnvironmentJarPath() {
			return environmentJarPath;
		}
	}

	private static class LoadedClassPath implements Closeable {
		private static final AtomicIntegerFieldUpdater<RepositoryManager.LoadedClassPath> AIFU_closed = AtomicIntegerFieldUpdater
				.newUpdater(RepositoryManager.LoadedClassPath.class, "closed");
		private volatile int closed = 0;

		private RepositoryManager manager;
		protected ClassPathLock classPathLock;
		protected StrongWeakReference<MultiDataClassLoader> classLoader;
		protected Object classPathVersion;

		protected ConcurrentHashMap<ClassPathServiceEnumerator<? extends SakerRepositoryFactory>, RepositoryEnumeratorState> enumeratorStates = new ConcurrentHashMap<>();
		protected ConcurrentHashMap<ClassPathServiceEnumerator<? extends SakerRepositoryFactory>, Lock> enumeratorStateLocks = new ConcurrentHashMap<>();

		private final Lock loadCountLock = ThreadUtils.newExclusiveLock();
		private int allLoadCount = 0;

		public LoadedClassPath(RepositoryManager manager) {
			this.manager = manager;
		}

		public ReturnedRepository getRepository(ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator,
				IOSupplier<ClassPathLock> classPathLockSupplier) throws IOException, ClassPathEnumerationError {
			return getEnumeratorState(enumerator).loadRepository(classPathLockSupplier);
		}

		public RepositoryEnumeratorState getEnumeratorState(
				ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator) throws IOException {
			Lock lock = enumeratorStateLocks.computeIfAbsent(enumerator, x -> ThreadUtils.newExclusiveLock());
			IOUtils.lockIO(lock, "Failed to acquire repository enumerator lock.");
			try {
				checkClosed();
				RepositoryEnumeratorState enstate = enumeratorStates.get(enumerator);
				if (enstate == null) {
					enstate = new RepositoryEnumeratorState(this, enumerator);
					enumeratorStates.put(enumerator, enstate);
				}
				return enstate;
			} finally {
				lock.unlock();
			}
		}

		@Override
		public void close() throws IOException {
			if (!AIFU_closed.compareAndSet(this, 0, 1)) {
				return;
			}
			IOException exc = null;
			for (Entry<ClassPathServiceEnumerator<? extends SakerRepositoryFactory>, Lock> entry : enumeratorStateLocks
					.entrySet()) {
				RepositoryEnumeratorState state;
				Lock lock = entry.getValue();
				lock.lock();
				try {
					state = enumeratorStates.remove(entry.getKey());
				} finally {
					lock.unlock();
				}
				exc = IOUtils.closeExc(exc, state);
			}
			loadCountLock.lock();
			try {
				MultiDataClassLoader clref = ObjectUtils.getReference(this.classLoader);
				if (clref != null) {
					exc = IOUtils.closeExc(exc, clref.getDatasFinders());
				}
				exc = IOUtils.closeExc(exc, this.classPathLock);
				this.classLoader = null;
				this.classPathLock = null;
				allLoadCount = 0;
			} finally {
				loadCountLock.unlock();
			}
			IOUtils.throwExc(exc);
		}

		private void checkClosed() {
			if (closed != 0) {
				throw new IllegalStateException("closed");
			}
		}

		public void addLoadCount(IOSupplier<ClassPathLock> classPathLockSupplier) throws IOException {
			IOUtils.lockIO(loadCountLock, "Acquiring class path load counter lock interrupted.");
			try {
				addLoadCountLocked(classPathLockSupplier);
			} finally {
				loadCountLock.unlock();
			}
		}

		protected void addLoadCountLocked(IOSupplier<ClassPathLock> classPathLockSupplier) throws IOException {
			checkClosed();
			if (allLoadCount == 0) {
				this.classPathLock = classPathLockSupplier.get();
				Object version = this.classPathLock.getVersion();
				if (version.equals(this.classPathVersion) && this.classLoader.makeStrong()) {
					//successfully reused the previous classloader
					//XXX we should not query and cast, but keep a reference instead? or something like that
					((ForwardingReplaceableClassLoaderDataFinder) classLoader.get().getDatasFinders().iterator().next())
							.setClassLoaderDataFinder(classPathLock.getClassLoaderDataFinder());
				} else {
					this.classLoader = new StrongWeakReference<>(new MultiDataClassLoader(
							SakerEnvironment.class.getClassLoader(),
							new ForwardingReplaceableClassLoaderDataFinder(classPathLock.getClassLoaderDataFinder())));
				}
				this.classPathVersion = version;
			}
			++allLoadCount;
		}

		public void removeLoadCount() {
			removeLoadCount(1);
		}

		public void removeLoadCount(int c) {
			loadCountLock.lock();
			try {
				removeLoadCountLocked(c);
			} finally {
				loadCountLock.unlock();
			}
		}

		protected void removeLoadCountLocked(int c) {
			//XXX we don't need to check closed here do we? if the manager is closed, unloading should still succeed
//				checkClosed();
			if (allLoadCount < c) {
				throw new AssertionError(allLoadCount + " - " + c);
			}
			allLoadCount -= c;
			if (allLoadCount == 0) {
				for (Entry<ClassPathServiceEnumerator<? extends SakerRepositoryFactory>, Lock> entry : enumeratorStateLocks
						.entrySet()) {
					RepositoryEnumeratorState state;
					Lock lock = entry.getValue();
					lock.lock();
					try {
						state = enumeratorStates.remove(entry.getKey());
					} finally {
						lock.unlock();
					}
					IOUtils.closePrint(state);
				}
				MultiDataClassLoader clref = ObjectUtils.getReference(this.classLoader);
				if (clref != null) {
					IOUtils.closePrint(clref.getDatasFinders());
				}
				IOUtils.closePrint(this.classPathLock);
				if (this.classLoader != null) {
					this.classLoader.makeWeak();
				}
				classPathLock = null;
			}
		}
	}

	private static class ForwardingReplaceableClassLoaderDataFinder implements ClassLoaderDataFinder {
		private ClassLoaderDataFinder finder;

		public ForwardingReplaceableClassLoaderDataFinder(ClassLoaderDataFinder finder) {
			this.finder = finder;
		}

		@Override
		public ByteArrayRegion getClassBytes(String classname) {
			return finder.getClassBytes(classname);
		}

		@Override
		public ByteArrayRegion getResourceBytes(String name) {
			return finder.getResourceBytes(name);
		}

		@Override
		public Supplier<? extends ByteSource> getResource(String name) {
			return finder.getResource(name);
		}

		@Override
		public ByteSource getResourceAsStream(String name) {
			return finder.getResourceAsStream(name);
		}

		@Override
		public void close() throws IOException {
			finder.close();
		}

		public void setClassLoaderDataFinder(ClassLoaderDataFinder finder) {
			this.finder = finder;
		}

		@Override
		public String toString() {
			return Objects.toString(finder);
		}
	}

	//TODO keep Reference<> to these and track unclosed garbage collected repositories?
	private static class ReturnedRepository extends ForwardingSakerRepository<SakerRepository> {
		private static final AtomicIntegerFieldUpdater<RepositoryManager.ReturnedRepository> AIFU_closed = AtomicIntegerFieldUpdater
				.newUpdater(RepositoryManager.ReturnedRepository.class, "closed");

		protected final RepositoryLoadState loadState;

		@SuppressWarnings("unused")
		private volatile int closed;

		public ReturnedRepository(RepositoryLoadState loadState) {
			super(loadState.repository);
			this.loadState = loadState;
		}

		public SakerRepository getForwardedRepository() {
			return super.repository;
		}

		@Override
		public void close() throws IOException {
			if (!AIFU_closed.compareAndSet(this, 0, 1)) {
				return;
			}
			loadState.closeRepositoryFromReturnedRepository();
		}

	}

	private static class RepositoryLoadState implements Closeable {
		private int refCount = 1;
		protected final SakerRepository repository;
		protected final RepositoryEnumeratorState enumeratorState;

		protected final Lock stateLock = ThreadUtils.newExclusiveLock();

		public RepositoryLoadState(SakerRepository repository, RepositoryEnumeratorState enumeratorstate) {
			this.repository = repository;
			this.enumeratorState = enumeratorstate;
		}

		protected void closeRepositoryFromReturnedRepository() throws IOException {
			stateLock.lock();
			try {
				int rc = this.refCount;
				if (rc > 0) {
					this.refCount = rc - 1;
					if (rc == 1) {
						//no more references
						enumeratorState.closeRepositoryLockedOnState(this);
					}
					enumeratorState.loadedClassPath.removeLoadCount();
				}
			} finally {
				stateLock.unlock();
			}
		}

		protected boolean increaseOpenRefCountLocked() {
			int rc = this.refCount;
			if (rc > 0) {
				this.refCount = rc + 1;
				return true;
			}
			return false;
		}

		@Override
		public void close() throws IOException {
			stateLock.lock();
			try {
				closeLocked();
			} finally {
				stateLock.unlock();
			}
		}

		protected void closeLocked() throws IOException {
			int rc = this.refCount;
			if (rc > 0) {
				enumeratorState.closeRepositoryLockedOnState(this);
				enumeratorState.loadedClassPath.removeLoadCount(rc);
				this.refCount = 0;
			}
		}

	}

	private static class RepositoryEnumeratorState implements Closeable {
		private static final AtomicIntegerFieldUpdater<RepositoryManager.RepositoryEnumeratorState> AIFU_closed = AtomicIntegerFieldUpdater
				.newUpdater(RepositoryManager.RepositoryEnumeratorState.class, "closed");
		private volatile int closed;

		private static final AtomicReferenceFieldUpdater<RepositoryManager.RepositoryEnumeratorState, RepositoryLoadState> ARFU_loadState = AtomicReferenceFieldUpdater
				.newUpdater(RepositoryManager.RepositoryEnumeratorState.class, RepositoryLoadState.class, "loadState");

		private volatile RepositoryLoadState loadState;

		private final ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator;
		private final LoadedClassPath loadedClassPath;

		private SakerRepositoryFactory factory = null;
		private Object factoriesClassLoaderVersion = null;

		protected final Lock repositoryLock = ThreadUtils.newExclusiveLock();

		public RepositoryEnumeratorState(LoadedClassPath loadedClassPath,
				ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator) {
			this.loadedClassPath = loadedClassPath;
			this.enumerator = enumerator;
		}

		public void closeRepositoryLockedOnState(RepositoryLoadState repostate) throws IOException {
			try {
				repostate.repository.close();
			} finally {
				ARFU_loadState.compareAndSet(this, repostate, null);
			}
		}

		private SakerRepositoryFactory loadFactoriesImpl(ClassLoader classLoader) throws ClassPathEnumerationError {
			Iterable<? extends SakerRepositoryFactory> serviceiterable = enumerator.getServices(classLoader);
			Iterator<? extends SakerRepositoryFactory> it = serviceiterable.iterator();
			if (it.hasNext()) {
				SakerRepositoryFactory res = it.next();
				try {
					if (it.hasNext()) {
						BuildTrace.ignoredException(new IllegalArgumentException(
								"Multiple repositories found in classloader: " + classLoader));
					}
				} catch (ClassPathEnumerationError e) {
					BuildTrace.ignoredException(e);
				}
				return res;
			}
			return null;
		}

		public SakerRepositoryFactory getFactoriesAddLoadCount(IOSupplier<ClassPathLock> classPathLockSupplier)
				throws IOException, ClassPathEnumerationError {
			IOUtils.lockIO(loadedClassPath.loadCountLock, "Acquiring class path load counter lock interrupted.");
			try {
				loadedClassPath.addLoadCountLocked(classPathLockSupplier);
				if (loadedClassPath.classPathVersion.equals(factoriesClassLoaderVersion)) {
					return factory;
				}
				try {
					this.factory = loadFactoriesImpl(loadedClassPath.classLoader.get());
					this.factoriesClassLoaderVersion = loadedClassPath.classPathVersion;
				} catch (Throwable e) {
					loadedClassPath.removeLoadCountLocked(1);
					throw e;
				}
			} finally {
				loadedClassPath.loadCountLock.unlock();
			}
			return factory;
		}

		public ReturnedRepository loadRepository(IOSupplier<ClassPathLock> classPathLockSupplier)
				throws IOException, ClassPathEnumerationError {
			SakerRepositoryFactory factory = getFactoriesAddLoadCount(classPathLockSupplier);
			try {
				if (factory == null) {
					return null;
				}
				IOUtils.lockIO(repositoryLock, "Failed to acquire repository lock.");
				try {
					checkClosed();
					RepositoryLoadState repostate = loadState;
					if (repostate != null) {
						IOUtils.lockIO(repostate.stateLock, "Failed to acquire repository load state lock.");
						try {
							if (repostate.increaseOpenRefCountLocked()) {
								loadedClassPath.addLoadCount(classPathLockSupplier);
								return new ReturnedRepository(repostate);
							}
							//repository was closed
							repostate.closeLocked();
						} finally {
							repostate.stateLock.unlock();
						}
						loadState = null;
					}
					RepositoryEnvironment repoenvironment = new SimpleRepositoryEnvironment(
							loadedClassPath.classPathLock.getClassPathPath(),
							loadedClassPath.manager.getRepositoryStorageDirectory(
									loadedClassPath.classPathLock.getLocationIdentifier()),
							loadedClassPath.classPathLock.getClassPathLoadDirectory(),
							loadedClassPath.manager.storageDirectoryPath, loadedClassPath.manager.environmentJarPath);
					repostate = new RepositoryLoadState(factory.create(repoenvironment), this);
					this.loadState = repostate;
					loadedClassPath.addLoadCount(classPathLockSupplier);
					return new ReturnedRepository(repostate);
				} finally {
					repositoryLock.unlock();
				}
			} finally {
				loadedClassPath.removeLoadCount();
			}
		}

//		public ReturnedRepository loadRepository(String repositoryidentifier) throws IOException {
//			boolean successful = false;
//			NavigableMap<String, SakerRepositoryFactory> factories = getFactoriesAddLoadCount();
//			try {
//				synchronized (repositoryLocks.computeIfAbsent(repositoryidentifier, Functionals.objectComputer())) {
//					checkClosed();
//					RepositoryLoadState repostate = repositories.get(repositoryidentifier);
//					if (repostate != null) {
//						synchronized (repostate) {
//							if (repostate.increaseOpenRefCountLocked()) {
//								successful = true;
//								return new ReturnedRepository(repostate);
//							}
//						}
//						//repository was closed
//						repostate.close();
//						repositories.remove(repositoryidentifier);
//					}
//					SakerRepositoryFactory factory = factories.get(repositoryidentifier);
//					if (factory == null) {
//						return null;
//					}
//					RepositoryEnvironment repoenvironment = new SimpleRepositoryEnvironment(loadedClassPath.classPathLock.getClassPathPath(),
//							loadedClassPath.manager.getRepositoryStorageDirectory(loadedClassPath.classPathLock.getLocationIdentifier(), repositoryidentifier),
//							loadedClassPath.classPathLock.getClassPathLoadDirectory(), loadedClassPath.manager.storageDirectoryPath);
//					repostate = new RepositoryLoadState(factory.create(repoenvironment), repositoryidentifier, this);
//					repositories.put(repositoryidentifier, repostate);
//					successful = true;
//					return new ReturnedRepository(repostate);
//				}
//			} finally {
//				if (!successful) {
//					loadedClassPath.removeLoadCount();
//				}
//			}
//		}
//
//		public SakerRepository loadSingleRepository() throws IOException {
//			boolean successful = false;
//			NavigableMap<String, SakerRepositoryFactory> factories = getFactoriesAddLoadCount();
//			try {
//				if (factories.size() != 1) {
//					return null;
//				}
//				Entry<String, SakerRepositoryFactory> entry = factories.firstEntry();
//				String repositoryidentifier = entry.getKey();
//				synchronized (repositoryLocks.computeIfAbsent(repositoryidentifier, Functionals.objectComputer())) {
//					checkClosed();
//					RepositoryLoadState repostate = repositories.get(repositoryidentifier);
//					if (repostate != null) {
//						synchronized (repostate) {
//							if (repostate.increaseOpenRefCountLocked()) {
//								successful = true;
//								return new ReturnedRepository(repostate);
//							}
//						}
//						//repository was closed
//						repostate.close();
//						repositories.remove(repositoryidentifier);
//					}
//					SakerRepositoryFactory factory = entry.getValue();
//					RepositoryEnvironment repoenvironment = new SimpleRepositoryEnvironment(loadedClassPath.classPathLock.getClassPathPath(),
//							loadedClassPath.manager.getRepositoryStorageDirectory(loadedClassPath.classPathLock.getLocationIdentifier(), repositoryidentifier),
//							loadedClassPath.classPathLock.getClassPathLoadDirectory(), loadedClassPath.manager.storageDirectoryPath);
//					repostate = new RepositoryLoadState(factory.create(repoenvironment), repositoryidentifier, this);
//					repositories.put(repositoryidentifier, repostate);
//					successful = true;
//					return new ReturnedRepository(repostate);
//				}
//			} finally {
//				if (!successful) {
//					loadedClassPath.removeLoadCount();
//				}
//			}
//		}

		@Override
		public void close() throws IOException {
			if (!AIFU_closed.compareAndSet(this, 0, 1)) {
				return;
			}
			IOException exc = null;
			repositoryLock.lock();
			try {
				exc = IOUtils.closeExc(exc, loadState);
				loadState = null;
			} finally {
				repositoryLock.unlock();
			}
			factory = null;
			IOUtils.throwExc(exc);
		}

		private void checkClosed() {
			if (closed != 0) {
				throw new IllegalStateException("closed");
			}
		}

	}

}
