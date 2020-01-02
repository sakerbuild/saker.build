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
import java.util.ServiceLoader;

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.scripting.model.ScriptModellingEngine;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Interface specifying the main access point for build script language implementations.
 * <p>
 * Language implementations provide this interface for accessing the various functions a script language implementations
 * should have.
 * <p>
 * Build language implementations and their versions are uniquely identified by their
 * {@linkplain #getScriptAccessorKey() accessor keys}.
 * <p>
 * Implementations should have a public no-arg constructor.
 * <p>
 * This class might by used by the {@link ServiceLoader} mechanism.
 * <p>
 * The script access provider for the built-in language is available using
 * {@link ExecutionScriptConfiguration#getScriptAccessorProvider(SakerEnvironment, ScriptProviderLocation)} with the
 * {@link ScriptProviderLocation#getBuiltin()} as the argument.
 */
public interface ScriptAccessProvider {
	/**
	 * Creates an object capable of reading build script target configurations.
	 * <p>
	 * This method can never return <code>null</code>.
	 * 
	 * @return The target configuration reader.
	 */
	public TargetConfigurationReader createConfigurationReader();

	/**
	 * Creates a modelling engine for this scripting language.
	 * <p>
	 * This method can return <code>null</code> if modelling is unsupported.
	 * 
	 * @param modellingenvironment
	 *            The modelling environment for the created engine.
	 * @return The modelling engine or <code>null</code> if modelling is not supported by this language.
	 */
	public ScriptModellingEngine createModellingEngine(ScriptModellingEnvironment modellingenvironment);

	/**
	 * Gets the object key uniquely identifying the underlying script language implementation and its version.
	 * <p>
	 * The purpose of script accessor keys is to uniquely identify an implementation of a script language. This can
	 * ensure that if two accessor keys {@linkplain Object#equals(Object) equal}, then the corresponding access
	 * providers will behave the same way. I.e. they will parse the same scripts with the same results.
	 * <p>
	 * This accessor keys are used during incremental compilation to ensure that the scripts haven't changed between
	 * executions.
	 * <p>
	 * The return value is required to adhere the {@link Object#equals(Object)} and {@link Object#hashCode()} contract
	 * as their main purpose is to be equality compared.
	 * <p>
	 * The return value is required to implement {@link Object#toString()} so that it returns a string representation
	 * identifying this script language implementation. A version number, or build date can be appropriate for this.
	 * Note that all the fields which are part of the equality check should be present in the string representation
	 * somehow.
	 * <p>
	 * The return value should be serializable, preferably implement {@link Externalizable}.
	 * <p>
	 * Implementations should consider creating a custom class for the return value, which can ensure classpath
	 * compatibility for incremental builds.
	 * 
	 * @return The script language version key.
	 */
	@RMISerialize
	@RMICacheResult
	public Object getScriptAccessorKey();
}
