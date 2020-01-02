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

import java.io.Externalizable;
import java.util.Map;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeMapSerializeKeySerializeValueWrapper;

/**
 * Interface for providing parsing options and configuration for scripts.
 * <p>
 * The options consists of the script path and user defined arbitrary options.
 * <p>
 * Instances of this interface are immuatable and are recommended to be {@link Externalizable}.
 * 
 * @see SimpleScriptParsingOptions
 */
public interface ScriptParsingOptions {
	/**
	 * Gets the path where the script resides.
	 * 
	 * @return The path.
	 */
	@RMICacheResult
	public SakerPath getScriptPath();

	/**
	 * Gets the arbitrary user defined options for parsing the script.
	 * 
	 * @return An unmodifiable map of options. Values are nullable.
	 */
	@RMICacheResult
	@RMIWrap(RMITreeMapSerializeKeySerializeValueWrapper.class)
	//XXX unmodifiable RMI wrap
	public Map<String, String> getOptions();

	/**
	 * The hash code of a script parsing options instance is defined as the following:
	 * 
	 * <pre>
	 * getScriptPath().hashCode()
	 * </pre>
	 * 
	 * The options are not included in the hash-code as it is expected for a given context to have only one option
	 * configuration for a given path.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	@RMICacheResult
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
