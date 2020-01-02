/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.scripting.model.info;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import saker.apiextract.api.PublicApi;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Simple {@link TypeInformation} data class.
 */
@PublicApi
public class SimpleTypeInformation implements TypeInformation, Externalizable {
	private static final long serialVersionUID = 1L;

	private String kind;
	private String qualifiedName;
	private String simpleName;
	private Map<String, FieldInformation> fields;
	private Map<String, FieldInformation> enumValues;
	private FormattedTextContent information;
	private boolean deprecated;
	private Set<TypeInformation> superTypes;
	private Set<TypeInformation> relatedTypes;
	private List<? extends TypeInformation> elementTypes;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleTypeInformation() {
	}

	/**
	 * Creates a new instance for the given type information kind.
	 * 
	 * @param kind
	 *            The kind.
	 * @see #getKind()
	 */
	public SimpleTypeInformation(String kind) {
		this.kind = kind;
	}

	@Override
	public String getKind() {
		return kind;
	}

	@Override
	public String getTypeQualifiedName() {
		return qualifiedName;
	}

	@Override
	public String getTypeSimpleName() {
		return simpleName;
	}

	@Override
	public Map<String, FieldInformation> getFields() {
		return fields;
	}

	@Override
	public Map<String, FieldInformation> getEnumValues() {
		return enumValues;
	}

	@Override
	public FormattedTextContent getInformation() {
		return information;
	}

	@Override
	public boolean isDeprecated() {
		return deprecated;
	}

	@Override
	public Set<TypeInformation> getSuperTypes() {
		return superTypes;
	}

	@Override
	public Set<TypeInformation> getRelatedTypes() {
		return relatedTypes;
	}

	@Override
	public List<? extends TypeInformation> getElementTypes() {
		return elementTypes;
	}

	/**
	 * Sets the qualified name of the type.
	 * 
	 * @param qualifiedName
	 *            The qualified name.
	 * @see #getTypeQualifiedName()
	 */
	public void setTypeQualifiedName(String qualifiedName) {
		this.qualifiedName = qualifiedName;
	}

	/**
	 * Sets the simple name of the type.
	 * 
	 * @param simpleName
	 *            The simple name.
	 * @see #getTypeSimpleName()
	 */
	public void setTypeSimpleName(String simpleName) {
		this.simpleName = simpleName;
	}

	/**
	 * Sets the type kind for this type.
	 * 
	 * @param kind
	 *            The kind.
	 * @see #getKind()
	 */
	public void setKind(String kind) {
		this.kind = kind;
	}

	/**
	 * Sets the field for this type.
	 * 
	 * @param fields
	 *            The fields.
	 * @see #getFields()
	 */
	public void setFields(Map<String, FieldInformation> fields) {
		this.fields = fields;
	}

	/**
	 * Sets the enumeration values for this type.
	 * 
	 * @param enumValues
	 *            The enumeration values.
	 * @see #getEnumValues()
	 */
	public void setEnumValues(Map<String, FieldInformation> enumValues) {
		this.enumValues = enumValues;
	}

	/**
	 * Sets the documentational information for this type.
	 * 
	 * @param information
	 *            The information.
	 * @see #getInformation()
	 */
	public void setInformation(FormattedTextContent information) {
		this.information = information;
	}

	/**
	 * Sets the deprecated flag for this type.
	 * 
	 * @param deprecated
	 *            <code>true</code> if deprecated.
	 * @see #isDeprecated()
	 */
	public void setDeprecated(boolean deprecated) {
		this.deprecated = deprecated;
	}

	/**
	 * Sets the super types for this type.
	 * 
	 * @param superTypes
	 *            The super types.
	 * @see #getSuperTypes()
	 */
	public void setSuperTypes(Set<TypeInformation> superTypes) {
		this.superTypes = superTypes;
	}

	/**
	 * Sets the related types for this type.
	 * 
	 * @param relatedTypes
	 *            The related types.
	 * @see #getRelatedTypes()
	 */
	public void setRelatedTypes(Set<TypeInformation> relatedTypes) {
		this.relatedTypes = relatedTypes;
	}

	/**
	 * Sets the element types for this type.
	 * 
	 * @param elementTypes
	 *            The element types.
	 * @see #getElementTypes()
	 */
	public void setElementTypes(List<? extends TypeInformation> elementTypes) {
		this.elementTypes = elementTypes;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(kind);
		out.writeObject(qualifiedName);
		out.writeObject(simpleName);
		out.writeObject(information);
		out.writeBoolean(deprecated);
		SerialUtils.writeExternalMap(out, fields);
		SerialUtils.writeExternalMap(out, enumValues);
		SerialUtils.writeExternalCollection(out, superTypes);
		SerialUtils.writeExternalCollection(out, relatedTypes);
		SerialUtils.writeExternalCollection(out, elementTypes);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		kind = (String) in.readObject();
		qualifiedName = (String) in.readObject();
		simpleName = (String) in.readObject();
		information = (FormattedTextContent) in.readObject();
		deprecated = in.readBoolean();

		fields = SerialUtils.readExternalMap(new TreeMap<>(), in);
		enumValues = SerialUtils.readExternalMap(new TreeMap<>(), in);
		superTypes = SerialUtils.readExternalImmutableLinkedHashSet(in);
		relatedTypes = SerialUtils.readExternalImmutableLinkedHashSet(in);
		elementTypes = SerialUtils.readExternalImmutableList(in);
	}

	@Override
	public String toString() {
		return "SimpleTypeInformation[" + (kind != null ? "kind=" + kind + ", " : "")
				+ (qualifiedName != null ? "qualifiedName=" + qualifiedName + ", " : "")
				+ (simpleName != null ? "simpleName=" + simpleName + ", " : "")
				+ (fields != null ? "fields=" + fields + ", " : "")
				+ (enumValues != null ? "enumValues=" + enumValues + ", " : "")
				+ (information != null ? "information=" + information + ", " : "")
				+ (deprecated ? "deprecated=true, " : "")
				+ (superTypes != null ? "superTypes=" + superTypes + ", " : "")
				+ (relatedTypes != null ? "relatedTypes=" + relatedTypes + ", " : "")
				+ (elementTypes != null ? "elementTypes=" + elementTypes : "") + "]";
	}

}
