package testing.saker.build.tests.tasks;

import java.util.Arrays;

import saker.build.task.InnerTaskResults;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.ExecutionOrderer;

@SakerTest
public class SharedDataInnerTaskTaskTest extends CollectingMetricEnvironmentTestCase {
	private static ExecutionOrderer orderer;
	private static final int TOTAL_INT_SUM_COUNT = 1000;
	private static int[] takenIntCounts;

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
			ConcurrentPrependAccumulator<InnerTaskResults<Integer>> futures = new ConcurrentPrependAccumulator<>();
			for (int i = 0; i < takenIntCounts.length; i++) {
				InnerTaskResults<Integer> fut = taskcontext.startInnerTask(new DataUserTaskFactory(i, ints), null);
				futures.add(fut);
			}
			int sum = 0;
			for (InnerTaskResults<Integer> itf : futures) {
				sum += itf.getNext().getResult();
			}
			assertEquals(sum, expectedsum);
			return null;
		}
	}

	public static class DataUserTaskFactory extends SelfStatelessTaskFactory<Integer> {
		private static final long serialVersionUID = 1L;

		private int index;
		private ConcurrentPrependAccumulator<Integer> ints;

		public DataUserTaskFactory(int index, ConcurrentPrependAccumulator<Integer> ints) {
			this.index = index;
			this.ints = ints;
		}

		@Override
		public Integer run(TaskContext taskcontext) throws Exception {
			Integer first = ints.take();
			int sum;
			if (first == null) {
				sum = 0;
			} else {
				takenIntCounts[index]++;
				sum = first;
			}
			orderer.enter(index + "");
			orderer.enter("CONTINUE");
			for (Integer i; (i = ints.take()) != null;) {
				takenIntCounts[index]++;
				sum += i;
				Thread.yield();
			}
			return sum;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		takenIntCounts = new int[5];
		orderer = new ExecutionOrderer();
		for (int i = 0; i < takenIntCounts.length; i++) {
			orderer.addSection(i + "");
		}
		for (int i = 0; i < takenIntCounts.length; i++) {
			orderer.addSection("CONTINUE");
		}
		runTask("main", new InnerTaskStarter());
		//assert that the tasks have been distributed to at least two inner tasks
		for (int i = 0; i < takenIntCounts.length; i++) {
			assertTrue(takenIntCounts[0] > 0);
		}
		assertNotEquals(takenIntCounts[0], TOTAL_INT_SUM_COUNT);
		System.out.println("SharedDataInnerTaskTaskTest.runTestImpl() " + Arrays.toString(takenIntCounts));
	}

}
