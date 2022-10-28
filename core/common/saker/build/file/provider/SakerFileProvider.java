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
package saker.build.file.provider;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.spi.FileSystemProvider;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import saker.apiextract.api.ExcludeApi;
import saker.apiextract.api.PublicApi;
import saker.build.file.path.PathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEventListener.ListenerToken;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIExceptionRethrow;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.RemoteIOException;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIArrayListRemoteElementWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIMapEntryWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeMapSerializeKeySerializeValueWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeSetStringElementWrapper;
import saker.build.thirdparty.saker.util.rmi.writer.EnumArrayRMIObjectWriteHandler;
import saker.build.util.rmi.EnumSetRMIWrapper;

/**
 * Interface for providing direct access to the file system files.
 * <p>
 * <b>Important:</b> tasks in the build system should use this interface to read from or write to the file system as
 * this interface is RMI compatible. Client code can use this to transparently access files on computers which are
 * available through network.
 * <p>
 * This interface is similar in functionality that {@link FileSystemProvider} would provide. It allows clients to access
 * the file system in a similar way, while allowing RMI compatibility.
 * <p>
 * Each file provider bears a key to itself which uniquely identifiers its location. If two file provider keys equal,
 * clients can be sure that they will operate on the same files if their functions are called with the same paths. See
 * {@link #getProviderKey()} and {@link FileProviderKey}.
 * <p>
 * If a function name contains the phrase <code>"Recursively"</code> then client should expect that it will not throw a
 * {@link DirectoryNotEmptyException} if the deleted directory is not empty.
 * <p>
 * Clients can install file event listeners for a file provider to get notified about changes in the file system. Build
 * system task implementations are usually not recommended to do this, unless very specific use-cases. If they do, use
 * it with the caching support of {@link SakerEnvironment}.
 * <p>
 * File providers can be used to wrap each other, providing restricted or enhanced functionality for a subject file
 * provider. An example for this is a file provider that mounts a directory therefor limits access to a directory on a
 * subject by allowing access to it using a specified root. File providers are considered root file providers if they do
 * not wrap a subject file provider. Root file providers are required to return a {@link RootFileProviderKey} instance
 * from {@link #getProviderKey()}.
 * <p>
 * File providers operate exclusively on absolute paths. Passing a relative path to them can result in an
 * {@link IllegalArgumentException}.
 * <p>
 * Any method is allowed to throw {@link NullPointerException} if a <code>null</code> path is passed to them.
 * 
 * @see LocalFileProvider
 * @see FileProviderKey
 * @see PathKey
 */
@PublicApi
public interface SakerFileProvider {
	/**
	 * Singleton instance for an empty {@linkplain LinkOption link options} array.
	 */
	public static final LinkOption[] EMPTY_LINK_OPTIONS = {};
	/**
	 * Singleton instance for an empty {@linkplain OpenOption open options} array.
	 */
	public static final OpenOption[] EMPTY_OPEN_OPTIONS = EMPTY_LINK_OPTIONS;
	/**
	 * Singleton instance for an empty {@linkplain CopyOption copy options} array.
	 */
	public static final CopyOption[] EMPTY_COPY_OPTIONS = EMPTY_LINK_OPTIONS;

	/**
	 * Operation flag constant specifying no flags.
	 */
	public static final int OPERATION_FLAG_NONE = 0;
	/**
	 * Operation flag to specify that if a directory is encountered, it shouldn't be recursively deleted.
	 * <p>
	 * This flag doesn't necessary prevent the deletion of empty directories.
	 * <p>
	 * Used by: <br>
	 * {@link #ensureWriteRequest(SakerPath, int, int)}
	 */
	public static final int OPERATION_FLAG_NO_RECURSIVE_DIRECTORY_DELETE = 1 << 0;
	/**
	 * Operation flag to specify that if directories are being recursively created, then intermediate already existing
	 * files should be deleted.
	 * <p>
	 * Used by: <br>
	 * {@link #ensureWriteRequest(SakerPath, int, int)}
	 */
	//TODO this flag should be useable in createdirectories
	public static final int OPERATION_FLAG_DELETE_INTERMEDIATE_FILES = 1 << 1;

	/**
	 * Operation result flag representing that no changes were issued.
	 */
	public static final int RESULT_NO_CHANGES = 0;
	/**
	 * Operation result flag representing that some directories were created.
	 */
	public static final int RESULT_FLAG_DIRECTORY_CREATED = 1 << 0;
	/**
	 * Operation result flag representing that some files were deleted.
	 */
	public static final int RESULT_FLAG_FILES_DELETED = 1 << 1;

	/**
	 * Gets the file system roots of this file provider.
	 * <p>
	 * File system roots do not change during the lifetime of a file provider. They are normalized according to the
	 * rules of {@link SakerPath#normalizeRoot(String)}.
	 * <p>
	 * Note: It can happen that some file system drives are changed during the lifetime of a file provider. (E.g. USB
	 * drive inserted/removed, etc...) The file provider implementations are not required to recognize these changes,
	 * and restarting the build system process might be required to handle the file system changes.
	 * 
	 * @return An unmodifiable set of root names for this file provider. The returned set is ordered by natural order.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@RMICacheResult
	@RMIWrap(RMITreeSetStringElementWrapper.class)
	@RMIExceptionRethrow(RemoteIOException.class)
	public NavigableSet<String> getRoots() throws IOException;

	/**
	 * Gets the file provider key for this file provider.
	 * <p>
	 * If this file provider is a root provider, then the result will be an instance of {@link RootFileProviderKey}.
	 * 
	 * @return The file provider key.
	 */
	@RMISerialize
	@RMICacheResult
	public FileProviderKey getProviderKey();

	/**
	 * Gets the attributes of a file in the directory specified by the argument path.
	 * <p>
	 * This method only returns non-<code>null</code> if there is a directory at the denoted path, and in that directory
	 * there is only one file. The attributes for the file is returned.
	 * <p>
	 * This method follows symbolic links when determining the attribute.
	 * 
	 * @param path
	 *            The directory to get the entry for.
	 * @return A map entry where the key is the name of the found file, the value is the attributes of the file.
	 *             <code>null</code> if no file or more than one was found.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@RMIWrap(RMIMapEntryWrapper.class)
	@RMIExceptionRethrow(RemoteIOException.class)
	public default Map.Entry<String, ? extends FileEntry> getDirectoryEntryIfSingle(SakerPath path) throws IOException {
		NavigableMap<String, ? extends FileEntry> entries = getDirectoryEntries(path);
		if (entries.size() == 1) {
			return entries.firstEntry();
		}
		return null;
	}

	/**
	 * Gets the entries in the directory denoted by the argument path.
	 * <p>
	 * The result contains the file names mapped to their attributes.
	 * <p>
	 * This method follows symbolic links when determining the attributes.
	 * 
	 * @param path
	 *            The path to the directory.
	 * @return The directory entries. The returned map is ordered by natural order.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@RMIWrap(RMITreeMapSerializeKeySerializeValueWrapper.class)
	@RMIExceptionRethrow(RemoteIOException.class)
	public NavigableMap<String, ? extends FileEntry> getDirectoryEntries(SakerPath path) throws IOException;

	/**
	 * Collects the entries in the specified directory, recursively.
	 * <p>
	 * This method contains every file entry that is under the specified directory and its subdirectories.
	 * <p>
	 * Use this method judiciously as a large number of files might be queried.
	 * <p>
	 * The file attribute values in the result map are mapped to their relative path based on the argument path.
	 * <p>
	 * This method follows symbolic links for directories and when determining attributes.
	 * 
	 * @param path
	 *            The path to the directory.
	 * @return The recursively collected file entries mapped to their paths relative to the argument. The returned map
	 *             is ordered by natural order.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@RMIWrap(RMITreeMapSerializeKeySerializeValueWrapper.class)
	@RMIExceptionRethrow(RemoteIOException.class)
	public NavigableMap<SakerPath, ? extends FileEntry> getDirectoryEntriesRecursively(SakerPath path)
			throws IOException;

	/**
	 * Gets the attributes of the file at the specified path.
	 * 
	 * @param path
	 *            The path to the file.
	 * @param linkoptions
	 *            Link options to use when retrieving the attributes.
	 * @return The attributes of the file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see LinkOption
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public FileEntry getFileAttributes(SakerPath path,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) LinkOption... linkoptions) throws IOException;

	/**
	 * Sets the posix file permissions for the file at the given path.
	 * <p>
	 * This method will only set the file permissions if the underlying filesystem supports it. In that case the method
	 * will return <code>true</code>. Otherwise <code>false</code>.
	 * <p>
	 * The method <b>does not</b> throw an exception if the underlying filesystem doesn't support posix file
	 * permissions.
	 * 
	 * @param path
	 *            The path of the file.
	 * @param permissions
	 *            The permississions to set.
	 * @return <code>true</code> if the underlying filesystem supports posix file permissions. <code>false</code>
	 *             otherwise.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IOException
	 *             In case of I/O error.
	 * @since saker.build 0.8.13
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default boolean setPosixFilePermissions(SakerPath path,
			@RMIWrap(EnumSetRMIWrapper.class) Set<PosixFilePermission> permissions)
			throws NullPointerException, IOException {
		return false;
	}

	/**
	 * Modifies the posix file permissions for the file at the given path.
	 * <p>
	 * The method takes two permission set arguments which control how the permissions should be modified. First all
	 * permissions in the <code>add</code> argument are added, then all permissions in the <code>remove</code> argument
	 * are removed.
	 * <p>
	 * The adding and removal are performed in a single filesystem operation.
	 * <p>
	 * If a permission is present in both <code>add</code> and <code>remove</code> permission sets, then it will be
	 * removed.
	 * <p>
	 * The method <b>does not</b> throw an exception if the underlying filesystem doesn't support posix file
	 * permissions.
	 * 
	 * @param path
	 *            The path of the file.
	 * @param addpermissions
	 *            The permissons to add. May be <code>null</code> to not add anything.
	 * @param removepermissions
	 *            The permissions to remove. May be <code>null</code> to not remove anything.
	 * @return <code>true</code> if the underlying filesystem supports posix file permissions. <code>false</code>
	 *             otherwise.
	 * @throws NullPointerException
	 *             If the path argument or any of the permission elements are <code>null</code>.
	 * @throws IOException
	 *             In case of I/O error.
	 * @since saker.build 0.8.13
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default boolean modifyPosixFilePermissions(SakerPath path,
			@RMIWrap(EnumSetRMIWrapper.class) Set<PosixFilePermission> addpermissions,
			@RMIWrap(EnumSetRMIWrapper.class) Set<PosixFilePermission> removepermissions)
			throws NullPointerException, IOException {
		return false;
	}

	/**
	 * Gets the posix file permissions of the file at the given path.
	 * <p>
	 * The method will query the posix file permissions of the specified file if the underlying file system supports
	 * them.
	 * <p>
	 * The method <b>does not</b> throw an exception if the underlying filesystem doesn't support posix file
	 * permissions.
	 * 
	 * @param path
	 *            The path of the file.
	 * @return The posix file permissions of the file or <code>null</code> if the underlying filesystem doesn't support
	 *             them.
	 * @throws NullPointerException
	 *             If the path argument is <code>null</code>.
	 * @throws IOException
	 *             In case of I/O error.
	 * @since saker.build 0.8.13
	 */
	@RMIWrap(EnumSetRMIWrapper.class)
	@RMIExceptionRethrow(RemoteIOException.class)
	public default Set<PosixFilePermission> getPosixFilePermissions(SakerPath path)
			throws NullPointerException, IOException {
		return null;
	}

	/**
	 * Sets the last modified time of the file at the given path.
	 * <p>
	 * The milliseconds are converted using {@link FileTime#fromMillis(long)}.
	 * 
	 * @param path
	 *            The file path.
	 * @param millis
	 *            The modification time in milliseconds.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public void setLastModifiedMillis(SakerPath path, long millis) throws IOException;

	/**
	 * Creates the directory and necessary parent directors at the given path.
	 * <p>
	 * If the directories cannot be created due to a non-directory file is already present at the path or at one of its
	 * parent paths, the method will fail.
	 * 
	 * @param path
	 *            The path to create the directory at.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws FileAlreadyExistsException
	 *             If there is already a non-directory file at the path (or any parent paths).
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public void createDirectories(SakerPath path) throws IOException, FileAlreadyExistsException;

	/**
	 * Deletes a file or directory at the given path, recursively if it is a directory.
	 * <p>
	 * This method will delete all files at the given path, clearing the directory beforehand if necessary.
	 * <p>
	 * This method doesn't follow symbolic links. Links to directories will be deleted, the directories won't be cleared
	 * if they are not under the specified path.
	 * 
	 * @param path
	 *            The path to delete.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public void deleteRecursively(SakerPath path) throws IOException;

	/**
	 * Deletes a file or directory at the given path.
	 * <p>
	 * The method will fail if the path denotes a directory and it is not empty.
	 * <p>
	 * If the path denotes a symbolic link, only the link will be deleted.
	 * <p>
	 * If there is no file at the given path, the method will succeed, and won't throw an exception. Therefore this
	 * methods works in the manner of "delete if exists".
	 * 
	 * @param path
	 *            The path to delete.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws DirectoryNotEmptyException
	 *             If the path denotes a file and it's not empty.
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public void delete(SakerPath path) throws IOException, DirectoryNotEmptyException;

	/**
	 * Opens an input stream to the file at the specified path.
	 * <p>
	 * The {@link OpenOption} argument must contain elements which are enumerations.
	 * <p>
	 * The returned stream is not thread-safe.
	 * 
	 * @param path
	 *            The path to the file.
	 * @param openoptions
	 *            The opening options for the input.
	 * @return An opened stream.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see StandardOpenOption
	 * @see ByteSource#toInputStream(ByteSource)
	 * @see FileSystemProvider#newInputStream(Path, OpenOption...)
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public ByteSource openInput(SakerPath path,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) OpenOption... openoptions) throws IOException;

	/**
	 * Opens an output stream to the file at the specified path.
	 * <p>
	 * The caller must close the returned stream to properly persist the written bytes to the stream. If the caller
	 * doesn't close the stream, data corruption might occur. If the stream is not closed, the data may not have been
	 * written to the disk. Implementations may cache the written bytes and flush them when {@link ByteSink#close()
	 * close()} is called.
	 * <p>
	 * If this method successfully returns, that doesn't necessarily mean that the file has been opened. Opening related
	 * exceptions may be thrown by the writing or closing methods of the returned stream.
	 * <p>
	 * The {@link OpenOption} argument must contain elements which are enumerations.
	 * <p>
	 * The returned stream is not thread-safe.
	 * 
	 * @param path
	 *            The path to the file.
	 * @param openoptions
	 *            The opening options for the output.
	 * @return An opened output stream.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see StandardOpenOption
	 * @see ByteSink#toOutputStream(ByteSink)
	 * @see FileSystemProvider#newOutputStream(Path, OpenOption...)
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public ByteSink openOutput(SakerPath path,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) OpenOption... openoptions) throws IOException;

	/**
	 * Ensures that a write request to the given path can be initiated with the given type.
	 * <p>
	 * This method examines the current file at the given path, and ensures that the file is either has the same type,
	 * or a write request can be initiated to it.
	 * <p>
	 * The argument file type and behaviour based on it is the following:
	 * <ul>
	 * <li>{@link FileEntry#TYPE_FILE}: If there is a directory at the path, it will be deleted recursively*. If there
	 * is a file at the path, no operation will be done as it can be overwritten. If not all parent directories exist at
	 * the path, the parent directories will be created recursively. This ensures that a following
	 * {@link #openOutput(SakerPath, OpenOption...)} can succeed.<br>
	 * * If the {@link #OPERATION_FLAG_NO_RECURSIVE_DIRECTORY_DELETE} is specified, the encountered directory will only
	 * be deleted if it is empty. An exception is thrown instead if it is not empty.</li>
	 * <li>{@link FileEntry#TYPE_DIRECTORY}: If there is a non-directory file at the path, it will be deleted. If there
	 * is a directory at the path, no operation will be done. If there is no file at the path, all directories will be
	 * created, including the one at the path as well.</li>
	 * </ul>
	 * The directory creation for parent paths in the above behaviours works the same way as
	 * {@link #createDirectories(SakerPath)}.
	 * <p>
	 * This method is mainly useful to batch the attribute reading-deletion-file creation operations into one call,
	 * therefore ensuring that a given path is writeable for the given file type.
	 * <p>
	 * Note that this function is not atomic. The checking of the currently present file, deletions, and creation are
	 * not done atomically, therefore this method may fail if concurrent modifications distrupt it. It is recommended
	 * for callers to ensure private and synchronized access to the given path.
	 * 
	 * @param path
	 *            The path.
	 * @param filetype
	 *            The file type to ensure the write request for.
	 * @param operationflag
	 *            The operation flag to take into account. Acceptable flags:
	 *            {@link #OPERATION_FLAG_NO_RECURSIVE_DIRECTORY_DELETE},
	 *            {@link #OPERATION_FLAG_DELETE_INTERMEDIATE_FILES}.
	 * @return The result flags describing the changes made by this method. See {@link #RESULT_NO_CHANGES},
	 *             {@link #RESULT_FLAG_FILES_DELETED}, {@link #RESULT_FLAG_DIRECTORY_CREATED}.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws IllegalArgumentException
	 *             If the file type is not {@link FileEntry#TYPE_FILE} and not {@link FileEntry#TYPE_DIRECTORY}.
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public int ensureWriteRequest(SakerPath path, int filetype, int operationflag)
			throws IOException, IllegalArgumentException;

	/**
	 * Moves a file from on location to another.
	 * 
	 * @param source
	 *            The file to move.
	 * @param target
	 *            The path to move the file to.
	 * @param copyoptions
	 *            The copy options to use.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see StandardCopyOption
	 * @see LinkOption
	 * @see FileSystemProvider#move(Path, Path, CopyOption...)
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public void moveFile(SakerPath source, SakerPath target,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) CopyOption... copyoptions) throws IOException;

	/**
	 * Clears the directory and all subdirectories at the specified path.
	 * <p>
	 * This method follows symbolic links for retrieving the children, but doesn't when deleting the entries. Meaning,
	 * if a directory contains a link, the link will be deleted and the directory at the end of the link will not be
	 * cleared.
	 * 
	 * @param path
	 *            The path to the directory to clear.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default void clearDirectoryRecursively(SakerPath path) throws IOException {
		deleteChildrenRecursivelyIfNotIn(path, Collections.emptyNavigableSet());
	}

	/**
	 * Checks if the given attributes of a file at the specified path are the same or not.
	 * <p>
	 * This method retrieves the attributes of the file at the path and compares the specified attributes.
	 * 
	 * @param path
	 *            The path to the file.
	 * @param size
	 *            The expected size of the file.
	 * @param modificationmillis
	 *            The expected modification time of the file.
	 * @param linkoptions
	 *            The link options to use.
	 * @return <code>true</code> if failed to retrieve the attributes, or the attributes changed.
	 * @see LinkOption
	 */
	public default boolean isChanged(SakerPath path, long size, long modificationmillis,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) LinkOption... linkoptions) {
		try {
			FileEntry entry = getFileAttributes(path, linkoptions);
			return entry.size() != size || entry.getLastModifiedMillis() != modificationmillis;
		} catch (IOException e) {
		}
		return true;
	}

	/**
	 * Deletes the children of a directory recursively, if the name of a given child is not in the specified set.
	 * <p>
	 * If a directory child is a directory, it will be recursively deleted, meaning any of its subdirectories will be
	 * deleted too.
	 * <p>
	 * This method doesn't follow symbolic links. Meaning, if a directory contains a link, the link will be deleted and
	 * the directory at the end of the link will not be cleared.
	 * 
	 * @param path
	 *            The path to the directory.
	 * @param childfilenames
	 *            A set of names in the specified directory to not delete.
	 * @return The names of the actually deleted files. The returned set is ordered by natural order.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws PartiallyDeletedChildrenException
	 *             If the deletion was aborted because the deletion of a given child threw an exception.
	 */
	@RMIWrap(RMITreeSetStringElementWrapper.class)
	@RMIExceptionRethrow(RemoteIOException.class)
	public default NavigableSet<String> deleteChildrenRecursivelyIfNotIn(SakerPath path,
			@RMIWrap(RMITreeSetStringElementWrapper.class) Set<String> childfilenames)
			throws IOException, PartiallyDeletedChildrenException {
		NavigableSet<String> result = new TreeSet<>();
		for (String entryname : getDirectoryEntryNames(path)) {
			if (!childfilenames.contains(entryname)) {
				SakerPath childpath = path.resolve(entryname);
				try {
					deleteRecursively(childpath);
				} catch (IOException e) {
					throw new PartiallyDeletedChildrenException(e, childpath, result);
				}
				result.add(entryname);
			}
		}
		return result;
	}

	/**
	 * Deletes the file at the given path if the type of it is not the specified one.
	 * <p>
	 * This method reads the attributes at the given path, and deletes the file recursively if it doesn't match the
	 * expected.
	 * <p>
	 * The attribute reading follows symbolic links, the deletion doesn't.
	 * <p>
	 * The file type argument should be one of the file type constants in {@link FileEntry}.
	 * 
	 * @param path
	 *            The path to the file.
	 * @param filetype
	 *            The expected file type.
	 * @return The current file type at the path. Either same as the argument, or {@link FileEntry#TYPE_NULL} to signal
	 *             that it was deleted.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default int deleteRecursivelyIfNotFileType(SakerPath path, int filetype) throws IOException {
		try {
			FileEntry attrs = getFileAttributes(path);
			if (attrs.getType() == filetype) {
				return filetype;
			}
		} catch (IOException e) {
			//file not found, we are okay
			return FileEntry.TYPE_NULL;
		}
		deleteRecursively(path);
		return FileEntry.TYPE_NULL;
	}

	/**
	 * Gets the names of the files present in the directory denoted by the specified path.
	 * <p>
	 * This method doesn't follow symbolic links.
	 * 
	 * @param path
	 *            The path to the directory.
	 * @return A set of file names which are present in the given directory. The returned set is ordered by natural
	 *             order.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see #getDirectoryEntries(SakerPath)
	 */
	@RMIWrap(RMITreeSetStringElementWrapper.class)
	@RMIExceptionRethrow(RemoteIOException.class)
	public default NavigableSet<String> getDirectoryEntryNames(SakerPath path) throws IOException {
		//clone to avoid keeping references to the attributes
		return ImmutableUtils.makeImmutableNavigableSet(getDirectoryEntries(path).navigableKeySet());
	}

	/**
	 * Gets the names of subdirectories in the directory denoted by the specified path.
	 * <p>
	 * This method follows symbolic links when determining if an entry is considered a directory.
	 * 
	 * @param path
	 *            The path to the directory.
	 * @return A set of directory names which are present in the given directory. The returned set is ordered by natural
	 *             order.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see #getDirectoryEntries(SakerPath)
	 */
	@RMIWrap(RMITreeSetStringElementWrapper.class)
	@RMIExceptionRethrow(RemoteIOException.class)
	public default NavigableSet<String> getSubDirectoryNames(SakerPath path) throws IOException {
		NavigableSet<String> result = new TreeSet<>();
		for (Entry<String, ? extends FileEntry> entry : getDirectoryEntries(path).entrySet()) {
			if (entry.getValue().isDirectory()) {
				result.add(entry.getKey());
			}
		}
		return result;
	}

	/**
	 * Writes the contents of the file at the given path to the specified output stream.
	 * 
	 * @param path
	 *            The path to the file.
	 * @param out
	 *            The output to write the file contents to.
	 * @param openoptions
	 *            The opening options for the input.
	 * @return The amount of bytes written to the output stream.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see StandardOpenOption
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default long writeTo(SakerPath path, ByteSink out,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) OpenOption... openoptions) throws IOException {
		try (ByteSource is = openInput(path, openoptions)) {
			return is.writeTo(out);
		}
	}

	/**
	 * Gets all of the byte contents of the file at the specified path.
	 * 
	 * @param path
	 *            The path to the file.
	 * @param openoptions
	 *            The opening options for the input.
	 * @return The byte contents of the file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see StandardOpenOption
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default ByteArrayRegion getAllBytes(SakerPath path,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) OpenOption... openoptions) throws IOException {
		try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
			writeTo(path, baos, openoptions);
			return baos.toByteArrayRegion();
		}
	}

	/**
	 * Writes the specified bytes to the file at the given path.
	 * 
	 * @param path
	 *            The output file path.
	 * @param data
	 *            The bytes to write to the file.
	 * @param openoptions
	 *            The opening options for the output.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see StandardOpenOption
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default void setFileBytes(SakerPath path, ByteArrayRegion data,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) OpenOption... openoptions) throws IOException {
		try (ByteSink os = openOutput(path, openoptions)) {
			os.write(data);
		}
	}

	/**
	 * Writes the contents from the given input stream to the specified output file path.
	 * 
	 * @param is
	 *            The input to get the bytes from.
	 * @param path
	 *            The output file path.
	 * @param openoptions
	 *            The opening options for the output.
	 * @return The amount of bytes written to the file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see StandardOpenOption
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default long writeToFile(ByteSource is, SakerPath path,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) OpenOption... openoptions) throws IOException {
		try (ByteSink os = openOutput(path, openoptions)) {
			return os.readFrom(is);
		}
	}

	/**
	 * Ensures a write request for the given path and opens an output stream to it.
	 * <p>
	 * The method works the same way as if {@link #ensureWriteRequest(SakerPath, int, int)} and
	 * {@link #openOutput(SakerPath, OpenOption...)} are called subsequently. This corresponds to the default
	 * implementation of:
	 * 
	 * <pre>
	 * ensureWriteRequest(path, FileEntry.TYPE_FILE, operationflag);
	 * return openOutput(path, openoptions);
	 * </pre>
	 * 
	 * @param path
	 *            The output file path.
	 * @param operationflag
	 *            The operation flag to take into account. Acceptable flags:
	 *            {@link #OPERATION_FLAG_NO_RECURSIVE_DIRECTORY_DELETE},
	 *            {@link #OPERATION_FLAG_DELETE_INTERMEDIATE_FILES}.
	 * @param openoptions
	 *            The opening options for the output.
	 * @return An opened output stream.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @since saker.build 0.8.15
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default ByteSink ensureWriteOpenOutput(SakerPath path, int operationflag,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) OpenOption... openoptions)
			throws IOException, NullPointerException {
		ensureWriteRequest(path, FileEntry.TYPE_FILE, operationflag);
		return openOutput(path, openoptions);
	}

	/**
	 * Creates a hash using the specified algorithm based on the contents of the file at the specified path.
	 * <p>
	 * This method will read the contents of the file at the specified path and use the appropriate
	 * {@link MessageDigest} to create a hash of its contents.
	 * <p>
	 * When using RMI, it is preferable to call this method instead of opening the stream on client side and reading the
	 * contents, as it can avoid transferring the whole content of the file over the network.
	 * 
	 * @param path
	 *            The path to the file.
	 * @param algorithm
	 *            The algorithm to use for hashing.
	 * @param openoptions
	 *            The opening options to use when opening the file.
	 * @return The computed hash.
	 * @throws NoSuchAlgorithmException
	 *             If the hashing algorithm not found.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see #openInput(SakerPath, OpenOption...)
	 * @see MessageDigest#getInstance(String)
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default FileHashResult hash(SakerPath path, String algorithm,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) OpenOption... openoptions)
			throws NoSuchAlgorithmException, IOException {
		MessageDigest digest = MessageDigest.getInstance(algorithm);
		long count = writeTo(path, StreamUtils.toByteSink(digest), openoptions);
		return new FileHashResult(count, digest.digest());
	}

	/**
	 * Adds a file event listener for the specified directory.
	 * <p>
	 * If the specified directory doesn't exist, the provider can either fail or succeed for installing the listener.
	 * <p>
	 * Callers should keep a strong reference to the returned token, and call {@link ListenerToken#removeListener()}
	 * when the listener is no longer needed. Removing all references to the returned token is not sufficient for
	 * removing the listener from the provider.
	 * <p>
	 * Listeners might receive events before this method finishes.
	 * <p>
	 * The listener will receive file event changes with file names which are a children of the specified directory.
	 * 
	 * @param directory
	 *            The directory to install the listener to.
	 * @param listener
	 *            The listener to install.
	 * @return The token for the installed listener.
	 * @throws IOException
	 *             If the provider failed to install the listener.
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public FileEventListener.ListenerToken addFileEventListener(SakerPath directory, FileEventListener listener)
			throws IOException;

	/**
	 * Removes the file listeners in a bulk operation.
	 * <p>
	 * To remove a single file listener call {@link ListenerToken#removeListener()}.
	 * <p>
	 * The file listeners might still receive some events after this call, as the events can occur out of order.
	 * Listeners should handle that scenario gracefully.
	 * <p>
	 * The method will silently ignore any <code>null</code> elements in the argument.
	 * 
	 * @param listeners
	 *            The file listeners to remove.
	 * @throws IllegalArgumentException
	 *             If any of the argument listener tokens were not issued by this file provider.
	 */
	public default void removeFileEventListeners(
			@RMIWrap(RMIArrayListRemoteElementWrapper.class) Iterable<? extends FileEventListener.ListenerToken> listeners)
			throws IllegalArgumentException {
		for (FileEventListener.ListenerToken lt : listeners) {
			if (lt == null) {
				continue;
			}
			lt.removeListener();
		}
	}

	//exclude from the API from now, as it is generally only for internal use.
	@ExcludeApi
	@RMIExceptionRethrow(RemoteIOException.class)
	public default SakerFileLock createLockFile(SakerPath path) throws IOException {
		throw new IOException("Unsupported.");
	}

	/**
	 * Gets the file provider <code>this</code> file provider wraps.
	 * <p>
	 * Wrapping file providers can be useful to extend the wrapped file providers functionality.
	 * <p>
	 * If this method returns <code>null</code>, then {@link #getProviderKey()} should return an instance of
	 * {@link RootFileProviderKey}.
	 * <p>
	 * If this method returns non-<code>null</code>, then the file provider implementation must override and provide an
	 * implementation {@link #resolveWrappedPath(SakerPath)}.
	 * 
	 * @return The wrapped file provider or <code>null</code> if this file provider is a root provider.
	 */
	@RMICacheResult
	public default SakerFileProvider getWrappedProvider() {
		return null;
	}

	/**
	 * Resolves the parameter path in a way that the result can be used to access the same files using the file provider
	 * returned from {@link #getWrappedProvider()}.
	 * <p>
	 * This method will throw an {@link UnsupportedOperationException} if and only if <code>this</code> file provider is
	 * root file provider.
	 * <p>
	 * The returned path will access the same file using the {@linkplain #getWrappedProvider() wrapped file provider} as
	 * this file provider would with the argument path.
	 * 
	 * @param path
	 *            The path to resolve.
	 * @return The resolved path in the domain of the wrapped file provider.
	 * @throws UnsupportedOperationException
	 *             If this is a root file provider. I.e. {@link #getWrappedProvider()} returns <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the path doesn't have a root that this file provider handles.
	 */
	public default SakerPath resolveWrappedPath(SakerPath path)
			throws UnsupportedOperationException, IllegalArgumentException {
		throw new UnsupportedOperationException();
	}

}
