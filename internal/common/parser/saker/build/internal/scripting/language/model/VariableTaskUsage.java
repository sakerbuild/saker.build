package saker.build.internal.scripting.language.model;

import java.util.Objects;

import saker.build.internal.scripting.language.task.TaskInvocationSakerTaskFactory;

public class VariableTaskUsage implements Comparable<VariableTaskUsage> {
	protected final String taskName;
	protected final String variableName;

	public VariableTaskUsage(String taskName, String variableName) {
		Objects.requireNonNull(taskName, "task name");
		Objects.requireNonNull(variableName, "var name");
		this.taskName = taskName;
		this.variableName = variableName;
	}

	public static VariableTaskUsage var(String varname) {
		return new VariableTaskUsage(TaskInvocationSakerTaskFactory.TASKNAME_VAR, varname);
	}

	public String getTaskName() {
		return taskName;
	}

	public String getVariableName() {
		return variableName;
	}

	@Override
	public int compareTo(VariableTaskUsage o) {
		int cmp = this.taskName.compareTo(o.taskName);
		if (cmp != 0) {
			return cmp;
		}
		cmp = this.variableName.compareTo(o.variableName);
		if (cmp != 0) {
			return cmp;
		}
		return 0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskName == null) ? 0 : taskName.hashCode());
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
		VariableTaskUsage other = (VariableTaskUsage) obj;
		if (taskName == null) {
			if (other.taskName != null)
				return false;
		} else if (!taskName.equals(other.taskName))
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
		return taskName + "(" + variableName + ")";
	}

}