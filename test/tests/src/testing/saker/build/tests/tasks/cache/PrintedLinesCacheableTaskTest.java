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
package testing.saker.build.tests.tasks.cache;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.NavigableSet;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import testing.saker.SakerTest;

@SakerTest
public class PrintedLinesCacheableTaskTest extends CacheableTaskTestCase {
	private static class LinePrintingCacheableTaskFactory implements TaskFactory<Void>, Task<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		private List<String> lines;

		/**
		 * For {@link Externalizable}.
		 */
		public LinePrintingCacheableTaskFactory() {
		}

		public LinePrintingCacheableTaskFactory(List<String> lines) {
			this.lines = lines;
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			for (String line : lines) {
				taskcontext.println(line);
			}
			return null;
		}

		@Override
		@SuppressWarnings("deprecation")
		public NavigableSet<String> getCapabilities() {
			return ImmutableUtils.singletonNavigableSet(TaskFactory.CAPABILITY_CACHEABLE);
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			SerialUtils.writeExternalCollection(out, lines);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			lines = SerialUtils.readExternalImmutableList(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((lines == null) ? 0 : lines.hashCode());
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
			LinePrintingCacheableTaskFactory other = (LinePrintingCacheableTaskFactory) obj;
			if (lines == null) {
				if (other.lines != null)
					return false;
			} else if (!lines.equals(other.lines))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		LinePrintingCacheableTaskFactory main = new LinePrintingCacheableTaskFactory(
				ImmutableUtils.asUnmodifiableArrayList("line1", "line2"));

		runTask("main", main);
		assertEquals(getMetric().getTaskPrintedLines().get(strTaskId("main")), main.lines);
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("main"));

		cleanProject();
		runTask("main", main);
		assertEquals(getMetric().getTaskPrintedLines().get(strTaskId("main")), main.lines);
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("main"));
	}

}
