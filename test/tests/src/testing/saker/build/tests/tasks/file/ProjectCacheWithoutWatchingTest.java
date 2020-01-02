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
public class ProjectCacheWithoutWatchingTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		//use hash configuration so the different attributes are not discovered when populating via path directory
		parameters.setDatabaseConfiguration(
				DatabaseConfiguration.builder().add(SakerPathFiles.getRootFileProviderKey(files),
						WildcardPath.valueOf("**"), CommonContentDescriptorSupplier.HASH_MD5).build());

		SakerPath filepath = PATH_WORKING_DIRECTORY.resolve("file.txt");
		FileStringContentTaskFactory main = new FileStringContentTaskFactory(filepath);

		files.putFile(filepath, "content");
		runTask("main", main);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "content");

		project.waitExecutionFinalization();

		files.putFile(filepath, "modified");
		runTask("main", main);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "modified");
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations()).setUseProject(true)
				.setProjectFileWatchingEnabled(false).build();
	}

}
