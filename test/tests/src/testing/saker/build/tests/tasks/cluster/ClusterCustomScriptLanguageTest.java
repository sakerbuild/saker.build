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
package testing.saker.build.tests.tasks.cluster;

import java.util.Set;

import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.NamedClassPathServiceEnumerator;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptOptionsConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.task.identifier.TaskIdentifier;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;
import testing.saker.build.tests.tasks.repo.RepositoryTestUtils;
import testing.saker.build.tests.tasks.script.customlang.CustomBuildTargetTaskFactory;
import testing.saker.build.tests.tasks.script.customlang.CustomScriptAccessProvider;
import testing.saker.build.tests.tasks.script.customlang.CustomScriptLanguageTest;
import testing.saker.build.tests.tasks.script.customlang.CustomTargetConfiguration;
import testing.saker.build.tests.tasks.script.customlang.CustomTargetConfigurationReader;

@SakerTest
public class ClusterCustomScriptLanguageTest extends ClusterBuildTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath jarpath = PATH_WORKING_DIRECTORY
				.resolve(CustomScriptLanguageTest.createScriptLangJarName(getClass()));
		RepositoryTestUtils.exportJarWithClasses(files, jarpath, CustomBuildTargetTaskFactory.class,
				CustomScriptAccessProvider.class, CustomTargetConfiguration.class,
				CustomTargetConfigurationReader.class, StringTaskFactory.class,
				TestClusterNameExecutionEnvironmentSelector.class);

		JarFileClassPathLocation jarclasspathlocation = new JarFileClassPathLocation(
				new SimpleProviderHolderPathKey(files, jarpath));

		ExecutionScriptConfiguration.Builder builder = ExecutionScriptConfiguration.builder();
		builder.addConfig(WildcardPath.valueOf("**/*.customlang"),
				new ScriptOptionsConfig(null, new ScriptProviderLocation(jarclasspathlocation,
						new NamedClassPathServiceEnumerator<>(CustomScriptAccessProvider.class.getName()))));
		builder.addConfig(WildcardPath.valueOf("**/*.build"),
				new ScriptOptionsConfig(null, ScriptProviderLocation.getBuiltin()));
		ExecutionScriptConfiguration scriptconfig = builder.build();
		parameters.setScriptConfiguration(scriptconfig);

		runScriptTask("build");
		assertMap(getMetric().getRunTaskIdResults()).contains(
				TaskIdentifier.builder(String.class.getName()).field("val", "builtin.remote.task").build(),
				"hello_remote");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		// use a private environment
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setEnvironmentStorageDirectory(null).build();
	}

}
