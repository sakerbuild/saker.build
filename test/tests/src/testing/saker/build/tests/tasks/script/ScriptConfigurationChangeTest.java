package testing.saker.build.tests.tasks.script;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import saker.build.file.path.WildcardPath;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptOptionsConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class ScriptConfigurationChangeTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		ExecutionScriptConfiguration.Builder builder = ExecutionScriptConfiguration.builder();
		builder.addConfig(WildcardPath.valueOf("**/*.build"),
				new ScriptOptionsConfig(Collections.emptyMap(), ScriptProviderLocation.getBuiltin()));
		parameters.setScriptConfiguration(builder.build());
		runScriptTask("build");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		builder = ExecutionScriptConfiguration.builder();
		Map<String, String> options = new TreeMap<>();
		options.put("o1", "v1");
		builder.addConfig(WildcardPath.valueOf("**/*.build"),
				new ScriptOptionsConfig(options, ScriptProviderLocation.getBuiltin()));
		parameters.setScriptConfiguration(builder.build());
		runScriptTask("build");
		//some tasks should be rerun, as the options were changed for the script file
		assertNotEmpty(getMetric().getRunTaskIdResults());

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
	}
}
