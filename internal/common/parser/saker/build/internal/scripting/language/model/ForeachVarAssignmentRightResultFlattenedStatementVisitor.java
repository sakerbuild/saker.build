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
	public Void visitAssignment(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		if (varName.equals(SakerParsedModel.getAssignmentLeftOperandDereferenceLiteralName(left))) {
			Statement rightstm = SakerScriptTargetConfigurationReader.visitFlattenedStatements(right,
					StatementReturningFlattenedStatementVisitor.INSTANCE);
			associationResults.add(new TypeAssociation(new StatementLocation(derived, rightstm, null)));
		}
		return super.visitAssignment(token, left, right);
	}
}
