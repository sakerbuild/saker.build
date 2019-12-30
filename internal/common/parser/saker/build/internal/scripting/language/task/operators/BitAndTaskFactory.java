package saker.build.internal.scripting.language.task.operators;

import java.io.Externalizable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import saker.build.internal.scripting.language.task.SakerTaskFactory;

public class BitAndTaskFactory extends BinaryNumberOperatorTaskFactory {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public BitAndTaskFactory() {
	}

	public BitAndTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		super(left, right);
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new BitAndTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
	}

	@Override
	public String toString() {
		return "(bita:(" + left + " & " + right + "))";
	}

	@Override
	protected BigDecimal apply(BigDecimal left, BigDecimal right) {
		throw new UnsupportedOperationException(
				"Bitwise operations are unsupported on floating point numbers. (" + left + " & " + right + ")");
	}

	@Override
	protected BigInteger apply(BigInteger left, BigInteger right) {
		return left.and(right);
	}

	@Override
	protected Number apply(double left, double right) {
		throw new UnsupportedOperationException(
				"Bitwise operations are unsupported on floating point numbers. (" + left + " & " + right + ")");
	}

	@Override
	protected Number apply(long left, long right) {
		return left & right;
	}

}
