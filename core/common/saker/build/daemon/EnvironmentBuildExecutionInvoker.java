/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.daemon;

import java.util.NavigableMap;

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
	public BuildTaskExecutionResult runBuildTarget(SakerPath buildfilepath, String targetname,
			ExecutionParametersImpl parameters, ProjectCacheHandle projecthandle) {
		return environment.runBuildTarget(buildfilepath, targetname, parameters, projecthandle);
	}

	@Override
	public BuildTaskExecutionResult runBuildTargetWithLiteralParameters(SakerPath buildfilepath, String targetname,
			ExecutionParametersImpl parameters, ProjectCacheHandle projecthandle,
			NavigableMap<String, ?> buildtargetparameters) {
		return environment.runBuildTargetWithLiteralParameters(buildfilepath, targetname, parameters, projecthandle,
				buildtargetparameters);
	}

}