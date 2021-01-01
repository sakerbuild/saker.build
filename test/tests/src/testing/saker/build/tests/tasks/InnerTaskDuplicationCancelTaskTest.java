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
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.TaskContext;
import saker.build.task.TaskDuplicationPredicate;
import saker.build.task.TaskResultCollection;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class InnerTaskDuplicationCancelTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final int TOTAL_INT_SUM_COUNT = 4000;

	public static class InnerTaskResult implements Externalizable {
		private static final long serialVersionUID = 1L;
		public Set<Integer> result = new ConcurrentSkipListSet<>();

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			SerialUtils.writeExternalCollection(out, result);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			result = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		}
	}

	private static final class DuplicationPredicate implements TaskDuplicationPredicate {
		private static final AtomicIntegerFieldUpdater<DuplicationPredicate> AIFU_c = AtomicIntegerFieldUpdater
				.newUpdater(DuplicationPredicate.class, "c");
		@SuppressWarnings("unused")
		private volatile int c;

		@Override
		public boolean shouldInvokeOnceMore() throws RuntimeException {
			return AIFU_c.getAndIncrement(this) < TOTAL_INT_SUM_COUNT;
		}
	}

	public static class InnerTaskStarter extends SelfStatelessTaskFactory<InnerTaskResult> {
		private static final long serialVersionUID = 1L;

		public InnerTaskStarter() {
		}

		@Override
		@SuppressWarnings("deprecation")
		public NavigableSet<String> getCapabilities() {
			return ImmutableUtils.singletonNavigableSet(CAPABILITY_INNER_TASKS_COMPUTATIONAL);
		}

		@Override
		public InnerTaskResult run(TaskContext taskcontext) throws Exception {
			ConcurrentPrependAccumulator<Integer> ints = new ConcurrentPrependAccumulator<>();
			for (int i = 0; i < TOTAL_INT_SUM_COUNT; i++) {
				ints.add(i);
			}

			InnerTaskExecutionParameters innerexecparams = new InnerTaskExecutionParameters();
			innerexecparams.setClusterDuplicateFactor(-1);
			innerexecparams.setDuplicationPredicate(new DuplicationPredicate());
			innerexecparams.setDuplicationCancellable(true);
			InnerTaskResult result = new InnerTaskResult();
			taskcontext.startInnerTask(new SummerTaskFactory(result, ints), innerexecparams);
			return result;
		}
	}

	public static class SummerTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		private InnerTaskResult result;
		private ConcurrentPrependAccumulator<Integer> ints;

		public SummerTaskFactory(InnerTaskResult result, ConcurrentPrependAccumulator<Integer> ints) {
			this.result = result;
			this.ints = ints;
		}

		@Override
		@SuppressWarnings("deprecation")
		public NavigableSet<String> getCapabilities() {
			return ImmutableUtils.singletonNavigableSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		@SuppressWarnings("deprecation")
		public int getRequestedComputationTokenCount() {
			return 1;
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			int result = ints.take().intValue();
			//sleep some to avoid running all of the tasks fast
			Thread.sleep(50);
			this.result.result.add(result);
			return null;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		TaskResultCollection res;
		res = runTask("main", new InnerTaskStarter());
		assertTrue(getSizeOfResult(res) < TOTAL_INT_SUM_COUNT);

		res = runTask("main", new InnerTaskStarter());
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertTrue(getSizeOfResult(res) < TOTAL_INT_SUM_COUNT);
	}

	private static int getSizeOfResult(TaskResultCollection res) {
		TaskIdentifier mainid = strTaskId("main");
		InnerTaskResult mainresult = (InnerTaskResult) res.getTaskResult(mainid);
		Set<Integer> mainresultints = mainresult.result;
		return mainresultints.size();
	}

}
