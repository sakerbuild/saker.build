package saker.build.internal.scripting.language.task.operators;

import java.io.Externalizable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import saker.build.internal.scripting.language.task.SakerTaskFactory;

public class GreaterThanEqualsTaskFactory extends BinaryNumberOperatorTaskFactory {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public GreaterThanEqualsTaskFactory() {
	}

	public GreaterThanEqualsTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		super(left, right);
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new GreaterThanEqualsTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
	}

	@Override
	public String toString() {
		return "(gteq:(" + left + " >= " + right + "))";
	}

	@Override
	protected Boolean apply(BigDecimal left, BigDecimal right) {
		return left.compareTo(right) >= 0;
	}

	@Override
	protected Boolean apply(BigInteger left, BigInteger right) {
		return left.compareTo(right) >= 0;
	}

	@Override
	protected Boolean apply(double left, double right) {
		return left >= right;
	}

	@Override
	protected Boolean apply(long left, long right) {
		return left >= right;
	}

}
