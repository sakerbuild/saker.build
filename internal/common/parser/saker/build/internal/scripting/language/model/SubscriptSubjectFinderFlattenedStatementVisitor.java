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
