package testing.saker.build.tests.tasks;

import saker.build.task.TaskContext;
import saker.build.task.exception.IllegalTaskOperationException;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.SequentialChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class FinishedResultRetrievalTaskTest extends CollectingMetricEnvironmentTestCase {
	private static class FinishedGetterTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			return taskcontext.getTaskFuture(strTaskId("waited")).getFinished().toString();
		}
	}

	private static class SelfStartFinishedGetterTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.getTaskUtilities().runTaskResult(strTaskId("waited"), new StringTaskFactory("str"));
			return taskcontext.getTaskFuture(strTaskId("waited")).getFinished().toString();
		}
	}

	private static class TransitiveCreatorTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.getTaskResult(strTaskId("waited"));
			return taskcontext.getTaskUtilities().runTaskResult(strTaskId("getter"), new FinishedGetterTaskFactory());
		}
	}

	private static class TransitiveStarterGetterTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		private static class TransitiveStarterGetterTaskFactorySecondary extends SelfStatelessTaskFactory<String> {
			private static final long serialVersionUID = 1L;

			@Override
			public String run(TaskContext taskcontext) throws Exception {
				taskcontext.getTaskUtilities().startTaskFuture(strTaskId("getrunner"),
						new ChildTaskStarterTaskFactory().add(strTaskId("getter"), new FinishedGetterTaskFactory()));
				taskcontext.getTaskResult(strTaskId("getter"));
				return null;
			}
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.getTaskUtilities().runTaskResult(strTaskId("dep"),
					new SequentialChildTaskStarterTaskFactory().add(strTaskId("waited"), new StringTaskFactory("str")));
			taskcontext.getTaskUtilities().startTaskFuture(strTaskId("secondary"),
					new TransitiveStarterGetterTaskFactorySecondary());
			return "123";
		}
	}

	private static class ReverseStarterTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			System.out.println("FinishedResultRetrievalTaskTest.ReverseStarterTaskFactory.run()");
			taskcontext.getTaskUtilities().startTaskFuture(strTaskId("getter"), new FinishedGetterTaskFactory());
			taskcontext.getTaskUtilities().runTaskResult(strTaskId("waited"), new StringTaskFactory("str"));
			return null;
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("selfwait", new SelfStartFinishedGetterTaskFactory());

		SequentialChildTaskStarterTaskFactory main = new SequentialChildTaskStarterTaskFactory();
		main.add(strTaskId("waited"), new StringTaskFactory("str"));
		main.add(strTaskId("getter"), new FinishedGetterTaskFactory());
		runTask("main", main);

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdResults());

		SequentialChildTaskStarterTaskFactory mainmod = new SequentialChildTaskStarterTaskFactory();
		mainmod.add(strTaskId("waited"), new StringTaskFactory("strmod"));
		mainmod.add(strTaskId("getter"), new FinishedGetterTaskFactory());
		runTask("main", mainmod);

		ChildTaskStarterTaskFactory illegalwaiter = new ChildTaskStarterTaskFactory();
		illegalwaiter.add(strTaskId("waited"), new StringTaskFactory("str"));
		illegalwaiter.add(strTaskId("gettera"), new FinishedGetterTaskFactory());
		assertTaskException(IllegalTaskOperationException.class, () -> runTask("illegalwaiter", illegalwaiter));

		SequentialChildTaskStarterTaskFactory indirectancestor = new SequentialChildTaskStarterTaskFactory();
		indirectancestor.add(strTaskId("waited"), new StringTaskFactory("str"));
		indirectancestor.add(strTaskId("getstarter"),
				new ChildTaskStarterTaskFactory().add(strTaskId("getter"), new FinishedGetterTaskFactory()));
		runTask("indirect", indirectancestor);

		SequentialChildTaskStarterTaskFactory indirect2ancestor = new SequentialChildTaskStarterTaskFactory();
		indirect2ancestor.add(strTaskId("waitedstarter"),
				new SequentialChildTaskStarterTaskFactory().add(strTaskId("waited"), new StringTaskFactory("str")));
		indirect2ancestor.add(strTaskId("getstarter"),
				new ChildTaskStarterTaskFactory().add(strTaskId("getter"), new FinishedGetterTaskFactory()));
		runTask("indirect2", indirect2ancestor);

		SequentialChildTaskStarterTaskFactory accessmodifyingancestor = new SequentialChildTaskStarterTaskFactory();
		accessmodifyingancestor.add(strTaskId("waitedstarter"),
				new SequentialChildTaskStarterTaskFactory().add(strTaskId("waited"), new StringTaskFactory("str")));
		accessmodifyingancestor.add(strTaskId("getstarter"),
				new ChildTaskStarterTaskFactory().add(strTaskId("getter"), new FinishedGetterTaskFactory()));
		runTask("accessmodifying", accessmodifyingancestor);

		SequentialChildTaskStarterTaskFactory accessmodifyingancestormod = new SequentialChildTaskStarterTaskFactory();
		accessmodifyingancestormod.add(strTaskId("waitedstarter"),
				new ChildTaskStarterTaskFactory().add(strTaskId("waited"), new StringTaskFactory("str")));
		accessmodifyingancestormod.add(strTaskId("getstarter"),
				new ChildTaskStarterTaskFactory().add(strTaskId("getter"), new FinishedGetterTaskFactory()));
		assertTaskException(IllegalTaskOperationException.class,
				() -> runTask("accessmodifying", accessmodifyingancestormod));

		ChildTaskStarterTaskFactory transitivecreated = new ChildTaskStarterTaskFactory();
		transitivecreated.add(strTaskId("waited"), new StringTaskFactory("str"));
		transitivecreated.add(strTaskId("trangetter"), new TransitiveCreatorTaskFactory());
		runTask("trancreated", transitivecreated);

		runTask("trancreated", transitivecreated);
		assertEmpty(getMetric().getRunTaskIdResults());

		TransitiveStarterGetterTaskFactory transtarter = new TransitiveStarterGetterTaskFactory();
		runTask("transtarter", transtarter);

		runTask("transtarter", transtarter);
		assertEmpty(getMetric().getRunTaskIdResults());

		assertTaskException(IllegalTaskOperationException.class,
				() -> runTask("reverse", new ReverseStarterTaskFactory()));
	}
}
