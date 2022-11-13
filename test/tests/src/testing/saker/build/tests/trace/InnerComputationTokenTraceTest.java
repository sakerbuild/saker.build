package testing.saker.build.tests.trace;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResultHolder;
import saker.build.task.InnerTaskResults;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskInvocationConfiguration;
import testing.saker.SakerTest;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;
import testing.saker.build.tests.tasks.cluster.ClusterBuildTestCase;

//cluster test case so we can test that as well
@SakerTest
public class InnerComputationTokenTraceTest extends ClusterBuildTestCase {
	private static final SakerPath BUILD_TRACE_PATH = PATH_WORKING_DIRECTORY.resolve("build.trace");

	public static class InnerStarterTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public TaskInvocationConfiguration getInvocationConfiguration() {
			return TaskInvocationConfiguration.builder().setInnerTasksComputationals(true).build();
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			InnerTaskExecutionParameters params = getParams(taskcontext);
			InnerTaskResults<?> results = taskcontext.startInnerTask(getTaskFactory(), params);
			InnerTaskResultHolder<?> resultholder = results.getNext();
			return resultholder.getResult();
		}

		protected InnerTaskExecutionParameters getParams(TaskContext taskcontext) {
			return null;
		}

		protected TaskFactory<?> getTaskFactory() {
			return new InnerTaskFactory();
		}
	}

	public static class InnerTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public TaskInvocationConfiguration getInvocationConfiguration() {
			return TaskInvocationConfiguration.builder().setRequestedComputationTokenCount(2).build();
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			return "123";
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		ProviderHolderPathKey tracepathkey = SakerPathFiles.getPathKey(files, BUILD_TRACE_PATH);
		parameters.setBuildTraceOutputPathKey(tracepathkey);
		{
			runTask("main", new InnerStarterTaskFactory());
			if (project != null) {
				project.waitExecutionFinalization();
			}
			assertNotEmpty((Iterable<?>) TraceTestUtils.getTraceField(tracepathkey, "tasks", 0, "inner_tasks"));
			Integer cputokencount = (Integer) TraceTestUtils.getTraceField(tracepathkey, "tasks", 0, "inner_tasks", 0,
					"cpu_tokens");
			assertNonNull(cputokencount);
			assertEquals(cputokencount, 2);
		}
	}

}
