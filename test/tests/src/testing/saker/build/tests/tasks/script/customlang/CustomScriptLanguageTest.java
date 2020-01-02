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
package testing.saker.build.tests.tasks.script.customlang;

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
import testing.saker.build.flag.TestFlag;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;
import testing.saker.build.tests.tasks.repo.RepositoryTestUtils;

@SakerTest
public class CustomScriptLanguageTest extends CollectingMetricEnvironmentTestCase {
	//TODO somewhy the class loader for the languge gets stuck in the class path load manager.

	private CollectingTestMetric baseMetric;

	@Override
	public void executeRunning() throws Exception {
		super.executeRunning();
		assertEmpty(getMetric().getLoadedClassPaths());
	}

	@Override
	protected void runTestImpl() throws Throwable {
		baseMetric = new CollectingTestMetric();
		TestFlag.set(baseMetric);

		SakerPath jarpath = PATH_WORKING_DIRECTORY.resolve(createScriptLangJarName(getClass()));
		RepositoryTestUtils.exportJarWithClasses(files, jarpath, CustomBuildTargetTaskFactory.class,
				CustomScriptAccessProvider.class, CustomTargetConfiguration.class,
				CustomTargetConfigurationReader.class, StringTaskFactory.class);

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
		assertMap(getMetric().getRunTaskIdResults())
				.contains(TaskIdentifier.builder(String.class.getName()).field("val", "builtin.task").build(), "hello");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		//modified the custom language
		// the task will return "append"
		// the access provider key is modified too
		RepositoryTestUtils.exportJarWithClasses(files, jarpath, CustomBuildTargetTaskFactory.class,
				CustomScriptAccessProvider.class, CustomTargetConfiguration.class,
				CustomTargetConfigurationReader.class, StringTaskFactory.class, AppendCustomLangClass.class);

		if (project != null) {
			project.waitExecutionFinalization();
			project.reset();
		}
		environment.clearCachedDatasWaitExecutions();
		// accesskor key .toString() changed, so the classes won't deserialize properly, therefore forcing invocation of the task
		runScriptTask("build");
		assertMap(getMetric().getRunTaskIdResults()).contains(
				TaskIdentifier.builder(String.class.getName()).field("val", "builtin.task").build(), "append");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		if (project != null) {
			project.waitExecutionFinalization();
			project.reset();
		}
		environment.clearCachedDatasWaitExecutions();
		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		//add the custom key, the toString() of the accessor key doesn't change, but the equality of it changes.
		//only the include task should rerun, but not any of the script tasks
		RepositoryTestUtils.exportJarWithClasses(files, jarpath, CustomBuildTargetTaskFactory.class,
				CustomScriptAccessProvider.class, CustomTargetConfiguration.class,
				CustomTargetConfigurationReader.class, StringTaskFactory.class, AppendCustomLangClass.class,
				CustomLangVersionKey.class);
		if (project != null) {
			project.waitExecutionFinalization();
			project.reset();
		}
		environment.clearCachedDatasWaitExecutions();
		runScriptTask("build");
		assertNotEmpty(getMetric().getRunTaskIdResults());
		//assert that the actual task is not rerun
		//we could assert that only the include task is rerun, but that has an internal task identifier
		assertFalse(getMetric().getRunTaskIdResults()
				.containsKey(TaskIdentifier.builder(String.class.getName()).field("val", "builtin.task").build()));
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		// use a private environment
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setEnvironmentStorageDirectory(null).build();
	}

	public static String createScriptLangJarName(Class<?> testclass) {
		return "scriptlang." + testclass.getName() + ".jar";
	}
}
