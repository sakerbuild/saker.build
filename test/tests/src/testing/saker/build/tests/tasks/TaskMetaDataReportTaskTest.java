/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
