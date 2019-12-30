package saker.build.internal.scripting.language.model;

import saker.build.scripting.ScriptParsingOptions;
import saker.build.scripting.model.ScriptModellingEngine;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;

public class SakerScriptModellingEngine implements ScriptModellingEngine {
	private ScriptModellingEnvironment modellingEnvironment;

	public SakerScriptModellingEngine(ScriptModellingEnvironment modellingenvironment) {
		this.modellingEnvironment = modellingenvironment;
	}

	@Override
	public ScriptSyntaxModel createModel(ScriptParsingOptions parsingoptions,
			IOSupplier<? extends ByteSource> baseinputsupplier) {
		return new SakerParsedModel(this, parsingoptions, baseinputsupplier, modellingEnvironment);
	}

	@Override
	public void destroyModel(ScriptSyntaxModel model) {
	}

	@Override
	public void close() {
	}

}
