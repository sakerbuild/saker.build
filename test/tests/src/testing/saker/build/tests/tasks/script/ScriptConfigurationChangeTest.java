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

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import saker.build.file.path.WildcardPath;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptOptionsConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class ScriptConfigurationChangeTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		ExecutionScriptConfiguration.Builder builder = ExecutionScriptConfiguration.builder();
		builder.addConfig(WildcardPath.valueOf("**/*.build"),
				new ScriptOptionsConfig(Collections.emptyMap(), ScriptProviderLocation.getBuiltin()));
		parameters.setScriptConfiguration(builder.build());
		runScriptTask("build");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		builder = ExecutionScriptConfiguration.builder();
		Map<String, String> options = new TreeMap<>();
		options.put("o1", "v1");
		builder.addConfig(WildcardPath.valueOf("**/*.build"),
				new ScriptOptionsConfig(options, ScriptProviderLocation.getBuiltin()));
		parameters.setScriptConfiguration(builder.build());
		runScriptTask("build");
		//some tasks should be rerun, as the options were changed for the script file
		assertNotEmpty(getMetric().getRunTaskIdResults());

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
	}
}
