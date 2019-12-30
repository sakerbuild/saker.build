package saker.build.task;

import java.util.Map;

import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredMapTaskResult;

/**
 * Interface representing the result of a build target task.
 * <p>
 * Build target tasks can have outputs which are explicitly named. This interface provides access to the outputs of a
 * build target by mapping them to their respective task identifiers.
 * <p>
 * This interface is similar in functionality to {@link StructuredMapTaskResult}, but is specifically declared for build
 * target task results.
 * 
 * @see SimpleBuildTargetTaskResult
 */
public interface BuildTargetTaskResult {
	/**
	 * Gets the build target results.
	 * 
	 * @return An unmodifiable map of build target result names mapped to their task identifiers.
	 */
	public Map<String, TaskIdentifier> getTaskResultIdentifiers();
}
