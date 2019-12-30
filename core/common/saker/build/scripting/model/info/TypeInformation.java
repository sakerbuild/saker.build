package saker.build.scripting.model.info;

import java.util.List;
import java.util.Map;
import java.util.Set;

import saker.build.scripting.model.FormattedTextContent;

/**
 * Interface holding information about a given type.
 * <p>
 * Each type information is associated with a given {@linkplain TypeInformationKind kind}. The kind determines how the
 * type itself should be viewed as from a scripting perspective. Correctly defining it can result in better user
 * experience as more related completion proposals, and information can be displayed to the user.
 * <p>
 * The additional method in this interface provides additional information about the fields, documentation, and type
 * hierarchy for the given type.
 * 
 * @see SimpleTypeInformation
 */
public interface TypeInformation extends InformationHolder {
	/**
	 * Gets the kind of the type.
	 * <p>
	 * The kind determines how the consumer should handle this type information.
	 * 
	 * @return The type kind.
	 */
	public String getKind();

	/**
	 * Gets the qualified name of the type.
	 * 
	 * @return The qualified name of the type, or <code>null</code> if not applicable or not available.
	 */
	public default String getTypeQualifiedName() {
		return null;
	}

	/**
	 * Gets the simple name of the type.
	 * 
	 * @return The simple name of the type or <code>null</code> if not available.
	 */
	public default String getTypeSimpleName() {
		return null;
	}

	/**
	 * Gets information about the fields this type has.
	 * 
	 * @return An unmodifiable map of field informations or <code>null</code> if not available.
	 */
	public default Map<String, FieldInformation> getFields() {
		return null;
	}

	/**
	 * Gets information about what kind of enumeration values this type decalres.
	 * <p>
	 * The returned informations usually have their {@linkplain FieldInformation#getType() types} as this type.
	 * 
	 * @return An unmodifiable map of enumeration value informations or <code>null</code> if not available.
	 */
	public default Map<String, FieldInformation> getEnumValues() {
		return null;
	}

	/**
	 * Gets the super types of this type.
	 * <p>
	 * Any direct superclasses and superinterfaces should be returned in this method. Transitive superclasses and
	 * superinterfaces may be too, but not required.
	 * 
	 * @return An unmodifiable set of supertypes or <code>null</code> if not available.
	 */
	public default Set<TypeInformation> getSuperTypes() {
		return null;
	}

	/**
	 * Gets documentational information about this type.
	 * 
	 * @return The information.
	 */
	@Override
	public default FormattedTextContent getInformation() {
		return null;
	}

	/**
	 * Gets a collection of related types to this type information.
	 * <p>
	 * Related types are which can be used in similar contexts as this type. E.g. If the type of a parameter is T, and
	 * if type E can be used as a value to assign to the parameter with type T, then E can be considered to be related
	 * to T, as it can be used in the same place as type T.
	 * <p>
	 * The related types should be chosen carefully, and only when appropriate. Script models might use these types to
	 * provide additional suggestions when satisfying type requirements, therefore returning unrelated types for these
	 * suggestions might result in runtime errors.
	 * <p>
	 * {@linkplain #getSuperTypes() Super types} should not be included in the results.
	 * 
	 * @return The related types.
	 */
	public default Set<TypeInformation> getRelatedTypes() {
		return null;
	}

	/**
	 * Gets if the type is deprecated.
	 * 
	 * @return <code>true</code> if the type is deprecated.
	 */
	public default boolean isDeprecated() {
		return false;
	}

	/**
	 * Gets the type information about the element types of this type.
	 * <p>
	 * See {@link TypeInformationKind#COLLECTION} and {@link TypeInformationKind#MAP} for more information.
	 * <p>
	 * The elements in the returned list may be <code>null</code> to represent that a given element type at the index is
	 * unknown, unspecified, or doesn't have a specific type.
	 * 
	 * @return An unmodifiable list of element types for this type.
	 */
	public default List<? extends TypeInformation> getElementTypes() {
		return null;
	}
}