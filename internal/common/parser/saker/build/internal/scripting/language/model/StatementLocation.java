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