package saker.build.internal.scripting.language.task.result;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import saker.build.internal.scripting.language.SakerScriptTargetConfiguration;
import saker.build.internal.scripting.language.exc.InvalidScriptDeclarationException;
import saker.build.internal.scripting.language.task.DefaultsDeclarationSakerTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.task.TaskName;
import saker.build.task.identifier.BuildFileTargetTaskIdentifier;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class SakerScriptTaskDefaults implements Externalizable {
	private static final long serialVersionUID = 1L;

	public static final SakerScriptTaskDefaults EMPTY_INSTANCE = new SakerScriptTaskDefaults();

	private NavigableMap<TaskName, NavigableMap<String, TaskIdentifier>> defaults;

	public SakerScriptTaskDefaults() {
	}

	public SakerScriptTaskDefaults(SakerScriptTargetConfiguration sakertargetconfig,
			BuildFileTargetTaskIdentifier rootbuildid) {
		Set<? extends DefaultsDeclarationSakerTaskFactory> defaults = sakertargetconfig.getDefaultDeclarations();
		for (DefaultsDeclarationSakerTaskFactory def : defaults) {
			NavigableMap<String, SakerTaskFactory> paramdefs = def.getParameterDefaults();
			if (paramdefs.isEmpty()) {
				continue;
			}
			for (TaskName tn : def.getDefaultTaskNames()) {
				NavigableMap<String, TaskIdentifier> paramids = this.defaults.computeIfAbsent(tn,
						Functionals.treeMapComputer());
				for (Entry<String, SakerTaskFactory> defentry : paramdefs.entrySet()) {
					String paramname = defentry.getKey();
					SakerScriptTaskIdentifier deftaskid = new SakerScriptTaskIdentifier(rootbuildid,
							defentry.getValue());
					TaskIdentifier prev = paramids.putIfAbsent(paramname, deftaskid);
					if (prev != null && !prev.equals(deftaskid)) {
						throw new InvalidScriptDeclarationException("Multiple default parameter declarations for task: "
								+ tn + " with parameter: " + paramname);
					}
				}
			}
		}
	}

	public void add(SakerScriptTaskDefaults defs) {
		for (Entry<TaskName, NavigableMap<String, TaskIdentifier>> entry : defs.defaults.entrySet()) {
			TaskName tn = entry.getKey();
			NavigableMap<String, TaskIdentifier> paramids = this.defaults.computeIfAbsent(tn,
					Functionals.treeMapComputer());
			for (Entry<String, TaskIdentifier> defentry : entry.getValue().entrySet()) {
				TaskIdentifier deftaskid = defentry.getValue();
				String paramname = defentry.getKey();
				TaskIdentifier prev = paramids.putIfAbsent(paramname, deftaskid);
				if (prev != null && !prev.equals(deftaskid)) {
					throw new InvalidScriptDeclarationException("Multiple default parameter declarations for task: "
							+ tn + " with parameter: " + paramname);
				}
			}
		}
	}

	public NavigableMap<String, TaskIdentifier> getDefaults(TaskName taskname) {
		return defaults.get(taskname);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalMap(out, defaults, SerialUtils::writeExternalObject, SerialUtils::writeExternalMap);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.defaults = SerialUtils.readExternalMap(new TreeMap<>(), in, SerialUtils::readExternalObject,
				SerialUtils::readExternalSortedImmutableNavigableMap);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((defaults == null) ? 0 : defaults.hashCode());
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
		SakerScriptTaskDefaults other = (SakerScriptTaskDefaults) obj;
		if (defaults == null) {
			if (other.defaults != null)
				return false;
		} else if (!defaults.equals(other.defaults))
			return false;
		return true;
	}

}
