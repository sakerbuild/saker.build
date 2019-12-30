package saker.build.internal.scripting.language.task.operators;

import java.io.Externalizable;
import java.util.Map;

import saker.build.internal.scripting.language.task.SakerTaskFactory;

public class BooleanOrTaskFactory extends BinaryBooleanOperatorTaskFactory {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public BooleanOrTaskFactory() {
	}

	public BooleanOrTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		super(left, right);
	}

	@Override
	protected boolean apply(boolean left, boolean right) {
		return left || right;
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new BooleanOrTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
	}

	@Override
	public String toString() {
		return "(boolo:(" + left + " || " + right + "))";
	}
}
