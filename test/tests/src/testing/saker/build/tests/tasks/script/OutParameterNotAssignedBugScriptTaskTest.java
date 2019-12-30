package testing.saker.build.tests.tasks.script;

import saker.build.task.exception.TaskExecutionFailedException;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class OutParameterNotAssignedBugScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		files.putFile(PATH_WORKING_DIRECTORY.resolve("saker.build"), "build(\nout x\n) { }");

		assertTaskException(TaskExecutionFailedException.class, () -> runScriptTask("build"));

		System.out.println("OutParameterNotAssignedBugScriptTaskTest.runTestImpl() RUN 2");

		files.putFile(PATH_WORKING_DIRECTORY.resolve("saker.build"), "build(\nout x\n) {\n $x = 3\n }");
		runScriptTask("build");
	}

}
