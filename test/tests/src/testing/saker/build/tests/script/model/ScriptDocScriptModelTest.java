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
package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class ScriptDocScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "build(")), setOf("doc_build"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "in inp")), setOf("doc_in"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "out outp")), setOf("doc_out"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$input")), setOf("doc_in"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "output")), setOf("doc_out"));

		assertTrue(getInformationsAtOffset(model, endIndexOf(filedata, "include(bui")).contains("doc_build"));
		assertTrue(getInformationsAtOffset(model, endIndexOf(filedata, "include(build, inp")).contains("doc_in"));
		assertTrue(getInformationsAtOffset(model, endIndexOf(filedata, "include(build, input: as")).contains("doc_in"));
		assertTrue(getInformationsAtOffset(model, endIndexOf(filedata, "include(build, input: asd)[out"))
				.contains("doc_out"));
		assertTrue(getInformationsAtOffset(model, indexOf(filedata, "ut = include(build, input: asd)[output]"))
				.contains("doc_out"));
	}

}
