package testing.saker.build.tests.trace;

import java.util.List;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResultHolder;
import saker.build.task.InnerTaskResults;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskInvocationConfiguration;
import saker.build.task.exception.InnerTaskExecutionException;
import testing.saker.SakerTest;
import testing.saker.build.tests.TestClusterNameExecutionEnvironmentSelector;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;
import testing.saker.build.tests.tasks.cluster.ClusterBuildTestCase;

//cluster test case so we can test that as well
@SakerTest
public class InnerTaskExceptionTraceTest extends ClusterBuildTestCase {
	private static final SakerPath BUILD_TRACE_PATH = PATH_WORKING_DIRECTORY.resolve("build.trace");

	public static class ThrowingInnerStarterTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			InnerTaskExecutionParameters params = getParams(taskcontext);
			InnerTaskResults<?> results = taskcontext.startInnerTask(getTaskFactory(), params);
			InnerTaskResultHolder<?> resultholder = results.getNext();
			Throwable exc = resultholder.getExceptionIfAny();
			if (exc != null) {
				throw new InnerTaskExecutionException(exc);
			}
			return resultholder.getResult();
		}

		protected InnerTaskExecutionParameters getParams(TaskContext taskcontext) {
			return null;
		}

		protected TaskFactory<?> getTaskFactory() {
			return new ThrowingInnerTaskFactory();
		}
	}

	public static class AbortingInnerStarterTaskFactory extends ThrowingInnerStarterTaskFactory {
		private static final long serialVersionUID = 1L;

		@Override
		protected TaskFactory<?> getTaskFactory() {
			return new AbortingInnerTaskFactory();
		}
	}

	public static class ShortAbortingInnerStarterTaskFactory extends ThrowingInnerStarterTaskFactory {
		private static final long serialVersionUID = 1L;

		@Override
		protected TaskFactory<?> getTaskFactory() {
			return new ShortAbortingInnerTaskFactory();
		}
	}

	public static class ClusterThrowingInnerStarterTaskFactory extends ThrowingInnerStarterTaskFactory {
		private static final long serialVersionUID = 1L;

		@Override
		protected TaskFactory<?> getTaskFactory() {
			return new ClusterThrowingInnerTaskFactory();
		}
	}

	public static class ClusterAbortingInnerStarterTaskFactory extends ThrowingInnerStarterTaskFactory {
		private static final long serialVersionUID = 1L;

		@Override
		protected TaskFactory<?> getTaskFactory() {
			return new ClusterAbortingInnerTaskFactory();
		}
	}

	public static class ThrowingInnerTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			throw new UnsupportedOperationException(getClass().getSimpleName());
		}
	}

	public static class AbortingInnerTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			taskcontext.abortExecution(new UnsupportedOperationException(getClass().getSimpleName()));
			return 123;
		}
	}

	public static class ShortAbortingInnerTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public TaskInvocationConfiguration getInvocationConfiguration() {
			return TaskInvocationConfiguration.builder().setShort(true).build();
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			taskcontext.abortExecution(new UnsupportedOperationException(getClass().getSimpleName()));
			return 123;
		}
	}

	public static class ClusterThrowingInnerTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public TaskInvocationConfiguration getInvocationConfiguration() {
			return TaskInvocationConfiguration.builder().setRemoteDispatchable(true)
					.setExecutionEnvironmentSelector(
							new TestClusterNameExecutionEnvironmentSelector(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME))
					.build();
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			throw new UnsupportedOperationException(getClass().getSimpleName());
		}
	}

	public static class ClusterAbortingInnerTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public TaskInvocationConfiguration getInvocationConfiguration() {
			return TaskInvocationConfiguration.builder().setRemoteDispatchable(true)
					.setExecutionEnvironmentSelector(
							new TestClusterNameExecutionEnvironmentSelector(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME))
					.build();
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			taskcontext.abortExecution(new UnsupportedOperationException(getClass().getSimpleName()));
			return 456;
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		ProviderHolderPathKey tracepathkey = SakerPathFiles.getPathKey(files, BUILD_TRACE_PATH);
		parameters.setBuildTraceOutputPathKey(tracepathkey);
		{
			assertTaskException(InnerTaskExecutionException.class,
					() -> runTask("throwing", new ThrowingInnerStarterTaskFactory()));
			if (project != null) {
				project.waitExecutionFinalization();
			}
			assertNotEmpty((Iterable<?>) TraceTestUtils.getTraceField(tracepathkey, "tasks", 0, "inner_tasks"));
			byte[] exceptionobj = (byte[]) TraceTestUtils.getTraceField(tracepathkey, "tasks", 0, "inner_tasks", 0,
					"exception");
			assertNonNull(exceptionobj);
			String excstr = new String(exceptionobj);
			assertTrue(excstr.contains(
					new UnsupportedOperationException(ThrowingInnerTaskFactory.class.getSimpleName()).toString()),
					excstr);
		}

		{
			assertTaskException(InnerTaskExecutionException.class,
					() -> runTask("aborting", new AbortingInnerStarterTaskFactory()));
			if (project != null) {
				project.waitExecutionFinalization();
			}
			assertNotEmpty((Iterable<?>) TraceTestUtils.getTraceField(tracepathkey, "tasks", 0, "inner_tasks"));
			@SuppressWarnings("unchecked")
			List<byte[]> abortexceptions = (List<byte[]>) TraceTestUtils.getTraceField(tracepathkey, "tasks", 0,
					"inner_tasks", 0, "abort_exceptions");
			assertNonNull(abortexceptions);
			assertEquals(abortexceptions.size(), 1);
			String abortexcstr = new String(abortexceptions.get(0));
			assertTrue(abortexcstr.contains(
					new UnsupportedOperationException(AbortingInnerTaskFactory.class.getSimpleName()).toString()),
					abortexcstr);
		}

		{
			assertTaskException(InnerTaskExecutionException.class,
					() -> runTask("shortaborting", new ShortAbortingInnerStarterTaskFactory()));
			if (project != null) {
				project.waitExecutionFinalization();
			}
			assertNotEmpty((Iterable<?>) TraceTestUtils.getTraceField(tracepathkey, "tasks", 0, "inner_tasks"));
			@SuppressWarnings("unchecked")
			List<byte[]> abortexceptions = (List<byte[]>) TraceTestUtils.getTraceField(tracepathkey, "tasks", 0,
					"inner_tasks", 0, "abort_exceptions");
			assertNonNull(abortexceptions);
			assertEquals(abortexceptions.size(), 1);
			String abortexcstr = new String(abortexceptions.get(0));
			assertTrue(abortexcstr.contains(
					new UnsupportedOperationException(ShortAbortingInnerTaskFactory.class.getSimpleName()).toString()),
					abortexcstr);
		}

		{
			assertTaskException(InnerTaskExecutionException.class,
					() -> runTask("clusterthrowing", new ClusterThrowingInnerStarterTaskFactory()));
			if (project != null) {
				project.waitExecutionFinalization();
			}
			assertNotEmpty((Iterable<?>) TraceTestUtils.getTraceField(tracepathkey, "tasks", 0, "inner_tasks"));
			byte[] exceptionobj = (byte[]) TraceTestUtils.getTraceField(tracepathkey, "tasks", 0, "inner_tasks", 0,
					"exception");
			assertNonNull(exceptionobj);
			String excstr = new String(exceptionobj);
			assertTrue(excstr
					.contains(new UnsupportedOperationException(ClusterThrowingInnerTaskFactory.class.getSimpleName())
							.toString()),
					excstr);
		}

		{
			assertTaskException(InnerTaskExecutionException.class,
					() -> runTask("clusteraborting", new ClusterAbortingInnerStarterTaskFactory()));
			if (project != null) {
				project.waitExecutionFinalization();
			}
			assertNotEmpty((Iterable<?>) TraceTestUtils.getTraceField(tracepathkey, "tasks", 0, "inner_tasks"));
			@SuppressWarnings("unchecked")
			List<byte[]> abortexceptions = (List<byte[]>) TraceTestUtils.getTraceField(tracepathkey, "tasks", 0,
					"inner_tasks", 0, "abort_exceptions");
			assertNonNull(abortexceptions);
			assertEquals(abortexceptions.size(), 1);
			String abortexcstr = new String(abortexceptions.get(0));
			assertTrue(abortexcstr
					.contains(new UnsupportedOperationException(ClusterAbortingInnerTaskFactory.class.getSimpleName())
							.toString()),
					abortexcstr);
		}
	}

}
