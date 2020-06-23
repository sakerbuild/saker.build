package testing.saker.build.tests.trace;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.task.InnerTaskResults;
import saker.build.task.TaskContext;
import saker.build.task.TaskResultCollection;
import saker.build.trace.BuildTrace;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

@SakerTest
public class OmitInnerTaskBuildTraceTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath BUILD_TRACE_PATH = PATH_WORKING_DIRECTORY.resolve("build.trace");

	public static class InnerStarterTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			InnerTaskResults<Object> results = taskcontext.startInnerTask(new OmittingInnterTaskFactory(), null);
			return results.getNext().getResult();
		}
	}

	public static class OmittingInnterTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			BuildTrace.omitInnerTask();
			return 123;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		ProviderHolderPathKey tracepathkey = SakerPathFiles.getPathKey(files, BUILD_TRACE_PATH);
		parameters.setBuildTraceOutputPathKey(tracepathkey);

		TaskResultCollection res = runTask("main", new InnerStarterTaskFactory());
		if (project != null) {
			project.waitExecutionFinalization();
		}
		assertEmpty((Iterable<?>) TraceTestUtils.getTraceField(tracepathkey, "tasks", 0, "inner_tasks"));
		assertEquals(res.getTaskResult(strTaskId("main")), 123);
	}

}
