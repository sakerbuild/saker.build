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

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class MapSubscriptScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {

		assertTaskException("saker.build.internal.scripting.language.exc.OperandExecutionException",
				() -> runScriptTask("build"));

		assertTaskException("saker.build.internal.scripting.language.exc.OperandExecutionException",
				() -> runScriptTask("conflict"));

		assertTaskException("saker.build.internal.scripting.language.exc.OperandExecutionException",
				() -> runScriptTask("directconflict", PATH_WORKING_DIRECTORY.resolve("parsedirectconflict.build")));

		assertTaskException("saker.build.internal.scripting.language.exc.OperandExecutionException",
				() -> runScriptTask("nokeyunused"));
		assertTaskException("saker.build.internal.scripting.language.exc.OperandExecutionException",
				() -> runScriptTask("nokeyout"));
	}

}
