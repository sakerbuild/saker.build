package testing.saker.build.tests.tasks.cluster;

import java.io.Externalizable;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.exception.ClusterEnvironmentSelectionFailedException;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;
import saker.build.util.property.UserParameterEnvironmentProperty;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestClusterNameExecutionEnvironmentSelector;
import testing.saker.build.tests.tasks.cluster.ClusterTaskFactoryTransferFailTest.IOFailRuntimeException;

@SakerTest
public class ClusterEnvironmentSelectorTransferFailTest extends ClusterBuildTestCase {
	private static class BaseTaskFactory extends ClusterTaskFactoryTransferFailTest.BaseTaskFactory
			implements Externalizable {
		private static final long serialVersionUID = 1L;

		public BaseTaskFactory() {
		}

		public BaseTaskFactory(String value) {
			super(value);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(value);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = (String) in.readObject();
		}
	}

	public static class NotSerializableTaskFactory extends BaseTaskFactory {
		private static final long serialVersionUID = 1L;

		public NotSerializableTaskFactory() {
		}

		public NotSerializableTaskFactory(String value) {
			super(value);
		}

		@Override
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new TaskExecutionEnvironmentSelector() {
				@Override
				public EnvironmentSelectionResult isSuitableExecutionEnvironment(SakerEnvironment environment) {
					UserParameterEnvironmentProperty prop = new UserParameterEnvironmentProperty(
							EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM);
					String val = environment.getEnvironmentPropertyCurrentValue(prop);
					if (ClusterBuildTestCase.DEFAULT_CLUSTER_NAME.equals(val)) {
						return new EnvironmentSelectionResult(Collections.singletonMap(prop, val));
					}
					return null;
				}
			};
		}
	}

	public static class WriteFailTaskFactory extends BaseTaskFactory {
		private static final long serialVersionUID = 1L;

		public WriteFailTaskFactory() {
		}

		public WriteFailTaskFactory(String value) {
			super(value);
		}

		@Override
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new WriteFailTestClusterNameExecutionEnvironmentSelector(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
		}
	}

	public static class ReadFailTaskFactory extends BaseTaskFactory {
		private static final long serialVersionUID = 1L;

		public ReadFailTaskFactory() {
		}

		public ReadFailTaskFactory(String value) {
			super(value);
		}

		@Override
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new ReadFailTestClusterNameExecutionEnvironmentSelector(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
		}
	}

	private static final class WriteFailTestClusterNameExecutionEnvironmentSelector
			extends TestClusterNameExecutionEnvironmentSelector {
		private static final long serialVersionUID = 1L;

		public WriteFailTestClusterNameExecutionEnvironmentSelector() {
		}

		private WriteFailTestClusterNameExecutionEnvironmentSelector(String clusterName) {
			super(clusterName);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			throw new IOFailRuntimeException("write-fail");
		}
	}

	private static final class ReadFailTestClusterNameExecutionEnvironmentSelector
			extends TestClusterNameExecutionEnvironmentSelector {
		private static final long serialVersionUID = 1L;

		public ReadFailTestClusterNameExecutionEnvironmentSelector() {
		}

		private ReadFailTestClusterNameExecutionEnvironmentSelector(String clusterName) {
			super(clusterName);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			throw new IOFailRuntimeException("read-fail");
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		TaskEnvironmentSelectionFailedException exc = assertTaskException(TaskEnvironmentSelectionFailedException.class,
				() -> runTask("nonserializable", new NotSerializableTaskFactory("test")));
		exc.printStackTrace();
		//the local environment is not suitable, cause is null
		assertEquals(exc.getCause(), null);
		assertEquals(exc.getSuppressed().length, 1);
		assertInstanceOf(exc.getSuppressed()[0], ClusterEnvironmentSelectionFailedException.class);
		assertInstanceOf(exc.getSuppressed()[0].getCause(), NotSerializableException.class);

		exc = assertTaskException(TaskEnvironmentSelectionFailedException.class,
				() -> runTask("writefail", new WriteFailTaskFactory("test")));
		System.err.println();
		exc.printStackTrace();
		//the local environment is not suitable, cause is null
		assertEquals(exc.getCause(), null);
		assertEquals(exc.getSuppressed().length, 1);
		assertInstanceOf(exc.getSuppressed()[0], ClusterEnvironmentSelectionFailedException.class);
		assertInstanceOf(exc.getSuppressed()[0].getCause(), IOFailRuntimeException.class);
		assertEquals(exc.getSuppressed()[0].getCause().getMessage(), "write-fail");

		exc = assertTaskException(TaskEnvironmentSelectionFailedException.class,
				() -> runTask("readfail", new ReadFailTaskFactory("test")));
		System.err.println();
		exc.printStackTrace();
		//the local environment is not suitable, cause is null
		assertEquals(exc.getCause(), null);
		assertEquals(exc.getSuppressed().length, 1);
		assertInstanceOf(exc.getSuppressed()[0], ClusterEnvironmentSelectionFailedException.class);
		assertInstanceOf(exc.getSuppressed()[0].getCause(), IOFailRuntimeException.class);
		assertEquals(exc.getSuppressed()[0].getCause().getMessage(), "read-fail");
	}

}
