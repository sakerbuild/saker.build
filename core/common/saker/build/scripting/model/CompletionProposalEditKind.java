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

import saker.apiextract.api.PublicApi;

/**
 * Class containing possible values for {@link CompletionProposalEdit} kinds.
 * <p>
 * The possible values are present as <code>static final String</code> fields in this class.
 * <p>
 * The type kinds are interpreted in an ignore-case manner.
 * <p>
 * <i>Implementation note:</i> This class is not an <code>enum</code> to ensure forward and backward compatibility. New
 * kinds may be added to it in the future.
 */
@PublicApi
public class CompletionProposalEditKind {
	/**
	 * Simple text insertion.
	 * <p>
	 * 
	 * @see InsertCompletionProposalEdit
	 */
	public static final String INSERT = "INSERT";

	private CompletionProposalEditKind() {
		throw new UnsupportedOperationException();
	}
}
