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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.exc.SakerScriptEvaluationException;
import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SimpleSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.TaskIdentifier;

public class UnaryMinusTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	private SakerTaskFactory subject;

	/**
	 * For {@link Externalizable}.
	 */
	public UnaryMinusTaskFactory() {
	}

	public UnaryMinusTaskFactory(SakerTaskFactory subject) {
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
		if (subjectres == null) {
			throw new OperandExecutionException("Operand evaluated to null.", subtaskid);
		}
		if (!(subjectres instanceof Number)) {
			throw new OperandExecutionException("Operand is not a Number. (" + subjectres.getClass().getName() + ")",
					subtaskid);
		}
		final Object result = negateValueImpl((Number) subjectres);
		return new SimpleSakerTaskResult<>(result);
	}

	public static Number negateValueImpl(Number num) {
		if (num instanceof Float || num instanceof Double) {
			return -num.doubleValue();
		}
		if (num instanceof BigInteger) {
			return ((BigInteger) num).negate();
		}
		if (num instanceof BigDecimal) {
			return ((BigDecimal) num).negate();
		}
		long longval = num.longValue();
		if (longval == Long.MIN_VALUE) {
			return BigInteger.valueOf(longval).negate();
		}
		return -longval;
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
		return new UnaryMinusTaskFactory(cloneHelper(taskfactoryreplacements, subject));
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
		if (!(subjectres instanceof Number)) {
			return null;
		}
		Number num = (Number) subjectres;
		Object negated = negateValueImpl(num);
		return new SakerLiteralTaskFactory(negated);
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
		UnaryMinusTaskFactory other = (UnaryMinusTaskFactory) obj;
		if (subject == null) {
			if (other.subject != null)
				return false;
		} else if (!subject.equals(other.subject))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(-" + subject + ")";
	}

}
