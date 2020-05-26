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

import java.util.ArrayList;
import java.util.List;

import saker.build.internal.scripting.language.FlattenedStatementVisitor;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.FlattenedToken;
import sipka.syntax.parser.model.statement.Statement;
import sipka.syntax.parser.util.Pair;

public class ExpressionValueFlattenedStatementVisitor implements FlattenedStatementVisitor<Object> {
	public static final ExpressionValueFlattenedStatementVisitor INSTANCE = new ExpressionValueFlattenedStatementVisitor();

	//TODO implement other methods

	@Override
	public String visitStringLiteral(FlattenedToken token) {
		Statement stm = token.getStatement();
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
	public Object visitLiteral(FlattenedToken token) {
		Statement stm = token.getStatement();
		String lit = stm.firstValue("literal_content");
		if ("null".equalsIgnoreCase(lit)) {
			return null;
		}
		if ("true".equalsIgnoreCase(lit)) {
			return true;
		}
		if ("false".equalsIgnoreCase(lit)) {
			return false;
		}
		return lit;
	}

	@Override
	public Object visitParentheses(FlattenedToken token) {
		Statement stm = token.getStatement();
		return SakerScriptTargetConfigurationReader.visitParenthesesExpressionStatement(stm, this);
	}

	@Override
	public Object visitList(FlattenedToken token) {
		Statement stm = token.getStatement();
		ArrayList<Object> result = new ArrayList<>();
		List<Statement> elements = stm.scopeTo("list_element");
		if (!elements.isEmpty()) {
			for (Statement elem : elements) {
				Statement elementexpression = elem.firstScope("expression");
				if (elementexpression == null) {
					//no content in this list element
					continue;
				}
				Object elemval = SakerScriptTargetConfigurationReader
						.visitFlattenExpressionStatements(elementexpression, this);
				if (elemval == null) {
					//check if the element expression represents the null, or if it is not constantizable
					List<Pair<String, Statement>> scopes = elementexpression.getScopes();
					if (scopes.size() == 1) {
						Pair<String, Statement> firstscope = scopes.get(0);
						if (firstscope.key.equals("literal")) {
							if ("null".equalsIgnoreCase(firstscope.value.firstValue("literal_content"))) {
								result.add(null);
							}
						}
					}
				} else {
					result.add(elemval);
				}
			}
		}
		return result;
	}

	@Override
	public Object visitAssignment(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
	}

}
