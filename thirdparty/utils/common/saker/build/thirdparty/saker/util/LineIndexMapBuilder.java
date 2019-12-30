package saker.build.thirdparty.saker.util;

import java.util.Arrays;

/**
 * Class that creates a line index map based on the character data passed to it.
 * <p>
 * The class creates line index map the same way as {@link StringUtils#getLineIndexMap(CharSequence)}, but doesn't
 * require to have the whole character sequence to be accessible, character data can be written to it in sequence.
 * <p>
 * Use {@link #getIndexMap()} to retrieve the line index map.
 */
public class LineIndexMapBuilder implements Appendable {
	private int[] map;
	private int count = 0;
	private int i = 0;

	/**
	 * Creates a new instance initialized to receive character data.
	 */
	public LineIndexMapBuilder() {
		this(32);
	}

	/**
	 * Crates a new builder with an estimated number of lines that the result will contain.
	 * <p>
	 * The passed line count is an estimate, the build will grow accordingly if more lines are encountered.
	 * 
	 * @param estimatedlinecount
	 *            The estimated line count.
	 * @throws IllegalArgumentException
	 *             If the estimation is 0 or negative.
	 */
	public LineIndexMapBuilder(int estimatedlinecount) throws IllegalArgumentException {
		if (estimatedlinecount <= 0) {
			throw new IllegalArgumentException("Invalid estimated line count: " + estimatedlinecount);
		}
		map = new int[estimatedlinecount];
	}

	/**
	 * Gets the line index map that was built from the character data passed to this builder.
	 * <p>
	 * The builder instance is reuseable. The builder is not reset by this call, so appending more data will continue
	 * building the same line index.
	 * 
	 * @return The line index map.
	 */
	public int[] getIndexMap() {
		return Arrays.copyOf(map, count);
	}

	/**
	 * Appends the argument characters to the index builder.
	 * 
	 * @param csq
	 *            The characters.
	 * @return <code>this</code>
	 */
	public LineIndexMapBuilder append(char[] csq) {
		return append(csq, 0, csq.length);
	}

	/**
	 * Appends a range of characters from a given array to the index builder.
	 * 
	 * @param csq
	 *            The character array.
	 * @param start
	 *            The starting index in the array. (inclusive)
	 * @param end
	 *            The end index in the array. (exclusive)
	 * @return <code>this</code>
	 * @throws IndexOutOfBoundsException
	 *             If the range is out of the array's bounds.
	 */
	public LineIndexMapBuilder append(char[] csq, int start, int end) throws IndexOutOfBoundsException {
		ArrayUtils.requireArrayStartEndRange(csq, start, end);
		for (int j = start; j < end; j++, i++) {
			char c = csq[j];
			if (c == '\n') {
				appendLineIndex(i + j + 1);
			}
		}
		return this;
	}

	@Override
	public LineIndexMapBuilder append(CharSequence data) {
		return append(data, 0, data.length());
	}

	@Override
	public LineIndexMapBuilder append(CharSequence csq, int start, int end) {
		ArrayUtils.requireArrayStartEndRangeLength(csq.length(), start, end);
		for (int j = start; j < end; j++, i++) {
			char c = csq.charAt(j);
			if (c == '\n') {
				appendLineIndex(i + j + 1);
			}
		}
		return this;
	}

	@Override
	public LineIndexMapBuilder append(char c) {
		if (c == '\n') {
			appendLineIndex(i + 1);
		}
		++i;
		return this;
	}

	private void appendLineIndex(int idx) {
		if (count >= map.length) {
			map = Arrays.copyOf(map, map.length * 2);
		}
		map[count++] = idx;
	}
}
