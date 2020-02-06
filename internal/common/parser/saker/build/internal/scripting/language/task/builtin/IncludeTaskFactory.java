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
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.TaskInvocationSakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.TaskInvocationOutputSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.BuildTargetBootstrapperTaskFactory;
import saker.build.task.utils.TaskUtils;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class IncludeTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	private SakerPath taskScriptPath;
	private NavigableMap<String, SakerTaskFactory> parameters;

	/**
	 * For {@link Externalizable}.
	 */
	public IncludeTaskFactory() {
	}

	public IncludeTaskFactory(SakerPath taskScriptPath, NavigableMap<String, SakerTaskFactory> parameters) {
		this.taskScriptPath = taskScriptPath;
		this.parameters = parameters;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		taskcontext.setStandardOutDisplayIdentifier(TaskInvocationSakerTaskFactory.TASKNAME_INCLUDE);
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();

		NavigableMap<String, TaskIdentifier> parametertaskids = new TreeMap<>();
		for (Entry<String, SakerTaskFactory> entry : parameters.entrySet()) {
			SakerTaskFactory paramfactory = entry.getValue();
			String paramname = entry.getKey();
			TaskIdentifier paramtaskid = paramfactory.createSubTaskIdentifier(thistaskid);
			taskcontext.getTaskUtilities().startTaskFuture(paramtaskid, paramfactory);

			parametertaskids.put(paramname, paramtaskid);
		}

		IncludeTaskData data = new IncludeTaskData();
		TaskUtils.initParametersOfTask(taskcontext, data, parametertaskids);
		data.targetInvocationParameters = new TreeMap<>(parametertaskids);
		data.targetInvocationParameters.remove("Target");
		data.targetInvocationParameters.remove("");
		data.targetInvocationParameters.remove("Path");
		data.targetInvocationParameters.remove("WorkingDirectory");

		SakerPath buildfilepath = data.Path;
		if (!parametertaskids.containsKey("Path")) {
			buildfilepath = taskScriptPath;
		}

		SakerPath workdir = data.WorkingDirectory;
		TaskIdentifier includetaskid = BuildTargetBootstrapperTaskFactory.runBootstrapping(taskcontext, buildfilepath,
				data.Target, data.targetInvocationParameters, workdir, SakerPath.EMPTY);
		SakerTaskResult result = new TaskInvocationOutputSakerTaskResult(includetaskid);
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new IncludeTaskFactory(taskScriptPath, cloneHelper(taskfactoryreplacements, parameters));
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(taskScriptPath);
		SerialUtils.writeExternalMap(out, parameters);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		taskScriptPath = (SakerPath) in.readObject();
		parameters = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((parameters == null) ? 0 : parameters.hashCode());
		result = prime * result + ((taskScriptPath == null) ? 0 : taskScriptPath.hashCode());
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
		IncludeTaskFactory other = (IncludeTaskFactory) obj;
		if (parameters == null) {
			if (other.parameters != null)
				return false;
		} else if (!parameters.equals(other.parameters))
			return false;
		if (taskScriptPath == null) {
			if (other.taskScriptPath != null)
				return false;
		} else if (!taskScriptPath.equals(other.taskScriptPath))
			return false;
		return true;
	}

	private static class IncludeTaskData {
		@SakerInput({ "", "Target" })
		public String Target;

		@SakerInput
		public SakerPath Path;

		@SakerInput
		public SakerPath WorkingDirectory;

		public NavigableMap<String, TaskIdentifier> targetInvocationParameters = Collections.emptyNavigableMap();
	}

}
