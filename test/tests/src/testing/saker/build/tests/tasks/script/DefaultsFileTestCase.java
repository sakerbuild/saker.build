package testing.saker.build.tests.tasks.script;

import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.VariablesMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.StatelessTaskFactory;

public abstract class DefaultsFileTestCase extends VariablesMetricEnvironmentTestCase {
	public static final SakerPath DEFAULT_DEFAULTS_FILE_PATH = PATH_WORKING_DIRECTORY.resolve("defaults.build");

	@Override
	protected CollectingTestMetric createMetricImpl() {
		CollectingTestMetric result = super.createMetricImpl();
		TreeMap<TaskName, TaskFactory<?>> injectedfactories = ObjectUtils.newTreeMap(result.getInjectedTaskFactories());
		injectedfactories.put(TaskName.valueOf("example.echo"), new EchoTaskFactory());

		result.setInjectedTaskFactories(injectedfactories);
		return result;
	}

	public static class EchoTaskFactory extends StatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return new ParameterizableTask<String>() {
				@SakerInput
				public String Input;

				@Override
				public String run(TaskContext taskcontext) throws Exception {
					return Input;
				}

			};
		}

	}

}
