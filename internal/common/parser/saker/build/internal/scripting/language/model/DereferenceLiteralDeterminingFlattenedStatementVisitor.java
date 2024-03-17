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

public class DereferenceLiteralDeterminingFlattenedStatementVisitor implements FlattenedStatementVisitor<String> {
	public static final DereferenceLiteralDeterminingFlattenedStatementVisitor INSTANCE = new DereferenceLiteralDeterminingFlattenedStatementVisitor();

	@Override
	public String visitDereference(FlattenedToken token, List<? extends FlattenedToken> subject) {
		return SakerParsedModel.getDereferenceStatementLiteralVariableName(token.getStatement());
	}
}
