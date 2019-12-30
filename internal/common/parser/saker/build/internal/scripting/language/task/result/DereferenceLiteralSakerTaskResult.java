package saker.build.internal.scripting.language.task.result;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.TaskResultResolver;
import saker.build.task.identifier.TaskIdentifier;

public class DereferenceLiteralSakerTaskResult extends AbstractDereferenceSakerTaskResult {
	private static final long serialVersionUID = 1L;

	private String variableName;

	public DereferenceLiteralSakerTaskResult() {
	}

	public DereferenceLiteralSakerTaskResult(TaskIdentifier ownerRootTaskId, String variableName) {
		super(ownerRootTaskId);
		this.variableName = variableName;
	}

	@Override
	protected String getVariableName(TaskResultResolver results) {
		return variableName;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeUTF(variableName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		variableName = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((variableName == null) ? 0 : variableName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		DereferenceLiteralSakerTaskResult other = (DereferenceLiteralSakerTaskResult) obj;
		if (variableName == null) {
			if (other.variableName != null)
				return false;
		} else if (!variableName.equals(other.variableName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + variableName + "]";
	}
}
