package testing.saker.build.tests.tasks.script;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import saker.build.file.path.WildcardPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptOptionsConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;

@SakerTest
public class ScriptOptionsMultiRerunChangeTest extends CollectingMetricEnvironmentTestCase {

	private static class ScriptFileReferencingTaskFactory implements TaskFactory<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String value;

		/**
		 * For {@link Externalizable}.
		 */
		public ScriptFileReferencingTaskFactory() {
		}

		public ScriptFileReferencingTaskFactory(String value) {
			this.value = value;
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return new Task<Object>() {
				@Override
				public Object run(TaskContext taskcontext) throws Exception {
					executioncontext.getTargetConfiguration(taskcontext, taskcontext.getTaskUtilities()
							.resolveFileAtPath(PATH_WORKING_DIRECTORY.resolve("saker.build")));
					return value;
				}
			};
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(value);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = SerialUtils.readExternalObject(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((value == null) ? 0 : value.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ScriptFileReferencingTaskFactory other = (ScriptFileReferencingTaskFactory) obj;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "ScriptFileReferencingTaskFactory[value=" + value + "]";
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		ExecutionScriptConfiguration.Builder builder = ExecutionScriptConfiguration.builder();
		builder.addConfig(WildcardPath.valueOf("**/*.build"),
				new ScriptOptionsConfig(Collections.emptyMap(), ScriptProviderLocation.getBuiltin()));
		parameters.setScriptConfiguration(builder.build());

		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory()
				.add(strTaskId("first"), new ScriptFileReferencingTaskFactory("first"))
				.add(strTaskId("second"), new ScriptFileReferencingTaskFactory("second"));

		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("first"), "first").contains(strTaskId("second"),
				"second");

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdResults());

		builder = ExecutionScriptConfiguration.builder();
		Map<String, String> options = new TreeMap<>();
		options.put("o1", "v1");
		builder.addConfig(WildcardPath.valueOf("**/*.build"),
				new ScriptOptionsConfig(options, ScriptProviderLocation.getBuiltin()));
		parameters.setScriptConfiguration(builder.build());
		runTask("main", main);
		//both of the target configuration referencing tasks should rerun
		getMetric().getRunTaskIdResults().entrySet().forEach(System.out::println);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("first"), "first").contains(strTaskId("second"),
				"second");
	}

}
