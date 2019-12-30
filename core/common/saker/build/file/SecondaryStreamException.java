package saker.build.file;

import java.io.IOException;

import saker.build.file.content.ContentDatabase;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.thirdparty.saker.util.io.ByteSink;

/**
 * Exception class for rethrowing exceptions thrown by additional stream writings.
 * 
 * @see SakerFile#synchronizeImpl(ProviderHolderPathKey, ByteSink)
 * @see ContentDatabase.ContentUpdater#updateWithStream(ByteSink)
 */
public class SecondaryStreamException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see IOException#IOException(String, Throwable)
	 */
	public SecondaryStreamException(String message, IOException cause) {
		super(message, cause);
	}

	/**
	 * @see IOException#IOException(Throwable)
	 */
	public SecondaryStreamException(IOException cause) {
		super(cause);
	}

	@Override
	public IOException getCause() {
		return (IOException) super.getCause();
	}
}