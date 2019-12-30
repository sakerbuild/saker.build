package saker.build.runtime.environment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import saker.build.exception.ScriptPositionedExceptionView;
import saker.build.exception.ScriptPositionedExceptionView.ScriptPositionStackTraceElement;
import saker.build.file.path.SakerPath;
import saker.build.scripting.ScriptInformationProvider;
import saker.build.scripting.ScriptPosition;
import saker.build.task.BuildTaskResultDatabase;
import saker.build.task.TaskExecutionManager.SpawnedResultTask;
import saker.build.task.TaskExecutionManager.TaskResultCollectionImpl;
import saker.build.task.TaskResultCollection;
import saker.build.task.identifier.BuildFileTargetTaskIdentifier;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.exc.ExceptionView;

public class BuildTaskExecutionResultImpl implements BuildTaskExecutionResult {
	private final ResultKind resultKind;
	private TaskResultCollection resultCollection;
	private ScriptExceptionInformationResolver scriptInformationResolver;
	private Throwable exception;

	protected BuildTaskExecutionResultImpl(ResultKind resultKind) {
		this.resultKind = resultKind;
	}

	public static BuildTaskExecutionResultImpl createInitializationFailed(Throwable exc) {
		BuildTaskExecutionResultImpl result = new BuildTaskExecutionResultImpl(ResultKind.INITIALIZATION_ERROR);
		result.exception = exc;
		return result;
	}

	public static BuildTaskExecutionResultImpl createFailed(BuildTaskResultDatabase taskresultdatabase,
			TaskResultCollectionImpl resultcollection, Throwable exc, TaskIdentifier roottaskid) {
		BuildTaskExecutionResultImpl result = new BuildTaskExecutionResultImpl(ResultKind.FAILURE);
		result.resultCollection = resultcollection;
		result.exception = exc;
		result.scriptInformationResolver = new ScriptExceptionInformationResolver(
				taskresultdatabase.getScriptInformationProviders(), resultcollection, roottaskid);
		return result;
	}

	public static BuildTaskExecutionResultImpl createSuccessful(BuildTaskResultDatabase taskresultdatabase,
			TaskResultCollectionImpl resultcollection, TaskIdentifier roottaskid) {
		BuildTaskExecutionResultImpl result = new BuildTaskExecutionResultImpl(ResultKind.SUCCESSFUL);
		result.resultCollection = resultcollection;
		result.scriptInformationResolver = new ScriptExceptionInformationResolver(
				taskresultdatabase.getScriptInformationProviders(), resultcollection, roottaskid);
		return result;
	}

	@Override
	public TaskResultCollection getTaskResultCollection() {
		return resultCollection;
	}

	@Override
	public ResultKind getResultKind() {
		return resultKind;
	}

	@Override
	public ExceptionView getExceptionView() {
		if (exception == null) {
			return null;
		}
		return ExceptionView.create(exception);
	}

	@Override
	public ScriptPositionedExceptionView getPositionedExceptionView() {
		if (exception == null) {
			return null;
		}
		return createPositionedExceptionView(exception);
	}

	private ScriptPositionedExceptionView createPositionedExceptionView(Throwable e) {
		if (scriptInformationResolver == null) {
			return ScriptPositionedExceptionView.create(e);
		}
		return ScriptPositionedExceptionView.create(e, tid -> scriptInformationResolver.getPositionStackTrace(tid));
	}

	@Override
	public Throwable getException() {
		return exception;
	}

	public void addException(Throwable e) {
		if (this.exception == null) {
			this.exception = e;
		} else {
			this.exception.addSuppressed(e);
		}
	}

	private static class ScriptExceptionInformationResolver {
		private Map<SakerPath, ? extends ScriptInformationProvider> informationProviders;
		private TaskResultCollectionImpl results;
		private TaskIdentifier rootTaskId;

		public ScriptExceptionInformationResolver(Map<SakerPath, ? extends ScriptInformationProvider> positionlocators,
				TaskResultCollectionImpl resultcollection, TaskIdentifier roottaskid) {
			this.informationProviders = positionlocators;
			this.results = resultcollection;
			this.rootTaskId = roottaskid;
		}

		private List<SpawnedResultTask> getTaskOriginResultTrace(TaskIdentifier targettaskid) {
			if (targettaskid.equals(rootTaskId)) {
				return Collections.emptyList();
			}
			Map<TaskIdentifier, SpawnedResultTask> taskidresults = this.results.getSpawnedTasks();
			SpawnedResultTask rootres = taskidresults.get(rootTaskId);
			if (rootres == null) {
				return Collections.emptyList();
			}
			LinkedList<SpawnedResultTask> result = new LinkedList<>();
			result.add(rootres);
			Set<SpawnedResultTask> examined = ObjectUtils.newIdentityHashSet();
			if (collectTaskOriginResultTrace(result, targettaskid, examined)) {
				SpawnedResultTask targetres = taskidresults.get(targettaskid);
				if (targetres != null) {
					result.add(targetres);
				}
				return result;
			}
			return Collections.emptyList();
		}

		private boolean collectTaskOriginResultTrace(LinkedList<SpawnedResultTask> resultstack,
				TaskIdentifier targettaskid, Set<SpawnedResultTask> examinedresults) {
			SpawnedResultTask currentres = resultstack.getLast();
			if (!examinedresults.add(currentres)) {
				return false;
			}
			for (TaskIdentifier createdtaskid : currentres.getChildren().keySet()) {
				if (createdtaskid.equals(targettaskid)) {
					return true;
				}
				SpawnedResultTask createdres = this.results.getSpawnedTasks().get(createdtaskid);
				if (createdres != null) {
					resultstack.addLast(createdres);
					if (collectTaskOriginResultTrace(resultstack, targettaskid, examinedresults)) {
						return true;
					}
					resultstack.removeLast();
				}
			}
			return false;
		}

		public ScriptPositionedExceptionView.ScriptPositionStackTraceElement[] getPositionStackTrace(
				TaskIdentifier taskid) {
			List<SpawnedResultTask> originstacktrace = getTaskOriginResultTrace(taskid);
			ArrayList<ScriptPositionStackTraceElement> resultlist = new ArrayList<>(originstacktrace.size());
			SakerPath currentfilepath = null;
			for (SpawnedResultTask stresult : originstacktrace) {
				TaskIdentifier restaskid = stresult.getTaskIdentifier();
				SakerPath bfpath = lookupBuildFilePathForTaskId(restaskid);
				if (bfpath != null) {
					currentfilepath = bfpath;
				} else {
					bfpath = currentfilepath;
				}
				if (bfpath != null) {
					ScriptPosition pos = lookupPositionForTaskId(restaskid, bfpath);
					if (pos != null) {
						ScriptPositionStackTraceElement stelem = new ScriptPositionStackTraceElement(currentfilepath,
								pos);
						if (!resultlist.isEmpty()) {
							ScriptPositionStackTraceElement last = resultlist.get(resultlist.size() - 1);
							if (!stelem.equals(last)) {
								//do not add a single element twice consecutively
								resultlist.add(stelem);
							}
						} else {
							resultlist.add(stelem);
						}
					}
				}
			}
			ScriptPositionStackTraceElement[] result = resultlist
					.toArray(ScriptPositionedExceptionView.EMPTY_STACKTRACE_ARRAY);
			ArrayUtils.reverse(result);
			return result;
		}

		private static SakerPath lookupBuildFilePathForTaskId(TaskIdentifier taskid) {
			if (taskid instanceof BuildFileTargetTaskIdentifier) {
				BuildFileTargetTaskIdentifier bftid = (BuildFileTargetTaskIdentifier) taskid;
				return bftid.getFilePath();
			}
			return null;
		}

		private ScriptPosition lookupPositionForTaskId(TaskIdentifier taskid, SakerPath bfpath) {
			if (taskid instanceof BuildFileTargetTaskIdentifier) {
				BuildFileTargetTaskIdentifier bftid = (BuildFileTargetTaskIdentifier) taskid;
				ScriptInformationProvider infoprovider = informationProviders.get(bftid.getFilePath());
				if (infoprovider != null) {
					return infoprovider.getTargetPosition(bftid.getTargetName());
				}
				return null;
			}
			ScriptInformationProvider locator = informationProviders.get(bfpath);
			if (locator != null) {
				ScriptPosition pos = locator.getScriptPosition(taskid);
				if (pos != null) {
					return pos;
				}
			}
			return null;
		}
	}
}
