package testing.saker.build.tests.trace;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Map;

import saker.build.exception.PropertyComputationFailedException;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.trace.BuildTrace;
import saker.build.trace.TraceContributorEnvironmentProperty;
import saker.build.util.exc.ExceptionView;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;
import testing.saker.build.tests.trace.TraceTestUtils.ExceptionDetailHolder;

@SakerTest
public class BuildTraceEnvironmentPropertyContributionTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath BUILD_TRACE_PATH = PATH_WORKING_DIRECTORY.resolve("build.trace");
	private static final RuntimeException VAL_THROWABLE = new RuntimeException("mythrowable");
	private static final RuntimeException VAL_EXCVIEW = new RuntimeException("myexcview");

	static {
		VAL_THROWABLE.setStackTrace(new StackTraceElement[] {});
		VAL_EXCVIEW.setStackTrace(new StackTraceElement[] {});
	}

	public static final class TraceContributingEnvironmentProperty
			implements TraceContributorEnvironmentProperty<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		public TraceContributingEnvironmentProperty() {
		}

		@Override
		public Object getCurrentValue(SakerEnvironment environment) throws Exception {
			return null;
		}

		@Override
		public void contributeBuildTraceInformation(Object propertyvalue,
				PropertyComputationFailedException thrownexception) {
			BuildTrace.setValues(Collections.singletonMap("key", "val"), BuildTrace.VALUE_CATEGORY_ENVIRONMENT);

			BuildTrace.setValues(Collections.singletonMap("throwable", VAL_THROWABLE),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
			BuildTrace.setValues(Collections.singletonMap("excview", ExceptionView.create(VAL_EXCVIEW)),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	public static final class EnvironmentPropertyDependentTraceTaskFactory extends SelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			taskcontext.getTaskUtilities().getReportEnvironmentDependency(new TraceContributingEnvironmentProperty());
			return null;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		ProviderHolderPathKey tracepathkey = SakerPathFiles.getPathKey(files, BUILD_TRACE_PATH);
		parameters.setBuildTraceOutputPathKey(tracepathkey);

		runTask("main", new EnvironmentPropertyDependentTraceTaskFactory());
		if (project != null) {
			project.waitExecutionFinalization();
		}
		Map<Object, Object> expectmap = TestUtils.hashMapBuilder().put("key", "val")
				.put("throwable",
						new ExceptionDetailHolder("java.lang.RuntimeException: mythrowable" + System.lineSeparator()))
				.put("excview",
						new ExceptionDetailHolder("java.lang.RuntimeException: myexcview" + System.lineSeparator()))
				.build();
		assertEquals(TraceTestUtils.getTraceField(tracepathkey, "environments", 0, "values"), expectmap);

		runTask("main", new EnvironmentPropertyDependentTraceTaskFactory());
		assertEmpty(getMetric().getRunTaskIdResults());
		if (project != null) {
			project.waitExecutionFinalization();
		}
		assertEquals(TraceTestUtils.getTraceField(tracepathkey, "environments", 0, "values"), expectmap);
	}

}
