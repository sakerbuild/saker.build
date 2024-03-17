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
package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader;
import saker.build.internal.scripting.language.task.operators.AssignmentTaskFactory;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.task.BuildTargetTask;
import saker.build.task.BuildTargetTaskFactory;
import saker.build.task.BuildTargetTaskResult;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.SimpleBuildTargetTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionUtilities;
import saker.build.task.TaskFactory;
import saker.build.task.exception.TaskParameterException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class SakerScriptBuildTargetTaskFactory implements BuildTargetTaskFactory, Externalizable {
	private static final long serialVersionUID = 1L;

	protected Set<SakerTaskFactory> factories = new HashSet<>();
	protected NavigableSet<String> outputNames = new TreeSet<>();
	protected NavigableMap<String, SakerTaskFactory> targetParameters = new TreeMap<>();
	protected ScriptParsingOptions parsingOptions;
	protected Set<SakerTaskFactory> globalExpressions;

	/**
	 * For {@link Externalizable}.
	 */
	public SakerScriptBuildTargetTaskFactory() {
	}

	public SakerScriptBuildTargetTaskFactory(ScriptParsingOptions parsingOptions) {
		this.parsingOptions = parsingOptions;
	}

	public void addResultTask(String resultname, SakerTaskFactory taskfactory) {
		outputNames.add(resultname);
		factories.add(taskfactory);
	}

	public SakerTaskFactory addTargetParameter(String parametername, SakerTaskFactory defaultvaluetaskfactory) {
		return targetParameters.put(parametername, defaultvaluetaskfactory);
	}

	public void addTask(SakerTaskFactory taskfactory) {
		factories.add(taskfactory);
	}

	public void addTasks(Collection<? extends SakerTaskFactory> factories) {
		this.factories.addAll(factories);
	}

	public void setGlobalExpressions(Set<SakerTaskFactory> globalexpressions) {
		this.globalExpressions = globalexpressions;
	}

	public boolean hasResultTaskWithName(String name) {
		return outputNames.contains(name);
	}

	public boolean hasTargetParameterWithName(String name) {
		return targetParameters.containsKey(name);
	}

	public boolean hasTargetDefaultValue(String name) {
		return targetParameters.get(name) != null;
	}

	@Override
	public NavigableSet<String> getTargetInputParameterNames() {
		return targetParameters.navigableKeySet();
	}

	@Override
	public BuildTargetTask createTask(ExecutionContext executioncontext) {
		return new BuildTargetTask() {
			//initialized in initParameters
			private NavigableMap<String, ? extends TaskIdentifier> taskTargetParameters = Collections
					.emptyNavigableMap();

			@Override
			public BuildTargetTaskResult run(TaskContext taskcontext) {
				TaskIdentifier thistaskid = taskcontext.getTaskId();

				SakerPath scriptpath = parsingOptions.getScriptPath();

				handleDefaultsFiles(taskcontext, scriptpath);

				TaskExecutionUtilities taskutils = taskcontext.getTaskUtilities();
				if (!ObjectUtils.isNullOrEmpty(globalExpressions)) {
					TaskIdentifier buildfiletaskid = new GlobalExpressionScopeRootTaskIdentifier(scriptpath);
					for (SakerTaskFactory factory : globalExpressions) {
						taskutils.startTask(new SakerScriptTaskIdentifier(buildfiletaskid, factory), factory);
					}
				}

				if (!targetParameters.isEmpty()) {
					for (Entry<String, SakerTaskFactory> entry : targetParameters.entrySet()) {
						String pname = entry.getKey();
						TaskIdentifier foundparam = taskTargetParameters.get(pname);
						SakerTaskFactory right;
						if (foundparam != null) {
							right = new SakeringFutureTaskFactory(foundparam);
						} else {
							SakerTaskFactory fac = entry.getValue();
							if (fac == null) {
								//XXX throw a more specific exception
								throw new TaskParameterException(
										"Build target input parameter: " + pname + " is missing.", thistaskid);
							}
							right = fac;
						}
						SakerScriptTaskIdentifier righttaskid = new SakerScriptTaskIdentifier(thistaskid, right);
						taskutils.startTask(righttaskid, right);
						AssignmentTaskFactory.startAssignmentTask(taskcontext, thistaskid, pname, righttaskid);
					}
				}
				if (!factories.isEmpty()) {
					for (SakerTaskFactory factory : factories) {
						taskutils.startTask(new SakerScriptTaskIdentifier(thistaskid, factory), factory);
					}
				}
				NavigableMap<String, TaskIdentifier> resulttaskids = new TreeMap<>();
				if (!ObjectUtils.isNullOrEmpty(outputNames)) {
					for (String outname : outputNames) {
						TaskIdentifier outtaskid = AssignmentTaskFactory.createAssignTaskIdentifier(thistaskid,
								outname);

						resulttaskids.put(outname, outtaskid);
					}
				}
				SimpleBuildTargetTaskResult result = new SimpleBuildTargetTaskResult(resulttaskids);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}

			private void handleDefaultsFiles(TaskContext taskcontext, SakerPath scriptpath) {
				ExecutionPathConfiguration pathconfig = taskcontext.getExecutionContext().getPathConfiguration();
				NavigableSet<SakerPath> defaultsfiles = SakerScriptTargetConfigurationReader
						.getDefaultsFiles(parsingOptions, pathconfig);
				if (defaultsfiles == null) {
					//the defaults file is automatic and optional
					SakerPath defaultdefaultspath = pathconfig.getWorkingDirectory()
							.tryResolve(SakerScriptTargetConfigurationReader.DEFAULT_DEFAULTS_BUILD_FILE_RELATIVE_PATH);
					if (!scriptpath.equals(defaultdefaultspath)) {
						SakerFile presentdefaultsfile = taskcontext.getTaskUtilities()
								.resolveFileAtPath(defaultdefaultspath);
						ContentDescriptor dependencycd;
						if (presentdefaultsfile == null) {
							dependencycd = CommonTaskContentDescriptors.IS_NOT_FILE;
						} else {
							dependencycd = CommonTaskContentDescriptors.IS_FILE;
							defaultsfiles = ImmutableUtils.singletonNavigableSet(defaultdefaultspath);
						}
						taskcontext.reportInputFileDependency(null, defaultdefaultspath, dependencycd);
					}
				}
				TaskFactory<?> defaultstaskfactory;
				if (!ObjectUtils.isNullOrEmpty(defaultsfiles) && !defaultsfiles.contains(scriptpath)) {
					Set<DefaultsLoaderTaskFactory> deftasks = new HashSet<>();
					for (SakerPath deffilepath : defaultsfiles) {
						DefaultsLoaderTaskFactory defaultstask = new DefaultsLoaderTaskFactory(deffilepath);
						deftasks.add(defaultstask);
					}
					defaultstaskfactory = new DefaultsAggregatorTaskFactory(deftasks);
				} else {
					//no defaults files
					//OR
					//THIS is a defaults file. in which case don't apply the defaults.

					//need to start the defaults task anyway so users don't wait forever
					defaultstaskfactory = LiteralTaskFactory.INSTANCE_NULL;
				}
				taskcontext.startTask(new ScriptPathTaskDefaultsLiteralTaskIdentifier(scriptpath), defaultstaskfactory,
						null);
			}

			@Override
			public void initParameters(TaskContext taskcontext,
					NavigableMap<String, ? extends TaskIdentifier> targetparameters) {
				this.taskTargetParameters = targetparameters;
			}
		};
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalMap(out, targetParameters);
		SerialUtils.writeExternalCollection(out, factories);
		SerialUtils.writeExternalCollection(out, outputNames);
		SerialUtils.writeExternalCollection(out, globalExpressions);
		out.writeObject(parsingOptions);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		targetParameters = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		factories = SerialUtils.readExternalImmutableHashSet(in);
		outputNames = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		globalExpressions = SerialUtils.readExternalImmutableHashSet(in);
		parsingOptions = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((factories == null) ? 0 : factories.hashCode());
		result = prime * result + ((globalExpressions == null) ? 0 : globalExpressions.hashCode());
		result = prime * result + ((outputNames == null) ? 0 : outputNames.hashCode());
		result = prime * result + ((parsingOptions == null) ? 0 : parsingOptions.hashCode());
		result = prime * result + ((targetParameters == null) ? 0 : targetParameters.hashCode());
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
		SakerScriptBuildTargetTaskFactory other = (SakerScriptBuildTargetTaskFactory) obj;
		if (factories == null) {
			if (other.factories != null)
				return false;
		} else if (!factories.equals(other.factories))
			return false;
		if (globalExpressions == null) {
			if (other.globalExpressions != null)
				return false;
		} else if (!globalExpressions.equals(other.globalExpressions))
			return false;
		if (outputNames == null) {
			if (other.outputNames != null)
				return false;
		} else if (!outputNames.equals(other.outputNames))
			return false;
		if (parsingOptions == null) {
			if (other.parsingOptions != null)
				return false;
		} else if (!parsingOptions.equals(other.parsingOptions))
			return false;
		if (targetParameters == null) {
			if (other.targetParameters != null)
				return false;
		} else if (!targetParameters.equals(other.targetParameters))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "SakerScriptBuildTargetTaskFactory[factories=" + factories + ", outputNames=" + outputNames
				+ ", targetParameters=" + targetParameters + ", parsingOptions=" + parsingOptions
				+ ", globalExpressions=" + globalExpressions + "]";
	}

}
