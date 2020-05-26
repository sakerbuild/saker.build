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

import saker.build.internal.scripting.language.FlattenedStatementVisitor;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.FlattenedToken;

public class VariableTaskUsageDeterminingFlattenedStatementVisitor
		implements FlattenedStatementVisitor<VariableTaskUsage> {
	public static final VariableTaskUsageDeterminingFlattenedStatementVisitor INSTANCE = new VariableTaskUsageDeterminingFlattenedStatementVisitor();

	@Override
	public VariableTaskUsage visitParentheses(FlattenedToken token) {
		return SakerScriptTargetConfigurationReader.visitParenthesesExpressionStatement(token.getStatement(), this);
	}

	@Override
	public VariableTaskUsage visitTask(FlattenedToken token) {
		return SakerParsedModel.getVariableTaskUsageFromTaskStatement(token.getStatement());
	}

	@Override
	public VariableTaskUsage visitDereference(FlattenedToken token, List<? extends FlattenedToken> subject) {
		String varname = SakerParsedModel.getDereferenceStatementLiteralVariableName(token.getStatement());
		if (varname != null) {
			return VariableTaskUsage.var(varname);
		}
		return null;
	}

}
