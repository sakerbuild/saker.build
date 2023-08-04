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
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionDirectoryContext;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.task.TaskContext;
import saker.build.task.TaskDirectoryContext;
import saker.build.thirdparty.saker.util.ArrayIterator;
import saker.build.thirdparty.saker.util.ConcurrentEntryMergeSorter;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.TransformingNavigableMap;
import saker.build.thirdparty.saker.util.io.FileUtils;

/**
 * File inclusion tester based on a specified path and specially handled wildcard pathnames.
 * <p>
 * The purpose of this class is similar to globbing, but the exact implementation details and accepted wildcards might
 * be different from the usual common implementations.
 * <p>
 * The class takes a path during instantiation, and uses that to provide functionality to test inclusion, or discover
 * files based on a {@linkplain ItemLister listing} implementation.
 * <p>
 * The following wildcards are supported in the current implementation:
 * <ul>
 * <li><code>*</code>: which matches any number of characters (0 or more), but doesn't pass through directory
 * boundary.</li>
 * <li><code>**</code>: which can be used only as a single full path name, and matches zero or more directories with any
 * name.</li>
 * </ul>
 * The following wildcards should be considered as reserved, as they might be added in future versions (without quotes):
 * <code>"?"</code>, <code>"["</code>, <code>"]"</code>.
 * <p>
 * The single star wildcard can be combined with other characters, to match path names that partially contain the
 * specified characters.
 * <p>
 * Wildcard paths that are relative, will only match relative paths, and absolute wildcard paths will only match
 * absolute paths. A wildcard path is considered to be absolute according to the rules of {@link SakerPath}.
 * <p>
 * Examples:
 * <ul>
 * <li><code>dir/no/wildcard</code> only matches <code>dir/no/wildcard</code></li>
 * <li><code>abs:/no/wildcard</code> only matches <code>abs:/no/wildcard</code></li>
 * <li><code>/no/wildcard</code> only matches <code>/no/wildcard</code></li>
 * <li><code>dir/*.ext</code> matches files under the directory <code>dir</code> which have the extension
 * <code>.ext</code></li>
 * <li><code>dir/*123*</code> matches files under the directory <code>dir</code> which contain the number
 * <code>123</code></li>
 * <li><code>dir/**&#47;*.ext</code> recursively matches files under the directory <code>dir</code> with the extension
 * <code>.ext</code></li>
 * <li><code>dir/**.ext</code> is the same as <code>dir/*.ext</code></li>
 * <li><code>dir/**</code> recursively matches all files under the directory <code>dir</code> (children and their
 * children too)</li>
 * <li><code>*d:/</code> will match root directories whose drive ends with <code>d</code></li>
 * </ul>
 * <p>
 * This class works in a case sensitive manner. When collecting files, it will return directories and files which match
 * the wildcard as well.
 * <p>
 * The usage of this class is not restricted to file paths, they are suitable for matching other kind of character
 * sequences when appropriately constructed. (E.g. it can be used to match class names.)
 * <p>
 * A {@link WildcardPath} can be constructed that it doesn't contain any wildcards. In that case the path will only
 * match the paths that equal to the one provided during instantiation.
 * <p>
 * The wildcard path doesn't handle the <code>"."</code> and <code>".."</code> path names specially.
 * <p>
 * The class is comparable, and serializable.
 */
public final class WildcardPath implements Externalizable, Comparable<WildcardPath> {
	/**
	 * Class holding information about the optimization performed in {@link WildcardPath#reduce()}.
	 */
	public static final class ReducedWildcardPath {
		protected final SakerPath file;
		protected final WildcardPath wildcard;

		protected ReducedWildcardPath(SakerPath file, WildcardPath wildcard) {
			this.file = file;
			this.wildcard = wildcard;
		}

		/**
		 * Gets the file part of the wildcard or <code>null</code> if not applicable.
		 * <p>
		 * The file part is at the start of the wildcard which have path names that match exactly the names.
		 * 
		 * @return The file part.
		 */
		public SakerPath getFile() {
			return file;
		}

		/**
		 * Gets the remaining wildcard part or <code>null</code> if not applicable.
		 * <p>
		 * The wildcard part is the remaining part of the original wildcard path that has any non exact matching path
		 * names.
		 * 
		 * @return The wildcard part.
		 */
		public WildcardPath getWildcard() {
			return wildcard;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (file != null ? "file=" + file + ", " : "")
					+ (wildcard != null ? "wildcard=" + wildcard : "") + "]";
		}

	}

	/**
	 * Interface for providing listing functionality for wildcard paths.
	 * 
	 * @param <T>
	 *            The object type the lister discovers.
	 */
	public interface ItemLister<T> {
		/**
		 * Gets the names of the root items.
		 * <p>
		 * Root item names follow the rules of {@link SakerPath} root names.
		 * 
		 * @return An unmodifiable set of root items.
		 */
		public Set<String> getRootItems();

		/**
		 * Gets the root item for the specified root item name.
		 * <p>
		 * The parameter root item name is one of the names returned by {@link #getRootItems()}.
		 * 
		 * @param name
		 *            The root item name.
		 * @return The root item, or <code>null</code> if not found.
		 */
		public T getRootItem(String name);

		/**
		 * Gets the item at the given absolute path.
		 * 
		 * @param path
		 *            The absolute path to resolve the item at.
		 * @return The item or <code>null</code> if not found.
		 */
		public T getItem(SakerPath path);

		/**
		 * Gets the working item for finding items with a relative path.
		 * 
		 * @return The working item, or <code>null</code> if not available.
		 * @see #getWorkingItemPath()
		 */
		public T getWorkingItem();

		/**
		 * The path of the working item.
		 * 
		 * @return The path to the working item or <code>null</code> if there's no working item.
		 * @see #getWorkingItem()
		 */
		public SakerPath getWorkingItemPath();

		/**
		 * Resolves a child item for a given parent.
		 * 
		 * @param item
		 *            The parent item.
		 * @param name
		 *            The name of the child item to look for.
		 * @return The child item, or <code>null</code> if not found.
		 */
		public T resolve(T item, String name);

		/**
		 * Lists all child items for a given parent.
		 * 
		 * @param item
		 *            The parent item.
		 * @return An map of child items.
		 */
		public Map<String, ? extends T> listChildItems(T item);

		/**
		 * Lists all child items for a given parent, recursively.
		 * <p>
		 * All child items and their children should be inclided in the returned map. The result map keys should be
		 * relative to the parent item.
		 * 
		 * @param item
		 *            The parent item.
		 * @return The recursively listed child items.
		 */
		public NavigableMap<SakerPath, ? extends T> listChildItemsRecursively(T item);

		/**
		 * Creates an {@link ItemLister} for a given file provider.
		 * <p>
		 * The created item lister will collect the file attributes of the files.
		 * <p>
		 * No working directory is used.
		 * 
		 * @param fp
		 *            The file provider.
		 * @return The created item lister.
		 * @throws NullPointerException
		 *             If the file provider is <code>null</code>.
		 */
		public static ItemLister<? extends BasicFileAttributes> forFileProvider(SakerFileProvider fp)
				throws NullPointerException {
			return new FileProviderItemLister(fp);
		}

		/**
		 * Creates an {@link ItemLister} for a given file provider.
		 * <p>
		 * The created item lister will collect the file attributes of the files.
		 * 
		 * @param fp
		 *            The file provider.
		 * @param workingdir
		 *            The working directory, or <code>null</code> to not use one.
		 * @return The created item lister.
		 * @throws NullPointerException
		 *             If the file provider is <code>null</code>.
		 */
		public static ItemLister<? extends BasicFileAttributes> forFileProvider(SakerFileProvider fp,
				SakerPath workingdir) throws NullPointerException {
			return new FileProviderItemLister(fp, workingdir);
		}

		/**
		 * Creates an {@link ItemLister} for a given file provider, but without any root directories.
		 * <p>
		 * The item lister will not use the root directories of the specified file provider, only the working directory.
		 * <p>
		 * The created item lister will collect the file attributes of the files.
		 * 
		 * @param fp
		 *            The file provider.
		 * @param workingdir
		 *            The working directory.
		 * @return The created item lister.
		 * @throws NullPointerException
		 *             If any of the arguments are <code>null</code>.
		 * @since saker.build 0.8.5
		 */
		public static ItemLister<? extends BasicFileAttributes> forFileProviderWithoutRoots(SakerFileProvider fp,
				SakerPath workingdir) throws NullPointerException {
			Objects.requireNonNull(workingdir, "working directory");
			return new FileProviderItemLister(fp, workingdir, Collections.emptySet());
		}

		/**
		 * Creates an {@link ItemLister} for collecting {@link SakerFile SakerFiles} for a task.
		 * <p>
		 * The task working directory is used to resolve relative paths.
		 * 
		 * @param taskcontext
		 *            The task context.
		 * @return The created item lister.
		 */
		public static ItemLister<? extends SakerFile> forSakerFiles(TaskContext taskcontext) {
			return new SakerFileItemLister(taskcontext.getTaskWorkingDirectory(),
					taskcontext.getExecutionContext().getRootDirectories());
		}

		/**
		 * Creates an {@link ItemLister} for collecting {@link SakerFile SakerFiles} with the given root directories.
		 * <p>
		 * No working directory is used.
		 * 
		 * @param rootdirectories
		 *            The root directories.
		 * @return The created item lister.
		 * @throws NullPointerException
		 *             If the argument is <code>null</code>.
		 */
		public static ItemLister<? extends SakerFile> forSakerFiles(
				NavigableMap<String, ? extends SakerDirectory> rootdirectories) throws NullPointerException {
			Objects.requireNonNull(rootdirectories, "root directories");
			return new SakerFileItemLister(null, rootdirectories);
		}

		/**
		 * Creates an {@link ItemLister} for collecting {@link SakerFile SakerFiles} in the specified working directory.
		 * <p>
		 * No root directories are used.
		 * 
		 * @param workingdirectory
		 *            The working directory.
		 * @return The created item lister.
		 * @throws NullPointerException
		 *             If the argument is <code>null</code>.
		 * @since saker.build 0.8.5
		 */
		public static ItemLister<? extends SakerFile> forSakerFiles(SakerDirectory workingdirectory)
				throws NullPointerException {
			Objects.requireNonNull(workingdirectory, "working directory");
			return new SakerFileItemLister(workingdirectory, Collections.emptyMap());
		}

		/**
		 * Creates an {@link ItemLister} for collecting {@link SakerFile SakerFiles} with the given root directories and
		 * working directory.
		 * 
		 * @param rootdirectories
		 *            The root directories or <code>null</code> to not use them.
		 * @param workingdirectory
		 *            The working directory, or <code>null</code> to not use one.
		 * @return The created item lister.
		 * @throws NullPointerException
		 *             If both arguments are <code>null</code>.
		 */
		public static ItemLister<? extends SakerFile> forSakerFiles(
				NavigableMap<String, ? extends SakerDirectory> rootdirectories, SakerDirectory workingdirectory)
				throws NullPointerException {
			if (rootdirectories == null) {
				if (workingdirectory == null) {
					throw new NullPointerException("No arguments specified.");
				}
				rootdirectories = Collections.emptyNavigableMap();
			}
			return new SakerFileItemLister(workingdirectory, rootdirectories);
		}
	}

	private static class FileProviderListerAttributes implements BasicFileAttributes, Externalizable {
		private static final long serialVersionUID = 1L;

		protected FileEntry entry;
		protected transient SakerPath path;

		/**
		 * For {@link Externalizable}.
		 */
		public FileProviderListerAttributes() {
		}

		public FileProviderListerAttributes(SakerPath path, FileEntry entry) {
			this.path = path;
			this.entry = entry;
		}

		@Override
		public FileTime lastModifiedTime() {
			return entry.lastModifiedTime();
		}

		@Override
		public FileTime lastAccessTime() {
			return entry.lastAccessTime();
		}

		@Override
		public FileTime creationTime() {
			return entry.creationTime();
		}

		@Override
		public boolean isRegularFile() {
			return entry.isRegularFile();
		}

		@Override
		public boolean isDirectory() {
			return entry.isDirectory();
		}

		@Override
		public boolean isSymbolicLink() {
			return entry.isSymbolicLink();
		}

		@Override
		public boolean isOther() {
			return entry.isOther();
		}

		@Override
		public long size() {
			return entry.size();
		}

		@Override
		public Object fileKey() {
			return entry.fileKey();
		}

		@Override
		public String toString() {
			return path.toString();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(entry);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			entry = (FileEntry) in.readObject();
		}
	}

	protected static class PathConfigurationItemLister implements ItemLister<FileProviderListerAttributes> {
		private ExecutionPathConfiguration pathConfig;

		public PathConfigurationItemLister(ExecutionPathConfiguration pathConfig) {
			this.pathConfig = pathConfig;
		}

		@Override
		public Set<String> getRootItems() {
			return pathConfig.getRootNames();
		}

		private static FileProviderListerAttributes toAttrs(SakerPath path, FileEntry entry) {
			return new FileProviderListerAttributes(path, entry);
		}

		@Override
		public FileProviderListerAttributes getRootItem(String name) {
			if (getRootItems().contains(name)) {
				SakerPath path = SakerPath.valueOf(name);
				return getItem(path);
			}
			return null;
		}

		@Override
		public FileProviderListerAttributes getItem(SakerPath path) {
			SakerFileProvider fp = pathConfig.getFileProviderIfPresent(path);
			if (fp == null) {
				return null;
			}
			try {
				return toAttrs(path, fp.getFileAttributes(path));
			} catch (IOException e) {
				return null;
			}
		}

		@Override
		public FileProviderListerAttributes getWorkingItem() {
			return getItem(pathConfig.getWorkingDirectory());
		}

		@Override
		public SakerPath getWorkingItemPath() {
			return pathConfig.getWorkingDirectory();
		}

		@Override
		public FileProviderListerAttributes resolve(FileProviderListerAttributes item, String name) {
			SakerPath rpath = item.path.resolve(name);

			return getItem(rpath);
		}

		@Override
		public Map<String, ? extends FileProviderListerAttributes> listChildItems(FileProviderListerAttributes item) {
			try {
				SakerPath itempath = item.path;
				SakerFileProvider fp = pathConfig.getFileProviderIfPresent(itempath);
				if (fp == null) {
					return Collections.emptyNavigableMap();
				}
				NavigableMap<String, ? extends FileEntry> entries = fp.getDirectoryEntries(itempath);
				NavigableMap<String, FileProviderListerAttributes> result = new TreeMap<>(
						new TransformingNavigableMap<String, FileEntry, String, FileProviderListerAttributes>(entries) {
							@Override
							protected Entry<String, FileProviderListerAttributes> transformEntry(String key,
									FileEntry value) {
								return ImmutableUtils.makeImmutableMapEntry(key, toAttrs(itempath.resolve(key), value));
							}
						});
				return result;
			} catch (IOException e) {
			}
			return Collections.emptyNavigableMap();
		}

		@Override
		public NavigableMap<SakerPath, ? extends FileProviderListerAttributes> listChildItemsRecursively(
				FileProviderListerAttributes item) {
			try {
				SakerPath itempath = item.path;
				SakerFileProvider fp = pathConfig.getFileProviderIfPresent(itempath);
				if (fp == null) {
					return Collections.emptyNavigableMap();
				}
				NavigableMap<SakerPath, ? extends FileEntry> entries = fp.getDirectoryEntriesRecursively(itempath);
				NavigableMap<SakerPath, FileProviderListerAttributes> result = new TreeMap<>(
						new TransformingNavigableMap<SakerPath, FileEntry, SakerPath, FileProviderListerAttributes>(
								entries) {
							@Override
							protected Entry<SakerPath, FileProviderListerAttributes> transformEntry(SakerPath key,
									FileEntry value) {
								return ImmutableUtils.makeImmutableMapEntry(key, toAttrs(itempath.resolve(key), value));
							}
						});
				return result;
			} catch (IOException e) {
				return Collections.emptyNavigableMap();
			}
		}

	}

	protected static class FileProviderItemLister implements ItemLister<FileProviderListerAttributes> {
		private final SakerFileProvider fp;
		private final SakerPath workingDirectoryPath;

		private transient Set<String> roots;
		private transient FileProviderListerAttributes workingDirAttrs;

		public FileProviderItemLister(SakerFileProvider fp) {
			this(fp, null);
		}

		public FileProviderItemLister(SakerFileProvider fp, SakerPath workingDirectory) {
			this(fp, workingDirectory, null);
		}

		/*default*/ FileProviderItemLister(SakerFileProvider fp, SakerPath workingDirectoryPath, Set<String> roots) {
			Objects.requireNonNull(fp, "file provider");
			this.fp = fp;
			this.workingDirectoryPath = workingDirectoryPath;
			this.roots = roots;
		}

		@Override
		public Set<String> getRootItems() {
			if (roots == null) {
				try {
					roots = fp.getRoots();
				} catch (IOException e) {
					roots = Collections.emptySet();
				}
			}
			return roots;
		}

		private static FileProviderListerAttributes toAttrs(SakerPath path, FileEntry entry) {
			return new FileProviderListerAttributes(path, entry);
		}

		@Override
		public FileProviderListerAttributes getRootItem(String name) {
			if (getRootItems().contains(name)) {
				SakerPath path = SakerPath.valueOf(name);
				try {
					return toAttrs(path, fp.getFileAttributes(path));
				} catch (IOException e) {
					//just proceed to return null
				}
			}
			return null;
		}

		@Override
		public FileProviderListerAttributes getItem(SakerPath path) {
			try {
				return toAttrs(path, fp.getFileAttributes(path));
			} catch (IOException e) {
				return null;
			}
		}

		@Override
		public FileProviderListerAttributes getWorkingItem() {
			if (workingDirectoryPath == null) {
				return null;
			}
			if (workingDirAttrs == null) {
				try {
					workingDirAttrs = toAttrs(workingDirectoryPath, fp.getFileAttributes(workingDirectoryPath));
				} catch (IOException e) {
					//proceed to return null
				}
			}
			return workingDirAttrs;
		}

		@Override
		public SakerPath getWorkingItemPath() {
			return workingDirectoryPath;
		}

		@Override
		public FileProviderListerAttributes resolve(FileProviderListerAttributes item, String name) {
			try {
				SakerPath rpath = item.path.resolve(name);

				return toAttrs(rpath, fp.getFileAttributes(rpath));
			} catch (IOException e) {
			}
			return null;
		}

		@Override
		public NavigableMap<String, ? extends FileProviderListerAttributes> listChildItems(
				FileProviderListerAttributes item) {
			try {
				SakerPath itempath = item.path;
				NavigableMap<String, ? extends FileEntry> entries = fp.getDirectoryEntries(itempath);
				NavigableMap<String, FileProviderListerAttributes> result = new TreeMap<>(
						new TransformingNavigableMap<String, FileEntry, String, FileProviderListerAttributes>(entries) {
							@Override
							protected Entry<String, FileProviderListerAttributes> transformEntry(String key,
									FileEntry value) {
								return ImmutableUtils.makeImmutableMapEntry(key, toAttrs(itempath.resolve(key), value));
							}
						});
				return result;
			} catch (IOException e) {
			}
			return Collections.emptyNavigableMap();
		}

		@Override
		public NavigableMap<SakerPath, ? extends FileProviderListerAttributes> listChildItemsRecursively(
				FileProviderListerAttributes item) {
			try {
				SakerPath itempath = item.path;
				NavigableMap<SakerPath, ? extends FileEntry> entries = fp.getDirectoryEntriesRecursively(itempath);
				NavigableMap<SakerPath, FileProviderListerAttributes> result = new TreeMap<>(
						new TransformingNavigableMap<SakerPath, FileEntry, SakerPath, FileProviderListerAttributes>(
								entries) {
							@Override
							protected Entry<SakerPath, FileProviderListerAttributes> transformEntry(SakerPath key,
									FileEntry value) {
								return ImmutableUtils.makeImmutableMapEntry(key, toAttrs(itempath.resolve(key), value));
							}
						});
				return result;
			} catch (IOException e) {
				return Collections.emptyNavigableMap();
			}
		}
	}

	protected static class SakerFileItemLister implements ItemLister<SakerFile> {
		private SakerDirectory workingDirectory;
		private Map<String, ? extends SakerDirectory> rootDirectories;

		public SakerFileItemLister(SakerDirectory workingDirectory,
				Map<String, ? extends SakerDirectory> rootDirectories) throws NullPointerException {
			Objects.requireNonNull(rootDirectories, "root directories");
			this.workingDirectory = workingDirectory;
			this.rootDirectories = rootDirectories;
		}

		@Override
		public Set<String> getRootItems() {
			return rootDirectories.keySet();
		}

		@Override
		public SakerDirectory getRootItem(String name) {
			return rootDirectories.get(name);
		}

		@Override
		public SakerFile getItem(SakerPath path) {
			SakerDirectory rootdir = rootDirectories.get(path.getRoot());
			if (rootdir == null) {
				return null;
			}
			return SakerPathFiles.resolveAtRelativePathNames(rootdir, path.nameIterable());
		}

		@Override
		public SakerDirectory getWorkingItem() {
			return workingDirectory;
		}

		@Override
		public SakerPath getWorkingItemPath() {
			if (workingDirectory == null) {
				return null;
			}
			return workingDirectory.getSakerPath();
		}

		@Override
		public SakerFile resolve(SakerFile item, String name) {
			if (item instanceof SakerDirectory) {
				return SakerPathFiles.resolve((SakerDirectory) item, name);
			}
			return null;
		}

		@Override
		public NavigableMap<String, ? extends SakerFile> listChildItems(SakerFile item) {
			if (item instanceof SakerDirectory) {
				return ((SakerDirectory) item).getChildren();
			}
			return Collections.emptyNavigableMap();
		}

		@Override
		public NavigableMap<SakerPath, ? extends SakerFile> listChildItemsRecursively(SakerFile item) {
			if (item instanceof SakerDirectory) {
				return SakerPathFiles.getFilesRecursiveByPath((SakerDirectory) item);
			}
			return Collections.emptyNavigableMap();
		}

	}

	private static final long serialVersionUID = 1L;
	private static final int CLASS_HASH_CODE = WildcardPath.class.hashCode();

	private static final String[] ROOT_SLASH_STRING_ARRAY = new String[] { SakerPath.ROOT_SLASH };

	private String[] splits;

	/**
	 * For {@link Externalizable}.
	 */
	public WildcardPath() {
	}

	private WildcardPath(String[] splits) {
		this.splits = splits;
	}

	/**
	 * Creates a new wildcard path based on the given path string.
	 * <p>
	 * The argument will be split up by <code>'/'</code> and <code>'\\'</code> characters. The path is considered to be
	 * absolute with the same rules as {@link SakerPath}.
	 * <p>
	 * Trailing slash (<code>'/'</code> or <code>'\\'</code>) will be ignored by this function.
	 * 
	 * @param path
	 *            The path.
	 * @return The constructed wildcard path.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>
	 * @throws InvalidPathFormatException
	 *             If the path has invalid format.
	 */
	public static WildcardPath valueOf(String path) throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(path, "path");
		return new WildcardPath(splitPath(path));
	}

	/**
	 * Creates a new wildcard path for the specified path names.
	 * <p>
	 * The argument is not sanity checked in any way. If it includes slashes, or other semantically important
	 * characters, they are not removed or split up. Passing an array containing invalid path names is considered to be
	 * undefined behaviour.
	 * <p>
	 * The array will be cloned, any modifications to it won't propagate to the created wildcard.
	 * 
	 * @param pathnames
	 *            An array of path names.
	 * @return The constructed wildcard path.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>
	 */
	public static WildcardPath valueOf(String[] pathnames) throws NullPointerException {
		Objects.requireNonNull(pathnames, "path names");
		//defensive clone
		return new WildcardPath(pathnames.clone());
	}

	/**
	 * Creates a new wildcard path for the specified path.
	 * <p>
	 * The created wildcard will have the same root (if absolute) and all path names as in the argument.
	 * 
	 * @param path
	 *            The path.
	 * @return The constructed wildcard path.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>
	 */
	public static WildcardPath valueOf(SakerPath path) throws NullPointerException {
		Objects.requireNonNull(path, "path");
		return new WildcardPath(path.getPathArray());
	}

	/**
	 * Creates a new wildcard path for the specified path.
	 * <p>
	 * The argument path will be {@linkplain Path#normalize() normalized}.
	 * <p>
	 * The created wildcard will have the same root (if absolute) and all path names as in the argument.
	 * 
	 * @param path
	 *            The path.
	 * @return The constructed wildcard path.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>
	 */
	public static WildcardPath valueOf(Path path) throws NullPointerException {
		Objects.requireNonNull(path, "path");
		return new WildcardPath(toPathArray(path));
	}

	/**
	 * Collects the files for multiple wildcard paths, and a given task context.
	 * <p>
	 * The {@linkplain ExecutionDirectoryContext#getRootDirectories() root directories} and
	 * {@linkplain TaskDirectoryContext#getTaskWorkingDirectory() working directory} for the task is used for base
	 * directories.
	 * 
	 * @param paths
	 *            The wildcard paths to collect the files for.
	 * @param taskcontext
	 *            The task context to use for base directories.
	 * @return The collected files mapped by their absolute paths.
	 * @see #getFiles(TaskContext)
	 * @throws NullPointerException
	 *             If any of the argument is <code>null</code>.
	 */
	public static NavigableMap<SakerPath, SakerFile> getFiles(Iterable<? extends WildcardPath> paths,
			TaskContext taskcontext) throws NullPointerException {
		Objects.requireNonNull(paths, "wildcards");
		Objects.requireNonNull(taskcontext, "task context");
		return getFiles(paths, taskcontext.getExecutionContext(), taskcontext.getTaskWorkingDirectory());
	}

	/**
	 * Collects the files for multiple wildcard paths, a given execution directory context, and a working directory.
	 * <p>
	 * If the execution directory context is <code>null</code>, no root directories are considered.
	 * 
	 * @param paths
	 *            The wildcard paths to collect the files for.
	 * @param executiondirectorycontext
	 *            The execution directory context.
	 * @param workingdirectory
	 *            The working directory to resolve relative paths against or <code>null</code> to not use a working
	 *            directory.
	 * @return The collected files mapped by their absolute paths.
	 * @throws NullPointerException
	 *             If the wildcards argument is <code>null</code>.
	 */
	public static NavigableMap<SakerPath, SakerFile> getFiles(Iterable<? extends WildcardPath> paths,
			ExecutionDirectoryContext executiondirectorycontext, SakerDirectory workingdirectory)
			throws NullPointerException {
		return getItems(paths,
				new SakerFileItemLister(workingdirectory,
						executiondirectorycontext == null ? Collections.emptyNavigableMap()
								: executiondirectorycontext.getRootDirectories()));
	}

	/**
	 * Collects the items for multiple wildcard paths and a given {@link ItemLister}.
	 * 
	 * @param paths
	 *            The wildcard paths to collect the files for.
	 * @param lister
	 *            The lister to use for enumerating items.
	 * @return The collected items mapped by their absolute paths to the items collected.
	 * @throws NullPointerException
	 *             If any of the argument is <code>null</code>.
	 */
	public static <T> NavigableMap<SakerPath, T> getItems(Iterable<? extends WildcardPath> paths, ItemLister<T> lister)
			throws NullPointerException {
		Objects.requireNonNull(paths, "paths");

		Iterator<? extends WildcardPath> it = paths.iterator();
		if (!it.hasNext()) {
			return Collections.emptyNavigableMap();
		}
		Objects.requireNonNull(lister, "item lister");
		WildcardPath first = it.next();
		if (!it.hasNext()) {
			return first.getItems(lister);
		}
		ConcurrentEntryMergeSorter<SakerPath, T> sorter = new ConcurrentEntryMergeSorter<>();
		sorter.add(first.getItems(lister));
		do {
			sorter.add(it.next().getItems(lister));
		} while (it.hasNext());
		return sorter.createTreeMap();
	}

	/**
	 * Collects the files for the specified task context.
	 * <p>
	 * The {@linkplain ExecutionDirectoryContext#getRootDirectories() root directories} and
	 * {@linkplain TaskDirectoryContext#getTaskWorkingDirectory() working directory} for the task is used for base
	 * directories.
	 * 
	 * @param taskcontext
	 *            The task context.
	 * @return The collected files mapped by their absolute paths.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public NavigableMap<SakerPath, SakerFile> getFiles(TaskContext taskcontext) throws NullPointerException {
		Objects.requireNonNull(taskcontext, "task context");
		return getFiles(taskcontext.getExecutionContext(), taskcontext.getTaskWorkingDirectory());
	}

	/**
	 * Collects the files for the specified directories.
	 * <p>
	 * The root directories specified by the execution directory will be used to discover absolute files.
	 * 
	 * @param executiondirectorycontext
	 *            The execution directory context.
	 * @param workingdir
	 *            The working directory to resolve relative paths against or <code>null</code> to not use a working
	 *            directory.
	 * @return The collected files mapped by their absolute paths.
	 */
	public NavigableMap<SakerPath, SakerFile> getFiles(ExecutionDirectoryContext executiondirectorycontext,
			SakerDirectory workingdir) {
		return getFiles(
				new SakerFileItemLister(workingdir, executiondirectorycontext == null ? Collections.emptyNavigableMap()
						: executiondirectorycontext.getRootDirectories()),
				splits);
	}

	/**
	 * Collects the files for a given file provider.
	 * <p>
	 * As no working directory is specified, only absolute wildcards should use this function.
	 * 
	 * @param fp
	 *            The file provider to discover files.
	 * @return The collected files mapped by the file attributes to their absolute paths.
	 * @throws NullPointerException
	 *             If the file provider is <code>null</code>.
	 */
	public NavigableMap<SakerPath, ? extends BasicFileAttributes> getFiles(SakerFileProvider fp)
			throws NullPointerException {
		return getFiles(fp, null);
	}

	/**
	 * Collects the files for a given file provider and working directory.
	 * 
	 * @param fp
	 *            The file provider to discover files.
	 * @param workingdir
	 *            The working directory to resolve relative paths against or <code>null</code> to not use a working
	 *            directory.
	 * @return The collected files mapped by the file attributes to their absolute paths.
	 * @throws NullPointerException
	 *             If the file provider is <code>null</code>.
	 */
	public NavigableMap<SakerPath, ? extends BasicFileAttributes> getFiles(SakerFileProvider fp, SakerPath workingdir)
			throws NullPointerException {
		Objects.requireNonNull(fp, "file provider");
		return getFiles(new FileProviderItemLister(fp, workingdir), splits);
	}

	/**
	 * Collects the files for the specified execution path configuration.
	 * <p>
	 * The root file providers and working directory in the argument configuration will be used as a base for file
	 * collection.
	 * 
	 * @param pathconfiguration
	 *            The path configuration.
	 * @return The collected files mapped by the file attributes to their absolute paths.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public NavigableMap<SakerPath, ? extends BasicFileAttributes> getFiles(ExecutionPathConfiguration pathconfiguration)
			throws NullPointerException {
		Objects.requireNonNull(pathconfiguration, "path configuration");
		return getFiles(new PathConfigurationItemLister(pathconfiguration), splits);
	}

	/**
	 * Collects the items for a given {@link ItemLister}.
	 * 
	 * @param lister
	 *            The lister to use for enumerating items.
	 * @return The collected items mapped by their absolute paths to the items collected.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public <T> NavigableMap<SakerPath, T> getItems(ItemLister<T> lister) throws NullPointerException {
		Objects.requireNonNull(lister, "item lister");
		return getFiles(lister, splits);
	}

	/**
	 * Collects a single file matching this wildcard for the given task context.
	 * <p>
	 * The {@linkplain ExecutionDirectoryContext#getRootDirectories() root directories} and
	 * {@linkplain TaskDirectoryContext#getTaskWorkingDirectory() working directory} for the task is used for base
	 * directories.
	 * 
	 * @param taskcontext
	 *            The task context.
	 * @return The discovered file or <code>null</code> if no matching file found.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public SakerFile getSingleFile(TaskContext taskcontext) throws NullPointerException {
		Objects.requireNonNull(taskcontext, "task context");
		return getSingleFile(taskcontext.getExecutionContext(), taskcontext.getTaskWorkingDirectory());
	}

	/**
	 * Collects a single file matching this wildcard for the given execution directory context and working directory.
	 * <p>
	 * The root directories specified by the execution directory will be used to discover absolute files.
	 * 
	 * @param executiondirectorycontext
	 *            The execution directory context.
	 * @param workingdir
	 *            The working directory to resolve relative paths against or <code>null</code> to not use a working
	 *            directory.
	 * @return The discovered file or <code>null</code> if no matching file found.
	 */
	public SakerFile getSingleFile(ExecutionDirectoryContext executiondirectorycontext, SakerDirectory workingdir) {
		Entry<SakerPath, SakerFile> found = getSingleFile(
				new SakerFileItemLister(workingdir, executiondirectorycontext == null ? Collections.emptyNavigableMap()
						: executiondirectorycontext.getRootDirectories()),
				splits);
		if (found == null) {
			return null;
		}
		return found.getValue();
	}

	/**
	 * Collects a single item matching this wildcard for the given {@link ItemLister}.
	 * 
	 * @param lister
	 *            The lister to use for enumerating items.
	 * @return The discovered item mapped to its absolute path or <code>null</code> if no matching item found.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public <T> Map.Entry<SakerPath, T> getSingleItem(ItemLister<T> lister) throws NullPointerException {
		Objects.requireNonNull(lister, "item lister");
		Entry<SakerPath, T> fentry = getSingleFile(lister, splits);
		if (fentry == null) {
			return null;
		}
		return ImmutableUtils.makeImmutableMapEntry(fentry);
	}

	/**
	 * Checks if <code>this</code> wildcard includes/matches the path specified by a list of path names.
	 * <p>
	 * The argument path may be relative or absolute.
	 * 
	 * @param paths
	 *            The list of path names.
	 * @return <code>true</code> if <code>this</code> wildcard includes the argument.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public boolean includes(List<? extends String> paths) throws NullPointerException {
		Objects.requireNonNull(paths, "paths");
		return includes(paths, splits);
	}

	/**
	 * Checks if <code>this</code> wildcard includes/matches the path specified by an array of path names.
	 * <p>
	 * The argument path may be relative or absolute.
	 * 
	 * @param paths
	 *            The array of path names.
	 * @return <code>true</code> if <code>this</code> wildcard includes the argument.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public boolean includes(String[] paths) throws NullPointerException {
		Objects.requireNonNull(paths, "paths");
		return includes(ImmutableUtils.asUnmodifiableArrayList(paths), splits);
	}

	/**
	 * Checks if <code>this</code> wildcard includes/matches the specified path.
	 * <p>
	 * The argument will be split up in the same way {@link #valueOf(String)} does.
	 * <p>
	 * The argument path may be relative or absolute.
	 * 
	 * @param path
	 *            The path.
	 * @return <code>true</code> if <code>this</code> wildcard includes the argument.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public boolean includes(String path) throws NullPointerException {
		Objects.requireNonNull(path, "path");
		return includes(ImmutableUtils.asUnmodifiableArrayList(splitPath(path)), splits);
	}

	/**
	 * Checks if <code>this</code> wildcard includes/matches the specified path.
	 * <p>
	 * The argument path may be relative or absolute.
	 * 
	 * @param path
	 *            The path.
	 * @return <code>true</code> if <code>this</code> wildcard includes the argument.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public boolean includes(SakerPath path) throws NullPointerException {
		Objects.requireNonNull(path, "path");
		return includes(path.getPathList(), splits);
	}

	/**
	 * Checks if appending one or more names to the argument path can result in the wildcard
	 * {@linkplain #includes(SakerPath) including} it.
	 * <p>
	 * The method returns <code>true</code> if any only if there exists an appending operation to the argument path that
	 * results in the wildcard matching the result.
	 * 
	 * @param path
	 *            The path to examine.
	 * @return <code>true</code> if the path can be finished so <code>this</code> wildcard matches it.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public boolean finishable(SakerPath path) throws NullPointerException {
		Objects.requireNonNull(path, "path");
		return finishable(path.getPathList(), splits);
	}

	/**
	 * Checks if appending one or more names to the argument array of path names can result in the wildcard
	 * {@linkplain #includes(SakerPath) including} it.
	 * <p>
	 * The method returns <code>true</code> if any only if there exists an appending operation to the argument path that
	 * results in the wildcard matching the result.
	 * 
	 * @param paths
	 *            The path names to examine.
	 * @return <code>true</code> if the path can be finished so <code>this</code> wildcard matches it.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public boolean finishable(String[] paths) throws NullPointerException {
		Objects.requireNonNull(paths, "paths");
		return finishable(ImmutableUtils.unmodifiableArrayList(paths), splits);
	}

	/**
	 * Tries to optimize <code>this</code> wildcard path by removing (reducing) the starting path names that match the
	 * path names in an exact manner.
	 * <p>
	 * As a result of this call, the wildcard path will be separated into two parts:
	 * <ol>
	 * <li>The first part contains the path names which doesn't contain any wildcards, therefore matching the input in
	 * an exact manner.</li>
	 * <li>The second part starts with the first wildcarded path name and lasts until the end of <code>this</code>
	 * path.</li>
	 * </ol>
	 * The resulting reduced wildcard can be used by resolving possible inputs against the reduced first part, and
	 * applying the remaining wildcard to that.
	 * <p>
	 * The resulting first or second parts might be <code>null</code> in which case <code>this</code> wildcard either
	 * fully matches paths exactly, or is wildcarded from the beginning.
	 * <p>
	 * Examples:
	 * <ul>
	 * <li><code>dir/*.ext</code> will be reduced to <code>dir</code> and <code>*ext</code></li>
	 * <li><code>dir/file.ext</code> will be reduced to <code>dir/file.ext</code> and <code>null</code></li>
	 * <li><code>*.ext</code> will be reduced to <code>null</code> and <code>*ext</code></li>
	 * </ul>
	 * 
	 * @return The reduced wildcard path.
	 */
	public ReducedWildcardPath reduce() {
		if (splits.length == 0 || isWildcardedPathPart(splits[0])) {
			return new ReducedWildcardPath(null, this);
		}
		int i;
		for (i = 1; i < splits.length; i++) {
			if (isWildcardedPathPart(splits[i])) {
				break;
			}
		}
		try {
			if (i == splits.length) {
				//no wildcard at all
				return new ReducedWildcardPath(SakerPath.valueOf(toString()), null);
			}
			String reldir = StringUtils.toStringJoin("/", new ArrayIterator<>(splits, 0, i));
			SakerPath reldirpath = SakerPath.valueOf(reldir);

			WildcardPath subwildcard = new WildcardPath(Arrays.copyOfRange(splits, i, splits.length));
			return new ReducedWildcardPath(reldirpath, subwildcard);
		} catch (IllegalArgumentException e) {
			//the wildcard might not be a valid path
			return new ReducedWildcardPath(null, this);
		}
	}

	/**
	 * Checks if this wildcard path only consists of path names that will match all files recursively.
	 * 
	 * @return <code>true</code> if this path contains only the <code>"**"</code> path names.
	 */
	public boolean isRecursiveAllFilesPath() {
		for (String s : splits) {
			switch (s) {
				case "**": {
					continue;
				}
				default: {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public String toString() {
		String[] splits = this.splits;
		int len = splits.length;
		switch (len) {
			case 0: {
				return "";
			}
			case 1: {
				return splits[0];
			}
			default: {
				StringBuilder sb;
				if (splits[0].equals("/")) {
					//the wildcard starts with the slash root path, don't append the first path name
					sb = new StringBuilder();
				} else {
					sb = new StringBuilder(splits[0]);
				}
				for (int i = 1; i < len; i++) {
					sb.append('/');
					sb.append(splits[i]);
				}
				return sb.toString();
			}
		}
	}

	@Override
	public int compareTo(WildcardPath o) {
		return ObjectUtils.compareArrays(splits, o.splits, String::compareTo);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(splits);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		splits = (String[]) in.readObject();
	}

	@Override
	public int hashCode() {
		return CLASS_HASH_CODE + Arrays.hashCode(splits);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		WildcardPath other = (WildcardPath) obj;
		if (!Arrays.equals(splits, other.splits))
			return false;
		return true;
	}

	private static String[] toPathArray(Path path) {
		path = path.normalize();

		String[] splits;
		int index = 0;
		Path root = path.getRoot();
		int namecount = path.getNameCount();
		if (root != null) {
			splits = new String[namecount + 1];
			splits[0] = root.toString();
			index = 1;
		} else {
			splits = new String[namecount];
		}
		for (Path p : path) {
			splits[index++] = p.toString();
		}
		return splits;
	}

	private static String[] splitPath(String path) {
		if (path.isEmpty()) {
			return ObjectUtils.EMPTY_STRING_ARRAY;
		}
		String[] splits = FileUtils.splitPath(path);
		if (splits.length == 0) {
			if (path.length() == 1) {
				char c = path.charAt(0);
				if (c == '/' || c == '\\') {
					return ROOT_SLASH_STRING_ARRAY;
				}
			}
			throw new InvalidPathFormatException("Failed to parse path: \"" + path + "\"");
		}
		if (path.charAt(0) == '/') {
			// absolute, linux, mac
			// String[] nsplit = new String[splits.length + 1];
			// nsplit[0] = "/";
			// System.arraycopy(splits, 0, nsplit, 1, splits.length);
			// splits = nsplit;

			// if starts with /, the the first split will be empty string, reassign it
			splits[0] = "/";
		}
		return splits;
	}

	private static boolean isWildcarded(String[] splits) {
		for (String part : splits) {
			if (isWildcardedPathPart(part)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isWildcardedPathPart(String part) {
		if (part.indexOf('*') >= 0) {
			return true;
		}
		return false;
	}

	private static <T> Map.Entry<SakerPath, T> getSingleFile(ItemLister<T> lister, String[] split) {
		//XXX make a more efficient implementation where only one file is collected, and the stopping? maybe with throwing an exception?
		return getFiles(lister, split).firstEntry();
	}

	private static <T> NavigableMap<SakerPath, T> getFiles(ItemLister<T> lister, String[] splits) {
		if (splits.length == 0) {
			T workingitem = lister.getWorkingItem();
			if (workingitem != null) {
				SakerPath workingpath = lister.getWorkingItemPath();
				if (workingpath != null) {
					return ImmutableUtils.singletonNavigableMap(workingpath, workingitem);
				}
			}
			return Collections.emptyNavigableMap();
		}
		String root = splits[0];
		if (!isWildcarded(splits)) {
			SakerPath splitspath = SakerPath.valueOf(String.join("/", splits));
			if (splitspath.isRelative()) {
				SakerPath workingpath = lister.getWorkingItemPath();
				if (workingpath == null) {
					return Collections.emptyNavigableMap();
				}
				splitspath = workingpath.resolve(splitspath);
			}
			T exactitem = lister.getItem(splitspath);
			if (exactitem == null) {
				return Collections.emptyNavigableMap();
			}
			return ImmutableUtils.singletonNavigableMap(splitspath, exactitem);
		}
		int startidx;
		final NavigableMap<SakerPath, T> currentdirs;
		if (isWildcardedPathPart(root)) {
			if (root.endsWith(":")) {
				startidx = 1;
				currentdirs = new TreeMap<>();
				//this is a root wildcard
				Set<String> roots = lister.getRootItems();
				for (String r : roots) {
					if (wildcardMatches(r, root)) {
						T resolvedroot = lister.getRootItem(r);
						if (resolvedroot != null) {
							//the root was actually not found
							currentdirs.put(SakerPath.valueOf(r), resolvedroot);
						}
					}
				}
			} else {
				//not a root wildcard, use the working directory as a base, and start parsing with the root path
				T workingitem = lister.getWorkingItem();
				if (workingitem == null) {
					//working directory is not available, no files found
					return Collections.emptyNavigableMap();
				}
				currentdirs = new TreeMap<>();
				currentdirs.put(lister.getWorkingItemPath(), workingitem);
				startidx = 0;
			}
		} else {
			T rootitem = lister.getRootItem(root);
			currentdirs = new TreeMap<>();
			if (rootitem != null) {
				currentdirs.put(SakerPath.valueOf(root), rootitem);
			}
			//do not try to resolve a root name against the working directory
			if (!SakerPath.isValidRootName(root)) {
				T workingitem = lister.getWorkingItem();
				if (workingitem != null) {
					T resolved = lister.resolve(workingitem, root);
					if (resolved != null) {
						currentdirs.put(lister.getWorkingItemPath().resolve(root), resolved);
					}
				}
			}
			startidx = 1;
		}
		if (startidx == splits.length) {
			return currentdirs;
		}
		//XXX reduce the number of object creation (TreeMap)
		for (int i = startidx;; i++) {
			String pathpart = splits[i];
			if (pathpart.isEmpty()) {
				//any empty path name shouldn't occur. 
				//only if the wildcard was constructed with unsanitized data in valueOf(String[])
				//we don't match any files with an empty path part
				return Collections.emptyNavigableMap();
			}
			NavigableMap<SakerPath, T> files = new TreeMap<>();
			if (isWildcardedPathPart(pathpart)) {
				if ("**".equals(pathpart)) {
					if (i == splits.length - 1) {
						//include the current dirs too, as ** can resolve to 0 path names
						files.putAll(currentdirs);
						//this is the last pathname, just collect the files recursively and return
						for (Entry<SakerPath, T> entry : currentdirs.entrySet()) {
							NavigableMap<SakerPath, ? extends T> children = lister
									.listChildItemsRecursively(entry.getValue());
							if (!children.isEmpty()) {
								SakerPath resolve = entry.getKey();
								for (Entry<SakerPath, ? extends T> centry : children.entrySet()) {
									files.put(resolve.resolve(centry.getKey()), centry.getValue());
								}
							}
						}
						return files;
					}
					//we need to examine the resolved paths as this "**" is not the last path name

					//include the "**" part in the remaining wildcard as well
					String[] remainsplits = Arrays.copyOfRange(splits, i, splits.length);

					for (Entry<SakerPath, T> entry : currentdirs.entrySet()) {
						SakerPath resolve = entry.getKey();
						T diritem = entry.getValue();
						NavigableMap<SakerPath, ? extends T> children = lister.listChildItemsRecursively(diritem);
						for (Entry<SakerPath, ? extends T> centry : children.entrySet()) {
							SakerPath childrelativepath = centry.getKey();
							if (includes(childrelativepath.getNameList(), remainsplits)) {
								files.put(resolve.resolve(childrelativepath), centry.getValue());
							}
						}
						//test the current directory too, but do it with an empty list as the relative path
						if (includes(Collections.emptyList(), remainsplits)) {
							files.put(resolve, diritem);
						}
					}

					//return files as we have checked all the recursively collected files for inclusion
					return files;
				}
				//handle non-recursive wildcard path name
				for (Entry<SakerPath, T> entry : currentdirs.entrySet()) {
					SakerPath resolve = entry.getKey();
					for (Entry<String, ? extends T> childentry : lister.listChildItems(entry.getValue()).entrySet()) {
						String name = childentry.getKey();
						if (wildcardMatches(name, pathpart)) {
							files.put(resolve.resolve(name), childentry.getValue());
						}
					}
				}
			} else {
				//non wildcarded path part
				for (Entry<SakerPath, T> entry : currentdirs.entrySet()) {
					T resolved = lister.resolve(entry.getValue(), pathpart);
					if (resolved != null) {
						files.put(entry.getKey().resolve(pathpart), resolved);
					}
				}
			}
			if (files.isEmpty()) {
				//cannot match any more files
				return Collections.emptyNavigableMap();
			}
			if (i == splits.length - 1) {
				return files;
			}
			currentdirs.clear();
			currentdirs.putAll(files);
		}
	}

	private static boolean includes(List<? extends String> paths, String[] wildcard, int pathslen, int pi, int wci) {
		while (pi < pathslen && wci < wildcard.length) {
			String currentpath = paths.get(pi++);
			String currentwildcard = wildcard[wci++];
			if ("**".equals(currentwildcard)) {
				// recursive directory wildcard
				if (wci >= wildcard.length) {
					// ** is at the end of the path
					return true;
				}
				String nextwildcard;
				do {
					nextwildcard = wildcard[wci++];
				} while ("**".equals(nextwildcard) && wci < wildcard.length);

				if ("**".equals(nextwildcard)) {
					// ** is at the end of the path
					return true;
				}
				--pi;
				while (pi < pathslen) {
					String next = paths.get(pi++);
					if (wildcardMatches(next, nextwildcard)) {
						// found match
						boolean restmatch = includes(paths, wildcard, pathslen, pi, wci);
						if (restmatch) {
							return true;
						}
					}
				}
				//no next path part found that matches the next wildcard path
				return false;
			}
			if (!wildcardMatches(currentpath, currentwildcard)) {
				// wildcard doesnt match current
				return false;
			}
		}
		while (wci < wildcard.length) {
			//handle extra "**" at wildcard ends
			if ("**".equals(wildcard[wci])) {
				++wci;
				continue;
			}
			break;
		}
		return pi >= pathslen && wci >= wildcard.length;
	}

	private static boolean includes(List<? extends String> paths, String[] wildcard) {
		return includes(paths, wildcard, paths.size(), 0, 0);
	}

	private static boolean finishable(List<? extends String> paths, String[] wildcard, int pathslen, int pi, int wci) {
		while (pi < pathslen && wci < wildcard.length) {
			String currentpath = paths.get(pi++);
			String currentwildcard = wildcard[wci++];
			if ("**".equals(currentwildcard)) {
				// recursive directory wildcard
				// if theres a ** part, then the path is always finishable, as if the next parts doesn't match
				//    then the ** can just ignore it
				return true;
			}
			if (!wildcardMatches(currentpath, currentwildcard)) {
				// wildcard doesnt match current
				return false;
			}
		}
		while (wci < wildcard.length) {
			//handle extra "**" at wildcard ends
			if ("**".equals(wildcard[wci])) {
				return true;
			}
			break;
		}
		return pi >= pathslen && wci < wildcard.length;
	}

	private static boolean finishable(List<? extends String> paths, String[] wildcard) {
		return finishable(paths, wildcard, paths.size(), 0, 0);
	}

	private static boolean wildcardMatches(String name, String wildcard) {
		//XXX make this function operate on a character level instead of creating substrings
		int nameindex = 0;
		int starindex = wildcard.indexOf('*');
		if (starindex < 0) {
			return name.equals(wildcard);
		}
		if (!name.regionMatches(0, wildcard, 0, starindex)) {
			//same as
			//    name.startsWith(wildcard.substring(0, starindex))
			//but doesnt create a substring
			return false;
		}
		int wildcardlen = wildcard.length();
		while (starindex + 1 < wildcardlen) {
			int nextstarindex = wildcard.indexOf('*', starindex + 1);

			if (nextstarindex < 0) {
				//if we got no more star wildcards, we return true if the remaining of the name matches the remaining of the wildcard

				int sublen = wildcardlen - (starindex + 1);
				//same as 
				//    return name.endsWith(wildcard.substring(starindex + 1, wildcardlen));
				// but doesnt create a substring
				return name.regionMatches(name.length() - sublen, wildcard, starindex + 1, sublen);
			}
			String sub = wildcard.substring(starindex + 1, nextstarindex);
			int subindex = name.indexOf(sub, nameindex);
			if (subindex < 0) {
				return false;
			}
			if (subindex >= nameindex) {
				nameindex = subindex + sub.length();
			}
			starindex = nextstarindex;
			if (nextstarindex < 0) {
				break;
			}
		}
		// Exits if starindex is at the end of name. Accept then.
		// either all of the name was processed, or the last star is at the end
		return nameindex == name.length() || starindex + 1 == wildcardlen;
	}
}
