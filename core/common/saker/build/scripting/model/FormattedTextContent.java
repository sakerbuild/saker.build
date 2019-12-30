package saker.build.scripting.model;

import java.util.Set;

/**
 * Interface for representing textual content in differently formatted ways.
 * <p>
 * This interface provides access to the semantically same text content in differently formatted ways (if available).
 * Different formants should hold the same information.
 * <p>
 * Predefined format identifiers are defined by this interface as constants.
 * 
 * @see MultiFormattedTextContent
 * @see SingleFormattedTextContent
 */
public interface FormattedTextContent {
	/**
	 * Format identifier for plain text content.
	 */
	public static final String FORMAT_PLAINTEXT = "format.plaintext";
	/**
	 * Format identifier for markdown content.
	 */
	public static final String FORMAT_MARKDOWN = "format.markdown";
	/**
	 * Format identifier for HTML content.
	 */
	public static final String FORMAT_HTML = "format.html";

	/**
	 * Gets the text formatted in the specified way.
	 * 
	 * @param format
	 *            The requested format.
	 * @return The text formatted in the requested way.
	 * @throws IllegalArgumentException
	 *             If this instance cannot provide the text for the given format.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see #FORMAT_PLAINTEXT
	 * @see #FORMAT_MARKDOWN
	 * @see #FORMAT_HTML
	 */
	public String getFormattedText(String format) throws IllegalArgumentException, NullPointerException;

	/**
	 * Gets a set of known format identifiers that this instance will return.
	 * <p>
	 * Format identifiers are arbitrary strings. Some known formats are defined by this interface.
	 * 
	 * @return An unmodifiable set of format identifiers.
	 * @see #FORMAT_PLAINTEXT
	 * @see #FORMAT_MARKDOWN
	 * @see #FORMAT_HTML
	 */
	public Set<String> getAvailableFormats();

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
