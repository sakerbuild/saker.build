package saker.build.file.provider;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

import saker.build.file.path.SakerPath;

/**
 * Exception for signalling that a delete request of multiple files succeded only partially.
 * <p>
 * When a file deletion request is issued for multiple files, it can succeed partially in which case some files are
 * deleted, and some may be left unchanged. In this case an exception of this kind is thrown which holds information
 * about the successfully deleted children, and the file that caused the operation to abort.
 *
 * @see #getCause()
 * @see #getDeletedChildren()
 * @see #getFailedChildPath()
 */
public class PartiallyDeletedChildrenException extends IOException {
	private static final long serialVersionUID = 1L;

	private Set<String> deletedChildren;
	private SakerPath failedChildPath;

	/**
	 * Creates a new exception holding the data related to the delete operation failure.
	 * 
	 * @param cause
	 *            The exception that caused the operation to fail.
	 * @param failedChildPath
	 *            The path of the delete request which failed.
	 * @param deletedChildren
	 *            The names of the successfully deleted children.
	 */
	public PartiallyDeletedChildrenException(IOException cause, SakerPath failedChildPath,
			Set<String> deletedChildren) {
		super(Objects.requireNonNull(cause, "cause"));
		Objects.requireNonNull(failedChildPath, "failed child path");
		Objects.requireNonNull(deletedChildren, "deleted children");
		this.failedChildPath = failedChildPath;
		this.deletedChildren = deletedChildren;
	}

	/**
	 * Gets the set of child file names which were successfully deleted.
	 * <p>
	 * The child names are single path names which are considered to be in the directory of the associated deletion
	 * request.
	 * 
	 * @return The child file names.
	 */
	public Set<String> getDeletedChildren() {
		return deletedChildren;
	}

	/**
	 * Gets the absolute path of the file which failed to be deleted.
	 * 
	 * @return The file path.
	 */
	public SakerPath getFailedChildPath() {
		return failedChildPath;
	}

	/**
	 * Gets the cause of the delete operation failure associated with the {@linkplain #getFailedChildPath() failed child
	 * path}.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public IOException getCause() {
		return (IOException) super.getCause();
	}
}
