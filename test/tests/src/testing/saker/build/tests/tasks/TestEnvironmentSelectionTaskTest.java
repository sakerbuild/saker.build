package testing.saker.build.tests.tasks;

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class TestEnvironmentSelectionTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class TesterTaskFactory extends StatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return new Task<Void>() {
				@Override
				public Void run(TaskContext taskcontext) throws Exception {
					assertNonNull(executioncontext.testEnvironmentSelection(getExecutionEnvironmentSelector(), null));
					assertException(TaskEnvironmentSelectionFailedException.class,
							() -> executioncontext.testEnvironmentSelection(new TaskExecutionEnvironmentSelector() {
								@Override
								public EnvironmentSelectionResult isSuitableExecutionEnvironment(
										SakerEnvironment environment) {
									return null;
								}
							}, null));
					TaskEnvironmentSelectionFailedException unsupportedexc = assertException(
							TaskEnvironmentSelectionFailedException.class,
							() -> executioncontext.testEnvironmentSelection(new TaskExecutionEnvironmentSelector() {
								@Override
								public EnvironmentSelectionResult isSuitableExecutionEnvironment(
										SakerEnvironment environment) {
									throw new UnsupportedOperationException();
								}
							}, null));
					assertEquals(ObjectUtils.classOf(unsupportedexc.getCause()), UnsupportedOperationException.class);
					return null;
				}
			};
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new TesterTaskFactory());
	}

}
