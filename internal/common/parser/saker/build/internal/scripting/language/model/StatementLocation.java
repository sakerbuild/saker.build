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
package saker.build.internal.scripting.language.model;

import sipka.syntax.parser.model.statement.Statement;

public class StatementLocation {
	protected final DerivedData derived;
	protected final Statement statement;
	protected transient Iterable<? extends Statement> parentContexts;

	public StatementLocation(DerivedData derived, Statement statement, Iterable<? extends Statement> parentContexts) {
		this.derived = derived;
		this.statement = statement;
		this.parentContexts = parentContexts;
	}

	public DerivedData getDerived() {
		return derived;
	}

	public Statement getStatement() {
		return statement;
	}

	public Iterable<? extends Statement> getParentContexts() {
		return parentContexts;
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(statement);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		StatementLocation other = (StatementLocation) obj;
		if (derived != other.derived) {
			return false;
		}
		if (statement != other.statement) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "StatementLocation[" + statement + "]";
	}
}