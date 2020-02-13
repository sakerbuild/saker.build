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
package saker.build.internal.scripting.language.task.builtin;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.Objects;

import saker.build.file.path.SakerPath;
import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.StaticScriptVariableTaskIdentifier;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.StaticVariableTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

public class StaticVariableTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	private SakerPath scriptPath;
	private SakerTaskFactory nameFactory;

	/**
	 * For {@link Externalizable}.
	 */
	public StaticVariableTaskFactory() {
	}

	public StaticVariableTaskFactory(SakerPath scriptPath, SakerTaskFactory nameFactory) {
		this.scriptPath = scriptPath;
		this.nameFactory = nameFactory;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		TaskIdentifier namefactoryid = nameFactory
				.createSubTaskIdentifier((SakerScriptTaskIdentifier) taskcontext.getTaskId());
		String varnamestr = Objects
				.toString(runForResult(taskcontext, namefactoryid, nameFactory).toResult(taskcontext), null);
		if (varnamestr == null) {
			taskcontext.abortExecution(
					new OperandExecutionException("Static variable name evaluated to null.", namefactoryid));
			return null;
		}
		StaticVariableTaskResult result = new StaticVariableTaskResult(
				new StaticScriptVariableTaskIdentifier(scriptPath, varnamestr));
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new StaticVariableTaskFactory(scriptPath, cloneHelper(taskfactoryreplacements, nameFactory));
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(scriptPath);
		out.writeObject(nameFactory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		scriptPath = (SakerPath) in.readObject();
		nameFactory = (SakerTaskFactory) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((nameFactory == null) ? 0 : nameFactory.hashCode());
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
		StaticVariableTaskFactory other = (StaticVariableTaskFactory) obj;
		if (nameFactory == null) {
			if (other.nameFactory != null)
				return false;
		} else if (!nameFactory.equals(other.nameFactory))
			return false;
		if (scriptPath == null) {
			if (other.scriptPath != null)
				return false;
		} else if (!scriptPath.equals(other.scriptPath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (scriptPath != null ? "scriptPath=" + scriptPath + ", " : "")
				+ (nameFactory != null ? "nameFactory=" + nameFactory : "") + "]";
	}

}
