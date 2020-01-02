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

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.scripting.SimpleTargetConfigurationReadingResult;
import saker.build.scripting.TargetConfigurationReader;
import saker.build.scripting.TargetConfigurationReadingResult;
import saker.build.task.BuildTargetTaskFactory;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.StreamUtils;

public final class CustomTargetConfigurationReader implements TargetConfigurationReader {
	@Override
	public TargetConfigurationReadingResult readConfiguration(ScriptParsingOptions options, ByteSource input)
			throws IOException, ScriptParsingFailedException {
		String s = StreamUtils.readSourceStringFully(input);
		String[] lines = s.split("\n");
		Map<String, BuildTargetTaskFactory> targets = new TreeMap<>();
		for (String l : lines) {
			if (l.isEmpty()) {
				continue;
			}
			int idx = l.indexOf(':');
			if (idx < 0) {
				throw new ScriptParsingFailedException("Invalid line: " + l, Collections.emptySet());
			}
			String targetname = l.substring(0, idx);
			TaskName taskname = TaskName.valueOf(l.substring(idx + 1).trim());
			targets.put(targetname, new CustomBuildTargetTaskFactory(taskname));
		}
		return new SimpleTargetConfigurationReadingResult(new CustomTargetConfiguration(targets, options), null);
	}
}