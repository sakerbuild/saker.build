package saker.osnative.watcher.base;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.osnative.watcher.RegisteringWatchService;

public abstract class SakerWatchService implements RegisteringWatchService {
	protected static final FileSystem DEFAULT_FILESYSTEM = FileSystems.getDefault();

	protected static final WatchEvent.Modifier FILE_TREE_MODIFIER = FileUtils.getFileTreeExtendedWatchEventModifier();
	protected static final Set<WatchEvent.Modifier> SENSITIVITY_MODIFIERS = getSensitivityModifiers();

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected static Set<WatchEvent.Modifier> getSensitivityModifiers() {
		try {
			Class clazz = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier", false, null);
			return EnumSet.allOf(clazz);
		} catch (ClassNotFoundException | IllegalArgumentException e) {
			return Collections.emptySet();
		}
	}

	protected final Object signaledKeysLock = new Object();
	protected volatile Collection<SakerUserWatchKey> signaledKeys = new LinkedHashSet<>();
	protected final Map<KeyConfig, Object> nativeKeyComputationLocks = new ConcurrentSkipListMap<>();
	protected final Map<KeyConfig, SakerNativeWatchKey> nativeKeys = new ConcurrentSkipListMap<>();

	protected volatile long nativeServicePtr;

	protected final ReadWriteLock serviceLock = new ReentrantReadWriteLock();

	public SakerWatchService(long nativeServicePtr) {
		this.nativeServicePtr = nativeServicePtr;
	}

	@Override
	public WatchKey poll() {
		synchronized (signaledKeysLock) {
			Collection<SakerUserWatchKey> keys = signaledKeys;
			if (keys == null) {
				throw new ClosedWatchServiceException();
			}
			SakerUserWatchKey result = ObjectUtils.removeFirstElement(keys);
			if (result != null) {
				result.removedFromQueue();
			}
			return result;
		}
	}

	@Override
	public WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
		synchronized (signaledKeysLock) {
			Collection<SakerUserWatchKey> keys = signaledKeys;
			if (keys == null) {
				throw new ClosedWatchServiceException();
			}

			long start = System.nanoTime();
			timeout = unit.toNanos(timeout);
			long remaining = timeout;
			while (true) {
				SakerUserWatchKey first = ObjectUtils.removeFirstElement(keys);
				if (first != null) {
					first.removedFromQueue();
					return first;
				}
				signaledKeysLock.wait(remaining / 1000000, (int) remaining % 1000000);
				keys = signaledKeys;
				if (keys == null) {
					//we dont throw a closed watch service exception yet, but only return null
					//the watch service was open when the polling was issued, but it became closed meanwhile
					//dont force the caller to handle unnecessary exceptions, but it will be thrown if he calls anything else
					return null;
				}
				long elapsed = System.nanoTime() - start;
				if (elapsed >= timeout) {
					SakerUserWatchKey result = ObjectUtils.removeFirstElement(keys);
					if (result != null) {
						result.removedFromQueue();
					}
					return result;
				}
				remaining = timeout - elapsed;
			}
		}
	}

	@Override
	public WatchKey take() throws InterruptedException {
		synchronized (signaledKeysLock) {
			while (true) {
				Collection<SakerUserWatchKey> keys = signaledKeys;
				if (keys == null) {
					throw new ClosedWatchServiceException();
				}
				SakerUserWatchKey first = ObjectUtils.removeFirstElement(keys);
				if (first != null) {
					first.removedFromQueue();
					return first;
				}
				signaledKeysLock.wait();
			}
		}
	}

	@Override
	public void close() throws IOException {
		Lock wlock = serviceLock.writeLock();
		wlock.lock();
		try {
			long serviceptr = nativeServicePtr;
			if (serviceptr == 0) {
				return;
			}
			synchronized (signaledKeysLock) {
				signaledKeys = null;
				signaledKeysLock.notifyAll();
			}
			//create a copy collection to defend against concurrent removal
			List<SakerNativeWatchKey> copy = new ArrayList<>(nativeKeys.values());
			for (SakerNativeWatchKey nativekey : copy) {
				closeNativeKeyServiceLocked(nativekey, serviceptr);
			}
			nativeKeys.clear();
			closeWatcher(serviceptr);
			nativeServicePtr = 0;
		} finally {
			wlock.unlock();
		}
	}

	private static int makeEventFlag(WatchEvent.Kind<?>[] events, WatchEvent.Modifier[] modifiers) {
		int flag = 0;
		for (Kind<?> kind : events) {
			if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
				flag |= SakerNativeWatchKey.FLAG_EVENT_CREATE;
			} else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
				flag |= SakerNativeWatchKey.FLAG_EVENT_MODIFY;
			} else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
				flag |= SakerNativeWatchKey.FLAG_EVENT_DELETE;
			} else if (kind == StandardWatchEventKinds.OVERFLOW) {
				//ignore
			} else {
				throw new UnsupportedOperationException("Unknown event kind: " + kind);
			}
		}
		if (modifiers != null) {
			if (FILE_TREE_MODIFIER == null) {
				for (Modifier m : modifiers) {
					if (!SENSITIVITY_MODIFIERS.contains(m)) {
						throw new UnsupportedOperationException("Unknown modifier: " + m);
					}
				}
			} else {
				for (Modifier m : modifiers) {
					if (m == FILE_TREE_MODIFIER) {
						flag |= SakerNativeWatchKey.FLAG_QUERY_FILE_TREE;
					} else {
						if (!SENSITIVITY_MODIFIERS.contains(m)) {
							throw new UnsupportedOperationException("Unknown modifier: " + m);
						}
					}
				}
			}
		}
		return flag;
	}

	private SakerUserWatchKey addUserWatchKeyToNativeKey(SakerNativeWatchKey nativekey, Path path, int eventFlags,
			long nativeservice) {
		if (nativekey == null) {
			return null;
		}
		Lock nrlock = nativekey.keyLock.readLock();
		nrlock.lock();
		try {
			long nptr = nativekey.nativePtr;
			if (nptr == 0) {
				return null;
			}
			if (!keyIsValid(nativeservice, nptr)) {
				return null;
			}
			SakerUserWatchKey userkey = new SakerUserWatchKey(this, nativekey, path, eventFlags);
			if (nativekey.addUserKey(userkey)) {
				return userkey;
			}
			return null;
		} finally {
			nrlock.unlock();
		}
	}

	@Override
	public WatchKey register(Path path, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
			throws IOException {
		if (path.getFileSystem() != DEFAULT_FILESYSTEM) {
			throw new IllegalArgumentException("Path is not bound to the default filesystem: " + path);
		}
		Path normalizedpath = path.toAbsolutePath().normalize();
		int flags = makeEventFlag(events, modifiers);
		KeyConfig config = new KeyConfig(getUseKeyConfigFlag(flags) & SakerNativeWatchKey.MASK_QUERY, normalizedpath);
		Lock rlock = serviceLock.readLock();
		rlock.lock();
		try {
			long nativeservice = this.nativeServicePtr;
			if (nativeservice == 0) {
				throw new ClosedWatchServiceException();
			}
			SakerNativeWatchKey nativekey = nativeKeys.get(config);
			SakerUserWatchKey result = addUserWatchKeyToNativeKey(nativekey, path, flags, nativeservice);
			if (result != null) {
				return result;
			}
			synchronized (nativeKeyComputationLocks.computeIfAbsent(config, Functionals.objectComputer())) {
				SakerNativeWatchKey npresentnativekey = nativeKeys.get(config);
				if (npresentnativekey != nativekey) {
					//don't try to add twice if it stayed the same
					result = addUserWatchKeyToNativeKey(npresentnativekey, path, flags, nativeservice);
					if (result != null) {
						return result;
					}
				}
				nativekey = new SakerNativeWatchKey(this, config);
				result = new SakerUserWatchKey(this, nativekey, path, flags);

				String pathstruse = getPathForNative(normalizedpath.toString());
				long createdkey = createKeyObject(nativeservice, pathstruse, flags, nativekey);
				if (createdkey == 0) {
					throw new IOException("Failed to register path at: " + path);
				}
				nativekey.initNative(createdkey, result);
				nativeKeys.put(config, nativekey);
				return result;
			}
		} finally {
			rlock.unlock();
		}
	}

	protected int getUseKeyConfigFlag(int flags) {
		return flags;
	}

	protected String getPathForNative(String path) {
		return path;
	}

	//called by subclasses
	protected static void dispatchEvent(SakerNativeWatchKey key, int eventflag, String path) {
		SimpleWatchEvent<?> event;
		switch (eventflag) {
			case SakerNativeWatchKey.FLAG_EVENT_CREATE: {
				event = new SimpleWatchEvent<>(StandardWatchEventKinds.ENTRY_CREATE, 1, Paths.get(path));
				break;
			}
			case SakerNativeWatchKey.FLAG_EVENT_MODIFY: {
				event = new SimpleWatchEvent<>(StandardWatchEventKinds.ENTRY_MODIFY, 1, Paths.get(path));
				break;
			}
			case SakerNativeWatchKey.FLAG_EVENT_DELETE: {
				event = new SimpleWatchEvent<>(StandardWatchEventKinds.ENTRY_DELETE, 1, Paths.get(path));
				break;
			}
			case SakerNativeWatchKey.FLAG_EVENT_OVERFLOW: {
				event = new SimpleWatchEvent<>(StandardWatchEventKinds.OVERFLOW, 1, null);
				break;
			}
			default: {
				throw new IllegalArgumentException("Unknown event flag: " + eventflag);
			}
		}
		key.postEvent(event, eventflag);
	}

	boolean isValidKey(SakerNativeWatchKey key) {
		//no need to lock on the service lock, as that will lock on the keys when closing
		//    see resetKey(SakerNativeWatchKey)
		Lock rlock = key.keyLock.readLock();
		rlock.lock();
		try {
			long serviceptr = nativeServicePtr;
			if (serviceptr == 0) {
				//already closed
				return false;
			}
			long ptr = key.nativePtr;
			if (ptr == 0) {
				return false;
			}
			return keyIsValid(serviceptr, ptr);
		} finally {
			rlock.unlock();
		}
	}

	boolean resetKey(SakerNativeWatchKey key) {
		//no need to lock on the service lock, as that will lock on the keys when closing
		//if the key ptr is non null, then we can be sure that the service hasn't closed it,
		//therefore the service hasn't closed themself yet, therefore it will lock on the key lock
		//therefore, if we only lock on the key lock, and see that the pointer is non null
		//then we can be sure that the service won't be closed until the key write lock is acquired
		Lock rlock = key.keyLock.readLock();
		rlock.lock();
		try {
			long ptr = key.nativePtr;
			if (ptr == 0) {
				return false;
			}
			long serviceptr = nativeServicePtr;
			if (serviceptr == 0) {
				//already closed
				return false;
			}
			return keyIsValid(serviceptr, ptr);
		} finally {
			rlock.unlock();
		}
	}

	void cancelUserKey(SakerUserWatchKey key, SakerNativeWatchKey nativekey) {
		nativekey.removeAndCloseIfEmpty(key);
		synchronized (signaledKeysLock) {
			signaledKeys.remove(key);
		}
	}

	void enqueue(SakerUserWatchKey key) {
		synchronized (signaledKeysLock) {
			if (signaledKeys.add(key)) {
				signaledKeysLock.notify();
			}
		}
	}

	void pollKeyReadLocked(long nativekey) {
		//no need to lock on the service lock
		//    see resetKey(SakerNativeWatchKey)
		long serviceptr = nativeServicePtr;
		if (serviceptr == 0) {
			return;
		}
		pollKey(serviceptr, nativekey);
	}

	void closeKey(SakerNativeWatchKey key) {
		Lock rlock = serviceLock.readLock();
		rlock.lock();
		try {
			long serviceptr = nativeServicePtr;
			if (serviceptr == 0) {
				return;
			}
			closeNativeKeyServiceLocked(key, serviceptr);
		} finally {
			rlock.unlock();
		}
	}

	private void closeNativeKeyServiceLocked(SakerNativeWatchKey key, long nativeservice) {
		Lock keylock = key.keyLock.writeLock();
		keylock.lock();
		try {
			long keyptr = key.nativePtr;
			if (keyptr == 0) {
				return;
			}
			boolean removed = nativeKeys.remove(key.getConfig(), key);
			if (!removed) {
				throw new IllegalStateException("Failed to remove key.");
			}
			key.nativePtr = 0;
			closeKey(nativeservice, keyptr);
		} finally {
			keylock.unlock();
		}
	}

	protected abstract void closeWatcher(long nativeservice);

	protected abstract long createKeyObject(long nativeservice, String path, int flags, SakerNativeWatchKey key)
			throws IOException;

	protected abstract void closeKey(long nativeservice, long nativekey);

	protected abstract void pollKey(long nativeservice, long nativekey);

	protected abstract boolean keyIsValid(long nativeservice, long nativekey);
}
