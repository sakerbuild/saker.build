package testing.saker.build.tests.trace;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;

import saker.build.exception.PropertyComputationFailedException;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.trace.BuildTrace;
import saker.build.trace.TraceContributorEnvironmentProperty;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

@SakerTest
public class BuildTraceEnvironmentPropertyContributionTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath BUILD_TRACE_PATH = PATH_WORKING_DIRECTORY.resolve("build.trace");

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
		assertEquals(TraceTestUtils.getTraceField(tracepathkey, "environments", 0, "values"),
				Collections.singletonMap("key", "val"));

		runTask("main", new EnvironmentPropertyDependentTraceTaskFactory());
		assertEmpty(getMetric().getRunTaskIdResults());
		if (project != null) {
			project.waitExecutionFinalization();
		}
		assertEquals(TraceTestUtils.getTraceField(tracepathkey, "environments", 0, "values"),
				Collections.singletonMap("key", "val"));
	}

}
