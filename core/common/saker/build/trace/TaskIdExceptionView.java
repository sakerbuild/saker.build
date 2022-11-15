package saker.build.trace;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.exception.TaskExecutionException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.util.exc.ExceptionView;

/**
 * {@link ExceptionView} that also holds a {@link TaskIdentifier} if the original exception is a
 * {@link TaskExecutionException}.
 */
public class TaskIdExceptionView extends ExceptionView {
	private static final long serialVersionUID = 1L;

	protected TaskIdentifier taskIdentifier;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskIdExceptionView() {
	}

	protected TaskIdExceptionView(Throwable e, TaskIdentifier taskid) {
		super(e);
		this.taskIdentifier = taskid;
	}

	public static ExceptionView create(Throwable e) throws NullPointerException {
		return ExceptionView.createImpl(e, t -> {
			if (t instanceof TaskExecutionException) {
				return new TaskIdExceptionView(t, ((TaskExecutionException) t).getTaskIdentifier());
			}
			return new ExceptionView(t);
		});
	}

	public TaskIdentifier getTaskIdentifier() {
		return taskIdentifier;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(taskIdentifier);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		taskIdentifier = (TaskIdentifier) in.readObject();
	}
}
