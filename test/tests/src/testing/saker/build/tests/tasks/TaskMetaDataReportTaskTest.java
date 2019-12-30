package testing.saker.build.tests.tasks;

import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class TaskMetaDataReportTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class MetaDataReportingTaskFactory extends StringTaskFactory {
		private static final long serialVersionUID = 1L;

		public MetaDataReportingTaskFactory() {
			super();
		}

		public MetaDataReportingTaskFactory(String result) {
			super(result);
		}

		@Override
		public String run(TaskContext context) throws Exception {
			String result = super.run(context);
			context.setMetaData("meta", result);
			return result;
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		MetaDataReportingTaskFactory main = new MetaDataReportingTaskFactory("result");

		runTask("main", main);
		assertMap(getMetric().getTaskIdMetaDatas().get(strTaskId("main"))).contains("meta", "result");
	}

}
