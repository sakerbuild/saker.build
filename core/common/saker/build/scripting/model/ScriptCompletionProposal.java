package saker.build.scripting.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Interface for defining the functionality of a script completion proposal.
 * <p>
 * A completion proposal consists of a label to display in the completion popup, a list of text changes to apply when
 * the proposal is selected, and additional information to describe the semantics of the proposal.
 * <p>
 * The label is combined of multiple parts to optinally separately stylize the displayed text.
 * ({@link #getDisplayString()}, {@link #getDisplayType()}, {@link #getDisplayRelation()}) The proposal label is usually
 * displayed in the following format:
 * 
 * <pre>
 * &lt;getDisplayString()&gt; : &lt;getDisplayType()&gt; - &lt;getDisplayRelation()&gt;
 * </pre>
 * 
 * If any of the extension display label methods return <code>null</code>, their part is omitted (with the preceeding
 * separator).
 * <p>
 * The text changes are a list of modifications to the source code which are applied after each other. All the positions
 * in a text change is relative to the original document on which the proposal request was invoked. The text changes
 * must not overlap in range. This is similar to the behaviour described in the Language Server Procol. See
 * <a href="https://microsoft.github.io/language-server-protocol/specification#textedit">LSP TextEdit[] strucure</a>.
 * <p>
 * Additional information may be displayed to the user when a proposal is highlighted. It can be returned in a formatted
 * structure using {@link #getInformation()}.
 */
public interface ScriptCompletionProposal {
	/**
	 * Gets the selection offset where the cursor should be moved to after the text changes are applied.
	 * <p>
	 * The returned offset is an offset in the resulting document. (Unlike the offsets in {@link #getTextChanges()}.)
	 * <p>
	 * Not all IDE implementations support this method.
	 * 
	 * @return The offset of the cursor after applying the text changes.
	 */
	public int getSelectionOffset();

	/**
	 * Gets the primary label to dislay in the proposal popup for this item.
	 * <p>
	 * See the usual display format in the documentation of {@link ScriptCompletionProposal}.
	 * 
	 * @return The string to display.
	 */
	public String getDisplayString();

	/**
	 * Gets the edits to apply when this proposal is selected by the user.
	 * <p>
	 * The regions in the text changes must not overlap.
	 * <p>
	 * The changes are applied in sequential order.
	 * 
	 * @return An unmodifiable list of proposal edits.
	 */
	public List<? extends CompletionProposalEdit> getTextChanges();

	/**
	 * Gets the secondary label to display in the proposal popup for this item.
	 * <p>
	 * A good value for this might be the expected type of the semantic item for this proposal. (E.g. method/task return
	 * type.)
	 * <p>
	 * See the usual display format in the documentation of {@link ScriptCompletionProposal}.
	 * 
	 * @return The display type.
	 */
	public default String getDisplayType() {
		return null;
	}

	/**
	 * Gets the relation label to display in the proposal popup for this item.
	 * <p>
	 * A good vlaue for this might be the enclosing related element in the source code for the semantic item in this
	 * proposal. (E.g. an enclosing class name for a called method, the task name for a proposed parameter name).
	 * <p>
	 * See the usual display format in the documentation of {@link ScriptCompletionProposal}.
	 * 
	 * @return The display relation.
	 */
	public default String getDisplayRelation() {
		return null;
	}

	/**
	 * Gets the semantic informations about this proposal.
	 * 
	 * @return The partitioned text content describing the proposal or <code>null</code> if not available.
	 */
	public default PartitionedTextContent getInformation() {
		return null;
	}

	/**
	 * Gets an arbitary text for sorting the proposals.
	 * <p>
	 * IDE implementations may sort the proposals based on the returned text. The sorting is stable, equal elements will
	 * not be reordered.
	 * <p>
	 * Implementations should test the behaviour of returning non-<code>null</code> from this method by testing in an
	 * IDE.
	 * <p>
	 * Returning <code>null</code> is usually fine.
	 * 
	 * @return The sorting information or <code>null</code>.
	 */
	public default String getSortingInformation() {
		return null;
	}

	/**
	 * Gets the identifier that is associated with this proposal schema.
	 * <p>
	 * The schema identifiers are arbitrary strings that should uniquely identify the nature of the proposal. It can be
	 * used by IDE plugins and others to interpret the proposal and present the user a more readable display.
	 * <p>
	 * One use case for this is to create IDE plugins that assign various icons for the proposal.
	 * <p>
	 * E.g.:
	 * 
	 * <pre>
	 * "org.company.scripting.proposal"
	 * </pre>
	 * 
	 * @return The schema identifier or <code>null</code> if none.
	 */
	public default String getSchemaIdentifier() {
		return null;
	}

	/**
	 * Gets the schema meta-data that is associated with the proposal.
	 * <p>
	 * The meta-data can contain arbitrary key-value pairs that can be used to describe various aspects of the proposal.
	 * This is used to convey information to the IDE plugins about different aspects of the proposal.
	 * 
	 * @return The meta-data for the proposal. May be <code>null</code> or empty.
	 * @see #getSchemaIdentifier()
	 */
	public default Map<String, String> getSchemaMetaData() {
		return Collections.emptyMap();
	}
}
