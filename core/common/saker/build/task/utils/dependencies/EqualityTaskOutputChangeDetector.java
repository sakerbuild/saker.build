package saker.build.task.utils.dependencies;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.build.task.dependencies.TaskOutputChangeDetector;

/**
 * {@link TaskOutputChangeDetector} implementation that compares the expected result by
 * {@linkplain Objects#equals(Object, Object) equality}.
 */
public class EqualityTaskOutputChangeDetector implements TaskOutputChangeDetector, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The object to compare the task output with.
	 */
	protected Object object;

	/**
	 * For {@link Externalizable}.
	 */
	public EqualityTaskOutputChangeDetector() {
	}

	/**
	 * Creates a new instance that compares the task output against the argument.
	 * 
	 * @param object
	 *            The object to compare the task output with.
	 */
	public EqualityTaskOutputChangeDetector(Object object) {
		this.object = object;
	}

	@Override
	public boolean isChanged(Object taskoutput) {
		return !Objects.equals(object, taskoutput);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(object);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		object = in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((object == null) ? 0 : object.hashCode());
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
		EqualityTaskOutputChangeDetector other = (EqualityTaskOutputChangeDetector) obj;
		if (object == null) {
			if (other.object != null)
				return false;
		} else if (!object.equals(other.object))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (object != null ? "object=" + object : "") + "]";
	}

}
