package testing.saker.build.tests.tasks.cluster;

import java.io.NotSerializableException;

import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResultHolder;
import saker.build.task.InnerTaskResults;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.exception.InnerTaskInitializationException;
import testing.saker.SakerTest;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;
import testing.saker.build.tests.tasks.cluster.ClusterTaskFactoryTransferFailTest.IOFailRuntimeException;

@SakerTest
public class ClusterInnerTaskFactoryTransferFailTest extends ClusterBuildTestCase {

	public abstract static class StarterBaseTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			InnerTaskExecutionParameters execparams = new InnerTaskExecutionParameters();
			//duplicate to all clusters
			execparams.setClusterDuplicateFactor(-1);
			TaskFactory<String> factory = getInnerTaskFactory();
			InnerTaskResults<String> innerresults = taskcontext.startInnerTask(factory, execparams);

			try {
				InnerTaskResultHolder<String> result = innerresults.getNext();
				throw fail("InnerTaskInitializationException wasn't thrown by getNext(), returned: " + result + " by "
						+ innerresults);
			} catch (InnerTaskInitializationException e) {
				//this is expected
				throw e;
			}
		}

		protected abstract TaskFactory<String> getInnerTaskFactory();
	}

	private static class NotSerializableStarterTaskFactory extends StarterBaseTaskFactory {
		private static final long serialVersionUID = 1L;

		@Override
		protected TaskFactory<String> getInnerTaskFactory() {
			return new ClusterTaskFactoryTransferFailTest.NotSerializableTaskFactory("test");
		}
	}

	private static class WriteFailStarterTaskFactory extends StarterBaseTaskFactory {
		private static final long serialVersionUID = 1L;

		@Override
		protected TaskFactory<String> getInnerTaskFactory() {
			return new ClusterTaskFactoryTransferFailTest.WriteFailTaskFactory("test");
		}
	}

	private static class ReadFailStarterTaskFactory extends StarterBaseTaskFactory {
		private static final long serialVersionUID = 1L;

		@Override
		protected TaskFactory<String> getInnerTaskFactory() {
			return new ClusterTaskFactoryTransferFailTest.ReadFailTaskFactory("test");
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		InnerTaskInitializationException exc = assertTaskException(InnerTaskInitializationException.class,
				() -> runTask("nonserializable", new NotSerializableStarterTaskFactory()));
		exc.printStackTrace();
		assertInstanceOf(exc.getCause(), NotSerializableException.class);
		//no need for any suppressed exceptions
		assertEquals(exc.getCause().getSuppressed().length, 0);

		exc = assertTaskException(InnerTaskInitializationException.class,
				() -> runTask("failwriter", new WriteFailStarterTaskFactory()));
		System.err.println();
		exc.printStackTrace();
		assertInstanceOf(exc.getCause(), IOFailRuntimeException.class);
		assertEquals(exc.getCause().getMessage(), "write-fail");
		//no need for any suppressed exceptions
		assertEquals(exc.getCause().getSuppressed().length, 0);

		exc = assertTaskException(InnerTaskInitializationException.class,
				() -> runTask("failreader", new ReadFailStarterTaskFactory()));
		System.err.println();
		exc.printStackTrace();
		assertInstanceOf(exc.getCause(), IOFailRuntimeException.class);
		assertEquals(exc.getCause().getMessage(), "read-fail");
		//no need for any suppressed exceptions
		assertEquals(exc.getCause().getSuppressed().length, 0);
	}

}
