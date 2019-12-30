package saker.build.runtime.execution;

import saker.build.scripting.ScriptAccessProvider;

public final class ScriptAccessorClassPathData {
	protected ScriptAccessProvider scriptAccessor;

	public ScriptAccessorClassPathData(ScriptAccessProvider scriptAccessor) {
		this.scriptAccessor = scriptAccessor;
	}

	public ClassLoader getClassLoader() {
		return scriptAccessor.getClass().getClassLoader();
	}

	public ScriptAccessProvider getScriptAccessor() {
		return scriptAccessor;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (scriptAccessor != null ? "scriptAccessor=" + scriptAccessor : "")
				+ "]";
	}

}
