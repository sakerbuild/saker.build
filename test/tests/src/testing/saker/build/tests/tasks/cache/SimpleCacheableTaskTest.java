package testing.saker.build.tests.tasks.cache;

import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class SimpleCacheableTaskTest extends CacheableTaskTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		StringTaskFactory main = new StringTaskFactory("content");
		main.setCapabilities(ImmutableUtils.singletonNavigableSet(TaskFactory.CAPABILITY_CACHEABLE));

		StringTaskFactory modifiedmain = new StringTaskFactory("modifiedcontent");
		modifiedmain.setCapabilities(ImmutableUtils.singletonNavigableSet(TaskFactory.CAPABILITY_CACHEABLE));

		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), main.getResult()).noRemaining();
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("main"));

		cleanProject();
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), main.getResult()).noRemaining();
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("main"));
		waitExecutionFinalization();
		assertEmpty(getMetric().getCachePublishedTasks());

		//ensure that the task factory change is detected
		cleanProject();
		runTask("main", modifiedmain);
		assertEmpty(getMetric().getCacheRetrievedTasks());
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), modifiedmain.getResult()).noRemaining();
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("main"));

		//ensure that the previous unmodified task result is still retrieable from the cache
		cleanProject();
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), main.getResult()).noRemaining();
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("main"));
		waitExecutionFinalization();
		assertEmpty(getMetric().getCachePublishedTasks());
	}

}
