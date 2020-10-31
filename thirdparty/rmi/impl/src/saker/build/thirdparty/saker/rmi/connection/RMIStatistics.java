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
package saker.build.thirdparty.saker.rmi.connection;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;

import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ImmutableUtils;

/**
 * Holds RMI statistics collected during the lifetime of an RMI connection.
 * <p>
 * The class holds various statistics about the RMI method invocations that occurred during an RMI connection. The
 * collection of these statistics are disabled by default and can be turned on by using
 * {@link RMIOptions#collectStatistics(boolean)}.
 * 
 * @since saker.rmi 0.8.2
 * @see RMIOptions#collectStatistics(boolean)
 */
public final class RMIStatistics {
	/**
	 * Encloses statistic information about an RMI method call.
	 */
	public static final class MethodStatistics {
		private Method method;
		private long invocationStartNanos;
		private long invocationEndNanos;

		MethodStatistics(Method executable, long invocationStartNanos, long invocationEndNanos) {
			this.method = executable;
			this.invocationStartNanos = invocationStartNanos;
			this.invocationEndNanos = invocationEndNanos;
		}

		/**
		 * The method that was called.
		 * 
		 * @return The method.
		 */
		public Method getMethod() {
			return method;
		}

		/**
		 * The nano time of the method invocation start.
		 * <p>
		 * The value is retrieved using {@link System#nanoTime()} at the start of the RMI request.
		 * 
		 * @return The nanos of the invocation start.
		 */
		public long getInvocationStartNanos() {
			return invocationStartNanos;
		}

		/**
		 * The nano time of the method invocation end.
		 * <p>
		 * The value is retrieved using {@link System#nanoTime()} when the RMI request result was received.
		 * 
		 * @return The nanos of the invocation end.
		 */
		public long getInvocationEndNanos() {
			return invocationEndNanos;
		}

		/**
		 * Gets the duration of the method call.
		 * <p>
		 * Same as
		 * 
		 * <pre>
		 * {@link #getInvocationEndNanos()} - {@link #getInvocationStartNanos()}
		 * </pre>
		 * 
		 * @return The duration of the method call in nanoseconds.
		 */
		public long getDurationNanos() {
			return invocationEndNanos - invocationStartNanos;
		}

		@Override
		public String toString() {
			return method + ": " + (getDurationNanos() / 1_000) + " us";
		}
	}

	private ConcurrentPrependAccumulator<MethodStatistics> methodStats = new ConcurrentPrependAccumulator<>();
	private ConcurrentSkipListSet<String> inaccessibleInterfaces = new ConcurrentSkipListSet<>();

	RMIStatistics() {
	}

	/**
	 * Gets the collected method statistics.
	 * 
	 * @return A method statistics iterable.
	 */
	public Iterable<? extends MethodStatistics> getMethodStatistics() {
		return methodStats;
	}

	/**
	 * Gets and clears the method statistics.
	 * <p>
	 * Calling this method will clear the method related statistics while return an {@link Iterable} that iterates over
	 * the recorded statistics.
	 * 
	 * @return A method statistics iterable.
	 */
	public Iterable<? extends MethodStatistics> getMethodStatisticsClear() {
		return methodStats.clearAndIterable();
	}

	/**
	 * Dumps a summary of the statistics to the specified output stream.
	 * 
	 * @param out
	 *            The output.
	 * @param timeunit
	 *            A {@link TimeUnit} that specifies the display metrics for the time values. May be <code>null</code>.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public void dumpSummary(PrintStream out, TimeUnit timeunit) throws NullPointerException {
		Objects.requireNonNull(out, "out");
		try {
			dumpSummaryImpl(out, timeunit);
		} catch (IOException e) {
			//PrintStream shouldn't throw
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Dumps a summary of the statistics to the specified output stream.
	 * 
	 * @param out
	 *            The output.
	 * @param timeunit
	 *            A {@link TimeUnit} that specifies the display metrics for the time values. May be <code>null</code>.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public void dumpSummary(Appendable out, TimeUnit timeunit) throws NullPointerException, IOException {
		Objects.requireNonNull(out, "out");
		dumpSummaryImpl(out, timeunit);
	}

	/**
	 * Dumps a summary of the statistics to the specified output stream.
	 * 
	 * @param out
	 *            The output.
	 * @param timeunit
	 *            A {@link TimeUnit} that specifies the display metrics for the time values. May be <code>null</code>.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public void dumpSummary(PrintWriter out, TimeUnit timeunit) throws NullPointerException {
		Objects.requireNonNull(out, "out");
		try {
			dumpSummaryImpl(out, timeunit);
		} catch (IOException e) {
			//PrintStream shouldn't throw
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Dumps a summary of the statistics to the specified output.
	 * 
	 * @param out
	 *            The output.
	 * @param timeunit
	 *            A {@link TimeUnit} that specifies the display metrics for the time values. May be <code>null</code>.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public void dumpSummary(StringBuilder out, TimeUnit timeunit) throws NullPointerException {
		Objects.requireNonNull(out, "out");
		try {
			dumpSummaryImpl(out, timeunit);
		} catch (IOException e) {
			//PrintStream shouldn't throw
			throw new UncheckedIOException(e);
		}
	}

	private void dumpSummaryImpl(Appendable out, TimeUnit timeunit) throws IOException {
		String ls = System.lineSeparator();
		StringBuilder sb = new StringBuilder();
		if (!methodStats.isEmpty()) {
			Map<Method, Entry<Long, Long>> callstats = new HashMap<>();
			for (MethodStatistics ms : methodStats) {
				callstats.compute(ms.getMethod(), (k, v) -> {
					if (v == null) {
						return ImmutableUtils.makeImmutableMapEntry(ms.getDurationNanos(), 1L);
					}
					return ImmutableUtils.makeImmutableMapEntry(ms.getDurationNanos() + v.getKey(), v.getValue() + 1L);
				});
			}
			@SuppressWarnings({ "unchecked", "rawtypes" })
			List<Entry<Method, Entry<Long, Long>>> statlist = Arrays.asList(callstats.entrySet().toArray(new Entry[0]));
			statlist.sort((l, r) -> {
				Method lm = l.getKey();
				Method rm = r.getKey();
				int cmp = lm.getDeclaringClass().getName().compareTo(rm.getDeclaringClass().getName());
				if (cmp != 0) {
					return cmp;
				}
				cmp = lm.getName().compareTo(rm.getName());
				if (cmp != 0) {
					return cmp;
				}
				int lpc = lm.getParameterCount();
				int rpc = rm.getParameterCount();
				int pc = Math.min(lpc, rpc);
				Class<?>[] lpt = lm.getParameterTypes();
				Class<?>[] rpt = rm.getParameterTypes();
				for (int i = 0; i < pc; i++) {
					cmp = lpt[i].getName().compareTo(rpt[i].getName());
					if (cmp != 0) {
						return cmp;
					}
				}
				return Integer.compare(lpc, rpc);
			});
			String metric;
			if (timeunit == null) {
				long minduration = Long.MAX_VALUE;
				for (Entry<Method, Entry<Long, Long>> entry : statlist) {
					Long count = entry.getValue().getValue();
					Long totalduration = entry.getValue().getKey();
					long durationval = totalduration / count;
					if (durationval < minduration) {
						minduration = durationval;
					}
				}
				if (minduration > 5_000_000_000L) {
					timeunit = TimeUnit.SECONDS;
					metric = " s";
				} else if (minduration > 5_000_000L) {
					timeunit = TimeUnit.MILLISECONDS;
					metric = " ms";
				} else if (minduration > 5_000L) {
					timeunit = TimeUnit.MICROSECONDS;
					metric = " us";
				} else {
					timeunit = TimeUnit.NANOSECONDS;
					metric = " ns";
				}
			} else {
				switch (timeunit) {
					case DAYS:
						metric = " d";
						break;
					case HOURS:
						metric = " h";
						break;
					case MICROSECONDS:
						metric = " us";
						break;
					case MILLISECONDS:
						metric = " ms";
						break;
					case MINUTES:
						metric = " m";
						break;
					case NANOSECONDS:
						metric = " ns";
						break;
					case SECONDS:
						metric = " s";
						break;
					default: {
						metric = "";
					}
				}
			}

			sb.setLength(0);
			sb.append("Method call statistics:");
			sb.append(ls);
			out.append(sb);

			for (Entry<Method, Entry<Long, Long>> entry : statlist) {
				Long count = entry.getValue().getValue();
				Long totalduration = entry.getValue().getKey();
				long durationval = totalduration / count;
				String durstr = Long.toString(timeunit.convert(durationval, TimeUnit.NANOSECONDS));

				sb.setLength(0);

				Method m = entry.getKey();
				Class<?>[] ptypes = m.getParameterTypes();
				sb.append(m.getDeclaringClass().getName());
				sb.append('\t');
				sb.append(m.getName());
				if (ptypes.length == 0) {
					sb.append("()");
				} else {
					sb.append("(");
					for (int i = 0;;) {
						Class<?> t = ptypes[i];
						appendTypeName(sb, t);
						if (++i < ptypes.length) {
							sb.append(',');
						} else {
							break;
						}
					}
					sb.append(")");
				}
				sb.append('\t');
				sb.append(durstr);
				sb.append(metric);
				sb.append(" / ");
				sb.append(count.toString());
				sb.append(ls);

				out.append(sb);
			}
		}

		Iterator<String> inaccessibleiterator = inaccessibleInterfaces.iterator();
		if (inaccessibleiterator.hasNext()) {
			sb.setLength(0);
			sb.append("Inaccessible interfaces:");
			sb.append(ls);
			do {
				String name = inaccessibleiterator.next();
				sb.setLength(0);
				sb.append(name);
				sb.append(ls);
			} while (inaccessibleiterator.hasNext());
			out.append(sb);
		}
	}

	private void appendTypeName(StringBuilder sb, Class<?> t) {
		if (t.isArray()) {
			appendTypeName(sb, t.getComponentType());
			sb.append("[]");
		} else {
			sb.append(t.getName());
		}
	}

	//referenced from ASM
	void recordMethodCall(Method executable, long startnanos, long endnanos) {
		methodStats.add(new MethodStatistics(executable, startnanos, endnanos));
	}

	void inaccessibleInterface(Class<?> type) {
		inaccessibleInterfaces.add(type.getName());
	}
}
