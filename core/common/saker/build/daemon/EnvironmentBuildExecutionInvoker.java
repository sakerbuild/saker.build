package saker.build.daemon;

import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.BuildTaskExecutionResult;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.runtime.project.ProjectCacheHandle;

public class EnvironmentBuildExecutionInvoker implements BuildExecutionInvoker {
	private final SakerEnvironmentImpl environment;

	public EnvironmentBuildExecutionInvoker(SakerEnvironmentImpl environment) {
		this.environment = environment;
	}

	@Override
	public BuildTaskExecutionResult run(SakerPath buildfilepath, String targetname, ExecutionParametersImpl parameters,
			ProjectCacheHandle projecthandle) {
		return environment.run(buildfilepath, targetname, parameters, projecthandle);
	}

}