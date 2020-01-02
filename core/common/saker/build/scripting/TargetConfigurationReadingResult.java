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

import saker.build.runtime.execution.TargetConfiguration;
import saker.build.thirdparty.saker.util.io.ByteSource;

/**
 * Container for build script parsing results.
 * <p>
 * This interface provides access to the result of a build script parsing. (See
 * {@link TargetConfigurationReader#readConfiguration(ScriptParsingOptions, ByteSource)})
 * <p>
 * In addition to the parsed target configuration this interface can provide information meta-data to use.
 * 
 * @see TargetConfigurationReader
 * @see SimpleTargetConfigurationReadingResult
 */
public interface TargetConfigurationReadingResult {
	/**
	 * Gets the parsed target configuration.
	 * 
	 * @return The target configuration.
	 */
	public TargetConfiguration getTargetConfiguration();

	/**
	 * Gets the optional information provider provided by the script parsing implementation.
	 * 
	 * @return The information provider or <code>null</code> if this functionality is not supported.
	 */
	public ScriptInformationProvider getInformationProvider();
}
