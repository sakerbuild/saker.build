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
package saker.build.ide.support.properties;

public interface ClassPathLocationIDEProperty {
	public interface Visitor<R, P> {
		public R visit(JarClassPathLocationIDEProperty property, P param);

		public R visit(HttpUrlJarClassPathLocationIDEProperty property, P param);

		public R visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, P param);

		public R visit(NestRepositoryClassPathLocationIDEProperty property, P param);
	}

	public <R, P> R accept(Visitor<R, P> visitor, P param);

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
