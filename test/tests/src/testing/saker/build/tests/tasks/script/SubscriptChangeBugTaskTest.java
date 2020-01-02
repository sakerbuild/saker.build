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
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class SubscriptChangeBugTaskTest extends CollectingMetricEnvironmentTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath bfpath = PATH_WORKING_DIRECTORY.resolve("saker.build");

		System.out.println("run 1");
		runScriptTask("test3");
		assertEquals(getMetric().getAllPrintedTaskLines(), setOf("{field=CONTENT}"));

		System.out.println("run 2");
		files.putFile(bfpath, files.getAllBytes(bfpath).toString().replace("#add", "[nonexistent]#add"));
		//should fail
		assertException(Exception.class, () -> runScriptTask("test3"));
		assertEmpty(getMetric().getAllPrintedTaskLines());

		System.out.println("run 3");
		//should fail again
		assertException(Exception.class, () -> runScriptTask("test3"));
		assertEmpty(getMetric().getAllPrintedTaskLines());
	}
}
