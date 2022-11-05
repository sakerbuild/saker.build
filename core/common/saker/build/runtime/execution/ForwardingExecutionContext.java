package saker.build.runtime.execution;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.UUID;

import saker.build.exception.FileMirroringUnavailableException;
import saker.build.exception.InvalidPathFormatException;
import saker.build.exception.MissingConfigurationException;
import saker.build.exception.PropertyComputationFailedException;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.runtime.repository.BuildRepository;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;

public class ForwardingExecutionContext implements ExecutionContext {
	protected final ExecutionContext executonContext;

	public ForwardingExecutionContext(ExecutionContext executonContext) {
		this.executonContext = executonContext;
	}

	@Override
	public SakerDirectory getExecutionWorkingDirectory() {
		return executonContext.getExecutionWorkingDirectory();
	}

	@Override
	public SakerDirectory getExecutionBuildDirectory() {
		return executonContext.getExecutionBuildDirectory();
	}

	@Override
	public NavigableMap<String, ? extends SakerDirectory> getRootDirectories() {
		return executonContext.getRootDirectories();
	}

	@Override
	public SakerPath getExecutionWorkingDirectoryPath() {
		return executonContext.getExecutionWorkingDirectoryPath();
	}

	@Override
	public SakerPath getExecutionBuildDirectoryPath() {
		return executonContext.getExecutionBuildDirectoryPath();
	}

	@Override
	public SakerEnvironment getEnvironment() {
		return executonContext.getEnvironment();
	}

	@Override
	public TargetConfiguration getTargetConfiguration(TaskContext taskcontext, SakerFile file)
			throws IOException, ScriptParsingFailedException, NullPointerException, MissingConfigurationException,
			InvalidPathFormatException {
		return executonContext.getTargetConfiguration(taskcontext, file);
	}

	@Override
	public ExecutionScriptConfiguration getScriptConfiguration() {
		return executonContext.getScriptConfiguration();
	}

	@Override
	public ExecutionRepositoryConfiguration getRepositoryConfiguration() {
		return executonContext.getRepositoryConfiguration();
	}

	@Override
	public ExecutionPathConfiguration getPathConfiguration() {
		return executonContext.getPathConfiguration();
	}

	@Override
	public boolean isIDEConfigurationRequired() {
		return executonContext.isIDEConfigurationRequired();
	}

	@Override
	public Map<String, String> getUserParameters() {
		return executonContext.getUserParameters();
	}

	@Override
	public <T> T getExecutionPropertyCurrentValue(ExecutionProperty<T> executionproperty)
			throws NullPointerException, PropertyComputationFailedException {
		return executonContext.getExecutionPropertyCurrentValue(executionproperty);
	}

	@Override
	public long getBuildTimeMillis() {
		return executonContext.getBuildTimeMillis();
	}

	@Override
	public Path toMirrorPath(SakerPath path)
			throws IllegalArgumentException, NullPointerException, FileMirroringUnavailableException {
		return executonContext.toMirrorPath(path);
	}

	@Override
	public SakerPath toUnmirrorPath(Path path) throws InvalidPathFormatException {
		return executonContext.toUnmirrorPath(path);
	}

	@Override
	public Path getMirrorDirectory() {
		return executonContext.getMirrorDirectory();
	}

	@Override
	public Map<String, ? extends BuildRepository> getLoadedRepositories() {
		return executonContext.getLoadedRepositories();
	}

	@Override
	public ScriptAccessProvider getLoadedScriptAccessProvider(ScriptProviderLocation location) {
		return executonContext.getLoadedScriptAccessProvider(location);
	}

	@Override
	public ContentDescriptor getContentDescriptor(ProviderHolderPathKey pathkey) throws NullPointerException {
		return executonContext.getContentDescriptor(pathkey);
	}

	@Override
	public EnvironmentSelectionResult testEnvironmentSelection(TaskExecutionEnvironmentSelector environmentselector,
			Set<UUID> allowedenvironmentids) throws NullPointerException, TaskEnvironmentSelectionFailedException {
		return executonContext.testEnvironmentSelection(environmentselector, allowedenvironmentids);
	}

	@Override
	public boolean isRecordsBuildTrace() {
		return executonContext.isRecordsBuildTrace();
	}
}
