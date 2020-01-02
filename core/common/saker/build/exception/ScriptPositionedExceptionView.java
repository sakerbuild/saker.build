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
package saker.build.exception;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;
import java.util.function.Function;

import saker.build.file.path.SakerPath;
import saker.build.scripting.ScriptPosition;
import saker.build.task.exception.TaskExecutionException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.util.exc.ExceptionView;

/**
 * {@link ExceptionView} subclass that holds invormation about the script trace of the exceptions.
 * <p>
 * The script trace specifies the origin of an exception that occurred during a task execution. The script trace is
 * constructed by examining which tasks created the exception that occurred during a task execution, and tracing it back
 * to the root task.
 * <p>
 * Use {@link #create(Throwable, Function)} to create a new instance.
 */
public class ScriptPositionedExceptionView extends ExceptionView {
	private static final long serialVersionUID = 1L;

	/**
	 * Singleton empty {@link ScriptPositionStackTraceElement} array.
	 */
	public static final ScriptPositionStackTraceElement[] EMPTY_STACKTRACE_ARRAY = {};

	/**
	 * Data class holding information of a script trace element.
	 * <p>
	 * The class contains data about the build file path and the position of the associated script element which
	 * corresponds to the task that threw the exception.
	 * <p>
	 * Both the file path and the script position may be <code>null</code> for a script trace element, but not both at
	 * the same time. Generally file path will not be <code>null</code> if there is position information available, but
	 * callers should handle that gracefully.
	 */
	public static class ScriptPositionStackTraceElement implements Externalizable {
		private static final long serialVersionUID = 1L;

		private SakerPath buildFilePath;
		private ScriptPosition position;

		/**
		 * For {@link Externalizable}.
		 */
		public ScriptPositionStackTraceElement() {
		}

		/**
		 * Creates a new script trace element with the given fields.
		 * 
		 * @param buildFilePath
		 *            The build file path.
		 * @param position
		 *            The position of the script task element.
		 * @throws NullPointerException
		 *             If both arguments are <code>null</code>.
		 */
		public ScriptPositionStackTraceElement(SakerPath buildFilePath, ScriptPosition position)
				throws NullPointerException {
			if (buildFilePath == null && position == null) {
				throw new NullPointerException("Both build file path and script position is null");
			}
			this.buildFilePath = buildFilePath;
			this.position = position;
		}

		/**
		 * Gets the file path to the script which is the location of the corresponding exception.
		 * 
		 * @return The file path or <code>null</code> if not available.
		 */
		public SakerPath getBuildFilePath() {
			return buildFilePath;
		}

		/**
		 * Gets the script position of the associated task element.
		 * 
		 * @return The script position or <code>null</code> if not available.
		 */
		public ScriptPosition getPosition() {
			return position;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(position);
			out.writeObject(buildFilePath);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			position = (ScriptPosition) in.readObject();
			buildFilePath = (SakerPath) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((buildFilePath == null) ? 0 : buildFilePath.hashCode());
			result = prime * result + ((position == null) ? 0 : position.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ScriptPositionStackTraceElement other = (ScriptPositionStackTraceElement) obj;
			if (buildFilePath == null) {
				if (other.buildFilePath != null)
					return false;
			} else if (!buildFilePath.equals(other.buildFilePath))
				return false;
			if (position == null) {
				if (other.position != null)
					return false;
			} else if (!position.equals(other.position))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return toString(buildFilePath, position);
		}

		/**
		 * Converts this instance to a {@link String}, optionally relativizing the file path against the argument.
		 * 
		 * @param relativizedir
		 *            The file path to optionally relativize against or <code>null</code>.
		 * @return The string representation of the script trace.
		 */
		public String toString(SakerPath relativizedir) {
			if (relativizedir == null || buildFilePath == null || !buildFilePath.startsWith(relativizedir)) {
				return toString(buildFilePath, position);
			}
			return toString(buildFilePath.subPath(relativizedir.getNameCount()), position);
		}

		/**
		 * Converts the arguments into a script trace element string.
		 * <p>
		 * The format of the element is the following:
		 * 
		 * <pre>
		 * path:line:linestart-lineend
		 * </pre>
		 * 
		 * If any of the parts are missing, then they won't be printed, and their corresponding connecting character is
		 * omitted.
		 * 
		 * @param path
		 *            The path of the script file.
		 * @param position
		 *            The position in the script file.
		 * @return The string representation of the argument script trace.
		 */
		public static String toString(SakerPath path, ScriptPosition position) {
			StringBuilder sb = new StringBuilder();
			if (path != null) {
				sb.append(path);
			}
			if (position != null) {
				sb.append(":");
				sb.append(position.getLine() + 1);
				int linepos = position.getLinePosition();
				if (linepos >= 0) {
					sb.append(":");
					sb.append(linepos + 1);
					int len = position.getLength();
					if (len > 0) {
						sb.append("-");
						sb.append(linepos + 1 + len - 1);
					}
				}
			}
			return sb.toString();
		}

	}

	/**
	 * The script trace array.
	 */
	protected ScriptPositionStackTraceElement[] positionScriptTrace;

	/**
	 * For {@link Externalizable}.
	 */
	public ScriptPositionedExceptionView() {
	}

	/**
	 * Creats a new exception view by copying the data from the argument.
	 * <p>
	 * The cause and suppressed exceptions are not copied recursively.
	 * 
	 * @param copy
	 *            The exception view to copy the attributes from.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see ExceptionView#ExceptionView(ExceptionView)
	 */
	public ScriptPositionedExceptionView(ScriptPositionedExceptionView copy) throws NullPointerException {
		super(copy);
		this.positionScriptTrace = copy.positionScriptTrace;
	}

	/**
	 * Creates a new exception view by copying the data from the argument and assigning a new script trace.
	 * <p>
	 * The cause and suppressed exceptions are not copied recursively.
	 * 
	 * @param copy
	 *            The exception view to copy the attributes from.
	 * @param newscripttrace
	 *            The script trace to assign to this instance. The array is not copied.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public ScriptPositionedExceptionView(ExceptionView copy, ScriptPositionStackTraceElement[] newscripttrace)
			throws NullPointerException {
		super(copy);
		Objects.requireNonNull(newscripttrace, "script trace");
		this.positionScriptTrace = newscripttrace;
	}

	/**
	 * Creates a new instance initialized with the argument exception and no script trace.
	 * 
	 * @param e
	 *            The exception.
	 * @throws NullPointerException
	 *             If the exception is <code>null</code>.
	 * @see ExceptionView#ExceptionView(Throwable)
	 */
	protected ScriptPositionedExceptionView(Throwable e) throws NullPointerException {
		this(e, EMPTY_STACKTRACE_ARRAY);
	}

	/**
	 * Creates a new instance with the argument exception and script trace.
	 * 
	 * @param e
	 *            The exception.
	 * @param positionScriptTrace
	 *            The script trace.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @see ExceptionView#ExceptionView(Throwable)
	 */
	protected ScriptPositionedExceptionView(Throwable e, ScriptPositionStackTraceElement[] positionScriptTrace)
			throws NullPointerException {
		super(e);
		Objects.requireNonNull(positionScriptTrace, "script trace");
		this.positionScriptTrace = positionScriptTrace;
	}

	/**
	 * Creates a new exception view based on the argument exception.
	 * <p>
	 * All script traces will be initialized to empty arrays in the created view.
	 * 
	 * @param e
	 *            The exception.
	 * @return The created exception view.
	 * @throws NullPointerException
	 *             If the exception is <code>null</code>.
	 */
	public static ScriptPositionedExceptionView create(Throwable e) throws NullPointerException {
		return createImpl(e, ScriptPositionedExceptionView::new);
	}

	/**
	 * Creates a new exception view based on the argument exception and uses the passed function to look up the script
	 * traces.
	 * <p>
	 * If the lookup function is <code>null</code>, this method is same as calling {@link #create(Throwable)}.
	 * 
	 * @param e
	 *            The exception.
	 * @param stacktraceresolver
	 *            The function to use to look up a script trace for a given task identifier.
	 * @return The created exception view.
	 * @throws NullPointerException
	 *             If the exception is <code>null</code>.
	 */
	public static ScriptPositionedExceptionView create(Throwable e,
			Function<? super TaskIdentifier, ? extends ScriptPositionStackTraceElement[]> stacktraceresolver)
			throws NullPointerException {
		if (stacktraceresolver == null) {
			return create(e);
		}
		return ExceptionView.createImpl(e, t -> {
			ScriptPositionStackTraceElement[] posstacktrace;
			if (t instanceof TaskExecutionException) {
				posstacktrace = stacktraceresolver.apply(((TaskExecutionException) t).getTaskIdentifier());
				if (posstacktrace == null) {
					posstacktrace = ScriptPositionedExceptionView.EMPTY_STACKTRACE_ARRAY;
				}
			} else {
				posstacktrace = ScriptPositionedExceptionView.EMPTY_STACKTRACE_ARRAY;
			}
			return new ScriptPositionedExceptionView(t, posstacktrace);
		});
	}

	/**
	 * Gets the backing script trace of this exception view.
	 * <p>
	 * The returned array is not copied. Do not modify it.
	 * 
	 * @return The script trace.
	 */
	public ScriptPositionStackTraceElement[] getPositionStackTrace() {
		return positionScriptTrace;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(positionScriptTrace);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		positionScriptTrace = (ScriptPositionStackTraceElement[]) in.readObject();
	}
}
