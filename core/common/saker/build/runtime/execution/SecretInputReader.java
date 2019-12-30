package saker.build.runtime.execution;

import java.io.Console;

import saker.apiextract.api.PublicApi;
import saker.build.task.TaskContext;

/**
 * Interface for reading a secret character sequence from a given source.
 * <p>
 * This interface is used to read characters from a given input without explicitly displaying those characters. The
 * purpose of this class is to provide a simplar secret reading mechanism to {@link Console} while abstracting away the
 * implementation.
 * <p>
 * This interface can be used by tasks by requesting it through the {@linkplain TaskContext#getSecretReader() task
 * context}.
 * <p>
 * Common implementations of this interface may be backed by the aforementioned console class from
 * {@link System#console()}, may be implemented by a password text box in an IDE, or a properties file that contains
 * identifier based associations to secrets.
 */
@PublicApi
public interface SecretInputReader {
	/**
	 * Reads a secret character sequence from the underlying input.
	 * <p>
	 * Extra information for the user may be provided using the parameters, which will be displayed when the user is
	 * prompted for the input. These informations are optional, but are strongly recommended, as they can provide the
	 * user information about why the given input is requested.
	 * <p>
	 * The provided informations are usually presented in the following way in a console:
	 * 
	 * <pre>
	 * &lt;Title information&gt;
	 * &lt;A longer explanation about
	 * why the given input information
	 * is required by the caller.&gt;
	 * &lt;The prompt for input:&gt;&lt;$cursor&gt;
	 * </pre>
	 *
	 * There is no new line after the prompt string.
	 * <p>
	 * If the implementation is not backed by a console, the informations may be displayed in dialog using a similar
	 * format, or in any other implementation dependent way.
	 * <p>
	 * An identifier for the requested secret can be provided in order to accomodate to other secret reading
	 * implementation which are not backed by the direct input of the user, but rather an identifier based database. The
	 * identifier may be arbitrary, but should be unique.
	 * 
	 * @param titleinfo
	 *            The title for the request or <code>null</code> if not specified.
	 * @param message
	 *            The message or explanation for the request or <code>null</code> if not specified.
	 * @param prompt
	 *            The prompt string for the request or <code>null</code> if not specified.
	 * @param secretidentifier
	 *            The identifier associated to the prompted secret reading or <code>null</code> if none.
	 * @return The user entered input or <code>null</code> in case the user didn't provide an answer. (E.g. closed the
	 *             dialog)
	 */
	public String readSecret(String titleinfo, String message, String prompt, String secretidentifier);
}
