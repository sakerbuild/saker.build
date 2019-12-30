package saker.build.internal.scripting.language.task.operators;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;

public abstract class BinaryOperatorTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	protected SakerTaskFactory left;
	protected SakerTaskFactory right;

	/**
	 * For {@link Externalizable}.
	 */
	public BinaryOperatorTaskFactory() {
	}

	public BinaryOperatorTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		this.left = left;
		this.right = right;
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
		BinaryOperatorTaskFactory other = (BinaryOperatorTaskFactory) obj;
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
}
