package testing.saker.build.tests.tasks.script;

import java.util.Map;
import java.util.TreeMap;

import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.VariablesMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringFileOutputTaskFactory;

@SakerTest
public class BuildFileInTaskModificationTest extends VariablesMetricEnvironmentTestCase {
	@Override
	protected CollectingTestMetric createMetricImpl() {
		CollectingTestMetric result = super.createMetricImpl();
		Map<TaskName, TaskFactory<?>> injectedTaskFactories = new TreeMap<>();
		injectedTaskFactories.put(TaskName.valueOf("test.Replace"),
				new StringFileOutputTaskFactory(PATH_WORKING_DIRECTORY.resolve("second.build"), "fail { }"));
		result.setInjectedTaskFactories(injectedTaskFactories);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		runScriptTask("build");
	}

}
