package testing.saker.build.tests;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ObjectUtils;

public abstract class VariablesMetricEnvironmentTestCase extends CollectingMetricEnvironmentTestCase {
	protected Map<String, ?> getTaskVariables() {
		return Collections.emptyNavigableMap();
	}

	@Override
	protected final CollectingTestMetric createMetric() {
		CollectingTestMetric metric = createMetricImpl();
		Map<String, ?> variables = getTaskVariables();
		if (!variables.isEmpty()) {
			Map<TaskName, TaskFactory<?>> injectedTaskFactories = ObjectUtils
					.newTreeMap(metric.getInjectedTaskFactories());
			for (Entry<String, ?> entry : variables.entrySet()) {
				String varname = entry.getKey();
				injectedTaskFactories.put(TaskName.valueOf(varname), new LiteralTaskFactory(entry.getValue()));
			}
			metric.setInjectedTaskFactories(injectedTaskFactories);
		}
		return metric;
	}

	protected CollectingTestMetric createMetricImpl() {
		return super.createMetric();
	}

}
