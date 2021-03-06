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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.TaskInvocationSakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerListTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskObjectSakerTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class SequenceTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	private List<SakerTaskFactory> elements;

	/**
	 * For {@link Externalizable}.
	 */
	public SequenceTaskFactory() {
	}

	public SequenceTaskFactory(List<SakerTaskFactory> elements) {
		this.elements = elements;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		taskcontext.setStandardOutDisplayIdentifier(TaskInvocationSakerTaskFactory.TASKNAME_SEQUENCE);
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		List<StructuredTaskResult> resulttids = new ArrayList<>(elements.size());
		for (SakerTaskFactory taskfactory : elements) {
			TaskIdentifier subtaskid = taskfactory.createSubTaskIdentifier(thistaskid);
			runForResult(taskcontext, subtaskid, taskfactory).toResult(taskcontext);
			resulttids.add(new SakerTaskObjectSakerTaskResult(subtaskid));
		}
		SakerListTaskResult result = new SakerListTaskResult(resulttids);
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new SequenceTaskFactory(cloneHelper(taskfactoryreplacements, elements));
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		SerialUtils.writeExternalCollection(out, elements);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		elements = SerialUtils.readExternalImmutableList(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((elements == null) ? 0 : elements.hashCode());
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
		SequenceTaskFactory other = (SequenceTaskFactory) obj;
		if (elements == null) {
			if (other.elements != null)
				return false;
		} else if (!elements.equals(other.elements))
			return false;
		return true;
	}

}
