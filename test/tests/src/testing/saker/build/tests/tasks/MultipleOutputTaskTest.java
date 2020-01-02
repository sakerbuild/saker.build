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

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class MultipleOutputTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class MultiOutputTaskFactory implements TaskFactory<Object>, Task<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		private Object output;
		private Object sideOutput;

		public MultiOutputTaskFactory() {
		}

		public MultiOutputTaskFactory(Object output, Object sideOutput) {
			this.output = output;
			this.sideOutput = sideOutput;
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			taskcontext.setTaskOutput("tag", sideOutput);
			return output;
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(output);
			out.writeObject(sideOutput);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			output = in.readObject();
			sideOutput = in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((output == null) ? 0 : output.hashCode());
			result = prime * result + ((sideOutput == null) ? 0 : sideOutput.hashCode());
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
			MultiOutputTaskFactory other = (MultiOutputTaskFactory) obj;
			if (output == null) {
				if (other.output != null)
					return false;
			} else if (!output.equals(other.output))
				return false;
			if (sideOutput == null) {
				if (other.sideOutput != null)
					return false;
			} else if (!sideOutput.equals(other.sideOutput))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		MultiOutputTaskFactory main = new MultiOutputTaskFactory("output", "side");

		runTask("main", main);
		assertEquals(getMetric().getRunTaskIdResults(), ImmutableUtils.singletonMap(strTaskId("main"), "output"));
		assertEquals(getMetric().getTaskIdTaggedResults().get(strTaskId("main")),
				ImmutableUtils.singletonMap("tag", "side"));
	}

}
