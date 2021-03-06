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

import saker.build.file.path.SakerPath;
import saker.build.file.provider.DirectoryMountFileProvider;
import saker.build.runtime.params.AmbiguousPathConfigurationException;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionPathConfiguration.Builder;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class AmbiguousPathConfigurationTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		{
			Builder builder = ExecutionPathConfiguration.builder(PATH_WORKING_DIRECTORY);
			builder.addAllRoots(files);
			//wd:/sub/dir is now available through wd:/sub/dir, sd:
			builder.addAllRoots(
					DirectoryMountFileProvider.create(files, PATH_WORKING_DIRECTORY.resolve("sub/dir"), "sd:"));
			assertException(AmbiguousPathConfigurationException.class, builder::build).printStackTrace();
		}

		{
			Builder builder = ExecutionPathConfiguration.builder(PATH_WORKING_DIRECTORY);
			builder.addAllRoots(files);
			//wd:/sub is now available through wd:/sub/dir, s:/dir
			builder.addAllRoots(DirectoryMountFileProvider.create(files, PATH_WORKING_DIRECTORY.resolve("sub"), "s:"));
			assertException(AmbiguousPathConfigurationException.class, builder::build).printStackTrace();
		}

		{
			Builder builder = ExecutionPathConfiguration.builder(SakerPath.valueOf("a:"));
			//wd:/sub is now available through a:, b:
			builder.addAllRoots(DirectoryMountFileProvider.create(files, PATH_WORKING_DIRECTORY.resolve("sub"), "a:"));
			builder.addAllRoots(DirectoryMountFileProvider.create(files, PATH_WORKING_DIRECTORY.resolve("sub"), "b:"));
			assertException(AmbiguousPathConfigurationException.class, builder::build).printStackTrace();
		}
		{
			Builder builder = ExecutionPathConfiguration.builder(SakerPath.valueOf("a:"));
			//wd:/sub is now available through b: and a:/dir
			builder.addAllRoots(DirectoryMountFileProvider.create(files, PATH_WORKING_DIRECTORY.resolve("sub"), "a:"));
			builder.addAllRoots(
					DirectoryMountFileProvider.create(files, PATH_WORKING_DIRECTORY.resolve("sub/dir"), "b:"));
			assertException(AmbiguousPathConfigurationException.class, builder::build).printStackTrace();
		}
	}
}
