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

import java.util.Collection;
import java.util.Iterator;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.scripting.model.info.BuildTargetInformation;
import saker.build.scripting.model.info.BuildTargetParameterInformation;
import testing.saker.SakerTest;

@SakerTest
public class BuildTargetInfoScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();

		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveScriptAnalysis(model, filedata);

		//returned in order of declaration
		Collection<? extends BuildTargetInformation> buildtargets = model.getBuildTargets();
		Iterator<? extends BuildTargetInformation> it = buildtargets.iterator();
		{
			BuildTargetInformation bt1 = it.next();
			assertEquals(bt1.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT), "target 1 doc");

			checkParameters(bt1);
		}
		{
			BuildTargetInformation bt2 = it.next();
			assertEquals(bt2.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT), "target 2 doc");

			checkParameters(bt2);
		}
	}

	private static void checkParameters(BuildTargetInformation bt) throws AssertionError {
		Iterator<? extends BuildTargetParameterInformation> paramit = bt.getParameters().iterator();
		BuildTargetParameterInformation inparam1 = paramit.next();
		BuildTargetParameterInformation inparam2 = paramit.next();
		
		BuildTargetParameterInformation outparam1 = paramit.next();
		BuildTargetParameterInformation outparam2 = paramit.next();
		
		assertFalse(paramit.hasNext());
		
		assertEquals(inparam1.getParameterName(), "inparam1");
		assertEquals(inparam2.getParameterName(), "inparam2");
		assertEquals(outparam1.getParameterName(), "outparam1");
		assertEquals(outparam2.getParameterName(), "outparam2");
		
		assertEquals(inparam1.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT),
				"input parameter 1");
		assertEquals(inparam1.getType(), BuildTargetParameterInformation.TYPE_INPUT);
		
		assertEquals(inparam2.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT),
				"input parameter 2");
		assertEquals(inparam2.getType(), BuildTargetParameterInformation.TYPE_INPUT);
		
		assertEquals(outparam1.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT),
				"output parameter 1");
		assertEquals(outparam1.getType(), BuildTargetParameterInformation.TYPE_OUTPUT);
		
		assertEquals(outparam2.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT),
				"output parameter 2");
		assertEquals(outparam2.getType(), BuildTargetParameterInformation.TYPE_OUTPUT);
	}
}
