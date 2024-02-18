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
package saker.build.internal.scripting.language;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.scripting.ScriptInformationProvider;
import saker.build.scripting.ScriptPosition;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class SakerScriptInformationProvider implements ScriptInformationProvider, Externalizable {
	private static final long serialVersionUID = 1L;

	private Map<Object, ScriptPosition> factoryPositions = new HashMap<>();
	private NavigableMap<String, ScriptPosition> targetNamePositions = new TreeMap<>();

	public SakerScriptInformationProvider() {
	}

	public void addTargetPosition(String targetname, ScriptPosition position) {
		this.targetNamePositions.put(targetname, position);
	}

	public void addPosition(String targetname, SakerTaskFactory factory, ScriptPosition position) {
		replaceScriptKey(targetname, factory);
		this.factoryPositions.put(factory.getScriptPositionKey(), position);
	}

	public void addPositionIfAbsent(String targetname, SakerTaskFactory factory, ScriptPosition position) {
		replaceScriptKey(targetname, factory);
		this.factoryPositions.putIfAbsent(factory.getScriptPositionKey(), position);
	}

	@Override
	public ScriptPosition getScriptPosition(TaskIdentifier taskid) {
		Objects.requireNonNull(taskid, "task identifier");
		if (!(taskid instanceof SakerScriptTaskIdentifier)) {
			return null;
		}
		SakerScriptTaskIdentifier stid = (SakerScriptTaskIdentifier) taskid;
		SakerTaskFactory sfactory = stid.getTaskFactory();
		ScriptPosition result = factoryPositions.get(sfactory.getScriptPositionKey());
		return result;
	}

	@Override
	public ScriptPosition getTargetPosition(String name) {
		Objects.requireNonNull(name, "name");
		return targetNamePositions.get(name);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ (factoryPositions != null ? "factoryPositions=" + factoryPositions + ", " : "")
				+ (targetNamePositions != null ? "targetNamePositions=" + targetNamePositions : "") + "]";
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalMap(out, factoryPositions);
		SerialUtils.writeExternalMap(out, targetNamePositions);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		SerialUtils.readExternalMap(factoryPositions, in);
		SerialUtils.readExternalMap(targetNamePositions, in);
	}

	private static void replaceScriptKey(String targetname, SakerTaskFactory factory) {
		if (targetname == null) {
			return;
		}
		Object currentposkey = factory.getScriptPositionKey();
		if (currentposkey instanceof BuildTargetScriptPositionKey) {
			String existingname = ((BuildTargetScriptPositionKey) currentposkey).targetName;
			if (!Objects.equals(existingname, targetname)) {
				throw new IllegalArgumentException("Trying to replace different target name: " + targetname
						+ " with existing: " + existingname + " in " + factory);
			}
			return;
		}
		BuildTargetScriptPositionKey nkey = new BuildTargetScriptPositionKey(targetname, currentposkey);
		factory.setScriptPositionKey(nkey);
	}

	private static class BuildTargetScriptPositionKey implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected String targetName;
		protected Object key;

		/**
		 * For {@link Externalizable}.
		 */
		public BuildTargetScriptPositionKey() {
		}

		public BuildTargetScriptPositionKey(String targetName, Object key) {
			this.targetName = targetName;
			this.key = key;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(targetName);
			out.writeObject(key);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			targetName = in.readUTF();
			key = in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((key == null) ? 0 : key.hashCode());
			result = prime * result + ((targetName == null) ? 0 : targetName.hashCode());
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
			BuildTargetScriptPositionKey other = (BuildTargetScriptPositionKey) obj;
			if (key == null) {
				if (other.key != null)
					return false;
			} else if (!key.equals(other.key))
				return false;
			if (targetName == null) {
				if (other.targetName != null)
					return false;
			} else if (!targetName.equals(other.targetName))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (targetName != null ? "targetName=" + targetName + ", " : "")
					+ (key != null ? "key=" + key : "") + "]";
		}

	}

}
