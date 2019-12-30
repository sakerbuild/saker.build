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
