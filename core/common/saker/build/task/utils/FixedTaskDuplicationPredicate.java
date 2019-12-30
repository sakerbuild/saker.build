package saker.build.task.utils;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import saker.apiextract.api.PublicApi;
import saker.build.task.TaskDuplicationPredicate;

/**
 * {@link TaskDuplicationPredicate} implemetation that allows the associated task to be duplicated to a fixed number of
 * executions.
 * <p>
 * An instance of this class is constructed by specifying the number of times the associated task can be duplicated. The
 * {@link #shouldInvokeOnceMore()} method will return <code>true</code> excatly the specified number amount of times.
 */
@PublicApi
public class FixedTaskDuplicationPredicate implements TaskDuplicationPredicate {
	private static final AtomicIntegerFieldUpdater<FixedTaskDuplicationPredicate> AIFU_count = AtomicIntegerFieldUpdater
			.newUpdater(FixedTaskDuplicationPredicate.class, "count");
	@SuppressWarnings("unused")
	private volatile int count;

	/**
	 * Creates a new instance with the specified number of duplication.
	 * 
	 * @param count
	 *            The duplication number.
	 * @throws IllegalArgumentException
	 *             If the argument is negative or zero.
	 */
	public FixedTaskDuplicationPredicate(int count) throws IllegalArgumentException {
		if (count <= 0) {
			throw new IllegalArgumentException("Invalid duplication count: " + count);
		}
		this.count = count;
	}

	@Override
	public boolean shouldInvokeOnceMore() throws RuntimeException {
		return AIFU_count.getAndDecrement(this) > 0;
	}

}
