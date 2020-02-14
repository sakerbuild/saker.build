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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.internal.scripting.language.exc.InvalidScriptDeclarationTaskFactory;
import saker.build.internal.scripting.language.task.builtin.AbortTaskFactory;
import saker.build.internal.scripting.language.task.builtin.GlobalVariableTaskFactory;
import saker.build.internal.scripting.language.task.builtin.IncludeTaskFactory;
import saker.build.internal.scripting.language.task.builtin.PathTaskFactory;
import saker.build.internal.scripting.language.task.builtin.PrintTaskFactory;
import saker.build.internal.scripting.language.task.builtin.SequenceTaskFactory;
import saker.build.internal.scripting.language.task.builtin.StaticVariableTaskFactory;
import saker.build.internal.scripting.language.task.operators.DereferenceTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.TaskInvocationOutputSakerTaskResult;
import saker.build.scripting.ScriptPosition;
import saker.build.task.TaskContext;
import saker.build.task.TaskFuture;
import saker.build.task.TaskName;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.TaskInvocationBootstrapperTaskFactory;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class TaskInvocationSakerTaskFactory extends SelfSakerTaskFactory {
	public static final String TASKNAME_ABORT = "abort";
	public static final String TASKNAME_PRINT = "print";
	public static final String TASKNAME_GLOBAL = "global";
	public static final String TASKNAME_INCLUDE = "include";
	public static final String TASKNAME_PATH = "path";
	public static final String TASKNAME_SEQUENCE = "sequence";
	public static final String TASKNAME_STATIC = "static";
	public static final String TASKNAME_VAR = "var";

	private static final long serialVersionUID = 1L;

	protected String taskName;
	protected List<SakerTaskFactory> qualifierFactories;
	protected String repository;

	protected NavigableMap<String, SakerTaskFactory> parameters;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskInvocationSakerTaskFactory() {
	}

	private TaskInvocationSakerTaskFactory(String taskName, List<SakerTaskFactory> qualifierFactories,
			String repository, NavigableMap<String, SakerTaskFactory> parameters) {
		this.taskName = taskName;
		this.qualifierFactories = qualifierFactories;
		this.repository = repository;
		this.parameters = parameters;
	}

	public static SakerTaskFactory create(String taskName, List<SakerTaskFactory> qualifierFactories, String repository,
			NavigableMap<String, SakerTaskFactory> parameters, SakerPath scriptpath, ScriptPosition scriptposition) {
		if (repository == null) {
			switch (taskName) {
				case TASKNAME_GLOBAL: {
					if (!ObjectUtils.isNullOrEmpty(qualifierFactories)) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_GLOBAL + "\" cannot have qualifiers.", scriptposition);
					}
					SakerTaskFactory varname;
					if (ObjectUtils.isNullOrEmpty(parameters) || (varname = parameters.get("")) == null) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_GLOBAL + "\" missing variable name as unnamed parameter.",
								scriptposition);
					}
					if (varname instanceof SakerLiteralTaskFactory
							&& ((SakerLiteralTaskFactory) varname).getValue() == null) {
						return new InvalidScriptDeclarationTaskFactory("Global variable name is null.", scriptposition);
					}
					if (parameters.size() > 1) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_GLOBAL + "\" cannot have multiple parameters.",
								scriptposition);
					}
					return new GlobalVariableTaskFactory(varname);
				}
				case TASKNAME_STATIC: {
					if (!ObjectUtils.isNullOrEmpty(qualifierFactories)) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_STATIC + "\" cannot have qualifiers.", scriptposition);
					}
					SakerTaskFactory varname;
					if (ObjectUtils.isNullOrEmpty(parameters) || (varname = parameters.get("")) == null) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_STATIC + "\" missing variable name as unnamed parameter.",
								scriptposition);
					}
					if (varname instanceof SakerLiteralTaskFactory
							&& ((SakerLiteralTaskFactory) varname).getValue() == null) {
						return new InvalidScriptDeclarationTaskFactory("Static variable name is null.", scriptposition);
					}
					if (parameters.size() > 1) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_STATIC + "\" cannot have multiple parameters.",
								scriptposition);
					}
					return new StaticVariableTaskFactory(scriptpath, varname);
				}
				case TASKNAME_VAR: {
					if (!ObjectUtils.isNullOrEmpty(qualifierFactories)) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_VAR + "\" cannot have qualifiers.", scriptposition);
					}
					SakerTaskFactory varname;
					if (ObjectUtils.isNullOrEmpty(parameters) || (varname = parameters.get("")) == null) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_VAR + "\" missing variable name as unnamed parameter.",
								scriptposition);
					}
					if (varname instanceof SakerLiteralTaskFactory
							&& ((SakerLiteralTaskFactory) varname).getValue() == null) {
						return new InvalidScriptDeclarationTaskFactory("Variable name is null.", scriptposition);
					}
					if (parameters.size() > 1) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_VAR + "\" cannot have multiple parameters.", scriptposition);
					}
					return DereferenceTaskFactory.create(varname, scriptposition);
				}
				case TASKNAME_INCLUDE: {
					if (!ObjectUtils.isNullOrEmpty(qualifierFactories)) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_INCLUDE + "\" cannot have qualifiers.", scriptposition);
					}
					//it is allowed to have no target when path is specified, as the default target can be invoked
					if (!parameters.containsKey("Path")) {
						if (!parameters.containsKey("Target") && !parameters.containsKey("")) {
							return new InvalidScriptDeclarationTaskFactory(
									"Path and Target parameters are missing for \"" + TASKNAME_INCLUDE + "\"",
									scriptposition);
						}
					}
					return new IncludeTaskFactory(scriptpath, parameters);
				}
				case TASKNAME_ABORT: {
					if (!ObjectUtils.isNullOrEmpty(qualifierFactories)) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_ABORT + "\" cannot have qualifiers.", scriptposition);
					}
					if (parameters.size() > 1) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_ABORT + "\" cannot have multiple parameters.", scriptposition);
					}
					SakerTaskFactory messagefactory;
					Entry<String, SakerTaskFactory> paramentry = parameters.firstEntry();
					if (paramentry != null) {
						if (!"".equals(paramentry.getKey())) {
							return new InvalidScriptDeclarationTaskFactory(
									"Invalid parameter name for task \"" + TASKNAME_ABORT + "\": " + paramentry.getKey()
											+ ". Expected an unnamed parameter for its message.",
									scriptposition);
						}
						messagefactory = paramentry.getValue();
					} else {
						messagefactory = null;
					}
					return new AbortTaskFactory(messagefactory);
				}
				case TASKNAME_PRINT: {
					if (!ObjectUtils.isNullOrEmpty(qualifierFactories)) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_PRINT + "\" cannot have qualifiers.", scriptposition);
					}
					SakerTaskFactory messagefactory;
					if (ObjectUtils.isNullOrEmpty(parameters) || (messagefactory = parameters.get("")) == null) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_PRINT + "\" missing message as unnamed parameter.",
								scriptposition);
					}
					return new PrintTaskFactory(messagefactory);
				}
				case TASKNAME_PATH: {
					if (!ObjectUtils.isNullOrEmpty(qualifierFactories)) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_PATH + "\" cannot have qualifiers.", scriptposition);
					}
					if (parameters.isEmpty()) {
						//returns the working directory
						return new PathTaskFactory(new SakerLiteralTaskFactory(SakerPath.EMPTY));
					}
					SakerTaskFactory pathfactory;
					if (parameters.size() != 1 || (pathfactory = parameters.get("")) == null) {
						return new InvalidScriptDeclarationTaskFactory(
								"Invalid parameters for task: '" + TASKNAME_PATH
										+ "' expected only a single unnamed parameter. (" + parameters.keySet() + ")",
								scriptposition);
					}
					//eagerly constantize if possible
					SakerLiteralTaskFactory paramconst = pathfactory.tryConstantize();
					if (paramconst != null) {
						String str = Objects.toString(paramconst.getValue(), null);
						if (str == null) {
							return new InvalidScriptDeclarationTaskFactory("Path evaluated to null.", scriptposition);
						}
						try {
							SakerPath parsedpath = SakerPath.valueOf(str);
							SakerLiteralTaskFactory pathliteralfactory = new SakerLiteralTaskFactory(parsedpath);
							if (parsedpath.isAbsolute()) {
								return pathliteralfactory;
							}
							return new PathTaskFactory(pathliteralfactory);
						} catch (InvalidPathFormatException e) {
							return new InvalidScriptDeclarationTaskFactory(
									"Failed to parse path: " + str + " (" + e + ")", scriptposition);
						}
					}
					return new PathTaskFactory(pathfactory);
				}
				case TASKNAME_SEQUENCE: {
					if (!ObjectUtils.isNullOrEmpty(qualifierFactories)) {
						return new InvalidScriptDeclarationTaskFactory(
								"The task \"" + TASKNAME_SEQUENCE + "\" cannot have qualifiers.", scriptposition);
					}
					SakerTaskFactory param;
					if (parameters.size() != 1 || (param = parameters.get("")) == null) {
						return new InvalidScriptDeclarationTaskFactory("Invalid parameters for task: '"
								+ TASKNAME_SEQUENCE + "' expected only a single unnamed list parameter. ("
								+ parameters.keySet() + ")", scriptposition);
					}
					SakerLiteralTaskFactory paramconst = param.tryConstantize();
					if (paramconst != null) {
						param = paramconst;
					}
					if (param instanceof SakerLiteralTaskFactory) {
						Object val = ((SakerLiteralTaskFactory) param).getValue();
						if (!(val instanceof List)) {
							return new InvalidScriptDeclarationTaskFactory("Parameter for task '" + TASKNAME_SEQUENCE
									+ "' is not a list. (" + (val == null ? "null" : val.getClass().getName()) + ")",
									scriptposition);
						}
						return new SakerLiteralTaskFactory(val);
					}
					if (!(param instanceof ListTaskFactory)) {
						return new InvalidScriptDeclarationTaskFactory("Parameter for task '" + TASKNAME_SEQUENCE
								+ "' is not a list. (" + param.getClass().getName() + ")", scriptposition);
					}
					List<SakerTaskFactory> elements = ((ListTaskFactory) param).getElements();
					if (elements.isEmpty()) {
						return new SakerLiteralTaskFactory(Collections.emptyList());
					}
					for (SakerTaskFactory taskfactory : elements) {
						if (taskfactory instanceof ForeachTaskFactory) {
							if (((ForeachTaskFactory) taskfactory).getResultFactory() == null) {
								return new InvalidScriptDeclarationTaskFactory(
										TASKNAME_SEQUENCE
												+ " cannot be used with foreach statements without result as element.",
										scriptposition);
							}
						}
					}
					return new SequenceTaskFactory(elements);
				}
				default: {
					if (taskName.indexOf('.') < 0) {
						//only a single name part
						//the single name part task names are reserved for the scripting language
						return new InvalidScriptDeclarationTaskFactory(
								"Unknown task name: " + taskName
										+ " (Single named tasks are reserved for the scripting language.)",
								scriptposition);
					}
					break;
				}
			}
		}
		return new TaskInvocationSakerTaskFactory(taskName, qualifierFactories, repository, parameters);
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		TaskName targettaskname;
		if (qualifierFactories.isEmpty()) {
			targettaskname = TaskName.valueOf(taskName);
		} else {
			int qsize = qualifierFactories.size();
			TaskFuture<?>[] qualifierfutures = new TaskFuture<?>[qsize];
			int idx = 0;
			for (SakerTaskFactory qtf : qualifierFactories) {
				qualifierfutures[idx++] = taskcontext.getTaskUtilities()
						.startTaskFuture(qtf.createSubTaskIdentifier(thistaskid), qtf);
			}
			String[] qualifiers = new String[qsize];
			for (int i = 0; i < qsize; i++) {
				Object qres = ((SakerTaskResult) qualifierfutures[i].get()).toResult(taskcontext);
				qualifiers[i] = Objects.toString(qres);
			}
			targettaskname = TaskName.valueOf(taskName, qualifiers);
		}

		NavigableMap<String, TaskIdentifier> parametertaskids = new TreeMap<>();
		for (Entry<String, SakerTaskFactory> entry : parameters.entrySet()) {
			SakerTaskFactory paramfactory = entry.getValue();
			String paramname = entry.getKey();
			TaskIdentifier paramtaskid = paramfactory.createSubTaskIdentifier(thistaskid);
			taskcontext.startTask(paramtaskid, paramfactory, null);

			parametertaskids.put(paramname, paramtaskid);
		}

		TaskIdentifier invokertaskid = TaskInvocationBootstrapperTaskFactory.runBootstrapping(taskcontext,
				targettaskname, parametertaskids, repository, null);
		TaskInvocationOutputSakerTaskResult result = new TaskInvocationOutputSakerTaskResult(invokertaskid);
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		TaskInvocationSakerTaskFactory result = new TaskInvocationSakerTaskFactory(this.taskName,
				cloneHelper(taskfactoryreplacements, this.qualifierFactories), this.repository,
				cloneHelper(taskfactoryreplacements, this.parameters));
		return result;
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		return null;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("task:");
		sb.append(taskName);
		sb.append(':');
		if (!qualifierFactories.isEmpty()) {
			for (SakerTaskFactory qtf : qualifierFactories) {
				sb.append("q:");
				sb.append(qtf);
				sb.append(':');
			}
		}
		if (repository != null) {
			sb.append("repo:");
			sb.append(repository);
			sb.append(':');
		}
		for (Iterator<Entry<String, SakerTaskFactory>> it = parameters.entrySet().iterator(); it.hasNext();) {
			Entry<String, SakerTaskFactory> param = it.next();
			sb.append("param:");
			sb.append(param.getKey());
			sb.append('=');
			sb.append(param.getValue());
			if (it.hasNext()) {
				sb.append(',');
			}
		}
		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((parameters == null) ? 0 : parameters.hashCode());
		result = prime * result + ((qualifierFactories == null) ? 0 : qualifierFactories.hashCode());
		result = prime * result + ((repository == null) ? 0 : repository.hashCode());
		result = prime * result + ((taskName == null) ? 0 : taskName.hashCode());
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
		TaskInvocationSakerTaskFactory other = (TaskInvocationSakerTaskFactory) obj;
		if (parameters == null) {
			if (other.parameters != null)
				return false;
		} else if (!parameters.equals(other.parameters))
			return false;
		if (qualifierFactories == null) {
			if (other.qualifierFactories != null)
				return false;
		} else if (!qualifierFactories.equals(other.qualifierFactories))
			return false;
		if (repository == null) {
			if (other.repository != null)
				return false;
		} else if (!repository.equals(other.repository))
			return false;
		if (taskName == null) {
			if (other.taskName != null)
				return false;
		} else if (!taskName.equals(other.taskName))
			return false;
		return true;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeUTF(taskName);
		out.writeObject(repository);
		SerialUtils.writeExternalMap(out, parameters);
		SerialUtils.writeExternalCollection(out, qualifierFactories);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		taskName = in.readUTF();
		repository = (String) in.readObject();
		parameters = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		qualifierFactories = SerialUtils.readExternalImmutableList(in);
	}
}
