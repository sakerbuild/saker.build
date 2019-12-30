package saker.build.scripting;

import java.io.Externalizable;

import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Interface providing information about various aspects of a parsed build script.
 * <p>
 * Implementations are strongly recommended to implement the {@link Externalizable} interface as well.
 * <p>
 * This interface is used by the build system to construct script stack traces if a script task reports an error.
 */
public interface ScriptInformationProvider {
	/**
	 * Gets the position of a task in the script.
	 * <p>
	 * This method can be used to look up the location of a task with a given identifier in the build script.
	 * 
	 * @param taskid
	 *            The task identifier to look up.
	 * @return The script position of the specified task or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If the task identifier is <code>null</code>.
	 */
	public ScriptPosition getScriptPosition(@RMISerialize TaskIdentifier taskid) throws NullPointerException;

	/**
	 * Gets the script position of a build target in the script.
	 * 
	 * @param name
	 *            The name of the build target.
	 * @return The script position of the specified target or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If the name is <code>null</code>.
	 */
	public ScriptPosition getTargetPosition(String name) throws NullPointerException;
}
