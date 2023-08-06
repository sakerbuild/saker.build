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

import java.util.NavigableMap;
import java.util.TreeMap;

import saker.build.task.exception.TaskParameterException;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class LiteralBuildTargetParameterTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		NavigableMap<String, Object> btparams = new TreeMap<>();
		btparams.put("mandatoryParam", "mandVal");
		CombinedTargetTaskResult taskresults = runScriptTask("build", DEFAULT_BUILD_FILE_PATH, btparams);
		assertEquals(taskresults.getTargetTaskResult("outConcat"), "mandVal+defval");

		taskresults = runScriptTask("build", DEFAULT_BUILD_FILE_PATH, btparams);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), setOf());
		assertEquals(taskresults.getTargetTaskResult("outConcat"), "mandVal+defval");

		//get an exception in case the required param is missing
		assertTaskException(TaskParameterException.class, () -> runScriptTask("build"));

		btparams.put("mandatoryParam", "changedVal");
		taskresults = runScriptTask("build", DEFAULT_BUILD_FILE_PATH, btparams);
		assertEquals(taskresults.getTargetTaskResult("outConcat"), "changedVal+defval");

		btparams.put("mandatoryParam", "thirdVal");
		btparams.put("defaultedParam", "modifiedDefault");
		taskresults = runScriptTask("build", DEFAULT_BUILD_FILE_PATH, btparams);
		assertEquals(taskresults.getTargetTaskResult("outConcat"), "thirdVal+modifiedDefault");
	}

}
