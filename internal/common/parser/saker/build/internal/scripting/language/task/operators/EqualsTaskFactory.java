package saker.build.internal.scripting.language.task.operators;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SimpleSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskFuture;
import saker.build.task.identifier.TaskIdentifier;

public class EqualsTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	private SakerTaskFactory left;
	private SakerTaskFactory right;

	/**
	 * For {@link Externalizable}.
	 */
	public EqualsTaskFactory() {
	}

	public EqualsTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		TaskIdentifier lefttaskid = left.createSubTaskIdentifier(thistaskid);
		TaskIdentifier righttaskid = right.createSubTaskIdentifier(thistaskid);

		TaskFuture<SakerTaskResult> leftfut = taskcontext.getTaskUtilities().startTaskFuture(lefttaskid, left);
		TaskFuture<SakerTaskResult> rightfut = taskcontext.getTaskUtilities().startTaskFuture(righttaskid, right);

		SakerTaskResult lefttaskres = leftfut.get();
		SakerTaskResult righttaskres = rightfut.get();
		return new SimpleSakerTaskResult<>(testEquality(taskcontext, lefttaskres, righttaskres));
	}

	public static boolean testEquality(TaskContext taskcontext, SakerTaskResult lefttaskres,
			SakerTaskResult righttaskres) {
		Object left = lefttaskres.toResult(taskcontext);
		Object right = righttaskres.toResult(taskcontext);
		return testEquality(left, right);
	}

	public static boolean testEquality(Object left, Object right) {
		if (left == right) {
			return true;
		}
		if (left == null || right == null) {
			return false;
		}
		if (left.getClass().equals(right.getClass())) {
			return left.equals(right);
		}
		//the objects have different classes
		//try promoting numbers if possible
		if (left instanceof Number && right instanceof Number) {
			if (left instanceof BigInteger) {
				BigInteger lint = (BigInteger) left;
				if (right instanceof BigDecimal) {
					return new BigDecimal(lint).equals(right);
				}
				if (right instanceof BigInteger) {
					return left.equals(right);
				}
				return lint.equals(BigInteger.valueOf(((Number) right).longValue()));
			}
			if (left instanceof BigDecimal) {
				if (right instanceof BigDecimal) {
					return left.equals(right);
				}
				if (right instanceof BigInteger) {
					return left.equals(new BigDecimal((BigInteger) right));
				}
				return left.equals(BigDecimal.valueOf(((Number) right).doubleValue()));
			}
			Number lnum = (Number) left;
			if (right instanceof BigInteger) {
				return right.equals(BigInteger.valueOf(lnum.longValue()));
			}
			if (right instanceof BigDecimal) {
				return right.equals(BigDecimal.valueOf(lnum.doubleValue()));
			}
			Number rnum = (Number) right;
			if (left instanceof Double || left instanceof Float || right instanceof Double || right instanceof Float) {
				return lnum.doubleValue() == rnum.doubleValue();
			}
			return lnum.longValue() == rnum.longValue();
		}
		return left.equals(right);
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		SakerLiteralTaskFactory lc = left.tryConstantize();
		if (lc == null) {
			return null;
		}
		SakerLiteralTaskFactory rc = right.tryConstantize();
		if (rc == null) {
			return null;
		}
		return new SakerLiteralTaskFactory(testEquality(lc.getValue(), rc.getValue()));
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(left);
		out.writeObject(right);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		left = (SakerTaskFactory) in.readObject();
		right = (SakerTaskFactory) in.readObject();
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new EqualsTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((left == null) ? 0 : left.hashCode());
		result = prime * result + ((right == null) ? 0 : right.hashCode());
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
		EqualsTaskFactory other = (EqualsTaskFactory) obj;
		if (left == null) {
			if (other.left != null)
				return false;
		} else if (!left.equals(other.left))
			return false;
		if (right == null) {
			if (other.right != null)
				return false;
		} else if (!right.equals(other.right))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(eq:(" + left + " == " + right + "))";
	}

}
