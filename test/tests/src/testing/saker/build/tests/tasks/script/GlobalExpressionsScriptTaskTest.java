package testing.saker.build.tests.tasks.script;

import saker.build.task.TaskResultCollection;
import saker.build.task.identifier.GlobalValueTaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class GlobalExpressionsScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		TaskResultCollection taskres;

		//chooses the default generated target for the global expressions
		taskres = runScriptTask("build").resultCollection;
		assertEquals(StructuredTaskResult.getActualTaskResult(new GlobalValueTaskIdentifier("GlobalVar"), taskres),
				123L);
	}

}
