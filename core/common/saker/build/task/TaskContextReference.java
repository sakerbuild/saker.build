package saker.build.task;

import java.io.Closeable;
import java.io.IOException;

import saker.apiextract.api.ExcludeApi;

// explicitly exclude this api class, as it is implementation detail
@ExcludeApi
public final class TaskContextReference implements Closeable {
	/**
	 * The thread local for tracking the current task context.
	 * <p>
	 * <b>Warning: Do not use. See explanation at {@link #current()}.</b>
	 */
	public static final ThreadLocal<TaskContextReference> CURRENT_THREAD_TASK_CONTEXT = new InheritableThreadLocal<>();

	/**
	 * Returns the {@link TaskContext} for the current thread.
	 * <p>
	 * <b>Warning: Do not use.This method should not be used as it is only present to provide internal functionality. As
	 * of this implementation it is used to redirect the output written to {@link System#out} to the current task output
	 * for convenience use-case.</b>
	 * <p>
	 * The current task context is tracked using an inheritable thread local variable, and it it set by the build
	 * runtime.
	 * 
	 * @return The task context for the current thread.
	 */
	public static TaskContext current() {
		TaskContextReference ref = CURRENT_THREAD_TASK_CONTEXT.get();
		if (ref == null) {
			return null;
		}
		return ref.get();
	}

	private final TaskContextReference previous;
	private volatile TaskContext taskContext;

	public TaskContextReference(TaskContext taskContext) {
		this.taskContext = taskContext;
		this.previous = CURRENT_THREAD_TASK_CONTEXT.get();
		CURRENT_THREAD_TASK_CONTEXT.set(this);
	}

	public TaskContext get() {
		return taskContext;
	}

	@Override
	public void close() throws IOException {
		if (previous == null) {
			CURRENT_THREAD_TASK_CONTEXT.remove();
		} else {
			CURRENT_THREAD_TASK_CONTEXT.set(previous);
		}
		this.taskContext = null;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (previous != null ? "previous=" + previous + ", " : "")
				+ (taskContext != null ? "taskContext=" + taskContext : "") + "]";
	}
}
