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

import java.io.Externalizable;
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
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SimpleSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.TaskIdentifier;

public class BoolNegateTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	private SakerTaskFactory subject;

	/**
	 * For {@link Externalizable}.
	 */
	public BoolNegateTaskFactory() {
	}

	public BoolNegateTaskFactory(SakerTaskFactory subject) {
		this.subject = subject;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		TaskIdentifier subtaskid = subject.createSubTaskIdentifier((SakerScriptTaskIdentifier) taskcontext.getTaskId());
		Object subjectres;
		try {
			subjectres = runForResult(taskcontext, subtaskid, subject).toResult(taskcontext);
		} catch (TaskExecutionFailedException | SakerScriptEvaluationException e) {
			throw new OperandExecutionException("Operand failed to evaluate.", e, subtaskid);
		}
//		if (subjectres == null) {
//			throw new OperandExecutionException("Operand evaluated to null.", subtaskid);
//		}
//		if (!(subjectres instanceof Boolean)) {
//			throw new OperandExecutionException("Operand is not a boolean. (" + subjectres.getClass().getName() + ")", subtaskid);
//		}
//		boolean result = negateImpl((Boolean) subjectres);
		return new SimpleSakerTaskResult<>(!SakerScriptTaskUtils.getConditionValue(subjectres));
	}

	private static boolean negateImpl(Boolean subjectres) {
		return !subjectres.booleanValue();
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		SakerLiteralTaskFactory sub = subject.tryConstantize();
		if (sub == null) {
			return null;
		}
		Object subjectres = sub.getValue();
		if (subjectres == null) {
			return null;
		}
		if (!(subjectres instanceof Boolean)) {
			return null;
		}
		boolean negated = negateImpl((Boolean) subjectres);
		return new SakerLiteralTaskFactory(negated);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(subject);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		subject = (SakerTaskFactory) in.readObject();
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new BoolNegateTaskFactory(cloneHelper(taskfactoryreplacements, subject));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((subject == null) ? 0 : subject.hashCode());
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
		BoolNegateTaskFactory other = (BoolNegateTaskFactory) obj;
		if (subject == null) {
			if (other.subject != null)
				return false;
		} else if (!subject.equals(other.subject))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(!" + subject + ")";
	}

}
