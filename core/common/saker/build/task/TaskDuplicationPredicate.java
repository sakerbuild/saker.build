package saker.build.task;

/**
 * Functional interface for checking if the associated task duplication should continue.
 * <p>
 * This interface is used when starting inner tasks in the build system. They can be duplicated to be executed multiple
 * times and an instance of this interface can be used to specify the nature of this duplication.
 * <p>
 * The {@link #shouldInvokeOnceMore()} function is called by the build system to determine if the associated task should
 * be invoked once more.
 */
@FunctionalInterface
public interface TaskDuplicationPredicate {
	/**
	 * Checks if the associated task should be invoked once more.
	 * <p>
	 * This method should check if there is anything left to be done for duplicated tasks. If so, return
	 * <code>true</code> in which case a new instance of the task will be started and executed.
	 * <p>
	 * If this method returns <code>false</code>, it should return <code>false</code> for any further calls to it.
	 * 
	 * @return <code>true</code> if the associated task should be invoked once more.
	 * @throws RuntimeException
	 *             If the predicate fails to determine its result. Implementations should <b>not</b> directly throw this
	 *             exception, but return <code>false</code> instead. The handling of the exception is implementation
	 *             dependent to the build system, but usually will be treated as if the associated task threw an
	 *             exception.
	 */
	public boolean shouldInvokeOnceMore() throws RuntimeException;
}