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

import saker.build.task.InternalTaskDependencyFuture;
import saker.build.task.TaskDependencyFuture;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.exception.TaskResultWaitingFailedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.function.LazySupplier;

final class ClusterTaskDependencyFuture<R> implements TaskDependencyFuture<R> {
	private TaskIdentifier taskId;
	private ClusterTaskContext clusterTaskContext;
	private LazySupplier<TaskDependencyFuture<R>> dependencyFuture;

	@SuppressWarnings("unchecked")
	public ClusterTaskDependencyFuture(TaskIdentifier taskId, ClusterTaskContext clustertaskcontext) {
		this.taskId = taskId;
		this.dependencyFuture = LazySupplier
				.of(() -> (TaskDependencyFuture<R>) clustertaskcontext.realTaskContext.getTaskDependencyFuture(taskId));
		this.clusterTaskContext = clustertaskcontext;
	}

	public ClusterTaskDependencyFuture(ClusterTaskFuture<R> future) {
		this.taskId = future.getTaskIdentifier();
		this.dependencyFuture = LazySupplier.of(future, ClusterTaskFuture::asDependencyFuture);
		this.clusterTaskContext = future.getTaskContext();
	}

	private ClusterTaskDependencyFuture(TaskIdentifier taskId, ClusterTaskContext clusterTaskContext,
			LazySupplier<TaskDependencyFuture<R>> dependencyFuture) {
		this.taskId = taskId;
		this.clusterTaskContext = clusterTaskContext;
		this.dependencyFuture = dependencyFuture;
	}

	@SuppressWarnings("unchecked")
	@Override
	public R get()
			throws TaskResultWaitingFailedException, TaskExecutionFailedException, IllegalTaskOperationException {
		clusterTaskContext.requireCalledOnMainThread(false);
		TaskDependencyFuture<R> future = getActualFuture();
		return ((InternalTaskDependencyFuture<R>) future).internalGetOnTaskThread();
	}

	@Override
	public R getFinished() throws TaskExecutionFailedException, IllegalTaskOperationException {
		return getActualFuture().getFinished();
	}

	@Override
	public void setTaskOutputChangeDetector(TaskOutputChangeDetector outputchangedetector)
			throws IllegalStateException, NullPointerException {
		getActualFuture().setTaskOutputChangeDetector(outputchangedetector);
	}

	@Override
	public Object getModificationStamp() throws IllegalTaskOperationException {
		return getActualFuture().getModificationStamp();
	}

	@Override
	public TaskIdentifier getTaskIdentifier() {
		return taskId;
	}

	@Override
	public ClusterTaskContext getTaskContext() {
		return clusterTaskContext;
	}

	@Override
	public TaskDependencyFuture<R> clone() {
		TaskDependencyFuture<R> computed = dependencyFuture.getIfComputed();
		if (computed != null) {
			return new ClusterTaskDependencyFuture<>(taskId, clusterTaskContext,
					LazySupplier.of(computed, TaskDependencyFuture::clone));
		}
		return new ClusterTaskDependencyFuture<>(taskId, clusterTaskContext);
	}

	private TaskDependencyFuture<R> getActualFuture() {
		return dependencyFuture.get();
	}
}
