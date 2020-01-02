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

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class TestEnvironmentSelectionTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class TesterTaskFactory extends StatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return new Task<Void>() {
				@Override
				public Void run(TaskContext taskcontext) throws Exception {
					assertNonNull(executioncontext.testEnvironmentSelection(getExecutionEnvironmentSelector(), null));
					assertException(TaskEnvironmentSelectionFailedException.class,
							() -> executioncontext.testEnvironmentSelection(new TaskExecutionEnvironmentSelector() {
								@Override
								public EnvironmentSelectionResult isSuitableExecutionEnvironment(
										SakerEnvironment environment) {
									return null;
								}
							}, null));
					TaskEnvironmentSelectionFailedException unsupportedexc = assertException(
							TaskEnvironmentSelectionFailedException.class,
							() -> executioncontext.testEnvironmentSelection(new TaskExecutionEnvironmentSelector() {
								@Override
								public EnvironmentSelectionResult isSuitableExecutionEnvironment(
										SakerEnvironment environment) {
									throw new UnsupportedOperationException();
								}
							}, null));
					assertEquals(ObjectUtils.classOf(unsupportedexc.getCause()), UnsupportedOperationException.class);
					return null;
				}
			};
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new TesterTaskFactory());
	}

}
