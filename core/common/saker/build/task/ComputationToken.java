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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.function.BooleanSupplier;

import saker.apiextract.api.ExcludeApi;
import saker.build.meta.PropertyNames;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.trace.InternalBuildTraceImpl;
import saker.build.util.exc.ExceptionView;
import testing.saker.build.flag.TestFlag;

public class ComputationToken implements AutoCloseable {
	public static final int MAX_TOKEN_COUNT;
	static {
		int val = -1;
		String prop = PropertyNames.getProperty(PropertyNames.PROPERTY_SAKER_COMPUTATION_TOKEN_COUNT);
		if (prop != null) {
			try {
				int parsed;
				try {
					parsed = Integer.parseInt(prop);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Property "
							+ PropertyNames.PROPERTY_SAKER_COMPUTATION_TOKEN_COUNT + " is not an integer: " + prop, e);
				}
				if (parsed > 0) {
					val = parsed;
				} else {
					throw new IllegalArgumentException(
							"Property " + PropertyNames.PROPERTY_SAKER_COMPUTATION_TOKEN_COUNT
									+ " must be a positive non zero integer number. (Current: " + parsed + ")");
				}
			} catch (IllegalArgumentException e) {
				//ignore for build trace
				InternalBuildTraceImpl.ignoredStaticException(ExceptionView.create(e));
			}
		}
		if (val < 0) {
			val = Runtime.getRuntime().availableProcessors() * 3 / 2;
		}
		MAX_TOKEN_COUNT = Math.max(val, 2);
	}

	private static final Map<Object, Integer> allocatedTokens = new IdentityHashMap<>();
	private static final Set<Object> priorityAllocators = ObjectUtils.newIdentityHashSet();
	private static final Lock allocationLock = ThreadUtils.newExclusiveLock();
	private static final Condition allocationCondition = allocationLock.newCondition();

	@ExcludeApi
	public static void wakeUpWaiters(Object allocator) {
		//TODO wake up only waiters for the given allocator
		final Lock lock = allocationLock;
		lock.lock();
		try {
			allocationCondition.signalAll();
		} finally {
			lock.unlock();
		}
	}

	@ExcludeApi
	public static ComputationToken request(Object allocator, final int count) throws InterruptedException {
		if (count <= 0) {
			return new ComputationToken(allocator, 0);
		}
		boolean priorityadded = false;
		final Lock lock = allocationLock;
		lock.lockInterruptibly();
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
				allocationCondition.await();
			}
		} finally {
			if (priorityadded) {
				if (priorityAllocators.remove(allocator)) {
					allocationCondition.signalAll();
				}
			}
			lock.unlock();
		}
	}

	@ExcludeApi
	public static ComputationToken requestAbortable(Object allocator, final int count, BooleanSupplier abortedsupplier)
			throws InterruptedException {
		if (count <= 0) {
			return new ComputationToken(allocator, 0);
		}
		boolean priorityadded = false;
		final Lock lock = allocationLock;
		lock.lockInterruptibly();
		try {
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
					allocationCondition.await();
				}
			} finally {
				if (priorityadded) {
					if (priorityAllocators.remove(allocator)) {
						allocationCondition.signalAll();
					}
				}
			}
		} finally {
			lock.unlock();
		}
	}

	@ExcludeApi
	public static ComputationToken requestAdditionalAbortable(Object allocator, final int count,
			BooleanSupplier abortedsupplier) throws InterruptedException {
		if (count <= 0) {
			return new ComputationToken(allocator, 0);
		}
		final Lock lock = allocationLock;
		lock.lockInterruptibly();
		try {
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
				allocationCondition.await();
			}
		} finally {
			lock.unlock();
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
		final Lock lock = allocationLock;
		lock.lock();
		try {
			allocatedTokens.remove(allocator);
			allocationCondition.signalAll();
		} finally {
			lock.unlock();
		}
	}

	private static void deallocate(Object allocator, int count) {
		if (count <= 0) {
			return;
		}
		final Lock lock = allocationLock;
		lock.lock();
		try {
			deallocateLocked(allocator, count);
		} finally {
			lock.unlock();
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
		allocationCondition.signalAll();
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
		final Lock lock = allocationLock;
		lock.lock();
		try {
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
		} finally {
			lock.unlock();
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
