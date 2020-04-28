package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.internal.scripting.language.SakerScriptTargetConfiguration;
import saker.build.internal.scripting.language.task.result.SakerScriptTaskDefaults;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.TargetConfiguration;
import saker.build.runtime.params.InvalidBuildConfigurationException;
import saker.build.task.BuildTargetTaskFactory;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionParameters;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.BuildFileTargetTaskIdentifier;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class DefaultsLoaderTaskFactory
		implements TaskFactory<SakerScriptTaskDefaults>, Task<SakerScriptTaskDefaults>, TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath defaultsFilePath;

	/**
	 * For {@link Externalizable}.
	 */
	public DefaultsLoaderTaskFactory() {
	}

	public DefaultsLoaderTaskFactory(SakerPath defaultsFilePath) {
		Objects.requireNonNull(defaultsFilePath, "defaults file path");
		this.defaultsFilePath = defaultsFilePath;
	}

	@Override
	public SakerScriptTaskDefaults run(TaskContext taskcontext) throws Exception {
		SakerFile defaultsfile = taskcontext.getTaskUtilities().resolveFileAtPath(defaultsFilePath);
		if (defaultsfile == null) {
			taskcontext.reportInputFileDependency(null, defaultsFilePath, CommonTaskContentDescriptors.IS_NOT_FILE);
			taskcontext
					.abortExecution(new FileNotFoundException("Defaults file not found at path: " + defaultsFilePath));
			return null;
		}
		taskcontext.reportInputFileDependency(null, defaultsFilePath, defaultsfile.getContentDescriptor());

		TargetConfiguration targetconfig = taskcontext.getExecutionContext().getTargetConfiguration(taskcontext,
				defaultsfile);
		if (!(targetconfig instanceof SakerScriptTargetConfiguration)) {
			throw new InvalidBuildConfigurationException(
					"Defaults file is configured with different scripting language than SakerScript.");
		}
		SakerScriptTargetConfiguration sakertargetconfig = (SakerScriptTargetConfiguration) targetconfig;
		sakertargetconfig.validateDefaultsFile();

		BuildTargetTaskFactory expressions = sakertargetconfig.getImplicitBuildTarget();
		TaskExecutionParameters execparams = new TaskExecutionParameters();
		execparams.setWorkingDirectory(this.defaultsFilePath.getParent());

		BuildFileTargetTaskIdentifier rootbuildid = new BuildFileTargetTaskIdentifier("build", defaultsFilePath,
				execparams.getWorkingDirectory(), null);
		taskcontext.startTask(rootbuildid, expressions, execparams);

		SakerScriptTaskDefaults result = SakerScriptTaskDefaults.createAndStartParameterTasks(taskcontext,
				sakertargetconfig, new GlobalExpressionScopeRootTaskIdentifier(defaultsFilePath),
				execparams.getWorkingDirectory());
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public Task<? extends SakerScriptTaskDefaults> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(defaultsFilePath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		defaultsFilePath = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((defaultsFilePath == null) ? 0 : defaultsFilePath.hashCode());
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
		DefaultsLoaderTaskFactory other = (DefaultsLoaderTaskFactory) obj;
		if (defaultsFilePath == null) {
			if (other.defaultsFilePath != null)
				return false;
		} else if (!defaultsFilePath.equals(other.defaultsFilePath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "DefaultsLoaderTaskFactory[defaultsFilePath=" + defaultsFilePath + "]";
	}

}
