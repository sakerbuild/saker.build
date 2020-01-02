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

import java.util.List;

import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.FlattenedToken;
import sipka.syntax.parser.model.statement.Statement;

public class SubscriptSubjectFinderFlattenedStatementVisitor extends BaseFlattenedStatementVisitor<Void> {
	public static class StatementFoundAbortException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		private final Statement result;

		public StatementFoundAbortException(Statement result) {
			super(null, null, false, false);
			this.result = result;
		}

		public Statement getResult() {
			return result;
		}
	}

	private final Statement subscriptStatement;

	public SubscriptSubjectFinderFlattenedStatementVisitor(Statement subscriptStatement) {
		this.subscriptStatement = subscriptStatement;
	}

	@Override
	public Void visitSubscript(Statement stm, List<? extends FlattenedToken> subject) {
		if (stm == subscriptStatement) {
			Statement subjectstm = SakerScriptTargetConfigurationReader.visitFlattenedStatements(subject,
					StatementReturningFlattenedStatementVisitor.INSTANCE);
			throw new StatementFoundAbortException(subjectstm);
		}
		return super.visitSubscript(stm, subject);
	}
}
