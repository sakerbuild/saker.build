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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.apiextract.api.PublicApi;

/**
 * {@link CompletionProposalEdit} implementation representing a text insertion.
 * <p>
 * Associated kind is {@link CompletionProposalEditKind#INSERT}.
 * <p>
 * The range of the proposal edit will be replaced with the text specified during construction of an instance.
 */
@PublicApi
public final class InsertCompletionProposalEdit implements CompletionProposalEdit, Externalizable {
	private static final long serialVersionUID = 1L;

	private TextRegionChange textChange;

	/**
	 * For {@link Externalizable}.
	 */
	public InsertCompletionProposalEdit() {
	}

	/**
	 * Contructs a new instance with a text region change to use.
	 * 
	 * @param textChange
	 *            The text region change.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public InsertCompletionProposalEdit(TextRegionChange textChange) throws NullPointerException {
		Objects.requireNonNull(textChange, "text change");
		this.textChange = textChange;
	}

	@Override
	public String getKind() {
		return CompletionProposalEditKind.INSERT;
	}

	@Override
	public int getOffset() {
		return textChange.getOffset();
	}

	@Override
	public int getLength() {
		return textChange.getLength();
	}

	/**
	 * Gets the inserted text.
	 * 
	 * @return The inserted text.
	 */
	public String getText() {
		return textChange.getText();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(textChange);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		textChange = (TextRegionChange) in.readObject();
	}

	@Override
	public int hashCode() {
		return getKind().hashCode() ^ getOffset() ^ getLength();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		InsertCompletionProposalEdit other = (InsertCompletionProposalEdit) obj;
		if (textChange == null) {
			if (other.textChange != null)
				return false;
		} else if (!textChange.equals(other.textChange))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + textChange + "]";
	}

}
