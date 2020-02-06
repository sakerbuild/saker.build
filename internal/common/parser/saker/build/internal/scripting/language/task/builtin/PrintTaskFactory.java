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
package saker.build.internal.scripting.language.task.builtin;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.Objects;

import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.TaskInvocationSakerTaskFactory;
import saker.build.internal.scripting.language.task.result.NoSakerTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;

public class PrintTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	private SakerTaskFactory messageTask;

	/**
	 * For {@link Externalizable}.
	 */
	public PrintTaskFactory() {
	}

	public PrintTaskFactory(SakerTaskFactory message) {
		this.messageTask = message;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		taskcontext.setStandardOutDisplayIdentifier(TaskInvocationSakerTaskFactory.TASKNAME_PRINT);
		//print "null" if the message is null
		TaskIdentifier messagetaskid = messageTask
				.createSubTaskIdentifier((SakerScriptTaskIdentifier) taskcontext.getTaskId());
		Object taskresult = taskcontext.getTaskUtilities().runTaskResult(messagetaskid, messageTask)
				.toResult(taskcontext);
		String messagestr = Objects.toString(taskresult);
		taskcontext.println(Objects.toString(messagestr));
		return new NoSakerTaskResult(taskcontext.getTaskId());
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new PrintTaskFactory(cloneHelper(taskfactoryreplacements, messageTask));
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(messageTask);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		messageTask = (SakerTaskFactory) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((messageTask == null) ? 0 : messageTask.hashCode());
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
		PrintTaskFactory other = (PrintTaskFactory) obj;
		if (messageTask == null) {
			if (other.messageTask != null)
				return false;
		} else if (!messageTask.equals(other.messageTask))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (messageTask != null ? "message=" + messageTask : "") + "]";
	}

}
