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

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;

/**
 * Model representation of build script files.
 * <p>
 * Script models are used to retrieve various informations from scripts. They are most commonly used by IDEs to provide
 * syntax highlight, content assist, and related features. Script models can also be used outside of an IDE.
 * <p>
 * Script models are created with {@linkplain #getParsingOptions() parsing options} which can contain user-specified
 * configuration data. They are also passed a {@link IOSupplier IOSupplier&lt;? extends ByteSource>} instance which
 * serves as the input of the underlying build script file. This is called the base input.
 * <p>
 * The base input should be used as a baseline for retrieving the source data of the build script file. As the models
 * can be used inside IDEs, there is a possibility that the current representation of the build script file doesn't
 * match the one on the file system. In this case the models should be able to handle the scenario when they don't use
 * this base input for the source data of the script file. Methods which should handle this scenario has a replacement
 * data supplier parameter. (See {@link #createModel(IOSupplier)}, {@link #updateModel(List, IOSupplier)})
 * <p>
 * Models are not required to support all information querying methods, they might return <code>null</code> or empty
 * collections.
 * <p>
 * Script models have a state depending on the current state of input. <br>
 * When the model is first created, it is in uninitialized state. When some information query method is called, models
 * should attemt to parse the base input and provide the information based on that.<br>
 * If the model failed to parse the input script, then it is in invalid state. In this state information query methods
 * can return empty results, but may not throw an exception due to the model being in invalid state.<br>
 * If any of the updating methods return successfully, the model will be in a valid state. A valid state only means that
 * the model implementation was able to create a representation of the data conveyed in the build script, but doesn't
 * mean that the underlying script is safe to be a subject of build execution. Model and script language implementations
 * should strive to be error-tolerant.
 * <p>
 * When a model is in a valid state, subsequent {@link #updateModel(List, IOSupplier)} calls can be used to
 * incrementally update the model by providing a list of incremental changes to the underlying source data of the build
 * script. This is mainly used when the user is editing a build script in the IDE. Models are not required to support
 * updating the model incrementally.
 * <p>
 * Models provide some information based on tokens. Tokens are non-overlapping regions of source code that have
 * specified properties based on the model implementation. Via tokens, models can provide {@linkplain #getTokenStyles()
 * syntax highlighting}, and {@linkplain #getTokenInformation(ScriptToken) general information} about them.
 * <p>
 * Models can provide a {@linkplain #getStructureOutline() structure outline} for themselves, which is a tree
 * representation of the model source code.
 * <p>
 * During IDE use the users will expect content assistant features to be present. With
 * {@link #getCompletionProposals(int)} method the models can provide a list of source code completions which are then
 * displayed to the user.
 * <p>
 * Models can (and recommended to) use the {@link ExternalScriptInformationProvider} interface to retrieve informational
 * data based on the current build configuration. Instances of these interfaces are avaiable from the script modelling
 * environment configuration by calling
 * {@link ScriptModellingEnvironment#getConfiguration()}{@link ScriptModellingEnvironmentConfiguration#getExternalScriptInformationProviders()
 * .getExternalScriptInformationProviders()}. Models should not keep references to these information providers, but
 * query them every time it would like to use them, as they might change during the lifetime of the model. <br>
 * Models can also use the {@link ScriptModellingEnvironmentConfiguration#getPathConfiguration()} of the same
 * configuration instance to provide assistant features for the user based on file data. Same with external information
 * providers, do not keep references to the path configuration, or file providers.
 */
public interface ScriptSyntaxModel {
	/**
	 * Gets the script parsing options used to create this model.
	 * <p>
	 * This doesn't change during the lifetime of the model.
	 * 
	 * @return The script parsing options.
	 */
	public ScriptParsingOptions getParsingOptions();

	/**
	 * Creates the model for the given data input.
	 * <p>
	 * Implementations should parse the provided input and build the model data required for operation of this class.
	 * <p>
	 * The argument input supplier may be <code>null</code>, in which case implementations should use the base input
	 * supplier passed during instantiation to parse the data.
	 * 
	 * @param scriptdatasupplier
	 *            The script data supplier or <code>null</code> to use the base input supplier.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ScriptParsingFailedException
	 *             If the parsing of the script failed.
	 */
	public void createModel(IOSupplier<? extends ByteSource> scriptdatasupplier)
			throws IOException, ScriptParsingFailedException;

	/**
	 * Updates the model incrementally for the specified list of text changes occurred since the last successful update.
	 * <p>
	 * This method takes a list of changes as a parameter, which describe what text related changes occurred to the
	 * document since the last successful update. The list is ordered historically, meaning that the first item is the
	 * first change that occurred to the text after the last successful update. The offsets in the
	 * {@link TextRegionChange} structure are relative to the directly previous state of the source document. Meaning,
	 * that unlike {@link ScriptCompletionProposal}, the text changes can be applied after each other to the starting
	 * document to get the current state.
	 * <p>
	 * This method might be called even when {@link #createModel(IOSupplier)} has not yet been called before, or the
	 * parsing wasn't successful. Implementations should gracefully handle this scenario, use the provided data supplier
	 * for retrieving the whole contents of the source code, and parse the code based on that.
	 * <p>
	 * The default implementation of this method simply calls {@link #createModel(IOSupplier)} with the provided
	 * supplier.
	 * 
	 * @param events
	 *            The text change events in sequential order.
	 * @param scriptdatasupplier
	 *            The data supplier for the whole contents of the script.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ScriptParsingFailedException
	 *             If the parsing/updating of the script failed.
	 */
	public default void updateModel(List<? extends TextRegionChange> events,
			IOSupplier<? extends ByteSource> scriptdatasupplier) throws IOException, ScriptParsingFailedException {
		createModel(scriptdatasupplier);
	}

	/**
	 * Invalidates the model, resets it to the uninitialized state.
	 * <p>
	 * Implementations are to discard any internal state when this method is called and rebuild it from the input next
	 * time some information is requested from the model.
	 */
	public void invalidateModel();

	/**
	 * Gets the build target names in this script.
	 * <p>
	 * If the model is in an invalid state, it should attempt to parse it using the base input.
	 * 
	 * @return An unmodifiable set of target names.
	 * @throws ScriptParsingFailedException
	 *             If the parsing of the script failed.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public default Set<String> getTargetNames() throws ScriptParsingFailedException, IOException {
		//TODO this method should not return the target names, but some general target information about in and out parameters too
		return Collections.emptySet();
	}

	/**
	 * Gets the script tokens for a given range of the document.
	 * <p>
	 * This method returns the tokens in the order sorted by their {@linkplain ScriptToken#getOffset() starting offset}.
	 * <p>
	 * The offset and length hint parameters can be used to only return a subset of the tokens. Implementations aren't
	 * required to always satisfy the range request, but they can to reduce computational load. All tokens should be
	 * returned which intersect (or touch) a given token region.
	 * <p>
	 * To get all tokens, pass 0, and {@link Integer#MAX_VALUE} as region hints.
	 * <p>
	 * The returned token regions should not overlap.
	 * 
	 * @param offsethint
	 *            The character offset hint for the starting region of the tokens.
	 * @param lengthhint
	 *            The length of the hint region.
	 * @return An iterable for the script tokens.
	 */
	public default Iterable<? extends ScriptToken> getTokens(int offsethint, int lengthhint) {
		return Collections.emptyList();
	}

	/**
	 * Gets the script token at the given offset.
	 * <p>
	 * As script token doesn't overlap, this method should uniquely identify the token that is at a given offset.
	 * <p>
	 * If the offset is located at the edge of two token ranges, then the implementation may choose which token it wants
	 * to return (left or right). They can choose, but they are encouraged to be consistent with this choosing. (I.e.
	 * always return the left token or always the right when the offset is exactly between two tokens).
	 * <p>
	 * If there are more than two tokens at a given offset (due to 0 length tokens), the tokens which has a length
	 * greater than 0 should be returned.
	 * <p>
	 * Implementations can return <code>null</code> if no token is found at the given offset.
	 * <p>
	 * The default implementation searches the tokens returned by {@link #getTokens(int, int)} and returns an
	 * appropriate one. (It returns the left token if the offset is at the edges, and searches for a token with greater
	 * than 0 length if there are more.)
	 * 
	 * @param offset
	 *            The offset to get the token for.
	 * @return The token at the given offset or <code>null</code> if not found.
	 * @see #getTokens(int, int)
	 */
	public default ScriptToken getTokenAtOffset(int offset) {
		Iterable<? extends ScriptToken> tokens = getTokens(offset, 0);
		Iterator<? extends ScriptToken> it = tokens.iterator();
		while (it.hasNext()) {
			ScriptToken t = it.next();
			if (offset >= t.getOffset() && offset <= t.getEndOffset()) {
				if (t.getLength() == 0) {
					//the token has zero length, try to find another token at the offset with greater length
					for (ScriptToken n; it.hasNext();) {
						n = it.next();
						if (offset >= n.getOffset() && offset <= n.getEndOffset()) {
							//the token 'n' is in the range of offset
							if (n.getLength() > 0) {
								//return it if it has greater than 0 length
								return n;
							}
							//go to next
						} else {
							//the token 'n' is not in the range, just return t
							break;
						}
					}
				}
				return t;
			}
		}
		return null;
	}

	/**
	 * Gets the token styles used by this model.
	 * <p>
	 * The token styles are mapped to the {@linkplain ScriptToken#getType() token types}, and contain a set of
	 * applicable styles. The applied style will be chosen on the current theme of the IDE.
	 * <p>
	 * The result should contain all styles the script model may contain. Callers of this method may cache the returned
	 * token styles in order to avoid calling this method multiple times.
	 * 
	 * @return An unmodifiable map of token styles.
	 */
	public default Map<String, Set<? extends TokenStyle>> getTokenStyles() {
		return Collections.emptyNavigableMap();
	}

	/**
	 * Gets the structural outline for this script model.
	 * <p>
	 * The structure outline is usually displayed in a tree view alongside the code in the IDE.
	 * 
	 * @return The structure outline.
	 */
	public default ScriptStructureOutline getStructureOutline() {
		return null;
	}

	/**
	 * Queries the information about a particular token.
	 * <p>
	 * The token informations are usually displayed when the user hovers over a given token in the code editor, or
	 * explicitly requests information about it.
	 * 
	 * @param token
	 *            The token to get the information for.
	 * @return The token information or <code>null</code> if not available.
	 */
	public default ScriptTokenInformation getTokenInformation(ScriptToken token) {
		return null;
	}

	/**
	 * Gets a list of completion proposals which can be applied to the text at the given character offset.
	 * 
	 * @param offset
	 *            The character offset in the file.
	 * @return An unmodifiable list of completion proposals.
	 */
	public default List<? extends ScriptCompletionProposal> getCompletionProposals(int offset) {
		return Collections.emptyList();
	}
}
