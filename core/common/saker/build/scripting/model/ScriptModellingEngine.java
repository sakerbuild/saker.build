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
package saker.build.scripting.model;

import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;

/**
 * Interface for providing modelling related functionality for a scripting language.
 * <p>
 * {@linkplain ScriptSyntaxModel Script models} are used to retrieve various informations from scripts. The
 * responsibility of a modelling engine is to provide a context for the models to share information.
 * <p>
 * Modelling engines work with {@linkplain ScriptModellingEnvironment modelling environment} which provide overall
 * information about the present scripts for a build configuration. The modelling environment is passed to the engine at
 * creation time.
 * <p>
 * Modelling engines should not hold references to unmanaged data. (E.g. file handles, network connections, etc...)
 * <p>
 * Modelling engines are not shared by modelling environments.
 */
public interface ScriptModellingEngine extends AutoCloseable {
	/**
	 * Creates a model representation for the given script identified by the parsing options.
	 * <p>
	 * The created model is to be lazily populated, meaning that creating a model does not entitle parsing it at the
	 * time of this function call.
	 * 
	 * @param parsingoptions
	 *            The parsing options associated with the created script model.
	 * @param baseinputsupplier
	 *            The base input supplier. (See documentation of {@link ScriptSyntaxModel})
	 * @return The created script model.
	 * @see ScriptSyntaxModel
	 * @see ExecutionScriptConfiguration
	 */
	public ScriptSyntaxModel createModel(ScriptParsingOptions parsingoptions,
			IOSupplier<? extends ByteSource> baseinputsupplier);

	/**
	 * Destroys a previously created script model from this engine.
	 * <p>
	 * Destroying a model should result in releasing any resources associated to it, and treating the corresponding
	 * script as no longer existing. Any other models that depend on the currently destroyed model should update their
	 * underlying data.
	 * 
	 * @param model
	 *            The model to destroy.
	 */
	public void destroyModel(ScriptSyntaxModel model);

	/**
	 * Closes this modelling engine.
	 * <p>
	 * The engine should release references to models, and treat them as invalidated. The engine should not make any
	 * further calls to the modelling environment. After closing a modelling engine, further calls to its models might
	 * be made, but they should be treated as invalid script models.
	 * <p>
	 * This method throws no checked exceptions, as it should not fail. Modelling engines should not keep references to
	 * unmanaged resources like files, network connections, etc...
	 */
	@Override
	public void close();
}
