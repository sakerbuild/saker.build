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
