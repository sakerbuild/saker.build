package testing.saker.build.tests.trace;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class BuildTracePersistenceTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath BUILD_TRACE_PATH = PATH_WORKING_DIRECTORY.resolve("build.trace");

	@Override
	protected void runTestImpl() throws Throwable {
		ProviderHolderPathKey tracepathkey = SakerPathFiles.getPathKey(files, BUILD_TRACE_PATH);
		parameters.setBuildTraceOutputPathKey(tracepathkey);

		runTask("main", new StringTaskFactory("abc"));
		if (project != null) {
			project.waitExecutionFinalization();
		}
		//test reading
		TraceTestUtils.readBuildTrace(tracepathkey);
	}

}
