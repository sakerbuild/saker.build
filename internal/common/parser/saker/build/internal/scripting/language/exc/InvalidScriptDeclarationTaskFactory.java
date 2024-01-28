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
import java.util.Map;
import java.util.Objects;

import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.scripting.ScriptPosition;
import saker.build.task.TaskContext;

public class InvalidScriptDeclarationTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	private String message;

	/**
	 * For {@link Externalizable}.
	 */
	public InvalidScriptDeclarationTaskFactory() {
	}

	public InvalidScriptDeclarationTaskFactory(String message, ScriptPosition scriptpositionkey) {
		this.message = message;
		setScriptPositionKey(scriptpositionkey);
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		taskcontext.abortExecution(new InvalidScriptDeclarationException(message));
		return null;
	}

	@Override
	protected boolean isShort() {
		return true;
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return this;
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(message);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
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
		InvalidScriptDeclarationTaskFactory other = (InvalidScriptDeclarationTaskFactory) obj;
		if (!Objects.equals(this.message, other.message)) {
			return false;
		}
		//include the script position key as well, because the exception should be unique for a script location, even if they have the same message
		if (!Objects.equals(this.getScriptPositionKey(), other.getScriptPositionKey())) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (message != null ? "message=" + message : "") + "]";
	}

}
