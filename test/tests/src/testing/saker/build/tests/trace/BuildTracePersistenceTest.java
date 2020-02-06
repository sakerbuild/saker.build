package testing.saker.build.tests.trace;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class BuildTracePersistenceTest extends EnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath buildtracepath = SakerPath.valueOf(getBuildDirectory().resolve("build.trace"));
		//delete so leftover don't distrupt
		LocalFileProvider.getInstance().delete(buildtracepath);
		parameters.setBuildTraceOutputLocalPath(buildtracepath);

		runTask("main", new StringTaskFactory("abc"));
		if (project != null) {
			project.waitExecutionFinalization();
		}
		//ensure existence
		LocalFileProvider.getInstance().getFileAttributes(buildtracepath);
	}

}
