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
package saker.build.internal.scripting.language.task.operators;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SubscriptSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

public class SubscriptTaskFactory extends SelfSakerTaskFactory {
	private static final Object MAP_DEFAULT_PLACEHOLDER = new Object();

	private static final long serialVersionUID = 1L;

	protected SakerTaskFactory subject;
	protected SakerTaskFactory index;

	public SubscriptTaskFactory() {
	}

	public SubscriptTaskFactory(SakerTaskFactory subject, SakerTaskFactory index) {
		this.subject = subject;
		this.index = index;
	}

	@Override
	protected boolean isShort() {
		//task is short because we don't wait for the subtask results
		return true;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) {
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		TaskIdentifier indextaskid = index.createSubTaskIdentifier(thistaskid);
		TaskIdentifier subjecttaskid = subject.createSubTaskIdentifier(thistaskid);

		taskcontext.getTaskUtilities().startTask(indextaskid, index);
		taskcontext.getTaskUtilities().startTask(subjecttaskid, subject);
		SubscriptSakerTaskResult result = new SubscriptSakerTaskResult(subjecttaskid, indextaskid);
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		SakerLiteralTaskFactory sub = subject.tryConstantize();
		if (sub == null) {
			return null;
		}
		SakerLiteralTaskFactory idx = index.tryConstantize();
		if (idx == null) {
			return null;
		}
		Object sv = sub.getValue();
		if (sv == null) {
			return null;
		}
		Object indexval = idx.getValue();
		if (indexval == null) {
			return null;
		}
		if (sv instanceof Map) {
			//constant maps are mapped by string keys
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) sv;
			String keystr = Objects.toString(indexval);
			Object gotval = map.getOrDefault(keystr, MAP_DEFAULT_PLACEHOLDER);
			if (gotval == MAP_DEFAULT_PLACEHOLDER) {
				return null;
			}
			return new SakerLiteralTaskFactory(gotval);
		}
		if (sv instanceof List) {
			if (!(indexval instanceof Number)) {
				return null;
			}
			return new SakerLiteralTaskFactory(((List<?>) sv).get(((Number) indexval).intValue()));
		}
		return null;
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new SubscriptTaskFactory(cloneHelper(taskfactoryreplacements, subject),
				cloneHelper(taskfactoryreplacements, index));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((index == null) ? 0 : index.hashCode());
		result = prime * result + ((subject == null) ? 0 : subject.hashCode());
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
		SubscriptTaskFactory other = (SubscriptTaskFactory) obj;
		if (index == null) {
			if (other.index != null)
				return false;
		} else if (!index.equals(other.index))
			return false;
		if (subject == null) {
			if (other.subject != null)
				return false;
		} else if (!subject.equals(other.subject))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(subscript:" + subject + "[" + index + "])";
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(subject);
		out.writeObject(index);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		subject = (SakerTaskFactory) in.readObject();
		index = (SakerTaskFactory) in.readObject();
	}

}
