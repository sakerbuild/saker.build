package testing.saker.build.tests.tasks.cache;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;

import saker.build.ide.configuration.SimpleIDEConfiguration;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskResultCollection;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;

@SakerTest
public class IdeConfigurationsCacheableTaskTest extends CacheableTaskTestCase {

	private static class IdeConfigurationReportingTaskFactory
			implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String content;

		public IdeConfigurationReportingTaskFactory() {
		}

		public IdeConfigurationReportingTaskFactory(String content) {
			this.content = content;
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return ImmutableUtils.singletonNavigableSet(TaskFactory.CAPABILITY_CACHEABLE);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.reportIDEConfiguration(new SimpleIDEConfiguration("idetype", "identifier",
					ImmutableUtils.singletonNavigableMap("field", content)));
			return content;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
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
			IdeConfigurationReportingTaskFactory other = (IdeConfigurationReportingTaskFactory) obj;
			if (content == null) {
				if (other.content != null)
					return false;
			} else if (!content.equals(other.content))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		IdeConfigurationReportingTaskFactory main = new IdeConfigurationReportingTaskFactory("content");
		TaskResultCollection res;

		res = runTask("main", main);
		assertTrue(res.getIDEConfigurations().contains(new SimpleIDEConfiguration("idetype", "identifier",
				ImmutableUtils.singletonNavigableMap("field", main.content))));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), main.content).noRemaining();
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("main"));

		cleanProject();
		res = runTask("main", main);
		assertTrue(res.getIDEConfigurations().contains(new SimpleIDEConfiguration("idetype", "identifier",
				ImmutableUtils.singletonNavigableMap("field", main.content))));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), main.content).noRemaining();
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("main"));
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf());
	}

}
