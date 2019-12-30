package saker.build.task.delta.impl;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.runtime.execution.ExecutionProperty;
import saker.build.task.delta.DeltaType;
import saker.build.task.delta.ExecutionDependencyDelta;

public final class ExecutionDependencyDeltaImpl<T> implements ExecutionDependencyDelta<T>, Externalizable {
	private static final long serialVersionUID = 1L;

	private ExecutionProperty<T> property;

	/**
	 * For {@link Externalizable}.
	 */
	public ExecutionDependencyDeltaImpl() {
	}

	public ExecutionDependencyDeltaImpl(ExecutionProperty<T> property) {
		this.property = property;
	}

	@Override
	public DeltaType getType() {
		return DeltaType.EXECUTION_PROPERTY_CHANGED;
	}

	@Override
	public ExecutionProperty<T> getProperty() {
		return property;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(property);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		property = (ExecutionProperty<T>) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((property == null) ? 0 : property.hashCode());
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
		ExecutionDependencyDeltaImpl<?> other = (ExecutionDependencyDeltaImpl<?>) obj;
		if (property == null) {
			if (other.property != null)
				return false;
		} else if (!property.equals(other.property))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + property + "]";
	}

}
