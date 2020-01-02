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
package saker.build.internal.scripting.language;

import java.util.List;

import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.FlattenedToken;
import sipka.syntax.parser.model.statement.Statement;

/**
 * @see SakerScriptTargetConfigurationReader#visitFlattenedStatements(List, FlattenedStatementVisitor)
 */
public interface FlattenedStatementVisitor<R> {
	public R visitMissing(Statement expplaceholderstm);

	public R visitStringLiteral(Statement stm);

	public R visitLiteral(Statement stm);

	public R visitParentheses(Statement stm);

	public R visitList(Statement stm);

	public R visitMap(Statement stm);

	public R visitForeach(Statement stm);

	public R visitTask(Statement stm);

	public R visitDereference(Statement stm, List<? extends FlattenedToken> subject);

	public R visitUnary(Statement stm, List<? extends FlattenedToken> subject);

	public R visitSubscript(Statement stm, List<? extends FlattenedToken> subject);

	public R visitAssignment(Statement stm, List<? extends FlattenedToken> left, List<? extends FlattenedToken> right);

	public R visitAddOp(Statement stm, List<? extends FlattenedToken> left, List<? extends FlattenedToken> right);

	public R visitMultiplyOp(Statement stm, List<? extends FlattenedToken> left, List<? extends FlattenedToken> right);

	public R visitEqualityOp(Statement stm, List<? extends FlattenedToken> left, List<? extends FlattenedToken> right);

	public R visitComparisonOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right);

	public R visitShiftOp(Statement stm, List<? extends FlattenedToken> left, List<? extends FlattenedToken> right);

	public R visitBitOp(Statement stm, List<? extends FlattenedToken> left, List<? extends FlattenedToken> right);

	public R visitBoolOp(Statement stm, List<? extends FlattenedToken> left, List<? extends FlattenedToken> right);

	public R visitTernary(Statement stm, List<? extends FlattenedToken> condition,
			List<? extends FlattenedToken> falseres);
}
