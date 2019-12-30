package testing.saker.build.tests.tasks;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.util.Collections;
import java.util.UUID;

import saker.build.file.content.CommonContentDescriptorSupplier;
import saker.build.file.path.SakerPath;
import saker.build.runtime.params.DatabaseConfiguration;
import saker.build.runtime.params.ExecutionPathConfiguration;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.MemoryFileProvider;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;

@SakerTest
public class MoveWorkingDirectoryTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		//set hash based content checking
		parameters.setDatabaseConfiguration(
				DatabaseConfiguration.builder(CommonContentDescriptorSupplier.HASH_MD5).build());

		testWithRelativeDependency();

		testWithAbsoluteDependency();
	}

	private void testWithRelativeDependency()
			throws IOException, Throwable, AssertionError, DirectoryNotEmptyException {
		MemoryFileProvider myfp = new MemoryFileProvider(Collections.singleton("secondwd:"),
				UUID.nameUUIDFromBytes((getClass().getName() + "_first").getBytes()));

		files.putFile(PATH_WORKING_DIRECTORY.resolve("a.txt"), "a1");

		FileStringContentTaskFactory first = new FileStringContentTaskFactory(SakerPath.valueOf("a.txt"));
		runTask("first", first);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("first")), "a1");

		runTask("first", first);
		assertEmpty(getMetric().getRunTaskIdFactories());

		ExecutionPathConfiguration originalpathconfig = parameters.getPathConfiguration();
		ExecutionPathConfiguration npathconfig = ExecutionPathConfiguration
				.builder(originalpathconfig, SakerPath.valueOf("secondwd:")).addAllRoots(myfp).build();
		parameters.setPathConfiguration(npathconfig);
		files.delete(PATH_WORKING_DIRECTORY.resolve("a.txt"));
		myfp.putFile(SakerPath.valueOf("secondwd:/a.txt"), "a1");

		runTask("first", first);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("first")), "a1");

		myfp.putFile(SakerPath.valueOf("secondwd:/a.txt"), "a2");
		runTask("first", first);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("first")), "a2");

		parameters.setPathConfiguration(originalpathconfig);
	}

	private void testWithAbsoluteDependency() throws Throwable {
		parameters.setDatabaseConfiguration(
				DatabaseConfiguration.builder(CommonContentDescriptorSupplier.HASH_MD5).build());

		MemoryFileProvider myfp = new MemoryFileProvider(Collections.singleton("abswd:"),
				UUID.nameUUIDFromBytes((getClass().getName() + "_abs").getBytes()));

		SakerPath wdabstxtpath = PATH_WORKING_DIRECTORY.resolve("abs.txt");
		files.putFile(wdabstxtpath, "e1");

		FileStringContentTaskFactory abs = new FileStringContentTaskFactory(wdabstxtpath);
		runTask("abs", abs);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("abs")), "e1");

		runTask("abs", abs);
		assertEmpty(getMetric().getRunTaskIdFactories());

		ExecutionPathConfiguration originalpathconfig = parameters.getPathConfiguration();
		ExecutionPathConfiguration npathconfig = ExecutionPathConfiguration
				.builder(originalpathconfig, SakerPath.valueOf("abswd:")).addAllRoots(myfp).build();
		parameters.setPathConfiguration(npathconfig);

		runTask("abs", abs);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("abs")), "e1");

		files.putFile(wdabstxtpath, "e2");
		runTask("abs", abs);
		assertTrue(getMetric().getRunTaskIdFactories().containsKey(strTaskId("abs")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("abs")), "e2");

		parameters.setPathConfiguration(originalpathconfig);
	}
}
