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
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;
import java.util.Set;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.StringUtils;

/**
 * Exception reporting that a build script evaluation failed due to some syntactic or semantic error.
 * <p>
 * The reasons of the parsing failure can be retrieved from {@link #getReasons()}.
 */
public class ScriptParsingFailedException extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * Class for containing the location and the reason of the script parsing failure.
	 */
	public static class Reason implements Externalizable {
		//XXX could make this class comparable
		private static final long serialVersionUID = 1L;

		private ScriptPosition position;
		private String explanation;

		/**
		 * For {@link Externalizable}.
		 */
		public Reason() {
		}

		/**
		 * Creates a new instance intializing the fields.
		 * 
		 * @param position
		 *            The position related to the reason of the failure.
		 * @param explanation
		 *            The explanation for the error. Can provide general message about the error, and describe
		 *            mitigation.
		 * @throws NullPointerException
		 *             If position is <code>null</code>.
		 */
		public Reason(ScriptPosition position, String explanation) throws NullPointerException {
			Objects.requireNonNull(position, "position");
			this.position = position;
			this.explanation = explanation;
		}

		/**
		 * Gets the positional information for the script parsing failure.
		 * 
		 * @return The position.
		 */
		public ScriptPosition getPosition() {
			return position;
		}

		/**
		 * Gets the explanation about the reasons of failure.
		 * <p>
		 * Can provide general message about the error, and describe mitigation.
		 * 
		 * @return The explanation.
		 */
		public String getExplanation() {
			return explanation;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(position);
			out.writeObject(explanation);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			position = (ScriptPosition) in.readObject();
			explanation = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((explanation == null) ? 0 : explanation.hashCode());
			result = prime * result + ((position == null) ? 0 : position.hashCode());
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
			Reason other = (Reason) obj;
			if (explanation == null) {
				if (other.explanation != null)
					return false;
			} else if (!explanation.equals(other.explanation))
				return false;
			if (position == null) {
				if (other.position != null)
					return false;
			} else if (!position.equals(other.position))
				return false;
			return true;
		}
	}

	private Set<Reason> reasons;

	/**
	 * Creates a new instance.
	 * 
	 * @param reasons
	 *            The reasons for parsing failure.
	 */
	public ScriptParsingFailedException(Set<Reason> reasons) {
		this.reasons = ImmutableUtils.makeImmutableNavigableSet(reasons, ScriptParsingFailedException::compareReasons);
	}

	/**
	 * Creates a new instance.
	 * 
	 * @param message
	 *            The detail message.
	 * @param cause
	 *            The cause.
	 * @param reasons
	 *            The reasons for parsing failure.
	 * @see Exception#Exception(String, Throwable)
	 */
	public ScriptParsingFailedException(String message, Throwable cause, Set<Reason> reasons) {
		super(message, cause);
		this.reasons = ImmutableUtils.makeImmutableNavigableSet(reasons, ScriptParsingFailedException::compareReasons);
	}

	/**
	 * Creates a new instance.
	 * 
	 * @param cause
	 *            The cause.
	 * @param reasons
	 *            The reasons for parsing failure.
	 * @see Exception#Exception( Throwable)
	 */
	public ScriptParsingFailedException(Throwable cause, Set<Reason> reasons) {
		super(cause);
		this.reasons = ImmutableUtils.makeImmutableNavigableSet(reasons, ScriptParsingFailedException::compareReasons);
	}

	/**
	 * Creates a new instance.
	 * 
	 * @param message
	 *            The detail message.
	 * @param reasons
	 *            The reasons for parsing failure.
	 * @see Exception#Exception(String)
	 */
	public ScriptParsingFailedException(String message, Set<Reason> reasons) {
		super(message);
		this.reasons = ImmutableUtils.makeImmutableNavigableSet(reasons, ScriptParsingFailedException::compareReasons);
	}

	/**
	 * Gets the reasons that caused the script parsing to fail.
	 * 
	 * @return An unmodifiable set of reasons.
	 */
	public Set<Reason> getReasons() {
		return reasons;
	}

	private static int compareReasons(Reason l, Reason r) {
		ScriptPosition lpos = l.getPosition();
		ScriptPosition rpos = r.getPosition();
		int cmp;
		cmp = Integer.compare(lpos.getFileOffset(), rpos.getFileOffset());
		if (cmp != 0) {
			return cmp;
		}
		cmp = Integer.compare(lpos.getLength(), rpos.getLength());
		if (cmp != 0) {
			return cmp;
		}
		cmp = Integer.compare(lpos.getLine(), rpos.getLine());
		if (cmp != 0) {
			return cmp;
		}
		cmp = Integer.compare(lpos.getLinePosition(), rpos.getLinePosition());
		if (cmp != 0) {
			return cmp;
		}
		cmp = StringUtils.compareStringsNullFirst(l.getExplanation(), r.getExplanation());
		if (cmp != 0) {
			return cmp;
		}
		return 0;
	}
}
