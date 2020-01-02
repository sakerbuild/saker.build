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

import java.util.Map;

import saker.build.task.identifier.TaskIdentifier;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class SimpleTaskIdTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertEquals(TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).build(),
				TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).build());
		assertEquals(TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).field("a", "a").field("b", "b").build(),
				TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).field("b", "b").field("a", "a").build());

		assertNotEquals(TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).build(),
				TaskIdentifier.builder(Runnable.class.getName()).build());
		assertNotEquals(TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).field("a", "a").field("b", "b").build(),
				TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).field("b", "x").field("a", "x").build());
		assertNotEquals(TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).field("a", "a").field("b", "b").build(),
				TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).field("b", "b").build());
	}

}
