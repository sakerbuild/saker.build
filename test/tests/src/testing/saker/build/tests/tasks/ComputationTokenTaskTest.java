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

import java.util.concurrent.atomic.AtomicInteger;

import saker.build.task.ComputationToken;
import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class ComputationTokenTaskTest extends CollectingMetricEnvironmentTestCase {
	public static class SingleRunningComputationTokenStringTaskFactory extends StringTaskFactory {
		private static final long serialVersionUID = 1L;

		private static final AtomicInteger runningCount = new AtomicInteger(0);

		public SingleRunningComputationTokenStringTaskFactory() {
			super();
		}

		public SingleRunningComputationTokenStringTaskFactory(String result) {
			super(result);
		}

		@Override
		public int getRequestedComputationTokenCount() {
			//to ensure no two tasks can run at the same time
			return ComputationToken.MAX_TOKEN_COUNT * 2;
		}

		@Override
		public String run(TaskContext context) throws Exception {
			runningCount.incrementAndGet();
			try {
				//sleep some amount to wait the other task to be started
				Thread.sleep(200);
				assertEquals(runningCount.get(), 1);
				return super.run(context);
			} finally {
				runningCount.decrementAndGet();
			}
		}
	}

	public static class MultiRunningComputationTokenStringTaskFactory extends StringTaskFactory {
		private static final long serialVersionUID = 1L;

		private static final AtomicInteger runningCount = new AtomicInteger(0);

		public MultiRunningComputationTokenStringTaskFactory() {
			super();
		}

		public MultiRunningComputationTokenStringTaskFactory(String result) {
			super(result);
		}

		@Override
		public int getRequestedComputationTokenCount() {
			//to ensure that two tasks can run at the same time
			return ComputationToken.MAX_TOKEN_COUNT / 2;
		}

		@Override
		public String run(TaskContext context) throws Exception {
			runningCount.incrementAndGet();
			synchronized (MultiRunningComputationTokenStringTaskFactory.class) {
				MultiRunningComputationTokenStringTaskFactory.class.notifyAll();
			}
			while (!Thread.interrupted()) {
				synchronized (MultiRunningComputationTokenStringTaskFactory.class) {
					if (runningCount.get() == 2) {
						break;
					}
					MultiRunningComputationTokenStringTaskFactory.class.wait();
				}
			}
			//if we reach here there is two running tasks at the same time
			//do not decrement the count so the other task has a chance to exit the loop
			return super.run(context);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		SingleRunningComputationTokenStringTaskFactory.runningCount.set(0);
		MultiRunningComputationTokenStringTaskFactory.runningCount.set(0);

		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory()
				.add(strTaskId("str1"), new SingleRunningComputationTokenStringTaskFactory("str1"))
				.add(strTaskId("str2"), new SingleRunningComputationTokenStringTaskFactory("str2"));

		runTask("main", main);

		ChildTaskStarterTaskFactory dual = new ChildTaskStarterTaskFactory()
				.add(strTaskId("str1"), new MultiRunningComputationTokenStringTaskFactory("str1"))
				.add(strTaskId("str2"), new MultiRunningComputationTokenStringTaskFactory("str2"));

		runTask("dual", dual);

	}

}
