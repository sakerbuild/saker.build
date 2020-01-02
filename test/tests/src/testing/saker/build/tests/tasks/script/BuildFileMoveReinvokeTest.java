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

import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.TargetConfiguration;
import saker.build.task.BuildTargetTaskFactory;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class BuildFileMoveReinvokeTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath buildfilepath1 = PATH_WORKING_DIRECTORY.resolve("saker.build");
		TargetConfiguration tc1 = parseTestTargetConfiguration(buildfilepath1);
		BuildTargetTaskFactory target1 = tc1.getTask("build");

		runTask("main", target1);
		assertNotEmpty(getMetric().getRunTaskIdResults());

		SakerPath buildfilepath2 = PATH_WORKING_DIRECTORY.resolve("moved.build");
		files.putFile(buildfilepath2, files.getAllBytes(buildfilepath1));
		files.deleteRecursively(buildfilepath1);

		TargetConfiguration tc2 = parseTestTargetConfiguration(buildfilepath2);
		BuildTargetTaskFactory target2 = tc2.getTask("build");
		runTask("main", target2);
		assertNotEmpty(getMetric().getRunTaskIdResults());
	}
}
