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
package testing.saker.build.tests.tasks.script;

import java.util.Map;
import java.util.TreeMap;

import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.VariablesMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringFileOutputTaskFactory;

@SakerTest
public class BuildFileInTaskModificationTest extends VariablesMetricEnvironmentTestCase {
	@Override
	protected CollectingTestMetric createMetricImpl() {
		CollectingTestMetric result = super.createMetricImpl();
		Map<TaskName, TaskFactory<?>> injectedTaskFactories = new TreeMap<>();
		injectedTaskFactories.put(TaskName.valueOf("test.Replace"),
				new StringFileOutputTaskFactory(PATH_WORKING_DIRECTORY.resolve("second.build"), "fail { }"));
		result.setInjectedTaskFactories(injectedTaskFactories);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		runScriptTask("build");
	}

}
