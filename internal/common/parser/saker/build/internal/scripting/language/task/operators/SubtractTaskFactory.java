package saker.build.internal.scripting.language.task.operators;

import java.io.Externalizable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import saker.build.internal.scripting.language.task.SakerTaskFactory;

public class SubtractTaskFactory extends BinaryNumberOperatorTaskFactory {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public SubtractTaskFactory() {
	}

	public SubtractTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		super(left, right);
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new SubtractTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
	}

	@Override
	public String toString() {
		return "(subtr:(" + left + " - " + right + "))";
	}

	@Override
	protected BigDecimal apply(BigDecimal left, BigDecimal right) {
		return left.subtract(right);
	}

	@Override
	protected BigInteger apply(BigInteger left, BigInteger right) {
		return left.subtract(right);
	}

	@Override
	protected Number apply(double left, double right) {
		return left - right;
	}

	@Override
	protected Number apply(long left, long right) {
		long result = left - right;
		if (((left ^ right) & (left ^ result)) < 0) {
			//as seen in Math.addExact
			return apply(BigInteger.valueOf(left), BigInteger.valueOf(right));
		}
		return result;
	}

}
