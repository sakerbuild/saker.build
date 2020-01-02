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

import java.io.Externalizable;

import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.ExecutionOrderer;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class InnerTaskOverExecuteTaskTest extends CollectingMetricEnvironmentTestCase {
	private static ExecutionOrderer orderer;

	public static class InnerTaskStarter extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public InnerTaskStarter() {
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			orderer.enter("TASK_START");
			taskcontext.startInnerTask(new SleepingTaskFactory(), null);
			orderer.enter("TASK_END");
			return null;
		}
	}

	public static class SleepingTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			orderer.enter("INNER_START");
			Thread.sleep(200);
			taskcontext.startInnerTask(new StringTaskFactory("hello"), null).getNext();
			orderer.enter("INNER_END");
			return null;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		ExecutionOrderer orderer = new ExecutionOrderer();
		orderer.addSection("TASK_START");
		orderer.addSection("TASK_END");
		orderer.addSection("INNER_START");
		orderer.addSection("INNER_END");

		InnerTaskOverExecuteTaskTest.orderer = new ExecutionOrderer(orderer);
		runTask("main", new InnerTaskStarter());
	}

}
