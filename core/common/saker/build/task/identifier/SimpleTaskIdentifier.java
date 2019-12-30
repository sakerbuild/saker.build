package saker.build.task.identifier;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableMap;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Simple implementation of a task identifier.
 * <p>
 * The class contains a class namespace and arbitrary field-object entries. The equality of task identifiers are
 * determined based on these fields.
 * <p>
 * The class provides a stable {@linkplain #hashCode() hash code}.
 * <p>
 * Use {@link TaskIdentifier#builder(String)} to create a new instance of this class.
 */
@PublicApi
public final class SimpleTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	protected String name;
	protected NavigableMap<String, Object> fields;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleTaskIdentifier() {
	}

	SimpleTaskIdentifier(String name, NavigableMap<String, Object> fieldMap) {
		this.name = name;
		this.fields = fieldMap;
	}

	/**
	 * Gets the task identifier name.
	 * <p>
	 * The name is generally a globally unique string that identifies the aspect of the associated task.
	 * 
	 * @return The name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the fields which are associated with this task identifier.
	 * 
	 * @return The fields.
	 */
	public NavigableMap<String, Object> getFields() {
		return fields;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(name);
		SerialUtils.writeExternalMap(out, fields);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		name = (String) in.readObject();
		fields = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + name.hashCode();
		result = prime * result + fields.hashCode();
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
		SimpleTaskIdentifier other = (SimpleTaskIdentifier) obj;
		if (!name.equals(other.name))
			return false;
		if (!ObjectUtils.mapOrderedEquals(fields, other.fields)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + name + ":" + fields + "]";
	}

}
