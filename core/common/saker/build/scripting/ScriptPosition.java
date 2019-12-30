package saker.build.scripting;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Immutable data class representing a region in a textual document.
 * <p>
 * This class is used to convey location data of elements in scripts.
 * <p>
 * Every position information in this class is indexed by 0 (zero).
 */
public final class ScriptPosition implements Externalizable {
//XXX could make this class comparable
	private static final long serialVersionUID = 1L;

	private int line = -1;
	private int linePosition = -1;
	private int length = 0;
	private int fileOffset = -1;

	/**
	 * For {@link Externalizable}.
	 */
	public ScriptPosition() {
	}

	/**
	 * Creates a new script position with the initial data.
	 * 
	 * @param line
	 *            The line index.
	 * @param linePosition
	 *            The line position index.
	 * @param length
	 *            The length of the region.
	 * @param fileOffset
	 *            The file offset,
	 * @throws IllegalArgumentException
	 *             If any of the values are negative.
	 */
	public ScriptPosition(int line, int linePosition, int length, int fileOffset) throws IllegalArgumentException {
		if (line < 0) {
			throw new IllegalArgumentException("Negative line.");
		}
		if (linePosition < 0) {
			throw new IllegalArgumentException("Negative line position.");
		}
		if (length < 0) {
			throw new IllegalArgumentException("Negative length.");
		}
		if (fileOffset < 0) {
			throw new IllegalArgumentException("Negative file offset.");
		}
		this.line = line;
		this.linePosition = linePosition;
		this.length = length;
		this.fileOffset = fileOffset;
	}

	/**
	 * Gets the line of the position in the script.
	 * <p>
	 * Indexed by 0 (zero).
	 * 
	 * @return The line index.
	 * @see #getLinePosition()
	 */
	public final int getLine() {
		return line;
	}

	/**
	 * Gets the start position in the line.
	 * <p>
	 * Indexed by 0 (zero).
	 * 
	 * @return The start position index.
	 * @see #getLine()
	 */
	public final int getLinePosition() {
		return linePosition;
	}

	/**
	 * Gets the length of the region.
	 * 
	 * @return The region length.
	 */
	public final int getLength() {
		return length;
	}

	/**
	 * Gets the file offset in the script.
	 * <p>
	 * Indexed by 0 (zero).
	 * 
	 * @return The file offset.
	 */
	public final int getFileOffset() {
		return fileOffset;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + fileOffset;
		result = prime * result + length;
		result = prime * result + line;
		result = prime * result + linePosition;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ScriptPosition other = (ScriptPosition) obj;
		if (fileOffset != other.fileOffset)
			return false;
		if (length != other.length)
			return false;
		if (line != other.line)
			return false;
		if (linePosition != other.linePosition)
			return false;
		return true;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(line);
		out.writeInt(linePosition);
		out.writeInt(length);
		out.writeInt(fileOffset);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		line = in.readInt();
		linePosition = in.readInt();
		length = in.readInt();
		fileOffset = in.readInt();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + line + ":" + linePosition + " - " + fileOffset + ":" + length + "]";
	}

}