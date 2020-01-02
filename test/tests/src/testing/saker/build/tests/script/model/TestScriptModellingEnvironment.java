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

import java.io.IOException;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.scripting.model.ScriptModellingEngine;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.scripting.model.ScriptModellingEnvironmentConfiguration;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;

public class TestScriptModellingEnvironment implements ScriptModellingEnvironment {
	private ScriptModellingEnvironmentConfiguration configuration;
	private NavigableMap<SakerPath, ScriptSyntaxModel> models;

	public TestScriptModellingEnvironment(ScriptModellingEnvironmentConfiguration configuration) {
		this.configuration = configuration;
	}

	public void init(ScriptModellingEngine engine, Set<SakerPath> files) {
		System.out.println("TestScriptModellingEnvironment.init() " + files);
		this.models = new TreeMap<>();
		for (SakerPath scriptfile : files) {
			SakerPathFiles.requireAbsolutePath(scriptfile);
			ProviderHolderPathKey pathkey = configuration.getPathConfiguration().getPathKey(scriptfile);
			IOSupplier<? extends ByteSource> inputsupplier = () -> pathkey.getFileProvider()
					.openInput(pathkey.getPath());
			ScriptSyntaxModel model = engine.createModel(
					configuration.getScriptConfiguration().getScriptParsingOptions(scriptfile), inputsupplier);
			models.put(scriptfile, model);
		}
	}

	@Override
	public NavigableSet<SakerPath> getTrackedScriptPaths() {
		return ImmutableUtils.unmodifiableNavigableSet(models.navigableKeySet());
	}

	@Override
	public ScriptSyntaxModel getModel(SakerPath scriptpath) throws InvalidPathFormatException {
		return models.get(scriptpath);
	}

	@Override
	public ScriptModellingEnvironmentConfiguration getConfiguration() {
		return configuration;
	}

	@Override
	public void close() throws IOException {

	}

}
