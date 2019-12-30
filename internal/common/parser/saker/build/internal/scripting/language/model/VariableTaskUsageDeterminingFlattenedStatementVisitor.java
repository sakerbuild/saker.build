package saker.build.internal.scripting.language.model;

import java.util.List;

import saker.build.internal.scripting.language.FlattenedStatementVisitor;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.FlattenedToken;
import sipka.syntax.parser.model.statement.Statement;

public class VariableTaskUsageDeterminingFlattenedStatementVisitor
		implements FlattenedStatementVisitor<VariableTaskUsage> {
	public static final VariableTaskUsageDeterminingFlattenedStatementVisitor INSTANCE = new VariableTaskUsageDeterminingFlattenedStatementVisitor();

	@Override
	public VariableTaskUsage visitMissing(Statement expplaceholderstm) {
		return null;
	}

	@Override
	public VariableTaskUsage visitStringLiteral(Statement stm) {
		return null;
	}

	@Override
	public VariableTaskUsage visitLiteral(Statement stm) {
		return null;
	}

	@Override
	public VariableTaskUsage visitParentheses(Statement stm) {
		return SakerScriptTargetConfigurationReader.visitParenthesesExpressionStatement(stm, this);
	}

	@Override
	public VariableTaskUsage visitList(Statement stm) {
		return null;
	}

	@Override
	public VariableTaskUsage visitMap(Statement stm) {
		return null;
	}

	@Override
	public VariableTaskUsage visitForeach(Statement stm) {
		return null;
	}

	@Override
	public VariableTaskUsage visitTask(Statement stm) {
		return SakerParsedModel.getVariableTaskUsageFromTaskStatement(stm);
	}

	@Override
	public VariableTaskUsage visitDereference(Statement stm, List<? extends FlattenedToken> subject) {
		String varname = SakerParsedModel.getDereferenceStatementLiteralVariableName(stm);
		if (varname != null) {
			return VariableTaskUsage.var(varname);
		}
		return null;
	}

	@Override
	public VariableTaskUsage visitUnary(Statement stm, List<? extends FlattenedToken> subject) {
		return null;
	}

	@Override
	public VariableTaskUsage visitSubscript(Statement stm, List<? extends FlattenedToken> subject) {
		return null;
	}

	@Override
	public VariableTaskUsage visitAssignment(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public VariableTaskUsage visitAddOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public VariableTaskUsage visitMultiplyOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public VariableTaskUsage visitEqualityOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public VariableTaskUsage visitComparisonOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public VariableTaskUsage visitShiftOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public VariableTaskUsage visitBitOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public VariableTaskUsage visitBoolOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	@Override
	public VariableTaskUsage visitTernary(Statement stm, List<? extends FlattenedToken> condition,
			List<? extends FlattenedToken> falseres) {
		return null;
	}

}
