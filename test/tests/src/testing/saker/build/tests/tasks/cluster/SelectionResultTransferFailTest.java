package testing.saker.build.tests.tasks.cluster;

import java.io.Externalizable;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.exception.ClusterEnvironmentSelectionFailedException;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.TestClusterNameExecutionEnvironmentSelector;
import testing.saker.build.tests.tasks.cluster.ClusterTaskFactoryTransferFailTest.IOFailRuntimeException;

@SakerTest
public class SelectionResultTransferFailTest extends ClusterBuildTestCase {

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
			return new NotSerializableExtraPropertyClusterNameExecutionEnvironmentSelector(
					ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
		}
	}

	//subclass instead of having the extra property as a field, because we want the environment selector to be serializable
	//and the selection result to be not serializable
	private static final class NotSerializableExtraPropertyClusterNameExecutionEnvironmentSelector
			extends ExtraPropertyClusterNameExecutionEnvironmentSelector {
		private static final long serialVersionUID = 1L;

		public NotSerializableExtraPropertyClusterNameExecutionEnvironmentSelector() {
		}

		public NotSerializableExtraPropertyClusterNameExecutionEnvironmentSelector(String clusterName) {
			super(clusterName);
		}

		@Override
		public EnvironmentProperty<?> getExtraProperty() {
			return new NotSerializableEnvironmentProperty();
		}
	}

	private static abstract class ExtraPropertyClusterNameExecutionEnvironmentSelector
			extends TestClusterNameExecutionEnvironmentSelector {
		private static final long serialVersionUID = 1L;

		public ExtraPropertyClusterNameExecutionEnvironmentSelector() {
		}

		public ExtraPropertyClusterNameExecutionEnvironmentSelector(String clusterName) {
			super(clusterName);
		}

		public abstract EnvironmentProperty<?> getExtraProperty();

		@Override
		public EnvironmentSelectionResult isSuitableExecutionEnvironment(SakerEnvironment environment) {
			EnvironmentSelectionResult result = super.isSuitableExecutionEnvironment(environment);
			if (result == null) {
				return null;
			}
			EnvironmentProperty<?> extraProperty = getExtraProperty();
			return new EnvironmentSelectionResult(result, new EnvironmentSelectionResult(Collections
					.singletonMap(extraProperty, environment.getEnvironmentPropertyCurrentValue(extraProperty))));
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
		}

	}

	private static class NotSerializableEnvironmentProperty implements EnvironmentProperty<Object> {
		@Override
		public Object getCurrentValue(SakerEnvironment environment) throws Exception {
			return null;
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
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
			return new WriteFailExtraPropertyClusterNameExecutionEnvironmentSelector(
					ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
		}
	}

	private static final class WriteFailExtraPropertyClusterNameExecutionEnvironmentSelector
			extends ExtraPropertyClusterNameExecutionEnvironmentSelector {
		private static final long serialVersionUID = 1L;

		public WriteFailExtraPropertyClusterNameExecutionEnvironmentSelector() {
		}

		private WriteFailExtraPropertyClusterNameExecutionEnvironmentSelector(String clusterName) {
			super(clusterName);
		}

		@Override
		public EnvironmentProperty<?> getExtraProperty() {
			return new WriteFailEnvironmentProperty();
		}
	}

	private static class WriteFailEnvironmentProperty implements EnvironmentProperty<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		public WriteFailEnvironmentProperty() {
		}

		@Override
		public Object getCurrentValue(SakerEnvironment environment) throws Exception {
			return null;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			throw new IOFailRuntimeException("write-fail");
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			//dummy read
			in.readObject();
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
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
			return new ReadFailExtraPropertyClusterNameExecutionEnvironmentSelector(
					ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
		}
	}

	private static final class ReadFailExtraPropertyClusterNameExecutionEnvironmentSelector
			extends ExtraPropertyClusterNameExecutionEnvironmentSelector {
		private static final long serialVersionUID = 1L;

		public ReadFailExtraPropertyClusterNameExecutionEnvironmentSelector() {
		}

		private ReadFailExtraPropertyClusterNameExecutionEnvironmentSelector(String clusterName) {
			super(clusterName);
		}

		@Override
		public EnvironmentProperty<?> getExtraProperty() {
			return new ReadFailEnvironmentProperty();
		}
	}

	private static class ReadFailEnvironmentProperty implements EnvironmentProperty<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		public ReadFailEnvironmentProperty() {
		}

		@Override
		public Object getCurrentValue(SakerEnvironment environment) throws Exception {
			return null;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject("123");
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			throw new IOFailRuntimeException("read-fail");
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		TaskEnvironmentSelectionFailedException exc = assertTaskException(TaskEnvironmentSelectionFailedException.class,
				() -> runTask("nonserializable", new NotSerializableTaskFactory("test")));
		exc.printStackTrace();
		assertEquals(exc.getCause(), null);
		assertEquals(exc.getSuppressed().length, 1);
		assertInstanceOf(exc.getSuppressed()[0], ClusterEnvironmentSelectionFailedException.class);
		assertInstanceOf(exc.getSuppressed()[0].getCause(), NotSerializableException.class);

		exc = assertTaskException(TaskEnvironmentSelectionFailedException.class,
				() -> runTask("writefail", new WriteFailTaskFactory("test")));
		System.err.println();
		exc.printStackTrace();
		assertEquals(exc.getCause(), null);
		assertEquals(exc.getSuppressed().length, 1);
		assertInstanceOf(exc.getSuppressed()[0], ClusterEnvironmentSelectionFailedException.class);
		assertInstanceOf(exc.getSuppressed()[0].getCause(), IOFailRuntimeException.class);
		assertEquals(exc.getSuppressed()[0].getCause().getMessage(), "write-fail");

		exc = assertTaskException(TaskEnvironmentSelectionFailedException.class,
				() -> runTask("readfail", new ReadFailTaskFactory("test")));
		System.err.println();
		exc.printStackTrace();
		assertEquals(exc.getCause(), null);
		assertEquals(exc.getSuppressed().length, 1);
		assertInstanceOf(exc.getSuppressed()[0], ClusterEnvironmentSelectionFailedException.class);
		assertInstanceOf(exc.getSuppressed()[0].getCause(), IOFailRuntimeException.class);
		assertEquals(exc.getSuppressed()[0].getCause().getMessage(), "read-fail");
	}

}
