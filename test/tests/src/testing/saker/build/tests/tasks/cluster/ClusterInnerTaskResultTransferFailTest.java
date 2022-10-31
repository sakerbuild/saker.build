package testing.saker.build.tests.tasks.cluster;

import java.io.Externalizable;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;

import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResultHolder;
import saker.build.task.InnerTaskResults;
import saker.build.task.TaskContext;
import saker.build.task.TaskDuplicationPredicate;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.task.exception.InnerTaskExecutionException;
import saker.build.task.exception.TaskResultSerializationException;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.TestClusterNameExecutionEnvironmentSelector;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;
import testing.saker.build.tests.tasks.cluster.ClusterTaskFactoryTransferFailTest.IOFailRuntimeException;

@SakerTest
public class ClusterInnerTaskResultTransferFailTest extends ClusterBuildTestCase {

	private static final int DUPLICATION_COUNT = 5;

	private static final class CountDuplicationPredicate implements TaskDuplicationPredicate, Externalizable {
		private static final long serialVersionUID = 1L;

		private int count;

		public CountDuplicationPredicate() {
		}

		private CountDuplicationPredicate(int duplicationcount) {
			count = duplicationcount;
		}

		@Override
		public synchronized boolean shouldInvokeOnceMore() throws RuntimeException {
			if (count > 0) {
				--count;
				return true;
			}
			return false;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeInt(count);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			count = in.readInt();
		}
	}

	public abstract static class StarterBaseTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			InnerTaskExecutionParameters execparams = new InnerTaskExecutionParameters();
			//duplicate to all clusters
			execparams.setClusterDuplicateFactor(-1);
			execparams.setDuplicationPredicateForEachCluster(new CountDuplicationPredicate(DUPLICATION_COUNT));
			TaskFactory<?> factory = getInnerTaskFactory();
			InnerTaskResults<?> innerresults = taskcontext.startInnerTask(factory, execparams);

			int resultcount = 0;
			while (true) {
				InnerTaskResultHolder<?> n = innerresults.getNext();
				System.out.println("ClusterInnerTaskResultTransferFailTest.StarterBaseTaskFactory.run() result: " + n);
				if (n == null) {
					break;
				}
				++resultcount;
				Object result;
				try {
					result = n.getResult();
					throw fail("Failed to catch InnerTaskExecutionException for result: " + result);
				} catch (InnerTaskExecutionException e) {
					// expected
					checkInnerTaskResult(n, e);
					continue;
				}
			}
			assertEquals(resultcount, DUPLICATION_COUNT);
			return resultcount;
		}

		protected abstract void checkInnerTaskResult(InnerTaskResultHolder<?> n, InnerTaskExecutionException e);

		protected abstract TaskFactory<?> getInnerTaskFactory();
	}

	private static class NotSerializableStarterTaskFactory extends StarterBaseTaskFactory {
		private static final long serialVersionUID = 1L;

		@Override
		protected TaskFactory<?> getInnerTaskFactory() {
			return new NotSerializableResultTaskFactory();
		}

		@Override
		protected void checkInnerTaskResult(InnerTaskResultHolder<?> n, InnerTaskExecutionException e)
				throws AssertionError {
			e.printStackTrace();
			assertInstanceOf(e.getCause(), TaskResultSerializationException.class);
			assertInstanceOf(n.getExceptionIfAny(), TaskResultSerializationException.class);

			assertInstanceOf(e.getCause().getCause(), NotSerializableException.class);
		}
	}

	private static class WriteFailStarterTaskFactory extends StarterBaseTaskFactory {
		private static final long serialVersionUID = 1L;

		@Override
		protected TaskFactory<?> getInnerTaskFactory() {
			return new WriteFailResultTaskFactory();
		}

		@Override
		protected void checkInnerTaskResult(InnerTaskResultHolder<?> n, InnerTaskExecutionException e)
				throws AssertionError {
			e.printStackTrace();
			assertInstanceOf(e.getCause(), TaskResultSerializationException.class);
			assertInstanceOf(n.getExceptionIfAny(), TaskResultSerializationException.class);

			assertInstanceOf(e.getCause().getCause(), IOFailRuntimeException.class);
			assertEquals(e.getCause().getCause().getMessage(), "write-fail");
		}
	}

	private static class ReadFailStarterTaskFactory extends StarterBaseTaskFactory {
		private static final long serialVersionUID = 1L;

		@Override
		protected TaskFactory<?> getInnerTaskFactory() {
			return new ReadFailResultTaskFactory();
		}

		@Override
		protected void checkInnerTaskResult(InnerTaskResultHolder<?> n, InnerTaskExecutionException e)
				throws AssertionError {
			e.printStackTrace();
			assertInstanceOf(e.getCause(), TaskResultSerializationException.class);
			assertInstanceOf(n.getExceptionIfAny(), TaskResultSerializationException.class);

			assertInstanceOf(e.getCause().getCause(), IOFailRuntimeException.class);
			assertEquals(e.getCause().getCause().getMessage(), "read-fail");
		}
	}

	private static abstract class BaseInnerTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

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
	}

	private static class NotSerializableResultTaskFactory extends BaseInnerTaskFactory {
		private static final long serialVersionUID = 1L;

		public NotSerializableResultTaskFactory() {
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			return new Object();
		}
	}

	private static class WriteFailResultTaskFactory extends BaseInnerTaskFactory {
		private static final long serialVersionUID = 1L;

		public WriteFailResultTaskFactory() {
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			return new WriteFailExternalizable();
		}
	}

	private static final class WriteFailExternalizable implements Externalizable {
		private static final long serialVersionUID = 1L;

		private String value;

		public WriteFailExternalizable() {
		}

		public WriteFailExternalizable(String value) {
			this.value = value;
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

	private static class ReadFailResultTaskFactory extends BaseInnerTaskFactory {
		private static final long serialVersionUID = 1L;

		public ReadFailResultTaskFactory() {
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			return new ReadFailExternalizable();
		}
	}

	private static final class ReadFailExternalizable implements Externalizable {
		private static final long serialVersionUID = 1L;

		private String value;

		public ReadFailExternalizable() {
		}

		public ReadFailExternalizable(String value) {
			this.value = value;
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

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("nonserializable", new NotSerializableStarterTaskFactory());
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("nonserializable")), DUPLICATION_COUNT);

		System.err.println();
		runTask("failwriter", new WriteFailStarterTaskFactory());
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("failwriter")), DUPLICATION_COUNT);

		System.err.println();
		runTask("failreader", new ReadFailStarterTaskFactory());
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("failreader")), DUPLICATION_COUNT);
	}

}
