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
package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SimpleSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.task.TaskFuture;
import saker.build.task.TaskInvocationConfiguration;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ObjectUtils;

public abstract class SakerTaskFactory implements TaskFactory<SakerTaskResult>, Externalizable {
	private static final long serialVersionUID = 1L;

	private transient Object scriptPositionKey = this;

	public abstract SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements);

	public abstract SakerLiteralTaskFactory tryConstantize();

	public TaskIdentifier createSubTaskIdentifier(SakerScriptTaskIdentifier parenttaskidentifier) {
		return createTaskId(parenttaskidentifier, this);
	}

	@Override
	public abstract int hashCode();

	@Override
	public abstract boolean equals(Object obj);

	public final Object getScriptPositionKey() {
		return scriptPositionKey;
	}

	public final void setScriptPositionKey(Object key) {
		this.scriptPositionKey = key;
	}

	// only allow overriding the task invocation configuration using these function
	// so it can be better managed/optimized by the subclasses
	protected boolean isShort() {
		return false;
	}

	@SuppressWarnings("deprecation")
	@Override
	public final Set<String> getCapabilities() {
		return TaskFactory.super.getCapabilities();
	}

	@SuppressWarnings("deprecation")
	@Override
	public final TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
		return TaskFactory.super.getExecutionEnvironmentSelector();
	}

	@SuppressWarnings("deprecation")
	@Override
	public final int getRequestedComputationTokenCount() {
		return TaskFactory.super.getRequestedComputationTokenCount();
	}

	@Override
	public final TaskInvocationConfiguration getInvocationConfiguration() {
		return isShort() ? TaskInvocationConfiguration.INSTANCE_SHORT_TASK
				: TaskInvocationConfiguration.INSTANCE_DEFAULTS;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(scriptPositionKey);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		scriptPositionKey = in.readObject();
	}

	protected static SakerTaskResult runForResult(TaskContext taskcontext, TaskIdentifier taskid,
			SakerTaskFactory taskfactory) {
		if (taskfactory instanceof SakerLiteralTaskFactory) {
			return new SimpleSakerTaskResult<>(((SakerLiteralTaskFactory) taskfactory).getValue());
		}
		return taskcontext.getTaskUtilities().runTaskResult(taskid, taskfactory);
	}

	protected static Object runForResultObject(TaskContext taskcontext, TaskIdentifier taskid,
			SakerTaskFactory taskfactory) {
		if (taskfactory instanceof SakerLiteralTaskFactory) {
			return ((SakerLiteralTaskFactory) taskfactory).getValue();
		}
		return taskcontext.getTaskUtilities().runTaskResult(taskid, taskfactory).toResult(taskcontext);
	}

	protected static TaskFuture<SakerTaskResult> startForFuture(TaskContext taskcontext, TaskIdentifier taskid,
			SakerTaskFactory taskfactory) {
		return taskcontext.getTaskUtilities().startTaskFuture(taskid, taskfactory);
	}

	private static SakerScriptTaskIdentifier createTaskId(SakerScriptTaskIdentifier currenttaskid,
			SakerTaskFactory factory) {
		TaskIdentifier rootid = currenttaskid.getRootIdentifier();
		return new SakerScriptTaskIdentifier(rootid, factory);
	}

	protected static SakerTaskFactory cloneHelper(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements,
			SakerTaskFactory task) {
		if (task == null) {
			return null;
		}
		SakerTaskFactory got = taskfactoryreplacements.get(task);
		if (got != null) {
			return got;
		}
		SakerTaskFactory res = task.clone(taskfactoryreplacements);
		res.scriptPositionKey = task.scriptPositionKey;
		return res;
	}

	protected static List<SakerTaskFactory> cloneHelper(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements,
			List<? extends SakerTaskFactory> tasks) {
		if (ObjectUtils.isNullOrEmpty(tasks)) {
			return new ArrayList<>();
		}
		List<SakerTaskFactory> result = new ArrayList<>(tasks.size());
		for (SakerTaskFactory stf : tasks) {
			result.add(cloneHelper(taskfactoryreplacements, stf));
		}
		return result;
	}

	protected static Set<SakerTaskFactory> cloneHelper(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements,
			Set<? extends SakerTaskFactory> tasks) {
		if (ObjectUtils.isNullOrEmpty(tasks)) {
			return new HashSet<>();
		}
		Set<SakerTaskFactory> result = new HashSet<>();
		for (SakerTaskFactory stf : tasks) {
			result.add(cloneHelper(taskfactoryreplacements, stf));
		}
		return result;
	}

	protected static NavigableMap<String, SakerTaskFactory> cloneHelper(
			Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements,
			Map<String, ? extends SakerTaskFactory> tasks) {
		if (ObjectUtils.isNullOrEmpty(tasks)) {
			return new TreeMap<>();
		}
		NavigableMap<String, SakerTaskFactory> result = new TreeMap<>();
		for (Entry<String, ? extends SakerTaskFactory> entry : tasks.entrySet()) {
			result.put(entry.getKey(), cloneHelper(taskfactoryreplacements, entry.getValue()));
		}
		return result;
	}
}
