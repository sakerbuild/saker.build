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
package saker.build.util.cache;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;

/**
 * The caching logic implementation for {@link SakerEnvironment}.
 * 
 * @see SakerEnvironment#getCachedData(CacheKey).
 */
public class SakerDataCache implements Closeable {
	//1 min
	private static final long EXPIRY_RECHECK_INTERVAL_MILLIS = 1 * DateUtils.MS_PER_MINUTE;

	private interface CommonReference<DataType, ResourceType> {
		public ResourceType getResource();

		public void freeResource() throws Exception;

		public CacheEntry<DataType, ResourceType> getEntry();
	}

	private static class ResourceSoftReference<DataType, ResourceType> extends SoftReference<DataType>
			implements CommonReference<DataType, ResourceType> {
		private CacheEntry<DataType, ResourceType> entry;

		public ResourceSoftReference(DataType datareferent, ReferenceQueue<? super DataType> q,
				CacheEntry<DataType, ResourceType> key) {
			super(datareferent, q);
			this.entry = key;
		}

		@Override
		public ResourceType getResource() {
			return entry.resource;
		}

		@Override
		public void freeResource() throws Exception {
			entry.key.close(get(), entry.resource);
		}

		@Override
		public CacheEntry<DataType, ResourceType> getEntry() {
			return entry;
		}
	}

	private static class ResourceWeakReference<DataType, ResourceType> extends WeakReference<DataType>
			implements CommonReference<DataType, ResourceType> {
		private CacheEntry<DataType, ResourceType> entry;

		public ResourceWeakReference(DataType datareferent, ReferenceQueue<? super DataType> q,
				CacheEntry<DataType, ResourceType> key) {
			super(datareferent, q);
			this.entry = key;
		}

		@Override
		public ResourceType getResource() {
			return entry.resource;
		}

		@Override
		public void freeResource() throws Exception {
			entry.key.close(get(), entry.resource);
		}

		@Override
		public CacheEntry<DataType, ResourceType> getEntry() {
			return entry;
		}
	}

	private static class CacheEntry<DataType, ResourceType> {
		private Reference<DataType> dataRef;
		private Long expiryMillis;
		private final CacheKey<DataType, ResourceType> key;
		private ResourceType resource;

		public CacheEntry(CacheKey<DataType, ResourceType> key) {
			this.key = key;
		}

		public boolean isInvalidated() {
			return isConstructed() && resource == null;
		}

		public void invalidate() {
			dataRef = null;
		}

		public boolean isConstructed() {
			return dataRef != null;
		}

		public ResourceType getResource() {
			return resource;
		}

		public DataType getData() {
			return dataRef.get();
		}

	}

	private ConcurrentHashMap<CacheKey<?, ?>, CacheEntry<?, ?>> entries = new ConcurrentHashMap<>();

	private ReferenceQueue<Object> queue = new ReferenceQueue<>();

	private volatile Thread removingThread;

	private ThreadGroup removingThreadGroup;

	public SakerDataCache(ThreadGroup threadgroup) {
		this.removingThreadGroup = threadgroup;
		init(threadgroup);
	}

	private void init(ThreadGroup threadgroup) {
		//this function may be called by others as well
		//call it in a priviliged context so there no reference leaks in the thread
		AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
			removingThread = new Thread(threadgroup, this::runQueueRemoving, "Cache GC");
			removingThread.setContextClassLoader(null);
			removingThread.setDaemon(true);
			removingThread.start();
			return null;
		});
	}

	public <D, R> void invalidate(CacheKey<D, R> key) {
		@SuppressWarnings("unchecked")
		CacheEntry<D, R> entry = (CacheEntry<D, R>) entries.remove(key);
		if (entry != null) {
			synchronized (entry) {
				R entryres = entry.getResource();
				if (entryres != null) {
					try {
						key.close(entry.getData(), entryres);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked", "cast" })
	public void invalidateIf(Predicate<? super CacheKey<?, ?>> keypredicate) {
		for (Iterator<Entry<CacheKey<?, ?>, CacheEntry<?, ?>>> it = entries.entrySet().iterator(); it.hasNext();) {
			Entry<CacheKey<?, ?>, CacheEntry<?, ?>> cacheentry = it.next();
			CacheKey key = cacheentry.getKey();
			//the cast is required in the following line, else javac throws an error for it. Eclipse warns about unnecessary cast, but leave it.
			if (keypredicate.test((CacheKey<?, ?>) key)) {
				it.remove();
				CacheEntry entry = cacheentry.getValue();
				if (entry != null) {
					synchronized (entry) {
						if (entry.resource != null) {
							try {
								key.close(entry.getData(), entry.getResource());
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	public <DataType, ResourceType> DataType get(CacheKey<DataType, ResourceType> key) throws Exception {
		//XXX could use compute...() functions instead of put and get in map accesses?
		CacheEntry<DataType, ResourceType> entry = new CacheEntry<>(key);
		CacheEntry<DataType, ResourceType> prev = (CacheEntry<DataType, ResourceType>) entries.putIfAbsent(key, entry);
		if (prev != null) {
			entry = prev;
		}
		while (true) {
			synchronized (entry) {
				if (entry.isInvalidated()) {
					CacheEntry<DataType, ResourceType> nentry = new CacheEntry<>(key);
					if (entries.replace(key, entry, nentry)) {
						entry = nentry;
					} else {
						entry = (CacheEntry<DataType, ResourceType>) entries.get(key);
						if (entry == null) {
							prev = (CacheEntry<DataType, ResourceType>) entries.putIfAbsent(key, nentry);
							if (prev != null) {
								entry = prev;
							} else {
								entry = nentry;
							}
						}
					}
					continue;
				}

				long keyexpiry = Math.max(key.getExpiry(), 0);

				DataType val;
				if (entry.resource == null) {
					//intialize entry
					ResourceType res = null;
					try {
						res = allocateCheckResource(key);
						DataType data = generateCheckData(key, res);
						entry.dataRef = createDataReference(entry, data, keyexpiry);
						entry.resource = res;
						val = data;
					} catch (Exception e) {
						//failed to allocate resource or generate data
						if (res != null) {
							try {
								key.close(null, res);
							} catch (Exception ce) {
								ce.addSuppressed(e);
								entries.remove(key, entry);
								throw ce;
							}
						}
						entries.remove(key, entry);
						throw e;
					}
				} else if ((val = entry.dataRef.get()) == null) {
					//not yet invalidated, but data was deallocated
					ResourceType res = entry.getResource();
					try {
						DataType data = generateCheckData(key, res);
						entry.dataRef = createDataReference(entry, data, keyexpiry);
						return data;
					} catch (Exception e) {
						//only throw, resource is deallocated in the thread
						throw e;
					}
				} else {
					//validate the data
					boolean validdata = key.validate(val, entry.resource);
					if (!validdata) {
						ResourceType res = entry.resource;
						boolean validres = key.validate(null, res);
						if (!validres) {
							try {
								res = allocateCheckResource(key);
								entry.resource = res;
							} catch (Exception e) {
								//failed to allocate data
								entries.remove(key, entry);
								throw e;
							}
						}
						try {
							DataType data = generateCheckData(key, res);
							val = data;
						} catch (Exception e) {
							//failed to generate data
							try {
								key.close(null, res);
							} catch (Exception ce) {
								ce.addSuppressed(e);
								entries.remove(key, entry);
								throw ce;
							}
						}
						entry.dataRef = createDataReference(entry, val, keyexpiry);
					} else if (entry.dataRef instanceof WeakReference) {
						//if the key expiry is less or eq than 0, then dont use soft references at all.
						if (keyexpiry > 0) {
							//reapply the soft reference if it was weak 
							entry.dataRef = new ResourceSoftReference<>(val, queue, entry);
						}
					}
				}
				//update the expiry date
				long nanos = System.nanoTime();
				entry.expiryMillis = nanos / 1_000_000 + keyexpiry;
				return val;
			}
		}
	}

	private static <DataType, ResourceType> DataType generateCheckData(CacheKey<DataType, ResourceType> key,
			ResourceType res) throws Exception {
		DataType data = key.generate(res);
		if (data == null) {
			throw new InvalidCacheKeyImplementationException("Generated data is null by: " + key);
		}
		if (data == res) {
			throw new InvalidCacheKeyImplementationException(
					"Generated data and allocated resource identity equals for cache key: " + key + " with data: "
							+ data);
		}
		return data;
	}

	private static <DataType, ResourceType> ResourceType allocateCheckResource(CacheKey<DataType, ResourceType> key)
			throws Exception {
		ResourceType res = key.allocate();
		if (res == null) {
			throw new InvalidCacheKeyImplementationException("Allocated resource is null by: " + key);
		}
		return res;
	}

	public <DataType, ResourceType> Reference<DataType> createDataReference(CacheEntry<DataType, ResourceType> entry,
			DataType val, long keyexpiry) {
		return keyexpiry <= 0 ? new ResourceWeakReference<>(val, queue, entry)
				: new ResourceSoftReference<>(val, queue, entry);
	}

	//TODO validate the mechanics in this class
	//garbage collection, closing, and instantiation might be bad

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void runQueueRemoving() {
		try {
			long lastcheck = System.nanoTime() / 1_000_000;
			//TODO wait the least amount of time until something happens.
			long nextcheckmillis = lastcheck + EXPIRY_RECHECK_INTERVAL_MILLIS;
			while (true) {
				long millis = System.nanoTime() / 1_000_000;
				Reference<?> removed;
				long towait = nextcheckmillis - millis;
				if (towait <= 0) {
					removed = queue.poll();
				} else {
					removed = queue.remove(towait);
				}
				remove_handler:
				if (removed != null) {
					CommonReference rpr = (CommonReference) removed;
					CacheEntry entry = rpr.getEntry();
					synchronized (entry) {
						if (entry.dataRef != rpr) {
							//dont close the resource as it is being used by a new data
							break remove_handler;
						}
						entry.invalidate();
					}
					entries.remove(entry.key, entry);
					try {
						rpr.freeResource();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				//else timed out
				millis = System.nanoTime() / 1_000_000;
				// recheck expirations
				// set references to weak ones if they expire
				if (millis - nextcheckmillis >= 0) {
					for (Iterator<Entry<CacheKey<?, ?>, CacheEntry<?, ?>>> it = entries.entrySet().iterator(); it
							.hasNext();) {
						Entry<CacheKey<?, ?>, CacheEntry<?, ?>> entry = it.next();
						CacheEntry ce = entry.getValue();
						synchronized (ce) {
							if (!ce.isConstructed() || ce.isInvalidated()) {
								//not yet finished constructing
								continue;
							}
							Object value = ce.dataRef.get();
							if (value == null) {
								it.remove();
								ce.invalidate();
								continue;
							}
							long expiry = ce.expiryMillis;
							if (millis - expiry >= 0) {
								//use subtraction instead of greater than because of signed overflow
								ce.dataRef = new ResourceWeakReference<>(value, queue, ce);
							}
						}
					}
					nextcheckmillis = System.nanoTime() / 1_000_000 + EXPIRY_RECHECK_INTERVAL_MILLIS;
				}
			}
		} catch (InterruptedException e) {
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void close() {
		Thread t = removingThread;
		if (t == null) {
			return;
		}
		t.interrupt();
		IOException exc = null;
		try {
			t.join();
			removingThread = null;
		} catch (InterruptedException e) {
			exc = IOUtils.addExc(exc, e);
		}
		while (true) {
			CommonReference<?, ?> polled = (CommonReference<?, ?>) queue.poll();
			if (polled == null) {
				break;
			}
			try {
				polled.freeResource();
			} catch (Exception e) {
				exc = IOUtils.addExc(exc, e);
			}
		}
		for (CacheEntry ce : entries.values()) {
			Object res = ce.getResource();
			if (res != null) {
				try {
					ce.key.close(ce.getData(), res);
				} catch (Exception e) {
					exc = IOUtils.addExc(exc, e);
				}
			}
		}
		entries.clear();
		//do not throw
		IOUtils.printExc(exc);
	}

	public void clear() {
		Thread t = removingThread;
		if (t == null) {
			//already closed
			return;
		}
		//closing is fine, it doesnt throw, shuts down the thread, and closes the resources
		//reinitialize after
		close();
		init(removingThreadGroup);
	}
}
