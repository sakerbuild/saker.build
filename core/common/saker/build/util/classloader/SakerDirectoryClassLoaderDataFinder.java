package saker.build.util.classloader;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.task.TaskExecutionUtilities;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;

/**
 * {@link ClassLoaderDataFinder} implementation that is backed by a {@link SakerDirectory}.
 * <p>
 * Any resource requests will be resolved against the underlying directory.
 * <p>
 * This data finder should only be used when a build is running. It should <b>not</b> be used or cached during
 * subsequent builds or task invocations, as the underlying directories may be invalidated. It should <b>not</b> be
 * shared between different tasks.
 */
public class SakerDirectoryClassLoaderDataFinder implements ClassLoaderDataFinder {
	/**
	 * The task utilities for the task.
	 */
	protected final TaskExecutionUtilities taskUtils;
	/**
	 * The directory which is used for finding resources.
	 */
	protected final SakerDirectory directory;

	/**
	 * Creates a new instance for the given directory.
	 * 
	 * @param taskUtils
	 *            The task utilities that the data finder can use.
	 * @param directory
	 *            The directory to use for resource requests.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public SakerDirectoryClassLoaderDataFinder(TaskExecutionUtilities taskUtils, SakerDirectory directory)
			throws NullPointerException {
		Objects.requireNonNull(taskUtils, "task utils");
		Objects.requireNonNull(directory, "directory");
		this.taskUtils = taskUtils;
		this.directory = directory;
	}

	@Override
	public Supplier<ByteSource> getResource(String name) {
		SakerPath path = SakerPath.valueOf(name);
		if (!path.isForwardRelative() || !path.isRelative()) {
			return null;
		}
		SakerFile f = taskUtils.resolveAtRelativePath(directory, path);
		if (f == null) {
			return null;
		}
		return () -> {
			try {
				return f.openByteSource();
			} catch (IOException e) {
			}
			return null;
		};
	}

	@Override
	public ByteSource getResourceAsStream(String name) {
		SakerPath path = SakerPath.valueOf(name);
		if (!path.isForwardRelative() || !path.isRelative()) {
			return null;
		}
		SakerFile f = taskUtils.resolveAtRelativePath(directory, path);
		if (f == null) {
			return null;
		}
		try {
			return f.openByteSource();
		} catch (IOException e) {
		}
		return null;
	}

	@Override
	public ByteArrayRegion getClassBytes(String classname) {
		String classpathname = classname.replace('.', '/').concat(".class");
		SakerPath path = SakerPath.valueOf(classpathname);
		if (!path.isForwardRelative() || !path.isRelative()) {
			return null;
		}
		SakerFile f = taskUtils.resolveAtRelativePath(directory, path);
		if (f == null) {
			return null;
		}
		try {
			return f.getBytes();
		} catch (IOException e) {
		}
		return null;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "[" + directory + "]";
	}
}
