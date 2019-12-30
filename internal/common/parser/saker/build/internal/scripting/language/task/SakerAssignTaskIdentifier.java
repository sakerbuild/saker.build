package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.identifier.TaskIdentifier;

public class SakerAssignTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	protected TaskIdentifier rootIdentifier;
	protected String variableName;

	public SakerAssignTaskIdentifier() {
	}

	public SakerAssignTaskIdentifier(TaskIdentifier rootIdentifier, String variablename) {
		this.rootIdentifier = rootIdentifier;
		this.variableName = variablename;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(rootIdentifier);
		out.writeObject(variableName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		rootIdentifier = (TaskIdentifier) in.readObject();
		variableName = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((rootIdentifier == null) ? 0 : rootIdentifier.hashCode());
		result = prime * result + ((variableName == null) ? 0 : variableName.hashCode());
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
		SakerAssignTaskIdentifier other = (SakerAssignTaskIdentifier) obj;
		if (rootIdentifier == null) {
			if (other.rootIdentifier != null)
				return false;
		} else if (!rootIdentifier.equals(other.rootIdentifier))
			return false;
		if (variableName == null) {
			if (other.variableName != null)
				return false;
		} else if (!variableName.equals(other.variableName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(assign_tid:" + rootIdentifier + "$" + variableName + ")";
	}

}