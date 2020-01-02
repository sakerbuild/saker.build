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
