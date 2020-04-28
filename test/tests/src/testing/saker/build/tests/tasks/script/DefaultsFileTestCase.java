package testing.saker.build.tests.tasks.script;

import java.util.Collections;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptOptionsConfig;
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
		injectedfactories.put(TaskName.valueOf("example.concat"), new ConcatTaskFactory());

		result.setInjectedTaskFactories(injectedfactories);
		return result;
	}

	protected static void setDefaultsFileScriptOption(ExecutionParametersImpl params, String val) {
		ExecutionScriptConfiguration.Builder scbuilder = ExecutionScriptConfiguration.builder();
		scbuilder.addConfig(WildcardPath.valueOf("**/*.build"),
				new ScriptOptionsConfig(
						val == null ? Collections.emptyMap() : Collections.singletonMap("defaults.file", val),
						ExecutionScriptConfiguration.ScriptProviderLocation.getBuiltin()));
		params.setScriptConfiguration(scbuilder.build());
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

	public static class ConcatTaskFactory extends StatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return new ParameterizableTask<String>() {
				@SakerInput
				public String Left;
				@SakerInput
				public String Right;

				@Override
				public String run(TaskContext taskcontext) throws Exception {
					return Left + Right;
				}

			};
		}
	}

}
