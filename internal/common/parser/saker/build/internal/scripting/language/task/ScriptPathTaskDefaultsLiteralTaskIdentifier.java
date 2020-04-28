package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.path.SakerPath;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class ScriptPathTaskDefaultsLiteralTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath scriptPath;

	/**
	 * For {@link Externalizable}.
	 */
	public ScriptPathTaskDefaultsLiteralTaskIdentifier() {
	}

	public ScriptPathTaskDefaultsLiteralTaskIdentifier(SakerPath scriptPath) {
		this.scriptPath = scriptPath;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(scriptPath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		scriptPath = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((scriptPath == null) ? 0 : scriptPath.hashCode());
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
		ScriptPathTaskDefaultsLiteralTaskIdentifier other = (ScriptPathTaskDefaultsLiteralTaskIdentifier) obj;
		if (scriptPath == null) {
			if (other.scriptPath != null)
				return false;
		} else if (!scriptPath.equals(other.scriptPath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ScriptPathTaskDefaultsLiteralTaskIdentifier[scriptPath=" + scriptPath + "]";
	}

}
