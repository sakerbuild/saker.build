package testing.saker.build.tests.trace;

import java.util.Collections;
import java.util.Map;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.task.TaskContext;
import saker.build.trace.BuildTrace;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

@SakerTest
public class AddTraceValueTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath BUILD_TRACE_PATH = PATH_WORKING_DIRECTORY.resolve("build.trace");

	public static final class EnvironmentValueAddingTraceTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			BuildTrace.addValues(Collections.singletonMap("primxprim", "val1"), BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			BuildTrace.addValues(Collections.singletonMap("primxlist", "val1"), BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			BuildTrace.addValues(Collections.singletonMap("primxmap", "val1"), BuildTrace.VALUE_CATEGORY_ENVIRONMENT);

			BuildTrace.addValues(Collections.singletonMap("primxprim", "val2"), BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			BuildTrace.addValues(Collections.singletonMap("primxlist", listOf("val2")),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			BuildTrace.addValues(Collections.singletonMap("primxmap", Collections.singletonMap("mk2", "mv2")),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);

			BuildTrace.addValues(Collections.singletonMap("listxprim", listOf("val1")),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			BuildTrace.addValues(Collections.singletonMap("listxlist", listOf("val1")),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			BuildTrace.addValues(Collections.singletonMap("listxmap", listOf("val1")),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);

			BuildTrace.addValues(Collections.singletonMap("listxprim", "val2"), BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			BuildTrace.addValues(Collections.singletonMap("listxlist", listOf("val2")),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			BuildTrace.addValues(Collections.singletonMap("listxmap", Collections.singletonMap("mk2", "mv2")),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);

			BuildTrace.addValues(Collections.singletonMap("mapxprim", Collections.singletonMap("mk1", "mv1")),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			BuildTrace.addValues(Collections.singletonMap("mapxlist", Collections.singletonMap("mk1", "mv1")),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			BuildTrace.addValues(Collections.singletonMap("mapxmap", Collections.singletonMap("mk1", "mv1")),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);

			BuildTrace.addValues(Collections.singletonMap("mapxprim", "val2"), BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			BuildTrace.addValues(Collections.singletonMap("mapxlist", listOf("val2")),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			BuildTrace.addValues(Collections.singletonMap("mapxmap", Collections.singletonMap("mk2", "mv2")),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			return null;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		ProviderHolderPathKey tracepathkey = SakerPathFiles.getPathKey(files, BUILD_TRACE_PATH);
		parameters.setBuildTraceOutputPathKey(tracepathkey);

		runTask("main", new EnvironmentValueAddingTraceTaskFactory());
		if (project != null) {
			project.waitExecutionFinalization();
		}
		Map<String, Object> bt = TraceTestUtils.readBuildTrace(tracepathkey);
		assertEquals(TraceTestUtils.getTraceField(bt, "environments", 0, "values", "primxprim"),
				listOf("val1", "val2"));
		assertEquals(TraceTestUtils.getTraceField(bt, "environments", 0, "values", "primxlist"),
				listOf("val1", "val2"));
		assertEquals(TraceTestUtils.getTraceField(bt, "environments", 0, "values", "primxmap"), "val1");

		assertEquals(TraceTestUtils.getTraceField(bt, "environments", 0, "values", "listxprim"),
				listOf("val1", "val2"));
		assertEquals(TraceTestUtils.getTraceField(bt, "environments", 0, "values", "listxlist"),
				listOf("val1", "val2"));
		assertEquals(TraceTestUtils.getTraceField(bt, "environments", 0, "values", "listxmap"), listOf("val1"));

		assertEquals(TraceTestUtils.getTraceField(bt, "environments", 0, "values", "mapxprim"),
				Collections.singletonMap("mk1", "mv1"));
		assertEquals(TraceTestUtils.getTraceField(bt, "environments", 0, "values", "mapxlist"),
				Collections.singletonMap("mk1", "mv1"));
		assertEquals(TraceTestUtils.getTraceField(bt, "environments", 0, "values", "mapxmap"),
				TestUtils.hashMapBuilder().put("mk1", "mv1").put("mk2", "mv2").build());
	}

}
