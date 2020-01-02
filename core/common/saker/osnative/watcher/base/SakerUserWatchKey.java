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

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.Watchable;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ImmutableUtils;

public final class SakerUserWatchKey implements WatchKey {
	protected static final AtomicIntegerFieldUpdater<SakerUserWatchKey> AIFU_enqueuedFlags = AtomicIntegerFieldUpdater
			.newUpdater(SakerUserWatchKey.class, "enqueuedFlags");

	protected static final AtomicReferenceFieldUpdater<SakerUserWatchKey, SakerNativeWatchKey> ARFU_nativeKey = AtomicReferenceFieldUpdater
			.newUpdater(SakerUserWatchKey.class, SakerNativeWatchKey.class, "nativeKey");

	protected final SakerWatchService service;
	protected final Path path;
	protected final int eventFlags;
	protected volatile SakerNativeWatchKey nativeKey;

	protected final ConcurrentPrependAccumulator<WatchEvent<?>> queuedEvents = new ConcurrentPrependAccumulator<>();

	protected static final int FLAG_READY = 1 << 0;
	protected static final int FLAG_ENQUEUED = 1 << 1;

	@SuppressWarnings("unused")
	protected volatile int enqueuedFlags = FLAG_READY;

	protected SakerUserWatchKey(SakerWatchService service, SakerNativeWatchKey nativeKey, Path path, int eventFlags) {
		this.service = service;
		this.nativeKey = nativeKey;
		this.path = path;
		this.eventFlags = eventFlags | SakerNativeWatchKey.FLAG_EVENT_OVERFLOW;
	}

	@Override
	public boolean isValid() {
		SakerNativeWatchKey nativekey = nativeKey;
		if (nativekey == null) {
			return false;
		}
		return nativekey.isValid();
	}

	@Override
	public List<WatchEvent<?>> pollEvents() {
		//if we have queued events, then return those
		//else execute a polling to the native key, and return the received events if any
		Iterator<WatchEvent<?>> it = queuedEvents.clearAndIterator();
		if (it.hasNext()) {
			return createPollEventsResultList(it);
		}
		SakerNativeWatchKey nativekey = nativeKey;
		if (nativekey != null) {
			//if we're not cancelled
			nativekey.pollEvents();
			it = queuedEvents.clearAndIterator();
			if (it.hasNext()) {
				return createPollEventsResultList(it);
			}
		}
		return Collections.emptyList();
	}

	@Override
	public boolean reset() {
		SakerNativeWatchKey nativekey = nativeKey;
		if (nativekey == null) {
			return false;
		}
		boolean successful = nativekey.reset();
		if (successful) {
			AIFU_enqueuedFlags.updateAndGet(this, f -> f | FLAG_READY);
		}
		return successful;
	}

	@Override
	public void cancel() {
		SakerNativeWatchKey nativekey = ARFU_nativeKey.getAndSet(this, null);
		if (nativekey != null) {
			service.cancelUserKey(this, nativekey);
			enqueuedFlags = 0;
		}
	}

	@Override
	public Watchable watchable() {
		return path;
	}

	public boolean isFileTreeWatching() {
		return (eventFlags & SakerNativeWatchKey.FLAG_QUERY_FILE_TREE) == SakerNativeWatchKey.FLAG_QUERY_FILE_TREE;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + path + "]";
	}

	void removedFromQueue() {
		enqueuedFlags = 0;
	}

	void postEvent(WatchEvent<?> event, int eventkindflag) {
		if (((eventFlags & eventkindflag) == eventkindflag)) {
			if (!isFileTreeWatching()) {
				//we are not watching for the whole file tree
				//the key might be file tree watching nonetheless, so it is possible to get events which are for a file that is not a direct child 
				//do not enqueue events for subtree children
				Path path = (Path) event.context();
				if (path != null) {
					if (path.getNameCount() > 1) {
						return;
					}
				}
			}
			//if we're interested in this event
			queuedEvents.add(event);
			if (AIFU_enqueuedFlags.compareAndSet(this, FLAG_READY, FLAG_READY | FLAG_ENQUEUED)) {
				service.enqueue(this);
			}
		}
	}

	private static List<WatchEvent<?>> createPollEventsResultList(Iterator<WatchEvent<?>> it) {
		//the events need to be reversed, as they are accumulated in a reverse order
		WatchEvent<?> first = it.next();
		if (!it.hasNext()) {
			return ImmutableUtils.singletonList(first);
		}
		LinkedList<WatchEvent<?>> result = new LinkedList<>();
		result.add(first);
		do {
			result.addFirst(it.next());
		} while (it.hasNext());
		return result;
	}

}
