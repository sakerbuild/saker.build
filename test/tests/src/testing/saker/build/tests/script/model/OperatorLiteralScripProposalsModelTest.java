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
public class OperatorLiteralScripProposalsModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveScriptAnalysis(model, filedata);

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(Param1: /")).assertPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(Param1: /") - 1).assertPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(Param1: \"/")).assertPresent("\"/EN1\"");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: /")).assertPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [/")).assertPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [/EN1, /"))
				.assertPresentFrequency(1, "/EN1");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "T1, \r\n" + "/"))
				.assertPresentFrequency(1, "/EN1");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "T2 \r\n" + "/"))
				.assertPresentFrequency(1, "/EN1");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TT \r\n" + "*"))
				.assertPresentFrequency(1, "*EN2");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TT \r\n" + "%"))
				.assertPresentFrequency(1, "%EN3");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TT \r\n" + "+"))
				.assertPresentFrequency(1, "+EN4").assertNotPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TT \r\n" + "-"))
				.assertPresentFrequency(1, "-EN5").assertNotPresent("/EN1");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TT \r\n" + "="))
				.assertPresentFrequency(1, "=EN6").assertPresentFrequency(1, "==EN8").assertNotPresent("/EN1");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TT \r\n" + "!="))
				.assertPresentFrequency(1, "!=EN7").assertNotPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TT \r\n" + "=="))
				.assertPresentFrequency(1, "==EN8").assertNotPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TT \r\n" + "!=") - 1)
				.assertPresentFrequency(1, "!=EN7").assertPresentFrequency(1, "!EN20").assertNotPresent("/EN1");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TT \r\n" + "<<"))
				.assertPresentFrequency(1, "<<EN9").assertNotPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TT \r\n" + ">>"))
				.assertPresentFrequency(1, ">>EN10").assertNotPresent("/EN1");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TC \r\n" + ">"))
				.assertPresentFrequency(1, ">EN11").assertPresentFrequency(1, ">>EN10")
				.assertPresentFrequency(1, ">=EN12").assertNotPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TC \r\n" + ">="))
				.assertPresentFrequency(1, ">=EN12").assertNotPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TC \r\n" + "<"))
				.assertPresentFrequency(1, "<EN13").assertNotPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TC \r\n" + "<="))
				.assertPresentFrequency(1, "<=EN14").assertNotPresent("/EN1");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TC \r\n" + ">=") - 1)
				.assertPresentFrequency(1, ">EN11").assertPresentFrequency(1, ">>EN10")
				.assertPresentFrequency(1, ">=EN12").assertNotPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TC \r\n" + "<=") - 1)
				.assertPresentFrequency(1, "<EN13").assertPresentFrequency(1, "<<EN9")
				.assertPresentFrequency(1, "<=EN14").assertNotPresent("/EN1");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TT \r\n" + "||"))
				.assertPresentFrequency(1, "||EN15").assertNotPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TT \r\n" + "&&"))
				.assertPresentFrequency(1, "&&EN16").assertNotPresent("/EN1");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TB \r\n" + "|"))
				.assertPresentFrequency(1, "|EN17").assertPresentFrequency(1, "||EN15").assertNotPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TB \r\n" + "&"))
				.assertPresentFrequency(1, "&EN18").assertPresentFrequency(1, "&&EN16").assertNotPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TB \r\n" + "^"))
				.assertPresentFrequency(1, "^EN19").assertNotPresent("/EN1");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TU \r\n" + "!"))
				.assertPresentFrequency(1, "!EN20").assertPresentFrequency(1, "!=EN7").assertNotPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: [\r\n" + "TU \r\n" + "~"))
				.assertPresentFrequency(1, "~EN21").assertNotPresent("/EN1");

		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: !"))
				.assertPresentFrequency(1, "!EN20").assertPresentFrequency(1, "!=EN7").assertNotPresent("/EN1");
		assertProposals(model, endIndexOf(filedata, "operator.enum.paramed(ListParam1: ~"))
				.assertPresentFrequency(1, "~EN21").assertNotPresent("/EN1");
	}
}
