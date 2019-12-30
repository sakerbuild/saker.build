package saker.build.internal.scripting.language.model;

import java.util.List;

import saker.build.internal.scripting.language.FlattenedStatementVisitor;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.FlattenedToken;
import sipka.syntax.parser.model.statement.Statement;

public class DereferenceLiteralDeterminingFlattenedStatementVisitor implements FlattenedStatementVisitor<String> {
	public static final DereferenceLiteralDeterminingFlattenedStatementVisitor INSTANCE = new DereferenceLiteralDeterminingFlattenedStatementVisitor();

	@Override
	public String visitMissing(Statement expplaceholderstm) {
		return null;
	}

	@Override
	public String visitStringLiteral(Statement stm) {
		return null;
	}

	@Override
	public String visitLiteral(Statement stm) {
		return null;
	}

	@Override
	public String visitParentheses(Statement stm) {
		return null;
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
		return SakerParsedModel.getDereferenceStatementLiteralVariableName(stm);
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
		return null;
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
