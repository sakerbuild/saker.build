package saker.build.scripting.model.info;

import java.util.Collection;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.task.TaskName;

/**
 * Provides information about a specific task implementation.
 * <p>
 * Underlying data in this interface can be lazy loaded similarly to {@link ExternalScriptInformationProvider}.
 * 
 * @see SimpleTaskInformation
 */
public interface TaskInformation extends InformationHolder {
	/**
	 * Gets the name of the task.
	 * 
	 * @return The task name.
	 */
	public TaskName getTaskName();

	/**
	 * Gets the return type information of the task.
	 * 
	 * @return The return type information or <code>null</code> if not available or still loading.
	 */
	public default TypeInformation getReturnType() {
		return null;
	}

	/**
	 * Gets documentational information about the task.
	 * 
	 * @return The information about the task or <code>null</code> if not available or still loading.
	 */
	@Override
	public default FormattedTextContent getInformation() {
		return null;
	}

	/**
	 * Gets information about the parameters of this task.
	 * <p>
	 * Each task can have only one parameter with a specific name. In other words, it is invalid for a task to have
	 * multiple parameters with the same name. See {@link TaskParameterInformation#getParameterName()}.
	 * 
	 * @return Collection of parameter informations for this task or <code>null</code> if not available or still
	 *             loading.
	 */
	public default Collection<? extends TaskParameterInformation> getParameters() {
		return null;
	}

	/**
	 * Gets if the task is deprecated.
	 * 
	 * @return <code>true</code> if the task is deprecated.
	 */
	public default boolean isDeprecated() {
		return false;
	}
}