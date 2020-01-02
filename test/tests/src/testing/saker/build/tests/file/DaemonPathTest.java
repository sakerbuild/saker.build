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
package testing.saker.build.tests.file;

import java.util.Map;

import saker.build.daemon.files.DaemonPath;
import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class DaemonPathTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		test("/", null, SakerPath.valueOf("/"));
		test("/home", null, SakerPath.valueOf("/home"));
		test("c:/", null, SakerPath.valueOf("c:/"));
		test("client:/c:", "client", SakerPath.valueOf("c:/"));
		test("client:/c:/", "client", SakerPath.valueOf("c:/"));
		test("client:/c:/sub", "client", SakerPath.valueOf("c:/sub"));
		test("client:/", null, SakerPath.valueOf("client:/"));
		test("client://", "client", SakerPath.valueOf("/"));
		test("client:////", "client", SakerPath.valueOf("/"));
		test("client://home", "client", SakerPath.valueOf("/home"));
		test("c:/home", null, SakerPath.valueOf("c:/home"));
	}

	private static void test(String daemonpath, String clientname, SakerPath path) {
		DaemonPath dp = DaemonPath.valueOf(daemonpath);
		assertEquals(dp.getClientName(), clientname);
		assertEquals(dp.getPath(), path);
	}

}
