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
package testing.saker.build.tests.tasks;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import saker.build.file.content.CommonContentDescriptorSupplier;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.params.DatabaseConfiguration;
import saker.build.runtime.params.ExecutionPathConfiguration;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.MemoryFileProvider;
import testing.saker.build.tests.tasks.factories.StringFileOutputTaskFactory;

@SakerTest
public class FileProviderModifyTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		//use hashes so file modification does not distrup this test
		parameters.setDatabaseConfiguration(
				DatabaseConfiguration.builder().add(SakerPathFiles.getRootFileProviderKey(files),
						WildcardPath.valueOf("**"), CommonContentDescriptorSupplier.HASH_MD5).build());

		SakerPath outfilepath = PATH_BUILD_DIRECTORY.resolve("out.txt");
		StringFileOutputTaskFactory task = new StringFileOutputTaskFactory(outfilepath, "filecontent");
		runTask("main", task);
		assertNotEmpty(getMetric().getRunTaskIdResults());
		assertEquals(files.getAllBytes(outfilepath).toString(), "filecontent");

		MemoryFileProvider nfiles = new MemoryFileProvider(files.getRoots(),
				UUID.nameUUIDFromBytes("modifiedprovider".getBytes(StandardCharsets.UTF_8)));
		//copy all files, including the database and the output file
		for (String fname : files.getDirectoryEntryNamesRecursive(PATH_BUILD_DIRECTORY)) {
			SakerPath fpath = PATH_BUILD_DIRECTORY.resolve(fname);
			nfiles.putFile(fpath, files.getAllBytes(fpath));
		}
		files = nfiles;
		parameters.setPathConfiguration(ExecutionPathConfiguration.forProvider(PATH_WORKING_DIRECTORY, nfiles));
		//reset the database configuration for the new file provider
		parameters.setDatabaseConfiguration(
				DatabaseConfiguration.builder().add(SakerPathFiles.getRootFileProviderKey(nfiles),
						WildcardPath.valueOf("**"), CommonContentDescriptorSupplier.HASH_MD5).build());

		runTask("main", task);
		assertEmpty(getMetric().getRunTaskIdResults());
		assertEquals(files.getAllBytes(outfilepath).toString(), "filecontent");
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		//no project caching
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations()).setUseProject(false).build();
	}

}
