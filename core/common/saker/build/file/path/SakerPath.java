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
package saker.build.file.path;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Objects;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.ArrayIterator;
import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ConcatIterator;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Immutable class representing an absolute or relative file path.
 * <p>
 * An instance consists of a root and given path names under that root. If the root is <code>null</code> then the path
 * is relative. The root can be one of the following format:
 * <ul>
 * <li>Simple <code>"/"</code> character as a {@link String}. Matching the Unix root drive semantics.</li>
 * <li>A root string in a drive format similar to Windows semantics. The drive name can have multiple characters, and
 * ends with the colon <code>':'</code> character. The drive letters are lowercase and in the range of
 * <code>'a' - 'z'</code>. E.g.: <code>"c:"</code>, <code>"drive:"</code>. <br>
 * When a path is constructed, the root name is always normalized to a lowercase format.</li>
 * </ul>
 * <p>
 * When parsing paths, they can contain the <code>"."</code> and <code>".."</code> path names which represent the
 * current and the parent paths accordingly. Absolute paths cannot escape their root name using the <code>".."</code>
 * path name. The paths are normalized during their construction.
 * <p>
 * Relative paths can contain only the special <code>".."</code> name at the start of it. Intermediate <code>".."</code>
 * names are normalized during resolution.
 * <p>
 * Absolute paths will not contains any of the above special path names. <br>
 * <code>"."</code> names are never present in any path as they are omitted during construction.
 * <p>
 * All methods in this class work in a case-sensitive manner unless indicated accordingly.
 * <p>
 * When an instance is constructed all backslash characters are treated as forward slash.
 * <p>
 * Examples for paths:
 * <ul>
 * <li><code>/home/User</code></li>
 * <li><code>c:/Users/User</code></li>
 * <li><code>drive:</code></li>
 * <li>Relative: <code>dir/child</code></li>
 * <li>Relative: <code>../sibling</code></li>
 * <li>Relative: <code>../in/parent/tree</code></li>
 * <li>Automatically normalized relative: <code>some/../directory/tree</code> will be normalized to
 * <code>directory/tree</code></li>
 * </ul>
 */
public final class SakerPath implements Comparable<SakerPath>, Externalizable, Cloneable {
	private static final long serialVersionUID = 1L;

	/**
	 * Path name representing the forward slash root.
	 */
	public static final String ROOT_SLASH = "/";
	/**
	 * Singleton instance that represents the {@linkplain #ROOT_SLASH slash root}.
	 */
	public static final SakerPath PATH_SLASH = valueOf(ROOT_SLASH);

	/**
	 * Single instance for an empty path.
	 */
	public static final SakerPath EMPTY = new SakerPath(null, ObjectUtils.EMPTY_STRING_ARRAY);
	/**
	 * Single instance for a relative parent path.
	 */
	public static final SakerPath PARENT = new SakerPath(null, new String[] { ".." });
	/**
	 * Single instance for the first absolute path that is ordered by the {@link Comparable} contract of this class.
	 * <p>
	 * All relative paths are ordered before this instance in the natural order.
	 */
	public static final SakerPath FIRST_ABSOLUTE_PATH = new SakerPath("", ObjectUtils.EMPTY_STRING_ARRAY);
	/**
	 * Single instance for the first path that is ordered by the {@link Comparable} contract of this class which root is
	 * not the slash drive.
	 * <p>
	 * All relative paths and all slash root paths are ordered before this instance in the natural order.
	 * <p>
	 * This instance does <i>not</i> represent a valid path for use.
	 */
	public static final SakerPath FIRST_NONSLASH_ROOT_PATH = new SakerPath(":", ObjectUtils.EMPTY_STRING_ARRAY);

	/**
	 * The root path of this instance.
	 */
	private String root;
	/**
	 * The backing list of the path names of this instance.
	 */
	private String[] names;

	private SakerPath(String root, String[] names) {
		this.root = root;
		this.names = names;
	}

	/**
	 * For {@link Externalizable}.
	 * <p>
	 * Creates the empty path.
	 */
	public SakerPath() {
	}

	/**
	 * Shallow clones this path instance.
	 * <p>
	 * All the backing fields are the same for the new instance as this one.
	 */
	@Override
	public SakerPath clone() {
		try {
			return (SakerPath) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError(e);
		}
	}

	/**
	 * Normalizes the parameter root for unified representation.
	 * <p>
	 * If the parameter is <code>"\\"</code> then it will return <code>"/"</code>.
	 * <p>
	 * In any other case the parameter will be converted to lowercase and any trailing slashes will be removed. It is
	 * ensured that the parameter is in a valid root format defined by this class.
	 * 
	 * @param root
	 *            The root to normalize.
	 * @return The normalized root.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the root format is invalid.
	 */
	public static String normalizeRoot(String root) throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(root, "root");

		int len = root.length();
		if (len == 0) {
			throw new InvalidPathFormatException("Invalid root: " + root);
		}
		if (len == 1) {
			if (!isSlashCharacter(root.charAt(0))) {
				throw new InvalidPathFormatException("Invalid root: " + root);
			}
			return ROOT_SLASH;
		}
		String checkroot;
		char lastchar = root.charAt(len - 1);
		if (isSlashCharacter(lastchar)) {
			//remove the trailing slash
			checkroot = root.substring(0, len - 1);
		} else {
			checkroot = root;
		}
		if (!isValidRootName(checkroot)) {
			throw new InvalidPathFormatException("Invalid root: " + root);
		}
		return checkroot.toLowerCase();
	}

	/**
	 * Parses the parameter {@link String} and resolves it against the parameter path is it is relative.
	 * <p>
	 * This function parses the passed {@link String} parameter and constructs a valid immutable {@link SakerPath}
	 * instance. The path names are semantically checked and an exception is thrown if they are in an illegal format.
	 * <p>
	 * The parsed path may be relative or absolute. If it is relative then it will be resolved against the passed path
	 * parameter if not <code>null</code>.
	 * 
	 * @param relativeresolve
	 *            The path to resolve the result against if is relative. May be <code>null</code>.
	 * @param path
	 *            The path to parse.
	 * @return The parsed path.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             In case of an invalid path name.
	 * @see #resolve(SakerPath)
	 */
	public static SakerPath valueOf(SakerPath relativeresolve, String path)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(path, "path");

		int len = path.length();

		if (len == 0) {
			if (relativeresolve != null) {
				return relativeresolve;
			}
			return EMPTY;
		}
		List<String> names = new ArrayList<>();
		StringBuilder sb = new StringBuilder(path.length());
		String root = null;
		int i = 0;
		root_finder:
		{
			boolean hadcolon = false;
			for (; i < len; i++) {
				char c = path.charAt(i);
				if (c == '/' || c == '\\') {
					int sblen = sb.length();
					if (sblen == 0) {
						root = ROOT_SLASH;
					} else {
						if (hadcolon) {
							if (sblen == 1) {
								throw new InvalidPathFormatException("Invalid root: " + sb.toString());
							}
							root = sb.toString();
						} else {
							//no colon found at the end of drive name
							names.add(path.substring(0, i));
						}
					}
					//end of the first segment
					//increment i after the slash as it wont be incremented when breaking out
					++i;
					sb.setLength(0);
					break root_finder;
				}
				if (hadcolon) {
					//colon must be a last character in a root name, or the path is invalid
					throw new InvalidPathFormatException(
							"':' character in path name at index: " + i + ". (" + path + ")");
				}
				if (c == ':') {
					hadcolon = true;
					sb.append(':');
				} else if (c >= 'a' && c <= 'z') {
					//a valid drive character
					sb.append(c);
				} else if (c >= 'A' && c <= 'Z') {
					//upper case drive character, make it lowercase
					sb.append((char) (c - 'A' + 'a'));
				} else {
					//invalid character in drive name
					//reset the parsing as the characters might be converted to lower case
					sb.setLength(0);
					++i;
					sb.append(path, 0, i);
					break root_finder;
				}
			}
			//no trailing slash
			//single path name
			//sb contains the path name
			//it is a valid path name
			if (hadcolon) {
				if (sb.length() == 1) {
					throw new InvalidPathFormatException("Invalid root: " + sb.toString());
				}
				root = sb.toString();
				return new SakerPath(root, ObjectUtils.EMPTY_STRING_ARRAY);
			}
			if (relativeresolve != null) {
				return relativeresolve.resolveValidatedImpl(path);
			}
			//no colon was at the end of the only path name
			return new SakerPath(null, new String[] { path });
		}
		addSplitPathNames(root, names, path, i, len, sb);
		if (relativeresolve != null && root == null) {
			return relativeresolve.resolveValidatedImpl(names);
		}
		return new SakerPath(root, names.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
	}

	/**
	 * Parses the parameter String and creates a path.
	 * <p>
	 * Equivalent to calling {@link #valueOf(SakerPath, String)} with <code>null</code> relative resolve parameter.
	 * 
	 * @param path
	 *            The path to parse.
	 * @return The parsed path.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             In case of an invalid path name.
	 * @see #valueOf(SakerPath, String)
	 */
	public static SakerPath valueOf(String path) throws NullPointerException, InvalidPathFormatException {
		return valueOf(null, path);
	}

	/**
	 * Creates a relative path with a single path name.
	 * <p>
	 * The resulting path will be relative and contain only the parameter path name.
	 * 
	 * @param path
	 *            The path name.
	 * @return The result path with single path name.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the parameter path name is not valid.
	 */
	public static SakerPath singleRelative(String path) throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(path, "path");
		if (".".equals(path)) {
			return EMPTY;
		}
		if ("..".equals(path)) {
			return PARENT;
		}
		if (!isValidPathName(path)) {
			throw new InvalidPathFormatException("Invalid path name: " + path);
		}
		SakerPath result = new SakerPath(null, new String[] { path });
		return result;
	}

	/**
	 * Creates a path from the given path argument.
	 * <p>
	 * The resulting path will be relative only if the argument path is relative. Roots are normalized and extraneous
	 * <code>"."</code> and <code>".."</code> path names are normalized.
	 * 
	 * @param path
	 *            The path to convert.
	 * @return The resulting path.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @see LocalFileProvider#toRealPath(SakerPath)
	 */
	public static SakerPath valueOf(Path path) throws NullPointerException {
		Objects.requireNonNull(path, "path");

		Path root = path.getRoot();
		if (root == null) {
			List<String> names = new ArrayList<>(path.getNameCount());
			for (Path p : path) {
				resolveRelative(names, p.toString());
			}
			return new SakerPath(null, names.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
		}
		String rootstr = normalizeRoot(root.toString());
		List<String> names = new ArrayList<>(path.getNameCount());
		for (Path p : path) {
			resolveAbsolute(names, p.toString());
		}
		return new SakerPath(rootstr, names.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
	}

	/**
	 * Returns <code>true</code> if the path is absolute.
	 * <p>
	 * It will only be <code>true</code> if {@link #getRoot()} is <code>null</code>.
	 * 
	 * @return <code>true</code> if the path is absolute.
	 */
	public boolean isAbsolute() {
		return getRoot() != null;
	}

	/**
	 * Forces the path to be relative by removing any root if present.
	 * <p>
	 * This method can be used to construct a path which contains the same names as this but has no root assigned.
	 * <p>
	 * The resulting path will be relative.
	 * <p>
	 * The method returns <code>this</code> if <code>this</code> is already relative.
	 * 
	 * @return A forced relativized path.
	 */
	public SakerPath forcedRelative() {
		if (isRelative()) {
			return this;
		}
		return new SakerPath(null, names);
	}

	/**
	 * Returns <code>true</code> if the path is relative.
	 * <p>
	 * It will only be <code>true</code> if {@link #getRoot()} is not <code>null</code>.
	 * 
	 * @return <code>true</code> if the path is relative.
	 */
	public boolean isRelative() {
		return getRoot() == null;
	}

	/**
	 * Returns if <code>this</code> is relative and resolving it against an other path will not escape to its parent
	 * path.
	 * <p>
	 * This method returns <code>true</code> if resolving this path against any given path X to result R, then
	 * <code>R.startsWith(X)</code> will return <code>true</code>.
	 * <p>
	 * Examples by resolving against <code>c:/Users</code>:
	 * <ul>
	 * <li>Forward relative: <code>dir/subdir -&gt; c:/Users/dir/subdir</code></li>
	 * <li>Forward relative: <code>dir/.. -&gt; c:/Users</code></li>
	 * <li>Forward relative: <code>dir/../otherdir -&gt; c:/Users/otherdir</code></li>
	 * <li>Not forward relative: <code>../dir -&gt; c:/dir</code></li>
	 * </ul>
	 * 
	 * @return <code>true</code> if this path is forward relative.
	 * @see #resolve(SakerPath)
	 * @see #startsWith(SakerPath)
	 */
	public boolean isForwardRelative() {
		if (root != null) {
			//absolute paths are not relative
			return false;
		}
		if (names.length == 0) {
			//no names
			return true;
		}
		return !"..".equals(names[0]);
	}

	/**
	 * Returns an array copy of the path names of this path.
	 * 
	 * @return The names of this path in an array.
	 */
	public String[] getNameArray() {
		if (names.length == 0) {
			return ObjectUtils.EMPTY_STRING_ARRAY;
		}
		return names.clone();
	}

	/**
	 * Returns an array copy of the optional root and path names of this path.
	 * <p>
	 * The root name will be the first element in the array if the path is absolute.
	 * <p>
	 * If the path is relative this method returns the same as {@link #getNameArray()}.
	 * 
	 * @return The array containing the root (optional) and path names.
	 */
	public String[] getPathArray() {
		if (root == null) {
			return getNameArray();
		}
		return ArrayUtils.prepended(names, root);
	}

	/**
	 * Returns an unmodifiable list of this path's optional root and path names of this path.
	 * 
	 * @return An unmodifiable list of path components.
	 */
	public List<String> getPathList() {
		if (root == null) {
			return getNameList();
		}
		//XXX create a prepend list that doesn't create a new array
		return ImmutableUtils.unmodifiableArrayList(ArrayUtils.prepended(names, root));
	}

	/**
	 * Returns an unmodifiable list of this path's names.
	 * 
	 * @return An unmodifiable list of path names.
	 */
	public List<String> getNameList() {
		return ImmutableUtils.unmodifiableArrayList(names);
	}

	/**
	 * Returns an unmodifiable list of this path's names in the given range.
	 * 
	 * @param startindex
	 *            The start index (inclusive).
	 * @param endindex
	 *            The end index (exclusive).
	 * @return An unmodifiable list of path names.
	 * @throws IndexOutOfBoundsException
	 *             If any if the indices are out of range.
	 */
	public List<String> getNameList(int startindex, int endindex) throws IndexOutOfBoundsException {
		return ImmutableUtils.unmodifiableArrayList(Arrays.copyOfRange(names, startindex, endindex));
	}

	/**
	 * Gets the root for this path.
	 * 
	 * @return The root of this path or <code>null</code> if relative.
	 */
	public String getRoot() {
		return root;
	}

	/**
	 * Returns a path that contains only the root of this path.
	 * <p>
	 * The resulting path will contain the root of <code>this</code> path and have no path names assigned. If this path
	 * is already contains no path names the result is <code>this</code>.
	 * <p>
	 * If <code>this</code> path is relative then the result will be the {@linkplain #EMPTY empty path} instance.
	 * 
	 * @return The path that contains only the root of this path.
	 */
	public SakerPath getRootPath() {
		if (root == null) {
			return EMPTY;
		}
		if (names.length == 0) {
			return this;
		}
		return new SakerPath(root, ObjectUtils.EMPTY_STRING_ARRAY);
	}

	/**
	 * Gets the last path name of this path if it represents a valid file name.
	 * <p>
	 * The last path name represents the file name of the location represented by this path.
	 * <p>
	 * If <code>this</code> path is relative and only consists of <code>".."</code> names then the result will be
	 * <code>null</code> as there is no explicit file name defined.
	 * 
	 * @return The file name for this path or <code>null</code> if the file name cannot be determined.
	 */
	public String getFileName() {
		if (names.length == 0) {
			return null;
		}
		String last = names[names.length - 1];
		if ("..".equals(last)) {
			//if the path consists only ".." names, then there is no actual filename
			return null;
		}
		return last;
	}

	/**
	 * Gets the last path name in this path.
	 * <p>
	 * This method is similar to {@link #getFileName()}, but does not examine the returned name if it is actually a
	 * valid file name. If the last name is <code>".."</code> then it will be returned.
	 * 
	 * @return The last path name or <code>null</code> if this path contains none.
	 */
	public String getLastName() {
		if (names.length == 0) {
			return null;
		}
		return names[names.length - 1];
	}

	/**
	 * Returns the path which represents the parent of this path or <code>null</code> if it cannot exist.
	 * <p>
	 * The parent of a path is determined by resolving the <code>".."</code> path name against it. If the parent path
	 * cannot exist (e.g. <code>this</code> is a simple root without subfolders like <code>"/"</code> or
	 * <code>"c:"</code>) this function returns <code>null</code>.
	 * <p>
	 * Relative paths always have parent paths.
	 * 
	 * @return The parent path or <code>null</code> if it cannot exist.
	 */
	public SakerPath getParent() {
		if (isAbsolute()) {
			if (names.length == 0) {
				return null;
			}
			String[] nlist = Arrays.copyOfRange(names, 0, names.length - 1);
			return new SakerPath(root, nlist);
		}
		//relative path
		List<String> nlist = ObjectUtils.newArrayList(names);
		resolveRelativeParent(nlist);
		return new SakerPath(null, nlist.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
	}

	/**
	 * Gets the path name count in this path.
	 * 
	 * @return The name count.
	 */
	public int getNameCount() {
		return names.length;
	}

	/**
	 * Gets the name at the specified index.
	 * 
	 * @param index
	 *            The index of the requested path name.
	 * @return The path name at index.
	 * @throws IndexOutOfBoundsException
	 *             if the index is out of range
	 */
	public String getName(int index) throws IndexOutOfBoundsException {
		return names[index];
	}

	/**
	 * Finds the first occurence of the parameter path name in <code>this</code> path.
	 * 
	 * @param pathname
	 *            The path name to find.
	 * @return The index of the path name if found or -1 if not.
	 */
	public int indexOfName(String pathname) {
		return ArrayUtils.arrayIndexOf(names, pathname);
	}

	/**
	 * Creates a relative subpath starting from the given index.
	 * 
	 * @param beginindex
	 *            The first index (inclusive).
	 * @return The relative subpath.
	 * @throws IndexOutOfBoundsException
	 *             If the indices are out of range.
	 * @see #subPath(String, int, int)
	 * @see #getNameCount()
	 */
	public SakerPath subPath(int beginindex) throws IndexOutOfBoundsException {
		return subPath(null, beginindex, names.length);
	}

	/**
	 * Creates a relative subpath in the given range.
	 * 
	 * @param beginindex
	 *            The first index (inclusive).
	 * @param endindex
	 *            The last index (exclusive).
	 * @return The relative subpath.
	 * @throws IndexOutOfBoundsException
	 *             If the indices are out of range.
	 * @see #subPath(String, int, int)
	 * @see #getNameCount()
	 */
	public SakerPath subPath(int beginindex, int endindex) throws IndexOutOfBoundsException {
		return subPath(null, beginindex, endindex);
	}

	/**
	 * Creates a subpath starting from the given index with a specific root.
	 * 
	 * @param root
	 *            The root for the resulting path to use or <code>null</code>.
	 * @param beginindex
	 *            The first index (inclusive).
	 * @return The relative subpath.
	 * @throws IndexOutOfBoundsException
	 *             If the indices are out of range.
	 * @see #subPath(String, int, int)
	 * @see #getNameCount()
	 */
	public SakerPath subPath(String root, int beginindex) throws IndexOutOfBoundsException {
		return new SakerPath(root, Arrays.copyOfRange(names, beginindex, names.length));
	}

	/**
	 * Creates a subpath in the given range with a specific root.
	 * 
	 * @param root
	 *            The root for the resulting path to use or <code>null</code>.
	 * @param beginindex
	 *            The first index (inclusive).
	 * @param endindex
	 *            The last index (exclusive).
	 * @return The relative subpath.
	 * @throws IndexOutOfBoundsException
	 *             If the indices are out of range.
	 * @see #getNameCount()
	 */
	public SakerPath subPath(String root, int beginindex, int endindex) throws IndexOutOfBoundsException {
		return new SakerPath(root, Arrays.copyOfRange(names, beginindex, endindex));
	}

	/**
	 * Checks if the parameter path can be relativized against this path.
	 * <p>
	 * This method requires the following conditions to be met to return <code>true</code>:
	 * <p>
	 * <ul>
	 * <li>Both path have the same root.</li>
	 * <li>If the paths are relative then <code>this</code> path must have less or equal number of <code>".."</code>
	 * path names at the start of it than the parameter.
	 * <p>
	 * E.g. <code>"../dir"</code> is not relativizable against <code>"some/otherdir"</code> as a path cannot be
	 * constructed in order to navigate outside of the <code>".."</code> name at the start.</li>
	 * </ul>
	 * 
	 * @param other
	 *            The path to check relativization against.
	 * @return <code>true</code> if the paths are relativizable.
	 */
	public boolean isRelativizable(SakerPath other) {
		boolean rooteq = Objects.equals(root, other.root);
		if (!rooteq) {
			//different roots, not relativizable
			return false;
		}
		if (this.root == null) {
			//relativization between relative paths
			//if this path has more ".." path names at the start than other then the relativiztation cannot be done
			//    e.g. if we relativize "../dir" against "somedir" then we cannot construct a path from the first to the second, as we cannot determine
			//         a path name to negate ".." part at the start. The result could be something like "../X/somedir", but X cannot be determined.
			int c = this.getNameCount();
			int oc = other.getNameCount();
			for (int i = 0; i < c; i++) {
				if (!"..".equals(this.names[i])) {
					return true;
				}
				if (i >= oc) {
					//we contain more ".." path names than other
					return false;
				}
				if (!"..".equals(other.names[i])) {
					//we contain more ".." path names than other
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Relativizes <code>this</code> path against the parameter.
	 * <p>
	 * The result of this operation constructs a path which is resolved against this will result in the parameter path.
	 * <p>
	 * The following will be <code>true</code> for any two relativizable paths:
	 * 
	 * <pre>
	 * SakerPath firt = ...;
	 * SakerPath other = ...;
	 * // the following is true:
	 * first.resolve(first.relativize(other)).equals(other);
	 * </pre>
	 * 
	 * Examples:
	 * <ul>
	 * <li><code>"/home"</code> relativized against <code>"/home/user"</code> is <code>"user"</code></li>
	 * <li><code>"/home/user"</code> relativized against <code>"/home"</code> is <code>".."</code></li>
	 * <li><code>"/home/user"</code> relativized against <code>"/home/user"</code> is <code>""</code></li>
	 * <li><code>"/home/user"</code> relativized against <code>"/home/john"</code> is <code>"../john"</code></li>
	 * </ul>
	 * The same applies for paths without a root name.
	 * 
	 * @param other
	 *            The path to relativize against.
	 * @return The relativized path.
	 * @throws IllegalArgumentException
	 *             If the paths are not relativizable. (See {@link #isRelativizable})
	 */
	public SakerPath relativize(SakerPath other) throws IllegalArgumentException {
		if (!isRelativizable(other)) {
			throw new IllegalArgumentException("Cannot relativize paths: " + this + " against " + other);
		}
		return relativizeImpl(other);
	}

	/**
	 * Tries to relativize <code>this</code> path against the parameter.
	 * <p>
	 * If the paths are not relativizable, the parameter path is returned.
	 * 
	 * @param other
	 *            The path to relativize against.
	 * @return The relativized path if succeeded, or the parameter reference.
	 */
	public SakerPath tryRelativize(SakerPath other) {
		if (!isRelativizable(other)) {
			return other;
		}
		return relativizeImpl(other);
	}

	/**
	 * Returns the common path of this and the parameter path.
	 * <p>
	 * This works in the same way as {@link #getCommonSubPath(SakerPath)}, but will extract the common part of both
	 * paths as a result.
	 * <p>
	 * If the roots differ then the result will be <code>null</code>.
	 * 
	 * @param other
	 *            The path to compare with.
	 * @return The common sub path or <code>null</code> if the roots differ.
	 * @see #getCommonNameCount(SakerPath)
	 */
	public SakerPath getCommonSubPath(SakerPath other) {
		if (!Objects.equals(this.root, other.root)) {
			return null;
		}
		//roots are the same
		int thisnc = this.names.length;
		if (thisnc == 0) {
			return this;
		}
		int othernc = other.names.length;
		if (othernc == 0) {
			return other;
		}

		int nc = Math.min(thisnc, othernc);
		for (int i = 0; i < nc; i++) {
			if (!this.names[i].equals(other.names[i])) {
				return subPath(this.root, 0, i);
			}
		}
		if (nc == thisnc) {
			return this;
		}
		return other;
	}

	/**
	 * Counts how many common starting path names does this and the parameter have.
	 * <p>
	 * This method computes how many common starting path names does this path have with the parameter.
	 * <p>
	 * If the paths have different roots, then the result will be -1.
	 * <p>
	 * Else this method will return the number of matching path names at the start of both paths.
	 * 
	 * @param other
	 *            The path to compare with.
	 * @return -1 if the roots differ, else common starting path name count.
	 */
	public int getCommonNameCount(SakerPath other) {
		if (!Objects.equals(this.root, other.root)) {
			return -1;
		}
		//roots are the same
		int nc = Math.min(this.names.length, other.names.length);
		for (int i = 0; i < nc; i++) {
			if (!this.names[i].equals(other.names[i])) {
				return i;
			}
		}
		return nc;
	}

	/**
	 * Checks if this path starts with the parameter path.
	 * <p>
	 * Returns <code>true</code> if both paths have the same roots and all of the path names of the parameter occurs at
	 * the start of <code>this</code> path.
	 * <p>
	 * This method works in a case-sensitive manner.
	 * 
	 * @param other
	 *            The base path to check for.
	 * @return If this path starts with the parameter.
	 */
	public boolean startsWith(SakerPath other) {
		int othernamecount = other.names.length;
		if (othernamecount > this.names.length || !Objects.equals(root, other.root)) {
			return false;
		}
		for (int i = 0; i < othernamecount; i++) {
			if (!this.names[i].equals(other.names[i])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if the argument path represents a location that is under <code>this</code> path.
	 * <p>
	 * This method works in the same way as {@link #startsWith(SakerPath)}, but in reverse order.
	 * 
	 * @param other
	 *            The path to test.
	 * @return <code>true</code> if the argument is a child path of <code>this</code>.
	 * @see #startsWith(SakerPath)
	 */
	public boolean isChildPath(SakerPath other) {
		return other.startsWith(this);
	}

	/**
	 * Resolves the given path names against this path.
	 * <p>
	 * Any directory separator characters are normalized.
	 * 
	 * @param paths
	 *            The paths to resolve.
	 * @return The resolved path.
	 * @see #resolve(SakerPath)
	 */
	public SakerPath resolve(Iterable<String> paths) {
		List<String> nnames = ObjectUtils.newArrayList(this.names);
		StringBuilder sb = new StringBuilder();
		for (String path : paths) {
			addSplitPathNames(this.root, nnames, path, sb);
		}
		return new SakerPath(this.root, nnames.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
	}

	/**
	 * Resolves the given path names against this path.
	 * <p>
	 * Any directory separator characters are normalized.
	 * 
	 * @param path
	 *            The path to resolve.
	 * @return The resolved path.
	 * @see #resolve(SakerPath)
	 */
	public SakerPath resolve(String path) {
		List<String> nnames = ObjectUtils.newArrayList(this.names);
		addSplitPathNames(this.root, nnames, path);
		return new SakerPath(this.root, nnames.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
	}

	/**
	 * Resolves the given path names against this path.
	 * <p>
	 * Any directory separator characters are normalized.
	 * 
	 * @param paths
	 *            The paths to resolve.
	 * @return The resolved path.
	 * @see #resolve(SakerPath)
	 */
	public SakerPath resolve(String... paths) {
		List<String> nnames = ObjectUtils.newArrayList(this.names);
		StringBuilder sb = new StringBuilder();
		for (String path : paths) {
			addSplitPathNames(this.root, nnames, path, sb);
		}
		return new SakerPath(this.root, nnames.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
	}

	/**
	 * Resolves the given relative argument against this path.
	 * <p>
	 * Path resolution is a binary operation which results in a single path that is the result of the first path and the
	 * second path concatenated. During concatenation any semantically significant path names are normalized
	 * (<code>"."</code> and <code>".."</code>)
	 * <p>
	 * One can look at this process as navigating to the first path in the filesystem, then navigating according to the
	 * second path. The result path is taken using the current path after the navigation.
	 * <p>
	 * If this path is absolute, and the argument would escape the root of this path, then an
	 * {@link InvalidPathFormatException} is thrown. (E.g.: this path is <code>/home</code> and argument is
	 * <code>../../file</code>)
	 * 
	 * @param other
	 *            The path to resolve.
	 * @return The parameter path resolved against <code>this</code>.
	 * @throws InvalidPathFormatException
	 *             If the parameter is not relative, or the argument cannot be resolved against this path.
	 */
	public SakerPath resolve(SakerPath other) throws InvalidPathFormatException {
		if (!other.isRelative()) {
			throw new InvalidPathFormatException("Argument is not relative path: " + other);
		}
		if (other.names.length == 0) {
			return this;
		}
		if (this.root == null && this.names.length == 0) {
			return other;
		}
		return resolveValidatedImpl(other.names);
	}

	/**
	 * Resolves the given relative argument against this path.
	 * 
	 * @param path
	 *            The path to resolve.
	 * @return The parameter path resolved against <code>this</code>.
	 * @throws InvalidPathFormatException
	 *             If the parameter is not relative, or the argument cannot be resolved against this path.
	 * @see #resolve(SakerPath)
	 */
	public SakerPath resolve(Path path) throws InvalidPathFormatException {
		if (path.isAbsolute()) {
			throw new InvalidPathFormatException("Argument is not relative path: " + path);
		}
		List<String> nnames = new ArrayList<>(this.names.length + path.getNameCount());
		for (String n : this.names) {
			nnames.add(n);
		}
		if (this.root == null) {
			//relative path
			for (Path p : path) {
				resolveRelative(nnames, p.toString());
			}
		} else {
			for (Path p : path) {
				resolveAbsolute(nnames, p.toString());
			}
		}
		return new SakerPath(this.root, nnames.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
	}

	/**
	 * Resolves the path to a sibling name.
	 * <p>
	 * Sibling resolution is taking the parent of <code>this</code> and then resolving the parameter against it.
	 * <p>
	 * The sibling resolution will not escape root boundaries and works for relative paths too.
	 * <p>
	 * This method is generally useful when a direct sibling of a path is needed.
	 * <p>
	 * E.g. <br>
	 * Sibling of <code>"/home/user"</code> for <code>"john"</code> is <code>"/home/john"</code> <br>
	 * Sibling of <code>"/home/user"</code> for <code>"john/content"</code> is <code>"/home/john/content"</code> <br>
	 * Sibling of <code>"../directory"</code> for <code>"second"</code> is <code>../second"</code> <br>
	 * 
	 * @param path
	 *            The sibling path.
	 * @return The resolved path.
	 * @throws InvalidPathFormatException
	 *             If this path is absolute and has no path names.
	 */
	public SakerPath resolveSibling(String path) throws InvalidPathFormatException {
		return resolve("..", path);
	}

	/**
	 * Tries to resolve the parameter path against this path.
	 * <p>
	 * If the parameter is not relative, then it is returned without modification. Else the parameter path is resolved
	 * against <code>this</code>.
	 * 
	 * @param other
	 *            The path to resolve.
	 * @return The resolved path.
	 * @see #resolve(SakerPath)
	 */
	public SakerPath tryResolve(SakerPath other) {
		if (!other.isRelative()) {
			return other;
		}
		if (other.names.length == 0) {
			return this;
		}
		if (this.root == null && this.names.length == 0) {
			return other;
		}
		return resolveValidatedImpl(other.names);
	}

	/**
	 * Appends the parameter relative path to this path.
	 * <p>
	 * Appending differs from the resolving only that the resulting path is not normalized. It is the responsibility of
	 * the user to provide a path that is forward relative.
	 * 
	 * @param other
	 *            The parameter to append.
	 * @return The first path appended with the parameter.
	 * @throws InvalidPathFormatException
	 *             If the parameter is not relative, or contains semantically specific path names (E.g.
	 *             <code>".."</code>).
	 * @see #isForwardRelative()
	 * @see #resolve(SakerPath)
	 */
	public SakerPath append(SakerPath other) throws InvalidPathFormatException {
		if (!other.isRelative()) {
			throw new InvalidPathFormatException("Argument is not relative path: " + other);
		}
		if (other.names.length == 0) {
			return this;
		}
		if ("..".equals(other.names[0])) {
			throw new InvalidPathFormatException("Argument is not forward relative: " + other);
		}
		if (this.names.length == 0) {
			return new SakerPath(root, other.names);
		}
		return new SakerPath(root, ArrayUtils.concat(this.names, other.names));
	}

	/**
	 * Constructs a path which has the parameter as root.
	 * <p>
	 * The parameter root is normalized and can be <code>null</code> to construct a relative path with the current
	 * names.
	 * 
	 * @param newroot
	 *            The new root.
	 * @return The result path.
	 */
	public SakerPath replaceRoot(String newroot) {
		if (newroot == null) {
			return new SakerPath(null, names);
		}
		return new SakerPath(normalizeRoot(newroot), names);
	}

	/**
	 * Promotes the first path name of <code>this</code> relative path to construct an absolute one.
	 * <p>
	 * This method takes the first path name in this path and promotes it to be the root of the newly constructed one.
	 * The new root will be removed from the list of path names and will not be duplicated.
	 * 
	 * @return The promoted path.
	 * @throws IllegalStateException
	 *             If this path is not relative or empty.
	 * @throws InvalidPathFormatException
	 *             If the first path name is not a valid root name.
	 */
	public SakerPath promoteRelativeRoot() throws IllegalStateException, InvalidPathFormatException {
		if (!isRelative()) {
			throw new IllegalStateException("Path is not relative. (" + this + ")");
		}
		if (names.length == 0) {
			throw new IllegalStateException("Empty path.");
		}
		return new SakerPath(normalizeRoot(names[0]), Arrays.copyOfRange(names, 1, names.length));
	}

	/**
	 * Compares <code>this</code> path to the parameter.
	 * <p>
	 * Relative paths are ordered first.
	 * <p>
	 * This method compares the root and the path names sequentially. Shorter paths ordered first.
	 * <p>
	 * The method works in a case-sensitive manner.
	 */
	@Override
	public int compareTo(SakerPath o) {
		//compares null first, so relative paths first
		int rootcmp = StringUtils.compareStringsNullFirst(this.root, o.root);
		if (rootcmp != 0) {
			return rootcmp;
		}
		//roots equal
		//compare by names
		int i = 0;
		int j = 0;
		while (i < names.length) {
			if (!(j < o.names.length)) {
				//this path is longer than the other, and all previous names match
				return 1;
			}
			String n = names[i++];
			String on = o.names[j++];
			if (n == on) {
				continue;
			}
			int cmp = n.compareTo(on);
			if (cmp != 0) {
				return cmp;
			}
		}
		if (!(j < o.names.length)) {
			//none of the iterators have next entry, both are equal
			return 0;
		}
		//the other path has additional paths after ours
		return -1;
	}

	/**
	 * Compares <code>this</code> path to the argument in a case-insensitive manner.
	 * 
	 * @param o
	 *            The path to compare against.
	 * @return A negative integer, zero, or a positive integer as this object is less than, equal to, or greater than
	 *             the specified object.
	 */
	public int compareToIgnoreCase(SakerPath o) {
		//compares null first, so relative paths first
		int rootcmp = StringUtils.compareStringsNullFirst(this.root, o.root);
		if (rootcmp != 0) {
			return rootcmp;
		}
		//roots equal
		//compare by names
		int i = 0;
		int j = 0;
		while (i < names.length) {
			if (!(j < o.names.length)) {
				//this path is longer than the other, and all previous names match
				return 1;
			}
			String n = names[i++];
			String on = o.names[j++];
			if (n == on) {
				continue;
			}
			int cmp = n.compareToIgnoreCase(on);
			if (cmp != 0) {
				return cmp;
			}
		}
		if (!(j < o.names.length)) {
			//none of the iterators have next entry, both are equal
			return 0;
		}
		//the other path has additional paths after ours
		return -1;
	}

	/**
	 * Converts this path to string, but does not include the root if any.
	 * 
	 * @return The string representation of this path without the root.
	 */
	public String toStringFromRoot() {
		return StringUtils.toStringJoin("/", names);
	}

	/**
	 * Converts this path to a String. All path names are separated by forward slashes (<code>'/'</code>).
	 * <p>
	 * Passing the result of this method to {@link #valueOf(String)} will result in a path that equals with
	 * <code>this</code>.
	 * <p>
	 * Converting a path that contains only a single drive root will not end in forward slash.
	 */
	@Override
	public String toString() {
		return toString(root, names);
	}

	/**
	 * Creates an iterator for the path names in this instance.
	 * 
	 * @return An unmodifiable iterator.
	 */
	public ListIterator<String> nameIterator() {
		return new ArrayIterator<>(names);
	}

	/**
	 * Creates an iterator for the path names starting at a given index.
	 * <p>
	 * The iterator will not contain the {@linkplain #getRoot() root} name. See {@link #pathIterator()} for including
	 * the root too.
	 * 
	 * @param index
	 *            The index to start the iterator form.
	 * @return An unmodifiable iterator.
	 */
	public ListIterator<String> nameIterator(int index) {
		return new ArrayIterator<>(names, 0, names.length, index);
	}

	/**
	 * Creates an iterable that creates iterators for the path names in this instance.
	 * <p>
	 * The iterable will not contain the {@linkplain #getRoot() root} name. See {@link #pathIterable()} for including
	 * the root too.
	 * 
	 * @return An unmodifiable iterable.
	 * @see #nameIterator()
	 */
	public Iterable<String> nameIterable() {
		return ImmutableUtils.unmodifiableArrayList(names);
	}

	/**
	 * Creates an iterator for the root and path names in this instance.
	 * <p>
	 * If this path is absolute then the first element will be the root name.
	 * <p>
	 * If this path is relative this call is the same as {@link #nameIterator()};
	 * 
	 * @return An unmodifiable iterator.
	 */
	public Iterator<String> pathIterator() {
		if (root == null) {
			return nameIterator();
		}
		if (names.length == 0) {
			return ImmutableUtils.singletonIterator(root);
		}
		@SuppressWarnings("unchecked")
		ConcatIterator<String> result = new ConcatIterator<>(new ArrayIterator<>(
				(Iterator<String>[]) new Iterator<?>[] { ImmutableUtils.singletonIterator(root), nameIterator() }));
		return result;
	}

	/**
	 * Creates an iterable that creates iterators for the root and path names in this instance.
	 * 
	 * @return An unmodifiable iterable.
	 * @see #pathIterator()
	 */
	public Iterable<String> pathIterable() {
		return this::pathIterator;
	}

	/**
	 * Returns the next sibling path in the natural order specified by {@link #compareTo(SakerPath)}.
	 * <p>
	 * It is ensured that <code>this.compareTo(result) &lt; 0</code> is always <code>true</code>. Any other path that
	 * compares greater than <code>this</code> and less than the result of this function is a subpath of
	 * <code>this</code> path.
	 * <p>
	 * The resulting path is not semantically correct, it should not be used with files, and should be restricted for
	 * ordering comparisons.
	 * 
	 * @return The next path in natural order.
	 */
	public SakerPath nextSiblingPathInNaturalOrder() {
		String lname = getLastName();
		if (lname != null) {
			String nfilename = StringUtils.nextInNaturalOrder(lname);
			String[] nnames = this.names.clone();
			nnames[nnames.length - 1] = nfilename;
			return new SakerPath(this.root, nnames);
		}
		String root = this.root;
		if (SakerPath.ROOT_SLASH.equals(root)) {
			return SakerPath.FIRST_NONSLASH_ROOT_PATH;
		}
		//there are no names, as file name is null
		if (root == null) {
			return SakerPath.FIRST_ABSOLUTE_PATH;
		}
		return new SakerPath(StringUtils.nextInNaturalOrder(root), ObjectUtils.EMPTY_STRING_ARRAY);
	}

	/**
	 * Returns the first subpath that starts with <code>this</code> path specified by the natural order.
	 * <p>
	 * It is ensured that <code>this.compareTo(result) &lt; 0</code> is always <code>true</code>.
	 * <p>
	 * The resulting path is not semantically correct, it should not be used with files, and should be restricted for
	 * ordering comparisons.
	 * 
	 * @return The next path in natural order.
	 */
	public SakerPath nextSubPathInNaturalOrder() {
		String[] nnames = Arrays.copyOf(names, names.length + 1);
		nnames[names.length] = "";
		return new SakerPath(this.root, nnames);
	}

	/**
	 * Converts this path to an all lowercase representation.
	 * <p>
	 * The {@linkplain Locale#getDefault() default locale} is used to convert each character of the path.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * {@link #toLowerCase(Locale) toLowerCase}(Locale.getDefault())
	 * </pre>
	 * 
	 * @return The all lowercase representation of the path.
	 */
	public SakerPath toLowerCase() {
		return toLowerCase(Locale.getDefault());
	}

	/**
	 * Converts this path to an all lowercase representation using the specified locale.
	 * <p>
	 * As paths have a case-sensitive representation, it may be useful to convert them to a lowercase representation
	 * when comparing them for equality in some cases.
	 * 
	 * @param locale
	 *            The locale to use when converting the path names to lower case.
	 * @return The all lowercase representation of the path.
	 * @throws NullPointerException
	 *             If the locale is <code>null</code>.
	 * @see String#toLowerCase(Locale)
	 */
	public SakerPath toLowerCase(Locale locale) throws NullPointerException {
		String r = this.root == null ? null : this.root.toLowerCase(locale);
		String[] nnames;
		int namessize = this.names.length;
		if (namessize == 0) {
			return new SakerPath(r, ObjectUtils.EMPTY_STRING_ARRAY);
		} else {
		}
		String[] array = new String[namessize];
		for (int i = 0; i < namessize; i++) {
			array[i] = this.names[i].toLowerCase(locale);
		}
		nnames = array;
		return new SakerPath(r, nnames);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((root == null) ? 0 : root.hashCode());
		result = prime * result + Arrays.hashCode(names);
		return result;
	}

	/**
	 * Equals method with already known type of the parameter.
	 * 
	 * @param path
	 *            The object to check equality against.
	 * @return If the two paths equal.
	 * @see #equals(Object)
	 */
	public boolean equals(SakerPath path) {
		if (this == path) {
			return true;
		}
		if (path == null) {
			return false;
		}
		return equalsNonNull(path);
	}

	/**
	 * Check if this path equals the argument in a case-insensitive manner.
	 * 
	 * @param path
	 *            The path to check case-insensitive equality for.
	 * @return <code>true</code> if the paths equal in a case-insensitive manner.
	 */
	public boolean equalsIgnoreCase(SakerPath path) {
		if (this == path) {
			return true;
		}
		if (path == null) {
			return false;
		}
		return equalsNonNullIgnoreCase(path);
	}

	/**
	 * Check if the parameter is a path and represents the same location.
	 * <p>
	 * This method works in a case-sensitive manner.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SakerPath other = (SakerPath) obj;
		return equalsNonNull(other);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(root);
		SerialUtils.writeExternalArray(out, names);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		root = (String) in.readObject();
		names = SerialUtils.readExternalArray(in, String[]::new);
	}

	/**
	 * Creates a relative path builder.
	 * 
	 * @return The builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a path builder with the given root.
	 * <p>
	 * The parameter might be <code>null</code> to construct a relative path.
	 * 
	 * @param root
	 *            The root to use for the constructing builder.
	 * @return The builder.
	 * @throws InvalidPathFormatException
	 *             If the root has an invalid format.
	 */
	public static Builder builder(String root) throws InvalidPathFormatException {
		return new Builder(root == null ? null : normalizeRoot(root));
	}

	/**
	 * {@link SakerPath} builder.
	 */
	public static final class Builder {
		private final String root;
		private ArrayList<String> names = new ArrayList<>();
		private StringBuilder sb;

		Builder() {
			this(null);
		}

		Builder(String root) {
			this.root = root;
		}

		/**
		 * Appends the parameter path to the builder.
		 * <p>
		 * The path is sanity checked and normalized.
		 * 
		 * @param path
		 *            The path to append.
		 * @return <code>this</code>
		 */
		public Builder append(String path) {
			if (sb == null) {
				sb = new StringBuilder(path.length());
			}
			addSplitPathNames(root, names, path, sb);
			return this;
		}

		/**
		 * Constructs the {@link SakerPath} object.
		 * <p>
		 * The build can be reused after this call and will continue to have the same root and path names to it.
		 * 
		 * @return The constructed path.
		 */
		public SakerPath build() {
			SakerPath result = new SakerPath(root, names.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
			return result;
		}

		/**
		 * Appends the parameter and builds the path object without modifying the state of the builder.
		 * <p>
		 * The parameter path is appended to the current path and a new {@link SakerPath} is constructed without
		 * modifying the state of this builder.
		 * 
		 * @param path
		 *            The path to append.
		 * @return The constructed path.
		 */
		public SakerPath appendBuildReuse(String path) {
			if (sb == null) {
				sb = new StringBuilder(path.length());
			}
			@SuppressWarnings("unchecked")
			List<String> nnames = (List<String>) this.names.clone();
			addSplitPathNames(root, nnames, path, sb);
			return new SakerPath(root, nnames.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
		}

		@Override
		public String toString() {
			return SakerPath.toString(root, names);
		}
	}

	/**
	 * Checks if the parameter is a valid path name.
	 * <p>
	 * Returns <code>true</code> if it doesn't contain any directory separators and colons (<code>':'</code>).
	 * 
	 * @param n
	 *            The name to check.
	 * @return <code>true</code> if it is a valid path name.
	 */
	public static boolean isValidPathName(CharSequence n) {
		int len = n.length();
		if (len == 0) {
			return false;
		}
		for (int i = 0; i < len; i++) {
			char c = n.charAt(i);
			if (c == '/' || c == '\\' || c == ':') {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if the parameter has a valid root name format.
	 * <p>
	 * Valid root name formats are:
	 * <ul>
	 * <li>Single forward (<code>'/'</code>) or backward (<code>'\\'</code>) slash character.</li>
	 * <li>Drive format matching the following regex: <code>"[a-zA-Z]+:"</code></li>
	 * </ul>
	 * 
	 * @param r
	 *            The root name to check.
	 * @return <code>true</code> if it is a valid root name.
	 */
	public static boolean isValidRootName(CharSequence r) {
		int len = r.length();
		if (len == 0) {
			return false;
		}
		if (len == 1) {
			char c = r.charAt(0);
			return isSlashCharacter(c);
		}
		if (r.charAt(len - 1) != ':') {
			return false;
		}
		for (int i = 0; i < len - 1; i++) {
			char c = r.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
				continue;
			}
			return false;
		}
		return true;
	}

	/**
	 * Checks if the parameter character can be considered as a path separator slash character.
	 * 
	 * @param c
	 *            The character to test.
	 * @return <code>true</code> if the parameter equals <code>'\\'</code> or <code>'/'</code>.
	 */
	public static boolean isSlashCharacter(char c) {
		return c == '\\' || c == '/';
	}

	private SakerPath relativizeImpl(SakerPath other) {
		if (names.length == 0) {
			//this path has no names, full relative equals the other path
			return new SakerPath(null, other.names);
		}
		int basesize = names.length;
		int othersize = other.names.length;
		int i = 0;
		int c = Math.min(basesize, othersize);

		while (i < c) {
			if (!names[i].equals(other.names[i])) {
				break;
			}
			++i;
		}

		String[] relative = new String[basesize - i + othersize - i];
		int relidx = 0;
		//i points to first non-matching name
		for (int k = i; k < basesize; k++) {
			//add ".." for each name in base remaining
			relative[relidx++] = "..";
		}
		for (int k = i; k < othersize; k++) {
			relative[relidx++] = other.names[k];
		}
		return new SakerPath(null, relative);
	}

	private SakerPath resolveValidatedImpl(String pathname) {
		List<String> nnames = ObjectUtils.newArrayList(this.names);
		if (this.root == null) {
			resolveRelative(nnames, pathname);
		} else {
			resolveAbsolute(nnames, pathname);
		}
		return new SakerPath(this.root, nnames.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
	}

	private SakerPath resolveValidatedImpl(String[] paths) {
		List<String> nnames = ObjectUtils.newArrayList(this.names);
		if (this.root == null) {
			for (String p : paths) {
				resolveRelative(nnames, p);
			}
		} else {
			for (String p : paths) {
				resolveAbsolute(nnames, p);
			}
		}
		return new SakerPath(this.root, nnames.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
	}

	private SakerPath resolveValidatedImpl(Iterable<String> paths) {
		List<String> nnames = ObjectUtils.newArrayList(this.names);
		if (this.root == null) {
			for (String p : paths) {
				resolveRelative(nnames, p);
			}
		} else {
			for (String p : paths) {
				resolveAbsolute(nnames, p);
			}
		}
		return new SakerPath(this.root, nnames.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
	}

	private boolean equalsNonNull(SakerPath path) {
		if (!Objects.equals(root, path.root)) {
			return false;
		}
		if (!Arrays.equals(this.names, path.names)) {
			return false;
		}
		return true;
	}

	private boolean equalsNonNullIgnoreCase(SakerPath path) {
		if (!Objects.equals(root, path.root)) {
			return false;
		}
		if (this.names.length != path.names.length) {
			return false;
		}
		for (int i = 0; i < names.length; i++) {
			if (!this.names[i].equalsIgnoreCase(path.names[i])) {
				return false;
			}
		}
		return true;
	}

	private static void resolveRelativeParent(List<String> subject) {
		if (subject.isEmpty()) {
			subject.add("..");
			return;
		}
		if (!"..".equals(subject.get(subject.size() - 1))) {
			//the last path can be removed instead of appending ".."
			subject.remove(subject.size() - 1);
			return;
		}
		subject.add("..");
	}

	private static void resolveRelative(List<String> subject, String name) {
		//only called for relative paths
		switch (name) {
			case "":
			case ".": {
				break;
			}
			case "..": {
				resolveRelativeParent(subject);
				break;
			}
			default: {
				subject.add(name);
				break;
			}
		}
	}

	private static void resolveAbsolute(List<String> subject, String name) {
		//only called for absolute paths
		switch (name) {
			case "":
			case ".": {
				break;
			}
			case "..": {
				if (subject.isEmpty()) {
					throw new InvalidPathFormatException("No parent found to resolve \"..\" path name.");
				}
				subject.remove(subject.size() - 1);
				break;
			}
			default: {
				subject.add(name);
				break;
			}
		}
	}

	private static void addSplitPathNames(String root, List<String> names, CharSequence path) {
		int len = path.length();
		StringBuilder sb = new StringBuilder(len);
		addSplitPathNames(root, names, path, 0, len, sb);
	}

	private static void addSplitPathNames(String root, List<String> names, CharSequence path, StringBuilder sb) {
		addSplitPathNames(root, names, path, 0, path.length(), sb);
	}

	private static void addSplitPathNames(String root, List<String> names, CharSequence path, int i, int len,
			StringBuilder sb) {
		if (root != null) {
			for (; i < len; i++) {
				char c = path.charAt(i);
				if (c == '/' || c == '\\') {
					if (sb.length() > 0) {
						resolveAbsolute(names, sb.toString());
						sb.setLength(0);
					}
					continue;
				}
				if (c == ':') {
					throw new InvalidPathFormatException(
							"':' character in path name at index: " + i + ". (" + path + ")");
				}
				sb.append(c);
			}
			if (sb.length() > 0) {
				resolveAbsolute(names, sb.toString());
				sb.setLength(0);
			}
		} else {
			for (; i < len; i++) {
				char c = path.charAt(i);
				if (c == '/' || c == '\\') {
					if (sb.length() > 0) {
						resolveRelative(names, sb.toString());
						sb.setLength(0);
					}
					continue;
				}
				if (c == ':') {
					throw new InvalidPathFormatException(
							"':' character in path name at index: " + i + ". (" + path + ")");
				}
				sb.append(c);
			}
			if (sb.length() > 0) {
				resolveRelative(names, sb.toString());
				sb.setLength(0);
			}
		}
	}

	private static String toString(String root, String[] names, char separator) {
		if (names.length == 0) {
			if (root == null) {
				return "";
			}
			return root;
		}
		StringBuilder sb = new StringBuilder();
		if (ROOT_SLASH.equals(root)) {
			sb.append(ROOT_SLASH);
		} else if (root != null) {
			sb.append(root);
			sb.append(separator);
		}
		for (int i = 0;;) {
			sb.append(names[i++]);
			if (i < names.length) {
				sb.append(separator);
			} else {
				break;
			}
		}
		return sb.toString();
	}

	private static String toString(String root, String[] names) {
		return toString(root, names, '/');
	}

	private static String toString(String root, Collection<String> names, char separator) {
		if (names.isEmpty()) {
			if (root == null) {
				return "";
			}
			return root;
		}
		StringBuilder sb = new StringBuilder();
		if (ROOT_SLASH.equals(root)) {
			sb.append(ROOT_SLASH);
		} else if (root != null) {
			sb.append(root);
			sb.append(separator);
		}
		Iterator<String> it = names.iterator();
		while (true) {
			sb.append(it.next());
			if (it.hasNext()) {
				sb.append(separator);
			} else {
				break;
			}
		}
		return sb.toString();
	}

	private static String toString(String root, Collection<String> names) {
		return toString(root, names, '/');
	}
}
