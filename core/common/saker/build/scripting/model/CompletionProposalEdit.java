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

/**
 * Common superinterface for the possible completion proposal related document changes.
 * <p>
 * The actual change is based on the {@linkplain #getKind() kind of the edit} and the related implementation class.
 * <p>
 * Each completion proposal edit has a range which it replaces in the originating source document. The range is defined
 * by a {@linkplain #getOffset() file offset} and a {@linkplain #getLength() region length}. When the edit is applied,
 * the characters will be replaced in the document to the contents of the edit based on its kind. The specified range
 * may be empty to signal an insertion. The offsets in the range is relative to the file offsets of the original
 * document when the proposal request was invoked.
 * <p>
 * Subsequent proposal edits in a {@linkplain ScriptCompletionProposal proposal} may not overlap in range.
 */
public interface CompletionProposalEdit {
	/**
	 * The kind of the edit to be applied to the document.
	 * 
	 * @return The kind.
	 */
	public String getKind();

	/**
	 * Gets the region starting offset of this proposal edit.
	 * 
	 * @return The offset.
	 */
	public int getOffset();

	/**
	 * Gets the region length of this proposal edit.
	 * 
	 * @return The region length.
	 */
	public int getLength();

	/**
	 * Gets the hash code of this completion proposal edit.
	 * <p>
	 * The hash code is defined to be the hash code of the following:
	 * 
	 * <pre>
	 * getKind().hashCode() ^ getOffset() ^ getLength()
	 * </pre>
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode();

	/**
	 * Checks if this completion proposal edit has the same kind and semantics as the parameter.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);

	/**
	 * Checks if the ranges specified by the argument proposal edits overlap.
	 * 
	 * @param r1
	 *            The first proposal edit.
	 * @param r2
	 *            The second proposal edit.
	 * @return <code>true</code> if the ranges overlap.
	 */
	public static boolean overlaps(CompletionProposalEdit r1, CompletionProposalEdit r2) {
		int r1o = r1.getOffset();
		int r2o = r2.getOffset();
		int r1l = r1.getLength();
		int r2l = r2.getLength();
		int r1e = r1o + r1l;
		int r2e = r2o + r2l;
		if (r1e <= r2o) {
			//r1 ends before r2 starts
			return false;
		}
		if (r2e <= r1o) {
			//r2 ends before r1 starts
			return false;
		}
		if (r1o >= r2e) {
			//r1 starts after r2 ends
			return false;
		}
		if (r2o >= r1e) {
			//r2 starts after r1 ends
			return false;
		}

		return true;
	}
}
