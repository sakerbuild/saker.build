package testing.saker.build.tests.tasks.script;

import saker.build.task.exception.TaskExecutionFailedException;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class UnassignedVarIncludeTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res;
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("second"), 18L);

		assertTaskException(TaskExecutionFailedException.class, () -> runScriptTask("unassignedoutput"));
	}

}
