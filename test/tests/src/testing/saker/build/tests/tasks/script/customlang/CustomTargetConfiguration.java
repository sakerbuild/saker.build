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