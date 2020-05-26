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
	public default R visitMissing(Statement expplaceholderstm) {
		return null;
	}

	public default R visitStringLiteral(FlattenedToken stm) {
		return null;
	}

	public default R visitLiteral(FlattenedToken stm) {
		return null;
	}

	public default R visitParentheses(FlattenedToken stm) {
		return null;
	}

	public default R visitList(FlattenedToken stm) {
		return null;
	}

	public default R visitMap(FlattenedToken stm) {
		return null;
	}

	public default R visitForeach(FlattenedToken stm) {
		return null;
	}

	public default R visitTask(FlattenedToken stm) {
		return null;
	}

	public default R visitDereference(FlattenedToken stm, List<? extends FlattenedToken> subject) {
		return null;
	}

	public default R visitUnary(FlattenedToken stm, List<? extends FlattenedToken> subject) {
		return null;
	}

	public default R visitSubscript(FlattenedToken stm, List<? extends FlattenedToken> subject) {
		return null;
	}

	public default R visitAssignment(FlattenedToken stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	public default R visitAddOp(FlattenedToken stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	public default R visitMultiplyOp(FlattenedToken stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	public default R visitEqualityOp(FlattenedToken stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	public default R visitComparisonOp(FlattenedToken stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	public default R visitShiftOp(FlattenedToken stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	public default R visitBitOp(FlattenedToken stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	public default R visitBoolOp(FlattenedToken stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		return null;
	}

	public default R visitTernary(FlattenedToken stm, List<? extends FlattenedToken> condition,
			List<? extends FlattenedToken> falseres) {
		return null;
	}
}
