package testing.saker.build.tests.trace;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import saker.build.exception.PropertyComputationFailedException;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.trace.BuildTrace;
import saker.build.trace.TraceContributorEnvironmentProperty;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;

@SakerTest
public class TransitiveBuildTraceEnvironmentPropertyContributionTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath BUILD_TRACE_PATH = PATH_WORKING_DIRECTORY.resolve("build.trace");

	public static final class TraceContributingEnvironmentProperty
			implements TraceContributorEnvironmentProperty<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String value;

		public TraceContributingEnvironmentProperty() {
		}

		public TraceContributingEnvironmentProperty(String value) {
			this.value = value;
		}

		@Override
		public Object getCurrentValue(SakerEnvironment environment) throws Exception {
			if (!ObjectUtils.isNullOrEmpty(value)) {
				environment.getEnvironmentPropertyCurrentValue(
						new TraceContributingEnvironmentProperty(value.substring(0, value.length() - 1)));
			}
			return value;
		}

		@Override
		public void contributeBuildTraceInformation(Object propertyvalue,
				PropertyComputationFailedException thrownexception) {
			BuildTrace.setValues(Collections.singletonMap("key-" + value, Objects.toString(value)),
					BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(value);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((value == null) ? 0 : value.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TraceContributingEnvironmentProperty other = (TraceContributingEnvironmentProperty) obj;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "TraceContributingEnvironmentProperty[" + (value != null ? "value=" + value : "") + "]";
		}
	}

	public static final class EnvironmentPropertyDependentTraceTaskFactory
			implements TaskFactory<Object>, Task<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		private TraceContributingEnvironmentProperty property;

		public EnvironmentPropertyDependentTraceTaskFactory() {
		}

		public EnvironmentPropertyDependentTraceTaskFactory(TraceContributingEnvironmentProperty property) {
			this.property = property;
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			return taskcontext.getTaskUtilities().getReportEnvironmentDependency(property);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(property);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			property = (TraceContributingEnvironmentProperty) in.readObject();
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((property == null) ? 0 : property.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			EnvironmentPropertyDependentTraceTaskFactory other = (EnvironmentPropertyDependentTraceTaskFactory) obj;
			if (property == null) {
				if (other.property != null)
					return false;
			} else if (!property.equals(other.property))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		ProviderHolderPathKey tracepathkey = SakerPathFiles.getPathKey(files, BUILD_TRACE_PATH);
		parameters.setBuildTraceOutputPathKey(tracepathkey);

		runTask("main",
				new EnvironmentPropertyDependentTraceTaskFactory(new TraceContributingEnvironmentProperty("ab")));
		if (project != null) {
			project.waitExecutionFinalization();
		}
		Map<Object, Object> expectmap = TestUtils.hashMapBuilder().put("key-ab", "ab").put("key-a", "a").put("key-", "")
				.build();
		assertEquals(TraceTestUtils.getTraceField(tracepathkey, "environments", 0, "values"), expectmap);

		runTask("main",
				new EnvironmentPropertyDependentTraceTaskFactory(new TraceContributingEnvironmentProperty("ab")));
		assertEmpty(getMetric().getRunTaskIdResults());
		if (project != null) {
			project.waitExecutionFinalization();
		}
		assertEquals(TraceTestUtils.getTraceField(tracepathkey, "environments", 0, "values"), expectmap);

		System.out.println("TransitiveBuildTraceEnvironmentPropertyContributionTest.runTestImpl()");
		Map<Object, Object> expectmap2 = TestUtils.hashMapBuilder().put("key-a", "a").put("key-", "").build();
		runTask("main",
				new EnvironmentPropertyDependentTraceTaskFactory(new TraceContributingEnvironmentProperty("a")));
		assertNotEmpty(getMetric().getRunTaskIdResults());
		if (project != null) {
			project.waitExecutionFinalization();
		}
		assertEquals(TraceTestUtils.getTraceField(tracepathkey, "environments", 0, "values"), expectmap2);

		Map<Object, Object> expectmap3 = TestUtils.hashMapBuilder().put("key-abx", "abx").put("key-ab", "ab")
				.put("key-a", "a").put("key-", "").build();
		runTask("main",
				new EnvironmentPropertyDependentTraceTaskFactory(new TraceContributingEnvironmentProperty("abx")));
		assertNotEmpty(getMetric().getRunTaskIdResults());
		if (project != null) {
			project.waitExecutionFinalization();
		}
		assertEquals(TraceTestUtils.getTraceField(tracepathkey, "environments", 0, "values"), expectmap3);
	}

}
