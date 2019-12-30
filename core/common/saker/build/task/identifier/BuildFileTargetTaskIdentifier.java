package saker.build.task.identifier;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.task.BuildTargetTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.BuildTargetRunnerTaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * {@link TaskIdentifier} implementation for uniquely identifying a build target task invocation.
 * <p>
 * This class holds the name of the target, the path of the build script, the working directory for the invoked target,
 * and the passed parameters of the invoked build target. Using this task identifier to invoke build targets can be
 * useful to ensure that a given build target with a specific set of parameters is only invoked once per build
 * execution.
 * <p>
 * This class is to be used in conjunction with {@link BuildTargetRunnerTaskFactory}.
 */
@PublicApi
public final class BuildFileTargetTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	protected String targetName;
	protected SakerPath filePath;
	protected SakerPath workingDirectory;
	protected NavigableMap<String, ? extends TaskIdentifier> parameters;

	/**
	 * For {@link Externalizable}.
	 */
	public BuildFileTargetTaskIdentifier() {
	}

	/**
	 * Creates a new instance with the given arguments, and empty task parameters.
	 * 
	 * @param targetName
	 *            The name of the build target.
	 * @param filePath
	 *            The absolute path to the build script.
	 * @throws InvalidPathFormatException
	 *             If file path is not absolute.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public BuildFileTargetTaskIdentifier(String targetName, SakerPath filePath)
			throws InvalidPathFormatException, NullPointerException {
		this(targetName, filePath, Objects.requireNonNull(filePath, "file path").getParent(),
				Collections.emptyNavigableMap());
	}

	/**
	 * Creates a new instance with the given arguments and specified task parameters.
	 * 
	 * @param targetName
	 *            The name of the build target.
	 * @param filePath
	 *            The absolute path to the build script.
	 * @param workingDirectory
	 *            The absolute path to the working directory of the invoked target.
	 * @param parameters
	 *            The task parameters to pass to the invoked build target. May be <code>null</code> or empty.
	 * @throws InvalidPathFormatException
	 *             If file path or working directory is not absolute.
	 * @throws NullPointerException
	 *             If target name, file path or working directory is <code>null</code>.
	 * @see BuildTargetTask#initParameters(TaskContext, NavigableMap)
	 */
	public BuildFileTargetTaskIdentifier(String targetName, SakerPath filePath, SakerPath workingDirectory,
			NavigableMap<String, ? extends TaskIdentifier> parameters)
			throws InvalidPathFormatException, NullPointerException {
		Objects.requireNonNull(targetName, "target name");
		Objects.requireNonNull(filePath, "file path");
		SakerPathFiles.requireAbsolutePath(filePath);
		SakerPathFiles.requireAbsolutePath(workingDirectory);

		this.targetName = targetName;
		this.filePath = filePath;
		this.workingDirectory = workingDirectory;
		//protective copy
		this.parameters = ObjectUtils.isNullOrEmpty(parameters) ? Collections.emptyNavigableMap()
				: ImmutableUtils.makeImmutableNavigableMap(parameters);
	}

	/**
	 * Gets the build script path this class was initialized with.
	 * 
	 * @return The build script path.
	 */
	public SakerPath getFilePath() {
		return filePath;
	}

	/**
	 * Gets the name of the build target to invoke.
	 * 
	 * @return The build target name.
	 */
	public String getTargetName() {
		return targetName;
	}

	/**
	 * Gets the absolute path to the working directory.
	 * 
	 * @return The working directory.
	 */
	public SakerPath getWorkingDirectory() {
		return workingDirectory;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(targetName);
		out.writeObject(filePath);
		out.writeObject(workingDirectory);
		SerialUtils.writeExternalMap(out, parameters);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		targetName = in.readUTF();
		filePath = (SakerPath) in.readObject();
		workingDirectory = (SakerPath) in.readObject();
		parameters = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + filePath.hashCode();
		result = prime * result + parameters.hashCode();
		result = prime * result + targetName.hashCode();
		result = prime * result + workingDirectory.hashCode();
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
		BuildFileTargetTaskIdentifier other = (BuildFileTargetTaskIdentifier) obj;
		if (filePath == null) {
			if (other.filePath != null)
				return false;
		} else if (!filePath.equals(other.filePath))
			return false;
		if (parameters == null) {
			if (other.parameters != null)
				return false;
		} else if (!parameters.equals(other.parameters))
			return false;
		if (targetName == null) {
			if (other.targetName != null)
				return false;
		} else if (!targetName.equals(other.targetName))
			return false;
		if (workingDirectory == null) {
			if (other.workingDirectory != null)
				return false;
		} else if (!workingDirectory.equals(other.workingDirectory))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName());
		sb.append("(");
		sb.append(targetName);
		sb.append("@");
		sb.append(filePath);
		if (!parameters.isEmpty()) {
			sb.append(":parameters=");
			sb.append(parameters);
		}
		sb.append("):");
		sb.append(workingDirectory);
		return sb.toString();
	}

}
