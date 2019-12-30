package saker.build.internal.scripting.language.model;

import java.util.Collections;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.internal.scripting.language.task.TaskInvocationSakerTaskFactory;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.SingleFormattedTextContent;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.scripting.model.info.SimpleTaskInformation;
import saker.build.scripting.model.info.SimpleTaskParameterInformation;
import saker.build.scripting.model.info.SimpleTypeInformation;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.build.task.BuildTargetTaskResult;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

final class BuiltinExternalScriptInformationProvider implements ExternalScriptInformationProvider {
	public static final String INCLUDE_PARAMETER_WORKING_DIRECTORY = "WorkingDirectory";
	public static final String INCLUDE_PARAMETER_TARGET = "Target";
	public static final String INCLUDE_PARAMETER_PATH = "Path";

	public static final String DEREFERENCE_VAR_PARAM_INFO = "The name of the dereferenced variable.";

	public static final ExternalScriptInformationProvider INSTANCE = new BuiltinExternalScriptInformationProvider();

	private static final Set<String> INCLUDE_TASK_PARAMETER_NAMES = ImmutableUtils.makeImmutableNavigableSet(
			new String[] { INCLUDE_PARAMETER_PATH, INCLUDE_PARAMETER_TARGET, INCLUDE_PARAMETER_WORKING_DIRECTORY });

	public static boolean isIncludeTaskParameterName(String name) {
		return name != null && INCLUDE_TASK_PARAMETER_NAMES.contains(name);
	}

	private static final NavigableMap<TaskName, TaskInformation> BUILTIN_TASK_INFORMATIONS;
	static {
		TreeMap<TaskName, TaskInformation> map = new TreeMap<>();
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_PATH);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"Converts a path to absolute representation. Returns the current working directory if no parameter is specified."));

			SimpleTaskParameterInformation pathparam = new SimpleTaskParameterInformation(taskinfo, "Path");
			pathparam.setAliases(ImmutableUtils.singletonSet(""));
			pathparam.setRequired(true);
			pathparam.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"The path to convert to absolute representation."));
			taskinfo.setParameters(ImmutableUtils.singletonList(pathparam));
			map.put(taskname, taskinfo);

			SimpleTypeInformation returninfo = new SimpleTypeInformation(TypeInformationKind.LITERAL);
			returninfo.setTypeQualifiedName(SakerPath.class.getCanonicalName());
			returninfo.setTypeSimpleName(SakerPath.class.getSimpleName());
			returninfo.setInformation(
					new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT, "An execution path."));
			taskinfo.setReturnType(returninfo);
		}
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_PRINT);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"Prints out the given argument message to the output."));

			SimpleTaskParameterInformation pathparam = new SimpleTaskParameterInformation(taskinfo, "");
			pathparam.setRequired(true);
			pathparam.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"The message to print to the output."));
			taskinfo.setParameters(ImmutableUtils.singletonList(pathparam));
			map.put(taskname, taskinfo);

			SimpleTypeInformation returninfo = new SimpleTypeInformation(TypeInformationKind.VOID);
			taskinfo.setReturnType(returninfo);
		}
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_ABORT);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"Aborts the build execution with the specified parameter message."));

			SimpleTaskParameterInformation messageparam = new SimpleTaskParameterInformation(taskinfo, "Message");
			messageparam.setAliases(ImmutableUtils.singletonSet(""));
			messageparam.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"Optional message to abort the build execution with."));
			taskinfo.setParameters(ImmutableUtils.singletonList(messageparam));
			map.put(taskname, taskinfo);

			taskinfo.setReturnType(null);
		}
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_INCLUDE);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"Includes the specified build target in the current build execution.\n"
							+ "All parameters apart from Target, Path and " + INCLUDE_PARAMETER_WORKING_DIRECTORY
							+ " will be passed as input to the specied build target."));

			SimpleTaskParameterInformation targetparam = new SimpleTaskParameterInformation(taskinfo,
					INCLUDE_PARAMETER_TARGET);
			targetparam.setAliases(ImmutableUtils.singletonSet(""));
			targetparam.setRequired(true);
			targetparam.setTypeInformation(new SimpleTypeInformation(TypeInformationKind.BUILD_TARGET));
			targetparam.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"The name of the build target to invoke."));

			SimpleTaskParameterInformation pathparam = new SimpleTaskParameterInformation(taskinfo,
					INCLUDE_PARAMETER_PATH);
			pathparam.setAliases(Collections.emptySet());
			pathparam.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"The path to the build file containing the target.\n"
							+ "If not specified, the Target parameter is interpreted to be in the same build script as this task invocation.\n"
							+ "If a relative path is specified, it is resolved against the enclosing directory of this build script, not "
							+ "against the " + INCLUDE_PARAMETER_WORKING_DIRECTORY + " argument."));

			SimpleTaskParameterInformation workingdirparam = new SimpleTaskParameterInformation(taskinfo,
					INCLUDE_PARAMETER_WORKING_DIRECTORY);
			workingdirparam.setAliases(Collections.emptySet());
			workingdirparam.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"The working directory to use when invoking the target.\n"
							+ "The specified build target will be invoked as if it executes in the specified working directory. If not specified "
							+ "then the working directory will be the enclosing directory of the build script."));

			SimpleTaskParameterInformation othersparam = new SimpleTaskParameterInformation(taskinfo, "*");
			othersparam.setAliases(Collections.emptySet());
			othersparam.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"Arbitrary parameter to pass to the invoked target.\n"
							+ "The parameter is directly passed to the invoked build target as an input."));

			taskinfo.setParameters(
					ImmutableUtils.asUnmodifiableArrayList(targetparam, pathparam, workingdirparam, othersparam));
			map.put(taskname, taskinfo);

			SimpleTypeInformation returninfo = new SimpleTypeInformation(TypeInformationKind.MAP);
			returninfo.setTypeQualifiedName(BuildTargetTaskResult.class.getCanonicalName());
			returninfo.setTypeSimpleName(BuildTargetTaskResult.class.getSimpleName());
			returninfo.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"The result of a build target invocation.\n"
							+ "Each field is an output value specified by the included build target."));
			taskinfo.setReturnType(returninfo);
		}
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_VAR);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"Dereferences a variable with the given name.\n"
							+ "The variable is constrained to the enclosing build target scope. If the var() task is used in the "
							+ "global scope, then only the variables defined in the global scope are accessible.\n"
							+ "Variables in the global scope are NOT accessible within build targets."));

			SimpleTaskParameterInformation varnameparam = new SimpleTaskParameterInformation(taskinfo, "");
			varnameparam.setRequired(true);
			varnameparam.setInformation(
					new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT, DEREFERENCE_VAR_PARAM_INFO));
			SimpleTypeInformation typeinfo = new SimpleTypeInformation(TypeInformationKind.STRING);
			typeinfo.setInformation(varnameparam.getInformation());
			varnameparam.setTypeInformation(typeinfo);

			taskinfo.setParameters(ImmutableUtils.singletonList(varnameparam));
			map.put(taskname, taskinfo);

			SimpleTypeInformation returninfo = new SimpleTypeInformation(TypeInformationKind.OBJECT);
			returninfo.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"Reference to a variable in the enclosing build target scope."));
			taskinfo.setReturnType(returninfo);
		}
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_STATIC);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"Dereferences a static variable with the given name.\n"
							+ "A static variable is private to the enclosing build script. Static variables are "
							+ "shared between build targets of the same build script file. This sharing includes the static "
							+ "variables defined in the global scope as well."));

			SimpleTaskParameterInformation varnameparam = new SimpleTaskParameterInformation(taskinfo, "");
			varnameparam.setRequired(true);
			varnameparam.setInformation(
					new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT, DEREFERENCE_VAR_PARAM_INFO));
			SimpleTypeInformation typeinfo = new SimpleTypeInformation(TypeInformationKind.STRING);
			typeinfo.setInformation(varnameparam.getInformation());
			varnameparam.setTypeInformation(typeinfo);

			taskinfo.setParameters(ImmutableUtils.singletonList(varnameparam));
			map.put(taskname, taskinfo);

			SimpleTypeInformation returninfo = new SimpleTypeInformation(TypeInformationKind.OBJECT);
			returninfo.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"Reference to a static variable in the enclosing build script scope."));
			taskinfo.setReturnType(returninfo);
		}
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_GLOBAL);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"Dereferences a global variable with the given name.\n"
							+ "A global variable is shared between ALL build scripts. This includes build scripts written in "
							+ "other languages.\n"
							+ "Note that global variables are NOT the same as variables or static variables used in the "
							+ "global scope of the build script."));

			SimpleTaskParameterInformation varnameparam = new SimpleTaskParameterInformation(taskinfo, "");
			varnameparam.setRequired(true);
			varnameparam.setInformation(
					new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT, DEREFERENCE_VAR_PARAM_INFO));
			SimpleTypeInformation typeinfo = new SimpleTypeInformation(TypeInformationKind.STRING);
			typeinfo.setInformation(varnameparam.getInformation());
			varnameparam.setTypeInformation(typeinfo);

			taskinfo.setParameters(ImmutableUtils.singletonList(varnameparam));
			map.put(taskname, taskinfo);

			SimpleTypeInformation returninfo = new SimpleTypeInformation(TypeInformationKind.OBJECT);
			returninfo.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"Reference to a global variable."));
			taskinfo.setReturnType(returninfo);
		}
		{
			TaskName taskname = TaskName.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_SEQUENCE);
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"Executes the parameter tasks after each other.\n"
							+ "The task takes a list of expressions as its argument, and will execute them in order. After a given expression "
							+ "is evaluated, the next expression will be evaluated, and so on.\n"
							+ "Note that this task DOES NOT guarantee that the expressions are evaluated in order. If the same "
							+ "expressions are present outside of the arguments of this task, out-of-order execution may occurr."));

			SimpleTaskParameterInformation tasksparam = new SimpleTaskParameterInformation(taskinfo, "Tasks");
			tasksparam.setAliases(ImmutableUtils.singletonSet(""));
			tasksparam.setRequired(true);
			tasksparam.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"A list of expressions to execute.\n"
							+ "The task should be used by directly passing an [a, b, c, ...] expression list as its argument."));
			SimpleTypeInformation typeinfo = new SimpleTypeInformation(TypeInformationKind.COLLECTION);
			typeinfo.setElementTypes(ImmutableUtils.singletonList(null));
			typeinfo.setInformation(tasksparam.getInformation());
			tasksparam.setTypeInformation(typeinfo);

			taskinfo.setParameters(ImmutableUtils.singletonList(tasksparam));
			map.put(taskname, taskinfo);

			SimpleTypeInformation returninfo = new SimpleTypeInformation(TypeInformationKind.COLLECTION);
			returninfo.setElementTypes(ImmutableUtils.singletonList(null));
			returninfo.setInformation(new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
					"A list of the results of each invoked task by the sequence() task.\n"
							+ "Each entry in the list is the result of the expression at the corresponding index of the input list."));
			taskinfo.setReturnType(returninfo);
		}
		BUILTIN_TASK_INFORMATIONS = ImmutableUtils.unmodifiableNavigableMap(map);
	}

	@Override
	public NavigableMap<TaskName, TaskInformation> getTasks(String tasknamekeyword) {
		if (ObjectUtils.isNullOrEmpty(tasknamekeyword)) {
			return BUILTIN_TASK_INFORMATIONS;
		}
		NavigableMap<TaskName, TaskInformation> result = new TreeMap<>();
		for (Entry<TaskName, TaskInformation> entry : BUILTIN_TASK_INFORMATIONS.entrySet()) {
			TaskName tn = entry.getKey();
			if (tn.getName().startsWith(tasknamekeyword)) {
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