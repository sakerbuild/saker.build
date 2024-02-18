package saker.build.internal.scripting.language.task;

import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.task.TaskContext;

/**
 * Allows retrieval of a task result without the need of actually starting the task.
 * 
 * @param <T>
 *            The result type.
 */
public interface DirectComputableSakerTaskFactory<T extends SakerTaskResult> {
	/**
	 * Computes the task result.
	 * 
	 * @param taskcontext
	 *            The context of the task in which this function is invoked. Not a task context for <code>this</code>
	 *            task instance.
	 * @return The computed result.
	 */
	public T directComputeTaskResult(TaskContext taskcontext);

	public boolean isDirectComputable();

	/**
	 * Converts the argument to a {@link DirectComputableSakerTaskFactory} instance by downcasting, and checking if the
	 * result is {@linkplain #isDirectComputable() direct computable}.
	 * 
	 * @param taskfactory
	 *            The task factory.
	 * @return The direct computable task factory or <code>null</code> if not direct computable.
	 */
	public static DirectComputableSakerTaskFactory<?> getDirectComputable(SakerTaskFactory taskfactory) {
		if (taskfactory instanceof DirectComputableSakerTaskFactory<?>) {
			DirectComputableSakerTaskFactory<?> dcfactory = (DirectComputableSakerTaskFactory<?>) taskfactory;
			if (dcfactory.isDirectComputable()) {
				return dcfactory;
			}
		}
		return null;
	}
}
