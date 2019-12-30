package testing.saker.build.tests.tasks.cache;

import java.io.IOException;

import saker.build.runtime.execution.ExecutionParametersImpl;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.MemoryBuildCache;

public abstract class CacheableTaskTestCase extends CollectingMetricEnvironmentTestCase {

	protected void cleanProject() throws IOException {
		if (project != null) {
			project.clean();
		}
		files.clearDirectoryRecursively(PATH_BUILD_DIRECTORY);
	}

	protected void waitExecutionFinalization() throws InterruptedException {
		if (project != null) {
			project.waitExecutionFinalization();
		}
	}

	@Override
	protected void setupParameters(ExecutionParametersImpl params) {
		super.setupParameters(params);
		params.setPublishCachedTasks(true);
		params.setBuildCache(new MemoryBuildCache());
	}
}
