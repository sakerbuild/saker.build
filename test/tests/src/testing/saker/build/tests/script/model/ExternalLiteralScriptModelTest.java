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
public class ExternalLiteralScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveScriptAnalysis(model, filedata);

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "EXT_SIMPLE_LITERAL")), setOf());
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "EXT_OBJECT_LITERAL")), setOf());

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "ExtLiteralParam1: EXT_SIMPLE_LITERAL")),
				setOf("doc_EXT_SIMPLE_LITERAL", "doc_example.task_ExtLiteralParam1_type",
						"doc_example.task_ExtLiteralParam1"));
		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "ExtLiteralParam1: EXT_OBJECT_LITERAL")),
				setOf("doc_EXT_OBJECT_LITERAL", "doc_example.task_ExtLiteralParam1_type",
						"doc_example.task_ExtLiteralParam1"));

		assertEquals(getInformationsAtOffset(model, endIndexOf(filedata, "$usedvar = EXT_SIMPLE_LITERAL")),
				setOf("doc_EXT_SIMPLE_LITERAL", "doc_example.task_ExtLiteralParam1_type",
						"doc_example.task_ExtLiteralParam1"));
	}

}
