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

import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.runtime.execution.SecretInputReader;
import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class SecretInputReaderTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class SecretReadingTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			return taskcontext.getSecretReader().readSecret(null, null, null, null);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new SecretReadingTaskFactory());

		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "secret");
	}

	@Override
	protected void setupParameters(ExecutionParametersImpl params) {
		super.setupParameters(params);
		params.setSecretInputReader(new SecretInputReader() {
			@Override
			public String readSecret(String titleinfo, String message, String prompt, String secretidentifier) {
				return "secret";
			}
		});
	}
}
