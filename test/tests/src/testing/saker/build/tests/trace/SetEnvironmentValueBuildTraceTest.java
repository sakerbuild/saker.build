package testing.saker.build.tests.trace;

import java.util.Collections;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.task.TaskContext;
import saker.build.trace.BuildTrace;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

@SakerTest
public class SetEnvironmentValueBuildTraceTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath BUILD_TRACE_PATH = PATH_WORKING_DIRECTORY.resolve("build.trace");

	public static final class EnvironmentValueTraceTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			BuildTrace.setValues(Collections.singletonMap("key", "val"), BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			return null;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		ProviderHolderPathKey tracepathkey = SakerPathFiles.getPathKey(files, BUILD_TRACE_PATH);
		parameters.setBuildTraceOutputPathKey(tracepathkey);

		runTask("main", new EnvironmentValueTraceTaskFactory());
		if (project != null) {
			project.waitExecutionFinalization();
		}
		assertEquals(TraceTestUtils.getTraceField(tracepathkey, "environments", 0, "values"),
				Collections.singletonMap("key", "val"));
	}

}
