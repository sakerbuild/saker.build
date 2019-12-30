package testing.saker.build.tests.tasks;

import saker.build.task.TaskContext;
import saker.build.task.exception.TaskExecutionFailedException;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class AbortExceptionTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class AbortingTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.abortExecution(new RuntimeException("aborted."));
			return null;
		}
	}

	private static class MultiAbortingTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.abortExecution(new RuntimeException("first"));
			taskcontext.abortExecution(new RuntimeException("second"));
			return null;
		}
	}

	private static class MultiExaminerTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			try {
				taskcontext.getTaskUtilities().runTask(strTaskId("multiabort"), new MultiAbortingTaskFactory());
				fail();
			} catch (TaskExecutionFailedException e) {
				assertEquals(e.getCause().getMessage(), "first");
				assertEquals(e.getSuppressed()[0].getMessage(), "second");
			}
			return "result";
		}
	}

	private static class MultiAbortThrowingTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.abortExecution(new RuntimeException("first"));
			taskcontext.abortExecution(new RuntimeException("second"));
			throw new RuntimeException("thrown");
		}
	}

	private static class MultiThrowingExaminerTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			try {
				taskcontext.getTaskUtilities().runTask(strTaskId("multiabort"), new MultiAbortThrowingTaskFactory());
				fail();
			} catch (TaskExecutionFailedException e) {
				assertEquals(e.getCause().getMessage(), "thrown");
				assertEquals(e.getSuppressed()[0].getMessage(), "first");
				assertEquals(e.getSuppressed()[1].getMessage(), "second");
			}
			return "throwresult";
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		AbortingTaskFactory task = new AbortingTaskFactory();
		assertException(RuntimeException.class, () -> runTask("main", task));
		assertNotEmpty(getMetric().getRunTaskIdFactories());

		assertException(RuntimeException.class, () -> runTask("main", task));
		assertEmpty(getMetric().getRunTaskIdFactories());

		runTask("multiexaminer", new MultiExaminerTaskFactory());
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("multiexaminer")), "result");

		runTask("multithrowexaminer", new MultiThrowingExaminerTaskFactory());
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("multithrowexaminer")), "throwresult");
	}

}
