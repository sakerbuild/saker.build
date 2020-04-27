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
import saker.build.trace.BuildTrace;

public class IncludeTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	public static final String PARAMETER_TARGET_NAME = "Target";

	private SakerPath taskScriptPath;
	private NavigableMap<String, SakerTaskFactory> parameters;
	private NavigableMap<String, SakerTaskFactory> metaParameters;

	/**
	 * For {@link Externalizable}.
	 */
	public IncludeTaskFactory() {
	}

	public IncludeTaskFactory(SakerPath taskScriptPath, NavigableMap<String, SakerTaskFactory> parameters) {
		this.taskScriptPath = taskScriptPath;
		this.parameters = new TreeMap<>(parameters);
		this.metaParameters = new TreeMap<>();
		boolean targetpresent = putParamToMetaIfPresent(PARAMETER_TARGET_NAME, this.parameters, this.metaParameters);
		boolean unnamedpresent = putParamToMetaIfPresent("", this.parameters, this.metaParameters);
		if (targetpresent && unnamedpresent) {
			throw new IllegalArgumentException(
					"Conflicting parameters for target name: unnamed and " + PARAMETER_TARGET_NAME);
		}
		putParamToMetaIfPresent("Path", this.parameters, this.metaParameters);
		putParamToMetaIfPresent("WorkingDirectory", this.parameters, this.metaParameters);
	}

	public IncludeTaskFactory(SakerPath taskScriptPath, NavigableMap<String, SakerTaskFactory> parameters,
			NavigableMap<String, SakerTaskFactory> metaParameters) {
		this.taskScriptPath = taskScriptPath;
		this.parameters = parameters;
		this.metaParameters = metaParameters;
	}

	private static boolean putParamToMetaIfPresent(String paramname, NavigableMap<String, SakerTaskFactory> params,
			NavigableMap<String, SakerTaskFactory> metaparams) {
		SakerTaskFactory target = params.remove(paramname);
		if (target != null) {
			metaparams.put(paramname, target);
			return true;
		}
		return false;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		taskcontext.setStandardOutDisplayIdentifier(TaskInvocationSakerTaskFactory.TASKNAME_INCLUDE);
		BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_META);

		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();

		NavigableMap<String, TaskIdentifier> parametertaskids = new TreeMap<>();
		NavigableMap<String, TaskIdentifier> metaparametertaskids = new TreeMap<>();
		startParameterTasks(taskcontext, thistaskid, parametertaskids, this.parameters);
		startParameterTasks(taskcontext, thistaskid, metaparametertaskids, this.metaParameters);

		IncludeTaskData data = new IncludeTaskData();
		TaskUtils.initParametersOfTask(taskcontext, data, metaparametertaskids);
		data.targetInvocationParameters = parametertaskids;

		SakerPath buildfilepath = data.Path;
		if (!metaparametertaskids.containsKey("Path")) {
			buildfilepath = taskScriptPath;
		}

		SakerPath workdir = data.WorkingDirectory;
		TaskIdentifier includetaskid = BuildTargetBootstrapperTaskFactory.runBootstrapping(taskcontext, buildfilepath,
				data.Target, data.targetInvocationParameters, workdir, SakerPath.EMPTY);
		SakerTaskResult result = new TaskInvocationOutputSakerTaskResult(includetaskid);
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	private static void startParameterTasks(TaskContext taskcontext, SakerScriptTaskIdentifier thistaskid,
			NavigableMap<String, TaskIdentifier> parametertaskids, NavigableMap<String, SakerTaskFactory> params) {
		for (Entry<String, SakerTaskFactory> entry : params.entrySet()) {
			SakerTaskFactory paramfactory = entry.getValue();
			String paramname = entry.getKey();
			TaskIdentifier paramtaskid = paramfactory.createSubTaskIdentifier(thistaskid);
			taskcontext.getTaskUtilities().startTask(paramtaskid, paramfactory);

			parametertaskids.put(paramname, paramtaskid);
		}
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new IncludeTaskFactory(taskScriptPath, cloneHelper(taskfactoryreplacements, parameters),
				cloneHelper(taskfactoryreplacements, metaParameters));
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
		SerialUtils.writeExternalMap(out, metaParameters);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		taskScriptPath = (SakerPath) in.readObject();
		parameters = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		metaParameters = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((metaParameters == null) ? 0 : metaParameters.hashCode());
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
		if (metaParameters == null) {
			if (other.metaParameters != null)
				return false;
		} else if (!metaParameters.equals(other.metaParameters))
			return false;
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
		@SakerInput({ "", PARAMETER_TARGET_NAME })
		public String Target;

		@SakerInput
		public SakerPath Path;

		@SakerInput
		public SakerPath WorkingDirectory;

		public NavigableMap<String, TaskIdentifier> targetInvocationParameters = Collections.emptyNavigableMap();
	}

}
