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
package saker.build.internal.scripting.language.task.result;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.SupplierTaskResultDependencyHandle;

public interface SakerTaskResult extends Externalizable, StructuredTaskResult {
	public static final long serialVersionUID = 1L;

	public Object get(TaskResultResolver results);

	/**
	 * Gets a dependency handle that is associated with the object returned by {@link #get(TaskResultResolver)}.
	 * <p>
	 * Implementation should return the handle for the returned by {@link #get(TaskResultResolver)}.
	 * 
	 * @param results
	 *            The task result resolver.
	 * @param handleforthis
	 *            The dependency handle which returned <code>this</code> from its
	 *            {@link TaskResultDependencyHandle#get()} method.
	 */
	public default TaskResultDependencyHandle getDependencyHandle(TaskResultResolver results,
			TaskResultDependencyHandle handleforthis) {
		return new SupplierTaskResultDependencyHandle(() -> get(results));
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException;

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException;

}
