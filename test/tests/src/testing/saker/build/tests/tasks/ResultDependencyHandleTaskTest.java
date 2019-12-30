package testing.saker.build.tests.tasks;

import java.io.Externalizable;

import saker.build.task.TaskContext;
import saker.build.task.TaskResultCollection;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.utils.StructuredTaskResult;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.MultiTaskOutputChangeDetectorTaskTest.CharAtTaskOutputChangeDetector;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;
import testing.saker.build.tests.tasks.factories.StructuredObjectReturnerTaskFactory;

@SakerTest
public class ResultDependencyHandleTaskTest extends CollectingMetricEnvironmentTestCase {
	private static class ResultHandleUserTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public ResultHandleUserTaskFactory() {
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			TaskResultDependencyHandle handle = StructuredTaskResult
					.getActualTaskResultDependencyHandle(strTaskId("strres"), taskcontext);
			String str = handle.get().toString();
			char c = str.charAt(0);
			handle.setTaskOutputChangeDetector(new CharAtTaskOutputChangeDetector(0, c));
			return Character.toString(c);
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory()
				.add(strTaskId("user"), new ResultHandleUserTaskFactory())
				.add(strTaskId("str"), new StringTaskFactory("val"))
				.add(strTaskId("strres"), new StructuredObjectReturnerTaskFactory(strTaskId("str")));

		TaskResultCollection res;
		res = runTask("main", main);
		assertEquals(res.getTaskResult(strTaskId("user")), "v");

		res = runTask("main", main);
		assertEquals(res.getTaskResult(strTaskId("user")), "v");
		assertEmpty(getMetric().getRunTaskIdFactories());

		main = new ChildTaskStarterTaskFactory().add(strTaskId("user"), new ResultHandleUserTaskFactory())
				.add(strTaskId("str"), new StringTaskFactory("xal"))
				.add(strTaskId("strres"), new StructuredObjectReturnerTaskFactory(strTaskId("str")));
		res = runTask("main", main);
		assertEquals(res.getTaskResult(strTaskId("user")), "x");

		res = runTask("main", main);
		assertEquals(res.getTaskResult(strTaskId("user")), "x");
		assertEmpty(getMetric().getRunTaskIdFactories());

		main = new ChildTaskStarterTaskFactory().add(strTaskId("user"), new ResultHandleUserTaskFactory())
				.add(strTaskId("str"), new StringTaskFactory("xax"))
				.add(strTaskId("strres"), new StructuredObjectReturnerTaskFactory(strTaskId("str")));
		res = runTask("main", main);
		assertEquals(res.getTaskResult(strTaskId("user")), "x");
		assertFalse(getMetric().getRunTaskIdResults().containsKey(strTaskId("user")));
	}

}
