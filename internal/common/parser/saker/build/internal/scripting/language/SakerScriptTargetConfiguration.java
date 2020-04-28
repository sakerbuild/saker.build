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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;

import saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException;
import saker.build.internal.scripting.language.task.DefaultsDeclarationSakerTaskFactory;
import saker.build.runtime.execution.TargetConfiguration;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.task.BuildTargetTaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class SakerScriptTargetConfiguration implements TargetConfiguration, Externalizable {
	private static final long serialVersionUID = 1L;

	private static final NavigableSet<String> DEFAULT_BUILD_TARGET_NAMES_SINGLETON = ImmutableUtils
			.singletonNavigableSet(SakerScriptTargetConfigurationReader.DEFAULT_BUILD_TARGET_NAME);

	private ScriptParsingOptions parsingOptions;

	// maps target names to execution steps
	private Map<String, BuildTargetTaskFactory> targets;
	private BuildTargetTaskFactory implicitBuildTarget;

	private Set<DefaultsDeclarationSakerTaskFactory> defaultDeclarations;

	/**
	 * For {@link Externalizable}.
	 */
	public SakerScriptTargetConfiguration() {
	}

	public SakerScriptTargetConfiguration(ScriptParsingOptions parsingOptions,
			LinkedHashMap<String, BuildTargetTaskFactory> tasks, BuildTargetTaskFactory implicitBuildTarget,
			Set<DefaultsDeclarationSakerTaskFactory> defaultDeclarations) {
		this.parsingOptions = parsingOptions;
		this.targets = tasks;
		this.implicitBuildTarget = implicitBuildTarget;
		this.defaultDeclarations = defaultDeclarations;
	}

	public BuildTargetTaskFactory getImplicitBuildTarget() {
		return implicitBuildTarget;
	}

	public Set<? extends DefaultsDeclarationSakerTaskFactory> getDefaultDeclarations() {
		return defaultDeclarations;
	}

	public void validateDefaultsFile() {
		if (!targets.isEmpty()) {
			throw new InvalidScriptDeclarationException(
					"Defaults build script cannot have any declared build targets.");
		}
	}

	@Override
	public ScriptParsingOptions getParsingOptions() {
		return parsingOptions;
	}

	@Override
	public BuildTargetTaskFactory getTask(String target) {
		Objects.requireNonNull(target, "target");
		BuildTargetTaskFactory res = targets.get(target);
		if (res == null && implicitBuildTarget != null
				&& SakerScriptTargetConfigurationReader.DEFAULT_BUILD_TARGET_NAME.equals(target) && targets.isEmpty()
				&& defaultDeclarations.isEmpty()) {
			return implicitBuildTarget;
		}
		return res;
	}

	@Override
	public Set<String> getTargetNames() {
		if (targets.isEmpty()) {
			if (defaultDeclarations.isEmpty() && implicitBuildTarget != null) {
				return DEFAULT_BUILD_TARGET_NAMES_SINGLETON;
			}
			return Collections.emptyNavigableSet();
		}
		return ImmutableUtils.unmodifiableSet(targets.keySet());
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(parsingOptions);
		SerialUtils.writeExternalMap(out, targets);
		out.writeObject(implicitBuildTarget);
		SerialUtils.writeExternalCollection(out, defaultDeclarations);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		parsingOptions = SerialUtils.readExternalObject(in);
		targets = SerialUtils.readExternalImmutableLinkedHashMap(in);
		implicitBuildTarget = SerialUtils.readExternalObject(in);
		defaultDeclarations = SerialUtils.readExternalImmutableHashSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((defaultDeclarations == null) ? 0 : defaultDeclarations.hashCode());
		result = prime * result + ((implicitBuildTarget == null) ? 0 : implicitBuildTarget.hashCode());
		result = prime * result + ((parsingOptions == null) ? 0 : parsingOptions.hashCode());
		result = prime * result + ((targets == null) ? 0 : targets.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SakerScriptTargetConfiguration other = (SakerScriptTargetConfiguration) obj;
		if (defaultDeclarations == null) {
			if (other.defaultDeclarations != null)
				return false;
		} else if (!defaultDeclarations.equals(other.defaultDeclarations))
			return false;
		if (implicitBuildTarget == null) {
			if (other.implicitBuildTarget != null)
				return false;
		} else if (!implicitBuildTarget.equals(other.implicitBuildTarget))
			return false;
		if (parsingOptions == null) {
			if (other.parsingOptions != null)
				return false;
		} else if (!parsingOptions.equals(other.parsingOptions))
			return false;
		if (targets == null) {
			if (other.targets != null)
				return false;
		} else if (!targets.equals(other.targets))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + parsingOptions.getScriptPath() + ", targets=" + targets.keySet()
				+ "]";
	}

}
