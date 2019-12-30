package saker.build.internal.scripting.language.model;

import java.util.Collection;
import java.util.List;

import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.FlattenedToken;
import saker.build.internal.scripting.language.model.ScriptModelInformationAnalyzer.TypeAssociation;
import sipka.syntax.parser.model.statement.Statement;

public class AssignmentRightResultFlattenedStatementVisitor extends BaseFlattenedStatementVisitor<Void> {
	private final DerivedData derived;
	private final VariableTaskUsage varTask;
	private final Collection<TypeAssociation> associationResults;

	public AssignmentRightResultFlattenedStatementVisitor(DerivedData derived, VariableTaskUsage varTask,
			Collection<TypeAssociation> associationResults) {
		this.derived = derived;
		this.varTask = varTask;
		this.associationResults = associationResults;
	}

	@Override
	public Void visitAssignment(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		if (varTask.equals(SakerParsedModel.getAssignmentLeftOperandVariableTaskUsage(left))) {
			Statement rightstm = SakerScriptTargetConfigurationReader.visitFlattenedStatements(right,
					StatementReturningFlattenedStatementVisitor.INSTANCE);
			associationResults.add(new TypeAssociation(new StatementLocation(derived, rightstm, null)));
		}
		return super.visitAssignment(stm, left, right);
	}
}
