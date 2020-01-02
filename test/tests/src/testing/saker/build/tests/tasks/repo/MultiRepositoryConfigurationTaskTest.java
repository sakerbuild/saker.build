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
package testing.saker.build.tests.tasks.repo;

import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.ServiceLoaderClassPathServiceEnumerator;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.repository.SakerRepositoryFactory;
import testing.saker.SakerTest;
import testing.saker.build.flag.TestFlag;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;

@SakerTest
public class MultiRepositoryConfigurationTaskTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		CollectingTestMetric tm = new CollectingTestMetric();
		TestFlag.set(tm);

		SakerPath repopath = PATH_WORKING_DIRECTORY.resolve(RepositoryTestUtils.createRepositoryJarName(getClass()));
		RepositoryTestUtils.exportTestRepositoryJar(files, repopath);

		JarFileClassPathLocation repoloc = new JarFileClassPathLocation(
				new SimpleProviderHolderPathKey(files, repopath));

		parameters.setRepositoryConfiguration(ExecutionRepositoryConfiguration.builder()
				.add(repoloc, new ServiceLoaderClassPathServiceEnumerator<>(SakerRepositoryFactory.class), "repo1")
				.add(repoloc, new ServiceLoaderClassPathServiceEnumerator<>(SakerRepositoryFactory.class), "repo2")
				.build());

		TreeMap<String, String> nuserparams = new TreeMap<>(parameters.getUserParameters());
		nuserparams.put("repo1.userparamtask.name", "test.task1");
		nuserparams.put("repo2.userparamtask.name", "test.task2");
		nuserparams.put("repo1.userparamtask.value", "value.task1");
		nuserparams.put("repo2.userparamtask.value", "value.task2");
		parameters.setUserParameters(nuserparams);

		CombinedTargetTaskResult res;

		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("out1"), "value.task1");
		assertEquals(res.getTargetTaskResult("out2"), "value.task2");

		nuserparams = new TreeMap<>(parameters.getUserParameters());
		nuserparams.put("repo1.userparamtask.name", "test.task");
		nuserparams.put("repo2.userparamtask.name", "test.task");
		nuserparams.put("repo1.userparamtask.value", "value.task1");
		nuserparams.put("repo2.userparamtask.value", "value.task2");
		parameters.setUserParameters(nuserparams);

		res = runScriptTask("repoidentified");
		assertEquals(res.getTargetTaskResult("out1"), "value.task1");
		assertEquals(res.getTargetTaskResult("out2"), "value.task2");

		//so the metric is not GCd prematurely
		System.out.println("CustomScriptLanguageTest.runTestImpl() " + tm);
	}
}
