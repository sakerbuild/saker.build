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

import saker.build.thirdparty.saker.util.ObjectUtils;

public class NestRepositoryFactoryServiceEnumeratorIDEProperty implements ClassPathServiceEnumeratorIDEProperty {
	public static final NestRepositoryFactoryServiceEnumeratorIDEProperty INSTANCE = new NestRepositoryFactoryServiceEnumeratorIDEProperty();

	public NestRepositoryFactoryServiceEnumeratorIDEProperty() {
	}

	@Override
	public <R, P> R accept(Visitor<R, P> visitor, P param) {
		return visitor.visit(this, param);
	}

	@Override
	public int hashCode() {
		return getClass().getName().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + "]";
	}
}
