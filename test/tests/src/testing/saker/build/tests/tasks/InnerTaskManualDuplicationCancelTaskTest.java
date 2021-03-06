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

import java.util.NavigableSet;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResults;
import saker.build.task.TaskContext;
import saker.build.task.TaskDuplicationPredicate;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class InnerTaskManualDuplicationCancelTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final int TOTAL_DUPLICATION_COUNT = 4000;

	private static final class DuplicationPredicate implements TaskDuplicationPredicate {
		private static final AtomicIntegerFieldUpdater<DuplicationPredicate> AIFU_c = AtomicIntegerFieldUpdater
				.newUpdater(DuplicationPredicate.class, "c");
		@SuppressWarnings("unused")
		private volatile int c;

		@Override
		public boolean shouldInvokeOnceMore() throws RuntimeException {
			return AIFU_c.getAndIncrement(this) < TOTAL_DUPLICATION_COUNT;
		}
	}

	public static class InnerTaskStarter extends SelfStatelessTaskFactory<Integer> {
		private static final long serialVersionUID = 1L;

		public InnerTaskStarter() {
		}

		@Override
		@SuppressWarnings("deprecation")
		public NavigableSet<String> getCapabilities() {
			return ImmutableUtils.singletonNavigableSet(CAPABILITY_INNER_TASKS_COMPUTATIONAL);
		}

		@Override
		public Integer run(TaskContext taskcontext) throws Exception {
			DuplicationPredicate duppredicate = new DuplicationPredicate();

			InnerTaskExecutionParameters innerexecparams = new InnerTaskExecutionParameters();
			innerexecparams.setDuplicationPredicate(duppredicate);
			innerexecparams.setDuplicationCancellable(true);
			InnerTaskResults<?> results = taskcontext.startInnerTask(new DuplicatedTaskFactory(), innerexecparams);
			//check if there are at least some invocations
			assertNonNull(results.getNext());
			assertNonNull(results.getNext());
			results.cancelDuplicationOptionally();
			int i = 2;
			while (results.getNext() != null) {
				i++;
			}
			System.out.println("InnerTaskManualDuplicationCancelTaskTest.InnerTaskStarter.run() " + i);
			assertTrue(i < TOTAL_DUPLICATION_COUNT);
			return i;
		}
	}

	public static class DuplicatedTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		@SuppressWarnings("deprecation")
		public int getRequestedComputationTokenCount() {
			return 1;
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			Thread.sleep(50);
			return null;
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new InnerTaskStarter());

		runTask("main", new InnerTaskStarter());
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
