package testing.saker.build.tests.tasks;

import saker.build.file.path.SakerPath;
import saker.build.runtime.params.ExecutionPathConfiguration;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;

@SakerTest
public class RelativeFileDependencyTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath filepath = PATH_WORKING_DIRECTORY.resolve("file.txt");
		FileStringContentTaskFactory task = new FileStringContentTaskFactory(SakerPath.valueOf(filepath.getFileName()));

		files.putFile(filepath, "content");
		runTask("task", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("task"), "content").noRemaining();

		runTask("task", task);
		assertMap(getMetric().getRunTaskIdResults()).noRemaining();

		files.putFile(filepath, "modcontent");
		runTask("task", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("task"), "modcontent").noRemaining();

		SakerPath nworkingdir = PATH_WORKING_DIRECTORY.resolve("changedworking");
		parameters
				.setPathConfiguration(ExecutionPathConfiguration.copy(parameters.getPathConfiguration(), nworkingdir));

		SakerPath nfilepath = nworkingdir.resolve(filepath.getFileName());
		files.putFile(nfilepath, "modcontent");
		//set the last modified millis, so the file attributes for the moved file will be the same
		files.setLastModifiedMillis(nfilepath, files.getFileAttributes(filepath).getLastModifiedMillis());
		files.deleteRecursively(filepath);
		runTask("task", task);
		//the task should run as the attribute content descriptors store the path and provider key too
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("task"), "modcontent").noRemaining();

		files.putFile(nfilepath, "modmodcontent");
		runTask("task", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("task"), "modmodcontent").noRemaining();
	}
}
