package testing.saker.build.tests.tasks;

import saker.build.file.content.CommonContentDescriptorSupplier;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.params.DatabaseConfiguration;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;

@SakerTest
public class HashDatabaseConfigurationTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath INPUT_FILE = PATH_WORKING_DIRECTORY.resolve("input.txt");

	@Override
	protected void runTestImpl() throws Throwable {
		parameters.setDatabaseConfiguration(DatabaseConfiguration.builder()
				.add(SakerPathFiles.getRootFileProviderKey(files),
						WildcardPath.valueOf(PATH_WORKING_DIRECTORY.resolve("**")),
						CommonContentDescriptorSupplier.HASH_MD5)
				.build());

		FileStringContentTaskFactory task = new FileStringContentTaskFactory(INPUT_FILE);

		files.putFile(INPUT_FILE, "content");
		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "content").noRemaining();

		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());

		files.touch(INPUT_FILE);
		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());

		files.putFile(INPUT_FILE, "modcontent");
		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "modcontent").noRemaining();

		files.touch(INPUT_FILE);
		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());

	}
}
