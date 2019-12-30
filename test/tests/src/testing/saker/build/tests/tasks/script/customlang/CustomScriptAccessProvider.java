package testing.saker.build.tests.tasks.script.customlang;

import saker.build.scripting.ScriptAccessProvider;
import saker.build.scripting.TargetConfigurationReader;
import saker.build.scripting.model.ScriptModellingEngine;
import saker.build.scripting.model.ScriptModellingEnvironment;

public class CustomScriptAccessProvider implements ScriptAccessProvider {
	private static final Object VERSION;
	static {
		String val;
		try {
			new AppendCustomLangClass();
			val = "vappend";
		} catch (LinkageError e) {
			val = "v1";
		}
		Object actualversion = val;
		try {
			actualversion = new CustomLangVersionKey(val);
		} catch (LinkageError e) {
		}
		VERSION = actualversion;
	}

	@Override
	public TargetConfigurationReader createConfigurationReader() {
		return new CustomTargetConfigurationReader();
	}

	@Override
	public Object getScriptAccessorKey() {
		return VERSION;
	}

	@Override
	public ScriptModellingEngine createModellingEngine(ScriptModellingEnvironment modellingenvironment) {
		return null;
	}

}
