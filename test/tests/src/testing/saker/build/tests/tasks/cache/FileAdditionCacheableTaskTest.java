package testing.saker.build.tests.tasks.cache;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableMap;
import java.util.NavigableSet;

import saker.build.file.SakerFile;
import saker.build.file.content.CommonContentDescriptorSupplier;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.params.DatabaseConfiguration;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.utils.dependencies.WildcardFileCollectionStrategy;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;

@SakerTest
public class FileAdditionCacheableTaskTest extends CacheableTaskTestCase {

	private static class FileAdditionReportingTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private FileCollectionStrategy dependency;

		public FileAdditionReportingTaskFactory() {
		}

		public FileAdditionReportingTaskFactory(FileCollectionStrategy dependency) {
			this.dependency = dependency;
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return ImmutableUtils.singletonNavigableSet(TaskFactory.CAPABILITY_CACHEABLE);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			NavigableMap<SakerPath, SakerFile> files = taskcontext.getTaskUtilities()
					.collectFilesReportInputFileAndAdditionDependency(null, dependency);
			StringBuilder sb = new StringBuilder();
			for (SakerFile f : files.values()) {
				sb.append(f.getContent());
			}
			return sb.toString();
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(dependency);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			dependency = (FileCollectionStrategy) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((dependency == null) ? 0 : dependency.hashCode());
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
			FileAdditionReportingTaskFactory other = (FileAdditionReportingTaskFactory) obj;
			if (dependency == null) {
				if (other.dependency != null)
					return false;
			} else if (!dependency.equals(other.dependency))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		//hash the files instead of using file attributes
		parameters.setDatabaseConfiguration(
				DatabaseConfiguration.builder(CommonContentDescriptorSupplier.HASH_MD5).build());

		FileAdditionReportingTaskFactory main = new FileAdditionReportingTaskFactory(
				WildcardFileCollectionStrategy.create(WildcardPath.valueOf("**/*")));

		files.putFile(PATH_WORKING_DIRECTORY.resolve("a"), "a");

		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "a").noRemaining();
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("main"));

		cleanProject();
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "a").noRemaining();
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("main"));
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf());

		files.putFile(PATH_WORKING_DIRECTORY.resolve("b"), "b");
		cleanProject();
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "ab").noRemaining();
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf());
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("main"));

		files.delete(PATH_WORKING_DIRECTORY.resolve("b"));
		cleanProject();
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "a").noRemaining();
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("main"));
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf());
	}

}
