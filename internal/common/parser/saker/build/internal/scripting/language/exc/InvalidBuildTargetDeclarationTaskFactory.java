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
package saker.build.internal.scripting.language.exc;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.BuildTargetTask;
import saker.build.task.BuildTargetTaskFactory;
import saker.build.task.BuildTargetTaskResult;
import saker.build.task.TaskContext;

public class InvalidBuildTargetDeclarationTaskFactory
		implements BuildTargetTaskFactory, BuildTargetTask, Externalizable {
	private static final long serialVersionUID = 1L;

	private String message;

	/**
	 * For {@link Externalizable}.
	 */
	public InvalidBuildTargetDeclarationTaskFactory() {
	}

	public InvalidBuildTargetDeclarationTaskFactory(String message) {
		this.message = message;
	}

	@Override
	public BuildTargetTask createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public NavigableSet<String> getTargetInputParameterNames() {
		// null is fine
		return null;
	}

	@Override
	public BuildTargetTaskResult run(TaskContext taskcontext) throws Exception {
		taskcontext.abortExecution(new InvalidScriptDeclarationException(message));
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(message);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		message = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
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
		InvalidBuildTargetDeclarationTaskFactory other = (InvalidBuildTargetDeclarationTaskFactory) obj;
		if (message == null) {
			if (other.message != null)
				return false;
		} else if (!message.equals(other.message))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (message != null ? "message=" + message : "") + "]";
	}

}
