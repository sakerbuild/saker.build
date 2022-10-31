package testing.saker.build.tests.tasks.cluster;

import java.io.Externalizable;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.task.exception.ClusterTaskExecutionFailedException;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.TestClusterNameExecutionEnvironmentSelector;

@SakerTest
public class ClusterTaskFactoryTransferFailTest extends ClusterBuildTestCase {

	public static class BaseTaskFactory implements TaskFactory<String>, Task<String> {
		protected String value;

		public BaseTaskFactory() {
		}

		public BaseTaskFactory(String value) {
			this.value = value;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			return value;
		}

		@Override
		@SuppressWarnings("deprecation")
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		@SuppressWarnings("deprecation")
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new TestClusterNameExecutionEnvironmentSelector(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
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
			BaseTaskFactory other = (BaseTaskFactory) obj;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder(getClass().getSimpleName());
			builder.append("[value=");
			builder.append(value);
			builder.append("]");
			return builder.toString();
		}
	}

	public static class NotSerializableTaskFactory extends BaseTaskFactory {

		public NotSerializableTaskFactory() {
		}

		public NotSerializableTaskFactory(String value) {
			super(value);
		}
	}

	public static class WriteFailTaskFactory extends BaseTaskFactory implements Externalizable {
		private static final long serialVersionUID = 1L;

		public WriteFailTaskFactory() {
		}

		public WriteFailTaskFactory(String value) {
			super(value);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			throw new IOFailRuntimeException("write-fail");
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = (String) in.readObject();
		}
	}

	public static class ReadFailTaskFactory extends BaseTaskFactory implements Externalizable {
		private static final long serialVersionUID = 1L;

		public ReadFailTaskFactory() {
		}

		public ReadFailTaskFactory(String value) {
			super(value);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(value);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			throw new IOFailRuntimeException("read-fail");
		}
	}

	public static class IOFailRuntimeException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public IOFailRuntimeException() {
			super();
		}

		public IOFailRuntimeException(String message) {
			super(message);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		ClusterTaskExecutionFailedException exc = assertTaskException(ClusterTaskExecutionFailedException.class,
				() -> runTask("nonserializable", new NotSerializableTaskFactory("test")));
		exc.printStackTrace();
		assertInstanceOf(exc.getCause(), NotSerializableException.class);
		//no need for any suppressed exceptions
		assertEquals(exc.getCause().getSuppressed().length, 0);

		exc = assertTaskException(ClusterTaskExecutionFailedException.class,
				() -> runTask("failwriter", new WriteFailTaskFactory("test")));
		System.err.println();
		exc.printStackTrace();
		assertInstanceOf(exc.getCause(), IOFailRuntimeException.class);
		assertEquals(exc.getCause().getMessage(), "write-fail");

		exc = assertTaskException(ClusterTaskExecutionFailedException.class,
				() -> runTask("failreader", new ReadFailTaskFactory("test")));
		System.err.println();
		exc.printStackTrace();
		assertInstanceOf(exc.getCause(), IOFailRuntimeException.class);
		assertEquals(exc.getCause().getMessage(), "read-fail");
	}

}
