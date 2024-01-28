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
package saker.build.internal.scripting.language.task.operators;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.exc.SakerScriptEvaluationException;
import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerScriptTaskUtils;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskObjectSakerTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

public class TernaryTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	protected SakerTaskFactory conditionTask;
	protected SakerTaskFactory trueTask;
	protected SakerTaskFactory falseTask;

	public TernaryTaskFactory() {
	}

	private TernaryTaskFactory(SakerTaskFactory conditionTask, SakerTaskFactory trueTask, SakerTaskFactory falseTask) {
		this.conditionTask = conditionTask;
		this.trueTask = trueTask;
		this.falseTask = falseTask;
	}

	public static SakerTaskFactory create(SakerTaskFactory conditionTask, SakerTaskFactory trueTask,
			SakerTaskFactory falseTask) {
		if (conditionTask instanceof SakerLiteralTaskFactory) {
			if (SakerScriptTaskUtils.getConditionValue(((SakerLiteralTaskFactory) conditionTask).getValue())) {
				return trueTask;
			}
			return falseTask;
		}
		return new TernaryTaskFactory(conditionTask, trueTask, falseTask);
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		SakerScriptTaskIdentifier currenttaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		TaskIdentifier conditiontaskid = conditionTask.createSubTaskIdentifier(currenttaskid);
		Object conditionval;
		try {
			conditionval = runForResultObject(taskcontext, conditiontaskid, conditionTask);
		} catch (TaskExecutionFailedException | SakerScriptEvaluationException e) {
			throw new OperandExecutionException("Condition failed to evaluate.", e, conditiontaskid);
		}
		boolean branch = SakerScriptTaskUtils.getConditionValue(conditionval);
		SakerTaskFactory resulttf;
		if (branch) {
			resulttf = trueTask;
		} else {
			resulttf = falseTask;
		}
		TaskIdentifier resulttaskid = resulttf.createSubTaskIdentifier(currenttaskid);
		taskcontext.getTaskUtilities().startTask(resulttaskid, resulttf);
		SakerTaskResult result = new SakerTaskObjectSakerTaskResult(resulttaskid);
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(conditionTask);
		out.writeObject(trueTask);
		out.writeObject(falseTask);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		conditionTask = (SakerTaskFactory) in.readObject();
		trueTask = (SakerTaskFactory) in.readObject();
		falseTask = (SakerTaskFactory) in.readObject();
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return create(cloneHelper(taskfactoryreplacements, conditionTask),
				cloneHelper(taskfactoryreplacements, trueTask), cloneHelper(taskfactoryreplacements, falseTask));
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		SakerLiteralTaskFactory c = conditionTask.tryConstantize();
		if (c == null) {
			return null;
		}
		SakerLiteralTaskFactory res;
		if (SakerScriptTaskUtils.getConditionValue(c.getValue())) {
			res = trueTask.tryConstantize();
		} else {
			res = falseTask.tryConstantize();
		}
		if (res == null) {
			return null;
		}
		return res;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((conditionTask == null) ? 0 : conditionTask.hashCode());
		result = prime * result + ((falseTask == null) ? 0 : falseTask.hashCode());
		result = prime * result + ((trueTask == null) ? 0 : trueTask.hashCode());
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
		TernaryTaskFactory other = (TernaryTaskFactory) obj;
		if (conditionTask == null) {
			if (other.conditionTask != null)
				return false;
		} else if (!conditionTask.equals(other.conditionTask))
			return false;
		if (falseTask == null) {
			if (other.falseTask != null)
				return false;
		} else if (!falseTask.equals(other.falseTask))
			return false;
		if (trueTask == null) {
			if (other.trueTask != null)
				return false;
		} else if (!trueTask.equals(other.trueTask))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(ternary:(" + conditionTask + " ? " + trueTask + " : " + falseTask + ")";
	}

}
