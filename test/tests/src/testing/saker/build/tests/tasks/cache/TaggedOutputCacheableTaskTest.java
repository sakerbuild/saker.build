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
import java.util.NavigableSet;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;

@SakerTest
public class TaggedOutputCacheableTaskTest extends CacheableTaskTestCase {

	private static class TaggedOutputTaskFactory implements Task<String>, TaskFactory<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String content;

		public TaggedOutputTaskFactory() {
		}

		public TaggedOutputTaskFactory(String content) {
			this.content = content;
		}

		@Override
		@SuppressWarnings("deprecation")
		public NavigableSet<String> getCapabilities() {
			return ImmutableUtils.singletonNavigableSet(TaskFactory.CAPABILITY_CACHEABLE);
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.setTaskOutput(TaggedOutputTaskFactory.class, content + content);
			return content;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(content);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			content = in.readUTF();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((content == null) ? 0 : content.hashCode());
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
			TaggedOutputTaskFactory other = (TaggedOutputTaskFactory) obj;
			if (content == null) {
				if (other.content != null)
					return false;
			} else if (!content.equals(other.content))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "TaggedOutputTaskFactory[" + (content != null ? "content=" + content : "") + "]";
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		TaggedOutputTaskFactory main = new TaggedOutputTaskFactory("content");
		TaggedOutputTaskFactory modmain = new TaggedOutputTaskFactory("mod");

		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), main.content).noRemaining();
		assertMap(getMetric().getTaskIdTaggedResults().get(strTaskId("main")))
				.contains(TaggedOutputTaskFactory.class, main.content + main.content).noRemaining();
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("main"));

		cleanProject();
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), main.content).noRemaining();
		assertMap(getMetric().getTaskIdTaggedResults().get(strTaskId("main")))
				.contains(TaggedOutputTaskFactory.class, main.content + main.content).noRemaining();
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("main"));
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf());

		cleanProject();
		runTask("main", modmain);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), modmain.content).noRemaining();
		assertMap(getMetric().getTaskIdTaggedResults().get(strTaskId("main")))
				.contains(TaggedOutputTaskFactory.class, modmain.content + modmain.content).noRemaining();
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("main"));

		cleanProject();
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), main.content).noRemaining();
		assertMap(getMetric().getTaskIdTaggedResults().get(strTaskId("main")))
				.contains(TaggedOutputTaskFactory.class, main.content + main.content).noRemaining();
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("main"));
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf());
	}

}
