package saker.build.util.file;

import java.io.Externalizable;

import saker.apiextract.api.PublicApi;
import saker.build.file.SakerDirectory;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.io.writer.SerializeRMIObjectWriteHandler;

/**
 * Same as {@link IgnoreCaseExtensionDirectoryVisitPredicate}, but doesn't accepts directories as the result.
 */
@RMIWriter(SerializeRMIObjectWriteHandler.class)
@PublicApi
public class FileOnlyIgnoreCaseExtensionDirectoryVisitPredicate extends IgnoreCaseExtensionDirectoryVisitPredicate {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public FileOnlyIgnoreCaseExtensionDirectoryVisitPredicate() {
	}

	/**
	 * Creates a new visitor for the given extension.
	 * 
	 * @param dotext
	 *            The extension.
	 * @see IgnoreCaseExtensionDirectoryVisitPredicate#IgnoreCaseExtensionDirectoryVisitPredicate(String)
	 */
	public FileOnlyIgnoreCaseExtensionDirectoryVisitPredicate(String dotext) {
		super(dotext);
	}

	@Override
	public boolean visitDirectory(String name, SakerDirectory directory) {
		return false;
	}
}
