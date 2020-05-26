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
import sipka.syntax.parser.model.statement.Statement;

public class BaseFlattenedStatementVisitor<R> implements FlattenedStatementVisitor<R> {
	//TODO this visitor doesn't visit inner expressions. this might not be what the subclasses expect. verify it

	@Override
	public R visitMissing(Statement expplaceholderstm) {
		return null;
	}

	@Override
	public R visitStringLiteral(FlattenedToken token) {
		//inline expressions are base receiver expressions
		return null;
	}

	@Override
	public R visitLiteral(FlattenedToken token) {
		//inline expressions are base receiver expressions
		return null;
	}

	@Override
	public R visitParentheses(FlattenedToken token) {
		SakerScriptTargetConfigurationReader.visitParenthesesExpressionStatement(token.getStatement(), this);
		return null;
	}

	@Override
	public R visitList(FlattenedToken token) {
		Statement stm = token.getStatement();
		List<Statement> elements = stm.scopeTo("list_element");
		if (!elements.isEmpty()) {
			for (Statement elem : elements) {
				Statement elementexpression = elem.firstScope("expression");
				if (elementexpression == null) {
					//no content in this list element
					continue;
				}
				SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(elementexpression, this);
			}
		}
		return null;
	}

	@Override
	public R visitMap(FlattenedToken token) {
		Statement stm = token.getStatement();
		List<Statement> elements = stm.scopeTo("map_element");
		if (!elements.isEmpty()) {
			for (Statement elem : elements) {
				Statement keyscope = elem.firstScope("map_key");
				Statement keyexpression = keyscope.firstScope("expression");

				Statement valscope = elem.firstScope("map_val");
				Statement valexpression = valscope == null ? null : valscope.firstScope("expression");
				if (keyexpression != null) {
					SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(keyexpression, this);
				}
				if (valexpression != null) {
					SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(valexpression, this);
				}
			}

		}
		return null;
	}

	@Override
	public R visitForeach(FlattenedToken token) {
		//iterable is base receiver expression
		//foreach statements are base receiver expressions, there's nothing to visit in it
		return null;
	}

	@Override
	public R visitTask(FlattenedToken token) {
		// params and qualifiers are base receiver expressions
		return null;
	}

	@Override
	public R visitDereference(FlattenedToken token, List<? extends FlattenedToken> subject) {
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(subject, this);
		return null;
	}

	@Override
	public R visitUnary(FlattenedToken token, List<? extends FlattenedToken> subject) {
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(subject, this);
		return null;
	}

	@Override
	public R visitSubscript(FlattenedToken token, List<? extends FlattenedToken> subject) {
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(subject, this);
		return null;
	}

	@Override
	public R visitAssignment(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return null;
	}

	@Override
	public R visitAddOp(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return null;
	}

	@Override
	public R visitMultiplyOp(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return null;
	}

	@Override
	public R visitEqualityOp(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return null;
	}

	@Override
	public R visitComparisonOp(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return null;
	}

	@Override
	public R visitShiftOp(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return null;
	}

	@Override
	public R visitBitOp(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return null;
	}

	@Override
	public R visitBoolOp(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return null;
	}

	@Override
	public R visitTernary(FlattenedToken token, List<? extends FlattenedToken> condition,
			List<? extends FlattenedToken> falseres) {
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(condition, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(falseres, this);
		SakerScriptTargetConfigurationReader.visitTernaryTrueExpressionStatement(token, this);
		return null;
	}

}
