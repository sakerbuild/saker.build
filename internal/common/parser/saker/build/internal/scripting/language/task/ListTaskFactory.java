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
package saker.build.internal.scripting.language.task;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import saker.build.internal.scripting.language.task.result.SakerListTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskObjectSakerTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class ListTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	protected List<SakerTaskFactory> elements = new ArrayList<>();

	public ListTaskFactory() {
	}

	public void addElement(SakerTaskFactory elem) {
		this.elements.add(elem);
	}

	public void addElement(Collection<? extends SakerTaskFactory> elems) {
		this.elements.addAll(elems);
	}

	public void add(ListTaskFactory factory) {
		this.elements.addAll(factory.elements);
	}

	public List<SakerTaskFactory> getElements() {
		return elements;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) {
		List<StructuredTaskResult> elems = new ArrayList<>(elements.size());
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		for (SakerTaskFactory tf : elements) {
			StructuredTaskResult elementobjresult;

			DirectComputableSakerTaskFactory<?> dcfactory = DirectComputableSakerTaskFactory.getDirectComputable(tf);
			if (dcfactory != null) {
				elementobjresult = dcfactory.directComputeTaskResult(taskcontext);
			} else {
				TaskIdentifier elementtaskid = tf.createSubTaskIdentifier(thistaskid);
				taskcontext.getTaskUtilities().startTask(elementtaskid, tf);

				elementobjresult = new SakerTaskObjectSakerTaskResult(elementtaskid);
			}
			elems.add(elementobjresult);
		}
		SakerListTaskResult result = new SakerListTaskResult(elems);
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		SakerLiteralTaskFactory[] literals = new SakerLiteralTaskFactory[elements.size()];
		for (int i = 0; i < literals.length; i++) {
			literals[i] = elements.get(i).tryConstantize();
			if (literals[i] == null) {
				return null;
			}
		}
		Object[] result = new Object[literals.length];
		for (int i = 0; i < literals.length; i++) {
			result[i] = literals[i].getValue();
		}
		return new SakerLiteralTaskFactory(ImmutableUtils.asUnmodifiableArrayList(result));
	}

	@Override
	protected boolean isShort() {
		//the task is short because we only start new tasks and don't wait for them
		return true;
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		ListTaskFactory result = new ListTaskFactory();
		for (SakerTaskFactory tf : elements) {
			result.addElement(cloneHelper(taskfactoryreplacements, tf));
		}
		return result;
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
		ListTaskFactory other = (ListTaskFactory) obj;
		if (elements == null) {
			if (other.elements != null)
				return false;
		} else if (!elements.equals(other.elements))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return StringUtils.toStringJoin("(list:[", ", ", elements, "])");
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

}
