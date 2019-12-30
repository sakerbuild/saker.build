package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.WildcardFileCollectionStrategy;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.StringTaskIdentifier;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;

@SakerTest
public class FileAdditionSubTaskDependencyTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class FileListerTaskFactory implements TaskFactory<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String wildcard;

		public FileListerTaskFactory() {
			this.wildcard = "**/*.txt";
		}

		public FileListerTaskFactory(String wildcard) {
			this.wildcard = wildcard;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public Task<String> createTask(ExecutionContext executioncontext) {
			return new Task<String>() {
				@Override
				public String run(TaskContext taskcontext) {
					WildcardPath wcpath = WildcardPath.valueOf(wildcard);
					FileCollectionStrategy adddep = WildcardFileCollectionStrategy.create(wcpath);
					Map<SakerPath, SakerFile> files = taskcontext.getTaskUtilities()
							.collectFilesReportInputFileAndAdditionDependency(null, adddep);
					String result = "";
					for (SakerPath path : files.keySet()) {
						taskcontext.getTaskUtilities().startTaskFuture(new StringTaskIdentifier(path.toString()),
								new FileStringContentTaskFactory(path));
					}
					for (SakerPath path : files.keySet()) {
						result += taskcontext.getTaskResult(new StringTaskIdentifier(path.toString()));
					}
					return result;
				}
			};
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((wildcard == null) ? 0 : wildcard.hashCode());
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
			FileListerTaskFactory other = (FileListerTaskFactory) obj;
			if (wildcard == null) {
				if (other.wildcard != null)
					return false;
			} else if (!wildcard.equals(other.wildcard))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "FileListerTaskFactory[" + (wildcard != null ? "wildcard=" + wildcard : "") + "]";
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		TaskIdentifier maintaskid = strTaskId("main");

		FileListerTaskFactory task = new FileListerTaskFactory("**/*.txt");

		files.putFile(PATH_WORKING_DIRECTORY.resolve("input.txt"), "1");

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(maintaskid, "1");

		runTask("main", task);
		assertTrue(getMetric().getRunTaskIdResults().isEmpty());

		files.putFile(PATH_WORKING_DIRECTORY.resolve("input2.txt"), "2");
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(maintaskid, "12");

		files.putFile(PATH_WORKING_DIRECTORY.resolve("input2.txt"), "3");
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(maintaskid, "13");

		files.deleteRecursively(PATH_WORKING_DIRECTORY.resolve("input2.txt"));
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(maintaskid, "1");
	}

}
