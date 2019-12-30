package testing.saker.build.tests.tasks.script.customlang;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import saker.build.runtime.execution.TargetConfiguration;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.task.BuildTargetTaskFactory;

public final class CustomTargetConfiguration implements TargetConfiguration {
	private final Map<String, BuildTargetTaskFactory> targets;
	private final ScriptParsingOptions options;

	CustomTargetConfiguration(Map<String, BuildTargetTaskFactory> targets, ScriptParsingOptions options) {
		this.targets = targets;
		this.options = options;
	}

	@Override
	public BuildTargetTaskFactory getTask(String target) {
		Objects.requireNonNull(target, "target");
		return targets.get(target);
	}

	@Override
	public Set<String> getTargetNames() {
		return targets.keySet();
	}

	@Override
	public ScriptParsingOptions getParsingOptions() {
		return options;
	}
}