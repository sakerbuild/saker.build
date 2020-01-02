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

import saker.build.task.exception.InnerTaskInitializationException;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Interface for providing access to the results of inner task executions.
 * <p>
 * The interface allows access to the task results by basically working like an iterator. The results can be retrieved
 * by calling {@link #getNext()}, which will wait if necessary and return non-<code>null</code> if there is an inner
 * task result available. This interface works like an iterator, but doesn't have <code>next()</code> function, as the
 * checking and retrieving the next result needs to be atomic in the context of multiple consumers.
 * <p>
 * The interface can hold any amount of task results. It is possible, that it never returns any inner task results.
 * <p>
 * The interface provides functionality for cancelling the duplication of the inner tasks.
 * 
 * @param <R>
 *            The type of the task results.
 * @see TaskContext#startInnerTask(TaskFactory, InnerTaskExecutionParameters)
 */
public interface InnerTaskResults<R> {
	/**
	 * Gets the results of a finished inner task.
	 * <p>
	 * This method will check if there is any unretrieved inner task result available, and if so, returns immediately.
	 * If not, then it will wait until one of the inner tasks finish. If there are no more inner tasks running,
	 * <code>null</code> is returned.
	 * <p>
	 * Once this method returns <code>null</code>, all further calls to it will return <code>null</code>.
	 * <p>
	 * If an inner task throws an exception, this method will not propagate it, but it is available through the returned
	 * object.
	 * 
	 * @return The result holder of a completed inner task or <code>null</code> if there are no more results.
	 * @throws InterruptedException
	 *             If the current thread is interrupted while it is waiting for the result of a task.
	 * @throws InnerTaskInitializationException
	 *             If the inner task failed to start on the execution environments. This exception may be thrown in a
	 *             delayed manner, and not directly by
	 *             {@link TaskContext#startInnerTask(TaskFactory, InnerTaskExecutionParameters)}.
	 */
	@RMISerialize
	public InnerTaskResultHolder<R> getNext() throws InterruptedException, InnerTaskInitializationException;

	/**
	 * Manually cancels the duplication of the associated inner task.
	 * <p>
	 * This method will attempt to cancel the duplication of the started inner task. If duplication cancellation is not
	 * enabled or all the tasks have finished, this method is a no-op.
	 * <p>
	 * When the duplication is cancelled, all already running inner tasks will run to completion, and no more will be
	 * started via duplication.
	 * 
	 * @see InnerTaskExecutionParameters#isDuplicationCancellable()
	 */
	public void cancelDuplicationOptionally();
}
