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
package saker.build.runtime.execution;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import saker.apiextract.api.PublicApi;
import saker.build.exception.ScriptPositionedExceptionView;
import saker.build.exception.ScriptPositionedExceptionView.ScriptPositionStackTraceElement;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.meta.PropertyNames;
import saker.build.task.InternalTaskContext;
import saker.build.task.TaskContext;
import saker.build.task.TaskContextReference;
import saker.build.task.TaskExecutionManager;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.trace.InternalBuildTraceImpl;
import saker.build.util.exc.ExceptionView;

/**
 * Logging utility class for printing formatted data to the specified output.
 * <p>
 * The class can be used in a builder pattern, instantiated by one of the static methods based on severity, and then
 * calling {@link #println()} for finishing. The instances are reusable.
 * <p>
 * The class defines a severity, which specifies the error level of the printed message. Each error message with a given
 * severity will begin with an appropriate word for it. E.g. <code>"Error"</code>.
 * <p>
 * The logger will print the messages to the specified output, or to a default stream when unspecified. The default
 * stream is based on the current task (if any), else it will be printed to the current {@link System#out standard
 * output} of the process. In case it is necessary, the message will be encoded using UTF-8.
 * <p>
 * A path can be specified for the log messages, which will be prepended to the printed line.
 * <p>
 * Line and line positions can be specified for the log messages, which will be written out after the path. Line
 * positions are only printed when a path was set.
 * <p>
 * The printed output has the following format: <br>
 * The phrases between brackets are hierarchically optional.
 * 
 * <pre>
 * [path][:line][:posstart][-posend]: [severity]: [message]
 * </pre>
 * 
 * Line number information is only present if a path was specified. Line position is only present, if line number is
 * present. Intermediate <code>": "</code> is omitted if no phrase comes after it.
 * <p>
 * This class also contains some utility functions for formatting other logging information.
 * <p>
 * <b>Note:</b> Functionality that automatically prints on the {@linkplain TaskContext#getStandardOut() task standard
 * output} without a specified {@link TaskContext} as the output relies on an internal {@link InheritableThreadLocal
 * inheritable thread local} variable. If you use your own threading implementation and don't inherit thread locals,
 * make sure to explicitly call {@link #out(TaskContext)} if you intend to write the output to the task output streams.
 */
@PublicApi
public class SakerLog {
	private static final BiConsumer<SakerLog, Object> DEFAULT_PRINTER = (sl, message) -> {
		TaskContext tc = TaskContextReference.current();
		StringBuilder s = sl.constructMessage(message);
		if (tc != null) {
			if (sl.verbose) {
				verbosePrintToTaskContext(tc, s);
			} else {
				tc.println(s.toString());
			}
		} else {
			System.out.println(s);
		}
	};

	private static final String[] SEVERITY_TEXTS = { "", "Info", "Warning", "Error", "Success" };

	/**
	 * Severity representing no severity.
	 * <p>
	 * Will create a message without any severity phrase.
	 */
	public static final int SEVERITY_PLAIN = 0;
	/**
	 * Severity about generic information.
	 */
	public static final int SEVERITY_INFO = 1;
	/**
	 * Severity for warnings.
	 */
	public static final int SEVERITY_WARNING = 2;
	/**
	 * Severity for errors.
	 */
	public static final int SEVERITY_ERROR = 3;
	/**
	 * Severity for representing success.
	 */
	public static final int SEVERITY_SUCCESS = 4;

	private static final int MAX_SEVERITY = SEVERITY_SUCCESS;

	private final int severity;
	private String path;
	private int line = -1;
	private int lineStart = -1;
	private int lineEnd = -1;
	private boolean verbose = false;

	private BiConsumer<SakerLog, Object> printer = DEFAULT_PRINTER;

	private SakerLog(int severity) {
		if (severity < 0 || severity > MAX_SEVERITY) {
			throw new IllegalArgumentException("Invalid severity: " + severity);
		}
		this.severity = severity;
	}

	/**
	 * Creates a new logger with the specified severity.
	 * 
	 * @param severity
	 *            The severity. See the <code>SEVERITY_*</code> constants defined by this class.
	 * @return The created logger.
	 * @throws IllegalArgumentException
	 *             If the severity is out of range of for the specified <code>SEVERITY_*</code> constants of this class.
	 */
	public static SakerLog severity(int severity) throws IllegalArgumentException {
		return new SakerLog(severity);
	}

	/**
	 * Creates a logger with severity of {@link #SEVERITY_SUCCESS}.
	 * 
	 * @return The created logger.
	 */
	public static SakerLog success() {
		return new SakerLog(SEVERITY_SUCCESS);
	}

	/**
	 * Creates a logger with severity of {@link #SEVERITY_ERROR}.
	 * 
	 * @return The created logger.
	 */
	public static SakerLog error() {
		return new SakerLog(SEVERITY_ERROR);
	}

	/**
	 * Creates a logger with severity of {@link #SEVERITY_WARNING}.
	 * 
	 * @return The created logger.
	 */
	public static SakerLog warning() {
		return new SakerLog(SEVERITY_WARNING);
	}

	/**
	 * Creates a logger with severity of {@link #SEVERITY_INFO}.
	 * 
	 * @return The created logger.
	 */
	public static SakerLog info() {
		return new SakerLog(SEVERITY_INFO);
	}

	/**
	 * Creates a logger with severity of {@link #SEVERITY_PLAIN}.
	 * 
	 * @return The created logger.
	 */
	public static SakerLog log() {
		return new SakerLog(SEVERITY_PLAIN);
	}

	/**
	 * Sets the path to display for this logger.
	 * <p>
	 * The logger will attempt to relativize the path based on the current task information. If this method is called in
	 * a context of a build system task, then {@link SakerPathFiles#toRelativeString(SakerPath)} will be called on it,
	 * which tries to convert it to a relative path based on the current execution configuration.
	 * 
	 * @param path
	 *            The path, or <code>null</code> to disable it.
	 * @return <code>this</code>
	 */
	public SakerLog path(SakerPath path) {
		if (path == null) {
			this.path = null;
		} else {
			this.path = SakerPathFiles.toRelativeString(path);
		}
		return this;
	}

	/**
	 * Sets the display line for this logger instance.
	 * <p>
	 * The line is 0 (zero) indexed.
	 * <p>
	 * Line information is displayed only if there was a path set.
	 * 
	 * @param line
	 *            The line. Less than zero to unset it.
	 * @return <code>this</code>
	 * @see #path(SakerPath)
	 */
	public SakerLog line(int line) {
		this.line = line;
		return this;
	}

	/**
	 * Sets the range in the line for this logger instance.
	 * <p>
	 * Parameters are 0 (zero) indexed.
	 * <p>
	 * Only affects the output if {@link #line(int)} is not negative.
	 * <p>
	 * If the line end parameter extends beyond the actual end of the line, the range is considered to be multi-line.
	 * <p>
	 * If the line start is non-negative, and the line end less than line start, the only the offset in the line is set.
	 * 
	 * @param linestart
	 *            The range start index in the line. (inclusive)
	 * @param lineend
	 *            The range end index in the line. (inclusive)
	 * @return <code>this</code>
	 */
	public SakerLog position(int linestart, int lineend) {
		this.lineStart = linestart;
		this.lineEnd = lineend;
		return this;
	}

	/**
	 * Modifies the logger to print its contents using the given task context and have the path and positional
	 * information of it.
	 * <p>
	 * This method will caues the logger to have the path and positional information that can be determined based on the
	 * currently executing task. This usually results in the appropriate script path and script position to be displayed
	 * in the log message.
	 * <p>
	 * Calling this method will also cause the task context to be used for printing the message.
	 * <p>
	 * Calling the display output modification methods of this logger will cause the script positional information to
	 * not be printed.
	 * <p>
	 * Calling path and positional information modifying methods without also replacing the display output will have no
	 * effect.
	 * 
	 * @param taskcontext
	 *            The task context to use.
	 * @return <code>this</code>
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public SakerLog taskScriptPosition(TaskContext taskcontext) throws NullPointerException {
		Objects.requireNonNull(taskcontext, "task context");
		if (!(taskcontext instanceof InternalTaskContext)) {
			throw new IllegalArgumentException("Argument has unrecognized type: " + taskcontext);
		}
		InternalTaskContext internaltaskcontext = (InternalTaskContext) taskcontext;
		this.printer = (sl, message) -> {
			String msgstr = sl.constructNonPositionMessage(message);
			String escapedmsgstr = msgstr.replace(TaskExecutionManager.PRINTED_LINE_VARIABLES_MARKER_CHAR_STR,
					"\\" + TaskExecutionManager.PRINTED_LINE_VARIABLES_MARKER_CHAR_STR);
			String printline = TaskExecutionManager.PRINTED_LINE_VARIABLES_MARKER_CHAR_STR
					+ TaskExecutionManager.PRINTED_LINE_VAR_LOG_TASK_SCRIPT_POSITION + escapedmsgstr;
			if (verbose) {
				internaltaskcontext.internalPrintlnVerboseVariables(printline);
			} else {
				internaltaskcontext.internalPrintlnVariables(printline);
			}
		};
		return this;
	}

	/**
	 * Sets the display output for this logger instance.
	 * 
	 * @param out
	 *            The output to display this log message on.
	 * @return <code>this</code>
	 * @throws NullPointerException
	 *             If the stream is <code>null</code>.
	 */
	public SakerLog out(PrintStream out) throws NullPointerException {
		Objects.requireNonNull(out, "out");
		this.printer = (sl, message) -> {
			StringBuilder s = sl.constructMessage(message);
			out.println(s.toString());
		};
		return this;
	}

	/**
	 * Sets the display output task for this logger instance.
	 * <p>
	 * The logger will use {@link TaskContext#println(String)} to print the message, which will therefore redisplayed
	 * automatically when the build system skips rerunning the task due to no incremental changes.
	 * <p>
	 * Note: this logger instance won't be reusable outside of this task execution, unless the output is reset.
	 * 
	 * @param taskcontext
	 *            The task context to use for printing.
	 * @return <code>this</code>
	 * @throws NullPointerException
	 *             If the task context is <code>null</code>.
	 */
	public SakerLog out(TaskContext taskcontext) throws NullPointerException {
		Objects.requireNonNull(taskcontext, "task context");
		this.printer = (sl, message) -> {
			StringBuilder s = sl.constructMessage(message);
			if (sl.verbose) {
				verbosePrintToTaskContext(taskcontext, s);
			} else {
				taskcontext.println(s.toString());
			}
		};
		return this;
	}

	/**
	 * Sets the display output for this logger instance to be verbose.
	 * <p>
	 * Verbose output will not display the message using {@link TaskContext#println(String)}, but via
	 * {@link TaskContext#getStandardOut()}, so it won't be redisplayed automatically when the build system skips
	 * rerunning the task due to no incremental changes.
	 * 
	 * @return <code>this</code>
	 */
	public SakerLog verbose() {
		this.verbose = true;
		return this;
	}

	/**
	 * Prints a line with empty message.
	 * <p>
	 * Any path, line, and severity information will be printed to the output, but no message.
	 * <p>
	 * It is strongly recommended to call {@link #println(Object)} instead, unless the caller specifically needs no
	 * message.
	 */
	public void println() {
		flushln(null);
	}

	/**
	 * Prints a line with the argument object as a message.
	 * <p>
	 * The argument will be converted to {@link String} via {@link StringBuilder#append(Object)}.
	 * <p>
	 * A <code>"null"</code> message will be printed if the argument is <code>null</code>.
	 * 
	 * @param text
	 *            The message.
	 */
	public void println(Object text) {
		flushln(text);
	}

	private StringBuilder constructMessage(Object message) {
		StringBuilder sb = new StringBuilder();
		if (path != null) {
			sb.append(path);
			if (line >= 0) {
				sb.append(':');
				sb.append(line + 1);
				if (lineStart >= 0) {
					sb.append(':');
					sb.append(lineStart + 1);
					if (lineEnd >= lineStart) {
						sb.append('-');
						sb.append(lineEnd + 1);
					}
				}
			}
			sb.append(": ");
		}
		constructNonPositionMessage(message, sb);
		return sb;
	}

	private String constructNonPositionMessage(Object message) {
		StringBuilder sb = new StringBuilder();
		constructNonPositionMessage(message, sb);
		return sb.toString();
	}

	private void constructNonPositionMessage(Object message, StringBuilder sb) {
		boolean dots;
		String sevtext = SEVERITY_TEXTS[severity];
		sb.append(sevtext);
		dots = !sevtext.isEmpty();
		String strmsg = Objects.toString(message, null);
		if (!ObjectUtils.isNullOrEmpty(strmsg)) {
			if (dots) {
				sb.append(": ");
			}
			sb.append(strmsg);
		}
	}

	private void flushln(Object message) {
		printer.accept(this, message);
	}

	/**
	 * Verbosely prints the argument message, appends a \n character to it.
	 */
	private static void verbosePrintToTaskContext(TaskContext tc, StringBuilder s) {
		s.append('\n');
		ByteSink out = tc.getStandardOut();
		//do not use the TaskContext.println to not record the output line
		try {
			out.write(ByteArrayRegion.wrap(s.toString().getBytes(StandardCharsets.UTF_8)));
		} catch (IOException ignored) {
			//this should not really happen, and signals that there is something wrong with the standard i/o of the build execution
			tc.getTaskUtilities().reportIgnoredException(ignored);
		}
	}

	/**
	 * Interface for defining how a given exception should be displayed to the stream.
	 * <p>
	 * 
	 * @see CommonExceptionFormat
	 */
	public interface ExceptionFormat {
		/**
		 * Checks if the specified stack trace element should be included in the displayed stack trace.
		 * 
		 * @param elem
		 *            The stack trace element.
		 * @return <code>true</code> to include it in the printed stack trace.
		 */
		public boolean includeStackTraceElement(StackTraceElement elem);

		/**
		 * Checks if a count line should be displayed in the place of skipped stack trace elements.
		 * 
		 * @return <code>true</code> to print a skip count.
		 * @see #includeStackTraceElement(StackTraceElement)
		 */
		public boolean isIncludeSkippedCount();

		/**
		 * Checks if the script trace should be included in the displayed exception trace.
		 * <p>
		 * The default implementation returns <code>true</code>.
		 * 
		 * @return <code>true</code> to print the script trace.
		 * @see ScriptPositionedExceptionView
		 */
		public default boolean isIncludeScriptTrace() {
			return true;
		}
	}

	/**
	 * Common {@link ExceptionFormat} implementation enumeration.
	 */
	public enum CommonExceptionFormat implements ExceptionFormat {
		/**
		 * Exception format for displaying everything, including script and all stack trace.
		 */
		FULL(false) {
			@Override
			public boolean includeStackTraceElement(StackTraceElement elem) {
				return true;
			}
		},
		/**
		 * Exception format simplar to {@link #COMPACT}, but displays the skipped frame count.
		 */
		REDUCED(true) {
			@Override
			public boolean includeStackTraceElement(StackTraceElement elem) {
				return COMPACT.includeStackTraceElement(elem);
			}
		},
		/**
		 * Compacted exception format that skips the frames which have their classes under the package
		 * <code>saker.build</code> and doesn't print the skipped frames count.
		 */
		COMPACT(false) {
			@Override
			public boolean includeStackTraceElement(StackTraceElement elem) {
				String cname = elem.getClassName();
				if (cname.startsWith("saker.build.")) {
					return false;
				}
				if (cname.equals("java.lang.Object")) {
					//do not include stack traces like wait()
					return false;
				}
				return true;
			}
		},
		/**
		 * Exception format that only includes script traces, but no Java stack traces.
		 * 
		 * @since saker.build 0.8.11
		 */
		SCRIPT_ONLY(false) {
			@Override
			public boolean includeStackTraceElement(StackTraceElement elem) {
				return false;
			}
		},
		/**
		 * Exception format that only includes Java stack traces, but no script traces.
		 * 
		 * @since saker.build 0.8.11
		 */
		JAVA_TRACE(true) {
			@Override
			public boolean includeStackTraceElement(StackTraceElement elem) {
				return true;
			}

			@Override
			public boolean isIncludeScriptTrace() {
				return false;
			}
		},
		/**
		 * Exception format that doesn't include any trace information only the exception headers.
		 * 
		 * @since saker.build 0.8.16
		 */
		NO_TRACE(false) {
			@Override
			public boolean includeStackTraceElement(StackTraceElement elem) {
				return false;
			}

			@Override
			public boolean isIncludeScriptTrace() {
				return false;
			}
		}

		;

		/**
		 * The default format to display the exceptions in.
		 * <p>
		 * It is {@link #COMPACT} by default.
		 * <p>
		 * The value is configurable using the {@link PropertyNames#PROPERTY_DEFAULT_EXCEPTION_FORMAT} system property.
		 */
		public static final ExceptionFormat DEFAULT_FORMAT;
		static {
			ExceptionFormat defaultvalue = COMPACT;
			String excformatproperty = PropertyNames.getProperty(PropertyNames.PROPERTY_DEFAULT_EXCEPTION_FORMAT);
			if (!ObjectUtils.isNullOrEmpty(excformatproperty)) {
				try {
					defaultvalue = valueOf(excformatproperty);
				} catch (IllegalArgumentException e) {
					//may be not found. try again by uppercasing and trimming it
					try {
						defaultvalue = valueOf(excformatproperty.toUpperCase(Locale.ENGLISH).trim());
					} catch (IllegalArgumentException e2) {
						e.addSuppressed(e2);
						try {
							InternalBuildTraceImpl.ignoredStaticException(e);
						} catch (LinkageError le) {
							// some classes aren't found for build trace, or others...
							// ignore this
						} catch (Throwable e3) {
							//other errors are not acceptable though
							e3.addSuppressed(e);
							throw e3;
						}
					}
				}
			}
			DEFAULT_FORMAT = defaultvalue;
		}

		private final boolean includeSkipped;

		private CommonExceptionFormat(boolean includeSkipped) {
			this.includeSkipped = includeSkipped;
		}

		@Override
		public abstract boolean includeStackTraceElement(StackTraceElement elem);

		@Override
		public boolean isIncludeSkippedCount() {
			return includeSkipped;
		}

		@Override
		public boolean isIncludeScriptTrace() {
			return true;
		}
	}

	/**
	 * Prints a formatted exception view to the {@linkplain System#err standard error} with the default exception
	 * format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @throws NullPointerException
	 *             If the exception view is <code>null</code>.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 */
	public static void printFormatException(ExceptionView exc) throws NullPointerException {
		try {
			printStackTraceImpl(exc, System.err, null, CommonExceptionFormat.DEFAULT_FORMAT);
		} catch (IOException e) {
			//PrintStream shouldn't throw IOException
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Prints a formatted exception view to the {@linkplain System#err standard error} with the default exception
	 * format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param workingdir
	 *            The path to relativize script trace element paths, or <code>null</code> to don't relativize.
	 * @throws NullPointerException
	 *             If the exception view is <code>null</code>.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @see ScriptPositionedExceptionView
	 */
	public static void printFormatException(ExceptionView exc, SakerPath workingdir) throws NullPointerException {
		printFormatException(exc, workingdir, System.err);
	}

	/**
	 * Prints a formatted exception view to the specified stream with the default exception format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param workingdir
	 *            The path to relativize script trace element paths, or <code>null</code> to don't relativize.
	 * @param ps
	 *            The output stream.
	 * @throws NullPointerException
	 *             If the exception view or the output is <code>null</code>.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @see ScriptPositionedExceptionView
	 */
	public static void printFormatException(ExceptionView exc, SakerPath workingdir, PrintStream ps)
			throws NullPointerException {
		try {
			printStackTraceImpl(exc, ps, workingdir, CommonExceptionFormat.DEFAULT_FORMAT);
		} catch (IOException e) {
			//PrintStream shouldn't throw IOException
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Prints a formatted exception view to the specified output with the default exception format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param workingdir
	 *            The path to relativize script trace element paths, or <code>null</code> to don't relativize.
	 * @param ps
	 *            The output.
	 * @throws NullPointerException
	 *             If the exception view or the output is <code>null</code>.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @see ScriptPositionedExceptionView
	 * @since saker.build 0.8.16
	 */
	public static void printFormatException(ExceptionView exc, SakerPath workingdir, Appendable ps)
			throws NullPointerException, IOException {
		printStackTraceImpl(exc, ps, workingdir, CommonExceptionFormat.DEFAULT_FORMAT);
	}

	/**
	 * Prints a formatted exception view to the specified string builder with the default exception format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param workingdir
	 *            The path to relativize script trace element paths, or <code>null</code> to don't relativize.
	 * @param ps
	 *            The output.
	 * @throws NullPointerException
	 *             If the exception view or the output is <code>null</code>.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @see ScriptPositionedExceptionView
	 * @since saker.build 0.8.16
	 */
	public static void printFormatException(ExceptionView exc, SakerPath workingdir, StringBuilder ps)
			throws NullPointerException {
		try {
			printStackTraceImpl(exc, ps, workingdir, CommonExceptionFormat.DEFAULT_FORMAT);
		} catch (IOException e) {
			//StringBuilder shouldn't throw IOException
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Prints a formatted exception view to the specified stream with the default exception format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param ps
	 *            The output stream.
	 * @throws NullPointerException
	 *             If the exception view or the output is <code>null</code>.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 */
	public static void printFormatException(ExceptionView exc, PrintStream ps) throws NullPointerException {
		try {
			printStackTraceImpl(exc, ps, null, CommonExceptionFormat.DEFAULT_FORMAT);
		} catch (IOException e) {
			//PrintStream shouldn't throw IOException
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Prints a formatted exception view to the specified output with the default exception format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param ps
	 *            The output.
	 * @throws NullPointerException
	 *             If the exception view or the output is <code>null</code>.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @since saker.build 0.8.16
	 */
	public static void printFormatException(ExceptionView exc, Appendable ps) throws NullPointerException, IOException {
		printStackTraceImpl(exc, ps, null, CommonExceptionFormat.DEFAULT_FORMAT);
	}

	/**
	 * Prints a formatted exception view to the specified string builder with the default exception format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param ps
	 *            The output.
	 * @throws NullPointerException
	 *             If the exception view or the output is <code>null</code>.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @since saker.build 0.8.16
	 */
	public static void printFormatException(ExceptionView exc, StringBuilder ps) throws NullPointerException {
		try {
			printStackTraceImpl(exc, ps, null, CommonExceptionFormat.DEFAULT_FORMAT);
		} catch (IOException e) {
			//StringBuilder shouldn't throw IOException
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Prints a formatted exception view to the {@linkplain System#err standard error} with the specified exception
	 * format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param format
	 *            The exception format. {@link CommonExceptionFormat#DEFAULT_FORMAT} in case of <code>null</code>.
	 * @throws NullPointerException
	 *             If the exception view is <code>null</code>.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 */
	public static void printFormatException(ExceptionView exc, ExceptionFormat format) throws NullPointerException {
		try {
			printStackTraceImpl(exc, System.err, null, format);
		} catch (IOException e) {
			//PrintStream shouldn't throw IOException
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Prints a formatted exception view to the {@linkplain System#err standard error} with the specified exception
	 * format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param workingdir
	 *            The path to relativize script trace element paths, or <code>null</code> to don't relativize.
	 * @param format
	 *            The exception format. {@link CommonExceptionFormat#DEFAULT_FORMAT} in case of <code>null</code>.
	 * @throws NullPointerException
	 *             If the exception view is <code>null</code>.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @see ScriptPositionedExceptionView
	 */
	public static void printFormatException(ExceptionView exc, SakerPath workingdir, ExceptionFormat format)
			throws NullPointerException {
		printFormatException(exc, System.err, workingdir, format);
	}

	/**
	 * Prints a formatted exception view to the specified stream with the specified exception format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param ps
	 *            The output stream.
	 * @param workingdir
	 *            The path to relativize script trace element paths, or <code>null</code> to don't relativize.
	 * @param format
	 *            The exception format. {@link CommonExceptionFormat#DEFAULT_FORMAT} in case of <code>null</code>.
	 * @throws NullPointerException
	 *             If the exception view or the output is <code>null</code>.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @see ScriptPositionedExceptionView
	 */
	public static void printFormatException(ExceptionView exc, PrintStream ps, SakerPath workingdir,
			ExceptionFormat format) throws NullPointerException {
		try {
			printStackTraceImpl(exc, ps, workingdir, format);
		} catch (IOException e) {
			//PrintStream shouldn't throw IOException
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Prints a formatted exception view to the specified output with the specified exception format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param ps
	 *            The output.
	 * @param workingdir
	 *            The path to relativize script trace element paths, or <code>null</code> to don't relativize.
	 * @param format
	 *            The exception format. {@link CommonExceptionFormat#DEFAULT_FORMAT} in case of <code>null</code>.
	 * @throws NullPointerException
	 *             If the exception view or the output is <code>null</code>.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @see ScriptPositionedExceptionView
	 * @since saker.build 0.8.16
	 */
	public static void printFormatException(ExceptionView exc, Appendable ps, SakerPath workingdir,
			ExceptionFormat format) throws NullPointerException, IOException {
		printStackTraceImpl(exc, ps, workingdir, format);
	}

	/**
	 * Prints a formatted exception view to the specified string builder with the specified exception format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param ps
	 *            The output.
	 * @param workingdir
	 *            The path to relativize script trace element paths, or <code>null</code> to don't relativize.
	 * @param format
	 *            The exception format. {@link CommonExceptionFormat#DEFAULT_FORMAT} in case of <code>null</code>.
	 * @throws NullPointerException
	 *             If the exception view or the output is <code>null</code>.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @see ScriptPositionedExceptionView
	 * @since saker.build 0.8.16
	 */
	public static void printFormatException(ExceptionView exc, StringBuilder ps, SakerPath workingdir,
			ExceptionFormat format) throws NullPointerException {
		try {
			printStackTraceImpl(exc, ps, workingdir, format);
		} catch (IOException e) {
			//StringBuilder shouldn't throw IOException
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Prints a formatted exception view to the specified stream with the specified exception format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param ps
	 *            The output stream.
	 * @param format
	 *            The exception format. {@link CommonExceptionFormat#DEFAULT_FORMAT} in case of <code>null</code>.
	 * @throws NullPointerException
	 *             If the exception view or the output is <code>null</code>.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 */
	public static void printFormatException(ExceptionView exc, PrintStream ps, ExceptionFormat format)
			throws NullPointerException {
		try {
			printStackTraceImpl(exc, ps, null, format);
		} catch (IOException e) {
			//PrintStream shouldn't throw IOException
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Prints a formatted exception view to the specified output with the specified exception format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param ps
	 *            The output.
	 * @param format
	 *            The exception format. {@link CommonExceptionFormat#DEFAULT_FORMAT} in case of <code>null</code>.
	 * @throws NullPointerException
	 *             If the exception view or the output is <code>null</code>.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @since saker.build 0.8.16
	 */
	public static void printFormatException(ExceptionView exc, Appendable ps, ExceptionFormat format)
			throws NullPointerException, IOException {
		printStackTraceImpl(exc, ps, null, format);
	}

	/**
	 * Prints a formatted exception view to the specified string builder with the specified exception format.
	 * 
	 * @param exc
	 *            The exception view.
	 * @param ps
	 *            The output.
	 * @param format
	 *            The exception format. {@link CommonExceptionFormat#DEFAULT_FORMAT} in case of <code>null</code>.
	 * @throws NullPointerException
	 *             If the exception view or the output is <code>null</code>.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @since saker.build 0.8.16
	 */
	public static void printFormatException(ExceptionView exc, StringBuilder ps, ExceptionFormat format)
			throws NullPointerException {
		try {
			printStackTraceImpl(exc, ps, null, format);
		} catch (IOException e) {
			//StringBuilder shouldn't throw IOException
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Prints multiple formatted exception views to the specified stream with the specified exception format.
	 * 
	 * @param exceptions
	 *            The exceptions to print.
	 * @param ps
	 *            The output stream.
	 * @param workingdir
	 *            The path to relativize script trace element paths, or <code>null</code> to don't relativize.
	 * @param format
	 *            The exception format. {@link CommonExceptionFormat#DEFAULT_FORMAT} in case of <code>null</code>.
	 * @throws NullPointerException
	 *             If the exceptions, or output stream arguments are <code>null</code>.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @see ScriptPositionedExceptionView
	 */
	public static void printFormatExceptions(Iterable<? extends ExceptionView> exceptions, PrintStream ps,
			SakerPath workingdir, ExceptionFormat format) throws NullPointerException {
		try {
			printFormatExceptionsImpl(exceptions, ps, workingdir, format);
		} catch (IOException e) {
			//PrintStream shouldn't throw IOException
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Prints multiple formatted exception views to the specified output with the specified exception format.
	 * 
	 * @param exceptions
	 *            The exceptions to print.
	 * @param ps
	 *            The output.
	 * @param workingdir
	 *            The path to relativize script trace element paths, or <code>null</code> to don't relativize.
	 * @param format
	 *            The exception format. {@link CommonExceptionFormat#DEFAULT_FORMAT} in case of <code>null</code>.
	 * @throws NullPointerException
	 *             If the exceptions, or output stream arguments are <code>null</code>.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @see ScriptPositionedExceptionView
	 * @since saker.build 0.8.16
	 */
	public static void printFormatExceptions(Iterable<? extends ExceptionView> exceptions, Appendable ps,
			SakerPath workingdir, ExceptionFormat format) throws NullPointerException, IOException {
		printFormatExceptionsImpl(exceptions, ps, workingdir, format);
	}

	/**
	 * Prints multiple formatted exception views to the specified string builder with the specified exception format.
	 * 
	 * @param exceptions
	 *            The exceptions to print.
	 * @param ps
	 *            The output.
	 * @param workingdir
	 *            The path to relativize script trace element paths, or <code>null</code> to don't relativize.
	 * @param format
	 *            The exception format. {@link CommonExceptionFormat#DEFAULT_FORMAT} in case of <code>null</code>.
	 * @throws NullPointerException
	 *             If the exceptions, or output stream arguments are <code>null</code>.
	 * @see CommonExceptionFormat#DEFAULT_FORMAT
	 * @see ScriptPositionedExceptionView
	 * @since saker.build 0.8.16
	 */
	public static void printFormatExceptions(Iterable<? extends ExceptionView> exceptions, StringBuilder ps,
			SakerPath workingdir, ExceptionFormat format) throws NullPointerException {
		try {
			printFormatExceptionsImpl(exceptions, ps, workingdir, format);
		} catch (IOException e) {
			//StringBuilder shouldn't throw IOException
			throw new UncheckedIOException(e);
		}
	}

	private static void printFormatExceptionsImpl(Iterable<? extends ExceptionView> exceptions, Appendable ps,
			SakerPath workingdir, ExceptionFormat format) throws NullPointerException, IOException {
		Objects.requireNonNull(exceptions, "exceptions");
		Iterator<? extends ExceptionView> it = exceptions.iterator();
		if (!it.hasNext()) {
			return;
		}

		Objects.requireNonNull(ps, "output");
		String ls = System.lineSeparator();
		while (true) {
			ExceptionView exc = it.next();
			Set<ExceptionView> printed = ObjectUtils.newIdentityHashSet();
			printEnclosedStackTrace(exc, ps, ObjectUtils.EMPTY_STACK_TRACE_ELEMENT_ARRAY,
					ScriptPositionedExceptionView.EMPTY_STACKTRACE_ARRAY, NO_CAPTION, "", printed, workingdir, format,
					ls);
			if (!it.hasNext()) {
				break;
			}
			ps.append(ls);
		}
	}

	private static final String SUPPRESSED_CAPTION = "Suppressed: ";
	private static final String CAUSE_CAPTION = "Caused by: ";
	private static final String NO_CAPTION = "";

	private static void printStackTraceImpl(ExceptionView ev, Appendable ps, SakerPath workingdir,
			ExceptionFormat format) throws NullPointerException, IOException {
		Objects.requireNonNull(ev, "exception view");
		Objects.requireNonNull(ps, "output");
		if (format == null) {
			format = CommonExceptionFormat.DEFAULT_FORMAT;
		}

		String ls = System.lineSeparator();
		String evstr = Objects.toString(ev, null);
		if (evstr != null) {
			ps.append(evstr);
			ps.append(ls);
		}

		StackTraceElement[] trace = ev.getStackTrace();
		int skipcount = 0;
		for (StackTraceElement traceElement : trace) {
			if (!format.includeStackTraceElement(traceElement)) {
				++skipcount;
				continue;
			}
			if (skipcount > 0) {
				if (format.isIncludeSkippedCount()) {
					ps.append("\t... (skipped " + skipcount + " frames)");
					ps.append(ls);
				}
				skipcount = 0;
			}
			ps.append("\tat " + traceElement);
			ps.append(ls);
		}
		if (skipcount > 0) {
			if (format.isIncludeSkippedCount()) {
				ps.append("\t... (skipped " + skipcount + " frames)");
				ps.append(ls);
			}
		}
		ScriptPositionStackTraceElement[] scripttrace = ScriptPositionedExceptionView.EMPTY_STACKTRACE_ARRAY;
		if (ev instanceof ScriptPositionedExceptionView) {
			ScriptPositionStackTraceElement[] posst = ((ScriptPositionedExceptionView) ev).getPositionStackTrace();
			if (posst.length > 0) {
				for (int i = 0; i < posst.length; i++) {
					ScriptPositionStackTraceElement posste = posst[i];
					ps.append("\t  at " + posste.toString(workingdir));
					ps.append(ls);
				}
			}
			scripttrace = posst;
		}

		Set<ExceptionView> printed = ObjectUtils.newIdentityHashSet();
		printed.add(ev);
		for (ExceptionView se : ev.getSuppressed()) {
			printEnclosedStackTrace(se, ps, trace, scripttrace, SUPPRESSED_CAPTION, "\t", printed, workingdir, format,
					ls);
		}

		ExceptionView cause = ev.getCause();
		if (cause != null) {
			printEnclosedStackTrace(cause, ps, trace, scripttrace, CAUSE_CAPTION, "", printed, workingdir, format, ls);
		}
	}

	private static int getFramesInCommon(ScriptPositionStackTraceElement[] enclosingTrace,
			ScriptPositionStackTraceElement[] trace) {
		int m = trace.length - 1;
		int n = enclosingTrace.length - 1;
		int framesincommon = 0;
		while (m >= 0 && n >= 0) {
			ScriptPositionStackTraceElement tm = trace[m];
			ScriptPositionStackTraceElement etn = enclosingTrace[n];
			m--;
			n--;
			if (!tm.equals(etn)) {
				break;
			}
			++framesincommon;
		}
		return framesincommon;
	}

	private static void printEnclosedStackTrace(ExceptionView ev, Appendable ps, StackTraceElement[] enclosingTrace,
			ScriptPositionStackTraceElement[] enclosingscripttrace, String caption, String prefix,
			Set<ExceptionView> printed, SakerPath workingdir, ExceptionFormat format, String ls) throws IOException {
		if (!printed.add(ev)) {
			ps.append(prefix + caption + "\t[CIRCULAR REFERENCE:" + ev + "]");
			ps.append(ls);
		} else {
			StackTraceElement[] trace = ev.getStackTrace();
			int m = trace.length - 1;
			int n = enclosingTrace.length - 1;
			int framesincommon = 0;
			int skipframesincommon = 0;
			while (m >= 0 && n >= 0) {
				StackTraceElement tm = trace[m];
				StackTraceElement etn = enclosingTrace[n];
				m--;
				n--;
				boolean accepttm = format.includeStackTraceElement(tm);
				boolean acceptetn = format.includeStackTraceElement(etn);
				if (acceptetn != accepttm) {
					break;
				}
				++skipframesincommon;
				if (!acceptetn) {
					//they don't display, dont count them as common
					continue;
				}
				if (!tm.equals(etn)) {
					--skipframesincommon;
					break;
				}
				++framesincommon;
			}
			ScriptPositionStackTraceElement[] scripttrace = enclosingscripttrace;

			// Print our stack trace
			ps.append(prefix + caption + ev);
			ps.append(ls);
			if (format.isIncludeScriptTrace() && ev instanceof ScriptPositionedExceptionView) {
				ScriptPositionedExceptionView spev = (ScriptPositionedExceptionView) ev;
				scripttrace = spev.getPositionStackTrace();
				int scriptframesincommon = getFramesInCommon(enclosingscripttrace, scripttrace);
				if (scriptframesincommon != scripttrace.length) {
					//if all the script frames are common with the previous one, then don't print anything
					if (scripttrace.length > 0) {
						for (int i = 0; i < scripttrace.length - scriptframesincommon; i++) {
							ScriptPositionStackTraceElement posste = scripttrace[i];
							ps.append(prefix + "\t  at " + posste.toString(workingdir));
							ps.append(ls);
						}
						if (scriptframesincommon > 0) {
							ps.append(prefix + "\t  ... (" + scriptframesincommon + " more)");
							ps.append(ls);
						}
					}
				}
			}
			int skipcount = 0;
			for (int i = 0; i < trace.length - skipframesincommon; i++) {
				StackTraceElement st = trace[i];
				if (!format.includeStackTraceElement(st)) {
					if (format.isIncludeSkippedCount()) {
						skipcount++;
					}
					continue;
				}
				if (format.isIncludeSkippedCount() && skipcount > 0) {
					ps.append(prefix + "\t... (skipped " + skipcount + " frames)");
					ps.append(ls);
					skipcount = 0;
				}
				ps.append(prefix + "\tat " + st);
				ps.append(ls);
			}
			if (framesincommon > 0 || skipcount > 0) {
				StringBuilder sb = new StringBuilder();
				sb.append(prefix);
				sb.append("\t... (");
				if (format.isIncludeSkippedCount() && skipcount > 0) {
					sb.append("skipped " + skipcount + " frames");
					if (framesincommon > 0) {
						sb.append(", ");
					}
				}
				if (framesincommon > 0) {
					sb.append(framesincommon + " more");
				}
				sb.append(")");
				ps.append(sb.toString());
				ps.append(ls);
			}

			for (ExceptionView se : ev.getSuppressed()) {
				printEnclosedStackTrace(se, ps, trace, scripttrace, SUPPRESSED_CAPTION, prefix + "\t", printed,
						workingdir, format, ls);
			}

			ExceptionView ourCause = ev.getCause();
			if (ourCause != null) {
				printEnclosedStackTrace(ourCause, ps, trace, scripttrace, CAUSE_CAPTION, prefix + "", printed,
						workingdir, format, ls);
			}
		}
	}
}
