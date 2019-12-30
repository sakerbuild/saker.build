package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.util.Arrays;
import java.util.NavigableMap;
import java.util.Set;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.task.TaskContext;
import saker.build.task.TaskResultCollection;
import saker.build.task.utils.dependencies.PathFileCollectionStrategy;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class MultiFileAdditionDependencyTaskTest extends CollectingMetricEnvironmentTestCase {
	public static final SakerPath PATH_FIRST = PATH_WORKING_DIRECTORY.resolve("first.txt");
	public static final SakerPath PATH_SECOND = PATH_WORKING_DIRECTORY.resolve("second.txt");

	public static class MultiAdditionTaskFactory extends SelfStatelessTaskFactory<Set<SakerPath>> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public MultiAdditionTaskFactory() {
		}

		@Override
		public Set<SakerPath> run(TaskContext taskcontext) throws Exception {
			NavigableMap<SakerPath, SakerFile> files = taskcontext.getTaskUtilities()
					.collectFilesReportInputFileAndAdditionDependency(null,
							Arrays.asList(PathFileCollectionStrategy.create(PATH_FIRST),
									PathFileCollectionStrategy.create(PATH_SECOND)));
			return ImmutableUtils.makeImmutableNavigableSet(files.navigableKeySet());
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		MultiAdditionTaskFactory main = new MultiAdditionTaskFactory();
		TaskResultCollection res;
		res = runTask("main", main);
		assertEquals(res.getTaskResult(strTaskId("main")), setOf());

		res = runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(res.getTaskResult(strTaskId("main")), setOf());

		files.putFile(PATH_FIRST, "");
		res = runTask("main", main);
		assertEquals(res.getTaskResult(strTaskId("main")), setOf(PATH_FIRST));

		res = runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(res.getTaskResult(strTaskId("main")), setOf(PATH_FIRST));

		files.putFile(PATH_SECOND, "");
		res = runTask("main", main);
		assertEquals(res.getTaskResult(strTaskId("main")), setOf(PATH_FIRST, PATH_SECOND));

		res = runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.delete(PATH_FIRST);
		res = runTask("main", main);
		assertEquals(res.getTaskResult(strTaskId("main")), setOf(PATH_SECOND));
		
		res = runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
