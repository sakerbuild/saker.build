package saker.build.internal.scripting.language.task.operators;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SimpleSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;

public class BitwiseNegateTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	private SakerTaskFactory subject;

	/**
	 * For {@link Externalizable}.
	 */
	public BitwiseNegateTaskFactory() {
	}

	public BitwiseNegateTaskFactory(SakerTaskFactory subject) {
		this.subject = subject;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		TaskIdentifier subjecttaskid = subject
				.createSubTaskIdentifier((SakerScriptTaskIdentifier) taskcontext.getTaskId());
		Object subjectres = taskcontext.getTaskUtilities().runTaskResult(subjecttaskid, subject).toResult(taskcontext);
		if (subjectres == null) {
			throw new OperandExecutionException("Failed to bitwise negate null.", subjecttaskid);
		}
		if (!(subjectres instanceof Number)) {
			throw new OperandExecutionException("Failed to bitwise negate value: " + subjectres, subjecttaskid);
		}
		Number num = (Number) subjectres;
		Number result = tryNegate(num);
		if (result == null) {
			throw new OperandExecutionException("Failed to bitwise negate value: " + subjectres, subjecttaskid);
		}
		Object negated = result;
		return new SimpleSakerTaskResult<>(negated);
	}

	public static Number tryNegate(Number num) {
		if (num instanceof Byte) {
			return ~num.byteValue();
		}
		if (num instanceof Short) {
			return ~num.shortValue();
		}
		if (num instanceof Integer) {
			return ~num.intValue();
		}
		if (num instanceof BigInteger) {
			return ((BigInteger) num).not();
		}
		if (num instanceof BigDecimal) {
			return ((BigDecimal) num).toBigInteger().not();
		}
		return ~num.longValue();
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
		Number result = tryNegate(num);
		if (result == null) {
			return null;
		}
		Object negated = result;
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
		return new BitwiseNegateTaskFactory(cloneHelper(taskfactoryreplacements, subject));
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
		BitwiseNegateTaskFactory other = (BitwiseNegateTaskFactory) obj;
		if (subject == null) {
			if (other.subject != null)
				return false;
		} else if (!subject.equals(other.subject))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(~" + subject + ")";
	}

}
