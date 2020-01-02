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
package testing.saker.build.tests.tasks.factories;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;

public class FailerTaskFactory implements TaskFactory<Void>, Task<Void>, Externalizable {
	private static final long serialVersionUID = 1L;

	private String message;
	private Class<? extends Exception> exceptionClass;

	/**
	 * For {@link Externalizable}.
	 */
	public FailerTaskFactory() {
	}

	public FailerTaskFactory(Class<? extends Exception> exceptionClass, String message) {
		this.message = message;
		this.exceptionClass = exceptionClass;
	}

	@Override
	public Void run(TaskContext taskcontext) throws Exception {
		throw exceptionClass.getConstructor(String.class).newInstance(message);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(message);
		out.writeObject(exceptionClass);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		message = (String) in.readObject();
		exceptionClass = (Class<? extends Exception>) in.readObject();
	}

	@Override
	public Task<? extends Void> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((exceptionClass == null) ? 0 : exceptionClass.hashCode());
		result = prime * result + ((message == null) ? 0 : message.hashCode());
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
		FailerTaskFactory other = (FailerTaskFactory) obj;
		if (exceptionClass == null) {
			if (other.exceptionClass != null)
				return false;
		} else if (!exceptionClass.equals(other.exceptionClass))
			return false;
		if (message == null) {
			if (other.message != null)
				return false;
		} else if (!message.equals(other.message))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "FailerTaskFactory[" + (message != null ? "message=" + message + ", " : "")
				+ (exceptionClass != null ? "exceptionClass=" + exceptionClass : "") + "]";
	}

}
