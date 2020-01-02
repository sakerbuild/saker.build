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
package saker.build.scripting.model.info;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;

import saker.apiextract.api.PublicApi;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Simple {@link TaskInformation} data class.
 */
@PublicApi
public class SimpleTaskInformation implements TaskInformation, Externalizable {
	private static final long serialVersionUID = 1L;

	private TaskName taskName;
	private TypeInformation returnType;
	private FormattedTextContent information;
	private Collection<? extends TaskParameterInformation> parameters;
	private boolean deprecated;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleTaskInformation() {
	}

	/**
	 * Creates a new instance for the given task name.
	 * 
	 * @param taskName
	 *            The task name.
	 * @see #getTaskName()
	 */
	public SimpleTaskInformation(TaskName taskName) {
		this.taskName = taskName;
	}

	@Override
	public TaskName getTaskName() {
		return taskName;
	}

	@Override
	public TypeInformation getReturnType() {
		return returnType;
	}

	@Override
	public FormattedTextContent getInformation() {
		return information;
	}

	@Override
	public Collection<? extends TaskParameterInformation> getParameters() {
		return parameters;
	}

	@Override
	public boolean isDeprecated() {
		return deprecated;
	}

	/**
	 * Sets the task name.
	 * 
	 * @param taskName
	 *            The task name.
	 * @see #getTaskName()
	 */
	public void setTaskName(TaskName taskName) {
		this.taskName = taskName;
	}

	/**
	 * Sets the return type of the task.
	 * 
	 * @param returnType
	 *            The return type.
	 * @see #getReturnType()
	 */
	public void setReturnType(TypeInformation returnType) {
		this.returnType = returnType;
	}

	/**
	 * Sets the documentational information.
	 * 
	 * @param information
	 *            The information.
	 * @see #getInformation()
	 */
	public void setInformation(FormattedTextContent information) {
		this.information = information;
	}

	/**
	 * Sets the parameter informations.
	 * 
	 * @param parameters
	 *            The parameter informations.
	 * @see #getParameters()
	 */
	public void setParameters(Collection<? extends TaskParameterInformation> parameters) {
		this.parameters = parameters;
	}

	/**
	 * Sets the deprecated flag for this task.
	 * 
	 * @param deprecated
	 *            <code>true</code> if deprecated.
	 * @see #isDeprecated()
	 */
	public void setDeprecated(boolean deprecated) {
		this.deprecated = deprecated;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskName);
		out.writeObject(returnType);
		out.writeObject(information);
		out.writeBoolean(deprecated);
		SerialUtils.writeExternalCollection(out, parameters);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskName = (TaskName) in.readObject();
		returnType = (TypeInformation) in.readObject();
		information = (FormattedTextContent) in.readObject();
		deprecated = in.readBoolean();
		parameters = SerialUtils.readExternalImmutableList(in);
	}

	@Override
	public String toString() {
		return "SimpleTaskInformation[" + (taskName != null ? "taskName=" + taskName + ", " : "")
				+ (returnType != null ? "returnType=" + returnType + ", " : "")
				+ (information != null ? "information=" + information + ", " : "")
				+ (parameters != null ? "parameters=" + parameters + ", " : "") + "deprecated=" + deprecated + "]";
	}

}
