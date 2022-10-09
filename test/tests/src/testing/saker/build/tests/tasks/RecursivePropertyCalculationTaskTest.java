package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.exception.PropertyComputationFailedException;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class RecursivePropertyCalculationTaskTest extends CollectingMetricEnvironmentTestCase {
	private static class RecursiveEnvironmentProperty implements EnvironmentProperty<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public RecursiveEnvironmentProperty() {
		}

		@Override
		public String getCurrentValue(SakerEnvironment environment) throws Exception {
			return environment.getEnvironmentPropertyCurrentValue(this);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public final int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public final boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName();
		}
	}

	private static class RecursiveExecutionProperty implements ExecutionProperty<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public RecursiveExecutionProperty() {
		}

		@Override
		public String getCurrentValue(ExecutionContext executioncontext) throws Exception {
			return executioncontext.getExecutionPropertyCurrentValue(this);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public final int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public final boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName();
		}
	}

	private static class ExecutionPropertyCalculatorTask extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			return taskcontext.getTaskUtilities().getReportExecutionDependency(new RecursiveExecutionProperty());
		}
	}

	private static class EnvironmentPropertyCalculatorTask extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			return taskcontext.getTaskUtilities().getReportEnvironmentDependency(new RecursiveEnvironmentProperty());
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		assertInstanceOf(
				assertTaskException(PropertyComputationFailedException.class,
						() -> runTask("main_exec", new ExecutionPropertyCalculatorTask())).getCause(),
				IllegalThreadStateException.class);

		assertInstanceOf(
				assertTaskException(PropertyComputationFailedException.class,
						() -> runTask("main_env", new EnvironmentPropertyCalculatorTask())).getCause(),
				IllegalThreadStateException.class);
	}

}
