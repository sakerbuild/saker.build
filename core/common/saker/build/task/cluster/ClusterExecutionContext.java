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
package saker.build.task.cluster;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.UUID;

import saker.build.exception.FileMirroringUnavailableException;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.runtime.execution.FileMirrorHandler;
import saker.build.runtime.execution.InternalExecutionContext;
import saker.build.runtime.execution.ScriptAccessorClassPathData;
import saker.build.runtime.execution.TargetConfiguration;
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
import saker.build.trace.InternalBuildTrace;

class ClusterExecutionContext implements ExecutionContext, InternalExecutionContext {
	private final SakerEnvironment environment;
	private final ExecutionContext realExecutionContext;

	private final FileMirrorHandler mirrorHandler;
	private final Map<String, ? extends BuildRepository> loadedRepositories;
	private final Map<ScriptProviderLocation, ? extends ScriptAccessorClassPathData> loadedScriptProviders;

	public ClusterExecutionContext(SakerEnvironment environment, ExecutionContext realExecutionContext,
			Map<String, ? extends BuildRepository> loadedRepositories, FileMirrorHandler mirrorhandler,
			Map<ScriptProviderLocation, ? extends ScriptAccessorClassPathData> loadedscriptlocators) {
		this.environment = environment;
		this.realExecutionContext = realExecutionContext;
		this.loadedRepositories = loadedRepositories;
		this.mirrorHandler = mirrorhandler;
		this.loadedScriptProviders = loadedscriptlocators;
	}

//	public Path mirror(SakerPath filepath, SakerFile file, DirectoryVisitPredicate synchpredicate) throws IOException {
//		return getMirrorHandlerThrow().mirror(filepath, file, synchpredicate);
//	}

	public Path mirror(SakerFile file, DirectoryVisitPredicate synchpredicate) throws IOException {
		return getMirrorHandlerThrow().mirror(file, synchpredicate);
	}

	public Path mirror(SakerPath filepath, SakerFile file, DirectoryVisitPredicate synchpredicate,
			ContentDescriptor filecontents) throws IOException {
		return getMirrorHandlerThrow().mirror(filepath, file, synchpredicate, filecontents);
	}

	@Override
	public Path toMirrorPath(SakerPath path) throws FileMirroringUnavailableException {
		return getMirrorHandlerThrow().toMirrorPath(path);
	}

	@Override
	public SakerPath toUnmirrorPath(Path path) {
		if (mirrorHandler == null) {
			return null;
		}
		return mirrorHandler.toUnmirrorPath(path);
	}

	@Override
	public Path getMirrorDirectory() {
		if (mirrorHandler == null) {
			return null;
		}
		return mirrorHandler.getMirrorDirectory();
	}

	@Override
	public <T> T getExecutionPropertyCurrentValue(ExecutionProperty<T> executionproperty) {
		return realExecutionContext.getExecutionPropertyCurrentValue(executionproperty);
	}

	@Override
	public SakerEnvironment getEnvironment() {
		return environment;
	}

	@Override
	public SakerDirectory getExecutionWorkingDirectory() {
		return realExecutionContext.getExecutionWorkingDirectory();
	}

	@Override
	public SakerDirectory getExecutionBuildDirectory() {
		return realExecutionContext.getExecutionBuildDirectory();
	}

	@Override
	public TargetConfiguration getTargetConfiguration(TaskContext taskcontext, SakerFile file)
			throws IOException, ScriptParsingFailedException {
		return realExecutionContext.getTargetConfiguration(taskcontext, file);
	}

	@Override
	public ExecutionRepositoryConfiguration getRepositoryConfiguration() {
		return realExecutionContext.getRepositoryConfiguration();
	}

	@Override
	public ExecutionPathConfiguration getPathConfiguration() {
		return realExecutionContext.getPathConfiguration();
	}

	@Override
	public ExecutionScriptConfiguration getScriptConfiguration() {
		return realExecutionContext.getScriptConfiguration();
	}

	@Override
	public Map<String, ? extends BuildRepository> getLoadedRepositories() {
		return loadedRepositories;
	}

	@Override
	public ScriptAccessProvider getLoadedScriptAccessProvider(ScriptProviderLocation location) {
		ScriptAccessorClassPathData data = loadedScriptProviders.get(location);
		if (data == null) {
			return null;
		}
		return data.getScriptAccessor();
	}

	@Override
	public long getBuildTimeMillis() {
		return realExecutionContext.getBuildTimeMillis();
	}

	@Override
	public NavigableMap<String, ? extends SakerDirectory> getRootDirectories() {
		return realExecutionContext.getRootDirectories();
	}

	@Override
	public boolean isIDEConfigurationRequired() {
		return realExecutionContext.isIDEConfigurationRequired();
	}

	@Override
	public Map<String, String> getUserParameters() {
		return realExecutionContext.getUserParameters();
	}

	@Override
	public ContentDescriptor getContentDescriptor(ProviderHolderPathKey pathkey) throws NullPointerException {
		//XXX handle specially if the path key is associated with the local file system?
		return realExecutionContext.getContentDescriptor(pathkey);
	}

	@Override
	public EnvironmentSelectionResult testEnvironmentSelection(TaskExecutionEnvironmentSelector environmentselector,
			Set<UUID> allowedenvironmentids) throws NullPointerException {
		return realExecutionContext.testEnvironmentSelection(environmentselector, allowedenvironmentids);
	}

	@Override
	public SakerPath getExecutionWorkingDirectoryPath() {
		return realExecutionContext.getExecutionWorkingDirectoryPath();
	}

	@Override
	public SakerPath getExecutionBuildDirectoryPath() {
		return realExecutionContext.getExecutionBuildDirectoryPath();
	}

	@Override
	public NavigableSet<String> getRootDirectoryNames() {
		return realExecutionContext.getRootDirectoryNames();
	}

	@Override
	public FilePathContents internalGetFilePathContents(SakerFile file) {
		return ((InternalExecutionContext) realExecutionContext).internalGetFilePathContents(file);
	}

	@Override
	public InternalBuildTrace internalGetBuildTrace() {
		return ((InternalExecutionContext) realExecutionContext).internalGetBuildTrace();
	}

	private FileMirrorHandler getMirrorHandlerThrow() throws FileMirroringUnavailableException {
		if (mirrorHandler == null) {
			throw new FileMirroringUnavailableException("No mirror handler specified.");
		}
		return mirrorHandler;
	}
}