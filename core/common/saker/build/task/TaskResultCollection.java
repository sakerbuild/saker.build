package saker.build.task;

import java.util.Collection;
import java.util.Set;

import saker.apiextract.api.PublicApi;
import saker.build.ide.configuration.IDEConfiguration;
import saker.build.task.exception.TaskExecutionException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIArrayListSerializeElementWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIHashSetSerializableElementWrapper;

/**
 * Collection class for enclosing the task results of a build execution.
 * <p>
 * This class holds the executed tasks and their corresponding results.
 * <p>
 * The {@link #getTaskIds()} function returns all of the task identifiers that this collection holds. The
 * {@link #getTaskResult(TaskIdentifier)} function can be used to retrieve the results for a given task.
 */
@PublicApi
public interface TaskResultCollection extends TaskResultResolver {
	/**
	 * Gets the task identifiers which are present in this task result collection.
	 * <p>
	 * Only task identifiers are returned which were run in the associated build execution.
	 * 
	 * @return An unmodifiable set of task identifiers.
	 */
	@RMICacheResult
	@RMIWrap(RMIHashSetSerializableElementWrapper.class)
	public Set<? extends TaskIdentifier> getTaskIds();

	/**
	 * Gets the collection of IDE configurations which are present in this result collection.
	 * 
	 * @return An unmodifiable collection of IDE configurations.
	 */
	@RMICacheResult
	@RMIWrap(RMIArrayListSerializeElementWrapper.class)
	public Collection<? extends IDEConfiguration> getIDEConfigurations();

	/**
	 * Looks up the task result for the given task id.
	 * <p>
	 * If the task is not found, {@link IllegalArgumentException} is thrown. If the task didn't finish successfully, a
	 * {@link TaskExecutionException} is thrown.
	 * 
	 * @see #getTaskIds()
	 */
	@Override
	public Object getTaskResult(TaskIdentifier taskid) throws TaskExecutionException, IllegalArgumentException;
}
