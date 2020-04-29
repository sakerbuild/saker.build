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
	public Object visitLiteral(Statement stm) {
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
	public Object visitParentheses(Statement stm) {
		return SakerScriptTargetConfigurationReader.visitParenthesesExpressionStatement(stm, this);
	}

	@Override
	public Object visitList(Statement stm) {
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
	public Object visitAssignment(Statement stm, List<? extends FlattenedToken> left,
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
