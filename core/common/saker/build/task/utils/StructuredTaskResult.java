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
package saker.build.task.utils;

import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.task.TaskContext;
import saker.build.task.TaskDependencyFuture;
import saker.build.task.TaskFuture;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.dependencies.CommonTaskOutputChangeDetector;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

/**
 * Represents a special kind of task result which should be handled as a structured output with fields that haven't
 * completed yet.
 * <p>
 * Structured tasks results can serve as a placeholder result for a task and make their data partially available.
 * <p>
 * Sometimes tasks divide their work into multiple sub-tasks. In this case the result of a task can include values from
 * the sub-tasks. This would require from the parent task to wait all of its child worker tasks and then construct an
 * appropriate object that contains these results. This behaviour can hinder dependent tasks in their execution as this
 * can result in waiting for tasks which are unrelated to the work of the dependent task.
 * <p>
 * Take the following example:
 * <p>
 * Task <i>A</i> is a complex task that divides its work into separate partitions. Task <i>A</i> will return a complex
 * object as a result, with fields of <i>X</i> and <i>Y</i>, each corresponding to the result of its worker tasks. <br>
 * Task <i>B</i> has an input dependency on task <i>A</i>, and will use the field <i>X</i> of its result. This means
 * that task <i>B</i> will be stalled until task <i>A</i> finishes completely.
 * <p>
 * This is an undesirable scenario, as task <i>B</i> only needs the result of the sub-task <i>X</i>, while the long
 * running of sub-task <i>Y</i> can stall the execution of task <i>B</i>.
 * <p>
 * In order to avoid this, structured task results were introduced which are able to separate the concerns of unrelated
 * results contained by their parent.
 * <p>
 * With structured results, task <i>B</i> in the example above will not be stalled, as task <i>A</i> can return before
 * the completion of its sub-tasks. Task <i>A</i> returns a structured task results with the fields <i>X</i> and
 * <i>Y</i>, which have the values of the sub-task task identifiers. Task <i>B</i> will receive the structured result
 * from task <i>A</i>, and see the task identifier for sub-task <i>X</i>. It will then wait for sub-task <i>X</i>, and
 * proceed with its execution. We can see that in this case it was not necessary for task <i>B</i> to wait for the
 * completion of sub-task <i>Y</i> to get the result from sub-task <i>X</i>.
 * <p>
 * Tasks can choose to retrieve the actual objectified, non-structured result by calling
 * {@link #toResult(TaskResultResolver)}.
 * <p>
 * If a task doesn't want to handle structured task results (but it is still encouraged to to so), it can choose to call
 * {@link #getActualTaskResult} which converts any possible structured results to object representation.
 * <p>
 * The {@link TaskContext} instance for the tasks can be used for the {@link TaskResultResolver} parameter of the
 * methods in this interface.
 * <p>
 * Implementations are not required to implement {@link Object#hashCode()} and {@link Object#equals(Object)}, but if
 * they do, they must ensure that if two results equal, then they semantically provide access to the same results. The
 * actual end results are not required to equal, but the semantics along which they are accessed must. E.g. Two
 * {@link StructuredObjectTaskResult} can equal if their {@linkplain StructuredObjectTaskResult#getTaskIdentifier()
 * enclosed task identifier} equal, however, it is not required that the task outputs of the associated task identifiers
 * are equal.
 * <p>
 * Note: When constructing strucuted task results, make sure to not have circular references in them.
 * 
 * @see StructuredListTaskResult
 * @see StructuredMapTaskResult
 * @see StructuredObjectTaskResult
 * @see ComposedStructuredTaskResult
 */
@PublicApi
public interface StructuredTaskResult {
	/**
	 * Converts the structured result to plain object.
	 * <p>
	 * Calling this method will ensure that the return value will not have any structured task result related fields in
	 * it.
	 * 
	 * @param results
	 *            The results to resolve the task identifiers against.
	 * @return The objectified structured result represented by <code>this</code> instance.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws RuntimeException
	 *             In cases when the result cannot be computed properly. (E.g. task execution failure)
	 */
	public Object toResult(TaskResultResolver results) throws NullPointerException, RuntimeException;

	/**
	 * Gets the task result dependency handle to the plain result object.
	 * <p>
	 * The {@link TaskResultDependencyHandle#get()} method must return the semantically same object as
	 * {@link #toResult(TaskResultResolver)}.
	 * <p>
	 * The {@link TaskResultDependencyHandle#setTaskOutputChangeDetector(TaskOutputChangeDetector)} method may or may
	 * not report dependencies to the backing task. It may be very well that there's no backing task.
	 * 
	 * @param results
	 *            The results to resolve the task identifiers against.
	 * @return The result dependency handle to the result plain object.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public default TaskResultDependencyHandle toResultDependencyHandle(TaskResultResolver results)
			throws NullPointerException {
		return new SupplierTaskResultDependencyHandle(() -> toResult(results));
	}

	/**
	 * Gets the actual object result for the given task identifier.
	 * <p>
	 * The result of the specified task will be retrieved by calling
	 * {@link TaskResultResolver#getTaskResult(TaskIdentifier)}, and if the task result is an instance of
	 * {@link StructuredTaskResult}, it will be objectified by calling {@link #toResult(TaskResultResolver)}.
	 * 
	 * @param taskid
	 *            The task identifier to get the task result of.
	 * @param results
	 *            The task result resolver to retrieve the results from.
	 * @return The objectified task result for the specified identifier.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static Object getActualTaskResult(TaskIdentifier taskid, TaskResultResolver results)
			throws NullPointerException {
		Objects.requireNonNull(taskid, "task identifier");
		Objects.requireNonNull(results, "task result resolver");
		Object result = results.getTaskResult(taskid);
		if (result instanceof StructuredTaskResult) {
			return ((StructuredTaskResult) result).toResult(results);
		}
		return result;
	}

	/**
	 * Gets the actual object result for the given task future.
	 * <p>
	 * If the result of the specified task is an instance of {@link StructuredTaskResult}, it will be objectified by
	 * calling {@link #toResult(TaskResultResolver)} with the {@link TaskFuture#getTaskContext()} as a result resolver.
	 * 
	 * @param taskfuture
	 *            The future handle for the task.
	 * @return The objectified task result.
	 * @throws NullPointerException
	 *             If the future is <code>null</code>.
	 */
	public static Object getActualTaskResult(TaskFuture<?> taskfuture) throws NullPointerException {
		Objects.requireNonNull(taskfuture, "task future");
		Object result = taskfuture.get();
		if (result instanceof StructuredTaskResult) {
			return ((StructuredTaskResult) result).toResult(taskfuture.getTaskContext());
		}
		return result;
	}

	/**
	 * Gets the actual object result for the given task dependency future.
	 * <p>
	 * If the result of the specified task is an instance of {@link StructuredTaskResult}, it will be objectified by
	 * calling {@link #toResult(TaskResultResolver)} with the {@link TaskDependencyFuture#getTaskContext()} as a result
	 * resolver.
	 * 
	 * @param taskdependencyfuture
	 *            The task dependency future.
	 * @return The objectified task result.
	 * @throws NullPointerException
	 *             If the task dependency future is <code>null</code>.
	 */
	public static Object getActualTaskResult(TaskDependencyFuture<?> taskdependencyfuture) throws NullPointerException {
		Objects.requireNonNull(taskdependencyfuture, "task dependency future");
		Object result = taskdependencyfuture.get();
		if (result instanceof StructuredTaskResult) {
			return ((StructuredTaskResult) result).toResult(taskdependencyfuture.getTaskContext());
		}
		return result;
	}

	/**
	 * Gets the dependency handle to the actual object of task results associated with the given task identifier.
	 * <p>
	 * The method will get the dependency handle to the result object that would be retrieved using
	 * {@link #getActualTaskResult(TaskIdentifier, TaskResultResolver)}. If the result of the associated task is an
	 * instance of {@link StructuredTaskResult}, then the {@link #toResultDependencyHandle(TaskResultResolver)} method
	 * is used to retrieve the result.
	 * <p>
	 * The returned handle will installs a dependency for the task result of the associated task (with the argument task
	 * id) that signals a change if the {@link StructuredTaskResult} nature of the result changes.
	 * 
	 * @param taskid
	 *            The task identifier to get the task result of.
	 * @param results
	 *            The task result resolver to retrieve the results from.
	 * @return The dependency handle to the actual result of the given task.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static TaskResultDependencyHandle getActualTaskResultDependencyHandle(TaskIdentifier taskid,
			TaskResultResolver results) throws NullPointerException {
		Objects.requireNonNull(taskid, "task identifier");
		Objects.requireNonNull(results, "task result resolver");
		TaskResultDependencyHandle handle = results.getTaskResultDependencyHandle(taskid);
		return new SupplierForwardingTaskResultDependencyHandle(() -> {
			Object obj = handle.get();
			if (obj instanceof StructuredTaskResult) {
				//NOTE: we don't set CommonTaskOutputChangeDetector.isInstanceOf(StructuredTaskResult.class)
				//  but instead we set the equality change detector as changes in the structured result needs to be noticed
				handle.setTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(obj));
				return ((StructuredTaskResult) obj).toResultDependencyHandle(results);
			}
			handle.setTaskOutputChangeDetector(
					CommonTaskOutputChangeDetector.notInstanceOf(StructuredTaskResult.class));
			//clone the handle so any get() calls on the returned handle will install a dependency, and the one we've set
			//here doesn't break it
			return handle.clone();
		});
	}

	/**
	 * Resolves the intermediate task results of the argument if it's an instance of
	 * {@link ComposedStructuredTaskResult} or {@link StructuredObjectTaskResult}.
	 * <p>
	 * The function will check if the argument is a composed structured task result, and if so, it will resolve the
	 * intermediate task results until it's no longer a composed task result.
	 * <p>
	 * The function will also set appropriate task output change detectors for the retrieved task results.
	 * <p>
	 * If the argument is <code>null</code>, or not a composed structured task result, it is simply returned.
	 * 
	 * @param value
	 *            The object to resolve.
	 * @param results
	 *            The task result resolver to retrieve the results from.
	 * @return The final resolved object.
	 * @throws NullPointerException
	 *             If <code>results</code> is <code>null</code> while attempting resolution.
	 * @see ComposedStructuredTaskResult#getIntermediateTaskResult(TaskResultResolver)
	 * @see StructuredObjectTaskResult#getTaskIdentifier()
	 * @since saker.build 0.8.21
	 */
	public static Object resolveComposition(Object value, TaskResultResolver results) throws NullPointerException {
		TaskResultDependencyHandle handle = null;
		while (true) {
			if (value instanceof ComposedStructuredTaskResult) {
				if (handle != null) {
					handle.setTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(value));
				}

				ComposedStructuredTaskResult composed = (ComposedStructuredTaskResult) value;
				handle = composed.toIntermediateTaskResultDependencyHandle(results);
				value = handle.get();
				continue;
			}
			if (handle != null) {
				handle.setTaskOutputChangeDetector(
						CommonTaskOutputChangeDetector.notInstanceOf(ComposedStructuredTaskResult.class));
			}

			if (value instanceof StructuredObjectTaskResult) {
				if (handle != null) {
					handle.setTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(value));
				}

				StructuredObjectTaskResult objres = (StructuredObjectTaskResult) value;
				handle = results.getTaskResultDependencyHandle(objres.getTaskIdentifier());
				Object obj = handle.get();
				value = obj;
				continue;
			}
			if (handle != null) {
				handle.setTaskOutputChangeDetector(
						CommonTaskOutputChangeDetector.notInstanceOf(StructuredObjectTaskResult.class));
			}
			return value;
		}
	}

	/**
	 * Resolves the intermediate task results of the argument dependency handle if it refers to an instance of
	 * {@link ComposedStructuredTaskResult} or {@link StructuredObjectTaskResult}.
	 * <p>
	 * The function will retrieve the result of the argument handle and will check if it's a composed structured task
	 * result. In that case it will apply an appropriate task output change detector and continue resolving the
	 * intermediate results.
	 * <p>
	 * The returned handle will refer to an object that is not a composed structured task result.
	 * <p>
	 * If the given handle doesn't refer to a composed task result, then it's simply returned.
	 * {@link CommonTaskOutputChangeDetector#notInstanceOf(Class) notInstanceOf} output change detector is also applied
	 * in this case.
	 * 
	 * @param handle
	 *            The handle to the task result to resolve.
	 * @param results
	 *            The task result resolver to retrieve the results from.
	 * @return The final resolved handle that doesn't refer to a composed structured task result..
	 * @throws NullPointerException
	 *             If <code>handle</code> is <code>null</code>, or if <code>results</code> is <code>null</code> while
	 *             attempting resolution.
	 * @see ComposedStructuredTaskResult#toIntermediateTaskResultDependencyHandle(TaskResultResolver)
	 * @see StructuredObjectTaskResult#getTaskIdentifier()
	 * @since saker.build 0.8.21
	 */
	public static TaskResultDependencyHandle resolveCompositionTaskResultDependencyHandle(
			TaskResultDependencyHandle handle, TaskResultResolver results) throws NullPointerException {
		Objects.requireNonNull(handle, "handle");
		Object value = handle.get();
		TaskResultDependencyHandle newhandle = handle;

		while (true) {
			if (value instanceof ComposedStructuredTaskResult) {
				newhandle.setTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(value));

				ComposedStructuredTaskResult composed = (ComposedStructuredTaskResult) value;
				newhandle = composed.toIntermediateTaskResultDependencyHandle(results);
				value = newhandle.get();
				continue;
			}
			newhandle.setTaskOutputChangeDetector(
					CommonTaskOutputChangeDetector.notInstanceOf(ComposedStructuredTaskResult.class));

			if (value instanceof StructuredObjectTaskResult) {
				newhandle.setTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(value));

				StructuredObjectTaskResult objres = (StructuredObjectTaskResult) value;
				newhandle = results.getTaskResultDependencyHandle(objres.getTaskIdentifier());
				Object obj = newhandle.get();
				value = obj;
				continue;
			}
			newhandle.setTaskOutputChangeDetector(
					CommonTaskOutputChangeDetector.notInstanceOf(StructuredObjectTaskResult.class));
			if (newhandle != handle) {
				//if we actually retrieve some results, then clone the handle
				return newhandle.clone();
			}
			return newhandle;
		}
	}

	/**
	 * Resolves the intermediate task results of the argument future handle if it refers to an instance of
	 * {@link ComposedStructuredTaskResult} or {@link StructuredObjectTaskResult}.
	 * <p>
	 * The function works the same way as
	 * {@link #resolveCompositionTaskResultDependencyHandle(TaskResultDependencyHandle, TaskResultResolver)}, after
	 * wrapping the argument future into a {@link TaskResultDependencyHandle}.
	 * 
	 * @param future
	 *            The future to uncompose.
	 * @return The final resolved handle that doesn't refer to a composed structured task result..
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see ComposedStructuredTaskResult#toIntermediateTaskResultDependencyHandle(TaskResultResolver)
	 * @see StructuredObjectTaskResult#getTaskIdentifier()
	 * @since saker.build 0.8.21
	 */
	public static TaskResultDependencyHandle resolveCompositionTaskDependencyFuture(TaskDependencyFuture<?> future)
			throws NullPointerException {
		Objects.requireNonNull(future, "future");
		return resolveCompositionTaskResultDependencyHandle(new FutureWrapperTaskResultDependencyHandle(future),
				future.getTaskContext());
	}

	/**
	 * Creates a structured task result object that returns the argument literal object.
	 * <p>
	 * The argument <b>must not</b> be a structured task result in nature. That is, it should be suitable to be directly
	 * returned from the {@link #toResult(TaskResultResolver)} function.
	 * <p>
	 * The returned structured task result implements {@link Object#hashCode()} and {@link Object#equals(Object)} that
	 * is based on the enclosed value.
	 * 
	 * @param literal
	 *            The value to enclose in a structured task result.
	 * @return The structured task result with the argument value.
	 * @since saker.build 0.8.10
	 */
	public static StructuredTaskResult createLiteral(Object literal) {
		return new LiteralStructuredTaskResult(literal);
	}
}
