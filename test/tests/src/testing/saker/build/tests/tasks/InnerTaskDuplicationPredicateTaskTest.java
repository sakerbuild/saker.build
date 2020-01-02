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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResultHolder;
import saker.build.task.InnerTaskResults;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskDuplicationPredicate;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class InnerTaskDuplicationPredicateTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final int TOTAL_INT_SUM_COUNT = 1000;

	private static final class DuplicationPredicate implements TaskDuplicationPredicate {
		private static final AtomicIntegerFieldUpdater<InnerTaskDuplicationPredicateTaskTest.DuplicationPredicate> AIFU_c = AtomicIntegerFieldUpdater
				.newUpdater(InnerTaskDuplicationPredicateTaskTest.DuplicationPredicate.class, "c");
		@SuppressWarnings("unused")
		private volatile int c;

		@Override
		public boolean shouldInvokeOnceMore() throws RuntimeException {
			return AIFU_c.getAndIncrement(this) < TOTAL_INT_SUM_COUNT;
		}
	}

	public static class InnerTaskStarter extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		public InnerTaskStarter() {
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			ConcurrentPrependAccumulator<Integer> ints = new ConcurrentPrependAccumulator<>();
			int expectedsum = 0;
			for (int i = 0; i < TOTAL_INT_SUM_COUNT; i++) {
				ints.add(i);
				expectedsum += i;
			}
			InnerTaskExecutionParameters innerexecparams = new InnerTaskExecutionParameters();
			innerexecparams.setDuplicationPredicate(new DuplicationPredicate());
			InnerTaskResults<Integer> innerresults = taskcontext.startInnerTask(new DataUserTaskFactory(ints),
					innerexecparams);
			int sum = 0;
			for (InnerTaskResultHolder<Integer> res; (res = innerresults.getNext()) != null;) {
				sum += res.getResult();
			}
			assertEquals(sum, expectedsum);
			if (!ints.isEmpty()) {
				throw fail("Not all ints taken.");
			}
			return null;
		}

	}

	public static class DataUserTaskFactory extends StatelessTaskFactory<Integer> {
		private static final long serialVersionUID = 1L;

		private ConcurrentPrependAccumulator<Integer> ints;

		public DataUserTaskFactory(ConcurrentPrependAccumulator<Integer> ints) {
			this.ints = ints;
		}

		@Override
		public Task<? extends Integer> createTask(ExecutionContext executioncontext) {
			return new Task<Integer>() {
				private int stateInt = 0;

				@Override
				public Integer run(TaskContext taskcontext) throws Exception {
					//to make sure that actually new task instances are created
					assertEquals(stateInt++, 0);
					int result = ints.take().intValue();
					return result;
				}
			};
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new InnerTaskStarter());

		runTask("main", new InnerTaskStarter());
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
