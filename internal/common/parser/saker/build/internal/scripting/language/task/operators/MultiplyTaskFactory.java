package saker.build.internal.scripting.language.task.operators;

import java.io.Externalizable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import saker.build.internal.scripting.language.task.SakerTaskFactory;

public class MultiplyTaskFactory extends BinaryNumberOperatorTaskFactory {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public MultiplyTaskFactory() {
	}

	public MultiplyTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		super(left, right);
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new MultiplyTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
	}

	@Override
	public String toString() {
		return "(mult:(" + left + " * " + right + "))";
	}

	@Override
	protected BigDecimal apply(BigDecimal left, BigDecimal right) {
		return left.multiply(right);
	}

	@Override
	protected BigInteger apply(BigInteger left, BigInteger right) {
		return left.multiply(right);
	}

	@Override
	protected Number apply(double left, double right) {
		return left * right;
	}

	@Override
	protected Number apply(long left, long right) {
		//as seen in Math.multiplyExact
		long result = left * right;
		long ax = Math.abs(left);
		long ay = Math.abs(right);
		if (((ax | ay) >>> 31 != 0)) {
			if (((right != 0) && (result / right != left)) || (left == Long.MIN_VALUE && right == -1)) {
				return apply(BigInteger.valueOf(left), BigInteger.valueOf(right));
			}
		}
		return result;
	}

}
