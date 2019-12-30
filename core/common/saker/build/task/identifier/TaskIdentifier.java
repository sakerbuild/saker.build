package saker.build.task.identifier;

import java.io.Externalizable;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import saker.build.thirdparty.saker.util.ImmutableUtils;

/**
 * Task identifiers uniquely identify a task in the build runtime.
 * <p>
 * Task identifiers are used as a key for the build executor to reference the tasks themselves. In the runtime one
 * identifier corresponds to exactly one task.
 * <p>
 * Task identifiers are required to implement the contract specified by {@link #equals(Object)} and {@link #hashCode()}.
 * They are also strongly encouraged to implement {@link Externalizable}.
 * <p>
 * The task identifier interface was defined instead of using raw objects to emphasise the importance of implementing
 * {@link #equals(Object)} and encouraging specifying custom classes as task keys instead of just relying on existing
 * class instances. E.g. lazy programmers (as we all are) could just use strings as task identifiers, but it is much
 * harder to handle separate versions and implementations of tasks that way, and is more collision-prone.
 * 
 * @see #builder(String)
 * @see BuildFileTargetTaskIdentifier
 */
public interface TaskIdentifier {
	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);

	@Override
	public String toString();

	/**
	 * Convenience method for building simple task identifiers without creating a standalone subclass.
	 * <p>
	 * The created task identifier can act as a compatibility task identifier between different versions of the same
	 * task.
	 * <p>
	 * The method takes a names as a parameter. The name should be an unique string that identifies the aspect of the
	 * task it will be used with. It can be arbitrarily chosen, and is recommended to be globally unique.
	 * 
	 * @param name
	 *            The task identifier name.
	 * @return A builder for a simple task identifier.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static SimpleTaskIdBuilder builder(String name) throws NullPointerException {
		Objects.requireNonNull(name, "name");
		return new SimpleTaskIdBuilder(name);
	}

	/**
	 * A builder class for creating simple task identifiers.
	 * <p>
	 * The simple task identifier consists of arbitrary field names and corresponding arbitrary objects. These pairs
	 * will be compared for equality when the task identifier equality is checked.
	 */
	public static final class SimpleTaskIdBuilder {
		private String name;
		private NavigableMap<String, Object> fields = new TreeMap<>();

		SimpleTaskIdBuilder(String name) {
			this.name = name;
		}

		/**
		 * Sets the field of the simple task identifier.
		 * <p>
		 * Overwrites previous value if field name is already present.
		 * 
		 * @param fieldname
		 *            The name of the field. (arbitrary)
		 * @param value
		 *            The value of the field. (arbitrary)
		 * @return <code>this</code> builder.
		 * @throws IllegalStateException
		 *             If {@link #build()} was already called.
		 */
		public SimpleTaskIdBuilder field(String fieldname, Object value) throws IllegalStateException {
			requireUnbuilt();
			fields.put(fieldname, value);
			return this;
		}

		/**
		 * Builds the task identifier.
		 * <p>
		 * The builder cannot be reused after this call.
		 * 
		 * @return The created task identifier.
		 * @throws IllegalStateException
		 *             If {@link #build()} was already called.
		 */
		public TaskIdentifier build() throws IllegalStateException {
			requireUnbuilt();
			SimpleTaskIdentifier result = new SimpleTaskIdentifier(name,
					fields.isEmpty() ? Collections.emptyNavigableMap()
							: ImmutableUtils.unmodifiableNavigableMap(fields));
			this.fields = null;
			return result;
		}

		private void requireUnbuilt() {
			if (fields == null) {
				throw new IllegalStateException("Builder is already used.");
			}
		}
	}
}
