package saker.build.task.delta.impl;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.delta.DeltaType;
import saker.build.task.delta.OutputLoadFailedDelta;
import saker.build.thirdparty.saker.util.ObjectUtils;

public final class OutputLoadFailedDeltaImpl implements OutputLoadFailedDelta, Externalizable {
	private static final long serialVersionUID = 1L;

	public static final OutputLoadFailedDeltaImpl INSTANCE = new OutputLoadFailedDeltaImpl();

	/**
	 * For {@link Externalizable}.
	 */
	public OutputLoadFailedDeltaImpl() {
	}

	@Override
	public DeltaType getType() {
		return DeltaType.OUTPUT_LOAD_FAILED;
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[]";
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}
}
