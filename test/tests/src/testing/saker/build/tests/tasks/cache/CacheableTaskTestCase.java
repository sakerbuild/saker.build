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
