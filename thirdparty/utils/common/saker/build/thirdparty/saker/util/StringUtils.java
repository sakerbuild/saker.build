/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.thirdparty.saker.util;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;

import saker.build.thirdparty.saker.util.function.Functionals;

/**
 * Utility class containing functions for working with and manipulating strings and {@linkplain CharSequence char
 * sequences}.
 */
public class StringUtils {
	/**
	 * Creates a string representation of the argument elements joined with the given delimiter.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * {@link #toStringJoin(CharSequence, CharSequence, Iterable, CharSequence) toStringJoin}(null, delimiter, elements, null);
	 * </pre>
	 * 
	 * @param delimiter
	 *            The delimiter to insert between consecutive elements.
	 * @param elements
	 *            The elements to join.
	 * @return The created string based on the arguments.
	 */
	public static String toStringJoin(CharSequence delimiter, Iterable<?> elements) {
		return toStringJoin(null, delimiter, elements, null);
	}

	/**
	 * Creates a string representation of the argument elements joined with the given delimiter.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * {@link #toStringJoin(CharSequence, CharSequence, Iterator, CharSequence) toStringJoin}(null, delimiter, elements, null);
	 * </pre>
	 * 
	 * @param delimiter
	 *            The delimiter to insert between consecutive elements.
	 * @param elements
	 *            The iterator of elements to join.
	 * @return The created string based on the arguments.
	 */
	public static String toStringJoin(CharSequence delimiter, Iterator<?> elements) {
		return toStringJoin(null, delimiter, elements, null);
	}

	/**
	 * Creates a string representation of the argument elements joined with the given delimiter.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * {@link #toStringJoin(CharSequence, CharSequence, Object[], CharSequence) toStringJoin}(null, delimiter, elements, null);
	 * </pre>
	 * 
	 * @param delimiter
	 *            The delimiter to insert between consecutive elements.
	 * @param elements
	 *            The array of elements to join.
	 * @return The created string based on the arguments.
	 */
	public static String toStringJoin(CharSequence delimiter, Object[] elements) {
		return toStringJoin(null, delimiter, elements, null);
	}

	/**
	 * Creates a string representation of the argument elements with the specified format.
	 * <p>
	 * The method converts the given elements into string representation and joins them into a string with the given
	 * delimiter between them. The result is then prepended with the given prefix, and appended with the given suffix.
	 * <p>
	 * All arguments are <code>null</code>able, in which case the corresponding part will not be appended to the result.
	 * If the elements are <code>null</code>, the delimiters won't be printed either. If all arguments are
	 * <code>null</code>, an empty string is returned.
	 * 
	 * @param prefix
	 *            The prefix to start the result with.
	 * @param delimiter
	 *            The delimiter to insert between consecutive elements.
	 * @param elements
	 *            The elements to join.
	 * @param suffix
	 *            The suffix to end the result with.
	 * @return The created string based on the arguments.
	 * @see StringBuilder#append(Object)
	 */
	public static String toStringJoin(CharSequence prefix, CharSequence delimiter, Iterable<?> elements,
			CharSequence suffix) {
		StringBuilder sb = new StringBuilder();
		if (prefix != null) {
			sb.append(prefix);
		}
		if (elements != null) {
			if (delimiter == null) {
				elements.forEach(sb::append);
			} else {
				Iterator<?> it = elements.iterator();
				if (it.hasNext()) {
					while (true) {
						Object e = it.next();
						sb.append(e);
						if (!it.hasNext()) {
							break;
						}
						sb.append(delimiter);
					}
				}
			}
		}
		if (suffix != null) {
			sb.append(suffix);
		}
		return sb.toString();
	}

	/**
	 * Creates a string representation of the argument elements with the specified format.
	 * <p>
	 * The method converts the given elements into string representation and joins them into a string with the given
	 * delimiter between them. The result is then prepended with the given prefix, and appended with the given suffix.
	 * <p>
	 * All arguments are <code>null</code>able, in which case the corresponding part will not be appended to the result.
	 * If the elements are <code>null</code>, the delimiters won't be printed either. If all arguments are
	 * <code>null</code>, an empty string is returned.
	 * 
	 * @param prefix
	 *            The prefix to start the result with.
	 * @param delimiter
	 *            The delimiter to insert between consecutive elements.
	 * @param elements
	 *            The iterator of elements to join.
	 * @param suffix
	 *            The suffix to end the result with.
	 * @return The created string based on the arguments.
	 * @see StringBuilder#append(Object)
	 */
	public static String toStringJoin(CharSequence prefix, CharSequence delimiter, Iterator<?> elements,
			CharSequence suffix) {
		StringBuilder sb = new StringBuilder();
		if (prefix != null) {
			sb.append(prefix);
		}
		if (elements != null) {
			if (delimiter == null) {
				elements.forEachRemaining(sb::append);
			} else {
				if (elements.hasNext()) {
					while (true) {
						Object e = elements.next();
						sb.append(e);
						if (!elements.hasNext()) {
							break;
						}
						sb.append(delimiter);
					}
				}
			}
		}
		if (suffix != null) {
			sb.append(suffix);
		}
		return sb.toString();
	}

	/**
	 * Creates a string representation of the argument elements with the specified format.
	 * <p>
	 * The method converts the given elements into string representation and joins them into a string with the given
	 * delimiter between them. The result is then prepended with the given prefix, and appended with the given suffix.
	 * <p>
	 * All arguments are <code>null</code>able, in which case the corresponding part will not be appended to the result.
	 * If the elements are <code>null</code>, the delimiters won't be printed either. If all arguments are
	 * <code>null</code>, an empty string is returned.
	 * 
	 * @param prefix
	 *            The prefix to start the result with.
	 * @param delimiter
	 *            The delimiter to insert between consecutive elements.
	 * @param elements
	 *            The array of elements to join.
	 * @param suffix
	 *            The suffix to end the result with.
	 * @return The created string based on the arguments.
	 * @see StringBuilder#append(Object)
	 */
	public static String toStringJoin(CharSequence prefix, CharSequence delimiter, Object[] elements,
			CharSequence suffix) {
		StringBuilder sb = new StringBuilder();
		if (prefix != null) {
			sb.append(prefix);
		}
		if (elements != null) {
			if (delimiter == null) {
				for (Object e : elements) {
					sb.append(e);
				}
			} else {
				if (elements.length > 0) {
					for (int i = 0;;) {
						Object e = elements[i];
						sb.append(e);
						if (++i == elements.length) {
							break;
						}
						sb.append(delimiter);
					}
				}
			}
		}
		if (suffix != null) {
			sb.append(suffix);
		}
		return sb.toString();
	}

	/**
	 * Converts the argument objects to string representation, but limiting its length if it is longer than the maximum
	 * limit.
	 * 
	 * @param obj
	 *            The object to convert to string.
	 * @param limit
	 *            The maximum number of characters the result can contain.
	 * @return The limited string representation of the object.
	 * @see Object#toString()
	 */
	public static String toStringLimit(Object obj, int limit) {
		if (obj == null) {
			if (limit < 4) {
				return "null".substring(0, limit);
			}
			return "null";
		}
		String res = obj.toString();
		if (res.length() > limit) {
			return res.substring(0, limit);
		}
		return res;
	}

	/**
	 * Converts the argument objects to string representation, but limiting its length if it is longer than the maximum
	 * limit, and adding an ellipsize if limited.
	 * <p>
	 * This method works the same way as {@link #toStringLimit(Object, int, String)}, but if the return value is
	 * actually limited by its length, then the ellipsize argument will be appended to it to signal that it got cropped.
	 * 
	 * @param obj
	 *            The object to convert to string.
	 * @param limit
	 *            The maximum number of characters the result can contain. (Excluding the ellipsize ending.)
	 * @param ellipsizeend
	 *            The ellipsize ending that should be appended if the result is cropped.
	 * @return The limited string representation of the object.
	 */
	public static String toStringLimit(Object obj, int limit, String ellipsizeend) {
		if (ObjectUtils.isNullOrEmpty(ellipsizeend)) {
			return toStringLimit(obj, limit);
		}
		if (obj == null) {
			if (limit < 4) {
				return "null".substring(0, limit) + ellipsizeend;
			}
			return "null";
		}
		String res = obj.toString();
		if (res.length() > limit) {
			return res.substring(0, limit) + ellipsizeend;
		}
		return res;
	}

	/**
	 * Returns the argument string with quotes around it unless it is <code>null</code>.
	 * 
	 * @param str
	 *            The string to quote.
	 * @return <code>"null"</code> (without quotes in the actual result string) if the argument is <code>null</code>, or
	 *             the given string with <code>'\"'</code> characters around it.
	 */
	public static String toStringQuoted(String str) {
		if (str == null) {
			return "null";
		}
		return "\"" + str + "\"";
	}

	/**
	 * Gets a view for the iterable that creates an iterator that converts the elements of it to {@link String Strings}.
	 * <p>
	 * Any modifications made to the argument will reflect on the returned iterable. The returned iterable creates
	 * iterators that support removal if and only if the argument supports removals.
	 * 
	 * @param iterable
	 *            The iterable.
	 * @return An iterable that converts the elements to strings, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static Iterable<String> asStringIterable(Iterable<?> iterable) {
		if (iterable == null) {
			return null;
		}
		return new ToStringIterable(iterable);
	}

	/**
	 * Gets a forwarding iterator for the argument that converts the returned elements to {@link String Strings}.
	 * <p>
	 * The returned iterator creates iterators that support removal if and only if the argument supports removals.
	 * 
	 * @param iterator
	 *            The iterator.
	 * @return An iterator that converts the elements to strings, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static Iterator<String> asStringIterator(Iterator<?> iterator) {
		if (iterator == null) {
			return null;
		}
		return new ToStringIterator(iterator);
	}

	/**
	 * Gets the length of the argument character sequence, returning 0 if <code>null</code>.
	 * 
	 * @param str
	 *            The character sequence.
	 * @return The length of the argument.
	 */
	public static int length(CharSequence str) {
		return str == null ? 0 : str.length();
	}

	/**
	 * Counts the number of occurrences of a character in the given character sequence.
	 * <p>
	 * This method iterates over the characters of the sequence, and counts the occurrences of the specified character.
	 * 
	 * @param s
	 *            The character sequence.
	 * @param c
	 *            The character to count.
	 * @return The number of occurrences.
	 * @throws NullPointerException
	 *             If the char sequence is <code>null</code>.
	 */
	public static int count(CharSequence s, char c) throws NullPointerException {
		Objects.requireNonNull(s, "char sequence");
		int len = s.length();
		int result = 0;
		for (int i = 0; i < len; i++) {
			if (s.charAt(i) == c) {
				++result;
			}
		}
		return result;
	}

	/**
	 * Gets the index of the first occurrence of the given character in the argument char sequence.
	 * 
	 * @param cs
	 *            The char sequence to examine. May be <code>null</code> in which case the character won't be found.
	 * @param c
	 *            The char to search for.
	 * @return The index of the first occurrence or -1 if not found.
	 */
	public static int indexOf(CharSequence cs, char c) {
		if (cs == null) {
			return -1;
		}
		int len = cs.length();
		for (int i = 0; i < len; i++) {
			if (cs.charAt(i) == c) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Gets the index of the last occurrence of the given character in the argument char sequence.
	 * 
	 * @param cs
	 *            The char sequence to examine. May be <code>null</code> in which case the character won't be found.
	 * @param c
	 *            The char to search for.
	 * @return The index of the last occurrence or -1 if not found.
	 */
	public static int lastIndexOf(CharSequence cs, char c) {
		if (cs == null) {
			return -1;
		}
		int len = cs.length();
		for (int i = len - 1; i >= 0; i--) {
			if (cs.charAt(i) == c) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Checks if the argument char sequence consists of only the given character.
	 * <p>
	 * An empty sequence is considered to consist only of the given character.
	 * 
	 * @param s
	 *            The char sequence to examine.
	 * @param c
	 *            The character to examine for.
	 * @return <code>true</code> if the char sequence is non-<code>null</code> and contains only the given character.
	 */
	public static boolean isConsistsOnlyChar(CharSequence s, char c) {
		if (s == null) {
			return false;
		}
		int l = s.length();
		for (int i = 0; i < l; i++) {
			if (s.charAt(i) != c) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if the given string ends with the specified prefix in a case-insensitive manner.
	 * 
	 * @param str
	 *            The string to examine.
	 * @param ending
	 *            The ending to check.
	 * @return <code>true</code> if the string ends with the prefix.
	 * @throws NullPointerException
	 *             If the ending is <code>null</code>.
	 */
	public static boolean endsWithIgnoreCase(String str, String ending) throws NullPointerException {
		if (str == null) {
			return false;
		}
		Objects.requireNonNull(ending, "ending");
		int baselen = str.length();
		int endlen = ending.length();
		if (baselen < endlen) {
			return false;
		}
		return str.regionMatches(true, baselen - endlen, ending, 0, endlen);
	}

	/**
	 * Checks if the given string starts with the specified prefix in a case-insensitive manner.
	 * 
	 * @param str
	 *            The string to examine.
	 * @param prefix
	 *            The prefix to check.
	 * @return <code>true</code> if the string starts with the prefix.
	 * @throws NullPointerException
	 *             If the prefix is <code>null</code>.
	 */
	public static boolean startsWithIgnoreCase(String str, String prefix) throws NullPointerException {
		if (str == null) {
			return false;
		}
		Objects.requireNonNull(prefix, "prefix");
		int baselen = str.length();
		int startinglen = prefix.length();
		if (baselen < startinglen) {
			return false;
		}
		return str.regionMatches(true, 0, prefix, 0, startinglen);
	}

	/**
	 * Checks if the argument string has an integral format.
	 * <p>
	 * A string is considered to be in integral format if it only contains numeric characters in the range of
	 * <code>'0' - '9'</code>, and may be optionally prefixed by a sign character either <code>'-'</code> or
	 * <code>'+'</code>.
	 * <p>
	 * A string that only contains a single sign character is not considered to be integral.
	 * 
	 * @param s
	 *            The string to examine.
	 * @return <code>true</code> if the argument is non-<code>null</code> and has the integral format defined by the
	 *             above rules.
	 */
	public static boolean isIntegralString(String s) {
		if (ObjectUtils.isNullOrEmpty(s)) {
			return false;
		}
		int len = s.length();
		char firstchar = s.charAt(0);
		if (firstchar == '+' || firstchar == '-') {
			if (len == 1) {
				//only a single sign character
				return false;
			}
		} else if (firstchar < '0' || firstchar > '9') {
			//the first character is not a sign, nor a number
			return false;
		}
		for (int i = 1; i < len; i++) {
			char c = s.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
		}
		return true;
	}

	/**
	 * Creates a string that has only the given character repeated in it the given number of times.
	 * 
	 * @param c
	 *            The character to repeat.
	 * @param count
	 *            The number of times to repeat the character.
	 * @return The string containing the repeated character.
	 */
	public static String repeatCharacter(char c, int count) {
		char[] chars = new char[count];
		Arrays.fill(chars, c);
		return new String(chars);
	}

	/**
	 * Removes the specified number of characters from the end of the given string.
	 * 
	 * @param str
	 *            The string.
	 * @param count
	 *            The number of characters to remove from the end.
	 * @return The string without the characters which were removed.
	 * @throws NullPointerException
	 *             If the string is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the string is shorter than the number of characters to remove.
	 * @throws IllegalArgumentException
	 *             If the count is negative.
	 */
	public static String removeFromEnd(String str, int count)
			throws NullPointerException, IndexOutOfBoundsException, IllegalArgumentException {
		Objects.requireNonNull(str, "str");
		if (count < 0) {
			throw new IllegalArgumentException("Count is negative: " + count);
		}
		int len = str.length();
		int endidx = len - count;
		if (endidx < 0) {
			throw new IndexOutOfBoundsException(
					"String is shorter than characters to remove: \"" + str + "\" : " + count);
		}
		return str.substring(0, endidx);
	}

	/**
	 * Gets a {@link CharSequence} view for the argument character array.
	 * <p>
	 * The returned {@link CharSequence} will use the argument array for its underlying data.
	 * <p>
	 * Any modifications made to the array will be reflected on the returned char sequence.
	 * <p>
	 * The method may return a shared singleton object if the argument is empty.
	 * 
	 * @param array
	 *            The array of characters.
	 * @return A character sequence that is backed by the array.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static CharSequence asCharSequence(char[] array) throws NullPointerException {
		Objects.requireNonNull(array, "array");
		if (array.length == 0) {
			return "";
		}
		return new ArrayCharSequence(array);
	}

	/**
	 * Gets a {@link CharSequence} view for the argument character array with the given range.
	 * <p>
	 * The returned {@link CharSequence} will use the argument array for its underlying data.
	 * <p>
	 * Any modifications made to the array in the given range will be reflected on the returned char sequence.
	 * <p>
	 * The method may return a shared singleton object if the specified range is empty.
	 * 
	 * @param array
	 *            The array of characters.
	 * @param offset
	 *            The offset at which the interested sequence starts.
	 * @param length
	 *            The number of characters in the sequence.
	 * @return A character sequence that is backed by the array range.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds for the array.
	 */
	public static CharSequence asCharSequence(char[] array, int offset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(array, offset, length);
		if (length == 0) {
			return "";
		}
		return new ArrayRangeCharSequence(array, offset, length);
	}

	/**
	 * Gets a sub char sequence of the argument with the given range.
	 * <p>
	 * This method can be used when the caller doesn't want to use {@link CharSequence#subSequence(int, int)}. This
	 * might be the case when the charsequence implementation would copy large amounts of data, create unnecessarily
	 * deep object hierarchy, or other arbitrary reasons.
	 * <p>
	 * This method will return a char sequence that forwards its calls to its subject. Any subsequence that is created
	 * on the result will not create long chaing of references, but will use the argument char sequence, only modifying
	 * the view range.
	 * <p>
	 * If the argument char sequence is already a type of the same type that this method would return, this will only
	 * call {@link CharSequence#subSequence(int, int)} on it, therefore there is no preformance penalty if this method
	 * is called with an object that was returned by this method.
	 * <p>
	 * <b>Warning:</b> This method takes an <code>offset-length</code> range instead of a <code>start-end</code> range,
	 * unlike {@link CharSequence#subSequence(int, int)}.
	 * <p>
	 * If the created range is empty, this method may return a singleton shared object.
	 * 
	 * @param cs
	 *            The char sequence to create a sub sequence of.
	 * @param offset
	 *            The offset at which the sequence starts. (inclusive)
	 * @param length
	 *            The length of the sub sequence.
	 * @return A subsequence that is backed by the argument contents.
	 * @throws NullPointerException
	 *             If the argument sequence is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static CharSequence subCharSequence(CharSequence cs, int offset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(cs, "charsequence");
		if (length == 0) {
			return "";
		}
		if (cs instanceof SubCharSequence) {
			return ((SubCharSequence) cs).subOffsetLengthSequence(offset, length);
		}
		int cslen = cs.length();
		ArrayUtils.requireArrayRangeLength(cslen, offset, length);
		return new SubCharSequence(cs, offset, length);
	}

	/**
	 * Creates an iterator that takes the argument sequence, and splits it up based on the given character.
	 * <p>
	 * The returned iterator will iterate over the split parts that doesn't contain the given character.
	 * <p>
	 * If multiple split characters occur after each other, the returned iterator will return empty sequences for the
	 * inner parts. If the character is at the start or end of the string, a preceeding and following empty sequence
	 * will be returned.
	 * 
	 * @param charsequence
	 *            The char sequence to split.
	 * @param c
	 *            The character to split by.
	 * @return An iterator that splits up the sequence.
	 * @throws NullPointerException
	 *             If the argument sequence is <code>null</code>.
	 */
	public static Iterator<? extends CharSequence> splitCharSequenceIterator(CharSequence charsequence, char c)
			throws NullPointerException {
		Objects.requireNonNull(charsequence, "char sequence");
		return new CharSplitIterator(charsequence, c);
	}

	/**
	 * Creates an iterable that creates iterators that splits up the argument sequence by the given character.
	 * <p>
	 * The returned iterable will create iterators that work in the same way as
	 * {@link #splitCharSequenceIterator(CharSequence, char)}.
	 * 
	 * @param charsequence
	 *            The char sequence to split.
	 * @param c
	 *            The character to split by.
	 * @return An iterable that creates iterators which splits up the sequence.
	 * @throws NullPointerException
	 *             If the argument sequence is <code>null</code>.
	 */
	public static Iterable<? extends CharSequence> splitCharSequenceIterable(CharSequence charsequence, char c)
			throws NullPointerException {
		Objects.requireNonNull(charsequence, "char sequence");
		return new CharSplitIterable(charsequence, c);
	}

	/**
	 * Compares two string in natural order with possible <code>null</code> values.
	 * <p>
	 * Any <code>null</code> values are ordered first.
	 * 
	 * @param l
	 *            The first string.
	 * @param r
	 *            The second string.
	 * @return The comparison result.
	 * @see Comparator#compare(Object, Object)
	 */
	public static int compareStringsNullFirst(String l, String r) {
		if (l == r) {
			return 0;
		}
		if (l == null) {
			return -1;
		}
		if (r == null) {
			return 1;
		}
		return l.compareTo(r);
	}

	/**
	 * Compares two string in natural order with possible <code>null</code> values.
	 * <p>
	 * Any <code>null</code> values are ordered last.
	 * 
	 * @param l
	 *            The first string.
	 * @param r
	 *            The second string.
	 * @return The comparison result.
	 * @see Comparator#compare(Object, Object)
	 */
	public static int compareStringsNullLast(String l, String r) {
		if (l == r) {
			return 0;
		}
		if (l == null) {
			return 1;
		}
		if (r == null) {
			return -1;
		}
		return l.compareTo(r);
	}

	/**
	 * Compares two string in natural order in an ignore-case manner with possible <code>null</code> values.
	 * <p>
	 * Any <code>null</code> values are ordered first.
	 * 
	 * @param l
	 *            The first string.
	 * @param r
	 *            The second string.
	 * @return The comparison result.
	 * @see Comparator#compare(Object, Object)
	 * @see String#compareToIgnoreCase(String)
	 */
	public static int compareStringsNullFirstIgnoreCase(String l, String r) {
		if (l == r) {
			return 0;
		}
		if (l == null) {
			return -1;
		}
		if (r == null) {
			return 1;
		}
		return l.compareToIgnoreCase(r);
	}

	/**
	 * Compares two string in natural order in an ignore-case manner with possible <code>null</code> values.
	 * <p>
	 * Any <code>null</code> values are ordered last.
	 * 
	 * @param l
	 *            The first string.
	 * @param r
	 *            The second string.
	 * @return The comparison result.
	 * @see Comparator#compare(Object, Object)
	 * @see String#compareToIgnoreCase(String)
	 */
	public static int compareStringsNullLastIgnoreCase(String l, String r) {
		if (l == r) {
			return 0;
		}
		if (l == null) {
			return 1;
		}
		if (r == null) {
			return -1;
		}
		return l.compareToIgnoreCase(r);
	}

	/**
	 * Gets the comparator that compares the argument strings by natural order, and orders <code>null</code>s first if
	 * any.
	 * 
	 * @return The comparator.
	 */
	public static Comparator<String> nullsFirstStringComparator() {
		return Functionals.nullsFirstNaturalComparator();
	}

	/**
	 * Gets the comparator that compares the argument strings by natural order, and orders <code>null</code>s last if
	 * any.
	 * 
	 * @return The comparator.
	 */
	public static Comparator<String> nullsLastStringComparator() {
		return Functionals.nullsLastNaturalComparator();
	}

	/**
	 * Gets a string that immediately follows the argument string given that they're ordered by natural order.
	 * <p>
	 * For the input string S, and result R, there are no string X for which the following is <code>true</code>:
	 * 
	 * <pre>
	 * S &lt; X &lt; R
	 * </pre>
	 * 
	 * The returned string R will compare to be greater than the argument.
	 * <p>
	 * The returned string may have characters that are not displayable, or otherwise semantically incorrect. It should
	 * not be used in any other way than comparing other strings to it.
	 * 
	 * @param s
	 *            The string to get the next in order for.
	 * @return The string that is next in order compared to the argument.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static String nextInNaturalOrder(String s) throws NullPointerException {
		Objects.requireNonNull(s, "string");
		if (s.isEmpty()) {
			return "\0";
		}
		int len = s.length();
		char[] res = new char[len + 1];
		s.getChars(0, len, res, 0);
		return new String(res);
	}

	/**
	 * Converts the contents of the argument array to hexadecimal string representation.
	 * <p>
	 * Each byte will be split into two hexadecimal characters, where the 4 high order bits is the first character, and
	 * the 4 low order bits are the second.
	 * 
	 * @param array
	 *            The array of bytes.
	 * @return The hexadecimal representation of the argument bytes.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static String toHexString(byte[] array) throws NullPointerException {
		Objects.requireNonNull(array, "array");
		return toHexString(array, 0, array.length);
	}

	/**
	 * Converts the contents of the argument array to hexadecimal string representation and appends it to the given
	 * string builder.
	 * <p>
	 * Each byte will be split into two hexadecimal characters, where the 4 high order bits is the first character, and
	 * the 4 low order bits are the second.
	 * 
	 * @param array
	 *            The array of bytes.
	 * @param sb
	 *            The string builder to append the hexadecimal representation to.
	 * @throws NullPointerException
	 *             If the array or string builder is <code>null</code>.
	 */
	public static void toHexString(byte[] array, StringBuilder sb) throws NullPointerException {
		Objects.requireNonNull(array, "array");
		toHexString(array, 0, array.length, sb);
	}

	/**
	 * Converts the contents in the given range of the argument array to hexadecimal string representation.
	 * <p>
	 * Each byte will be split into two hexadecimal characters, where the 4 high order bits is the first character, and
	 * the 4 low order bits are the second.
	 * 
	 * @param array
	 *            The array of bytes.
	 * @param start
	 *            The starting index of the range. (inclusive)
	 * @param end
	 *            The end index of the range. (exclusive)
	 * @return The hexadecimal representation of the bytes in the given range.
	 * @throws NullPointerException
	 *             If the array or string builder is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the range is out of bounds for the given array.
	 */
	public static String toHexString(byte[] array, int start, int end)
			throws NullPointerException, IndexOutOfBoundsException {
		StringBuilder sb = new StringBuilder((end - start) * 2);
		toHexString(array, start, end, sb);
		return sb.toString();
	}

	/**
	 * Converts the contents in the given range of the argument array to hexadecimal string representation and appends
	 * it to the given string builder.
	 * <p>
	 * Each byte will be split into two hexadecimal characters, where the 4 high order bits is the first character, and
	 * the 4 low order bits are the second.
	 * 
	 * @param array
	 *            The array of bytes.
	 * @param start
	 *            The starting index of the range. (inclusive)
	 * @param end
	 *            The end index of the range. (exclusive)
	 * @param sb
	 *            The string builder to append the hexadecimal representation to.
	 * @throws NullPointerException
	 *             If the array or string builder is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the range is out of bounds for the given array.
	 */
	public static void toHexString(byte[] array, int start, int end, StringBuilder sb)
			throws NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayStartEndRange(array, start, end);
		Objects.requireNonNull(sb, "string builder");
		for (int i = start; i < end; i++) {
			byte b = array[i];
			int l = (b >>> 4) & 0xF;
			int r = b & 0xF;
			sb.append(HEX_ARRAY[l]);
			sb.append(HEX_ARRAY[r]);
		}
	}

	/**
	 * Converts a hexadecimal digit to integral value.
	 * <p>
	 * The argument must be a character in the range of <code>'0' - '9'</code>, <code>'a' - 'f'</code>,
	 * <code>'A' - 'F'</code> which is the range of the characters of a number in base 16.
	 * 
	 * @param c
	 *            The character.
	 * @return The integral value of the base-16 digit.
	 * @throws IllegalArgumentException
	 *             If the argument is out of range.
	 */
	public static int hexCharToValue(char c) throws IllegalArgumentException {
		if (c <= '9') {
			if (c >= '0') {
				return c - '0';
			}
		} else if (c <= 'F') {
			if (c >= 'A') {
				return c - 'A' + 10;
			}
		} else if (c <= 'f') {
			if (c >= 'a') {
				return c - 'a' + 10;
			}
		}
		throw new IllegalArgumentException("Out of range: " + c + " with value of " + Integer.toUnsignedString(c));
	}

	/**
	 * Converts a hexadecimal character sequence back to byte array representation.
	 * <p>
	 * The argument sequence length must be a multiply of 2 and contain only hexadecimal characters.
	 * 
	 * @param s
	 *            The string to parse.
	 * @return The byte data parsed from the characters.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the length is not a multiply of 2, or the string contains non-hexadecimal characters.
	 * @see #toHexString(byte[])
	 * @see #hexCharToValue(char)
	 */
	public static byte[] parseHexString(CharSequence s) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(s, "char sequence");
		int len = s.length();
		if (len % 2 != 0) {
			throw new IllegalArgumentException("String length must be a multiply of 2. (" + len + ")");
		}
		byte[] b = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			char c1 = s.charAt(i);
			char c2 = s.charAt(i + 1);
			int i1 = hexCharToValue(c1);
			int i2 = hexCharToValue(c2);
			b[i / 2] = (byte) ((i1 << 4) | i2);
		}
		return b;
	}

	/**
	 * Creates an offset map for each line in the parameter string.
	 * <p>
	 * The parameter string is examined and each starting offset for each line is returned in the result array. The
	 * first element is always 0. The <code>n</code>th element in the array represents the offset for the
	 * <code>n</code>th line, and so on.
	 * <p>
	 * Each element in the resulting array represents the character offset in the parameter string where the given line
	 * starts.
	 * <p>
	 * The line ending is determined by the <code>'\n'</code> character.
	 * 
	 * @param data
	 *            The string to create the line map for.
	 * @return The line map.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static int[] getLineIndexMap(CharSequence data) throws NullPointerException {
		Objects.requireNonNull(data, "data");
		//rather iterate the string twice to count the lines beforehand, than keep a growing array
		int len = data.length();
		int[] result = new int[count(data, '\n') + 1];
		result[0] = 0;
		int li = 1;
		for (int i = 0; i < len; i++) {
			char c = data.charAt(i);
			if (c == '\n') {
				result[li++] = i + 1;
			}
		}
		return result;
	}

	/**
	 * Searches the index of the line based on an index map and a character offset.
	 * <p>
	 * The method determines which is the corresponding line for the given character offset.
	 * 
	 * @param lineindices
	 *            The line index map.
	 * @param offset
	 *            The character offset to search the line index of.
	 * @return The found line index.
	 * @throws IndexOutOfBoundsException
	 *             If offset is negative.
	 * @see #getLineIndexMap(CharSequence)
	 */
	public static int getLineIndex(int[] lineindices, int offset) throws IndexOutOfBoundsException {
		if (offset < 0) {
			throw new IndexOutOfBoundsException("offset < 0 (" + offset + ")");
		}
		int idx = Arrays.binarySearch(lineindices, offset);
		if (idx >= 0) {
			return idx;
		}
		return -(idx + 1) - 1;
	}

	/**
	 * Gets the position in the line for a given line index map and character offset.
	 * <p>
	 * This method searches the corresponding line for the offset and returns the offset in the found line.
	 * 
	 * @param lineindices
	 *            The line index map.
	 * @param offset
	 *            The character offset.
	 * @return The index of the character in the corresponding line.
	 * @see #getLineIndexMap(CharSequence)
	 * @see #getLineIndex(int[], int)
	 */
	public static int getLinePositionIndex(int[] lineindices, int offset) {
		return getLinePositionIndex(lineindices, getLineIndex(lineindices, offset), offset);
	}

	/**
	 * Gets the position in the line for a given line, index map, and offset.
	 * <p>
	 * The parameter line index should be a result of {@link #getLineIndex(int[], int)}. It is not sanity checked if the
	 * line index is valid input, and it is the responsibility of the caller to call this method with appropriate
	 * parameters. <br>
	 * This method is only publicly available to avoid searching the line index multiple times if not necessary. If you
	 * don't use the line index directly, consider using {@link #getLinePositionIndex(int[], int)} instead.
	 * 
	 * @param lineindices
	 *            The line index map.
	 * @param lineindex
	 *            The line index corresponding to the offset.
	 * @param offset
	 *            The character offset.
	 * @return The index of the character in the corresponding line.
	 * @throws IndexOutOfBoundsException
	 *             If the line index is out of bounds.
	 * @see #getLineIndexMap(CharSequence)
	 * @see #getLineIndex(int[], int)
	 * @see #getLinePositionIndex(int[], int)
	 */
	public static int getLinePositionIndex(int[] lineindices, int lineindex, int offset)
			throws IndexOutOfBoundsException {
		return offset - lineindices[lineindex];
	}

	private StringUtils() {
		throw new UnsupportedOperationException();
	}

	private static final char[] HEX_ARRAY = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e',
			'f' };
}
