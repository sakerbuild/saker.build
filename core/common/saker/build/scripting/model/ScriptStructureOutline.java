package saker.build.scripting.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Interface describing the structural outline information about the source code.
 * <p>
 * A structure outline defines a general outline information about the structure of the source code. It is in a tree
 * hierarchy, meaning every outline element can have children of their own, and so on.
 * <p>
 * Each structure outline contains the root entries which serve as the top level containers for any sub entries. The
 * entries can have hierarchical relations, and therefore nested in each other in a parent-child structure.
 * <p>
 * The outline tree is usually display in the IDE in a tree view alongside the source code editor. See
 * {@link StructureOutlineEntry} for more information.
 * 
 * @see StructureOutlineEntry
 */
public interface ScriptStructureOutline {
	/**
	 * Gets the top level root entries in this outline.
	 * 
	 * @return The entries. (May be <code>null</code>.)
	 */
	public List<? extends StructureOutlineEntry> getRootEntries();

	/**
	 * Gets the identifier string that is associated with the outline schema.
	 * <p>
	 * The schema identifiers are arbitrary strings that should uniquely identify the structure of the enclosed outline.
	 * It can be used by IDE plugins and others to interpret the outline structure and present the user a more readable
	 * display.
	 * <p>
	 * One use case for this is to create IDE plugins that assign various icons for the script outline to display.
	 * <p>
	 * The schema identifier can be provided by the scripting language, can be arbitrary, and should be unique among
	 * other scripting languages.
	 * <p>
	 * E.g.:
	 * 
	 * <pre>
	 * "org.company.scripting.language.outline"
	 * </pre>
	 * 
	 * @return The schema identifier or <code>null</code> if none.
	 */
	public default String getSchemaIdentifier() {
		return null;
	}

	/**
	 * Gets the schema meta-data that is associated with the outline.
	 * <p>
	 * The meta-data can contain arbitrary key-value pairs that can be used to describe various aspects of the outline.
	 * This is used to convey information to the IDE plugins about different aspects of the outline.
	 * 
	 * @return The meta-data for the outline. May be <code>null</code> or empty.
	 * @see #getSchemaIdentifier()
	 */
	public default Map<String, String> getSchemaMetaData() {
		return Collections.emptyMap();
	}
}
