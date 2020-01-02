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
package saker.build.task.delta.impl;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.delta.DeltaType;
import saker.build.task.delta.TaskChangeDelta;
import saker.build.thirdparty.saker.util.ObjectUtils;

public final class TaskChangeDeltaImpl implements TaskChangeDelta, Externalizable {
	private static final long serialVersionUID = 1L;

	public static final TaskChangeDeltaImpl INSTANCE = new TaskChangeDeltaImpl();

	/**
	 * For {@link Externalizable}.
	 */
	public TaskChangeDeltaImpl() {
	}

	@Override
	public DeltaType getType() {
		return DeltaType.TASK_CHANGE;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + "]";
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}
}
