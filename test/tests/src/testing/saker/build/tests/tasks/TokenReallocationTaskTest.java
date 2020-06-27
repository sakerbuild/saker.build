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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntSupplier;

import saker.build.task.ComputationToken;
import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResultHolder;
import saker.build.task.InnerTaskResults;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.ExecutionOrderer;

@SakerTest
public class TokenReallocationTaskTest extends CollectingMetricEnvironmentTestCase {
	private static ExecutionOrderer orderer;
	private static List<String> executedOrder;

	public static final int MAX_COUNTER = ComputationToken.getMaxTokenCount() * 2;

	public static class StarterTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			try {
				taskcontext.startTask(strTaskId("starter"), new InnerStarterTaskFactory(), null);
				orderer.enter("started");
				taskcontext.startTask(strTaskId("concurrent"), new ConcurrentTaskFactory(), null);
				//wait for a few millis to allow the token request to arrive from the concurrent task
				LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(500));
				orderer.enter("started-all");
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw e;
			}
			return null;
		}

	}

	public static class InnerStarterTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			InnerTaskExecutionParameters execparams = new InnerTaskExecutionParameters();
			AtomicInteger ival = new AtomicInteger();
			execparams.setDuplicationPredicate(() -> ival.get() <= MAX_COUNTER);
			InnerTaskResults<Void> results = taskcontext.startInnerTask(new InnerTaskFactory(ival::getAndIncrement),
					execparams);

			//get the results
			InnerTaskResultHolder<Void> n;
			while ((n = results.getNext()) != null) {
				n.getResult();
			}
			orderer.enter("exit");
			return null;
		}

		@Override
		public Set<String> getCapabilities() {
			return ImmutableUtils.singletonSet(CAPABILITY_INNER_TASKS_COMPUTATIONAL);
		}
	}

	public static class ConcurrentTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			System.out.println("TokenReallocationTaskTest.ConcurrentTaskFactory.run() enter");
			executedOrder.add("concurrent");
			LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(500));
			System.out.println("TokenReallocationTaskTest.ConcurrentTaskFactory.run() exit");
			return null;
		}

		@Override
		public int getRequestedComputationTokenCount() {
			return 1;
		}
	}

	public static class InnerTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		private IntSupplier supplier;

		public InnerTaskFactory(IntSupplier supplier) {
			this.supplier = supplier;
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			try {
				int val = supplier.getAsInt();
				if (val >= MAX_COUNTER) {
					System.out.println("TokenReallocationTaskTest.InnerTaskFactory.run() skip " + val);
					return null;
				}
				System.out.println("TokenReallocationTaskTest.InnerTaskFactory.run() enter " + val);
				orderer.enter("enter-inner-" + val);

				LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(500));
				executedOrder.add("inner-" + val);

				System.out.println("TokenReallocationTaskTest.InnerTaskFactory.run() exiting " + val);
				orderer.enter("exit-inner-" + val);
				System.out.println("TokenReallocationTaskTest.InnerTaskFactory.run() exited " + val);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw e;
			}
			return null;
		}

		@Override
		public int getRequestedComputationTokenCount() {
			return 1;
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		System.out.println("Max count: " + MAX_COUNTER);
		System.out.println("Computation token count: " + ComputationToken.getMaxTokenCount());
		ExecutionOrderer orderer = new ExecutionOrderer();
		for (int i = 0; i < ComputationToken.getMaxTokenCount(); i++) {
			orderer.addSection("enter-inner-" + i);
		}
		orderer.addSection("started");
		orderer.addSection("started-all");
		orderer.addSection("exit-inner-" + (ComputationToken.getMaxTokenCount() - 1));
		for (int i = 0; i < ComputationToken.getMaxTokenCount() - 1; i++) {
			orderer.addSection("exit-inner-" + i);
		}
		for (int i = ComputationToken.getMaxTokenCount(); i < MAX_COUNTER; i++) {
			orderer.addSection("enter-inner-" + i);
			orderer.addSection("exit-inner-" + i);
		}
		orderer.addSection("exit");
		TokenReallocationTaskTest.orderer = orderer;
		executedOrder = Collections.synchronizedList(new ArrayList<>());

		System.out.println(orderer);

		TaskFactory<?> main = new StarterTaskFactory();
		Thread chtread = Thread.currentThread();
		ThreadUtils.startDaemonThread(() -> {
			try {
				Thread.sleep(20 * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			chtread.interrupt();
		});
		runTask("main", main);
		//assert that the concurrent task was run during the inner task duplication
		//i.e. it was not run last
		System.out.println("Execution order: " + executedOrder);
		assertTrue(executedOrder.indexOf("concurrent") >= 0);
		assertTrue(executedOrder.indexOf("concurrent") != executedOrder.size() - 1);
	}

}
