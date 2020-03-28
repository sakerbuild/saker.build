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
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class SyntaxErrorsScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		runScriptTask("normal");
		BuildTaskExecutionResult res;

		SakerPath mainbuildfile = PATH_WORKING_DIRECTORY.resolve(DEFAULT_BUILD_FILE_NAME);

		res = environment.run(mainbuildfile, "missingparen", parameters, project);
		ScriptTestUtils.assertHasScriptTrace(res.getPositionedExceptionView(), mainbuildfile, 2, 1, 2);

		res = environment.run(mainbuildfile, "missingsubsc", parameters, project);
		ScriptTestUtils.assertHasScriptTrace(res.getPositionedExceptionView(), mainbuildfile, 6, 6, 0);

		res = environment.run(mainbuildfile, "missingif", parameters, project);
		ScriptTestUtils.assertHasScriptTrace(res.getPositionedExceptionView(), mainbuildfile, 9, 4, 1);

		runScriptTask("build", PATH_WORKING_DIRECTORY.resolve("semicolons.build"));

		assertTaskException("saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException",
				() -> runScriptTask("build", PATH_WORKING_DIRECTORY.resolve("missingternary.build")));
	}

}
