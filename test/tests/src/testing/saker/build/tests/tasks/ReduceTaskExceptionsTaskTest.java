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
package testing.saker.build.tests.tasks;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeMap;

import saker.build.exception.ScriptPositionedExceptionView;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.SakerLog.CommonExceptionFormat;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.utils.TaskUtils;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;
import testing.saker.build.flag.TestFlag;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;

@SakerTest
public class ReduceTaskExceptionsTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class FailerTaskFactory extends StatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		public FailerTaskFactory() {
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return new ParameterizableTask<Object>() {
				@SakerInput("")
				public String input;

				@Override
				public Object run(TaskContext taskcontext) throws Exception {
					throw new UnsupportedOperationException();
				}
			};
		}
	}

	private static class UserTaskFactory extends StatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		public UserTaskFactory() {
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return new ParameterizableTask<Object>() {
				@SakerInput("")
				public String input;

				@Override
				public Object run(TaskContext taskcontext) throws Exception {
					return input + input;
				}
			};
		}
	}

	@Override
	protected CollectingTestMetric createMetric() {
		CollectingTestMetric result = super.createMetric();
		TreeMap<TaskName, TaskFactory<?>> injected = ObjectUtils.newTreeMap(result.getInjectedTaskFactories());
		injected.put(TaskName.valueOf("test.failer"), new FailerTaskFactory());
		injected.put(TaskName.valueOf("test.user"), new UserTaskFactory());
		result.setInjectedTaskFactories(injected);
		return result;
	}

	@Override
	public void executeRunning() throws Exception {
		CollectingTestMetric nmetric = createMetric();
		TestFlag.set(nmetric);
		super.executeRunning();
		//print to keep GCing of metric
		System.out.println("ReduceTaskExceptionsTaskTest.executeRunning() " + nmetric);
	}

	@Override
	protected void runTestImpl() throws Throwable {
		testSimple();
		testMulti();
		testTransitive();
	}

	private void testSimple() throws AssertionError {
		SakerPath buildfilepath = PATH_WORKING_DIRECTORY.resolve("saker.build");
		System.out.println(buildfilepath);
		String[] lines = getCleanedExceptionLines(
				environment.run(buildfilepath, "build", parameters, project).getPositionedExceptionView());

		assertEquals(Collections.frequency(Arrays.asList(lines), "java.lang.UnsupportedOperationException"), 1);
		assertEquals(lines[0], "java.lang.UnsupportedOperationException");
		assertEquals(lines[1], "at saker.build:1:9-21");
		assertEquals(lines[2], "at saker.build:1:1-21");
		assertTrue(lines[3]
				.startsWith("at testing.saker.build.tests.tasks.ReduceTaskExceptionsTaskTest$FailerTaskFactory"));
		System.out.println();
	}

	private void testMulti() throws AssertionError {
		SakerPath buildfilepath = PATH_WORKING_DIRECTORY.resolve("multi.build");
		System.out.println(buildfilepath);
		String[] lines = getCleanedExceptionLines(
				environment.run(buildfilepath, "build", parameters, project).getPositionedExceptionView());

		assertEquals(lines.length, 8);
		assertEquals(lines[0], "java.lang.UnsupportedOperationException");
		assertTrue(lines[3]
				.startsWith("at testing.saker.build.tests.tasks.ReduceTaskExceptionsTaskTest$FailerTaskFactory"));
		assertEquals(lines[4], "java.lang.UnsupportedOperationException");

		assertTrue(ArrayUtils.arrayIndexOf(lines, "at multi.build:1:9-22") > 0);
		assertTrue(ArrayUtils.arrayIndexOf(lines, "at multi.build:1:1-22") > 0);

		assertTrue(ArrayUtils.arrayIndexOf(lines, "at multi.build:4:10-23") > 0);
		assertTrue(ArrayUtils.arrayIndexOf(lines, "at multi.build:4:1-23") > 0);
		System.out.println();
	}

	private void testTransitive() throws AssertionError {
		SakerPath buildfilepath = PATH_WORKING_DIRECTORY.resolve("transitive.build");
		System.out.println(buildfilepath);
		String[] lines = getCleanedExceptionLines(
				environment.run(buildfilepath, "build", parameters, project).getPositionedExceptionView());

		//shouldn't display too many lines
		assertEquals(lines.length, 5);
		assertEquals(lines[0], "java.lang.UnsupportedOperationException");
		assertTrue(lines[4]
				.startsWith("at testing.saker.build.tests.tasks.ReduceTaskExceptionsTaskTest$FailerTaskFactory"));

		assertTrue(ArrayUtils.arrayIndexOf(lines, "at transitive.build:1:21-34") > 0);
		assertTrue(ArrayUtils.arrayIndexOf(lines, "at transitive.build:1:9-35") > 0);
		assertTrue(ArrayUtils.arrayIndexOf(lines, "at transitive.build:1:1-35") > 0);

		System.out.println();
	}

	private static String[] getCleanedExceptionLines(ScriptPositionedExceptionView excview) {
		UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
		try (PrintStream ps = new PrintStream(baos)) {
			printCleanedException(excview, ps);
		}
		System.out.println("ReduceTaskExceptionsTaskTest.getCleanedExceptionLines()");
		System.out.print(baos);
		System.out.println("ReduceTaskExceptionsTaskTest.getCleanedExceptionLines()");

		String[] lines = baos.toString().split("[\\n\\r]+");
		for (int i = 0; i < lines.length; i++) {
			lines[i] = lines[i].trim();
		}
		return lines;
	}

	private static void printCleanedException(ScriptPositionedExceptionView excview, PrintStream ps) {
		TaskUtils.printTaskExceptionsOmitTransitive(excview, ps, PATH_WORKING_DIRECTORY, CommonExceptionFormat.COMPACT);
	}

}
