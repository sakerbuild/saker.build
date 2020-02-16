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
package saker.build.runtime.execution;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import saker.build.exception.FileMirroringUnavailableException;
import saker.build.exception.InvalidPathFormatException;
import saker.build.exception.MissingConfigurationException;
import saker.build.exception.PropertyComputationFailedException;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.ide.configuration.IDEConfiguration;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptOptionsConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.runtime.repository.BuildRepository;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.TaskContext;
import saker.build.task.TaskDirectoryContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.delta.DeltaType;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeMapWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeSetSerializeElementWrapper;
import saker.build.trace.BuildTrace;
import saker.build.util.property.IDEConfigurationRequiredExecutionProperty;
import saker.build.util.property.ScriptParsingConfigurationExecutionProperty;

/**
 * Provides access to executional services provided by the build runtime.
 * <p>
 * Execution context is to be considered as a parent of {@link TaskContext}, it is created for each build execution.
 * <p>
 * The execution context provides the following functionalities:
 * <ul>
 * <li>Accessing the build configuration</li>
 * <li>Managing script target configurations</li>
 * <li>Evaluating execution properties</li>
 * <li>Providing access to loaded repositories</li>
 * <li>Root directory management</li>
 * <li>Execution-wide related features (e.g. appropriate caching)</li>
 * <li>Other executional services which have a longer lifetime than task execution, but do not necessarily outlive
 * execution lifetime</li>
 * </ul>
 * The execution context is used to retrieve target configurations for build scripts. It does appropriate caching to
 * ensure validity during the execution. See {@link #getTargetConfiguration(TaskContext, SakerFile)} for more
 * information.
 * <p>
 * {@linkplain ExecutionProperty Execution properties} can be evaluated using the execution context, which will be
 * cached during the lifetime of the execution.
 * <p>
 * The execution context holds references to the loaded build repositores for this execution. They are mapped to their
 * {@link String} identifiers specified by user configuration. See {@link #getLoadedRepositories()}.
 * <p>
 * The execution context provides access to the root directories of the in-memory file hierarchy. They should be used
 * tasks to handle file in- and output. The root directories are determined based on the user provided path
 * configuration. See {@link ExecutionContext#getRootDirectories()}.
 * <p>
 * Clients should not implement this interface.
 */
public interface ExecutionContext extends ExecutionDirectoryContext, ExecutionDirectoryPathContext {
	/**
	 * Gets the environment for the current execution.
	 * <p>
	 * For remote execution: The environment is always on the same machine as the currently running task, meaning that
	 * the build environment will be replicated for every PC that is used as a cluster.
	 * 
	 * @return The environment.
	 */
	@RMIForbidden
	public SakerEnvironment getEnvironment();

	/**
	 * Gets the target configuration for a given file based on the execution configuration.
	 * <p>
	 * This method can be used to retrieve a build target configuration for a given build script file.
	 * <p>
	 * When the target configuration is constructed, the current script configuration will be taken into account.
	 * <p>
	 * The target configurations are cached during an execution, which means that retrieving the target configuration
	 * for files that bear the same path will result in the same configurations returned, even if the files have changed
	 * during the two queries. This caching ensures that invoking a target from a given script at a path will run the
	 * same build steps even if the build files were changed meanwhile. This is required due to the fact that task
	 * execution is highly concurrent and ordering of tasks should not have an effect of executed build targets.
	 * <p>
	 * This method will report an execution dependency at the file path for the used script options. If the script
	 * configuration for the execution changes, expect a rerun for the task with a
	 * {@link DeltaType#EXECUTION_PROPERTY_CHANGED} delta with the property class of
	 * {@link ScriptParsingConfigurationExecutionProperty} .<br>
	 * The method will not report an input dependency on the file.
	 * 
	 * @param taskcontext
	 *            The task context of the querying task.
	 * @param file
	 *            The file to get the target configuration for.
	 * @return The parsed target configuration,
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ScriptParsingFailedException
	 *             If the parsing of the file failed.
	 * @throws NullPointerException
	 *             If any of the parameters are <code>null</code>.
	 * @throws MissingConfigurationException
	 *             If there is no corresponding script configuration for the file.
	 * @throws InvalidPathFormatException
	 *             If the path of the file is relative.
	 * @see ExecutionScriptConfiguration
	 */
	@RMISerialize
	public TargetConfiguration getTargetConfiguration(TaskContext taskcontext, SakerFile file)
			throws IOException, ScriptParsingFailedException, NullPointerException, MissingConfigurationException,
			InvalidPathFormatException;

	/**
	 * Gets the script configuration which was used to start this build execution.
	 * 
	 * @return The script configuration.
	 */
	@RMICacheResult
	public ExecutionScriptConfiguration getScriptConfiguration();

	/**
	 * Gets the repository configuration which was used to start this build execution.
	 * 
	 * @return The repository configuration.
	 */
	@RMICacheResult
	public ExecutionRepositoryConfiguration getRepositoryConfiguration();

	/**
	 * Gets the path configuration which was used to start this build execution.
	 * <p>
	 * Tasks are not recommended to interact with the result of this function, but handle its in- and outputs using the
	 * in-memory file hierarchy. See {@link ExecutionDirectoryContext} and {@link TaskDirectoryContext}.
	 * 
	 * @return The path configuration.
	 * @see #getRootDirectories()
	 * @see TaskContext#getTaskWorkingDirectory()
	 */
	@RMICacheResult
	public ExecutionPathConfiguration getPathConfiguration();

	/**
	 * Gets if the task implementations are recommended to provide configurations for IDE usage.
	 * 
	 * @return <code>true</code> if reporting IDE configurations is recommended.
	 * @see IDEConfiguration
	 * @see TaskContext#reportIDEConfiguration(IDEConfiguration)
	 * @see IDEConfigurationRequiredExecutionProperty
	 */
	@RMICacheResult
	public boolean isIDEConfigurationRequired();

	/**
	 * Gets the arbitrary user parameters used to start this build execution.
	 * 
	 * @return An unmodifiable map of user parameters.
	 */
	@RMICacheResult
	@RMIWrap(RMITreeMapWrapper.class)
	//XXX RMI wrap to be unmodifiable
	public Map<String, String> getUserParameters();

	/**
	 * Gets the current value of the parameter execution property.
	 * <p>
	 * The properties are cached during the lifetime of the execution.
	 * 
	 * @param executionproperty
	 *            The property.
	 * @return The current value of the property.
	 * @throws NullPointerException
	 *             If the property is <code>null</code>.
	 * @throws PropertyComputationFailedException
	 *             If the argument property throws an exception during the computation of its value. The thrown
	 *             exception is available through the {@linkplain PropertyComputationFailedException#getCause() cause}.
	 * @see TaskContext#reportExecutionDependency(ExecutionProperty, Object)
	 */
	@RMISerialize
	public <T> T getExecutionPropertyCurrentValue(@RMISerialize ExecutionProperty<T> executionproperty)
			throws NullPointerException, PropertyComputationFailedException;

	/**
	 * Gets the current date milliseconds for the build execution.
	 * <p>
	 * The representation is the same as {@link System#currentTimeMillis()}.
	 * 
	 * @return The date milliseconds of the execution.
	 */
	@RMICacheResult
	public long getBuildTimeMillis();

	/**
	 * Gets the mirror path for the argument execution path.
	 * <p>
	 * This method converts the parameter path to the same path that would be the result of the actual
	 * {@linkplain TaskContext#mirror(SakerFile, DirectoryVisitPredicate) mirroring}.
	 * 
	 * @param path
	 *            The path to get the mirror path for.
	 * @return The mirrored path.
	 * @throws IllegalArgumentException
	 *             If the path is not absolute or is not a valid path for the current execution.
	 * @throws NullPointerException
	 *             If path is <code>null</code>.
	 * @throws FileMirroringUnavailableException
	 *             If file mirroring is not available for this path.
	 * @see #toUnmirrorPath(Path)
	 */
	@RMIForbidden
	public Path toMirrorPath(SakerPath path)
			throws IllegalArgumentException, NullPointerException, FileMirroringUnavailableException;

	/**
	 * Converts a mirror path to execution path.
	 * <p>
	 * This is the inverse conversion of {@link #toMirrorPath(SakerPath)}. The parameter file system path will be
	 * converted to the corresponding execution path.
	 * 
	 * @param path
	 *            The path to convert to execution path.
	 * @return The converted path or <code>null</code> if the argument path has no corresponding execution path. This is
	 *             usually happens when the argument is not a subpath of {@link #getMirrorDirectory()}, or file
	 *             mirroring is not available.
	 * @throws InvalidPathFormatException
	 *             If path is not absolute.
	 */
	@RMIForbidden
	public SakerPath toUnmirrorPath(Path path) throws InvalidPathFormatException;

	/**
	 * Gets the mirror directory which was configured for this build execution.
	 * 
	 * @return The mirror directory or <code>null</code> if not available.
	 */
	@RMIForbidden
	public Path getMirrorDirectory();

	/**
	 * Gets the loaded repositories for this execution.
	 * <p>
	 * The repositories are mapped to their identifiers specified by the user. If an identifier was not set by the user,
	 * an automatically generated one is used.
	 * 
	 * @return An unmodifiable map of the loaded repositories for this execution.
	 */
	@RMIForbidden
	public Map<String, ? extends BuildRepository> getLoadedRepositories();

	/**
	 * Gets the loaded script language provider for the specifeid script provider location.
	 * <p>
	 * The script language providers are loaded based on the {@linkplain ExecutionScriptConfiguration execution script
	 * configuration} and the providers are accessible based on the script load location.
	 * <p>
	 * The location can be queried from the current script configuration ({@link #getScriptConfiguration()}) and the
	 * location can be retrieved from
	 * {@link ExecutionScriptConfiguration#getScriptOptionsConfig(SakerPath)}<code>.</code>{@link ScriptOptionsConfig#getProviderLocation()
	 * getProviderLocation()}.
	 * <p>
	 * Make sure callers report an appropriate execution property dependency when handling script providers. It is
	 * recommended that they use {@link ScriptParsingConfigurationExecutionProperty} for retrieving the requested
	 * resources.
	 * 
	 * @param location
	 *            The script provider location to get the loaded provider for.
	 * @return The script language provider or <code>null</code> if no language provider was loaded for the given
	 *             location.
	 */
	@RMIForbidden
	public ScriptAccessProvider getLoadedScriptAccessProvider(ScriptProviderLocation location);

	/**
	 * Gets the content descriptor of the file that is represented by the given argument path key.
	 * <p>
	 * The current content descriptor is retrieved that is tracked by the build system for the given file. The in-memory
	 * file hierarchy doesn't affect the content descriptor returned by this method.
	 * <p>
	 * This method can be used to retrieve content descriptors for files which are not part of the build execution. I.e.
	 * are not accessible via the execution path roots.
	 * <p>
	 * Usually, the returned descriptor will be constructed based on the database configuration. (Attribute, hash based
	 * descriptors, etc...) If a file synchronization already took place for the given file, then the synchronized
	 * content descriptor is returned, if the file hasn't been modified since.
	 * 
	 * @param pathkey
	 *            The path key for the file.
	 * @return The content descriptor for the file or <code>null</code> if the file doesn't exist.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	//TODO create bulk method as getContentDescriptor(SakerFileProvider, Set<SakerPath>)
	@RMISerialize
	public ContentDescriptor getContentDescriptor(ProviderHolderPathKey pathkey) throws NullPointerException;

	/**
	 * Tests the environment selection of a given selector the same way it is determined by the build system during
	 * execution.
	 * <p>
	 * This method can be used to test if a given task can be invoked with the given selector. In general, task
	 * implementations don't need to used this, however, there might be cases when it is appropriate and necessary to
	 * support incremental compilation.
	 * <p>
	 * One such scenario is when a task depends on the presence of an SDK installed on an executor machine. In this
	 * case, if the SDK gets removed or modified, the build system needs to trigger a reinvocation. In order to achieve
	 * this, the task implementation will need to install a dependency on an appropriate {@link ExecutionProperty} that
	 * determines if a given task can still run in the current build configuration. While this may not be necessary if
	 * the interested SDK is present on the coordinator machine, it can be crucial to support proper incremental
	 * compilation with build clusters in use.
	 * 
	 * @param environmentselector
	 *            The task environment selector to test.
	 * @param allowedenvironmentids
	 *            The allowed environment identifiers for the selection. See
	 *            {@link InnerTaskExecutionParameters#getAllowedClusterEnvironmentIdentifiers()}. <code>null</code> is
	 *            accepted.
	 * @return The result of environment selection as returned by the {@link TaskExecutionEnvironmentSelector}.
	 * @throws NullPointerException
	 *             If the environment selector is <code>null</code>.
	 * @throws TaskEnvironmentSelectionFailedException
	 *             If the environment selection fails.
	 */
	@RMISerialize
	public EnvironmentSelectionResult testEnvironmentSelection(
			@RMISerialize TaskExecutionEnvironmentSelector environmentselector,
			@RMIWrap(RMITreeSetSerializeElementWrapper.class) Set<UUID> allowedenvironmentids)
			throws NullPointerException, TaskEnvironmentSelectionFailedException;

	/**
	 * Gets if the current build execution records a build trace.
	 * <p>
	 * A build trace is a recording of various aspects of the build execution to be displayed later for the user.
	 * 
	 * @return <code>true</code> if a build trace is being recorded.
	 * @see BuildTrace
	 * @since 0.8.7
	 */
	@RMICacheResult
	public boolean isRecordsBuildTrace();
}
