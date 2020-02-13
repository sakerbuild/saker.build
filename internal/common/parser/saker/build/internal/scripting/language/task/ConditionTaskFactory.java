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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.exc.SakerScriptEvaluationException;
import saker.build.internal.scripting.language.task.result.NoSakerTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class ConditionTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	protected SakerTaskFactory conditionTask;
	protected List<SakerTaskFactory> trueSubTasks;
	protected List<SakerTaskFactory> falseSubTasks;

	public ConditionTaskFactory() {
	}

	private ConditionTaskFactory(SakerTaskFactory conditionTask, List<SakerTaskFactory> trueSubTasks,
			List<SakerTaskFactory> falseSubTasks) {
		this.conditionTask = conditionTask;
		this.trueSubTasks = trueSubTasks;
		this.falseSubTasks = falseSubTasks;
	}

	public static SakerTaskFactory create(SakerTaskFactory conditionTask, List<SakerTaskFactory> trueSubTasks,
			List<SakerTaskFactory> falseSubTasks) {
		if (conditionTask instanceof SakerLiteralTaskFactory) {
			if (SakerScriptTaskUtils.getConditionValue(((SakerLiteralTaskFactory) conditionTask).getValue())) {
				falseSubTasks = Collections.emptyList();
			} else {
				trueSubTasks = Collections.emptyList();
			}
		}
		return new ConditionTaskFactory(conditionTask, trueSubTasks, falseSubTasks);
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		SakerScriptTaskIdentifier currenttaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		TaskIdentifier conditiontaskid = conditionTask.createSubTaskIdentifier(currenttaskid);

		Object conditionval;
		try {
			conditionval = runForResult(taskcontext, conditiontaskid, conditionTask).toResult(taskcontext);
		} catch (TaskExecutionFailedException | SakerScriptEvaluationException e) {
			throw new OperandExecutionException("Condition failed to evaluate.", e, conditiontaskid);
		}

		boolean branch = SakerScriptTaskUtils.getConditionValue(conditionval);
		List<SakerTaskFactory> tasks = branch ? trueSubTasks : falseSubTasks;
		for (SakerTaskFactory stf : tasks) {
			taskcontext.getTaskUtilities().startTaskFuture(stf.createSubTaskIdentifier(currenttaskid), stf);
		}
		NoSakerTaskResult result = new NoSakerTaskResult(currenttaskid);
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(conditionTask);
		SerialUtils.writeExternalCollection(out, trueSubTasks);
		SerialUtils.writeExternalCollection(out, falseSubTasks);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		conditionTask = (SakerTaskFactory) in.readObject();
		trueSubTasks = SerialUtils.readExternalImmutableList(in);
		falseSubTasks = SerialUtils.readExternalImmutableList(in);
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return create(cloneHelper(taskfactoryreplacements, conditionTask),
				cloneHelper(taskfactoryreplacements, trueSubTasks),
				cloneHelper(taskfactoryreplacements, falseSubTasks));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((conditionTask == null) ? 0 : conditionTask.hashCode());
		result = prime * result + ((falseSubTasks == null) ? 0 : falseSubTasks.hashCode());
		result = prime * result + ((trueSubTasks == null) ? 0 : trueSubTasks.hashCode());
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
		ConditionTaskFactory other = (ConditionTaskFactory) obj;
		if (conditionTask == null) {
			if (other.conditionTask != null)
				return false;
		} else if (!conditionTask.equals(other.conditionTask))
			return false;
		if (falseSubTasks == null) {
			if (other.falseSubTasks != null)
				return false;
		} else if (!falseSubTasks.equals(other.falseSubTasks))
			return false;
		if (trueSubTasks == null) {
			if (other.trueSubTasks != null)
				return false;
		} else if (!trueSubTasks.equals(other.trueSubTasks))
			return false;
		return true;
	}

}
