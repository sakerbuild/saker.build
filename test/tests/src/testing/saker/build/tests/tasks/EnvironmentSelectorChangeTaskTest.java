package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.util.property.SystemPropertyEnvironmentProperty;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class EnvironmentSelectorChangeTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class SelectorTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private transient TaskExecutionEnvironmentSelector selector;

		/**
		 * For {@link Externalizable}.
		 */
		public SelectorTaskFactory() {
		}

		public SelectorTaskFactory(TaskExecutionEnvironmentSelector selector) {
			this.selector = selector;
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			return "val";
		}

		@Override
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return selector;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(selector);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			selector = (TaskExecutionEnvironmentSelector) in.readObject();
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	private static class UserParameterEnvironmentSelector implements TaskExecutionEnvironmentSelector, Externalizable {
		private static final long serialVersionUID = 1L;

		private String userParamName;

		/**
		 * For {@link Externalizable}.
		 */
		public UserParameterEnvironmentSelector() {
		}

		public UserParameterEnvironmentSelector(String userParamName) {
			this.userParamName = userParamName;
		}

		@Override
		public EnvironmentSelectionResult isSuitableExecutionEnvironment(SakerEnvironment environment) {
			SystemPropertyEnvironmentProperty prop = new SystemPropertyEnvironmentProperty(userParamName);
			String val = environment.getEnvironmentPropertyCurrentValue(prop);
			System.out.println(
					"EnvironmentSelectorChangeTaskTest.UserParameterEnvironmentSelector.isSuitableExecutionEnvironment() "
							+ userParamName + " -> " + val);
			return new EnvironmentSelectionResult(Collections.singletonMap(prop, val));
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(userParamName);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			userParamName = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((userParamName == null) ? 0 : userParamName.hashCode());
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
			UserParameterEnvironmentSelector other = (UserParameterEnvironmentSelector) obj;
			if (userParamName == null) {
				if (other.userParamName != null)
					return false;
			} else if (!userParamName.equals(other.userParamName))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "UserParameterEnvironmentSelector[" + (userParamName != null ? "userParamName=" + userParamName : "")
					+ "]";
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		String p1name = getClass().getName() + ".param1";
		String p2name = getClass().getName() + ".param2";

		SelectorTaskFactory p1 = new SelectorTaskFactory(new UserParameterEnvironmentSelector(p1name));
		SelectorTaskFactory p2 = new SelectorTaskFactory(new UserParameterEnvironmentSelector(p2name));

		System.setProperty(p1name, "p1val");
		System.setProperty(p2name, "p2val");

		runTask("main", p1);

		System.setProperty(p1name, "p1val2");
		environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
		runTask("main", p1);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));

		environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
		runTask("main", p1);
		assertEmpty(getMetric().getRunTaskIdFactories());

		environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
		runTask("main", p2);
		assertEmpty(getMetric().getRunTaskIdFactories());

		//even if the selector depends on p2, and we modify p2, the task doesn't get reinvoked
		//as the runtime dependency was installed on p1, and that still didn't change
		//if the task would produce different values based on the values of the properties
		//then the task factory should have the task environment selector checked in the equality
		System.setProperty(p2name, "p2val2");
		environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
		runTask("main", p2);
		assertEmpty(getMetric().getRunTaskIdFactories());

		//modify p1, get reinvoked
		System.setProperty(p1name, "p1val3");
		environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
		runTask("main", p2);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));

		//modify p1 again, we don't get reinvoked, as we no longer care about p1
		System.setProperty(p1name, "p1val4");
		environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
		runTask("main", p2);
		assertEmpty(getMetric().getRunTaskIdFactories());

		//however, if we modify p2, then we do
		System.setProperty(p2name, "p2val3");
		environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
		runTask("main", p2);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));
	}

}
