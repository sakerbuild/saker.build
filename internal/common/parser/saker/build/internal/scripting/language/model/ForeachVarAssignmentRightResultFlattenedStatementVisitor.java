package saker.build.internal.scripting.language.model;

import java.util.Collection;
import java.util.List;

import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.FlattenedToken;
import saker.build.internal.scripting.language.model.ScriptModelInformationAnalyzer.TypeAssociation;
import sipka.syntax.parser.model.statement.Statement;

public class ForeachVarAssignmentRightResultFlattenedStatementVisitor extends BaseFlattenedStatementVisitor<Void> {
	private final DerivedData derived;
	private final String varName;
	private final Collection<TypeAssociation> associationResults;

	public ForeachVarAssignmentRightResultFlattenedStatementVisitor(DerivedData derived, String varName,
			Collection<TypeAssociation> associationResults) {
		this.derived = derived;
		this.varName = varName;
		this.associationResults = associationResults;
	}

	@Override
	public Void visitAssignment(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		if (varName.equals(SakerParsedModel.getAssignmentLeftOperandDereferenceLiteralName(left))) {
			Statement rightstm = SakerScriptTargetConfigurationReader.visitFlattenedStatements(right,
					StatementReturningFlattenedStatementVisitor.INSTANCE);
			associationResults.add(new TypeAssociation(new StatementLocation(derived, rightstm, null)));
		}
		return super.visitAssignment(stm, left, right);
	}
}
