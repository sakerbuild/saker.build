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
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.FlattenedToken;
import sipka.syntax.parser.model.statement.Statement;

public class StatementReturningFlattenedStatementVisitor implements FlattenedStatementVisitor<Statement> {
	public static final StatementReturningFlattenedStatementVisitor INSTANCE = new StatementReturningFlattenedStatementVisitor();

	@Override
	public Statement visitMissing(Statement expplaceholderstm) {
		return expplaceholderstm;
	}

	@Override
	public Statement visitStringLiteral(FlattenedToken token) {
		return token.getStatement();
	}

	@Override
	public Statement visitLiteral(FlattenedToken token) {
		return token.getStatement();
	}

	@Override
	public Statement visitParentheses(FlattenedToken token) {
		return token.getStatement();
	}

	@Override
	public Statement visitList(FlattenedToken token) {
		return token.getStatement();
	}

	@Override
	public Statement visitMap(FlattenedToken token) {
		return token.getStatement();
	}

	@Override
	public Statement visitForeach(FlattenedToken token) {
		return token.getStatement();
	}

	@Override
	public Statement visitTask(FlattenedToken token) {
		return token.getStatement();
	}

	@Override
	public Statement visitDereference(FlattenedToken token, List<? extends FlattenedToken> subject) {
		return token.getStatement();
	}

	@Override
	public Statement visitUnary(FlattenedToken token, List<? extends FlattenedToken> subject) {
		return token.getStatement();
	}

	@Override
	public Statement visitSubscript(FlattenedToken token, List<? extends FlattenedToken> subject) {
		return token.getStatement();
	}

	@Override
	public Statement visitAssignment(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return token.getStatement();
	}

	@Override
	public Statement visitAddOp(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return token.getStatement();
	}

	@Override
	public Statement visitMultiplyOp(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return token.getStatement();
	}

	@Override
	public Statement visitEqualityOp(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return token.getStatement();
	}

	@Override
	public Statement visitComparisonOp(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return token.getStatement();
	}

	@Override
	public Statement visitShiftOp(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return token.getStatement();
	}

	@Override
	public Statement visitBitOp(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return token.getStatement();
	}

	@Override
	public Statement visitBoolOp(FlattenedToken token, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return token.getStatement();
	}

	@Override
	public Statement visitTernary(FlattenedToken token, List<? extends FlattenedToken> condition,
			List<? extends FlattenedToken> falseres) {
		return token.getStatement();
	}

}
