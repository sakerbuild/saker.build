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
package saker.build.util.property;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * Singleton {@link ExecutionProperty} implementation that queries the current build time milliseconds of the build
 * execution.
 * 
 * @see ExecutionContext#getBuildTimeMillis()
 * @see #INSTANCE
 */
@PublicApi
public final class BuildTimeExecutionProperty implements ExecutionProperty<Long>, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The singleton instance.
	 */
	public static final BuildTimeExecutionProperty INSTANCE = new BuildTimeExecutionProperty();

	/**
	 * For {@link Externalizable}.
	 * 
	 * @see #INSTANCE
	 */
	public BuildTimeExecutionProperty() {
	}

	@Override
	public Long getCurrentValue(ExecutionContext executioncontext) {
		return executioncontext.getBuildTimeMillis();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
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
		return getClass().getSimpleName() + "[]";
	}
}
