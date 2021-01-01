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
package saker.build.internal.scripting.language.model;

import java.util.Collections;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import saker.build.file.path.SakerPath;
import saker.build.internal.scripting.language.task.TaskInvocationSakerTaskFactory;
import saker.build.internal.scripting.language.task.builtin.IncludeTaskFactory;
import saker.build.scripting.model.SingleFormattedTextContent;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.scripting.model.info.SimpleTaskInformation;
import saker.build.scripting.model.info.SimpleTaskParameterInformation;
import saker.build.scripting.model.info.SimpleTypeInformation;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.build.task.BuildTargetTaskResult;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;

final class BuiltinExternalScriptInformationProvider implements ExternalScriptInformationProvider {
	public static final String DEREFERENCE_VAR_PARAM_INFO = "The name of the dereferenced variable.";

	public static final ExternalScriptInformationProvider INSTANCE = new BuiltinExternalScriptInformationProvider();

	private static final Set<String> INCLUDE_TASK_PARAMETER_NAMES = ImmutableUtils
			.makeImmutableNavigableSet(new String[] { IncludeTaskFactory.PARAMETER_PATH,
					IncludeTaskFactory.PARAMETER_TARGET, IncludeTaskFactory.PARAMETER_WORKING_DIRECTORY });

	public static boolean isIncludeTaskParameterName(String name) {
		return name != null && INCLUDE_TASK_PARAMETER_NAMES.contains(name);
	}

	public static final TaskInformation DEFAULTS_TASK_INFORMATION;
	public static final TaskParameterInformation DEFAULTS_TASK_UNNAMED_PARAMETER_INFORMATION;

	private static final NavigableSet<TaskName> BUILTIN_TASK_NAMES;

	private static final NavigableMap<TaskName, TaskInformation> BUILTIN_TASK_INFORMATIONS;
	static {
		TreeMap<TaskName, TaskInformation> map = new TreeMap<>();
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_PATH);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(SingleFormattedTextContent.createPlaintext(
					"Converts a path to absolute representation. Returns the current working directory if no parameter is specified."));

			SimpleTaskParameterInformation pathparam = new SimpleTaskParameterInformation(taskinfo, "Path");
			pathparam.setAliases(ImmutableUtils.singletonSet(""));
			pathparam.setRequired(true);
			pathparam.setInformation(
					SingleFormattedTextContent.createPlaintext("The path to convert to absolute representation."));
			taskinfo.setParameters(ImmutableUtils.singletonList(pathparam));
			map.put(taskname, taskinfo);

			SimpleTypeInformation returninfo = new SimpleTypeInformation(TypeInformationKind.LITERAL);
			returninfo.setTypeQualifiedName(SakerPath.class.getCanonicalName());
			returninfo.setTypeSimpleName(SakerPath.class.getSimpleName());
			returninfo.setInformation(SingleFormattedTextContent.createPlaintext("An execution path."));
			taskinfo.setReturnType(returninfo);
		}
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_PRINT);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(
					SingleFormattedTextContent.createPlaintext("Prints out the given argument message to the output."));

			SimpleTaskParameterInformation pathparam = new SimpleTaskParameterInformation(taskinfo, "");
			pathparam.setRequired(true);
			pathparam.setInformation(SingleFormattedTextContent.createPlaintext("The message to print to the output."));
			taskinfo.setParameters(ImmutableUtils.singletonList(pathparam));
			map.put(taskname, taskinfo);

			SimpleTypeInformation returninfo = new SimpleTypeInformation(TypeInformationKind.VOID);
			taskinfo.setReturnType(returninfo);
		}
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_ABORT);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(SingleFormattedTextContent
					.createPlaintext("Aborts the build execution with the specified parameter message."));

			SimpleTaskParameterInformation messageparam = new SimpleTaskParameterInformation(taskinfo, "Message");
			messageparam.setAliases(ImmutableUtils.singletonSet(""));
			messageparam.setInformation(
					SingleFormattedTextContent.createPlaintext("Optional message to abort the build execution with."));
			taskinfo.setParameters(ImmutableUtils.singletonList(messageparam));
			map.put(taskname, taskinfo);

			taskinfo.setReturnType(null);
		}
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_INCLUDE);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(SingleFormattedTextContent
					.createPlaintext("Includes the specified build target in the current build execution.\n"
							+ "All parameters apart from Target, Path and "
							+ IncludeTaskFactory.PARAMETER_WORKING_DIRECTORY
							+ " will be passed as input to the specied build target."));

			SimpleTaskParameterInformation targetparam = new SimpleTaskParameterInformation(taskinfo,
					IncludeTaskFactory.PARAMETER_TARGET);
			targetparam.setAliases(ImmutableUtils.singletonSet(""));
			targetparam.setRequired(true);
			targetparam.setTypeInformation(new SimpleTypeInformation(TypeInformationKind.BUILD_TARGET));
			targetparam.setInformation(
					SingleFormattedTextContent.createPlaintext("The name of the build target to invoke."));

			SimpleTaskParameterInformation pathparam = new SimpleTaskParameterInformation(taskinfo,
					IncludeTaskFactory.PARAMETER_PATH);
			pathparam.setAliases(Collections.emptySet());
			pathparam.setInformation(
					SingleFormattedTextContent.createPlaintext("The path to the build file containing the target.\n"
							+ "If not specified, the Target parameter is interpreted to be in the same build script as this task invocation.\n"
							+ "If a relative path is specified, it is resolved against the enclosing directory of this build script, not "
							+ "against the " + IncludeTaskFactory.PARAMETER_WORKING_DIRECTORY + " argument."));

			SimpleTaskParameterInformation workingdirparam = new SimpleTaskParameterInformation(taskinfo,
					IncludeTaskFactory.PARAMETER_WORKING_DIRECTORY);
			workingdirparam.setAliases(Collections.emptySet());
			workingdirparam.setInformation(SingleFormattedTextContent
					.createPlaintext("The working directory to use when invoking the target.\n"
							+ "The specified build target will be invoked as if it executes in the specified working directory. If not specified "
							+ "then the working directory will be the enclosing directory of the build script."));

			SimpleTaskParameterInformation othersparam = new SimpleTaskParameterInformation(taskinfo, "*");
			othersparam.setAliases(Collections.emptySet());
			othersparam.setInformation(
					SingleFormattedTextContent.createPlaintext("Arbitrary parameter to pass to the invoked target.\n"
							+ "The parameter is directly passed to the invoked build target as an input."));

			taskinfo.setParameters(
					ImmutableUtils.asUnmodifiableArrayList(targetparam, pathparam, workingdirparam, othersparam));
			map.put(taskname, taskinfo);

			SimpleTypeInformation returninfo = new SimpleTypeInformation(TypeInformationKind.MAP);
			returninfo.setTypeQualifiedName(BuildTargetTaskResult.class.getCanonicalName());
			returninfo.setTypeSimpleName(BuildTargetTaskResult.class.getSimpleName());
			returninfo.setInformation(
					SingleFormattedTextContent.createPlaintext("The result of a build target invocation.\n"
							+ "Each field is an output value specified by the included build target."));
			taskinfo.setReturnType(returninfo);
		}
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_VAR);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(
					SingleFormattedTextContent.createPlaintext("Dereferences a variable with the given name.\n"
							+ "The variable is constrained to the enclosing build target scope. If the var() task is used in the "
							+ "global scope, then only the variables defined in the global scope are accessible.\n"
							+ "Variables in the global scope are NOT accessible within build targets."));

			SimpleTaskParameterInformation varnameparam = new SimpleTaskParameterInformation(taskinfo, "");
			varnameparam.setRequired(true);
			varnameparam.setInformation(SingleFormattedTextContent.createPlaintext(DEREFERENCE_VAR_PARAM_INFO));
			SimpleTypeInformation typeinfo = new SimpleTypeInformation(TypeInformationKind.STRING);
			typeinfo.setInformation(varnameparam.getInformation());
			varnameparam.setTypeInformation(typeinfo);

			taskinfo.setParameters(ImmutableUtils.singletonList(varnameparam));
			map.put(taskname, taskinfo);

			SimpleTypeInformation returninfo = new SimpleTypeInformation(TypeInformationKind.OBJECT);
			returninfo.setInformation(SingleFormattedTextContent
					.createPlaintext("Reference to a variable in the enclosing build target scope."));
			taskinfo.setReturnType(returninfo);
		}
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_STATIC);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(
					SingleFormattedTextContent.createPlaintext("Dereferences a static variable with the given name.\n"
							+ "A static variable is private to the enclosing build script. Static variables are "
							+ "shared between build targets of the same build script file. This sharing includes the static "
							+ "variables defined in the global scope as well."));

			SimpleTaskParameterInformation varnameparam = new SimpleTaskParameterInformation(taskinfo, "");
			varnameparam.setRequired(true);
			varnameparam.setInformation(SingleFormattedTextContent.createPlaintext(DEREFERENCE_VAR_PARAM_INFO));
			SimpleTypeInformation typeinfo = new SimpleTypeInformation(TypeInformationKind.STRING);
			typeinfo.setInformation(varnameparam.getInformation());
			varnameparam.setTypeInformation(typeinfo);

			taskinfo.setParameters(ImmutableUtils.singletonList(varnameparam));
			map.put(taskname, taskinfo);

			SimpleTypeInformation returninfo = new SimpleTypeInformation(TypeInformationKind.OBJECT);
			returninfo.setInformation(SingleFormattedTextContent
					.createPlaintext("Reference to a static variable in the enclosing build script scope."));
			taskinfo.setReturnType(returninfo);
		}
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_GLOBAL);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(
					SingleFormattedTextContent.createPlaintext("Dereferences a global variable with the given name.\n"
							+ "A global variable is shared between ALL build scripts. This includes build scripts written in "
							+ "other languages.\n"
							+ "Note that global variables are NOT the same as variables or static variables used in the "
							+ "global scope of the build script."));

			SimpleTaskParameterInformation varnameparam = new SimpleTaskParameterInformation(taskinfo, "");
			varnameparam.setRequired(true);
			varnameparam.setInformation(SingleFormattedTextContent.createPlaintext(DEREFERENCE_VAR_PARAM_INFO));
			SimpleTypeInformation typeinfo = new SimpleTypeInformation(TypeInformationKind.STRING);
			typeinfo.setInformation(varnameparam.getInformation());
			varnameparam.setTypeInformation(typeinfo);

			taskinfo.setParameters(ImmutableUtils.singletonList(varnameparam));
			map.put(taskname, taskinfo);

			SimpleTypeInformation returninfo = new SimpleTypeInformation(TypeInformationKind.OBJECT);
			returninfo.setInformation(SingleFormattedTextContent.createPlaintext("Reference to a global variable."));
			taskinfo.setReturnType(returninfo);
		}
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_SEQUENCE);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(
					SingleFormattedTextContent.createPlaintext("Executes the parameter tasks after each other.\n"
							+ "The task takes a list of expressions as its argument, and will execute them in order. After a given expression "
							+ "is evaluated, the next expression will be evaluated, and so on.\n"
							+ "Note that this task DOES NOT guarantee that the expressions are evaluated in order. If the same "
							+ "expressions are present outside of the arguments of this task, out-of-order execution may occurr."));

			SimpleTaskParameterInformation tasksparam = new SimpleTaskParameterInformation(taskinfo, "Tasks");
			tasksparam.setAliases(ImmutableUtils.singletonSet(""));
			tasksparam.setRequired(true);
			tasksparam.setInformation(SingleFormattedTextContent.createPlaintext("A list of expressions to execute.\n"
					+ "The task should be used by directly passing an [a, b, c, ...] expression list as its argument."));
			SimpleTypeInformation typeinfo = new SimpleTypeInformation(TypeInformationKind.COLLECTION);
			typeinfo.setElementTypes(ImmutableUtils.singletonList(null));
			typeinfo.setInformation(tasksparam.getInformation());
			tasksparam.setTypeInformation(typeinfo);

			taskinfo.setParameters(ImmutableUtils.singletonList(tasksparam));
			map.put(taskname, taskinfo);

			SimpleTypeInformation returninfo = new SimpleTypeInformation(TypeInformationKind.COLLECTION);
			returninfo.setElementTypes(ImmutableUtils.singletonList(null));
			returninfo.setInformation(SingleFormattedTextContent
					.createPlaintext("A list of the results of each invoked task by the sequence() task.\n"
							+ "Each entry in the list is the result of the expression at the corresponding index of the input list."));
			taskinfo.setReturnType(returninfo);
		}
		BUILTIN_TASK_INFORMATIONS = ImmutableUtils.unmodifiableNavigableMap(map);
		BUILTIN_TASK_NAMES = new TreeSet<>(BUILTIN_TASK_INFORMATIONS.navigableKeySet());
		BUILTIN_TASK_NAMES.add(TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_DEFAULTS));
	}
	static {
		SimpleTaskInformation taskinfo = new SimpleTaskInformation(SakerParsedModel.TASK_NAME_DEFAULTS);
		taskinfo.setInformation(SingleFormattedTextContent
				.createPlaintext("Declares default parameters for the associated task invocations.\n" + "The "
						+ TaskInvocationSakerTaskFactory.TASKNAME_DEFAULTS
						+ "() task can be used in the defaults file to set the default inputs "
						+ "for the specified parameters.\n"
						+ "The default parameters are added to all task invocations in the same "
						+ "script language configurations during the build execution.\n" + "The "
						+ TaskInvocationSakerTaskFactory.TASKNAME_DEFAULTS
						+ "() task can only be used as a top level declaration in the defaults file."));
		SimpleTaskParameterInformation paraminfo = new SimpleTaskParameterInformation(taskinfo, "");
		paraminfo.setInformation(SingleFormattedTextContent
				.createPlaintext("One or more task names for which the default parameters are defined for."));
		SimpleTypeInformation paramtype = new SimpleTypeInformation(TypeInformationKind.COLLECTION);
		paramtype.setElementTypes(
				ImmutableUtils.singletonList(new SimpleTypeInformation(TypeInformationKind.BUILD_TASK_NAME)));
		paraminfo.setTypeInformation(paramtype);

		taskinfo.setParameters(ImmutableUtils.singletonList(paraminfo));

		DEFAULTS_TASK_INFORMATION = taskinfo;
		DEFAULTS_TASK_UNNAMED_PARAMETER_INFORMATION = paraminfo;
	}

	public static boolean isBuiltinTaskName(String taskname) {
		try {
			return isBuiltinTaskName(TaskName.valueOf(taskname));
		} catch (IllegalArgumentException e) {
			//the task name cannot be parsed
			return false;
		}
	}

	public static boolean isBuiltinTaskName(TaskName taskname) {
		return BUILTIN_TASK_NAMES.contains(taskname);
	}

	@Override
	public NavigableMap<TaskName, TaskInformation> getTasks(String tasknamekeyword) {
		if (ObjectUtils.isNullOrEmpty(tasknamekeyword)) {
			return BUILTIN_TASK_INFORMATIONS;
		}
		NavigableMap<TaskName, TaskInformation> result = new TreeMap<>();
		for (Entry<TaskName, TaskInformation> entry : BUILTIN_TASK_INFORMATIONS.entrySet()) {
			TaskName tn = entry.getKey();
			if (StringUtils.startsWithIgnoreCase(tn.getName(), tasknamekeyword)) {
				result.put(tn, entry.getValue());
			}
		}
		return result;
	}

	@Override
	public NavigableMap<TaskName, TaskInformation> getTaskInformation(TaskName taskname) {
		TaskInformation got = BUILTIN_TASK_INFORMATIONS.get(taskname);
		if (got != null) {
			return ImmutableUtils.singletonNavigableMap(taskname, got);
		}
		return Collections.emptyNavigableMap();
	}
}