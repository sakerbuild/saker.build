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
import java.util.Set;
import java.util.function.Function;

import saker.build.thirdparty.saker.util.ObjectUtils;
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
		printStackTrace(System.err);
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
		printStackTraceImpl(ps);
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
		printStackTraceImpl(ps);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(exceptionClassName);
		out.writeObject(message);
		SerialUtils.writeExternalArray(out, stackTrace, SerialUtils::writeExternalStackTraceElement);
		out.writeObject(cause);
		out.writeObject(suppressed);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		exceptionClassName = in.readUTF();
		message = (String) in.readObject();
		stackTrace = SerialUtils.readExternalArray(in, StackTraceElement[]::new,
				SerialUtils::readExternalStackTraceElement);
		cause = (ExceptionView) in.readObject();
		suppressed = (ExceptionView[]) in.readObject();
	}

	@Override
	public String toString() {
		String exccname = getExceptionClassName();
		String message = getMessage();
		if (message == null) {
			return exccname;
		}
		return exccname + ": " + message;
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
		((ExceptionView) result).cause = cause;
		((ExceptionView) result).suppressed = supressed;
		return result;
	}

	//XXX use the printing functions in SakerLog instead

	private static final String SUPPRESSED_CAPTION = "Suppressed: ";
	private static final String CAUSE_CAPTION = "Caused by: ";

	private void printStackTraceImpl(Appendable ps) {
		String ls = System.lineSeparator();
		try {
			ps.append(this.toString());
			ps.append(ls);

			StackTraceElement[] trace = this.stackTrace;
			for (StackTraceElement traceElement : trace) {
				ps.append("\tat " + traceElement);
				ps.append(ls);
			}
			Set<ExceptionView> dejaVu = ObjectUtils.newIdentityHashSet();
			// Print suppressed exceptions, if any
			for (ExceptionView se : suppressed) {
				se.printEnclosedStackTrace(ps, trace, SUPPRESSED_CAPTION, "\t", dejaVu);
			}

			// Print cause, if any
			ExceptionView ourCause = this.cause;
			if (ourCause != null) {
				ourCause.printEnclosedStackTrace(ps, trace, CAUSE_CAPTION, "", dejaVu);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void printEnclosedStackTrace(Appendable ps, StackTraceElement[] enclosingTrace, String caption,
			String prefix, Set<ExceptionView> dejaVu) throws IOException {
		String ls = System.lineSeparator();
		if (!dejaVu.add(this)) {
			ps.append("\t[CIRCULAR REFERENCE:" + this + "]");
			ps.append(ls);
		} else {
			StackTraceElement[] trace = this.stackTrace;
			int m = trace.length - 1;
			int n = enclosingTrace.length - 1;
			while (m >= 0 && n >= 0 && trace[m].equals(enclosingTrace[n])) {
				m--;
				n--;
			}
			int framesInCommon = trace.length - 1 - m;

			// Print our stack trace
			ps.append(prefix + caption + this);
			ps.append(ls);
			for (int i = 0; i <= m; i++) {
				ps.append(prefix + "\tat " + trace[i]);
				ps.append(ls);
			}
			if (framesInCommon != 0) {
				ps.append(prefix + "\t... " + framesInCommon + " more");
				ps.append(ls);
			}

			// Print suppressed exceptions, if any
			for (ExceptionView se : this.suppressed) {
				se.printEnclosedStackTrace(ps, trace, SUPPRESSED_CAPTION, prefix + "\t", dejaVu);
			}

			// Print cause, if any
			ExceptionView ourCause = this.cause;
			if (ourCause != null) {
				ourCause.printEnclosedStackTrace(ps, trace, CAUSE_CAPTION, prefix, dejaVu);
			}
		}
	}

}
