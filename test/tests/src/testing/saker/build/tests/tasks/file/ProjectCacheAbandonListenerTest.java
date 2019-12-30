package testing.saker.build.tests.tasks.file;

import java.util.Set;

import saker.build.file.content.CommonContentDescriptorSupplier;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.params.DatabaseConfiguration;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;

@SakerTest
public class ProjectCacheAbandonListenerTest extends CollectingMetricEnvironmentTestCase {
	//bug test

	private static final SakerPath DIR_PATH = PATH_WORKING_DIRECTORY.resolve("dir");
	private static final SakerPath FILE_PATH = DIR_PATH.resolve("file.txt");

	@Override
	protected void runTestImpl() throws Throwable {
		//use hash configuration so the different attributes are not discovered when populating via path directory
		parameters.setDatabaseConfiguration(
				DatabaseConfiguration.builder().add(SakerPathFiles.getRootFileProviderKey(files),
						WildcardPath.valueOf("**"), CommonContentDescriptorSupplier.HASH_MD5).build());
		FileStringContentTaskFactory maintask = new FileStringContentTaskFactory(FILE_PATH);

		files.putFile(FILE_PATH, "content");

		runTask("main", maintask);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "content");

		project.waitExecutionFinalization();
		files.triggerListenerAbandon(DIR_PATH);
		files.triggerListenerAbandon(PATH_WORKING_DIRECTORY);
		files.triggerListenerAbandon(FILE_PATH);

		//get the handle and get the content to force manifestation of the file attributes
		project.getExecutionContentDatabase().getContentHandle(SakerPathFiles.getPathKey(files, FILE_PATH))
				.getContent();
		//modify the file
		files.putFile(FILE_PATH, "modcontent");

		//run the task and expect the results to be modified as well
		runTask("main", maintask);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "modcontent");
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		//always use project cache
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations()).setUseProject(true).build();
	}

}
