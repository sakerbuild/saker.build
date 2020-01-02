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
package saker.build.scripting;

import java.io.IOException;

import saker.build.runtime.execution.TargetConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.thirdparty.saker.util.io.ByteSource;

/**
 * Interface for reading a build script and converting it to a {@linkplain TargetConfiguration target configuration}.
 * <p>
 * The parsing of the build script can be tuned using {@link ScriptParsingOptions} which provide arbitrary options for
 * the reader.
 * <p>
 * Implementations of this interface should be stateless, and should be able to handle concurrent configuration reading
 * requests from multiple sources.
 * 
 * @see ExecutionScriptConfiguration
 */
public interface TargetConfigurationReader {
	/**
	 * Reads the target configuration from the given input.
	 * <p>
	 * The return value of this method contains the actual parsed target configuration with other information meta-data.
	 * 
	 * @param options
	 *            The parsing options to use.
	 * @param input
	 *            The byte input of the script.
	 * @return The parsing result for the input.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ScriptParsingFailedException
	 *             If the script parsing failed for some reason (invalid syntax or semantics most commonly).
	 * @throws NullPointerException
	 *             If any of the parameters are <code>null</code>.
	 */
	public TargetConfigurationReadingResult readConfiguration(ScriptParsingOptions options, ByteSource input)
			throws IOException, ScriptParsingFailedException, NullPointerException;
}