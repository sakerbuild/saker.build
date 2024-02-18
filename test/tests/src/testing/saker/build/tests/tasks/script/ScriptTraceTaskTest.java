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
import saker.build.runtime.environment.BuildTaskExecutionResult;
import saker.build.runtime.execution.SakerLog;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class ScriptTraceTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath mainbuildfile = PATH_WORKING_DIRECTORY.resolve(DEFAULT_BUILD_FILE_NAME);

		BuildTaskExecutionResult res;

		res = runTask(() -> environment.runBuildTarget(mainbuildfile, "notassigned", parameters, project));
		SakerLog.printFormatException(res.getPositionedExceptionView());
		System.err.println();
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile,
				"out notassigned = $var");

		res = runTask(() -> environment.runBuildTarget(mainbuildfile, "noresassign", parameters, project));
		SakerLog.printFormatException(res.getPositionedExceptionView());
		System.err.println();
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile, "print(out)");
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile, "$print(out)");
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile,
				"$print(out) = 123");

		res = runTask(() -> environment.runBuildTarget(mainbuildfile, "abortassign", parameters, project));
		SakerLog.printFormatException(res.getPositionedExceptionView());
		System.err.println();
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile, "abort(abrt)");
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile,
				"$abort(abrt) = 123");

		res = runTask(() -> environment.runBuildTarget(mainbuildfile, "simpleabort", parameters, project));
		SakerLog.printFormatException(res.getPositionedExceptionView());
		System.err.println();
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile, "abort(simple)");

		res = runTask(() -> environment.runBuildTarget(mainbuildfile, "notassignassign", parameters, project));
		SakerLog.printFormatException(res.getPositionedExceptionView());
		System.err.println();
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile,
				"$var = $nnassigned");
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile, "print($var)");

		res = runTask(() -> environment.runBuildTarget(mainbuildfile, "notassignable", parameters, project));
		SakerLog.printFormatException(res.getPositionedExceptionView());
		System.err.println();
		assertNotEmpty(getMetric().getRunTaskIdFactories());
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile, "print(x)");
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile, "print(x) = 123");

		//no task should be re-ran, as the left operand didn't become assignable
		res = runTask(() -> environment.runBuildTarget(mainbuildfile, "notassignable", parameters, project));
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertNonNull(res.getPositionedExceptionView());

		res = runTask(() -> environment.runBuildTarget(mainbuildfile, "conflictassign", parameters, project));
		SakerLog.printFormatException(res.getPositionedExceptionView());
		System.err.println();
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile,
				"$conflictvar = str1");
		//Note: this following assertion is commented out, as this trace is only present if the 
		//      str2 assignment fails, not if the str1 one
//		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile, "$conflictvar = str2");

		res = runTask(() -> environment.runBuildTarget(mainbuildfile, "includingabort", parameters, project));
		SakerLog.printFormatException(res.getPositionedExceptionView());
		System.err.println();
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile, "includingabort { aborter(abmsg: abortmsg) }");
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile, "aborter(abmsg: abortmsg)");
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile, "aborter(in abmsg) { abort($abmsg) }");
		ScriptTestUtils.assertHasScriptTrace(files, res.getPositionedExceptionView(), mainbuildfile, "abort($abmsg)");
	}

}
