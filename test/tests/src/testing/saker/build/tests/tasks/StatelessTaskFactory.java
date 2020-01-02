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
package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;

public abstract class StatelessTaskFactory<R> implements TaskFactory<R>, Externalizable {
	private static final long serialVersionUID = 1L;

	@Override
	public final void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public final void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	@Override
	public final int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public final boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

}
