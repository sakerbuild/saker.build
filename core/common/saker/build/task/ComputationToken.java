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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BooleanSupplier;

import saker.apiextract.api.ExcludeApi;
import saker.build.meta.PropertyNames;
import saker.build.runtime.execution.SakerLog;
import saker.build.thirdparty.saker.util.ObjectUtils;
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
	private static final Set<Object> priorityAllocators = ObjectUtils.newIdentityHashSet();
	private static final Object allocationLock = new Object();

	@ExcludeApi
	public static void wakeUpWaiters(Object allocator) {
		//TODO wake up only waiters for the given allocator
		synchronized (allocationLock) {
			allocationLock.notifyAll();
		}
	}

	@ExcludeApi
	public static ComputationToken request(Object allocator, final int count) throws InterruptedException {
		if (count <= 0) {
			return new ComputationToken(allocator, 0);
		}
		boolean priorityadded = false;
		synchronized (allocationLock) {
			try {
				while (true) {
					Integer prev = allocatedTokens.computeIfPresent(allocator, (a, allocated) -> allocated + count);
					if (prev != null) {
						//there was already tokens allocated to the given allocator
						return new ComputationToken(allocator, count);
					}
					int sum = getAllocatedCount();
					//not yet allocated any tokens to the allocator
					if (sum < MAX_TOKEN_COUNT) {
						//we can allocate the given amount
						//sum + count > MAX_TOKEN_COUNT is acceptable as we prefer overallocation over underutilization
						allocatedTokens.putIfAbsent(allocator, count);
						return new ComputationToken(allocator, count);
					}
					//sum is already equals or greater than the max token count, wait for the deallocation to occur
					if (!priorityadded) {
						priorityAllocators.add(allocator);
						priorityadded = true;
					}
					allocationLock.wait();
				}
			} finally {
				if (priorityadded) {
					if (priorityAllocators.remove(allocator)) {
						allocationLock.notifyAll();
					}
				}
			}
		}
	}

	@ExcludeApi
	public static ComputationToken requestAbortable(Object allocator, final int count, BooleanSupplier abortedsupplier)
			throws InterruptedException {
		if (count <= 0) {
			return new ComputationToken(allocator, 0);
		}
		boolean priorityadded = false;
		synchronized (allocationLock) {
			try {
				while (true) {
					Integer prev = allocatedTokens.computeIfPresent(allocator, (a, allocated) -> allocated + count);
					if (prev != null) {
						//there was already tokens allocated to the given allocator
						return new ComputationToken(allocator, count);
					}
					if (abortedsupplier.getAsBoolean()) {
						return null;
					}
					int sum = getAllocatedCount();
					//not yet allocated any tokens to the allocator
					if (sum < MAX_TOKEN_COUNT) {
						//we can allocate the given amount
						//sum + count > MAX_TOKEN_COUNT is acceptable as we prefer overallocation over underutilization
						allocatedTokens.putIfAbsent(allocator, count);
						return new ComputationToken(allocator, count);
					}
					//sum is already equals or greater than the max token count, wait for the deallocation to occur
					if (!priorityadded) {
						priorityAllocators.add(allocator);
						priorityadded = true;
					}
					allocationLock.wait();
				}
			} finally {
				if (priorityadded) {
					if (priorityAllocators.remove(allocator)) {
						allocationLock.notifyAll();
					}
				}
			}
		}
	}

	@ExcludeApi
	public static ComputationToken requestAdditionalAbortable(Object allocator, final int count,
			BooleanSupplier abortedsupplier) throws InterruptedException {
		if (count <= 0) {
			return new ComputationToken(allocator, 0);
		}
		synchronized (allocationLock) {
			while (true) {
				if (abortedsupplier.getAsBoolean()) {
					return null;
				}
				if (priorityAllocators.isEmpty()) {
					int sum = getAllocatedCount();
					//not yet allocated any tokens to the allocator
					if (sum < MAX_TOKEN_COUNT) {
						//we can allocate the given amount
						//sum + count > MAX_TOKEN_COUNT is acceptable as we prefer overallocation over underutilization
						allocatedTokens.compute(allocator, (k, v) -> v == null ? count : v + count);
						return new ComputationToken(allocator, count);
					}
					//sum is already equals or greater than the max token count, wait for the deallocation to occur
				}
				//else there are priority allocators present. let them allocate before us
				allocationLock.wait();
			}
		}
	}

	private static int getAllocatedCount() {
		int sum = 0;
		for (Integer c : allocatedTokens.values()) {
			sum += c.intValue();
		}
		return sum;
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
			deallocateLocked(allocator, count);
		}
	}

	private static void deallocateLocked(Object allocator, int count) {
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

	public boolean releaseIfOverAllocated() {
		if (allocated == 0) {
			this.allocator = null;
			return true;
		}
		if (allocator == null) {
			return true;
		}
		synchronized (allocationLock) {
			int sum = getAllocatedCount();
			if (sum - MAX_TOKEN_COUNT >= allocated || !priorityAllocators.isEmpty()) {
				//deallocate ourselves if
				//  the overallocation is at least as much as our allocated token count
				//  or there are more important waiters for the tokens
				Object allocator = ARFU_allocator.getAndSet(this, null);
				if (allocator != null) {
					deallocateLocked(allocator, allocated);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public void close() {
		Object allocator = ARFU_allocator.getAndSet(this, null);
		if (allocator != null) {
			deallocate(allocator, allocated);
		}
	}

	@Override
	public String toString() {
		return "ComputationToken[" + (allocator != null ? "allocator=" + allocator + ", " : "") + "allocated="
				+ allocated + "]";
	}
}
