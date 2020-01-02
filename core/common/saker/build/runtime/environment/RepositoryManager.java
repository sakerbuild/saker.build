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
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
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
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;
import saker.build.thirdparty.saker.util.ref.StrongWeakReference;

public class RepositoryManager implements Closeable {
	private static final String STORAGE_SUBDIRECTORY_NAME = "repository";
	private final Path storageDirectoryPath;
	private final Path environmentJarPath;

	private ClassPathLoadManager classPathManager;

	private final ConcurrentNavigableMap<Path, LoadedClassPath> locationLoadedClassPaths = new ConcurrentSkipListMap<>();
	private final ConcurrentNavigableMap<Path, Object> classPathDirectoryLoadedClassPathLocks = new ConcurrentSkipListMap<>();

	private volatile boolean closed = false;

	public RepositoryManager(Path storageDirectoryPath, ClassPathLoadManager classPathManager,
			Path environmentJarPath) {
		SakerPathFiles.requireAbsolutePath(storageDirectoryPath);

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
			ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator) throws IOException {
		LoadedClassPath loadedcp = loadClassPath(repolocation);
		return loadedcp.getRepository(enumerator, () -> classPathManager.loadClassPath(repolocation));
	}

	public SakerRepository loadDirectRepository(Path repositoryclasspathloaddirectory,
			ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator) throws IOException {
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
		for (Entry<Path, Object> entry : classPathDirectoryLoadedClassPathLocks.entrySet()) {
			synchronized (entry.getValue()) {
				exc = IOUtils.closeExc(exc, locationLoadedClassPaths.remove(entry.getKey()));
			}
		}
		exc = IOUtils.closeExc(locationLoadedClassPaths.values());
		locationLoadedClassPaths.clear();
		IOUtils.throwExc(exc);
	}

	private LoadedClassPath loadDirectClassPath(Path classpathdirectory) {
		LoadedClassPath loadedcp;
		synchronized (classPathDirectoryLoadedClassPathLocks.computeIfAbsent(classpathdirectory,
				Functionals.objectComputer())) {
			checkClosed();
			loadedcp = locationLoadedClassPaths.get(classpathdirectory);
			if (loadedcp == null) {
				loadedcp = new LoadedClassPath(this);
				locationLoadedClassPaths.put(classpathdirectory, loadedcp);
			}
		}
		return loadedcp;
	}

	private LoadedClassPath loadClassPath(ClassPathLocation repolocation) {
		LoadedClassPath loadedcp;
		Path classpathdirectory = classPathManager.getClassPathLoadDirectoryPath(repolocation);
		synchronized (classPathDirectoryLoadedClassPathLocks.computeIfAbsent(classpathdirectory,
				Functionals.objectComputer())) {
			checkClosed();
			loadedcp = locationLoadedClassPaths.get(classpathdirectory);
			if (loadedcp == null) {
				loadedcp = new LoadedClassPath(this);
				locationLoadedClassPaths.put(classpathdirectory, loadedcp);
			}
		}
		return loadedcp;
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
		private RepositoryManager manager;
		protected ClassPathLock classPathLock;
		protected StrongWeakReference<MultiDataClassLoader> classLoader;
		protected Object classPathVersion;

		protected ConcurrentHashMap<ClassPathServiceEnumerator<? extends SakerRepositoryFactory>, RepositoryEnumeratorState> enumeratorStates = new ConcurrentHashMap<>();
		protected ConcurrentHashMap<ClassPathServiceEnumerator<? extends SakerRepositoryFactory>, Object> enumeratorStateLocks = new ConcurrentHashMap<>();

		private volatile boolean closed = false;

		private final Object loadCountLock = new Object();
		private int allLoadCount = 0;

		public LoadedClassPath(RepositoryManager manager) {
			this.manager = manager;
		}

		public ReturnedRepository getRepository(ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator,
				IOSupplier<ClassPathLock> classPathLockSupplier) throws IOException {
			return getEnumeratorState(enumerator).loadRepository(classPathLockSupplier);
		}

		public RepositoryEnumeratorState getEnumeratorState(
				ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator) {
			synchronized (enumeratorStateLocks.computeIfAbsent(enumerator, Functionals.objectComputer())) {
				checkClosed();
				RepositoryEnumeratorState enstate = enumeratorStates.get(enumerator);
				if (enstate == null) {
					enstate = new RepositoryEnumeratorState(this, enumerator);
					enumeratorStates.put(enumerator, enstate);
				}
				return enstate;
			}
		}

		@Override
		public void close() throws IOException {
			closed = true;
			IOException exc = null;
			for (Entry<ClassPathServiceEnumerator<? extends SakerRepositoryFactory>, Object> entry : enumeratorStateLocks
					.entrySet()) {
				synchronized (entry.getValue()) {
					exc = IOUtils.closeExc(exc, enumeratorStates.remove(entry.getKey()));
				}
			}
			synchronized (loadCountLock) {
				MultiDataClassLoader clref = ObjectUtils.getReference(this.classLoader);
				if (clref != null) {
					exc = IOUtils.closeExc(exc, clref.getDatasFinders());
				}
				exc = IOUtils.closeExc(exc, this.classPathLock);
				this.classLoader = null;
				this.classPathLock = null;
				allLoadCount = 0;
			}
			IOUtils.throwExc(exc);
		}

		private void checkClosed() {
			if (closed) {
				throw new IllegalStateException("closed");
			}
		}

		public void addLoadCount(IOSupplier<ClassPathLock> classPathLockSupplier) throws IOException {
			synchronized (loadCountLock) {
				addLoadCountLocked(classPathLockSupplier);
			}
		}

		protected void addLoadCountLocked(IOSupplier<ClassPathLock> classPathLockSupplier) throws IOException {
			checkClosed();
			if (allLoadCount == 0) {
				this.classPathLock = classPathLockSupplier.get();
				Object version = this.classPathLock.getVersion();
				if (version.equals(this.classPathVersion) && this.classLoader.makeStrong()) {
					//successfully reused the previous classloader
					//TODO we should not query and cast, but keep a reference instead? or something like that
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
			synchronized (loadCountLock) {
				//XXX we don't need to check closed here do we? if the manager is closed, unloading should still succeed
//				checkClosed();
				if (allLoadCount < c) {
					throw new AssertionError(allLoadCount + " - " + c);
				}
				allLoadCount -= c;
				if (allLoadCount == 0) {
					for (Entry<ClassPathServiceEnumerator<? extends SakerRepositoryFactory>, Object> entry : enumeratorStateLocks
							.entrySet()) {
						synchronized (entry.getValue()) {
							IOUtils.closePrint(enumeratorStates.remove(entry.getKey()));
						}
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

		public RepositoryLoadState(SakerRepository repository, RepositoryEnumeratorState enumeratorstate) {
			this.repository = repository;
			this.enumeratorState = enumeratorstate;
		}

		protected synchronized void closeRepositoryFromReturnedRepository() throws IOException {
			if (refCount > 0) {
				if (--refCount == 0) {
					enumeratorState.closeRepositoryLockedOnState(this);
				}
				enumeratorState.loadedClassPath.removeLoadCount();
			}
		}

		protected boolean increaseOpenRefCountLocked() {
			if (refCount > 0) {
				++refCount;
				return true;
			}
			return false;
		}

		@Override
		public synchronized void close() throws IOException {
			closeLocked();
		}

		protected void closeLocked() throws IOException {
			if (refCount > 0) {
				enumeratorState.closeRepositoryLockedOnState(this);
				enumeratorState.loadedClassPath.removeLoadCount(refCount);
				refCount = 0;
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

		protected final Object repositoryLock = new Object();

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

		private SakerRepositoryFactory loadFactoriesImpl(ClassLoader classLoader) {
			try {
				Iterable<? extends SakerRepositoryFactory> serviceiterable = enumerator.getServices(classLoader);
				Iterator<? extends SakerRepositoryFactory> it = serviceiterable.iterator();
				if (it.hasNext()) {
					SakerRepositoryFactory res = it.next();
					try {
						if (it.hasNext()) {
							System.err.println("Warning: Multiple repositories found in classloader: " + classLoader);
						}
					} catch (ClassPathEnumerationError e) {
						e.printStackTrace();
					}
					return res;
				}
			} catch (ClassPathEnumerationError e) {
				//print for info
				e.printStackTrace();
			}
			return null;
		}

		public SakerRepositoryFactory getFactoriesAddLoadCount(IOSupplier<ClassPathLock> classPathLockSupplier)
				throws IOException {
			synchronized (loadedClassPath.loadCountLock) {
				loadedClassPath.addLoadCountLocked(classPathLockSupplier);
				if (loadedClassPath.classPathVersion.equals(factoriesClassLoaderVersion)) {
					return factory;
				}
				this.factory = loadFactoriesImpl(loadedClassPath.classLoader.get());
				this.factoriesClassLoaderVersion = loadedClassPath.classPathVersion;
			}
			return factory;
		}

		public ReturnedRepository loadRepository(IOSupplier<ClassPathLock> classPathLockSupplier) throws IOException {
			SakerRepositoryFactory factory = getFactoriesAddLoadCount(classPathLockSupplier);
			try {
				if (factory == null) {
					return null;
				}
				synchronized (repositoryLock) {
					checkClosed();
					RepositoryLoadState repostate = loadState;
					if (repostate != null) {
						synchronized (repostate) {
							if (repostate.increaseOpenRefCountLocked()) {
								loadedClassPath.addLoadCount(classPathLockSupplier);
								return new ReturnedRepository(repostate);
							}
							//repository was closed
							repostate.closeLocked();
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
			synchronized (repositoryLock) {
				exc = IOUtils.closeExc(exc, loadState);
				loadState = null;
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
