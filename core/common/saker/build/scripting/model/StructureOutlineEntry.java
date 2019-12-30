package saker.build.scripting.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Interface for providing outline information about the source code.
 * <p>
 * A structure outline defines a general outline information about the structure of the source code. It is in a tree
 * hierarchy, meaning every outline element can have children of their own, and so on.
 * <p>
 * Each outline bears a label, which is usually displayed alongside the object in an IDE. A secondary information
 * ({@linkplain #getType() type}) is present for more optional short information which the IDE can display. These
 * informations are usually displayed in the following format:
 * 
 * <pre>
 * label : type
 * </pre>
 * 
 * where <code>": type"</code> is optional and might be using a different color for text display.
 * <p>
 * The outline defines two text ranges which are used to provide dynamic navigation to the user.
 * <p>
 * The {@linkplain #getOffset() offset}-{@linkplain #getLength() length} pair will define the range of the corresponding
 * source element for an outline object. When the user navigates in the source code, and the cursor is in the range, the
 * corresponding outline object will be highlighted in the outline view.
 * <p>
 * The {@linkplain #getSelectionOffset() selection offset}-{@linkplain #getSelectionLength() selection length} pair
 * defines a range which is highlighted in the document when the user selects the outline object manually.
 * <p>
 * Example:
 * 
 * <pre>
 * build_target {
 * 	...
 * }
 * </pre>
 * 
 * When the user navigates the cursor inside the braces, the outline for <code>build_target</code> will be highlighted.
 * When the user selects the outline for <code>build_target</code>, the preceding <code>"build_target"</code> phrase
 * will be highlighted.
 * <p>
 * Outline tree objects should be constructed in a way that the positional information ({@linkplain #getOffset() offset}
 * and {@linkplain #getLength() length}) will be inside of the parent positional information. This ensures that the
 * appropriate outline object will be highlighted when the user navigates through the code.
 * <p>
 * Usually the innermost outline object is highlighted.
 * <p>
 * The above mechanism for outline highlight in the IDE is a recommendation for IDE plugin implementation, but they
 * might follow a different behaviour.
 * <p>
 * All offsets in this class is zero based, and work on decoded characters, not on raw byte data.
 */
public interface StructureOutlineEntry {
	/**
	 * Gets the children outlines for this object.
	 * <p>
	 * The default implementation returns an empty list.
	 * 
	 * @return An unmodifiable list of child outlines.
	 */
	public default List<? extends StructureOutlineEntry> getChildren() {
		return Collections.emptyList();
	}

	/**
	 * Gets the selection offset.
	 * 
	 * @return The selection offset.
	 */
	public int getSelectionOffset();

	/**
	 * Gets the selection length.
	 * 
	 * @return The selection length.
	 */
	public int getSelectionLength();

	/**
	 * Gets the outline position offset.
	 * 
	 * @return The offset.
	 */
	public int getOffset();

	/**
	 * Gets the outline position length.
	 * 
	 * @return The length.
	 */
	public int getLength();

	/**
	 * Gets the label for the outline.
	 * <p>
	 * The label is the primary information about the outline object, should be short, and single line.
	 * 
	 * @return The label.
	 */
	public String getLabel();

	/**
	 * Gets the type of this outline.
	 * <p>
	 * The type is a secondary optional information about the outline object. Should be short and single line.
	 * 
	 * @return The type.
	 */
	public default String getType() {
		return null;
	}

	/**
	 * Gets the schema identifier string of the outline entry.
	 * <p>
	 * Similarly to {@link ScriptStructureOutline}, entries can also have schema identifiers. They can be used to
	 * differentiate various outline entries for IDE plugins.
	 * <p>
	 * See {@link ScriptStructureOutline#getSchemaIdentifier()} for more information.
	 * <p>
	 * E.g.:
	 * 
	 * <pre>
	 * "org.company.scripting.language.outline.entry.target"
	 * </pre>
	 * 
	 * @return The schema identifier or <code>null</code> if none.
	 */
	public default String getSchemaIdentifier() {
		return null;
	}

	/**
	 * Gets the schema related meta-data ssociated with this outline entry.
	 * <p>
	 * Similarly to {@link ScriptStructureOutline}, entries can also have meta-data defined for them. They can be used
	 * to convey various information about the given outline entry.
	 * <p>
	 * See {@link ScriptStructureOutline#getSchemaMetaData()} for more information.
	 * 
	 * @return The meta-data for the outline entry. May be <code>null</code>, or empty.
	 */
	public default Map<String, String> getSchemaMetaData() {
		return Collections.emptyMap();
	}
}
