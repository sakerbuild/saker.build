package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.path.SakerPath;
import saker.build.task.identifier.TaskIdentifier;

public class GlobalExpressionScopeRootTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath scriptPath;

	/**
	 * For {@link Externalizable}.
	 */
	public GlobalExpressionScopeRootTaskIdentifier() {
	}

	public GlobalExpressionScopeRootTaskIdentifier(SakerPath scriptPath) {
		this.scriptPath = scriptPath;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(scriptPath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		scriptPath = (SakerPath) in.readObject();
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
		GlobalExpressionScopeRootTaskIdentifier other = (GlobalExpressionScopeRootTaskIdentifier) obj;
		if (scriptPath == null) {
			if (other.scriptPath != null)
				return false;
		} else if (!scriptPath.equals(other.scriptPath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (scriptPath != null ? "scriptPath=" + scriptPath : "") + "]";
	}

}
