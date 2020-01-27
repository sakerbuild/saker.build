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
package testing.saker;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import saker.java.testing.api.test.exc.JavaTestRunnerFailureException;
import saker.java.testing.api.test.invoker.BasicInstrumentationJavaTestInvoker;
import saker.java.testing.api.test.invoker.TestInvocationParameters;
import saker.java.testing.api.test.invoker.TestInvokerParameters;

public class SakerJavaTestingInvoker extends BasicInstrumentationJavaTestInvoker {
	private static final String PARAM_TIMEOUT_MILLIS = "TimeoutMillis";
	
	private static final StackTraceElement[] EMPTY_STACK_TRACE_ELEMENT_ARRAY = {};

	private static final LinkedList<AutoCloseable> closeables = new LinkedList<>();

	private static Map<String, String> TEST_INVOKER_PARAMETERS = Collections.emptyMap();
	private static int TEST_INTERRUPT_DELAY_MILLIS = 0;

	public static Map<String, String> getTestInvokerParameters() {
		return TEST_INVOKER_PARAMETERS;
	}

	@Override
	public void initTestRunner(ClassLoader testrunnerclassloader, TestInvokerParameters parameters)
			throws JavaTestRunnerFailureException {
		//pre-gc any previously remaining classloaders and others
		//this is useful when loading native libraries
		System.gc();

		Map<String, String> parammap = parameters.getParameters();
		TEST_INVOKER_PARAMETERS = parammap == null ? Collections.emptyMap() : new TreeMap<>(parammap);

		String tmilisparam = parameters.get(PARAM_TIMEOUT_MILLIS);
		if (tmilisparam != null) {
			try {
				int tmilisint = Integer.parseInt(tmilisparam);
				if (tmilisint <= 0) {
					throw new JavaTestRunnerFailureException("Invalid " + PARAM_TIMEOUT_MILLIS + ": " + tmilisint);
				}
				TEST_INTERRUPT_DELAY_MILLIS = tmilisint;
			} catch (NumberFormatException e) {
				throw new JavaTestRunnerFailureException(
						"Failed to parse " + PARAM_TIMEOUT_MILLIS + " parameter as number. (" + tmilisparam + ")", e);
			}
		}
		super.initTestRunner(testrunnerclassloader, parameters);
	}

	@Override
	protected void runTest(TestInvocationParameters parameters)
			throws JavaTestRunnerFailureException, InvocationTargetException {
		try {
			invokeTest(parameters);
		} catch (Throwable e) {
			throw new InvocationTargetException(e);
		}
	}

	private void invokeTest(TestInvocationParameters parameters) throws Throwable {
		String testclassname = parameters.getTestClassName();
		Class<?> tc;
		try {
			tc = Class.forName(testclassname, false, getTestClassLoader());
		} catch (Exception e) {
			throw new AssertionError("Test class not found: " + testclassname, e);
		}
		if (tc.getAnnotation(SakerTest.class) == null) {
			//dont care
			return;
		}
		boolean[] finished = { false };
		Object instance;
		try {
			instance = tc.getConstructor().newInstance();
		} catch (InvocationTargetException e) {
			throw new AssertionError("Failed to construct new test case instance for: " + tc, e.getCause());
		} catch (Throwable e) {
			throw new AssertionError("Failed to construct new test case instance for: " + tc, e);
		}
		Throwable[] exc = { null };
		ThreadGroup subgroup = new ThreadGroup("Main sub group");
		Thread testingthread = new Thread(subgroup, "Test: " + testclassname) {
			@Override
			public void run() {
				try {
					tc.getMethod("runTest", Map.class).invoke(instance, parameters.getParameters());
				} catch (InvocationTargetException e) {
					exc[0] = e.getCause();
				} catch (Throwable e) {
					exc[0] = e;
				}
			}
		};
		Thread timeout = startTimoutThread(testclassname, finished, testingthread);
		try {
			testingthread.start();
			while (true) {
				try {
					testingthread.join();
					break;
				} catch (InterruptedException e) {
					testingthread.interrupt();
					if (timeout != null) {
						timeout.interrupt();
					}
				}
			}

			if (exc[0] != null) {
				AssertionError e = new AssertionError("Test case failed: " + testclassname, exc[0]);
				//clear the stack trace as it is irrelevant for the test case failure
				e.setStackTrace(EMPTY_STACK_TRACE_ELEMENT_ARRAY);
				throw e;
			}
		} finally {
			synchronized (finished) {
				finished[0] = true;
				Thread.interrupted();
				if (timeout != null) {
					timeout.interrupt();
				}
			}
			if (timeout != null) {
				timeout.join();
			}
		}
	}

	private static Thread startTimoutThread(String tcname, boolean[] finished, Thread tointerrupt) {
		if (TEST_INTERRUPT_DELAY_MILLIS <= 0) {
			return null;
		}
		Thread t = new Thread() {
			@Override
			public void run() {
				String cause;
				try {
					Thread.sleep(TEST_INTERRUPT_DELAY_MILLIS);
					cause = "timeout";
				} catch (InterruptedException e) {
					cause = "interrupted";
				}
				synchronized (finished) {
					if (!finished[0]) {
						Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
						//synchronize so we print the trace in a block
						synchronized (System.err) {
							System.err.println("Info: " + cause + " on " + tcname);
							for (Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
								if (entry.getKey() == this) {
									continue;
								}
								System.err.println("Thread: " + entry.getKey());
								for (StackTraceElement ste : entry.getValue()) {
									System.err.println("    " + ste);
								}
							}
						}
						ThreadGroup tg = tointerrupt.getThreadGroup();
						tg.interrupt();
					}
				}
			}
		};
		t.setDaemon(true);
		t.start();
		return t;
	}

	@Override
	public void close() throws IOException {
		try {
			Exception exc = null;
			while (true) {
				AutoCloseable c;
				synchronized (SakerJavaTestingInvoker.class) {
					if (closeables.isEmpty()) {
						break;
					}
					c = closeables.pollLast();
				}
				try {
					c.close();
				} catch (Exception e) {
					if (exc == null) {
						exc = e;
					} else {
						exc.addSuppressed(e);
					}
				}
			}

			try {
				super.close();
			} catch (IOException e) {
				if (exc == null) {
					exc = e;
				} else {
					exc.addSuppressed(e);
				}
			}
			if (exc != null) {
				if (exc instanceof IOException) {
					throw (IOException) exc;
				}
				throw new IOException(exc);
			}
		} finally {
			//call the garbage collector so classloaders and others can be garbage collected
			//this is useful when loading native libraries
			System.gc();
		}
	}

	public static synchronized void addCloseable(AutoCloseable c) {
		closeables.add(c);
	}
}
