package saker.build.exception;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.exception.TaskExecutionException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.util.exc.ExceptionView;

/**
 * {@link ExceptionView} subclass that holds task identifier information for the corresponding exceptions.
 * <p>
 * While creating an instance of this class, if the corresponding exception is an instance of
 * {@link TaskExecutionException}, then the associated task identifier will be stored in the exception view.
 * <p>
 * An instance of this exception view is usually constructed during task execution, to be able to print a script trace
 * after the execution has finished. It will probably be converted to a {@link ScriptPositionedExceptionView}, and then
 * it will be printable to user-facing output streams. Although this may be an usual use-case for this class, it may be
 * used in other ways as the user sees fit.
 * <p>
 * Use {@link #create(Throwable)} to create a new instance.
 */
public class TaskIdentifierExceptionView extends ExceptionView {
	private static final long serialVersionUID = 1L;

	/**
	 * The task identifier associated with this exception.
	 */
	protected TaskIdentifier taskId;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskIdentifierExceptionView() {
	}

	/**
	 * Creates a new instance for the given exception and task identifier.
	 * 
	 * @param e
	 *            The exception.
	 * @param taskId
	 *            The task identifier.
	 * @throws NullPointerException
	 *             If the exception is <code>null</code>.
	 * @see ExceptionView#ExceptionView(Throwable)
	 */
	protected TaskIdentifierExceptionView(Throwable e, TaskIdentifier taskId) throws NullPointerException {
		super(e);
		this.taskId = taskId;
	}

	/**
	 * Creates a new exception view based on the argument exception.
	 * 
	 * @param e
	 *            The exception.
	 * @return The created exception view.
	 * @throws NullPointerException
	 *             If the exception is <code>null</code>.
	 */
	public static TaskIdentifierExceptionView create(Throwable e) throws NullPointerException {
		return createImpl(e, t -> {
			if (t instanceof TaskExecutionException) {
				return new TaskIdentifierExceptionView(t, ((TaskExecutionException) t).getTaskIdentifier());
			}
			return new TaskIdentifierExceptionView(t, null);
		});
	}

	/**
	 * Gets the task identifier associated with this exception view.
	 * 
	 * @return The task identifier or <code>null</code> if there's none.
	 */
	public TaskIdentifier getTaskIdentifier() {
		return taskId;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(taskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		taskId = (TaskIdentifier) in.readObject();
	}
}
