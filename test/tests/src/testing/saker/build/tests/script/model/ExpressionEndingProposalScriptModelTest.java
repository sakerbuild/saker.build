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

import java.util.List;

import saker.build.scripting.model.ScriptCompletionProposal;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import testing.saker.SakerTest;

@SakerTest
public class ExpressionEndingProposalScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void initFileProvider() throws Exception {
		super.initFileProvider();
		files.putFile(DEFAULT_BUILD_FILE, "");
	}

	@Override
	protected void runTest() throws Throwable {
		List<? extends ScriptCompletionProposal> proposals;
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);

		{
			String data = "include()\n";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes()));
			proposals = model.getCompletionProposals(data.length());
			assertNotEmpty(proposals);
		}
		{
			String data = "include();";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes()));
			proposals = model.getCompletionProposals(data.length());
			assertNotEmpty(proposals);
		}
		{
			String data = "include();\n";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes()));
			proposals = model.getCompletionProposals(data.length());
			assertNotEmpty(proposals);
		}
		{
			String data = "include()\r\n";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes()));
			proposals = model.getCompletionProposals(data.length());
			assertNotEmpty(proposals);
		}
		{
			String data = "include()\r";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes()));
			proposals = model.getCompletionProposals(data.length());
			assertNotEmpty(proposals);
		}
		{
			String data = "include()\r\n";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes()));
			proposals = model.getCompletionProposals(data.length() - 1);
			assertEmpty(proposals);
		}
		{
			String data = "include()";
			model.createModel(() -> new UnsyncByteArrayInputStream(data.getBytes()));
			proposals = model.getCompletionProposals(data.length());
			assertEmpty(proposals);
		}
	}

}
