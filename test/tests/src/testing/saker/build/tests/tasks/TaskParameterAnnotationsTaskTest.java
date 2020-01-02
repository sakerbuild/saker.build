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
import java.util.Collections;
import java.util.Optional;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;

@SakerTest
public class TaskParameterAnnotationsTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class ParameterAssignTestTaskFactory implements TaskFactory<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private int type;

		/**
		 * For {@link Externalizable}.
		 */
		public ParameterAssignTestTaskFactory() {
		}

		public ParameterAssignTestTaskFactory(int type) {
			this.type = type;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			switch (type) {
				case 1: {
					return new ParameterizableTask<String>() {
						@SakerInput("")
						public String s;

						@Override
						public String run(TaskContext taskcontext) throws Exception {
							return s;
						}
					};
				}
				case 2: {
					return new ParameterizableTask<String>() {
						@SakerInput("")
						public Optional<String> s;

						@Override
						public String run(TaskContext taskcontext) throws Exception {
							return s == null ? "NO-INPUT" : ObjectUtils.getOptional(s);
						}
					};
				}
				default: {
					break;
				}
			}
			throw fail(type + "");
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeInt(type);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			type = in.readInt();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + type;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ParameterAssignTestTaskFactory other = (ParameterAssignTestTaskFactory) obj;
			if (type != other.type)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "ParameterAssignTestTaskFactory[type=" + type + "]";
		}

	}

	private ParameterAssignTestTaskFactory factory;

	@Override
	protected CollectingTestMetric createMetric() {
		CollectingTestMetric result = super.createMetric();
		result.setInjectedTaskFactories(Collections.singletonMap(TaskName.valueOf("test.my.task"), factory));
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res;

		factory = new ParameterAssignTestTaskFactory(1);
		res = runScriptTask("_1");
		assertEquals(res.getTargetTaskResult("out"), "test");

		factory = new ParameterAssignTestTaskFactory(2);
		res = runScriptTask("_2_noin");
		assertEquals(res.getTargetTaskResult("out"), "NO-INPUT");
		res = runScriptTask("_2_nullin");
		assertEquals(res.getTargetTaskResult("out"), null);
		res = runScriptTask("_2_str");
		assertEquals(res.getTargetTaskResult("out"), "str");
	}

}
