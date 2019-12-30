package saker.build.scripting.model;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Data class implementation of a {@link StructureOutlineEntry}.
 * <p>
 * The class is mutable.
 */
@PublicApi
public class SimpleStructureOutlineEntry implements StructureOutlineEntry, Externalizable {
	private static final long serialVersionUID = 1L;

	private int offset;
	private int length;
	private int selectionOffset;
	private int selectionLength;

	private List<StructureOutlineEntry> children = new ArrayList<>();
	private String type;
	private String label;

	private String schemaIdentifier;
	private Map<String, String> schemaMetaData = new TreeMap<>();

	/**
	 * Creates a new instance with fields initialized to <code>null</code>, or empty collections.
	 */
	public SimpleStructureOutlineEntry() {
	}

	/**
	 * Creates a new instance with the given label.
	 * 
	 * @param label
	 *            The label.
	 * @see #getLabel()
	 */
	public SimpleStructureOutlineEntry(String label) {
		this.label = label;
	}

	/**
	 * Appends a child outline to this outline.
	 * 
	 * @param child
	 *            The child.
	 * @see #getChildren()
	 */
	public void addChild(StructureOutlineEntry child) {
		this.children.add(child);
	}

	/**
	 * Sets the selection range for this outline.
	 * 
	 * @param offset
	 *            The offset of the range.
	 * @param length
	 *            The length of the range.
	 * @see #getSelectionOffset()
	 * @see #getSelectionLength()
	 */
	public void setSelection(int offset, int length) {
		this.selectionOffset = offset;
		this.selectionLength = length;
	}

	/**
	 * Sets the position range for this outline.
	 * 
	 * @param offset
	 *            The offset of the range.
	 * @param length
	 *            The length of the range.
	 * @see #getOffset()
	 * @see #getLength()
	 */
	public void setRange(int offset, int length) {
		this.offset = offset;
		this.length = length;
	}

	/**
	 * Sets the label for this outline.
	 * 
	 * @param label
	 *            The label.
	 * @see #getLabel()
	 */
	public void setLabel(String label) {
		this.label = label;
	}

	/**
	 * Sets the type of this outline.
	 * 
	 * @param type
	 *            The type.
	 * @see #getType()
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * Sets the schema identifier for the outline entry.
	 * 
	 * @param schemaIdentifier
	 *            The schema identifier.
	 * @see #getSchemaIdentifier()
	 */
	public void setSchemaIdentifier(String schemaIdentifier) {
		this.schemaIdentifier = schemaIdentifier;
	}

	/**
	 * Ads a schema meta-data key-value pair for the outline entry.
	 * 
	 * @param key
	 *            The key of the meta-data.
	 * @param value
	 *            The value of the meta-data.
	 * @see #getSchemaMetaData()
	 * @throws NullPointerException
	 *             If the key is <code>null</code>.
	 */
	public void addSchemaMetaData(String key, String value) throws NullPointerException {
		Objects.requireNonNull(key, "key");
		this.schemaMetaData.put(key, value);
	}

	@Override
	public List<? extends StructureOutlineEntry> getChildren() {
		return children;
	}

	@Override
	public int getSelectionLength() {
		return selectionLength;
	}

	@Override
	public int getSelectionOffset() {
		return selectionOffset;
	}

	@Override
	public int getOffset() {
		return offset;
	}

	@Override
	public int getLength() {
		return length;
	}

	@Override
	public String getLabel() {
		return label;
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public String getSchemaIdentifier() {
		return schemaIdentifier;
	}

	@Override
	public Map<String, String> getSchemaMetaData() {
		return schemaMetaData;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(label);
		out.writeObject(type);
		out.writeInt(length);
		out.writeInt(offset);
		out.writeInt(selectionLength);
		out.writeInt(selectionOffset);
		SerialUtils.writeExternalCollection(out, children);

		out.writeObject(schemaIdentifier);
		SerialUtils.writeExternalMap(out, schemaMetaData);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		label = (String) in.readObject();
		type = (String) in.readObject();
		length = in.readInt();
		offset = in.readInt();
		selectionLength = in.readInt();
		selectionOffset = in.readInt();
		children = SerialUtils.readExternalImmutableList(in);

		schemaIdentifier = (String) in.readObject();
		schemaMetaData = SerialUtils.readExternalMap(new TreeMap<>(), in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + length;
		result = prime * result + offset;
		result = prime * result + selectionLength;
		result = prime * result + selectionOffset;
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
		SimpleStructureOutlineEntry other = (SimpleStructureOutlineEntry) obj;
		if (children == null) {
			if (other.children != null)
				return false;
		} else if (!children.equals(other.children))
			return false;
		if (label == null) {
			if (other.label != null)
				return false;
		} else if (!label.equals(other.label))
			return false;
		if (length != other.length)
			return false;
		if (offset != other.offset)
			return false;
		if (schemaIdentifier == null) {
			if (other.schemaIdentifier != null)
				return false;
		} else if (!schemaIdentifier.equals(other.schemaIdentifier))
			return false;
		if (schemaMetaData == null) {
			if (other.schemaMetaData != null)
				return false;
		} else if (!schemaMetaData.equals(other.schemaMetaData))
			return false;
		if (selectionLength != other.selectionLength)
			return false;
		if (selectionOffset != other.selectionOffset)
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + offset + " - (" + length + ")]";
	}

}
