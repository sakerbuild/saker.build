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
package testing.saker.build.tests.tasks.mgmt;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import saker.build.task.InnerTaskResults;
import saker.build.task.TaskContext;
import saker.build.task.TaskFuture;
import saker.build.task.TaskInvocationConfiguration;
import saker.build.task.TaskResultCollection;
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.TestClusterNameExecutionEnvironmentSelector;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;
import testing.saker.build.tests.tasks.cluster.ClusterBuildTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.SequentialChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class TaskManagementRestrictionTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final TaskInvocationConfiguration CLUSTER_INVOCATION_CONFIG = TaskInvocationConfiguration.builder()
			.setRemoteDispatchable(true).setExecutionEnvironmentSelector(
					new TestClusterNameExecutionEnvironmentSelector(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME))
			.build();

	public static abstract class EnvironmentSelectingSelfStatelessTaskFactory<T> extends SelfStatelessTaskFactory<T> {
		private static final long serialVersionUID = 1L;

		public TaskInvocationConfiguration invocationConfiguration;

		public EnvironmentSelectingSelfStatelessTaskFactory() {
		}

		public EnvironmentSelectingSelfStatelessTaskFactory(TaskInvocationConfiguration invocationConfiguration) {
			this.invocationConfiguration = invocationConfiguration;
		}

		@Override
		public TaskInvocationConfiguration getInvocationConfiguration() {
			return invocationConfiguration == null ? super.getInvocationConfiguration() : invocationConfiguration;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
			out.writeObject(invocationConfiguration);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			invocationConfiguration = (TaskInvocationConfiguration) in.readObject();
		}
	}

	public static class StarterTaskFactory extends EnvironmentSelectingSelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		public StarterTaskFactory() {
			super();
		}

		public StarterTaskFactory(TaskInvocationConfiguration invocationConfiguration) {
			super(invocationConfiguration);
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			taskcontext.startTask(strTaskId("started1"), new StringTaskFactory("123"), null);
			taskcontext.getTaskUtilities().runTask(strTaskId("started2"), new StringTaskFactory("123"));
			taskcontext.getTaskUtilities().runTaskResult(strTaskId("started3"), new StringTaskFactory("123"));
			TaskFuture<String> ft1 = taskcontext.getTaskUtilities().runTaskFuture(strTaskId("started4"),
					new StringTaskFactory("123"));
			TaskFuture<String> ft2 = taskcontext.getTaskUtilities().startTaskFuture(strTaskId("started5"),
					new StringTaskFactory("123"));

			ThreadUtils.startDaemonThread(() -> {
				assertException(IllegalTaskOperationException.class, ft1::get);
				assertException(IllegalTaskOperationException.class, ft2::get);
			}).join();

			ft1.get();
			ft2.get();
			return "st";
		}

	}

	public static class WorkerThreadStarterTaskFactory extends EnvironmentSelectingSelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		public WorkerThreadStarterTaskFactory() {
			super();
		}

		public WorkerThreadStarterTaskFactory(TaskInvocationConfiguration invocationConfiguration) {
			super(invocationConfiguration);
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			ThreadUtils.startDaemonThread(() -> {
				assertException(IllegalTaskOperationException.class, () -> {
					taskcontext.startTask(strTaskId("started1"), new StringTaskFactory("123"), null);
				});
			}).join();
			ThreadUtils.startDaemonThread(() -> {
				assertException(IllegalTaskOperationException.class, () -> {
					taskcontext.getTaskUtilities().runTask(strTaskId("started2"), new StringTaskFactory("123"));
				});
			}).join();
			ThreadUtils.startDaemonThread(() -> {
				assertException(IllegalTaskOperationException.class, () -> {
					taskcontext.getTaskUtilities().runTaskResult(strTaskId("started3"), new StringTaskFactory("123"));
				});
			}).join();
			ThreadUtils.startDaemonThread(() -> {
				assertException(IllegalTaskOperationException.class, () -> {
					taskcontext.getTaskUtilities().runTaskFuture(strTaskId("started4"), new StringTaskFactory("123"));
				});
			}).join();
			ThreadUtils.startDaemonThread(() -> {
				assertException(IllegalTaskOperationException.class, () -> {
					taskcontext.getTaskUtilities().startTaskFuture(strTaskId("started5"), new StringTaskFactory("123"));
				});
			}).join();
			return "wt";
		}

	}

	public static class InnerThreadStarterTaskFactory extends EnvironmentSelectingSelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		public TaskInvocationConfiguration innerConfig;

		public InnerThreadStarterTaskFactory() {
			super();
		}

		public InnerThreadStarterTaskFactory(TaskInvocationConfiguration invocationConfiguration) {
			super(invocationConfiguration);
		}

		public InnerThreadStarterTaskFactory(TaskInvocationConfiguration invocationConfiguration,
				TaskInvocationConfiguration innerConfig) {
			super(invocationConfiguration);
			this.innerConfig = innerConfig;
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			InnerTaskResults<String> ires = taskcontext.startInnerTask(new StringTaskFactory("inner"), null);
			ThreadUtils.startDaemonThread(() -> {
				assertException(IllegalTaskOperationException.class, () -> {
					ires.getNext().getResult();
				});
			}).join();

			return null;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
			out.writeObject(innerConfig);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			innerConfig = (TaskInvocationConfiguration) in.readObject();
		}
	}

	public static class InnerWorkerThreadStarterTaskFactory
			extends EnvironmentSelectingSelfStatelessTaskFactory<Object> {
		private static final long serialVersionUID = 1L;

		public TaskInvocationConfiguration innerConfig;

		public InnerWorkerThreadStarterTaskFactory() {
			super();
		}

		public InnerWorkerThreadStarterTaskFactory(TaskInvocationConfiguration invocationConfiguration) {
			super(invocationConfiguration);
		}

		public InnerWorkerThreadStarterTaskFactory(TaskInvocationConfiguration invocationConfiguration,
				TaskInvocationConfiguration innerConfig) {
			super(invocationConfiguration);
			this.innerConfig = innerConfig;
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			InnerTaskResults<Object> res = taskcontext.startInnerTask(new WorkerThreadStarterTaskFactory(innerConfig),
					null);
			assertEquals(res.getNext().getResult(), "wt");
			return null;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
			out.writeObject(innerConfig);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			innerConfig = (TaskInvocationConfiguration) in.readObject();
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		TaskResultCollection res;
		//should succeed
		res = runTask("1", new ChildTaskStarterTaskFactory().add(strTaskId("str"), new StringTaskFactory("str"))
				.add(strTaskId("second"), new StringTaskFactory("second")));

		//should succeed
		res = runTask("2",
				new SequentialChildTaskStarterTaskFactory().add(strTaskId("str"), new StringTaskFactory("str"))
						.add(strTaskId("second"), new StringTaskFactory("second")));

		res = runTask("3", new StarterTaskFactory());
		assertEquals(res.getTaskResult(strTaskId("3")), "st");
		res = runTask("4", new WorkerThreadStarterTaskFactory());
		assertEquals(res.getTaskResult(strTaskId("4")), "wt");

		res = runTask("5", new InnerThreadStarterTaskFactory());
		res = runTask("6", new InnerWorkerThreadStarterTaskFactory());

		res = runTask("c3", new StarterTaskFactory(CLUSTER_INVOCATION_CONFIG));
		assertEquals(res.getTaskResult(strTaskId("c3")), "st");
		res = runTask("c4", new WorkerThreadStarterTaskFactory(CLUSTER_INVOCATION_CONFIG));
		assertEquals(res.getTaskResult(strTaskId("c4")), "wt");

		res = runTask("c5", new InnerThreadStarterTaskFactory(CLUSTER_INVOCATION_CONFIG));
		res = runTask("c6", new InnerWorkerThreadStarterTaskFactory(CLUSTER_INVOCATION_CONFIG));

		res = runTask("cx5", new InnerThreadStarterTaskFactory(null, CLUSTER_INVOCATION_CONFIG));
		res = runTask("cc5", new InnerThreadStarterTaskFactory(CLUSTER_INVOCATION_CONFIG, CLUSTER_INVOCATION_CONFIG));
		res = runTask("cx6", new InnerWorkerThreadStarterTaskFactory(null, CLUSTER_INVOCATION_CONFIG));
		res = runTask("cc6",
				new InnerWorkerThreadStarterTaskFactory(CLUSTER_INVOCATION_CONFIG, CLUSTER_INVOCATION_CONFIG));
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setClusterNames(ImmutableUtils.singletonSet(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME)).build();
	}

}
