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
	private transient LazySupplier<TaskDependencyFuture<R>> dependencyFuture;

	@SuppressWarnings("unchecked")
	public ClusterTaskDependencyFuture(TaskIdentifier taskId, ClusterTaskContext clustertaskcontext) {
		this.taskId = taskId;
		this.dependencyFuture = LazySupplier
				.of(() -> (TaskDependencyFuture<R>) clusterTaskContext.realTaskContext.getTaskDependencyFuture(taskId));
		this.clusterTaskContext = clustertaskcontext;
	}

	public ClusterTaskDependencyFuture(ClusterTaskFuture<R> future) {
		this.taskId = future.getTaskIdentifier();
		this.dependencyFuture = LazySupplier.of(future::asDependencyFuture);
		this.clusterTaskContext = future.getTaskContext();
	}

	private ClusterTaskDependencyFuture(TaskIdentifier taskId, ClusterTaskContext clusterTaskContext,
			LazySupplier<TaskDependencyFuture<R>> dependencyFuture) {
		this.taskId = taskId;
		this.clusterTaskContext = clusterTaskContext;
		this.dependencyFuture = dependencyFuture;
	}

	@Override
	public R get()
			throws TaskResultWaitingFailedException, TaskExecutionFailedException, IllegalTaskOperationException {
		return dependencyFuture.get().get();
	}

	@Override
	public R getFinished() throws TaskExecutionFailedException, IllegalTaskOperationException {
		return dependencyFuture.get().getFinished();
	}

	@Override
	public void setTaskOutputChangeDetector(TaskOutputChangeDetector outputchangedetector)
			throws IllegalStateException, NullPointerException {
		dependencyFuture.get().setTaskOutputChangeDetector(outputchangedetector);
	}

	@Override
	public Object getModificationStamp() throws IllegalTaskOperationException {
		return dependencyFuture.get().getModificationStamp();
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
			return new ClusterTaskDependencyFuture<>(taskId, clusterTaskContext, LazySupplier.of(computed::clone));
		}
		return new ClusterTaskDependencyFuture<>(taskId, clusterTaskContext);
	}

}
