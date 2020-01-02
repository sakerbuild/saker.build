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
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;

import saker.build.internal.scripting.language.task.result.CompoundStringLiteralSakerTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class CompoundStringLiteralTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	protected List<SakerTaskFactory> components = new ArrayList<>();

	public CompoundStringLiteralTaskFactory() {
	}

	public CompoundStringLiteralTaskFactory(List<SakerTaskFactory> components) {
		this.components = components;
	}

	public void addComponent(SakerTaskFactory factory) {
		components.add(factory);
	}

	public List<SakerTaskFactory> getComponents() {
		return components;
	}

	@Override
	public NavigableSet<String> getCapabilities() {
		return SakerScriptTaskUtils.CAPABILITIES_SHORT_TASK;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) {
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();

		List<TaskIdentifier> futureids = new ArrayList<>(components.size());
		for (SakerTaskFactory tf : components) {
			TaskIdentifier componenttaskid = tf.createSubTaskIdentifier(thistaskid);
			taskcontext.getTaskUtilities().startTaskFuture(componenttaskid, tf);
			futureids.add(componenttaskid);
		}
		CompoundStringLiteralSakerTaskResult result = new CompoundStringLiteralSakerTaskResult(futureids);
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new CompoundStringLiteralTaskFactory(cloneHelper(taskfactoryreplacements, components));
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		SakerLiteralTaskFactory[] literals = new SakerLiteralTaskFactory[components.size()];
		for (int i = 0; i < literals.length; i++) {
			literals[i] = components.get(i).tryConstantize();
			if (literals[i] == null) {
				return null;
			}
		}
		StringBuilder sb = new StringBuilder(literals.length * 16);
		for (int i = 0; i < literals.length; i++) {
			sb.append(literals[i].getValue());
		}
		return new SakerLiteralTaskFactory(sb.toString());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((components == null) ? 0 : components.hashCode());
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
		CompoundStringLiteralTaskFactory other = (CompoundStringLiteralTaskFactory) obj;
		if (components == null) {
			if (other.components != null)
				return false;
		} else if (!components.equals(other.components))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("(compound:(");
		for (SakerTaskFactory tf : components) {
			if (tf instanceof SakerLiteralTaskFactory) {
				sb.append(((SakerLiteralTaskFactory) tf).getValue());
			} else {
				sb.append("{");
				sb.append(tf);
				sb.append("}");
			}
		}
		sb.append("))");
		return sb.toString();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		SerialUtils.writeExternalCollection(out, components);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		components = SerialUtils.readExternalImmutableList(in);
	}

}
