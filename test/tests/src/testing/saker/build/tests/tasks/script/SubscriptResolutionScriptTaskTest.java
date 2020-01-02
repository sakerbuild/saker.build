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
package testing.saker.build.tests.tasks.script;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.exception.TaskParameterException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.LiteralTaskFactory;

@SakerTest
public class SubscriptResolutionScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class ListConsumerTaskFactory implements TaskFactory<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		public ListConsumerTaskFactory() {
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return new ParameterizableTask<Void>() {
				@SakerInput("")
				public List<String> vals;

				@Override
				public Void run(TaskContext taskcontext) throws Exception {
					assertEquals(vals, listOf("a", "b", "c"));
					return null;
				}

				@Override
				public void initParameters(TaskContext taskcontext,
						NavigableMap<String, ? extends TaskIdentifier> parameters) throws TaskParameterException {
					//sync to avoid interlaced output
					synchronized (ListConsumerTaskFactory.class) {
						parameters.entrySet().forEach(System.out::println);
						ParameterizableTask.super.initParameters(taskcontext, parameters);
						System.out.println("SubscriptResolutionScriptTaskTest.ListConsumerTaskFactory val: " + vals
								+ " - " + vals.getClass());
					}
				}
			};
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	@Override
	protected CollectingTestMetric createMetric() {
		CollectingTestMetric result = super.createMetric();
		TreeMap<TaskName, TaskFactory<?>> injected = ObjectUtils.newTreeMap(result.getInjectedTaskFactories());
		injected.put(TaskName.valueOf("test.consume.list"), new ListConsumerTaskFactory());
		injected.put(TaskName.valueOf("test.lit.a"), new LiteralTaskFactory("a"));
		result.setInjectedTaskFactories(injected);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("list"), listOf("a", "b", "c"));
	}

}
