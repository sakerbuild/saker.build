package testing.saker.build.tests.tasks.script;

import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.VariablesMetricEnvironmentTestCase;

public abstract class DefaultsFileTestCase extends VariablesMetricEnvironmentTestCase {
	public static final SakerPath DEFAULT_DEFAULTS_FILE_PATH = PATH_WORKING_DIRECTORY.resolve("defaults.build");

	@Override
	protected CollectingTestMetric createMetricImpl() {
		CollectingTestMetric result = super.createMetricImpl();
		TreeMap<TaskName, TaskFactory<?>> injectedfactories = ObjectUtils.newTreeMap(result.getInjectedTaskFactories());

		result.setInjectedTaskFactories(injectedfactories);
		return result;
	}

}
