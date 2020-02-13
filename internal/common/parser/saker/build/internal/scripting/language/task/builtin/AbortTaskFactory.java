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
import java.util.Set;

import saker.build.internal.scripting.language.exc.BuildAbortedException;
import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerScriptTaskUtils;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.TaskInvocationSakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.runtime.execution.SakerLog;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.io.IOUtils;

public class AbortTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	private SakerTaskFactory messageTask;

	/**
	 * For {@link Externalizable}.
	 */
	public AbortTaskFactory() {
	}

	public AbortTaskFactory(SakerTaskFactory message) {
		this.messageTask = message;
	}

	@Override
	public Set<String> getCapabilities() {
		//we have the same capabilities as the message task
		//if the message is short, we can be short as well.
		if (messageTask == null) {
			return SakerScriptTaskUtils.CAPABILITIES_SHORT_TASK;
		}
		return messageTask.getCapabilities();
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		taskcontext.setStandardOutDisplayIdentifier(TaskInvocationSakerTaskFactory.TASKNAME_ABORT);
		if (messageTask == null) {
			taskcontext.abortExecution(new BuildAbortedException());
		} else {
			//add the evaluation exception of the message as suppressed to the thrown exception
			//not the cause, as the cause is the abortion request, and the abortion was not caused
			//    by the message evaluation failure
			Exception suppress = null;
			String messagestr = null;
			TaskIdentifier messageid = messageTask
					.createSubTaskIdentifier((SakerScriptTaskIdentifier) taskcontext.getTaskId());
			try {
				messagestr = Objects.toString(runForResult(taskcontext, messageid, messageTask).toResult(taskcontext),
						null);
			} catch (Exception e) {
				suppress = new OperandExecutionException(
						"Failed to evaluate " + TaskInvocationSakerTaskFactory.TASKNAME_ABORT + "() message.",
						messageid);
			}
			if (messagestr != null) {
				SakerLog.error().taskScriptPosition(taskcontext).println(messagestr);
			}
			Throwable abortexc = messagestr == null ? new BuildAbortedException()
					: new BuildAbortedException(messagestr);
			IOUtils.addExc(abortexc, suppress);
			taskcontext.abortExecution(abortexc);
		}
		return null;
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new AbortTaskFactory(cloneHelper(taskfactoryreplacements, messageTask));
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
		AbortTaskFactory other = (AbortTaskFactory) obj;
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
