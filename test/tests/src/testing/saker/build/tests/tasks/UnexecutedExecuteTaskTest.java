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
package testing.saker.build.tests.tasks;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class UnexecutedExecuteTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		StringTaskFactory hello = new StringTaskFactory("hello");
		StringTaskFactory world = new StringTaskFactory("world");

		runTask("hello", hello);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("hello"));
		runTask("world", world);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("world"));

		runTask("hello", hello);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());
		runTask("world", world);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());
	}

}
