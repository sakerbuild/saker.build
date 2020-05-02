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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.task.result.SakerMapTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class MapTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	//XXX store this in a (linked?) hash map instead?
	protected List<SakerTaskFactory> keys = new ArrayList<>();
	protected List<SakerTaskFactory> values = new ArrayList<>();

	public MapTaskFactory() {
	}

	public void addEntry(SakerTaskFactory key, SakerTaskFactory value) {
		keys.add(key);
		values.add(value);
	}

	public void add(MapTaskFactory factory) {
		this.keys.addAll(factory.keys);
		this.values.addAll(factory.values);
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) {
		NavigableMap<String, StructuredTaskResult> elements = new TreeMap<>(StringUtils.nullsFirstStringComparator());
		Map<TaskIdentifier, StructuredTaskResult> keyvals = new HashMap<>();

		int size = keys.size();
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		for (int i = 0; i < size; i++) {
			SakerTaskFactory ktf = keys.get(i);
			SakerTaskFactory vtf = values.get(i);

			TaskIdentifier valtaskid = vtf.createSubTaskIdentifier(thistaskid);
			taskcontext.getTaskUtilities().startTaskFuture(valtaskid, vtf);
			SimpleStructuredObjectTaskResult valobjresult = new SimpleStructuredObjectTaskResult(valtaskid);
			//optimize literal keys not to start a task unnecessarily
			if (ktf instanceof SakerLiteralTaskFactory) {
				Object keystr = ((SakerLiteralTaskFactory) ktf).getValue();
				Object prev = elements.put(Objects.toString(keystr, null), valobjresult);
				if (prev != null) {
					throw new OperandExecutionException("Map key present multiple times: " + keystr, valtaskid);
				}
			} else {
				TaskIdentifier keytaskid = ktf.createSubTaskIdentifier(thistaskid);
				taskcontext.getTaskUtilities().startTaskFuture(keytaskid, ktf);

				Object present = keyvals.putIfAbsent(keytaskid, valobjresult);
				if (present != null) {
					throw new OperandExecutionException(
							"Map key present multiple times: " + valobjresult + " - " + present, keytaskid);
				}
			}
		}
		for (Entry<TaskIdentifier, StructuredTaskResult> entry : keyvals.entrySet()) {
			TaskIdentifier keytaskid = entry.getKey();
			String keystr = Objects.toString(StructuredTaskResult.getActualTaskResult(keytaskid, taskcontext), null);
			StructuredTaskResult prev = elements.put(keystr, entry.getValue());
			if (prev != null) {
				throw new OperandExecutionException("Map key present multiple times: " + keystr, thistaskid);
			}
		}
		SakerMapTaskResult result = new SakerMapTaskResult(elements);
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		SakerLiteralTaskFactory[] keyliterals = new SakerLiteralTaskFactory[keys.size()];
		SakerLiteralTaskFactory[] valliterals = new SakerLiteralTaskFactory[keyliterals.length];
		//we do the literalization in two pass. usually the values are computed, so we can fail the constantization early if any values does not conform
		for (int i = 0; i < keyliterals.length; i++) {
			valliterals[i] = values.get(i).tryConstantize();
			if (valliterals[i] == null) {
				return null;
			}
		}
		for (int i = 0; i < keyliterals.length; i++) {
			keyliterals[i] = keys.get(i).tryConstantize();
			if (keyliterals[i] == null) {
				return null;
			}
		}
		NavigableMap<String, Object> result = new TreeMap<>(StringUtils.nullsFirstStringComparator());
		for (int i = 0; i < keyliterals.length; i++) {
			Object keyval = keyliterals[i].getValue();
			String keystr = Objects.toString(keyval, null);
			if (result.containsKey(keystr)) {
				return null;
			}
			result.put(keystr, valliterals[i].getValue());
		}
		return new SakerLiteralTaskFactory(ImmutableUtils.unmodifiableNavigableMap(result));
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		MapTaskFactory result = new MapTaskFactory();
		int size = keys.size();
		for (int i = 0; i < size; i++) {
			SakerTaskFactory ktf = keys.get(i);
			SakerTaskFactory vtf = values.get(i);

			result.addEntry(cloneHelper(taskfactoryreplacements, ktf), cloneHelper(taskfactoryreplacements, vtf));
		}
		return result;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		SerialUtils.writeExternalCollection(out, keys);
		SerialUtils.writeExternalCollection(out, values);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		keys = SerialUtils.readExternalImmutableList(in);
		values = SerialUtils.readExternalImmutableList(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((keys == null) ? 0 : keys.hashCode());
		result = prime * result + ((values == null) ? 0 : values.hashCode());
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
		MapTaskFactory other = (MapTaskFactory) obj;
		if (keys == null) {
			if (other.keys != null)
				return false;
		} else if (!keys.equals(other.keys))
			return false;
		if (values == null) {
			if (other.values != null)
				return false;
		} else if (!values.equals(other.values))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("(map:{");
		int size = keys.size();
		for (int i = 0; i < size; i++) {
			SakerTaskFactory ktf = keys.get(i);
			SakerTaskFactory vtf = values.get(i);
			sb.append(ktf);
			sb.append(": ");
			sb.append(vtf);
			if (i + 1 < size) {
				sb.append(", ");
			}
		}
		sb.append("})");
		return sb.toString();
	}

}
