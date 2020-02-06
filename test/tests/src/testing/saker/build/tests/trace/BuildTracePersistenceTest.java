package testing.saker.build.tests.trace;

import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class BuildTracePersistenceTest extends EnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath buildtracepath = PATH_WORKING_DIRECTORY.resolve("build.trace");
		parameters.setBuildTraceOutputPathKey(new SimpleProviderHolderPathKey(files, buildtracepath));

		runTask("main", new StringTaskFactory("abc"));
		if (project != null) {
			project.waitExecutionFinalization();
		}
		//ensure existence
		files.getFileAttributes(buildtracepath);
	}

}
