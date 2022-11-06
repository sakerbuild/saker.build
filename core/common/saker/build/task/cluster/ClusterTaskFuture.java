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
package saker.build.task.cluster;

import java.util.function.Supplier;

import saker.build.task.InternalTaskFuture;
import saker.build.task.TaskContext;
import saker.build.task.TaskDependencyFuture;
import saker.build.task.TaskExecutionManager;
import saker.build.task.TaskFuture;
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.exception.TaskResultWaitingFailedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.LazySupplier;

final class ClusterTaskFuture<R> implements TaskFuture<R> {
	private TaskIdentifier taskId;
	private Supplier<TaskFuture<R>> future;
	private ClusterTaskContext clusterTaskContext;

	@SuppressWarnings("unchecked")
	public ClusterTaskFuture(TaskIdentifier taskId, TaskContext realtaskcontext,
			ClusterTaskContext clustertaskcontext) {
		this.taskId = taskId;
		this.future = LazySupplier.of(() -> (TaskFuture<R>) realtaskcontext.getTaskFuture(taskId));
		this.clusterTaskContext = clustertaskcontext;
	}

	public ClusterTaskFuture(TaskIdentifier taskId, TaskFuture<R> future, ClusterTaskContext clusterTaskContext) {
		this.taskId = taskId;
		this.future = Functionals.valSupplier(future);
		this.clusterTaskContext = clusterTaskContext;
	}

	@Override
	public TaskIdentifier getTaskIdentifier() {
		return taskId;
	}

	@SuppressWarnings("unchecked")
	@Override
	public R get()
			throws TaskResultWaitingFailedException, TaskExecutionFailedException, IllegalTaskOperationException {
		TaskExecutionManager.requireCalledOnTaskThread(clusterTaskContext, false);
		return ((InternalTaskFuture<R>) getActualFuture()).internalGetOnTaskThread();
	}

	@Override
	public R getFinished() throws TaskExecutionFailedException, IllegalTaskOperationException {
		return getActualFuture().getFinished();
	}

	@Override
	public TaskDependencyFuture<R> asDependencyFuture() {
		return new ClusterTaskDependencyFuture<>(this);
	}

	@Override
	public Object getModificationStamp() throws IllegalTaskOperationException {
		return getActualFuture().getModificationStamp();
	}

	private TaskFuture<R> getActualFuture() {
		return future.get();
	}

	@Override
	public ClusterTaskContext getTaskContext() {
		return clusterTaskContext;
	}

	public Supplier<TaskFuture<R>> getActualFutureSupplier() {
		return future;
	}

	public TaskDependencyFuture<R> getActualFutureAsDependencyFuture() {
		return getActualFutureSupplier().get().asDependencyFuture();
	}
}