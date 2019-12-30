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

	public void addPosition(SakerTaskFactory factory, ScriptPosition position) {
		this.factoryPositions.put(factory.getScriptPositionKey(), position);
	}

	public void addPositionIfAbsent(SakerTaskFactory factory, ScriptPosition position) {
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

}
