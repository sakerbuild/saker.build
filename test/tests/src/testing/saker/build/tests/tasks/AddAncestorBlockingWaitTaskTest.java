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

import java.io.IOException;

import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskFuture;
import testing.saker.SakerTest;
import testing.saker.build.flag.TestFlag;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.ExecutionOrderer;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

/**
 * Task setup:
 * 
 * <pre>
 * 				(starts)
 * main------------------------------------------->waiter
 *  | \                                             /
 *  |  \  (starts)            (starts,waits)        
 *  |   \------------>blocker----------           /
 *  |                                  \          
 *   \ (starts,waits)       (starts)    V       /
 *    \-------------->plus------------->str<- - (waits)
 * </pre>
 * 
 * waiter will start to wait for str right away. It can only get the result after plus finishes. <br>
 * blocker won't finish until waiter gets the result of str.
 */
@SakerTest
public class AddAncestorBlockingWaitTaskTest extends CollectingMetricEnvironmentTestCase {
	//constants are in order they should be encountered.
	/**
	 * The {@link WaiterTaskFactory} started.
	 */
	private static final String SECTION_WAITER_START = "waiter_start";
	/**
	 * The {@link BlockerStarterTaskFactory} started.
	 */
	private static final String SECTION_BLOCKER_START = "blocker_start";
	/**
	 * {@link BlockerStarterTaskFactory} started its str subtask.
	 * <p>
	 * blocker will start to wait for str.
	 */
	private static final String SECTION_BLOCKER_STARTED = "blocker_started";
	/**
	 * The plus task is getting started
	 */
	private static final String SECTION_PLUS_STARTER = "plus_starter";
	/**
	 * The plus task has finished, and its result has been waited for by main.
	 */
	private static final String SECTION_PLUS_FINISHED = "plus_finished";
	/**
	 * The str task has ben waited for by waiter.
	 */
	private static final String SECTION_WAITER_END = "waiter_end";
	/**
	 * After blocker waited for str, it will assert that waiter got its task result from str.
	 */
	private static final String SECTION_BLOCKER_END = "blocker_end";

	private static ExecutionOrderer orderer;
	private static volatile boolean gotStrTaskResultByWaiter = false;

	private static volatile boolean waitStrFinishInStarter = false;

	private static class StarterTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			taskcontext.getTaskUtilities().startTaskFuture(strTaskId("waiter"), new WaiterTaskFactory());
			taskcontext.getTaskUtilities().startTaskFuture(strTaskId("blocker"), new BlockerStarterTaskFactory());
			orderer.enter(SECTION_PLUS_STARTER);
			ChildTaskStarterTaskFactory childstarter = new ChildTaskStarterTaskFactory() {
				@Override
				public Void run(TaskContext context) throws Exception {
					if (waitStrFinishInStarter) {
						System.out.println("Wait result of str task before starting it...");
						while (!((CollectingTestMetric) TestFlag.metric()).getRunTaskIdResults()
								.containsKey(strTaskId("str"))) {
							Thread.sleep(100);
						}

						System.out.println("Got result of str through test metric.");
					}
					return super.run(context);
				}
			};
			childstarter.add(strTaskId("str"), new StringTaskFactory("str"));

			taskcontext.getTaskUtilities().runTaskResult(strTaskId("plus"), childstarter);
			orderer.enter(SECTION_PLUS_FINISHED);
			return null;
		}
	}

	private static class WaiterTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			orderer.enter(SECTION_WAITER_START);
			taskcontext.getTaskResult(strTaskId("str"));
			gotStrTaskResultByWaiter = true;
			orderer.enter(SECTION_WAITER_END);
			return null;
		}

	}

	private static class BlockerStarterTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			orderer.enter(SECTION_BLOCKER_START);
			TaskFuture<String> strfut = taskcontext.getTaskUtilities().startTaskFuture(strTaskId("str"),
					new StringTaskFactory("str"));
			orderer.enter(SECTION_BLOCKER_STARTED);
			strfut.get();
			orderer.enter(SECTION_BLOCKER_END);
			assertTrue(gotStrTaskResultByWaiter);
			return null;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		for (int i = 0; i < 10; i++) {
			waitStrFinishInStarter = false;
			runMainTask();
			cleanProject();
			System.out.println();

			System.out.println("Wait str:");
			waitStrFinishInStarter = true;
			runMainTask();
			cleanProject();
			System.out.println();
		}
	}

	private void cleanProject() throws IOException {
		if (project != null) {
			project.clean();
		} else {
			files.clearDirectoryRecursively(PATH_BUILD_DIRECTORY);
		}
	}

	private void runMainTask() throws Throwable, AssertionError {
		gotStrTaskResultByWaiter = false;
		ExecutionOrderer orderer = new ExecutionOrderer();
		orderer.addSection(SECTION_WAITER_START);
		orderer.addSection(SECTION_BLOCKER_START);
		orderer.addSection(SECTION_BLOCKER_STARTED);
		orderer.addSection(SECTION_PLUS_STARTER);
		orderer.addSection(SECTION_PLUS_FINISHED);
		orderer.addSection(SECTION_WAITER_END);
		orderer.addSection(SECTION_BLOCKER_END);
		TaskFactory<?> main = new StarterTaskFactory();

		AddAncestorBlockingWaitTaskTest.orderer = new ExecutionOrderer(orderer);
		runTask("main", main);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(),
				strTaskIdSetOf("main", "blocker", "str", "waiter", "plus"));
	}

}
