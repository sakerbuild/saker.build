package saker.build.task;

import java.util.NavigableSet;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.TargetConfiguration;

/**
 * {@link TaskFactory} specialization for representing build targets which are the basic root tasks in build scripts.
 * <p>
 * Build targets are the result of parsing a build script for the build system.
 * <p>
 * Build targets may optionally have parameters, which can be used to configure invocations of it. They are generally
 * used when the user specifies a script to include another build targets, and pass some parameters to it.
 * <p>
 * Build targets create instances of {@link BuildTargetTask} as their task implementations.
 * 
 * @see TargetConfiguration
 * @see BuildTargetTask
 */
public interface BuildTargetTaskFactory extends TaskFactory<BuildTargetTaskResult> {
	@Override
	public BuildTargetTask createTask(ExecutionContext executioncontext);

	/**
	 * Gets the possible parameter names for this build target.
	 * <p>
	 * This method is mostly informational, as the build system currently only uses it to report a warning if the build
	 * target was invoked with a parameter that the build target doesn't publish.
	 * <p>
	 * Implementations can return <code>null</code> to signal that the parameter names are not available for this build
	 * target. Returning <code>null</code> will disable parameter related warnings for this build target.
	 * 
	 * @return An unmodifiable set of parameter names or <code>null</code> if this query is unsupported.
	 */
	public NavigableSet<String> getTargetInputParameterNames();
}
