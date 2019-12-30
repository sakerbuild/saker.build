package saker.build.scripting.model;

import saker.apiextract.api.PublicApi;

/**
 * Class containing possible values for {@link CompletionProposalEdit} kinds.
 * <p>
 * The possible values are present as <code>static final String</code> fields in this class.
 * <p>
 * The type kinds are interpreted in an ignore-case manner.
 * <p>
 * <i>Implementation note:</i> This class is not an <code>enum</code> to ensure forward and backward compatibility. New
 * kinds may be added to it in the future.
 */
@PublicApi
public class CompletionProposalEditKind {
	/**
	 * Simple text insertion.
	 * <p>
	 * 
	 * @see InsertCompletionProposalEdit
	 */
	public static final String INSERT = "INSERT";

	private CompletionProposalEditKind() {
		throw new UnsupportedOperationException();
	}
}
