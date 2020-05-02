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
package testing.saker.build.tests;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskInvocationConfiguration;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

public class LiteralTaskFactory implements TaskFactory<Object>, Task<Object>, Externalizable {
	private static final long serialVersionUID = 1L;

	protected Object literal;

	/**
	 * For {@link Externalizable}.
	 */
	public LiteralTaskFactory() {
	}

	public LiteralTaskFactory(Object literal) {
		this.literal = literal;
	}

	public Object getLiteral() {
		return literal;
	}

	@Override
	public TaskInvocationConfiguration getInvocationConfiguration() {
		return TaskInvocationConfiguration.INSTANCE_SHORT_TASK;
	}

	@Override
	public Task<? extends Object> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public Object run(TaskContext taskcontext) throws Exception {
		Object result = literal;
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(literal);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		literal = in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((literal == null) ? 0 : literal.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LiteralTaskFactory other = (LiteralTaskFactory) obj;
		if (literal == null) {
			if (other.literal != null)
				return false;
		} else if (!literal.equals(other.literal))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + literal + "]";
	}

}