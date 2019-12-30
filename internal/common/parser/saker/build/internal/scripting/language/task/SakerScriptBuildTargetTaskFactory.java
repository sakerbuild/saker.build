package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import saker.build.file.path.SakerPath;
import saker.build.internal.scripting.language.task.operators.AssignmentTaskFactory;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.BuildTargetTask;
import saker.build.task.BuildTargetTaskFactory;
import saker.build.task.BuildTargetTaskResult;
import saker.build.task.SimpleBuildTargetTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionUtilities;
import saker.build.task.exception.TaskParameterException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class SakerScriptBuildTargetTaskFactory implements BuildTargetTaskFactory, Externalizable {
	private static final long serialVersionUID = 1L;

	protected Set<SakerTaskFactory> factories = new HashSet<>();
	protected NavigableSet<String> outputNames = new TreeSet<>();
	protected NavigableMap<String, SakerTaskFactory> targetParameters = new TreeMap<>();
	protected SakerPath scriptPath;
	protected Set<SakerTaskFactory> globalExpressions;

	/**
	 * For {@link Externalizable}.
	 */
	public SakerScriptBuildTargetTaskFactory() {
	}

	public SakerScriptBuildTargetTaskFactory(SakerPath scriptPath) {
		this.scriptPath = scriptPath;
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

				TaskExecutionUtilities taskutils = taskcontext.getTaskUtilities();
				if (!ObjectUtils.isNullOrEmpty(globalExpressions)) {
					TaskIdentifier buildfiletaskid = new GlobalExpressionScopeRootTaskIdentifier(scriptPath);
					for (SakerTaskFactory factory : globalExpressions) {
						taskutils.startTaskFuture(new SakerScriptTaskIdentifier(buildfiletaskid, factory), factory);
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
								throw new TaskParameterException("Build target input parameter: " + pname + " is missing.", thistaskid);
							}
							right = fac;
						}
						SakerScriptTaskIdentifier righttaskid = new SakerScriptTaskIdentifier(thistaskid, right);
						taskutils.startTaskFuture(righttaskid, right);
						AssignmentTaskFactory.startAssignmentTask(taskcontext, thistaskid, pname, righttaskid);
					}
				}
				if (!factories.isEmpty()) {
					for (SakerTaskFactory factory : factories) {
						taskutils.startTaskFuture(new SakerScriptTaskIdentifier(thistaskid, factory), factory);
					}
				}
				NavigableMap<String, TaskIdentifier> resulttaskids = new TreeMap<>();
				if (!ObjectUtils.isNullOrEmpty(outputNames)) {
					for (String outname : outputNames) {
						TaskIdentifier outtaskid = AssignmentTaskFactory.createAssignTaskIdentifier(thistaskid,
								outname);

						//XXX as composed structured task results have been introduced, we might not need to unsaker the results
						// we need to unsaker the result, so the most appropriate data type gets returned
						//    in case of a variable dereference, the value of that variable is returned
						//    if the dereference result was returned then the receiving script would interpret that as a structured result
						//         but if the real value is a list, then it should be interpreted as a structured list.
						//         this is not possible without unsyntaxing the result
						UnsakerFutureTaskFactory unsyntaxer = new UnsakerFutureTaskFactory(outtaskid);
						TaskIdentifier restaskid = new UnsakerTaskFactoryTaskIdentifier(unsyntaxer);
						taskutils.startTaskFuture(restaskid, unsyntaxer);
						resulttaskids.put(outname, restaskid);
					}
				}
				SimpleBuildTargetTaskResult result = new SimpleBuildTargetTaskResult(resulttaskids);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}

			@Override
			public void initParameters(TaskContext taskcontext,
					NavigableMap<String, ? extends TaskIdentifier> targetparameters) {
				this.taskTargetParameters = targetparameters;
			}
		};
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((factories == null) ? 0 : factories.hashCode());
		result = prime * result + ((globalExpressions == null) ? 0 : globalExpressions.hashCode());
		result = prime * result + ((outputNames == null) ? 0 : outputNames.hashCode());
		result = prime * result + ((scriptPath == null) ? 0 : scriptPath.hashCode());
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
		if (scriptPath == null) {
			if (other.scriptPath != null)
				return false;
		} else if (!scriptPath.equals(other.scriptPath))
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
		return "SakerScriptBuildTargetTaskFactory[" + (factories != null ? "factories=" + factories + ", " : "")
				+ (outputNames != null ? "outputNames=" + outputNames + ", " : "")
				+ (targetParameters != null ? "targetParameters=" + targetParameters + ", " : "")
				+ (scriptPath != null ? "scriptPath=" + scriptPath + ", " : "")
				+ (globalExpressions != null ? "globalExpressions=" + globalExpressions : "") + "]";
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalMap(out, targetParameters);
		SerialUtils.writeExternalCollection(out, factories);
		SerialUtils.writeExternalCollection(out, outputNames);
		SerialUtils.writeExternalCollection(out, globalExpressions);
		out.writeObject(scriptPath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		targetParameters = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		factories = SerialUtils.readExternalImmutableHashSet(in);
		outputNames = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		globalExpressions = SerialUtils.readExternalImmutableHashSet(in);
		scriptPath = (SakerPath) in.readObject();
	}

}
