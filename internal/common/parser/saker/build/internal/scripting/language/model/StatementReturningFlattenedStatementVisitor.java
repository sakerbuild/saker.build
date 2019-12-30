package saker.build.internal.scripting.language.model;

import java.util.List;

import saker.build.internal.scripting.language.FlattenedStatementVisitor;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.FlattenedToken;
import sipka.syntax.parser.model.statement.Statement;

public class StatementReturningFlattenedStatementVisitor implements FlattenedStatementVisitor<Statement> {
	public static final StatementReturningFlattenedStatementVisitor INSTANCE = new StatementReturningFlattenedStatementVisitor();

	@Override
	public Statement visitMissing(Statement expplaceholderstm) {
		return expplaceholderstm;
	}

	@Override
	public Statement visitStringLiteral(Statement stm) {
		return stm;
	}

	@Override
	public Statement visitLiteral(Statement stm) {
		return stm;
	}

	@Override
	public Statement visitParentheses(Statement stm) {
		return stm;
	}

	@Override
	public Statement visitList(Statement stm) {
		return stm;
	}

	@Override
	public Statement visitMap(Statement stm) {
		return stm;
	}

	@Override
	public Statement visitForeach(Statement stm) {
		return stm;
	}

	@Override
	public Statement visitTask(Statement stm) {
		return stm;
	}

	@Override
	public Statement visitDereference(Statement stm, List<? extends FlattenedToken> subject) {
		return stm;
	}

	@Override
	public Statement visitUnary(Statement stm, List<? extends FlattenedToken> subject) {
		return stm;
	}

	@Override
	public Statement visitSubscript(Statement stm, List<? extends FlattenedToken> subject) {
		return stm;
	}

	@Override
	public Statement visitAssignment(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return stm;
	}

	@Override
	public Statement visitAddOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return stm;
	}

	@Override
	public Statement visitMultiplyOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return stm;
	}

	@Override
	public Statement visitEqualityOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return stm;
	}

	@Override
	public Statement visitComparisonOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return stm;
	}

	@Override
	public Statement visitShiftOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return stm;
	}

	@Override
	public Statement visitBitOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return stm;
	}

	@Override
	public Statement visitBoolOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return stm;
	}

	@Override
	public Statement visitTernary(Statement stm, List<? extends FlattenedToken> condition,
			List<? extends FlattenedToken> falseres) {
		return stm;
	}

}
