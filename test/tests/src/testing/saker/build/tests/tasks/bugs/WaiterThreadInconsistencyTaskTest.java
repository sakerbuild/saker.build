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
package testing.saker.build.tests.tasks.bugs;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.StructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskConcatTaskFactory;

@SakerTest
public class WaiterThreadInconsistencyTaskTest extends CollectingMetricEnvironmentTestCase {
	//tests an issue where the condition checker in saker.build.task.TaskExecutionManager.ManagerTaskFutureImpl.waitCondition
	//threw an exception and the waiter thread handle wasn't properly closed
	//therefore imbalancing the thread counts.
	//the following code reliably reproduces the encountered issue: (only in case of test builds) 
//[saker.java.test]java.lang.AssertionError: Test case failed: testing.saker.build.tests.tasks.bugs.WaiterThreadInconsistencyTaskTest
//[saker.java.test]Caused by: java.lang.AssertionError: WaitingThreadCounter[runningThreadCount=0, waitingThreadCount=-1]
//[saker.java.test]	at saker.build.task.TaskExecutionManager.execute(TaskExecutionManager.java:4469)
//[saker.java.test]	at saker.build.runtime.execution.ExecutionContextImpl.executeTask(ExecutionContextImpl.java:501)

	private static class SecondTask implements TaskFactory<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public SecondTask() {
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return new ParameterizableTask<Object>() {

				@SakerInput("")
				public StructuredObjectTaskResult in;

				@Override
				public Object run(TaskContext taskcontext) throws Exception {
					System.out.println(
							"WaiterThreadInconsistencyTaskTest.SecondTask.createTask(...).new ParameterizableTask() {...}.run() "
									+ in);
					TaskIdentifier reftaskid = in.getTaskIdentifier();
					StringTaskConcatTaskFactory concattask = new StringTaskConcatTaskFactory("something", reftaskid);
					TaskIdentifier concattaskid = strTaskId("concat");
					taskcontext.startTask(concattaskid, concattask, null);
					return new SimpleStructuredObjectTaskResult(concattaskid);
				}
			};
		}
	}

	@Override
	protected CollectingTestMetric createMetric() {
		CollectingTestMetric metric = super.createMetric();
		TreeMap<TaskName, TaskFactory<?>> taskfactories = ObjectUtils.newTreeMap(metric.getInjectedTaskFactories());
		taskfactories.put(TaskName.valueOf("test.first.task"), new ChildTaskStarterTaskFactory().add(strTaskId("str"),
				new FileStringContentTaskFactory(SakerPath.valueOf("file.txt"))));
		taskfactories.put(TaskName.valueOf("test.second.task"), new SecondTask());
		metric.setInjectedTaskFactories(taskfactories);
		return metric;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		files.putFile(PATH_WORKING_DIRECTORY.resolve("file.txt"), "content");
		runScriptTask("build");

		files.putFile(PATH_WORKING_DIRECTORY.resolve("file.txt"), "different");
		runScriptTask("build");
	}

}
