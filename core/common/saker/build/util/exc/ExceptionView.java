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
package saker.build.util.exc;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.function.Function;

import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.execution.SakerLog.CommonExceptionFormat;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * A view of an exception.
 * <p>
 * An exception view contains the full information of an exception stack trace and causes, but does not hold any
 * instances of the exceptions. This is generally useful when clients only want to access the stack trace and causes of
 * an exception, but not the actual exception instances.
 * <p>
 * Sometimes it is not possible to transfer the exceptions over RMI, as the exceptions classes might not be found on the
 * other endpoint. In order to avoid the errors this could cause, and exception view can be transferred, which only
 * holds raw data.
 * <p>
 * Use {@link #create(Throwable)} to create a new instance.
 */
public class ExceptionView implements Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * Singleton empty {@link ExceptionView} array.
	 */
	protected static final ExceptionView[] EMPTY_EXCEPTIONVIEW_ARRAY = {};

	/**
	 * The class name of the exception.
	 */
	protected String exceptionClassName;
	/**
	 * The message of the exception.
	 */
	protected String message;

	/**
	 * The result of {@link Throwable#toString()}.
	 *
	 * @since saker.build 0.8.16
	 */
	protected String stringRepresentation;

	/**
	 * The stack trace of the exception.
	 */
	protected StackTraceElement[] stackTrace;

	/**
	 * The cause of the exception.
	 */
	protected ExceptionView cause;
	/**
	 * The suppressed exceptions.
	 */
	protected ExceptionView[] suppressed = EMPTY_EXCEPTIONVIEW_ARRAY;

	/**
	 * For {@link Externalizable}.
	 */
	public ExceptionView() {
	}

	/**
	 * Creates a new exception view intialized with the argument throwable.
	 * <p>
	 * The cause and suppressed exceptions are not initialized.
	 * 
	 * @param e
	 *            The exception.
	 * @throws NullPointerException
	 *             If the exception is <code>null</code>.
	 */
	public ExceptionView(Throwable e) throws NullPointerException {
		Objects.requireNonNull(e, "exception");
		this.exceptionClassName = e.getClass().getName();
		this.message = e.getMessage();
		this.stackTrace = e.getStackTrace();
		this.stringRepresentation = e.toString();
	}

	/**
	 * Creates a new exception view by copying the data from the argument.
	 * <p>
	 * The cause and suppressed exceptions are not copied recursively.
	 * 
	 * @param copy
	 *            The exception view to copy the attributes from.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ExceptionView(ExceptionView copy) throws NullPointerException {
		Objects.requireNonNull(copy, "copy exception view");
		this.exceptionClassName = copy.exceptionClassName;
		this.message = copy.message;
		this.stackTrace = copy.stackTrace;
		this.cause = copy.cause;
		this.suppressed = copy.suppressed;
		this.stringRepresentation = copy.stringRepresentation;
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
	public static ExceptionView create(Throwable e) throws NullPointerException {
		Objects.requireNonNull(e, "exception");
		return createImpl(e, ExceptionView::new, new IdentityHashMap<>());
	}

	/**
	 * Gets the class name of the exception.
	 * 
	 * @return The class name.
	 */
	public String getExceptionClassName() {
		return exceptionClassName;
	}

	/**
	 * Gets the message of the exception.
	 * 
	 * @return The message.
	 * @see Throwable#getMessage()
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Gets the cause of the exception.
	 * 
	 * @return The cause.
	 * @see Throwable#getCause()
	 */
	public ExceptionView getCause() {
		return cause;
	}

	/**
	 * Gets the stack trace of the exception.
	 * 
	 * @return The stack trace.
	 * @see Throwable#getStackTrace()
	 */
	public StackTraceElement[] getStackTrace() {
		return stackTrace;
	}

	/**
	 * Gets the suppressed exceptions of this exception.
	 * 
	 * @return The suppressed exceptions.
	 * @see Throwable#getSuppressed()
	 */
	public ExceptionView[] getSuppressed() {
		return suppressed;
	}

	/**
	 * Prints the stack trace to {@link System#err}.
	 * <p>
	 * The format is the same as {@link Throwable#printStackTrace()}.
	 */
	public void printStackTrace() {
		SakerLog.printFormatException(this, System.err, CommonExceptionFormat.FULL);
	}

	/**
	 * Prints the stack trace to the specified stream.
	 * <p>
	 * The format is the same as {@link Throwable#printStackTrace(PrintStream)}.
	 * 
	 * @param ps
	 *            The output stream for the stack trace.
	 */
	public void printStackTrace(PrintStream ps) {
		SakerLog.printFormatException(this, ps, CommonExceptionFormat.FULL);
	}

	/**
	 * Prints the stack trace to the specified stream.
	 * <p>
	 * The format is the same as {@link Throwable#printStackTrace(PrintWriter)}.
	 * 
	 * @param ps
	 *            The output stream for the stack trace.
	 */
	public void printStackTrace(PrintWriter ps) {
		try {
			SakerLog.printFormatException(this, ps, CommonExceptionFormat.FULL);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Prints the stack trace to the specified string builder.
	 * <p>
	 * The format is the same as {@link Throwable#printStackTrace(PrintWriter)}.
	 * 
	 * @param sb
	 *            The output stream builder for the stack trace.
	 * @since saker.build 0.8.14
	 */
	public void printStackTrace(StringBuilder sb) {
		SakerLog.printFormatException(this, sb, CommonExceptionFormat.FULL);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(exceptionClassName);
		out.writeObject(message);
		SerialUtils.writeExternalArray(out, stackTrace, SerialUtils::writeExternalStackTraceElement);
		out.writeObject(cause);
		out.writeObject(suppressed);
		out.writeObject(stringRepresentation);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		exceptionClassName = in.readUTF();
		message = (String) in.readObject();
		stackTrace = SerialUtils.readExternalArray(in, StackTraceElement[]::new,
				SerialUtils::readExternalStackTraceElement);
		cause = (ExceptionView) in.readObject();
		suppressed = (ExceptionView[]) in.readObject();
		stringRepresentation = (String) in.readObject();
	}

	@Override
	public String toString() {
		return stringRepresentation;
	}

	/**
	 * Creates a new exception view based on the argument exception, using the specified view creator.
	 * 
	 * @param <T>
	 *            The type of the exception view to create.
	 * @param e
	 *            The exception.
	 * @param viewcreator
	 *            The function to use when creating an exception view.
	 * @return The created exception view.
	 */
	protected static <T extends ExceptionView> T createImpl(Throwable e, Function<? super Throwable, T> viewcreator) {
		return createImpl(e, viewcreator, new IdentityHashMap<>());
	}

	private static <T extends ExceptionView> T createImpl(Throwable e, Function<? super Throwable, T> viewcreator,
			IdentityHashMap<Throwable, T> createds) {
		T got = createds.get(e);
		if (got != null) {
			return got;
		}
		T result = viewcreator.apply(e);
		createds.put(e, result);
		Throwable[] supr = e.getSuppressed();
		ExceptionView[] supressed;
		if (supr.length == 0) {
			supressed = EMPTY_EXCEPTIONVIEW_ARRAY;
		} else {
			supressed = new ExceptionView[supr.length];
			for (int i = 0; i < supr.length; i++) {
				supressed[i] = createImpl(supr[i], viewcreator, createds);
			}
		}
		Throwable c = e.getCause();
		ExceptionView cause;
		if (c != null) {
			cause = createImpl(c, viewcreator, createds);
		} else {
			cause = null;
		}
		result.cause = cause;
		result.suppressed = supressed;
		return result;
	}
}
