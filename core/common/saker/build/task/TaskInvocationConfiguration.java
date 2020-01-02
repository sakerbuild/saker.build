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

import java.util.Collections;
import java.util.Set;

import saker.build.task.exception.InvalidTaskInvocationConfigurationException;

public final class TaskInvocationConfiguration {
	private boolean shortTask;
	private boolean remoteDispatchable;
	private boolean cacheable;
	private boolean innerTasksComputationals;
	private int computationTokenCount;
	private TaskExecutionEnvironmentSelector environmentSelector;

	private TaskInvocationConfiguration(TaskExecutionEnvironmentSelector environmentSelector) {
		this.environmentSelector = environmentSelector;
	}

	public static TaskInvocationConfiguration create(TaskFactory<?> taskfactory)
			throws InvalidTaskInvocationConfigurationException {
		Set<String> capabilities = taskfactory.getCapabilities();
		if (capabilities == null) {
			capabilities = Collections.emptyNavigableSet();
		}
		int cptokencount = taskfactory.getRequestedComputationTokenCount();
		if (capabilities.isEmpty() && cptokencount <= 0) {
			return new TaskInvocationConfiguration(taskfactory.getExecutionEnvironmentSelector());
		}
		boolean shorttask = capabilities.contains(TaskFactory.CAPABILITY_SHORT_TASK);
		boolean remotedispatch = capabilities.contains(TaskFactory.CAPABILITY_REMOTE_DISPATCHABLE);
		boolean cacheable = capabilities.contains(TaskFactory.CAPABILITY_CACHEABLE);
		boolean innertaskcomputational = capabilities.contains(TaskFactory.CAPABILITY_INNER_TASKS_COMPUTATIONAL);

		if (shorttask) {
			if (remotedispatch) {
				throw new InvalidTaskInvocationConfigurationException("Short tasks cannot be remote dispatchable.");
			}
			if (cptokencount > 0) {
				throw new InvalidTaskInvocationConfigurationException("Short tasks cannot use computation tokens.");
			}
			if (innertaskcomputational) {
				throw new InvalidTaskInvocationConfigurationException(
						"Short tasks cannot have computational inner tasks.");
			}
		}

		TaskInvocationConfiguration result = new TaskInvocationConfiguration(
				taskfactory.getExecutionEnvironmentSelector());
		result.shortTask = shorttask;
		result.remoteDispatchable = remotedispatch;
		result.cacheable = cacheable;
		result.computationTokenCount = cptokencount;
		result.innerTasksComputationals = innertaskcomputational;
		return result;
	}

	public boolean isShortTask() {
		return shortTask;
	}

	public boolean isRemoteDispatchable() {
		return remoteDispatchable;
	}

	public boolean isCacheable() {
		return cacheable;
	}

	public boolean isInnerTasksComputationals() {
		return innerTasksComputationals;
	}

	public int getComputationTokenCount() {
		return computationTokenCount;
	}

	public TaskExecutionEnvironmentSelector getEnvironmentSelector() {
		return environmentSelector;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (cacheable ? 1231 : 1237);
		result = prime * result + computationTokenCount;
		result = prime * result + ((environmentSelector == null) ? 0 : environmentSelector.hashCode());
		result = prime * result + (innerTasksComputationals ? 1231 : 1237);
		result = prime * result + (remoteDispatchable ? 1231 : 1237);
		result = prime * result + (shortTask ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TaskInvocationConfiguration other = (TaskInvocationConfiguration) obj;
		if (cacheable != other.cacheable)
			return false;
		if (computationTokenCount != other.computationTokenCount)
			return false;
		if (environmentSelector == null) {
			if (other.environmentSelector != null)
				return false;
		} else if (!environmentSelector.equals(other.environmentSelector))
			return false;
		if (innerTasksComputationals != other.innerTasksComputationals)
			return false;
		if (remoteDispatchable != other.remoteDispatchable)
			return false;
		if (shortTask != other.shortTask)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[shortTask=" + shortTask + ", remoteDispatchable=" + remoteDispatchable
				+ ", cacheable=" + cacheable + ", computationTokenCount=" + computationTokenCount + "]";
	}

}