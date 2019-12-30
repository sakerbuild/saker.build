package testing.saker.build.tests.tasks.bugs;

import java.util.Set;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;

@SakerTest
public class ForeachContentIOTaskTest extends CollectingMetricEnvironmentTestCase {
	//this tests for a proper serialization bug occurred during development
	//solution: ContentWriterObjectOutput serializable class readers should add the serialized object to the index as the first instruction of the reader lambda

	@Override
	protected void runTestImpl() throws Throwable {
		runScriptTask("build");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		// dont use project
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations()).setUseProject(false).build();
	}

}
