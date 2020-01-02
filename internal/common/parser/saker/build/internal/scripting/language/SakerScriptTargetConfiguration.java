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
package saker.build.internal.scripting.language;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import saker.build.runtime.execution.TargetConfiguration;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.task.BuildTargetTaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;

public class SakerScriptTargetConfiguration implements TargetConfiguration {
	// maps target names to execution steps
	private ScriptParsingOptions parsingOptions;

	private final Map<String, BuildTargetTaskFactory> tasks;

	public SakerScriptTargetConfiguration(ScriptParsingOptions parsingOptions,
			LinkedHashMap<String, BuildTargetTaskFactory> tasks) {
		this.parsingOptions = parsingOptions;
		this.tasks = tasks;
	}

	@Override
	public ScriptParsingOptions getParsingOptions() {
		return parsingOptions;
	}

	@Override
	public BuildTargetTaskFactory getTask(String target) {
		Objects.requireNonNull(target, "target");
		return tasks.get(target);
	}

	@Override
	public Set<String> getTargetNames() {
		return ImmutableUtils.unmodifiableSet(tasks.keySet());
	}

	public boolean hasTarget(String name) {
		return tasks.containsKey(name);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + parsingOptions.getScriptPath() + ", targets=" + tasks.keySet() + "]";
	}
}
