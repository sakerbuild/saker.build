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

import java.io.IOException;

import saker.build.file.SakerFile;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.scripting.TargetConfigurationReader;
import saker.build.scripting.TargetConfigurationReadingResult;
import saker.build.thirdparty.saker.util.io.ByteSource;

class TargetConfigurationReadingFileDataComputer implements FileDataComputer<TargetConfigurationReadingResult> {
	private ScriptParsingOptions parseOptions;
	private Object accessorKey;
	private transient ScriptAccessProvider scriptAccessor;

	public TargetConfigurationReadingFileDataComputer(ScriptAccessProvider scriptAccessor,
			ScriptParsingOptions parseOptions) {
		this.scriptAccessor = scriptAccessor;
		this.parseOptions = parseOptions;
		this.accessorKey = scriptAccessor.getScriptAccessorKey();
	}

	@Override
	public TargetConfigurationReadingResult compute(SakerFile file) throws IOException {
		try (ByteSource input = file.openByteSource()) {
			TargetConfigurationReader reader = scriptAccessor.createConfigurationReader();
			if (reader == null) {
				throw new NullPointerException(
						"Scripting language provider returned null configuration reader: " + scriptAccessor);
			}
			return reader.readConfiguration(parseOptions, input);
		} catch (ScriptParsingFailedException e) {
			throw new IOException("Failed to parse target configuration.", e);
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((accessorKey == null) ? 0 : accessorKey.hashCode());
		result = prime * result + ((parseOptions == null) ? 0 : parseOptions.hashCode());
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
		TargetConfigurationReadingFileDataComputer other = (TargetConfigurationReadingFileDataComputer) obj;
		if (accessorKey == null) {
			if (other.accessorKey != null)
				return false;
		} else if (!accessorKey.equals(other.accessorKey))
			return false;
		if (parseOptions == null) {
			if (other.parseOptions != null)
				return false;
		} else if (!parseOptions.equals(other.parseOptions))
			return false;
		return true;
	}
}