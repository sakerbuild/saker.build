package saker.build.daemon;

import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.BuildTaskExecutionResult;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.runtime.project.ProjectCacheHandle;

public interface BuildExecutionInvoker {
	public BuildTaskExecutionResult run(SakerPath buildfilepath, String targetname, ExecutionParametersImpl parameters,
			ProjectCacheHandle projecthandle);
}
