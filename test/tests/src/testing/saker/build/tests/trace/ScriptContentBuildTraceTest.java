package testing.saker.build.tests.trace;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import testing.saker.SakerTest;
import testing.saker.build.tests.VariablesMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class ScriptContentBuildTraceTest extends VariablesMetricEnvironmentTestCase {
	private static final SakerPath BUILD_TRACE_PATH = PATH_WORKING_DIRECTORY.resolve("build.trace");

	private String message;

	@Override
	protected Map<String, ?> getTaskVariables() {
		TreeMap<String, Object> result = new TreeMap<>(super.getTaskVariables());
		result.put("test.message", message);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath scriptpath = PATH_WORKING_DIRECTORY.resolve("saker.build");

		ProviderHolderPathKey tracepathkey = SakerPathFiles.getPathKey(files, BUILD_TRACE_PATH);
		parameters.setBuildTraceOutputPathKey(tracepathkey);

		message = "abc";

		runScriptTask("build");
		if (project != null) {
			project.waitExecutionFinalization();
		}
		assertEquals(TraceTestUtils.getTraceField(tracepathkey, "scripts", scriptpath.toString()),
				files.getAllBytes(scriptpath).copyOptionally());

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
		if (project != null) {
			project.waitExecutionFinalization();
		}
		assertEquals(TraceTestUtils.getTraceField(tracepathkey, "scripts", scriptpath.toString()),
				files.getAllBytes(scriptpath).copyOptionally());

		message = "123";

		runScriptTask("build");
		assertNotEmpty(getMetric().getRunTaskIdResults());
		if (project != null) {
			project.waitExecutionFinalization();
		}
		assertEquals(TraceTestUtils.getTraceField(tracepathkey, "scripts", scriptpath.toString()),
				files.getAllBytes(scriptpath).copyOptionally());

		runTask("main", new StringTaskFactory("1"));
		if (project != null) {
			project.waitExecutionFinalization();
		}
		//no scripts recorded
		assertException(NoSuchElementException.class,
				() -> TraceTestUtils.getTraceField(tracepathkey, "scripts", scriptpath.toString()));

		//script recorded again, no tasks run though
		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
		if (project != null) {
			project.waitExecutionFinalization();
		}
		assertEquals(TraceTestUtils.getTraceField(tracepathkey, "scripts", scriptpath.toString()),
				files.getAllBytes(scriptpath).copyOptionally());
	}
}
