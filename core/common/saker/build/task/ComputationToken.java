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
package saker.build.task;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import saker.apiextract.api.ExcludeApi;
import saker.build.meta.PropertyNames;
import saker.build.runtime.execution.SakerLog;
import testing.saker.build.flag.TestFlag;

public class ComputationToken implements AutoCloseable {
	public static final int MAX_TOKEN_COUNT;
	static {
		int val = -1;
		String prop = PropertyNames.getProperty(PropertyNames.PROPERTY_SAKER_COMPUTATION_TOKEN_COUNT);
		if (prop != null) {
			try {
				int parsed = Integer.parseInt(prop);
				if (parsed > 0) {
					val = parsed;
				} else {
					SakerLog.warning().out(System.err)
							.println("Property " + PropertyNames.PROPERTY_SAKER_COMPUTATION_TOKEN_COUNT
									+ " must be a positive non zero integer number.");
				}
			} catch (NumberFormatException e) {
				SakerLog.warning().out(System.err).println("Property "
						+ PropertyNames.PROPERTY_SAKER_COMPUTATION_TOKEN_COUNT + " is not an integer: " + prop);
			}
		}
		if (val < 0) {
			val = Runtime.getRuntime().availableProcessors() * 3 / 2;
		}
		MAX_TOKEN_COUNT = Math.max(val, 2);
	}

	private static final Map<Object, Integer> allocatedTokens = new IdentityHashMap<>();
	private static final Object allocationLock = new Object();

	@ExcludeApi
	public static Object getAllocationLock() {
		return allocationLock;
	}

	@ExcludeApi
	public static ComputationToken requestIfAnyAvailableOrAlreadyAllocatedLocked(Object allocator, final int count) {
		if (TestFlag.ENABLED && !Thread.holdsLock(allocationLock)) {
			throw new AssertionError();
		}
		if (count <= 0) {
			return new ComputationToken(allocator, 0);
		}
		Integer prev = allocatedTokens.computeIfPresent(allocator, (a, allocated) -> allocated + count);
		if (prev != null) {
			//there was already tokens allocated to the given allocator
			return new ComputationToken(allocator, count);
		}
		int sum = 0;
		for (Integer c : allocatedTokens.values()) {
			sum += c.intValue();
		}
		//not yet allocated any tokens to the allocator
		if (sum < MAX_TOKEN_COUNT) {
			//we can allocate the given amount
			//sum + count > MAX_TOKEN_COUNT is acceptable as we prefer overallocation over underutilization
			allocatedTokens.putIfAbsent(allocator, count);
			return new ComputationToken(allocator, count);
		}
		//sum is already equals or greater than the max token count, return null as we cannot allocate right now
		return null;
	}

	@ExcludeApi
	public static ComputationToken requestIfAnyAvailableLocked(Object allocator, final int count) {
		if (TestFlag.ENABLED && !Thread.holdsLock(allocationLock)) {
			throw new AssertionError();
		}
		if (count <= 0) {
			return new ComputationToken(allocator, 0);
		}
		int sum = 0;
		for (Integer c : allocatedTokens.values()) {
			sum += c.intValue();
		}
		//not yet allocated any tokens to the allocator
		if (sum < MAX_TOKEN_COUNT) {
			//we can allocate the given amount
			//sum + count > MAX_TOKEN_COUNT is acceptable as we prefer overallocation over underutilization
			allocatedTokens.compute(allocator, (k, v) -> v == null ? count : v + count);
			return new ComputationToken(allocator, count);
		}
		//sum is already equals or greater than the max token count, return null as we cannot allocate right now
		return null;
	}

	@ExcludeApi
	public static ComputationToken request(Object allocator, final int count) throws InterruptedException {
		if (count <= 0) {
			return new ComputationToken(allocator, 0);
		}
		synchronized (allocationLock) {
			while (true) {
				Integer prev = allocatedTokens.computeIfPresent(allocator, (a, allocated) -> allocated + count);
				if (prev != null) {
					//there was already tokens allocated to the given allocator
					return new ComputationToken(allocator, count);
				}
				int sum = 0;
				for (Integer c : allocatedTokens.values()) {
					sum += c.intValue();
				}
				//not yet allocated any tokens to the allocator
				if (sum < MAX_TOKEN_COUNT) {
					//we can allocate the given amount
					//sum + count > MAX_TOKEN_COUNT is acceptable as we prefer overallocation over underutilization
					allocatedTokens.putIfAbsent(allocator, count);
					return new ComputationToken(allocator, count);
				}
				//sum is already equals or greater than the max token count, wait for the deallocation to occur
				allocationLock.wait();
			}
		}
	}

	private static void deallocateAll(Object allocator) {
		synchronized (allocationLock) {
			allocatedTokens.remove(allocator);
			allocationLock.notifyAll();
		}
	}

	private static void deallocate(Object allocator, int count) {
		if (count <= 0) {
			return;
		}
		synchronized (allocationLock) {
			allocatedTokens.computeIfPresent(allocator, (a, allocated) -> {
				int iav = allocated.intValue();
				if (TestFlag.ENABLED) {
					if (iav < count) {
						throw new AssertionError(iav + " - " + count);
					}
				}
				if (iav == count) {
					return null;
				}
				return iav - count;
			});
			allocationLock.notifyAll();
		}
	}

	public static int getMaxTokenCount() {
		return MAX_TOKEN_COUNT;
	}

	private static final AtomicReferenceFieldUpdater<ComputationToken, Object> ARFU_allocator = AtomicReferenceFieldUpdater
			.newUpdater(ComputationToken.class, Object.class, "allocator");

	@SuppressWarnings("unused")
	private volatile Object allocator;
	private final int allocated;

	private ComputationToken(Object allocator, int allocated) {
		this.allocator = allocator;
		this.allocated = allocated;
	}

	public void closeAll() {
		Object allocator = ARFU_allocator.getAndSet(this, null);
		if (allocator != null) {
			if (allocated > 0) {
				deallocateAll(allocator);
			}
		}
	}

	@Override
	public void close() {
		Object allocator = ARFU_allocator.getAndSet(this, null);
		if (allocator != null) {
			deallocate(allocator, allocated);
		}
	}

}
