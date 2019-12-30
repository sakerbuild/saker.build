package saker.build.internal.scripting.language.task.operators;

import java.io.Externalizable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import saker.build.internal.scripting.language.task.SakerTaskFactory;

public class ShiftRightTaskFactory extends BinaryNumberOperatorTaskFactory {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public ShiftRightTaskFactory() {
	}

	public ShiftRightTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		super(left, right);
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new ShiftRightTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
	}

	@Override
	public String toString() {
		return "(shr:(" + left + " >> " + right + "))";
	}

	@Override
	protected BigDecimal apply(BigDecimal left, BigDecimal right) {
		throw new UnsupportedOperationException(
				"Bitwise operations are unsupported on floating point numbers. (" + left + " >> " + right + ")");
	}

	@Override
	protected BigInteger apply(BigInteger left, BigInteger right) {
		return left.shiftRight(right.intValue());
	}

	@Override
	protected Number apply(double left, double right) {
		throw new UnsupportedOperationException(
				"Bitwise operations are unsupported on floating point numbers. (" + left + " >> " + right + ")");
	}

	@Override
	protected Number apply(long left, long right) {
		return applyRightShift(left, right);
	}

	public static Number applyRightShift(long left, long right) {
		if (right == 0) {
			return left;
		}
		if (right < 0) {
			//right shifting via a negative number, consider it to be a left shift
			return ShiftLeftTaskFactory.applyLeftShift(left, Math.negateExact(right));
		}
		//right operand is positive.
		if (right >= 64) {
			//shifting out everything
			return left < 0 ? -1 : 0;
		}
		return left >> right;
	}

}
