package saker.build.runtime.execution;

import java.util.Set;

import saker.build.scripting.ScriptParsingOptions;
import saker.build.task.BuildTargetTaskFactory;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIArrayListStringElementWrapper;

/**
 * Target configuration is a container interface for build targets.
 * <p>
 * Build targets are root tasks in build scripts. Build targets are identified by a {@link String} name.
 * <p>
 * Target configuration instances are the results of parsing build scripts.
 */
public interface TargetConfiguration {
	/**
	 * Gets the build target task factory for the specified build target name.
	 * 
	 * @param target
	 *            The build target name.
	 * @return The build target for the given name or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If target is <code>null</code>.
	 */
	public BuildTargetTaskFactory getTask(String target) throws NullPointerException;

	/**
	 * Gets the names of the build targets contained in <code>this</code> instance.
	 * 
	 * @return An unmodifiable set of build target names. Doesn't contain <code>null</code>.
	 */
	@RMIWrap(RMIArrayListStringElementWrapper.class)
	@RMICacheResult
	public Set<String> getTargetNames();

	/**
	 * Gets the parsing options that were used for the parsing of this target configuration.
	 * 
	 * @return The parsing options.
	 */
	@RMICacheResult
	public ScriptParsingOptions getParsingOptions();
}
