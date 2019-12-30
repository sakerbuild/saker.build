package saker.build.internal.scripting.language.model;

import java.util.List;

import saker.build.internal.scripting.language.FlattenedStatementVisitor;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.FlattenedToken;
import sipka.syntax.parser.model.statement.Statement;
import sipka.syntax.parser.util.Pair;

public class ExpressionValueFlattenedStatementVisitor implements FlattenedStatementVisitor<String> {
	public static final ExpressionValueFlattenedStatementVisitor INSTANCE = new ExpressionValueFlattenedStatementVisitor();

	@Override
	public String visitMissing(Statement expplaceholderstm) {
		return null;
	}

	@Override
	public String visitStringLiteral(Statement stm) {
		StringBuilder sb = new StringBuilder();
		for (Pair<String, Statement> scope : stm.getScopes()) {
			if (!"stringliteral_content".equals(scope.key)) {
				return null;
			}
			sb.append(scope.value.getValue());
		}
		return sb.toString();
	}

	@Override
	public String visitLiteral(Statement stm) {
		return stm.firstValue("literal_content");
	}

	@Override
	public String visitParentheses(Statement stm) {
		return SakerScriptTargetConfigurationReader.visitParenthesesExpressionStatement(stm, this);
	}

	@Override
	public String visitList(Statement stm) {
		return null;
	}

	@Override
	public String visitMap(Statement stm) {
		return null;
	}

	@Override
	public String visitForeach(Statement stm) {
		return null;
	}

	@Override
	public String visitTask(Statement stm) {
		return null;
	}

	@Override
	public String visitDereference(Statement stm, List<? extends FlattenedToken> subject) {
		return null;
	}

	@Override
	public String visitUnary(Statement stm, List<? extends FlattenedToken> subject) {
		return null;
	}

	@Override
	public String visitSubscript(Statement stm, List<? extends FlattenedToken> subject) {
		return null;
	}

	@Override
	public String visitAssignment(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
	}

	@Override
	public String visitAddOp(Statement stm, List<? extends FlattenedToken> left, List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public String visitMultiplyOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public String visitEqualityOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public String visitComparisonOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public String visitShiftOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public String visitBitOp(Statement stm, List<? extends FlattenedToken> left, List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public String visitBoolOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public String visitTernary(Statement stm, List<? extends FlattenedToken> condition,
			List<? extends FlattenedToken> falseres) {
		return null;
	}

}
