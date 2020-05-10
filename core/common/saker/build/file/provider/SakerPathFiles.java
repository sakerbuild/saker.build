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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentNavigableMap;

import saker.apiextract.api.PublicApi;
import saker.build.exception.InvalidPathFormatException;
import saker.build.exception.MissingConfigurationException;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.FileHandle;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.SynchronizingContentUpdater;
import saker.build.file.content.ContentDatabase;
import saker.build.file.content.ContentDatabase.DeferredSynchronizer;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.runtime.execution.ExecutionDirectoryContext;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.task.TaskContext;
import saker.build.task.TaskContextReference;
import saker.build.task.TaskDirectoryContext;
import saker.build.task.TaskDirectoryPathContext;
import saker.build.task.TaskExecutionUtilities;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.TransformingNavigableMap;
import saker.build.thirdparty.saker.util.TransformingSortedMap;
import saker.build.thirdparty.saker.util.TransformingSortedSet;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.thread.ParallelExecutionFailedException;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;

/**
 * Utility class holding methods for manipulating, querying and working with files.
 * <p>
 * The methods which provide utility functions for paths and sorted sets/maps containing them are implemented
 * efficiently, meaning they will rarely require more time complexity than O(lookup) where lookup is the time it takes
 * to look up an element in a sorted set/map. Unless noted otherwise, these functions expect the parameter sets/maps to
 * be sorted by the natural order.
 */
@PublicApi
public class SakerPathFiles {
	//TODO use TaskDirectoryContext instead of TaskContext
	private SakerPathFiles() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Converts the argument path to a relative string if possible.
	 * <p>
	 * If the argument is already relative, it will be converted to string directly.
	 * <p>
	 * If it is absolute, the method will try to convert it to a relative one by checking if it is under the execution
	 * working directory. The resulting path string will be {@linkplain SakerPath#isForwardRelative() forward relative}.
	 * <p>
	 * If this method is called from a build execution thread, the configured
	 * {@linkplain ExecutionPathConfiguration#getWorkingDirectory() execution working directory} will be used to convert
	 * the path to a relative string.
	 * <p>
	 * If the path is not a subpath of the execution working directory, it will be stringized as an absolute path.
	 * <p>
	 * If this method is not called from a build execution, it is equivalent to <code>path.toString()</code>.
	 * <p>
	 * This method is useful when displaying paths to the user, as relative paths are often more meaningful.
	 * 
	 * @param path
	 *            The path to convert to relative string.
	 * @return The string representation of the path. May be absolute, if the relativization failed.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static String toRelativeString(SakerPath path) throws NullPointerException {
		Objects.requireNonNull(path, "path");
		if (path.isRelative()) {
			return path.toString();
		}
		TaskContext tc = TaskContextReference.current();
		if (tc != null) {
			SakerPath workingdir = tc.getExecutionContext().getPathConfiguration().getWorkingDirectory();
			if (path.startsWith(workingdir)) {
				return path.subPath(workingdir.getNameCount()).toString();
			}
		}
		return path.toString();
	}

	/**
	 * Validation method for ensuring that the specified file provider is a root file provider.
	 * 
	 * @param fileprovider
	 *            The file provider.
	 * @return The file provider argument.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the file provider is not a root file provider.
	 */
	public static SakerFileProvider requireRootFileProvider(SakerFileProvider fileprovider)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(fileprovider, "file provider");
		FileProviderKey result = fileprovider.getProviderKey();
		if (!(result instanceof RootFileProviderKey)) {
			throw new IllegalArgumentException("File provider is not a root provider. (" + fileprovider + ")");
		}
		return fileprovider;
	}

	/**
	 * Validation method for ensuring that the specified file provider is a root file provider.
	 * 
	 * @param fileprovider
	 *            The file provider.
	 * @return The {@linkplain SakerFileProvider#getProviderKey() file provider key} of the argument.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the file provider is not a root file provider.
	 */
	public static RootFileProviderKey requireRootFileProviderKey(SakerFileProvider fileprovider)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(fileprovider, "file provider");
		FileProviderKey result = fileprovider.getProviderKey();
		if (!(result instanceof RootFileProviderKey)) {
			throw new IllegalArgumentException("File provider is not a root provider. (" + fileprovider + ")");
		}
		return (RootFileProviderKey) result;
	}

	/**
	 * Validation method for ensuring that the specified path is absolute.
	 * 
	 * @param path
	 *            The path.
	 * @return The path argument.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static SakerPath requireAbsolutePath(SakerPath path)
			throws InvalidPathFormatException, NullPointerException {
		Objects.requireNonNull(path, "path");
		if (!path.isAbsolute()) {
			throw new InvalidPathFormatException("Path is not absolute: " + path);
		}
		return path;
	}

	/**
	 * Validation method for ensuring that the specified path is relative.
	 * 
	 * @param path
	 *            The path.
	 * @return The path argument.
	 * @throws InvalidPathFormatException
	 *             If the path is not relative.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static SakerPath requireRelativePath(SakerPath path)
			throws InvalidPathFormatException, NullPointerException {
		Objects.requireNonNull(path, "path");
		if (!path.isRelative()) {
			throw new InvalidPathFormatException("Path is not relative: " + path);
		}
		return path;
	}

	/**
	 * Validation method for ensuring that the specified path is absolute.
	 * 
	 * @param path
	 *            The path.
	 * @return The path argument.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static Path requireAbsolutePath(Path path) throws InvalidPathFormatException, NullPointerException {
		Objects.requireNonNull(path, "path");
		if (!path.isAbsolute()) {
			throw new InvalidPathFormatException("Path is not absolute: " + path);
		}
		return path;
	}

	/**
	 * Validation method for ensuring that the specified path is relative.
	 * 
	 * @param path
	 *            The path.
	 * @return The path argument.
	 * @throws InvalidPathFormatException
	 *             If the path is not relative.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static Path requireRelativePath(Path path) throws InvalidPathFormatException, NullPointerException {
		Objects.requireNonNull(path, "path");
		if (path.isAbsolute()) {
			throw new InvalidPathFormatException("Path is not relative: " + path);
		}
		return path;
	}

	/**
	 * Validation method for ensuring that the specified file is available through an absolute path.
	 * <p>
	 * A file has an absolute path if and only if it has every parent until one of the root directories for the
	 * execution, or is a root directory itself.
	 * 
	 * @param file
	 *            The file.
	 * @return The absolute path of the file.
	 * @throws InvalidPathFormatException
	 *             If the path for the file is not absolute.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see SakerFile#getSakerPath()
	 */
	public static SakerPath requireAbsolutePath(SakerFile file)
			throws InvalidPathFormatException, NullPointerException {
		Objects.requireNonNull(file, "file");
		SakerPath path = file.getSakerPath();
		if (!path.isAbsolute()) {
			throw new InvalidPathFormatException("File path is not absolute: " + path);
		}
		return path;
	}

	/**
	 * Validation method for ensuring that the specified file is available through a relative path.
	 * <p>
	 * A file has a relative path if and only if it has no parent, or none of its parents are a root directory of the
	 * execution.
	 * 
	 * @param file
	 *            The file.
	 * @return The absolute path of the file.
	 * @throws InvalidPathFormatException
	 *             If the path for the file is not relative.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see SakerFile#getSakerPath()
	 */
	public static SakerPath requireRelativePath(SakerFile file)
			throws InvalidPathFormatException, NullPointerException {
		Objects.requireNonNull(file, "file");
		SakerPath path = file.getSakerPath();
		if (!path.isRelative()) {
			throw new InvalidPathFormatException("File path is not relative: " + path);
		}
		return path;
	}

	/**
	 * Validation method for ensuring that the specified path has a valid file name.
	 * 
	 * @param path
	 *            The path.
	 * @return The file name of the path.
	 * @throws InvalidPathFormatException
	 *             If the path has no valid file name.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see SakerPath#getFileName()
	 */
	public static String requireFileName(SakerPath path) throws InvalidPathFormatException, NullPointerException {
		Objects.requireNonNull(path, "path");
		String fn = path.getFileName();
		if (fn == null) {
			throw new InvalidPathFormatException("Path contains no file name: " + path);
		}
		return fn;
	}

	/**
	 * Validation method for ensuring that a specified path has a non-<code>null</code>
	 * {@linkplain SakerPath#getParent() parent}.
	 * 
	 * @param path
	 *            The path.
	 * @return The parent of the path.
	 * @throws InvalidPathFormatException
	 *             If the path has not parent.
	 * @throws NullPointerException
	 *             If path is <code>null</code>.
	 */
	public static SakerPath requireParent(SakerPath path) throws InvalidPathFormatException, NullPointerException {
		Objects.requireNonNull(path, "path");
		SakerPath parent = path.getParent();
		if (parent == null) {
			throw new InvalidPathFormatException("Path has no parent: " + path);
		}
		return parent;
	}

	/**
	 * Validation method for ensuring that the task has a build directory configured for it.
	 * 
	 * @param taskdircontext
	 *            The task directory context.
	 * @return The build directory.
	 * @throws MissingConfigurationException
	 *             If the build directory is not available for the task.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see TaskDirectoryContext#getTaskBuildDirectory()
	 */
	public static SakerDirectory requireBuildDirectory(TaskDirectoryContext taskdircontext)
			throws MissingConfigurationException, NullPointerException {
		Objects.requireNonNull(taskdircontext, "task directory context");
		SakerDirectory bdir = taskdircontext.getTaskBuildDirectory();
		if (bdir == null) {
			throw new MissingConfigurationException("No task build directory specified.");
		}
		return bdir;
	}

	/**
	 * Validation method for ensuring that the task has a build directory path configured for it.
	 * 
	 * @param taskdircontext
	 *            The task directory path context.
	 * @return The build directory path.
	 * @throws MissingConfigurationException
	 *             If the build directory is not available for the task.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see TaskDirectoryPathContext#getTaskBuildDirectoryPath()
	 */
	public static SakerPath requireBuildDirectoryPath(TaskDirectoryPathContext taskdircontext)
			throws MissingConfigurationException, NullPointerException {
		Objects.requireNonNull(taskdircontext, "task directory path context");
		SakerPath bdir = taskdircontext.getTaskBuildDirectoryPath();
		if (bdir != null) {
			return bdir;
		}
		throw new MissingConfigurationException("No task build directory specified.");
	}

	/**
	 * Validation method for ensuring that the execution has a build directory configured for it.
	 * 
	 * @param executiondircontext
	 *            The execution directory context.
	 * @return The build directory.
	 * @throws MissingConfigurationException
	 *             If the build directory is not available for the execution.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see ExecutionDirectoryContext#getExecutionBuildDirectory()
	 */
	public static SakerDirectory requireBuildDirectory(ExecutionDirectoryContext executiondircontext)
			throws MissingConfigurationException, NullPointerException {
		SakerDirectory bdir = executiondircontext.getExecutionBuildDirectory();
		if (bdir == null) {
			throw new MissingConfigurationException("No execution build directory specified.");
		}
		return bdir;
	}

	/**
	 * Validation method for a file name according to the rules of {@link FileHandle#getName()}.
	 * <p>
	 * The name may not be <code>null</code>, empty, <code>"."</code> or <code>".."</code>, must not contain slash
	 * characters (<code>'/'</code>, <code>'\\'</code>), and must not contain the colon (<code>':'</code>) and semicolon
	 * (<code>';'</code>) characters.
	 * 
	 * @param name
	 *            The file name.
	 * @return The argument file name.
	 * @throws InvalidPathFormatException
	 *             If the file name is not valid.
	 * @throws NullPointerException
	 *             If the file name is <code>null</code>.
	 * @see FileHandle#getName()
	 */
	public static String requireValidFileName(String name) throws InvalidPathFormatException, NullPointerException {
		Objects.requireNonNull(name, "file name");
		if (name.isEmpty()) {
			throw new InvalidPathFormatException("Empty file name.");
		}
		//len is at least 1, as the name is not empty

		//probably unnecessary optimization of unrolling the first 2 loops, but there it is anyway
		int len = name.length();
		char c = name.charAt(0);
		if (SakerPath.isSlashCharacter(c) || c == ':' || c == ';') {
			throw new InvalidPathFormatException("Invalid file name: " + name);
		}

		int i = 1;
		if (c == '.') {
			if (len == 1) {
				throw new InvalidPathFormatException("Invalid file name: .");
			}
			//len is at least 2
			c = name.charAt(1);
			if (SakerPath.isSlashCharacter(c) || c == ':' || c == ';') {
				throw new InvalidPathFormatException("Invalid file name: " + name);
			}
			if (c == '.') {
				throw new InvalidPathFormatException("Invalid file name: ..");
			}
			i = 2;
		}
		for (; i < len; i++) {
			c = name.charAt(i);
			if (SakerPath.isSlashCharacter(c) || c == ':' || c == ';') {
				throw new InvalidPathFormatException("Invalid file name: " + name);
			}
		}
		return name;
	}

	/**
	 * Gets the path key for a local path.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * LocalFileProvider.getInstance().getPathKey(path)
	 * </pre>
	 * 
	 * @param path
	 *            The path to get the path key for.
	 * @return The created path key.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute, or not associated with the default file system.
	 */
	public static ProviderHolderPathKey getPathKey(Path path) throws NullPointerException, InvalidPathFormatException {
		return LocalFileProvider.getInstance().getPathKey(path);
	}

	/**
	 * Gets the path key for a file provider and corresponding path.
	 * 
	 * @param fileprovider
	 *            The file provider.
	 * @param path
	 *            The path.
	 * @return The created path key.
	 * @throws NullPointerException
	 *             If any argument is <code>null</code>.
	 */
	public static ProviderHolderPathKey getPathKey(SakerFileProvider fileprovider, SakerPath path)
			throws NullPointerException {
		Objects.requireNonNull(fileprovider, "file provider");
		Objects.requireNonNull(path, "path");
		for (SakerFileProvider w; (w = fileprovider.getWrappedProvider()) != null;) {
			path = fileprovider.resolveWrappedPath(path);
			fileprovider = w;
		}

		return new SimpleProviderHolderPathKey(fileprovider, path);
	}

	/**
	 * Gets the root file provider key of a file provider by unwrapping it according the wrapping rules of file
	 * providers.
	 * 
	 * @param provider
	 *            The file provider.
	 * @return The root file provider key of the last file provider.
	 * @throws NullPointerException
	 *             If the file provider is <code>null</code>.
	 * @see SakerFileProvider#getWrappedProvider()
	 */
	public static RootFileProviderKey getRootFileProviderKey(SakerFileProvider provider) throws NullPointerException {
		while (true) {
			SakerFileProvider wrapped = provider.getWrappedProvider();
			if (wrapped == null) {
				return (RootFileProviderKey) provider.getProviderKey();
			}
			provider = wrapped;
		}
	}

	/**
	 * Resolves a file or directory at the given path.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#resolveAtPath(SakerPath)} instead.
	 * <p>
	 * If the path is relative, it will be resolved against the current task working directory.
	 * <p>
	 * If it is absolute, the execution root directories will be used as a base of resolution.
	 * 
	 * @param taskcontext
	 *            The task context.
	 * @param path
	 *            The path to resolve.
	 * @return The found file at the given path or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @see SakerDirectory#get(String)
	 */
	public static SakerFile resolveAtPath(TaskContext taskcontext, SakerPath path) throws NullPointerException {
		Objects.requireNonNull(taskcontext, "task context");
		return resolveAtPath(taskcontext.getExecutionContext(), taskcontext.getTaskWorkingDirectory(), path);
	}

	/**
	 * Resolves a file or directory using the given absolute path.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#resolveAtAbsolutePath(SakerPath)} instead.
	 * 
	 * @param executiondirectorycontext
	 *            The execution directory context.
	 * @param path
	 *            The absolute path.
	 * @return The resolved file or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see #resolve(SakerDirectory, String)
	 * @see ExecutionDirectoryContext#getRootDirectories()
	 */
	public static SakerFile resolveAtAbsolutePath(ExecutionDirectoryContext executiondirectorycontext, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		requireAbsolutePath(path);
		Objects.requireNonNull(executiondirectorycontext, "execution directory context");
		SakerDirectory dir = executiondirectorycontext.getRootDirectories().get(path.getRoot());
		if (dir != null) {
			return resolveAtRelativePathNamesImpl(dir, path.nameIterator());
		}
		return null;
	}

	/**
	 * Same as {@link #resolveAtPath(TaskContext, SakerPath)}, but uses the specified base directory to resolve relative
	 * paths.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#resolveAtPath(SakerDirectory, SakerPath)}
	 * instead.
	 * 
	 * @param executiondirectorycontext
	 *            The execution directory context.
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The path to resolve.
	 * @return The found file at the given path or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If path is <code>null</code>, or path is relative and base directory is <code>null</code>, or path is
	 *             absolute and execution directory context is <code>null</code>.
	 */
	public static SakerFile resolveAtPath(ExecutionDirectoryContext executiondirectorycontext, SakerDirectory basedir,
			SakerPath path) throws NullPointerException {
		Objects.requireNonNull(path, "path");
		if (path.isRelative()) {
			return resolveAtRelativePath(basedir, path);
		}
		return resolveAtAbsolutePath(executiondirectorycontext, path);
	}

	/**
	 * Resolves a file or directory using the given relative path against a base directory.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#resolveAtRelativePath(SakerDirectory, SakerPath)}
	 * instead.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The relative path.
	 * @return The resolved file or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not relative.
	 * @see #resolve(SakerDirectory, String)
	 */
	public static SakerFile resolveAtRelativePath(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(basedir, "base directory");
		requireRelativePath(path);

		Iterator<String> it = path.nameIterator();
		return resolveAtRelativePathNamesImpl(basedir, it);
	}

	/**
	 * Resolves a file or directory with the given path names against a base directory.
	 * <p>
	 * It is recommended that users call
	 * {@link TaskExecutionUtilities#resolveAtRelativePathNames(SakerDirectory, Iterable)} instead.
	 * <p>
	 * The path names are interpreted as if they were relative compared to the base directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param names
	 *            The relative path names.
	 * @return The resolved file or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If any of the arguments or path names are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If any of the path name has invalid format.
	 * @see #resolveAtRelativePath(SakerDirectory, SakerPath)
	 * @see #requireValidFileName(String)
	 */
	public static SakerFile resolveAtRelativePathNames(SakerDirectory basedir, Iterable<? extends String> names)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(basedir, "base directory");
		Iterator<? extends String> it = names.iterator();
		return resolveAtRelativePathNamesImpl(basedir, it);
	}

	private static SakerFile resolveAtRelativePathNamesImpl(SakerDirectory basedir, Iterator<? extends String> it)
			throws NullPointerException {
		if (!it.hasNext()) {
			return basedir;
		}
		SakerFile result = resolve(basedir, it.next());
		while (result != null && it.hasNext()) {
			if (!(result instanceof SakerDirectory)) {
				return null;
			}
			String fn = it.next();
			result = resolve((SakerDirectory) result, fn);
		}
		return result;
	}

	/**
	 * Resolves a file but not a directory at the given path.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#resolveFileAtPath(SakerPath)} instead.
	 * <p>
	 * If the path is relative, it will be resolved against the current task working directory.
	 * <p>
	 * If it is absolute, the execution root directories will be used as a base of resolution.
	 * 
	 * @param taskcontext
	 *            The task context.
	 * @param path
	 *            The path to resolve.
	 * @return The found file at the given path or <code>null</code> if not found or it's a directory.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @see SakerDirectory#get(String)
	 */
	public static SakerFile resolveFileAtPath(TaskContext taskcontext, SakerPath path) throws NullPointerException {
		Objects.requireNonNull(taskcontext, "task context");
		return resolveFileAtPath(taskcontext.getExecutionContext(), taskcontext.getTaskWorkingDirectory(), path);
	}

	/**
	 * Resolves a file but not a directory using the given absolute path.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#resolveFileAtAbsolutePath(SakerPath)} instead.
	 * 
	 * @param executiondirectorycontext
	 *            The execution directory context.
	 * @param path
	 *            The absolute path.
	 * @return The resolved file or <code>null</code> if not found or it's a directory.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see #resolve(SakerDirectory, String)
	 * @see ExecutionDirectoryContext#getRootDirectories()
	 */
	public static SakerFile resolveFileAtAbsolutePath(ExecutionDirectoryContext executiondirectorycontext,
			SakerPath path) throws NullPointerException, InvalidPathFormatException {
		requireAbsolutePath(path);
		Objects.requireNonNull(executiondirectorycontext, "execution directory context");
		SakerDirectory dir = executiondirectorycontext.getRootDirectories().get(path.getRoot());
		if (dir != null) {
			return resolveFileAtRelativePathNamesImpl(dir, path.nameIterator());
		}
		return null;
	}

	/**
	 * Same as {@link #resolveFileAtPath(TaskContext, SakerPath)}, but uses the specified base directory to resolve
	 * relative paths.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#resolveFileAtPath(SakerDirectory, SakerPath)}
	 * instead.
	 * 
	 * @param executiondirectorycontext
	 *            The execution directory context.
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The path to resolve.
	 * @return The found file at the given path or <code>null</code> if not found or it's a directory.
	 * @throws NullPointerException
	 *             If path is <code>null</code>, or path is relative and base directory is <code>null</code>, or path is
	 *             absolute and execution directory context is <code>null</code>.
	 */
	public static SakerFile resolveFileAtPath(ExecutionDirectoryContext executiondirectorycontext,
			SakerDirectory basedir, SakerPath path) throws NullPointerException {
		Objects.requireNonNull(path, "path");
		if (path.isRelative()) {
			return resolveFileAtRelativePath(basedir, path);
		}
		return resolveFileAtAbsolutePath(executiondirectorycontext, path);
	}

	/**
	 * Resolves a file but not a directory using the given relative path against a base directory.
	 * <p>
	 * It is recommended that users call
	 * {@link TaskExecutionUtilities#resolveFileAtRelativePath(SakerDirectory, SakerPath)} instead.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The relative path.
	 * @return The resolved file or <code>null</code> if not found or it's a directory.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not relative.
	 * @see #resolve(SakerDirectory, String)
	 */
	public static SakerFile resolveFileAtRelativePath(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(basedir, "base directory");
		requireRelativePath(path);

		Iterator<String> it = path.nameIterator();
		return resolveFileAtRelativePathNamesImpl(basedir, it);
	}

	/**
	 * Resolves a file but not a directory with the given path names against a base directory.
	 * <p>
	 * It is recommended that users call
	 * {@link TaskExecutionUtilities#resolveFileAtRelativePathNames(SakerDirectory, Iterable)} instead.
	 * <p>
	 * The path names are interpreted as if they were relative compared to the base directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param names
	 *            The relative path names.
	 * @return The resolved file or <code>null</code> if not found or it's a directory.
	 * @throws NullPointerException
	 *             If any of the arguments or path names are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If any of the path name has invalid format.
	 * @see #resolveAtRelativePath(SakerDirectory, SakerPath)
	 * @see #requireValidFileName(String)
	 */
	public static SakerFile resolveFileAtRelativePathNames(SakerDirectory basedir, Iterable<? extends String> names)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(basedir, "base directory");
		Iterator<? extends String> it = names.iterator();
		return resolveFileAtRelativePathNamesImpl(basedir, it);
	}

	private static SakerFile resolveFileAtRelativePathNamesImpl(SakerDirectory basedir, Iterator<? extends String> it)
			throws NullPointerException {
		SakerFile result = resolveAtRelativePathNamesImpl(basedir, it);
		if (result instanceof SakerDirectory) {
			return null;
		}
		return result;
	}

	/**
	 * Resolves a directory at a given path.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#resolveDirectoryAtPath(SakerPath)} instead.
	 * <p>
	 * If the path is relative, it will be resolved against the current task working directory.
	 * <p>
	 * If it is absolute, the execution root directories will be used as a base of resolution.
	 * 
	 * @param taskcontext
	 *            The task context.
	 * @param path
	 *            The path to resolve.
	 * @return The found directory at the given path or <code>null</code> if a directory was not found at the path.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @see SakerDirectory#getDirectory(String)
	 */
	public static SakerDirectory resolveDirectoryAtPath(TaskContext taskcontext, SakerPath path)
			throws NullPointerException {
		Objects.requireNonNull(taskcontext, "task context");
		return resolveDirectoryAtPath(taskcontext.getExecutionContext(), taskcontext.getTaskWorkingDirectory(), path);
	}

	/**
	 * Resolves a directory using the given absolute path.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#resolveDirectoryAtAbsolutePath(SakerPath)}
	 * instead.
	 * 
	 * @param executiondirectorycontext
	 *            The execution directory context.
	 * @param path
	 *            The absolute path.
	 * @return The resolved directory or <code>null</code> if a directory was not found at the path.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see #resolveDirectory(SakerDirectory, String)
	 * @see ExecutionDirectoryContext#getRootDirectories()
	 */
	public static SakerDirectory resolveDirectoryAtAbsolutePath(ExecutionDirectoryContext executiondirectorycontext,
			SakerPath path) throws NullPointerException, InvalidPathFormatException {
		requireAbsolutePath(path);
		Objects.requireNonNull(executiondirectorycontext, "execution directory context");
		SakerDirectory dir = executiondirectorycontext.getRootDirectories().get(path.getRoot());
		if (dir != null) {
			return resolveDirectoryAtRelativePathNamesImpl(dir, path.nameIterator());
		}
		return null;
	}

	/**
	 * Same as {@link #resolveDirectoryAtPath(TaskContext, SakerPath)}, but uses the specified base directory to resolve
	 * relative paths.
	 * <p>
	 * It is recommended that users call
	 * {@link TaskExecutionUtilities#resolveDirectoryAtPath(SakerDirectory, SakerPath)} instead.
	 * 
	 * @param executiondirectorycontext
	 *            The execution directory context.
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The path to resolve.
	 * @return The found directory at the given path or <code>null</code> if a directory was not found at the path.
	 * @throws NullPointerException
	 *             If path is <code>null</code>, or path is relative and base directory is <code>null</code>, or path is
	 *             absolute and execution directory context is <code>null</code>.
	 */
	public static SakerDirectory resolveDirectoryAtPath(ExecutionDirectoryContext executiondirectorycontext,
			SakerDirectory basedir, SakerPath path) throws NullPointerException {
		Objects.requireNonNull(path, "path");
		if (path.isRelative()) {
			return resolveDirectoryAtRelativePath(basedir, path);
		}
		return resolveDirectoryAtAbsolutePath(executiondirectorycontext, path);
	}

	/**
	 * Resolves a directory using the given relative path against a base directory.
	 * <p>
	 * It is recommended that users call
	 * {@link TaskExecutionUtilities#resolveDirectoryAtRelativePath(SakerDirectory, SakerPath)} instead.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The relative path.
	 * @return The resolved directory or <code>null</code> if a directory was not found at the path.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not relative.
	 * @see #resolveDirectory(SakerDirectory, String)
	 */
	public static SakerDirectory resolveDirectoryAtRelativePath(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(basedir, "base directory");
		requireRelativePath(path);

		Iterator<String> it = path.nameIterator();
		return resolveDirectoryAtRelativePathNamesImpl(basedir, it);
	}

	/**
	 * Resolves a directory with the given path names against a base directory.
	 * <p>
	 * It is recommended that users call
	 * {@link TaskExecutionUtilities#resolveDirectoryAtRelativePathNames(SakerDirectory, Iterable)} instead.
	 * <p>
	 * The path names are interpreted as if they were relative compared to the base directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param names
	 *            The relative path names.
	 * @return The resolved directory or <code>null</code> if a directory was not found at the path.
	 * @throws NullPointerException
	 *             If any of the arguments or path names are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If any of the path name has invalid format.
	 * @see #resolveDirectoryAtRelativePath(SakerDirectory, SakerPath)
	 * @see #requireValidFileName(String)
	 */
	public static SakerDirectory resolveDirectoryAtRelativePathNames(SakerDirectory basedir,
			Iterable<? extends String> names) throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(basedir, "base directory");
		Iterator<? extends String> it = names.iterator();
		return resolveDirectoryAtRelativePathNamesImpl(basedir, it);
	}

	private static SakerDirectory resolveDirectoryAtRelativePathNamesImpl(SakerDirectory basedir,
			Iterator<? extends String> it) throws NullPointerException {
		if (!it.hasNext()) {
			return basedir;
		}
		SakerFile result = resolve(basedir, it.next());
		while (result != null && it.hasNext()) {
			if (!(result instanceof SakerDirectory)) {
				return null;
			}
			result = resolve((SakerDirectory) result, it.next());
		}
		if (result != null && result instanceof SakerDirectory) {
			return (SakerDirectory) result;
		}
		return null;
	}

	/**
	 * Resolves a directory at a given path, creating it if necessary, overwriting already existing files.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#resolveDirectoryAtPathCreate(SakerPath)} instead.
	 * <p>
	 * If the path is relative, it will be resolved against the current task working directory.
	 * <p>
	 * If it is absolute, the execution root directories will be used as a base of resolution.
	 * 
	 * @param taskcontext
	 *            The task context.
	 * @param path
	 *            The path to resolve.
	 * @return The directory at the given path.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @see SakerDirectory#getDirectoryCreate(String)
	 */
	public static SakerDirectory resolveDirectoryAtPathCreate(TaskContext taskcontext, SakerPath path)
			throws NullPointerException {
		Objects.requireNonNull(taskcontext, "task context");
		return resolveDirectoryAtPathCreate(taskcontext.getExecutionContext(), taskcontext.getTaskWorkingDirectory(),
				path);
	}

	/**
	 * Resolves a directory using the given absolute path, creating it if necessary, overwriting already existing files.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#resolveDirectoryAtAbsolutePathCreate(SakerPath)}
	 * instead.
	 * 
	 * @param executiondirectorycontext
	 *            The execution directory context.
	 * @param path
	 *            The absolute path.
	 * @return The directory at the given path.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see #resolveDirectoryCreate(SakerDirectory, String)
	 * @see ExecutionDirectoryContext#getRootDirectories()
	 */
	public static SakerDirectory resolveDirectoryAtAbsolutePathCreate(
			ExecutionDirectoryContext executiondirectorycontext, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		requireAbsolutePath(path);
		Objects.requireNonNull(executiondirectorycontext, "execution directory context");
		SakerDirectory dir = executiondirectorycontext.getRootDirectories().get(path.getRoot());
		if (dir != null) {
			return resolveDirectoryAtRelativePathNamesCreateImpl(dir, path.nameIterator());
		}
		return null;
	}

	/**
	 * Same as {@link #resolveDirectoryAtPathCreate(TaskContext, SakerPath)}, but uses the specified base directory to
	 * resolve relative paths.
	 * <p>
	 * It is recommended that users call
	 * {@link TaskExecutionUtilities#resolveDirectoryAtPathCreate(SakerDirectory, SakerPath)} instead.
	 * 
	 * @param executiondirectorycontext
	 *            The execution directory context.
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The path to resolve.
	 * @return The directory at the given path.
	 * @throws NullPointerException
	 *             If path is <code>null</code>, or path is relative and base directory is <code>null</code>, or path is
	 *             absolute and execution directory context is <code>null</code>.
	 */
	public static SakerDirectory resolveDirectoryAtPathCreate(ExecutionDirectoryContext executiondirectorycontext,
			SakerDirectory basedir, SakerPath path) throws NullPointerException {
		Objects.requireNonNull(path, "path");
		if (path.isRelative()) {
			return resolveDirectoryAtRelativePathCreate(basedir, path);
		}
		return resolveDirectoryAtAbsolutePathCreate(executiondirectorycontext, path);
	}

	/**
	 * Resolves a directory using the given relative path against a base directory, creating it if necessary,
	 * overwriting already existing files.
	 * <p>
	 * It is recommended that users call
	 * {@link TaskExecutionUtilities#resolveDirectoryAtRelativePathCreate(SakerDirectory, SakerPath)} instead.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The relative path.
	 * @return The directory at the given path.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not relative.
	 */
	public static SakerDirectory resolveDirectoryAtRelativePathCreate(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(basedir, "base directory");
		requireRelativePath(path);
		Iterator<String> it = path.nameIterator();
		return resolveDirectoryAtRelativePathNamesCreateImpl(basedir, it);
	}

	/**
	 * Resolves a directory with the given path names against a base directory, creating it if necessary, overwriting
	 * already existing files.
	 * <p>
	 * It is recommended that users call
	 * {@link TaskExecutionUtilities#resolveDirectoryAtRelativePathNamesCreate(SakerDirectory, Iterable)} instead.
	 * <p>
	 * The path names are interpreted as if they were relative compared to the base directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param names
	 *            The relative path names.
	 * @return The directory at the given path.
	 * @throws NullPointerException
	 *             If any of the arguments or path names are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If any of the path name has invalid format.
	 * @see #resolveDirectoryAtRelativePathCreate(SakerDirectory, SakerPath)
	 * @see #requireValidFileName(String)
	 */
	public static SakerDirectory resolveDirectoryAtRelativePathNamesCreate(SakerDirectory basedir,
			Iterable<? extends String> names) throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(basedir, "base directory");
		Iterator<? extends String> it = names.iterator();
		return resolveDirectoryAtRelativePathNamesCreateImpl(basedir, it);
	}

	private static SakerDirectory resolveDirectoryAtRelativePathNamesCreateImpl(SakerDirectory basedir,
			Iterator<? extends String> it) throws NullPointerException {
		if (!it.hasNext()) {
			return basedir;
		}
		while (it.hasNext()) {
			String p = it.next();
			basedir = resolveDirectoryCreate(basedir, p);
		}
		return basedir;
	}

	/**
	 * Resolves a directory at a given path, creating it if possible.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#resolveDirectoryAtPathCreateIfAbsent(SakerPath)}
	 * instead.
	 * <p>
	 * This method doesn't overwrite the already existing file at the path if it's not a directory.
	 * <p>
	 * If the path is relative, it will be resolved against the current task working directory.
	 * <p>
	 * If it is absolute, the execution root directories will be used as a base of resolution.
	 * 
	 * @param taskcontext
	 *            The task context.
	 * @param path
	 *            The path to resolve.
	 * @return The directory at the given path or <code>null</code> if it cannot be created.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @see {@link SakerDirectory#getDirectoryCreateIfAbsent(String)}
	 */
	public static SakerDirectory resolveDirectoryAtPathCreateIfAbsent(TaskContext taskcontext, SakerPath path)
			throws NullPointerException {
		Objects.requireNonNull(taskcontext, "task context");
		return resolveDirectoryAtPathCreateIfAbsent(taskcontext.getExecutionContext(),
				taskcontext.getTaskWorkingDirectory(), path);
	}

	/**
	 * Resolves a directory using the given absolute path, creating it if possible.
	 * <p>
	 * It is recommended that users call
	 * {@link TaskExecutionUtilities#resolveDirectoryAtAbsolutePathCreateIfAbsent(SakerPath)} instead.
	 * <p>
	 * This method doesn't overwrite the already existing file at the path if it's not a directory.
	 * 
	 * @param executiondirectorycontext
	 *            The execution directory context.
	 * @param path
	 *            The absolute path.
	 * @return The directory at the given path or <code>null</code> if it cannot be created.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see #resolveDirectoryCreateIfAbsent(SakerDirectory, String)
	 * @see ExecutionDirectoryContext#getRootDirectories()
	 */
	public static SakerDirectory resolveDirectoryAtAbsolutePathCreateIfAbsent(
			ExecutionDirectoryContext executiondirectorycontext, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		requireAbsolutePath(path);
		Objects.requireNonNull(executiondirectorycontext, "execution directory context");
		SakerDirectory dir = executiondirectorycontext.getRootDirectories().get(path.getRoot());
		if (dir != null) {
			return resolveDirectoryAtRelativePathNamesCreateIfAbsentImpl(dir, path.nameIterator());
		}
		return null;
	}

	/**
	 * Same as {@link #resolveDirectoryAtPathCreateIfAbsent(TaskContext, SakerPath)}, but uses the specified base
	 * directory to resolve relative paths.
	 * <p>
	 * It is recommended that users call
	 * {@link TaskExecutionUtilities#resolveDirectoryAtPathCreateIfAbsent(SakerDirectory, SakerPath)} instead.
	 * 
	 * @param executiondirectorycontext
	 *            The execution directory context.
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The path to resolve.
	 * @return The directory at the given path or <code>null</code> if it cannot be created.
	 * @throws NullPointerException
	 *             If path is <code>null</code>, or path is relative and base directory is <code>null</code>, or path is
	 *             absolute and execution directory context is <code>null</code>.
	 */
	public static SakerDirectory resolveDirectoryAtPathCreateIfAbsent(
			ExecutionDirectoryContext executiondirectorycontext, SakerDirectory basedir, SakerPath path)
			throws NullPointerException {
		Objects.requireNonNull(path, "path");
		if (path.isRelative()) {
			return resolveDirectoryAtRelativePathCreateIfAbsent(basedir, path);
		}
		return resolveDirectoryAtAbsolutePathCreateIfAbsent(executiondirectorycontext, path);
	}

	/**
	 * Resolves a directory using the given relative path against a base directory, creating it if possible.
	 * <p>
	 * It is recommended that users call
	 * {@link TaskExecutionUtilities#resolveDirectoryAtRelativePathCreateIfAbsent(SakerDirectory, SakerPath)} instead.
	 * <p>
	 * This method doesn't overwrite the already existing file at the path if it's not a directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The relative path.
	 * @return The directory at the given path or <code>null</code> if it cannot be created.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not relative.
	 */
	public static SakerDirectory resolveDirectoryAtRelativePathCreateIfAbsent(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(basedir, "base directory");
		requireRelativePath(path);
		Iterator<String> it = path.nameIterator();
		return resolveDirectoryAtRelativePathNamesCreateIfAbsentImpl(basedir, it);
	}

	/**
	 * Resolves a directory with the given path names against a base directory, creating it if possible.
	 * <p>
	 * It is recommended that users call
	 * {@link TaskExecutionUtilities#resolveDirectoryAtRelativePathNamesCreateIfAbsent(SakerDirectory, Iterable)}
	 * instead.
	 * <p>
	 * This method doesn't overwrite the already existing file at the path if it's not a directory.
	 * <p>
	 * The path names are interpreted as if they were relative compared to the base directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param names
	 *            The relative path names.
	 * @return The directory at the given path or <code>null</code> if it cannot be created.
	 * @throws NullPointerException
	 *             If any of the arguments or path names are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If any of the path name has invalid format.
	 * @see #resolveDirectoryAtRelativePathCreateIfAbsent(SakerDirectory, SakerPath)
	 * @see #requireValidFileName(String)
	 */
	public static SakerDirectory resolveDirectoryAtRelativePathNamesCreateIfAbsent(SakerDirectory basedir,
			Iterable<? extends String> names) throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(basedir, "base directory");
		Iterator<? extends String> it = names.iterator();
		return resolveDirectoryAtRelativePathNamesCreateIfAbsentImpl(basedir, it);
	}

	private static SakerDirectory resolveDirectoryAtRelativePathNamesCreateIfAbsentImpl(SakerDirectory basedir,
			Iterator<? extends String> it) throws NullPointerException {
		if (!it.hasNext()) {
			return basedir;
		}
		while (basedir != null && it.hasNext()) {
			String p = it.next();
			basedir = resolveDirectoryCreateIfAbsent(basedir, p);
		}
		return basedir;
	}

	/**
	 * Resolves a file or directory name against the specified directory.
	 * <p>
	 * Tha names <code>"."</code> and <code>".."</code> are specially handled meaning the directory itself, and its
	 * parent respectively.
	 * <p>
	 * In any other case a child file of the directory is returned.
	 * 
	 * @param dir
	 *            The directory.
	 * @param childname
	 *            The name of the requested child.
	 * @return The resolved file or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the child name has an invalid format.
	 * @see SakerDirectory#get(String)
	 */
	public static SakerFile resolve(SakerDirectory dir, String childname)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(dir, "directory");
		Objects.requireNonNull(childname, "file name");
		switch (childname) {
			case ".": {
				return dir;
			}
			case "..": {
				return dir.getParent();
			}
			default: {
				return dir.get(childname);
			}
		}
	}

	/**
	 * Resolves a directory name on the specified directory.
	 * <p>
	 * Tha names <code>"."</code> and <code>".."</code> are specially handled meaning the directory itself, and its
	 * parent respectively.
	 * <p>
	 * If the specified child of the directory is not a directory instance, then <code>null</code> is returned.
	 * <p>
	 * In any other case a child directory of the directory is returned.
	 * 
	 * @param dir
	 *            The directory.
	 * @param childname
	 *            The name of the requested child.
	 * @return The resolved directory or <code>null</code> if a directory was not found for the name.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the child name has an invalid format.
	 * @see SakerDirectory#getDirectory(String)
	 */
	public static SakerDirectory resolveDirectory(SakerDirectory dir, String childname)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(dir, "directory");
		Objects.requireNonNull(childname, "file name");
		switch (childname) {
			case ".": {
				return dir;
			}
			case "..": {
				return dir.getParent();
			}
			default: {
				return dir.getDirectory(childname);
			}
		}
	}

	/**
	 * Resolves a file but not a directory name on the specified directory.
	 * <p>
	 * Tha names <code>"."</code> and <code>".."</code> are specially handled meaning the directory itself, and its
	 * parent respectively. In these cases <code>null</code> is returned.
	 * <p>
	 * If the specified child of the directory is a directory instance, then <code>null</code> is returned.
	 * <p>
	 * In any other case a child file of the directory is returned.
	 * 
	 * @param dir
	 *            The directory.
	 * @param childname
	 *            The name of the requested child file.
	 * @return The resolved file or <code>null</code> if a file was not found for the name or the found file is a
	 *             directory.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the child name has an invalid format.
	 * @see SakerDirectory#get(String)
	 */
	public static SakerFile resolveFile(SakerDirectory dir, String childname)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(dir, "directory");
		Objects.requireNonNull(childname, "file name");
		switch (childname) {
			case ".": {
				return null;
			}
			case "..": {
				return null;
			}
			default: {
				SakerFile res = dir.get(childname);
				if (res instanceof SakerDirectory) {
					return null;
				}
				return res;
			}
		}
	}

	/**
	 * Resolves a directory name on the specified directory, creating if necessary.
	 * <p>
	 * Tha names <code>"."</code> and <code>".."</code> are specially handled meaning the directory itself, and its
	 * parent respectively.
	 * <p>
	 * If the file name is <code>".."</code> and the directory has no parent, <code>null</code> is returned.
	 * <p>
	 * In any other case a child directory of the passed directory is returned or created.
	 * 
	 * @param dir
	 *            The directory.
	 * @param childname
	 *            The name of the requested child.
	 * @return The resolved/created directory or <code>null</code> if the file name is <code>".."</code> and the
	 *             directory has no parent.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the child name has an invalid format.
	 * @see SakerDirectory#getDirectoryCreate(String)
	 */
	public static SakerDirectory resolveDirectoryCreate(SakerDirectory dir, String childname)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(dir, "directory");
		Objects.requireNonNull(childname, "file name");
		switch (childname) {
			case ".": {
				return dir;
			}
			case "..": {
				return dir.getParent();
			}
			default: {
				return dir.getDirectoryCreate(childname);
			}
		}
	}

	/**
	 * Resolves a directory name on the specified directory, creating if possible.
	 * <p>
	 * Tha names <code>"."</code> and <code>".."</code> are specially handled meaning the directory itself, and its
	 * parent respectively.
	 * <p>
	 * If the specified child of the directory is present and not a directory instance, then <code>null</code> is
	 * returned.
	 * <p>
	 * If the file name is <code>".."</code> and the directory has no parent, <code>null</code> is returned.
	 * <p>
	 * In any other case a child directory of the passed directory is returned or created.
	 * 
	 * @param dir
	 *            The directory.
	 * @param childname
	 *            The name of the requested child.
	 * @return The resolved/created directory or <code>null</code> if the file name is <code>".."</code> and the
	 *             directory has no parent, or failed to create the directory.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the child name has an invalid format.
	 * @see SakerDirectory#getDirectoryCreateIfAbsent(String)
	 */
	public static SakerDirectory resolveDirectoryCreateIfAbsent(SakerDirectory dir, String childname)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(dir, "directory");
		Objects.requireNonNull(childname, "file name");
		switch (childname) {
			case ".": {
				return dir;
			}
			case "..": {
				return dir.getParent();
			}
			default: {
				return dir.getDirectoryCreateIfAbsent(childname);
			}
		}
	}

	/**
	 * Writes the contents of the file to the parameter stream, implicitly synchronizing the file using the specified
	 * content database.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#writeTo(SakerFile, OutputStream)} instead.
	 * 
	 * @param file
	 *            The file to write the contents of.
	 * @param os
	 *            The stream to write the contents to.
	 * @param db
	 *            The content database for implicit synchronization. May be <code>null</code> to skip implicit
	 *            synchronization.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If file or the stream is <code>null</code>.
	 * @see SakerFile#writeTo(OutputStream)
	 */
	public static void writeTo(SakerFile file, OutputStream os, ContentDatabase db)
			throws IOException, NullPointerException {
		Objects.requireNonNull(file, "file");
		Objects.requireNonNull(os, "output stream");
		int effopenmethods = file.getEfficientOpeningMethods();
		if (((effopenmethods & SakerFile.OPENING_METHOD_WRITETOSTREAM) == SakerFile.OPENING_METHOD_WRITETOSTREAM)) {
			file.writeToStreamImpl(os);
			return;
		}
		if (((effopenmethods & SakerFile.OPENING_METHOD_GETBYTES) == SakerFile.OPENING_METHOD_GETBYTES)) {
			file.getBytesImpl().writeTo(os);
			return;
		}
		if (((effopenmethods & SakerFile.OPENING_METHOD_GETCONTENTS) == SakerFile.OPENING_METHOD_GETCONTENTS)) {
			String str = file.getContentImpl();
			//XXX we could stream the bytes and avoid allocating a full new byte array
			os.write(str.getBytes(StandardCharsets.UTF_8));
			return;
		}
		if (db != null) {
			SakerPath filepath = file.getSakerPath();
			if (filepath.isAbsolute()) {
				ProviderHolderPathKey pathkey = db.getPathConfiguration().getPathKey(filepath);
				db.writeToStreamWithContentOrSynchronize(pathkey, file.getContentDescriptor(), ByteSink.valueOf(os),
						new SynchronizingContentUpdater(file, pathkey));
				//successfully wrote data to stream
				return;
			}
		}
		//else if db is null
		file.writeToStreamImpl(os);
	}

	/**
	 * Opens an input stream to the contents of the file, implicitly synchronizing the file using the specified content
	 * database.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#openInputStream(SakerFile)} instead.
	 * 
	 * @param file
	 *            The file to write the contents of.
	 * @param db
	 *            The content database for implicit synchronization. May be <code>null</code> to skip implicit
	 *            synchronization.
	 * @return An opened stream for the file contents.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If file is <code>null</code>.
	 * @see SakerFile#openInputStream()
	 */
	public static InputStream openInputStream(SakerFile file, ContentDatabase db)
			throws IOException, NullPointerException {
		Objects.requireNonNull(file, "file");
		int effopenmethods = file.getEfficientOpeningMethods();
		if (((effopenmethods & SakerFile.OPENING_METHOD_OPENINPUTSTREAM) == SakerFile.OPENING_METHOD_OPENINPUTSTREAM)) {
			return file.openInputStreamImpl();
		}
		if (((effopenmethods & SakerFile.OPENING_METHOD_GETBYTES) == SakerFile.OPENING_METHOD_GETBYTES)) {
			return new UnsyncByteArrayInputStream(file.getBytesImpl());
		}
		if (((effopenmethods & SakerFile.OPENING_METHOD_GETCONTENTS) == SakerFile.OPENING_METHOD_GETCONTENTS)) {
			//XXX maybe create a streaming decoder to bytes stream?
			return new UnsyncByteArrayInputStream(file.getContentImpl().getBytes(StandardCharsets.UTF_8));
		}
		if (db != null) {
			SakerPath filepath = file.getSakerPath();
			if (filepath.isAbsolute()) {
				ProviderHolderPathKey pathkey = db.getPathConfiguration().getPathKey(filepath);
				return ByteSource.toInputStream(db.openInputWithContentOrSynchronize(pathkey,
						file.getContentDescriptor(), new SynchronizingContentUpdater(file, pathkey)));
			}
		}
		//else if db is null
		return file.openInputStreamImpl();
	}

	/**
	 * Gets the raw byte contents of the file, implicitly synchronizing the file using the specified content database.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#getBytes(SakerFile)} instead.
	 * 
	 * @param file
	 *            The file to write the contents of.
	 * @param db
	 *            The content database for implicit synchronization. May be <code>null</code> to skip implicit
	 *            synchronization.
	 * @return The byte contents of the file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If file is <code>null</code>.
	 * @see SakerFile#getBytes()
	 */
	public static ByteArrayRegion getBytes(SakerFile file, ContentDatabase db)
			throws IOException, NullPointerException {
		Objects.requireNonNull(file, "file");
		int effopenmethods = file.getEfficientOpeningMethods();
		if (((effopenmethods & SakerFile.OPENING_METHOD_GETBYTES) == SakerFile.OPENING_METHOD_GETBYTES)) {
			return file.getBytesImpl();
		}
		if (((effopenmethods & SakerFile.OPENING_METHOD_GETCONTENTS) == SakerFile.OPENING_METHOD_GETCONTENTS)) {
			return ByteArrayRegion.wrap(file.getContentImpl().getBytes(StandardCharsets.UTF_8));
		}
		return getNonEfficientBytes(file, db, effopenmethods);
	}

	/**
	 * Gets the string contents of the file, implicitly synchronizing the file using the specified content database.
	 * <p>
	 * It is recommended that users call {@link TaskExecutionUtilities#getContent(SakerFile)} instead.
	 * <p>
	 * Unless the file reports {@linkplain SakerFile#OPENING_METHOD_GETBYTES getting the bytes} efficient, this method
	 * gets the raw bytes of the file and decodes them as UTF-8.
	 * 
	 * @param file
	 *            The file to write the contents of.
	 * @param db
	 *            The content database for implicit synchronization. May be <code>null</code> to skip implicit
	 *            synchronization.
	 * @return The character contents of the file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If file is <code>null</code>.
	 * @see SakerFile#getContent()
	 */
	public static String getContent(SakerFile file, ContentDatabase db) throws IOException, NullPointerException {
		Objects.requireNonNull(file, "file");
		int effopenmethods = file.getEfficientOpeningMethods();
		if (((effopenmethods & SakerFile.OPENING_METHOD_GETCONTENTS) == SakerFile.OPENING_METHOD_GETCONTENTS)) {
			return file.getContentImpl();
		}

		if (((effopenmethods & SakerFile.OPENING_METHOD_GETBYTES) == SakerFile.OPENING_METHOD_GETBYTES)) {
			return file.getBytesImpl().toString();
		}
		return getNonEfficientBytes(file, db, effopenmethods).toString(StandardCharsets.UTF_8);
	}

	private static ByteArrayRegion getNonEfficientBytes(SakerFile file, ContentDatabase db, int effopenmethods)
			throws IOException {
		if (((effopenmethods & SakerFile.OPENING_METHOD_WRITETOSTREAM) == SakerFile.OPENING_METHOD_WRITETOSTREAM)) {
			try (UnsyncByteArrayOutputStream os = new UnsyncByteArrayOutputStream()) {
				file.writeToStreamImpl(os);
				return os.toByteArrayRegion();
			}
		}
		if (((effopenmethods & SakerFile.OPENING_METHOD_OPENINPUTSTREAM) == SakerFile.OPENING_METHOD_OPENINPUTSTREAM)) {
			try (UnsyncByteArrayOutputStream os = new UnsyncByteArrayOutputStream();
					InputStream is = file.openInputStreamImpl()) {
				os.readFrom(is);
				return os.toByteArrayRegion();
			}
		}
		if (db != null) {
			SakerPath filepath = file.getSakerPath();
			if (filepath.isAbsolute()) {
				ProviderHolderPathKey pathkey = db.getPathConfiguration().getPathKey(filepath);
				return db.getBytesWithContentOrSynchronize(pathkey, file.getContentDescriptor(),
						new SynchronizingContentUpdater(file, pathkey));
			}
		}
		//else if db is null
		return file.getBytesImpl();
	}

	/**
	 * Executes the synchronization of a file (but not a directory) to the specified path using the given content
	 * database.
	 * <p>
	 * Clients should not call this method directly, but {@link SakerFile#synchronize(ProviderHolderPathKey)} instead.
	 * 
	 * @param file
	 *            The file to synchronize.
	 * @param pathkey
	 *            The path to synchronize the file to.
	 * @param db
	 *            The content database to manage synchronization.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws IllegalArgumentException
	 *             If the file is an instance of {@link SakerDirectory}.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @see SakerFile#synchronize(ProviderHolderPathKey)
	 */
	public static void synchronizeFile(SakerFile file, ProviderHolderPathKey pathkey, ContentDatabase db)
			throws IOException, IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(file, "file");
		Objects.requireNonNull(pathkey, "path key");
		Objects.requireNonNull(db, "content database");
		if (file instanceof SakerDirectory) {
			throw new IllegalArgumentException("Argument is a directory. (" + file + ")");
		}

		synchronizeFileImpl(file, pathkey, db);
	}

	/**
	 * Executes the synchronization of a file (but not a directory) to its denoted path using the given content
	 * database.
	 * <p>
	 * The actual path is determined using {@link ContentDatabase#getPathConfiguration()}.
	 * <p>
	 * Clients should not call this method directly, but {@link SakerFile#synchronize()} instead.
	 * 
	 * @param file
	 *            The file to synchronize.
	 * @param db
	 *            The content database to manage synchronization.
	 * @throws IOException
	 *             In case of I/O error, or if the path of the file is relative.
	 * @throws IllegalArgumentException
	 *             If the file is an instance of {@link SakerDirectory}.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @see SakerFile#synchronize()
	 */
	public static void synchronizeFile(SakerFile file, ContentDatabase db)
			throws IOException, IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(file, "file");
		Objects.requireNonNull(db, "content database");
		if (file instanceof SakerDirectory) {
			throw new IllegalArgumentException("Argument is a directory. (" + file + ")");
		}

		SakerPath path = file.getSakerPath();
		if (path.isRelative()) {
			throw new IOException("Failed to synchronize file without parent: " + path);
		}
		ExecutionPathConfiguration pathconfig = db.getPathConfiguration();
		synchronizeFileImpl(file, pathconfig.getPathKey(path), db);
	}

	/**
	 * Executes the synchronization of a directory to its denoted path using the given content database.
	 * <p>
	 * The actual path is determined using {@link ContentDatabase#getPathConfiguration()}.
	 * <p>
	 * Clients should not call this method directly, but {@link SakerDirectory#synchronize()} instead.
	 * 
	 * @param dir
	 *            The directory to synchronize.
	 * @param db
	 *            The content database to manage synchronization.
	 * @throws IOException
	 *             In case of I/O error, or if the path of the file is relative.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static void synchronizeDirectory(SakerDirectory dir, ContentDatabase db)
			throws IOException, NullPointerException {
		Objects.requireNonNull(dir, "directory");
		Objects.requireNonNull(db, "content database");
		SakerPath path = dir.getSakerPath();
		if (path.isRelative()) {
			throw new IOException("Failed to synchronize file without parent: " + path);
		}
		ExecutionPathConfiguration pathconfig = db.getPathConfiguration();
		synchronizeDirectory(dir, pathconfig.getPathKey(path), DirectoryVisitPredicate.everything(), db);
	}

	/**
	 * Executes the synchronization of a directory to its denoted path using the given content database and
	 * synchronization predicate.
	 * <p>
	 * The actual path is determined using {@link ContentDatabase#getPathConfiguration()}.
	 * <p>
	 * Clients should not call this method directly, but {@link SakerDirectory#synchronize(DirectoryVisitPredicate)}
	 * instead.
	 * 
	 * @param dir
	 *            The directory to synchronize.
	 * @param synchpredicate
	 *            The synchronization predicate to determine which files should be synchronized. Passing
	 *            <code>null</code> is the same as {@link DirectoryVisitPredicate#everything()}.
	 * @param db
	 *            The content database to manage synchronization.
	 * @throws IOException
	 *             In case of I/O error, or if the path of the file is relative.
	 * @throws NullPointerException
	 *             If any of the arguments except predicate are <code>null</code>.
	 */
	public static void synchronizeDirectory(SakerDirectory dir, DirectoryVisitPredicate synchpredicate,
			ContentDatabase db) throws IOException, NullPointerException {
		Objects.requireNonNull(dir, "directory");
		Objects.requireNonNull(db, "content database");
		SakerPath path = dir.getSakerPath();
		if (path.isRelative()) {
			throw new IOException("Failed to synchronize file without parent: " + path);
		}
		ExecutionPathConfiguration pathconfig = db.getPathConfiguration();
		synchronizeDirectory(dir, pathconfig.getPathKey(path), synchpredicate, db);
	}

	/**
	 * Executes the synchronization of a directory to the given path using the given content database and
	 * synchronization predicate.
	 * <p>
	 * Clients should not call this method directly, but
	 * {@link SakerDirectory#synchronize(ProviderHolderPathKey, DirectoryVisitPredicate)} instead.
	 * 
	 * @param dir
	 *            The directory to synchronize.
	 * @param pathkey
	 *            The path to synchronize the directory to.
	 * @param synchpredicate
	 *            The synchronization predicate to determine which files should be synchronized. Passing
	 *            <code>null</code> is the same as {@link DirectoryVisitPredicate#everything()}.
	 * @param db
	 *            The content database to manage synchronization.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments except predicate are <code>null</code>.
	 */
	public static void synchronizeDirectory(SakerDirectory dir, ProviderHolderPathKey pathkey,
			DirectoryVisitPredicate synchpredicate, ContentDatabase db) throws IOException, NullPointerException {
		Objects.requireNonNull(dir, "directory");
		Objects.requireNonNull(pathkey, "path key");
		Objects.requireNonNull(db, "content database");
		if (synchpredicate == null) {
			synchpredicate = DirectoryVisitPredicate.everything();
		}

		try (ThreadWorkPool pool = ThreadUtils.newFixedWorkPool("fsync-")) {
			synchronizeDirectoryImpl(dir, pathkey, synchpredicate, pool, db);
		} catch (ParallelExecutionFailedException e) {
			throw new IOException("Synchronization failed: " + pathkey.getPath(), e);
		}
	}

	private static void synchronizeFileImpl(SakerFile file, ProviderHolderPathKey pathkey, ContentDatabase db)
			throws IOException {
		ContentDescriptor thiscontent = file.getContentDescriptor();
		db.synchronize(pathkey, thiscontent, new SynchronizingContentUpdater(file, pathkey));
	}

	private static DeferredSynchronizer synchronizeFileDeferredImpl(SakerFile file, ProviderHolderPathKey pathkey,
			ContentDatabase db) {
		ContentDescriptor thiscontent = file.getContentDescriptor();
		return db.synchronizeDeferred(pathkey, thiscontent, new SynchronizingContentUpdater(file, pathkey));
	}

	private static void synchronizeDirectoryImpl(SakerDirectory dir, ProviderHolderPathKey pathkey,
			DirectoryVisitPredicate synchpredicate, ThreadWorkPool pool, ContentDatabase db) throws IOException {
		//we need to ensure that the files are populated
		//   if we're not synchronizing to the actual directory, then all the files need to match
		//   the .getChildren() call below will ensure that the directory is populated

		SakerPath dirpath = pathkey.getPath();
		db.syncronizeDirectory(pathkey, () -> {
			//get a snapshot of the files
			NavigableMap<String, ? extends SakerFile> thistrackedfiles = dir.getChildren();

			Set<String> keepuntrackedchildren = synchpredicate.getSynchronizeFilesToKeep();
			if (keepuntrackedchildren != null) {
				if (thistrackedfiles.isEmpty()) {
					//clear the directory as we contain no files
					db.deleteChildrenIfNotIn(pathkey, keepuntrackedchildren);
					return;
				}
				NavigableSet<String> presentchildren;
				if (!keepuntrackedchildren.isEmpty()) {
					presentchildren = new TreeSet<>(thistrackedfiles.navigableKeySet());
					presentchildren.addAll(keepuntrackedchildren);
				} else {
					presentchildren = thistrackedfiles.navigableKeySet();
				}

				pool.offer(() -> {
					//delete the files that we don't contain
					db.deleteChildrenIfNotIn(pathkey, presentchildren);
				});
			} else {
				if (thistrackedfiles.isEmpty()) {
					//nothing to synchronize
					return;
				}
			}
			for (SakerFile file : thistrackedfiles.values()) {
				String filename = file.getName();
				if (file instanceof SakerDirectory) {
					SakerDirectory subdir = (SakerDirectory) file;
					DirectoryVisitPredicate dirsyncher = synchpredicate.directoryVisitor(filename, subdir);
					ProviderHolderPathKey filepathkey = new SimpleProviderHolderPathKey(pathkey,
							dirpath.resolve(filename));
					if (dirsyncher == null) {
						if (synchpredicate.visitDirectory(filename, subdir)) {
							//we don't need to synchronize any children of the directory, but we need to synchronize the directory file itself
							//    therefore we need to ensure that the file on the filesystem is actually a directory
							//    but don't need to visit the child files in the directory itself
							db.createDirectoryAtPath(filepathkey);
						}
						continue;
					}
					pool.offer(() -> synchronizeDirectoryImpl(subdir, filepathkey, dirsyncher, pool, db));
				} else {
					if (!synchpredicate.visitFile(filename, file)) {
						continue;
					}
					ProviderHolderPathKey filepathkey = new SimpleProviderHolderPathKey(pathkey,
							dirpath.resolve(filename));
					DeferredSynchronizer defsync = synchronizeFileDeferredImpl(file, filepathkey, db);
					if (defsync != null) {
						pool.offer(defsync::update);
					}
				}
			}
		});
	}

	/**
	 * Gets the children of the specified directory by resolving the names against the current path of the directory.
	 * <p>
	 * Only the direct children of the directory is included.
	 * <p>
	 * This method works in the same way as if {@link SakerDirectory#getChildren()} was called, and every entry key is
	 * resolved against the directory path.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * getDirectoryChildrenByPath(dir, dir.getSakerPath())
	 * </pre>
	 * 
	 * @param dir
	 *            The directory.
	 * @return The direct children of the directory mapped to their names resolved against the directory path.
	 * @throws NullPointerException
	 *             If the directory is <code>null</code>.
	 * @see SakerDirectory#getChildren()
	 */
	public static NavigableMap<SakerPath, SakerFile> getDirectoryChildrenByPath(SakerDirectory dir)
			throws NullPointerException {
		Objects.requireNonNull(dir, "directory");
		return getDirectoryChildrenByPathImpl(dir, dir.getSakerPath());
	}

	/**
	 * Gets the children of the specified directory by resolving the names against the specified path.
	 * <p>
	 * Only the direct children of the directory is included.
	 * <p>
	 * This method works in the same way as if {@link SakerDirectory#getChildren()} was called, and every entry key is
	 * resolved against the base path.
	 * 
	 * @param dir
	 *            The directory.
	 * @param basepath
	 *            The path to resolve the directory child names against.
	 * @return The direct children of the directory mapped to their names resolved against the base path.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @see SakerDirectory#getChildren()
	 */
	public static NavigableMap<SakerPath, SakerFile> getDirectoryChildrenByPath(SakerDirectory dir, SakerPath basepath)
			throws NullPointerException {
		Objects.requireNonNull(dir, "directory");
		Objects.requireNonNull(basepath, "base path");

		return getDirectoryChildrenByPathImpl(dir, basepath);
	}

	private static NavigableMap<SakerPath, SakerFile> getDirectoryChildrenByPathImpl(SakerDirectory dir,
			SakerPath basepath) {
		NavigableMap<String, ? extends SakerFile> children = dir.getChildren();
		return new TreeMap<>(new TransformingNavigableMap<String, SakerFile, SakerPath, SakerFile>(children) {
			@Override
			protected Entry<SakerPath, SakerFile> transformEntry(String key, SakerFile value) {
				return ImmutableUtils.makeImmutableMapEntry(basepath.resolve(key), value);
			}
		});
	}

	/**
	 * Gets the children in the specified directory tree.
	 * <p>
	 * The result map will have the files mapped to a relative path compared to the argument directory. Any file that is
	 * a direct child of the directory will have a relative path with a single path name as their key.
	 * <p>
	 * This is a convenience function for calling:
	 * 
	 * <pre>
	 * dir.getFilesRecursiveByPath(SakerPath.EMPTY, DirectoryVisitPredicate.everything())
	 * </pre>
	 * 
	 * @param dir
	 *            The directory to collect the children of.
	 * @return The collected children. The returned map is mutable.
	 * @throws NullPointerException
	 *             If the directory is <code>null</code>.
	 * @see SakerDirectory#getFilesRecursiveByPath(SakerPath, DirectoryVisitPredicate)
	 */
	public static NavigableMap<SakerPath, SakerFile> getFilesRecursiveByPath(SakerDirectory dir)
			throws NullPointerException {
		Objects.requireNonNull(dir, "directory");
		return dir.getFilesRecursiveByPath(SakerPath.EMPTY, DirectoryVisitPredicate.everything());
	}

	/**
	 * Gets the all of the children in the specified directory tree, resolving the relative paths against the argument.
	 * <p>
	 * This is a convenience function for calling:
	 * 
	 * <pre>
	 * dir.getFilesRecursiveByPath(basepath, DirectoryVisitPredicate.everything())
	 * </pre>
	 * 
	 * @param dir
	 *            The directory to collect the children of.
	 * @param basepath
	 *            The base path to resolve the relative paths agains.
	 * @return The collected children. The returned map is mutable.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @see SakerDirectory#getFilesRecursiveByPath(SakerPath, DirectoryVisitPredicate)
	 */
	public static NavigableMap<SakerPath, SakerFile> getFilesRecursiveByPath(SakerDirectory dir, SakerPath basepath)
			throws NullPointerException {
		Objects.requireNonNull(dir, "directory");
		Objects.requireNonNull(basepath, "base path");
		return dir.getFilesRecursiveByPath(basepath, DirectoryVisitPredicate.everything());
	}

	/**
	 * Gets the children in the specified directory tree using the given predicate.
	 * <p>
	 * This is a convenience function for calling:
	 * 
	 * <pre>
	 * dir.getFilesRecursiveByPath(SakerPath.EMPTY, predicate)
	 * </pre>
	 * 
	 * @param dir
	 *            The directory to collect the children of.
	 * @param predicate
	 *            The predicate to define the files to visit. If this is <code>null</code>,
	 *            {@link DirectoryVisitPredicate#everything()} will be used.
	 * @return The collected children. The returned map is mutable.
	 * @throws NullPointerException
	 *             If the directory is <code>null</code>.
	 * @see SakerDirectory#getFilesRecursiveByPath(SakerPath, DirectoryVisitPredicate)
	 */
	public static NavigableMap<SakerPath, SakerFile> getFilesRecursiveByPath(SakerDirectory dir,
			DirectoryVisitPredicate predicate) throws NullPointerException {
		Objects.requireNonNull(dir, "directory");
		return dir.getFilesRecursiveByPath(SakerPath.EMPTY, predicate);
	}

	/**
	 * Converts the argument to an absolute path by optionally resolving it against the working directory of the task
	 * context.
	 * <p>
	 * If the argument path is already absolute, it is returned directly.
	 * 
	 * @param taskcontext
	 *            The task context.
	 * @param path
	 *            The path to absolutize.
	 * @return The path converted to an absolute path.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static SakerPath toAbsolutePath(TaskContext taskcontext, SakerPath path) throws NullPointerException {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(taskcontext, "task context");
		if (path.isAbsolute()) {
			return path;
		}
		return taskcontext.getTaskWorkingDirectory().getSakerPath().resolve(path);
	}

	/**
	 * Checks if a location specified by a file provider and absolute path is same as the given path key.
	 * 
	 * @param fileprovider
	 *            The file provider for the path.
	 * @param path
	 *            The path.
	 * @param pathkey
	 *            The location to compare the path against.
	 * @return <code>true</code> if they represent the same file location.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see PathKey
	 */
	public static boolean isSamePaths(SakerFileProvider fileprovider, SakerPath path, PathKey pathkey)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(fileprovider, "file provider");
		Objects.requireNonNull(pathkey, "path key");
		requireAbsolutePath(path);
		for (SakerFileProvider w; (w = fileprovider.getWrappedProvider()) != null;) {
			path = fileprovider.resolveWrappedPath(path);
			fileprovider = w;
		}
		return pathkey.getPath().equals(path) && pathkey.getFileProviderKey().equals(fileprovider.getProviderKey());
	}

	/**
	 * Checks if the specified file provider-path pair point to the same location.
	 * 
	 * @param firstprovider
	 *            The file provider for the first path.
	 * @param firstpath
	 *            The first path.
	 * @param secondprovider
	 *            The file provider for the second path.
	 * @param secondpath
	 *            The second path.
	 * @return <code>true</code> if they represent the same file location.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path arguments are not absolute.
	 * @see PathKey
	 */
	public static boolean isSamePaths(SakerFileProvider firstprovider, SakerPath firstpath,
			SakerFileProvider secondprovider, SakerPath secondpath)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(firstprovider, "first file provider");
		Objects.requireNonNull(secondprovider, "second file provider");
		requireAbsolutePath(firstpath);
		requireAbsolutePath(secondpath);
		for (SakerFileProvider w; (w = firstprovider.getWrappedProvider()) != null;) {
			firstpath = firstprovider.resolveWrappedPath(firstpath);
			firstprovider = w;
		}
		for (SakerFileProvider w; (w = secondprovider.getWrappedProvider()) != null;) {
			secondpath = secondprovider.resolveWrappedPath(secondpath);
			secondprovider = w;
		}
		return firstpath.equals(secondpath) && SakerPathFiles.isSameProvider(firstprovider, secondprovider);
	}

	/**
	 * Checks if the specified path with the given file provider has the same location as the argument local path.
	 * <p>
	 * The {@linkplain Path#getFileSystem() file system} of the argument {@link Path} object is not taken into account.
	 * It is always interpreted as a path on the local file system.
	 * 
	 * @param fileprovider
	 *            The file provider for the path.
	 * @param path
	 *            The path.
	 * @param localpath
	 *            The local path to compare against.
	 * @return <code>true</code> if they represent the same file location.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the paths are not absolute.
	 * @see PathKey
	 */
	public static boolean isSamePaths(SakerFileProvider fileprovider, SakerPath path, Path localpath)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(fileprovider, "file provider");
		Objects.requireNonNull(localpath, "local path");
		requireAbsolutePath(path);
		requireAbsolutePath(localpath);
		for (SakerFileProvider w; (w = fileprovider.getWrappedProvider()) != null;) {
			path = fileprovider.resolveWrappedPath(path);
			fileprovider = w;
		}
		return LocalFileProvider.getProviderKeyStatic().equals(fileprovider.getProviderKey())
				&& SakerPath.valueOf(localpath).equals(path);
	}

	/**
	 * Checks if the argument path key operate on the same file providers, and the second path is a subpath of the first
	 * (base) path.
	 * 
	 * @param basepath
	 *            The base path.
	 * @param path
	 *            The path to check if it is a subpath ot the base path.
	 * @return <code>true</code> if the paths are associated with the same file providers, and the second path is a
	 *             subpath of the first.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @see SakerPath#startsWith(SakerPath)
	 * @see FileProviderKey
	 */
	public static boolean isSubPath(PathKey basepath, PathKey path) throws NullPointerException {
		Objects.requireNonNull(basepath, "base path");
		Objects.requireNonNull(path, "path");
		return basepath.getFileProviderKey().equals(path.getFileProviderKey())
				&& path.getPath().startsWith(basepath.getPath());
	}

	/**
	 * Tests if second file provider-path pair is a subpath of the first base pair.
	 * 
	 * @param baseprovider
	 *            The base path file provider.
	 * @param basepath
	 *            The base path.
	 * @param provider
	 *            The test path file provider.
	 * @param path
	 *            The test path.
	 * @return <code>true</code> if the second path is a subpath of the first.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the paths are not absolute.
	 * @see SakerPath#startsWith(SakerPath)
	 * @see FileProviderKey
	 */
	public static boolean isSubPath(SakerFileProvider baseprovider, SakerPath basepath, SakerFileProvider provider,
			SakerPath path) throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(baseprovider, "base provider");
		Objects.requireNonNull(provider, "provider");
		requireAbsolutePath(basepath);
		requireAbsolutePath(path);
		for (SakerFileProvider w; (w = baseprovider.getWrappedProvider()) != null;) {
			basepath = baseprovider.resolveWrappedPath(basepath);
			baseprovider = w;
		}
		for (SakerFileProvider w; (w = provider.getWrappedProvider()) != null;) {
			path = provider.resolveWrappedPath(path);
			provider = w;
		}
		return path.startsWith(basepath) && SakerPathFiles.isSameProvider(baseprovider, provider);
	}

	/**
	 * Checks if the argument file provider keys are the same.
	 * <p>
	 * The keys are checked for equality.
	 * 
	 * @param key1
	 *            The first key.
	 * @param key2
	 *            The second key.
	 * @return <code>true</code> if the keys {@linkplain Object#equals(Object) equal}.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static boolean isSameProvider(FileProviderKey key1, FileProviderKey key2) throws NullPointerException {
		Objects.requireNonNull(key1, "key1");
		Objects.requireNonNull(key2, "key2");
		return Objects.equals(key1, key2);
	}

	/**
	 * Checks if the file provider key represents the file provider as the file provider argument.
	 * <p>
	 * The key is checked for equality against the key of the argument file provider.
	 * 
	 * @param key
	 *            The file provider key.
	 * @param fileprovider
	 *            The file provider.
	 * @return <code>true</code> if the key equals to the key of the file provider.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static boolean isSameProvider(FileProviderKey key, SakerFileProvider fileprovider)
			throws NullPointerException {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(fileprovider, "file provider");
		try {
			FileProviderKey fpk2 = fileprovider.getProviderKey();
			return Objects.equals(key, fpk2);
		} catch (RMIRuntimeException e) {
			return false;
		}
	}

	/**
	 * Checks if the argument file providers operate on the same files.
	 * <p>
	 * Their file provider keys are checked for equality.
	 * 
	 * @param provider1
	 *            The first file provider.
	 * @param provider2
	 *            The second file provider.
	 * @return <code>true</code> if their file provider keys {@linkplain Object#equals(Object) equal}.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static boolean isSameProvider(SakerFileProvider provider1, SakerFileProvider provider2)
			throws NullPointerException {
		Objects.requireNonNull(provider1, "provider1");
		Objects.requireNonNull(provider2, "provider2");
		if (provider1 == provider2) {
			return true;
		}
		try {
			FileProviderKey fpk1 = provider1.getProviderKey();
			FileProviderKey fpk2 = provider2.getProviderKey();
			return Objects.equals(fpk1, fpk2);
		} catch (RMIRuntimeException e) {
			return false;
		}
	}

	/**
	 * Converts the given {@linkplain SakerFile files} to a map with their retrieved paths as keys.
	 * <p>
	 * Works the same way as {@link TaskExecutionUtilities#toPathFileMap(Iterable)}, and it is recommended for tasks to
	 * call that instead.
	 * 
	 * @param files
	 *            The files to map to their paths.
	 * @return A new map of paths mapped to the corresponding files.
	 * @throws NullPointerException
	 *             If the files or any of its elements are <code>null</code>.
	 */
	public static NavigableMap<SakerPath, SakerFile> toPathFileMap(Iterable<? extends SakerFile> files)
			throws NullPointerException {
		Objects.requireNonNull(files, "files");
		TreeMap<SakerPath, SakerFile> result = new TreeMap<>();
		for (SakerFile f : files) {
			Objects.requireNonNull(f, "file");
			result.put(f.getSakerPath(), f);
		}
		return result;
	}

	/**
	 * Converts the given {@linkplain SakerFile files} to a map with their retrieved paths as keys and content
	 * descriptors as values.
	 * <p>
	 * Works the same way as {@link TaskExecutionUtilities#toPathContentMap(Iterable)}, and it is recommended for tasks
	 * to call that instead.
	 * 
	 * @param files
	 *            The files to retrieve the paths and contents.
	 * @return A new map of paths mapped to the corresponding content descriptors.
	 * @throws NullPointerException
	 *             If the files or any of its elements are <code>null</code>.
	 */
	public static NavigableMap<SakerPath, ContentDescriptor> toPathContentMap(Iterable<? extends SakerFile> files)
			throws NullPointerException {
		Objects.requireNonNull(files, "files");
		TreeMap<SakerPath, ContentDescriptor> result = new TreeMap<>();
		for (SakerFile f : files) {
			Objects.requireNonNull(f, "file");
			result.put(f.getSakerPath(), f.getContentDescriptor());
		}
		return result;
	}

	/**
	 * Gets the content descriptors of the children of the specified directory.
	 * <p>
	 * Works the same way as {@link TaskExecutionUtilities#getChildrenFileContents(SakerDirectory)}, and it is
	 * recommended for tasks to call that instead.
	 * 
	 * @param dir
	 *            The directory.
	 * @return A new map of file names mapped to their content descriptors.
	 * @throws NullPointerException
	 *             If the directory is <code>null</code>.
	 */
	public static NavigableMap<String, ContentDescriptor> getChildrenFileContents(SakerDirectory dir)
			throws NullPointerException {
		Objects.requireNonNull(dir, "directory");
		return toFileContentMap(dir.getChildren());
	}

	/**
	 * Transforms the parameter map by taking the content descriptor of the values.
	 * <p>
	 * The ordering of the returned map is the same as the argument.
	 * <p>
	 * This function has no conterpart in {@link TaskExecutionUtilities}. To retrieve content descriptors of files see
	 * {@link TaskExecutionUtilities#toPathContentMap(Iterable)}.
	 * 
	 * @param map
	 *            The map to transform.
	 * @return A new map mapped to the appropriate keys to the corresponding content descriptors of the files.
	 * @throws NullPointerException
	 *             If the map or any of the files are <code>null</code>.
	 */
	public static <K> NavigableMap<K, ContentDescriptor> toFileContentMap(SortedMap<K, ? extends SakerFile> map)
			throws NullPointerException {
		Objects.requireNonNull(map, "map");
		NavigableMap<K, ContentDescriptor> childcontents = new TreeMap<>(
				new TransformingSortedMap<K, SakerFile, K, ContentDescriptor>(map, map.comparator()) {
					@Override
					protected Entry<K, ContentDescriptor> transformEntry(K key, SakerFile value) {
						return ImmutableUtils.makeImmutableMapEntry(key, value.getContentDescriptor());
					}
				});
		return childcontents;
	}

	/**
	 * Checks if all paths in the argument set starts with the specified base path.
	 * <p>
	 * The argument must be naturally ordered.
	 * <p>
	 * If the path set is empty, <code>true</code> is returned.
	 * 
	 * @param paths
	 *            The paths.
	 * @param base
	 *            The base path to check the paths agains.
	 * @return <code>true</code> if all paths in the set start with the base path argument.
	 */
	public static boolean isAllSubPath(SortedSet<SakerPath> paths, SakerPath base) {
		ObjectUtils.requireNaturalOrder(paths);
		if (paths.isEmpty()) {
			return true;
		}
		SakerPath f = paths.first();
		if (!f.startsWith(base)) {
			return false;
		}
		SakerPath l = paths.last();
		if (l == f) {
			//only a single path in the set
			return true;
		}
		if (!l.startsWith(base)) {
			return false;
		}
		return true;
	}

	/**
	 * Checks if all key paths in the argument map starts with the specified base path.
	 * <p>
	 * The argument must be naturally ordered.
	 * <p>
	 * If the path map is empty, <code>true</code> is returned.
	 * 
	 * @param paths
	 *            The paths.
	 * @param base
	 *            The base path to check the paths agains.
	 * @return <code>true</code> if all paths in the map start with the base path argument.
	 */
	public static boolean isAllSubPath(SortedMap<SakerPath, ?> paths, SakerPath base) {
		ObjectUtils.requireNaturalOrder(paths);
		if (paths.isEmpty()) {
			return true;
		}
		SakerPath f = paths.firstKey();
		if (!f.startsWith(base)) {
			return false;
		}
		SakerPath l = paths.lastKey();
		if (l == f) {
			//only a single path in the set
			return true;
		}
		if (!l.startsWith(base)) {
			return false;
		}
		return true;
	}

	/**
	 * Converts a map by taking the {@linkplain SakerPath#subPath(int) subpath} of the keys for a given path base.
	 * <p>
	 * The argument must be naturally ordered.
	 * 
	 * @param <V>
	 *            The value type of the map.
	 * @param paths
	 *            The paths map to convert.
	 * @param base
	 *            The path base for taking the subpaths of the keys.
	 * @return The converted map, where every key is relative to the base path.
	 * @throws IllegalArgumentException
	 *             If not all key in the map is a subpath of the path base.
	 * @see #isAllSubPath(SortedMap, SakerPath)
	 */
	public static <V> NavigableMap<SakerPath, V> relativizeSubPath(SortedMap<SakerPath, V> paths, SakerPath base)
			throws IllegalArgumentException {
		ObjectUtils.requireNaturalOrder(paths);
		if (!isAllSubPath(paths, base)) {
			throw new IllegalArgumentException("Not all paths are subpath of base: " + base + " for range: "
					+ paths.firstKey() + " to " + paths.lastKey());
		}

		int namecount = base.getNameCount();
		return new TreeMap<>(new TransformingSortedMap<SakerPath, V, SakerPath, V>(paths) {
			@Override
			protected Entry<SakerPath, V> transformEntry(SakerPath key, V value) {
				return ImmutableUtils.makeImmutableMapEntry(key.subPath(namecount), value);
			}
		});
	}

	/**
	 * Converts a path set by taking the {@linkplain SakerPath#subPath(int) subpath} of the paths for a given path base.
	 * <p>
	 * The argument must be naturally ordered.
	 * 
	 * @param paths
	 *            The paths set to convert.
	 * @param base
	 *            The path base for taking the subpaths of the elements.
	 * @return The converted set, where every element is relative to the base path.
	 * @throws IllegalArgumentException
	 *             If not all element in the set is a subpath of the path base.
	 * @see #isAllSubPath(SortedSet, SakerPath)
	 */
	public static NavigableSet<SakerPath> relativizeSubPath(SortedSet<SakerPath> paths, SakerPath base)
			throws IllegalArgumentException {
		ObjectUtils.requireNaturalOrder(paths);
		if (!isAllSubPath(paths, base)) {
			throw new IllegalArgumentException("Not all paths are subpath of base: " + base + " for range: "
					+ paths.first() + " to " + paths.last());
		}

		int namecount = base.getNameCount();
		return new TreeSet<>(new TransformingSortedSet<SakerPath, SakerPath>(paths) {
			@Override
			protected SakerPath transform(SakerPath key) {
				return key.subPath(namecount);
			}
		});
	}

	/**
	 * Checks if the argument set contains any relative path element.
	 * <p>
	 * The argument must be naturally ordered.
	 * <p>
	 * If the argument is empty, <code>false</code> is returned.
	 * 
	 * @param paths
	 *            The paths.
	 * @return <code>true</code> if the argument contains at least one relative path.
	 * @see SakerPath#isRelative()
	 */
	public static boolean hasRelativePath(SortedSet<? extends SakerPath> paths) {
		if (paths == null) {
			return false;
		}
		ObjectUtils.requireNaturalOrder(paths);

		return !paths.isEmpty() && paths.first().isRelative();
	}

	/**
	 * Checks if the argument set contains any absolute path element.
	 * <p>
	 * The argument must be naturally ordered.
	 * <p>
	 * If the argument is empty, <code>false</code> is returned.
	 * 
	 * @param paths
	 *            The paths.
	 * @return <code>true</code> if the argument contains at least one absolute path.
	 * @see SakerPath#isAbsolute()
	 */
	public static boolean hasAbsolutePath(SortedSet<? extends SakerPath> paths) {
		if (paths == null) {
			return false;
		}
		ObjectUtils.requireNaturalOrder(paths);

		return !paths.isEmpty() && paths.last().isAbsolute();
	}

	/**
	 * Checks if the argument map contains any relative path keys.
	 * <p>
	 * The argument must be naturally ordered.
	 * <p>
	 * If the argument is empty, <code>false</code> is returned.
	 * 
	 * @param paths
	 *            The paths.
	 * @return <code>true</code> if the argument contains at least one relative path.
	 * @see SakerPath#isRelative()
	 */
	public static boolean hasRelativePath(SortedMap<? extends SakerPath, ?> paths) {
		if (paths == null) {
			return false;
		}
		ObjectUtils.requireNaturalOrder(paths);

		return !paths.isEmpty() && paths.firstKey().isRelative();
	}

	/**
	 * Checks if the argument map contains any absolute path key.
	 * <p>
	 * The argument must be naturally ordered.
	 * <p>
	 * If the argument is empty, <code>false</code> is returned.
	 * 
	 * @param paths
	 *            The paths.
	 * @return <code>true</code> if the argument contains at least one absolute path.
	 * @see SakerPath#isAbsolute()
	 */
	public static boolean hasAbsolutePath(SortedMap<? extends SakerPath, ?> paths) {
		if (paths == null) {
			return false;
		}
		ObjectUtils.requireNaturalOrder(paths);

		return !paths.isEmpty() && paths.lastKey().isAbsolute();
	}

	/**
	 * Checks if the argument set contains at least one path that is a subpath of the specified test path.
	 * <p>
	 * The argument must be naturally ordered.
	 * 
	 * @param paths
	 *            The paths.
	 * @param testpath
	 *            The test path to check subpaths for.
	 * @return <code>true</code> if there is at least one subpath in the set.
	 */
	public static boolean hasSubPath(NavigableSet<SakerPath> paths, SakerPath testpath) {
		if (paths == null || testpath == null) {
			return false;
		}
		ObjectUtils.requireNaturalOrder(paths);

		SakerPath higher = paths.higher(testpath);
		return higher != null && higher.startsWith(testpath);
	}

	/**
	 * Checks if the argument set contains the specified test path, or any subpath of it.
	 * <p>
	 * The argument must be naturally ordered.
	 * 
	 * @param paths
	 *            The paths.
	 * @param testpath
	 *            The test path to check inclusion and subpaths for.
	 * @return <code>true</code> if the set contains the path or there is at least one subpath in it.
	 */
	public static boolean hasPathOrSubPath(NavigableSet<SakerPath> paths, SakerPath testpath) {
		if (paths == null || testpath == null) {
			return false;
		}
		ObjectUtils.requireNaturalOrder(paths);

		SakerPath ceiling = paths.ceiling(testpath);
		return ceiling != null && ceiling.startsWith(testpath);
	}

	/**
	 * Gets a subset of the argument paths which only contain relative paths.
	 * <p>
	 * The argument must be naturally ordered.
	 * 
	 * @param paths
	 *            The paths.
	 * @return A subset of the argument which only contains the relative paths of the argument.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see SakerPath#isRelative()
	 */
	public static NavigableSet<SakerPath> getPathSubSetRelatives(NavigableSet<SakerPath> paths)
			throws NullPointerException {
		ObjectUtils.requireNaturalOrder(paths);

		return paths.headSet(SakerPath.FIRST_ABSOLUTE_PATH, false);
	}

	/**
	 * Gets a subset of the argument paths which only contain relative paths.
	 * <p>
	 * The argument must be naturally ordered.
	 * 
	 * @param paths
	 *            The paths.
	 * @return A subset of the argument which only contains the relative paths of the argument.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see SakerPath#isRelative()
	 */
	public static SortedSet<SakerPath> getPathSubSetRelatives(SortedSet<SakerPath> paths) throws NullPointerException {
		ObjectUtils.requireNaturalOrder(paths);

		return paths.headSet(SakerPath.FIRST_ABSOLUTE_PATH);
	}

	/**
	 * Gets a subset of the argument paths which only contain absolute paths.
	 * <p>
	 * The argument must be naturally ordered.
	 * 
	 * @param paths
	 *            The paths.
	 * @return A subset of the argument which only contains the absolute paths of the argument.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see SakerPath#isAbsolute()
	 */
	public static NavigableSet<SakerPath> getPathSubSetAbsolutes(NavigableSet<SakerPath> paths)
			throws NullPointerException {
		ObjectUtils.requireNaturalOrder(paths);

		return paths.tailSet(SakerPath.FIRST_ABSOLUTE_PATH, false);
	}

	/**
	 * Gets a submap of the argument which only contains entries that are mapped to a relative path.
	 * <p>
	 * The argument must be naturally ordered.
	 * 
	 * @param <V>
	 *            The value type of the map.
	 * @param paths
	 *            The path map.
	 * @return A submap of the argument which only contains entries that are mapped to a relative path.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see SakerPath#isRelative()
	 */
	public static <V> NavigableMap<SakerPath, V> getPathSubMapRelatives(NavigableMap<SakerPath, V> paths)
			throws NullPointerException {
		ObjectUtils.requireNaturalOrder(paths);

		return paths.headMap(SakerPath.FIRST_ABSOLUTE_PATH, false);
	}

	/**
	 * Gets a submap of the argument which only contains entries that are mapped to a absolute path.
	 * <p>
	 * The argument must be naturally ordered.
	 * 
	 * @param <V>
	 *            The value type of the map.
	 * @param paths
	 *            The path map.
	 * @return A submap of the argument which only contains entries that are mapped to a absolute path.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see SakerPath#isAbsolute()
	 */
	public static <V> NavigableMap<SakerPath, V> getPathSubMapAbsolutes(NavigableMap<SakerPath, V> paths)
			throws NullPointerException {
		ObjectUtils.requireNaturalOrder(paths);

		return paths.tailMap(SakerPath.FIRST_ABSOLUTE_PATH, false);
	}

	/**
	 * Gets a subset of the argument which only contains paths that only contain paths that start with the argument
	 * directory path.
	 * <p>
	 * The argument must be naturally ordered.
	 * 
	 * @param paths
	 *            The paths.
	 * @param dirpath
	 *            The directory base path.
	 * @param includedirectory
	 *            <code>boolean</code> indicating whether to include the directory base path in the result subset or
	 *            not.
	 * @return The subset which only contains paths that start with the specified directory base path.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static NavigableSet<SakerPath> getPathSubSetDirectoryChildren(NavigableSet<SakerPath> paths,
			SakerPath dirpath, boolean includedirectory) throws NullPointerException {
		ObjectUtils.requireNaturalOrder(paths);
		Objects.requireNonNull(dirpath, "dir path");

		return paths.subSet(dirpath, includedirectory, dirpath.nextSiblingPathInNaturalOrder(), false);
	}

	/**
	 * Gets a submap of the argument which only contains entries with keys that start with the argument directory path.
	 * <p>
	 * The argument must be naturally ordered.
	 * 
	 * @param <V>
	 *            The value type of the map.
	 * @param paths
	 *            The paths.
	 * @param dirpath
	 *            The directory base path.
	 * @param includedirectory
	 *            <code>boolean</code> indicating whether to include the directory base path in the result subset or
	 *            not.
	 * @return The submap which only contains entries with keys that start with the specified directory base path.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <V> NavigableMap<SakerPath, V> getPathSubMapDirectoryChildren(NavigableMap<SakerPath, V> paths,
			SakerPath dirpath, boolean includedirectory) throws NullPointerException {
		ObjectUtils.requireNaturalOrder(paths);
		Objects.requireNonNull(dirpath, "dir path");

		return paths.subMap(dirpath, includedirectory, dirpath.nextSiblingPathInNaturalOrder(), false);
	}

	/**
	 * Gets a submap of the argument which only contains entries with keys that start with the argument directory path.
	 * <p>
	 * The argument must be naturally ordered.
	 * <p>
	 * This method is same as {@link #getPathSubMapDirectoryChildren(NavigableMap, SakerPath, boolean)}, but is
	 * specialized for {@linkplain ConcurrentNavigableMap concurrent navigable maps}.
	 * 
	 * @param <V>
	 *            The value type of the map.
	 * @param paths
	 *            The paths.
	 * @param dirpath
	 *            The directory base path.
	 * @param includedirectory
	 *            <code>boolean</code> indicating whether to include the directory base path in the result subset or
	 *            not.
	 * @return The submap which only contains entries with keys that start with the specified directory base path.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <V> ConcurrentNavigableMap<SakerPath, V> getPathSubMapDirectoryChildren(
			ConcurrentNavigableMap<SakerPath, V> paths, SakerPath dirpath, boolean includedirectory)
			throws NullPointerException {
		ObjectUtils.requireNaturalOrder(paths);
		Objects.requireNonNull(dirpath, "dir path");

		return paths.subMap(dirpath, includedirectory, dirpath.nextSiblingPathInNaturalOrder(), false);
	}

	/**
	 * Checks if the specified path set doesn't contain paths which have different roots.
	 * <p>
	 * The argument must be naturally ordered.
	 * <p>
	 * If the argument is empty, then <code>false</code> is returned as it satisfies the condition.
	 * 
	 * @param paths
	 *            The paths.
	 * @return <code>true</code> if all of the paths in the set have the same root.
	 * @see SakerPath#getRoot()
	 */
	public static boolean hasDifferentRootedPaths(SortedSet<? extends SakerPath> paths) {
		if (paths == null) {
			return false;
		}
		ObjectUtils.requireNaturalOrder(paths);

		if (paths.isEmpty()) {
			return false;
		}
		SakerPath first = paths.first();
		SakerPath last = paths.last();
		return !Objects.equals(first.getRoot(), last.getRoot());
	}

	/**
	 * Checks if the argument pahts set contains the specified path, or any parent path of it.
	 * <p>
	 * The argument must be naturally ordered.
	 * 
	 * @param paths
	 *            The paths.
	 * @param path
	 *            The path to search for.
	 * @return <code>true</code> if the set contains the path or any of its parent.
	 */
	public static boolean hasPathOrParent(NavigableSet<SakerPath> paths, SakerPath path) {
		return getPathOrParent(paths, path) != null;
	}

	/**
	 * Gets the path or any of its parent which is contained in a set for a specified path.
	 * <p>
	 * The argument must be naturally ordered.
	 * 
	 * @param paths
	 *            The paths.
	 * @param path
	 *            The path to search for.
	 * @return The found path which is either the same as the argument path, or any parent of it, or <code>null</code>
	 *             if not found.
	 */
	public static SakerPath getPathOrParent(NavigableSet<SakerPath> paths, SakerPath path) {
		if (paths == null || path == null) {
			return null;
		}
		ObjectUtils.requireNaturalOrder(paths);
		requireAbsolutePath(path);

		while (true) {
			SakerPath floor = paths.floor(path);
			if (floor == null) {
				return null;
			}
			SakerPath common = floor.getCommonSubPath(path);
			if (common == floor) {
				//path starts with floor
				return common;
			}
			if (common != null) {
				path = common;
				continue;
			}
			return null;
		}
	}

	/**
	 * Gets the entry for the path path or any of its parent which is contained in a map for a specified path.
	 * <p>
	 * The argument must be naturally ordered.
	 * 
	 * @param <V>
	 *            The value type of the map.
	 * @param paths
	 *            The paths.
	 * @param path
	 *            The path to search for.
	 * @return The found entry for the path which is either the same as the argument path, or any parent of it, or
	 *             <code>null</code> if not found.
	 */
	public static <V> Entry<SakerPath, V> getPathOrParentEntry(NavigableMap<SakerPath, V> paths, SakerPath path) {
		if (paths == null || path == null) {
			return null;
		}
		ObjectUtils.requireNaturalOrder(paths);
		requireAbsolutePath(path);

		while (true) {
			Entry<SakerPath, V> fentry = paths.floorEntry(path);
			if (fentry == null) {
				return null;
			}
			SakerPath floor = fentry.getKey();
			SakerPath common = floor.getCommonSubPath(path);
			if (common == floor) {
				//path starts with floor
				return fentry;
			}
			if (common != null) {
				path = common;
				continue;
			}
			return null;
		}
	}

	/**
	 * Checks if a specified file path is a direct children of a directory path.
	 * <p>
	 * This method returns <code>true</code> if and only if the file path starts with the directory path, and the path
	 * name count of the file is one greater than the directory path name count.
	 * 
	 * @param directory
	 *            The directory path.
	 * @param file
	 *            The file path.
	 * @return <code>true</code>, if the path for the file is a direct child path of the directory.
	 */
	public static boolean isPathInDirectory(SakerPath directory, SakerPath file) {
		if (directory == null || file == null) {
			return false;
		}
		return file.startsWith(directory) && file.getNameCount() == directory.getNameCount() + 1;
	}

	/**
	 * Checks if the argument sorted path set has any non-forward relative path.
	 * <p>
	 * If there is an absolute path in the set, the result will be <code>true</code>.
	 * <p>
	 * Empty argument will result in <code>false</code>.
	 * <p>
	 * In any other cases if there is a path in it that starts with <code>".."</code>, <code>true</code> is returned.
	 * 
	 * @param paths
	 *            The paths to examine.
	 * @return <code>true</code> if there is at least one not forward relative path in the set.
	 * @see SakerPath#isForwardRelative()
	 */
	public static boolean hasNonForwardRelative(SortedSet<SakerPath> paths) {
		if (paths == null) {
			return false;
		}
		ObjectUtils.requireNaturalOrder(paths);

		if (paths.isEmpty()) {
			return false;
		}
		if (paths.last().isAbsolute()) {
			//found an absolute path 
			//   absolute paths are not forward relative.
			return true;
		}

		SortedSet<SakerPath> tail = paths.subSet(SakerPath.PARENT, SakerPath.PARENT.nextSiblingPathInNaturalOrder());
		if (tail.isEmpty()) {
			//there is no path in the set that starts with ..
			return false;
		}
		return true;
	}

	/**
	 * Checks if the argument sorted path set consists only of forward relative paths.
	 * <p>
	 * If the argument is empty, the result is <code>true</code>.
	 * <p>
	 * If the argument contains an absolute path, the result is <code>false</code>.
	 * <p>
	 * If the argument contains a path that is not forward relative, the result is <code>false</code>.
	 * 
	 * @param paths
	 *            The paths to examine.
	 * @return <code>true</code> if all of the paths in the argument set are forward relative.
	 * @see SakerPath#isForwardRelative()
	 */
	public static boolean isOnlyForwardRelatives(SortedSet<SakerPath> paths) {
		if (paths == null) {
			return false;
		}
		ObjectUtils.requireNaturalOrder(paths);

		if (paths.isEmpty()) {
			return true;
		}
		if (paths.last().isAbsolute()) {
			//found an absolute path 
			//   absolute paths are not forward relative.
			return false;
		}
		SortedSet<SakerPath> tail = paths.subSet(SakerPath.PARENT, SakerPath.PARENT.nextSiblingPathInNaturalOrder());
		if (!tail.isEmpty()) {
			//there is a path that starts with ".."
			return false;
		}
		return true;
	}

	/**
	 * Pretty prints a file hierarchy to the specified stream.
	 * <p>
	 * If the file is a directory, the children and their children recursively will be printed, and indented in the
	 * output.
	 * <p>
	 * The file name, its identity hash, and the classname is printed to the output.
	 * 
	 * @param ps
	 *            The stream.
	 * @param file
	 *            The file.
	 */
	public static final void prettyPrintRecursive(PrintStream ps, SakerFile file) {
		prettyPrintRecursive(ps, new StringBuilder(), file);
	}

	private static final void prettyPrintRecursive(PrintStream ps, StringBuilder tabs, SakerFile file) {
		ps.print(tabs.toString());
		ps.println(file.getName() + " 0x" + Integer.toHexString(System.identityHashCode(file)) + " "
				+ file.getClass().getName());
		if (file instanceof SakerDirectory) {
			tabs.append('|');
			tabs.append('\t');
			for (Entry<String, ? extends SakerFile> entry : ((SakerDirectory) file).getChildren().entrySet()) {
				SakerFile child = entry.getValue();
				prettyPrintRecursive(ps, tabs, child);
			}
		}
		tabs.setLength(tabs.length() - 2);
	}

	/**
	 * Comparator function for comparing the argument files by their name.
	 * 
	 * @param left
	 *            The first file.
	 * @param right
	 *            The second file.
	 * @return The comparion result of their names.
	 * @see String#compareTo(String)
	 */
	public static int nameComparator(SakerFile left, SakerFile right) {
		return left.getName().compareTo(right.getName());
	}
}
