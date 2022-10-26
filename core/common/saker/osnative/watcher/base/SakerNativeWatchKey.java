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
package saker.osnative.watcher.base;

import java.nio.file.WatchEvent;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import saker.build.thirdparty.saker.util.ArrayUtils;

public final class SakerNativeWatchKey {
	private static final SakerUserWatchKey[] EMPTY_USERKEYS_ARRAY = new SakerUserWatchKey[0];

	public static final int FLAG_QUERY_FILE_TREE = 1 << 0;
	public static final int FLAG_EVENT_CREATE = 1 << 1;
	public static final int FLAG_EVENT_MODIFY = 1 << 2;
	public static final int FLAG_EVENT_DELETE = 1 << 3;
	public static final int FLAG_EVENT_OVERFLOW = 1 << 4;

	public static final int MASK_QUERY = FLAG_QUERY_FILE_TREE;
	public static final int MASK_EVENT = FLAG_EVENT_CREATE | FLAG_EVENT_DELETE | FLAG_EVENT_MODIFY;

	protected static final AtomicIntegerFieldUpdater<SakerNativeWatchKey> AIFU_pollId = AtomicIntegerFieldUpdater
			.newUpdater(SakerNativeWatchKey.class, "pollId");

	private static final AtomicReferenceFieldUpdater<SakerNativeWatchKey, SakerUserWatchKey[]> ARFU_userKeys = AtomicReferenceFieldUpdater
			.newUpdater(SakerNativeWatchKey.class, SakerUserWatchKey[].class, "userKeys");

	protected volatile SakerUserWatchKey[] userKeys;

	protected final SakerWatchService service;
	protected final KeyConfig config;
	protected volatile long nativePtr;

	protected int lastPollInvocationId = -1;
	protected volatile int pollId = 0;

	protected final Lock keyReadLock;
	protected final Lock keyWriteLock;
	protected final Object pollLock = new Object();

	protected SakerNativeWatchKey(SakerWatchService service, KeyConfig config) {
		this.service = service;
		this.config = config;

		ReadWriteLock keylock = new ReentrantReadWriteLock();
		keyReadLock = keylock.readLock();
		keyWriteLock = keylock.writeLock();
	}

	public void initNative(long nativeKey, SakerUserWatchKey firstkey) {
		this.nativePtr = nativeKey;
		this.userKeys = new SakerUserWatchKey[] { firstkey };
	}

	public KeyConfig getConfig() {
		return config;
	}

	public boolean isValid() {
		return service.isValidKey(this);
	}

	public void pollEvents() {
		//poll controlling scenario:
		//    last poll invocation id: 0
		//    thread1 arrives with ID: 1
		//    thread1 acquires lock
		//    thread2 arrives and gets ID: 2
		//    thread1 sets the last invocation id to 2
		//    thread1 invokes polling
		//    thread3 arrives and gets ID: 3
		//        2 & 3 can't acquire the lock, so they wait
		//    thread1 finishes with the polling
		//    thread2 acquires the lock, sees that last invocation is 2, skips polling invocation
		//    thread3 invokes polling
		//this mostly improves performance when a lot of user keys try to poll at once
		//e.g.
		//    10 thread arrives, 1 invokes polling
		//    9 thread is waiting, and 1 might need to invoke the polling again, as it arrived a bit late
		//    1 invokes the polling again
		//    8 thread waits, but they can skip, as they were waiting when the last polling was invoked
		//    result: only 2 or less polling was invoked for multiple threads

		int pollid = AIFU_pollId.getAndIncrement(this);
		Lock rlock = keyReadLock;
		rlock.lock();
		try {
			long ptr = this.nativePtr;
			if (ptr == 0) {
				return;
			}
			synchronized (pollLock) {
				//overflow conscious compare
				if (lastPollInvocationId - pollid < 0) {
					//set the id before invocation
					//this ensures that no race condition occurs when the polling is done and setting the id
					//error scenario when id is set after poll invocation:
					//    polling is done, thread gets unscheduled
					//    some file is created, and a poller arrives, takes an ID, starts to wait
					//    the poller thread is rescheduled, the id is set to the id of the newly arrived waiter
					//    the waiter will skip polling and won't receive event for the newly created file.
					lastPollInvocationId = this.pollId;
					service.pollKeyReadLocked(ptr);
				}
			}
		} finally {
			rlock.unlock();
		}
	}

	public boolean reset() {
		return service.resetKey(this);
	}

	void postEvent(WatchEvent<?> event, int eventflag) {
		for (SakerUserWatchKey userkey : userKeys) {
			userkey.postEvent(event, eventflag);
		}
	}

	void removeAndCloseIfEmpty(SakerUserWatchKey key) {
		//remove the key after closing, to receive the remaining events until closing
		while (true) {
			SakerUserWatchKey[] keys = userKeys;
			if (keys.length == 0) {
				return;
			}
			int idx = ArrayUtils.arrayIndexOf(keys, key);
			if (idx < 0) {
				return;
			}
			if (keys.length == 1) {
				//close
				if (!ARFU_userKeys.compareAndSet(this, keys, EMPTY_USERKEYS_ARRAY)) {
					continue;
				}

				service.closeKey(this);
				return;
			}
			SakerUserWatchKey[] narray = ArrayUtils.removedAtIndex(keys, idx);
			if (ARFU_userKeys.compareAndSet(this, keys, narray)) {
				return;
			}
			//try again
			continue;
		}
	}

	boolean addUserKey(SakerUserWatchKey key) {
		while (true) {
			SakerUserWatchKey[] keys = userKeys;
			if (keys.length == 0) {
				return false;
			}
			SakerUserWatchKey[] nkeys = ArrayUtils.appended(keys, key);
			if (ARFU_userKeys.compareAndSet(this, keys, nkeys)) {
				return true;
			}
			//try again
			continue;
		}
	}
}
