package testing.saker.build.tests.tasks.cluster;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

/**
 * tests the proper workings when the exception cannot be read in cases when the cluster suitability test fails with
 * one.
 * <p>
 * We assert that the execution doesn't halt, but fail instead properly.
 */
@SakerTest
public class UnreadableUnsuitableClusterExceptionTaskTest extends ClusterBuildTestCase {

	public static final class UnreadableFailingTaskExecutionEnvironmentSelector
			implements TaskExecutionEnvironmentSelector, Externalizable {
		private static final long serialVersionUID = 1L;

		public UnreadableFailingTaskExecutionEnvironmentSelector() {
		}

		@Override
		public EnvironmentSelectionResult isSuitableExecutionEnvironment(SakerEnvironment environment) {
			throw new UnreadableException();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	public static class SelectionFailerTaskFactory extends SelfStatelessTaskFactory<Object> {

		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			//this should never run
			throw new UnsupportedOperationException();
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new UnreadableFailingTaskExecutionEnvironmentSelector();
		}
	}

	public static class UnreadableException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public UnreadableException() {
		}

		private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
			throw new UnsupportedOperationException();
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		assertTaskException(TaskEnvironmentSelectionFailedException.class,
				() -> runTask("main", new SelectionFailerTaskFactory()));
	}

}
