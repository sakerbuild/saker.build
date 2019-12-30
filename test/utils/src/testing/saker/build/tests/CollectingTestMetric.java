package testing.saker.build.tests;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.delta.BuildDelta;
import saker.build.task.identifier.TaskIdentifier;
import testing.saker.api.TaskTestMetric;

public class CollectingTestMetric implements TaskTestMetric {

	protected Map<TaskIdentifier, TaskFactory<?>> runTaskIdFactories = new ConcurrentHashMap<>();
	protected Map<TaskIdentifier, Set<? extends BuildDelta>> runTaskIdDeltas = new ConcurrentHashMap<>();
	protected Map<TaskIdentifier, Object> taskIdResults = Collections.synchronizedMap(new HashMap<>());
	protected Map<TaskIdentifier, Map<Object, ?>> taskIdTaggedResults = Collections.synchronizedMap(new HashMap<>());
	protected Map<TaskIdentifier, Map<String, ?>> taskIdMetaDatas = Collections.synchronizedMap(new HashMap<>());
	protected Set<TaskIdentifier> abandonedTasks = ConcurrentHashMap.newKeySet();
	protected Set<TaskIdentifier> cacheRetrievedTasks = ConcurrentHashMap.newKeySet();
	protected Set<TaskIdentifier> cachePublishedTasks = ConcurrentHashMap.newKeySet();
	protected Map<TaskIdentifier, List<String>> taskPrintedLines = Collections.synchronizedMap(new HashMap<>());

	protected Map<TaskName, TaskFactory<?>> injectedTaskFactories = new TreeMap<>();
	protected Set<String> warnedSerializations = new ConcurrentSkipListSet<>();

	//this must be static as the classpaths need to be tracked between metric instantiations
	protected static final Map<Path, Integer> loadedClassPaths = new ConcurrentSkipListMap<>();

	public void setInjectedTaskFactories(Map<TaskName, TaskFactory<?>> injectedTaskFactories) {
		this.injectedTaskFactories = injectedTaskFactories;
	}

	public Map<TaskName, TaskFactory<?>> getInjectedTaskFactories() {
		return injectedTaskFactories;
	}

	@Override
	public TaskFactory<?> getInjectedTaskFactory(TaskName task) {
		return injectedTaskFactories.get(task);
	}

	@Override
	public void runningTask(TaskIdentifier taskid, TaskFactory<?> taskfactory, Set<? extends BuildDelta> deltas) {
		runTaskIdFactories.put(taskid, taskfactory);
		runTaskIdDeltas.put(taskid, deltas);
	}

	@Override
	public <R> void taskFinished(TaskIdentifier taskid, TaskFactory<R> taskfactory, R result,
			Map<Object, ?> taggedoutputs, Map<String, ?> metadatas) {
		taskIdResults.put(taskid, result);
		taskIdTaggedResults.put(taskid, taggedoutputs);
		taskIdMetaDatas.put(taskid, metadatas);
	}

	@Override
	public void taskLinesPrinted(TaskIdentifier taskid, List<String> lines) {
		taskPrintedLines.putIfAbsent(taskid, lines);
	}

	@Override
	public void classPathLoadedAtPath(Path path) {
		loadedClassPaths.compute(path, (p, c) -> {
			if (c == null) {
				return 1;
			}
			return c.intValue() + 1;
		});
	}

	@Override
	public void classPathUnloadedAtPath(Path path) {
		loadedClassPaths.compute(path, (p, c) -> {
			if (c == null) {
				throw new AssertionError("Trying to unload classpath which was not loaded before: " + p);
			}
			if (c.intValue() == 1) {
				return null;
			}
			return c - 1;
		});
	}

	public Map<Path, Integer> getLoadedClassPaths() {
		return loadedClassPaths;
	}

	@Override
	public void taskAbandoned(TaskIdentifier taskIdentifier) {
		abandonedTasks.add(taskIdentifier);
	}

	@Override
	public void taskRetrievedFromCache(TaskIdentifier taskid) {
		cacheRetrievedTasks.add(taskid);
	}

	@Override
	public void taskPublishedToCache(TaskIdentifier taskid) {
		cachePublishedTasks.add(taskid);
	}

	@Override
	public void serializationWarning(String classname) {
		warnedSerializations.add(classname);
	}

	public Set<String> getWarnedSerializations() {
		return warnedSerializations;
	}

	public Set<TaskIdentifier> getCachePublishedTasks() {
		return cachePublishedTasks;
	}

	public Set<TaskIdentifier> getCacheRetrievedTasks() {
		return cacheRetrievedTasks;
	}

	public Set<TaskIdentifier> getAbandonedTasks() {
		return abandonedTasks;
	}

	public Map<TaskIdentifier, TaskFactory<?>> getRunTaskIdFactories() {
		return runTaskIdFactories;
	}

	public Map<TaskIdentifier, Object> getRunTaskIdResults() {
		return taskIdResults;
	}

	public Map<TaskIdentifier, Map<Object, ?>> getTaskIdTaggedResults() {
		return taskIdTaggedResults;
	}

	public Map<TaskIdentifier, Set<? extends BuildDelta>> getRunTaskIdDeltas() {
		return runTaskIdDeltas;
	}

	public Map<TaskIdentifier, Map<String, ?>> getTaskIdMetaDatas() {
		return taskIdMetaDatas;
	}

	public Map<TaskIdentifier, List<String>> getTaskPrintedLines() {
		return taskPrintedLines;
	}

	public Set<?> getAllPrintedTaskLines() {
		Set<Object> result = new LinkedHashSet<>();
		for (List<?> lines : taskPrintedLines.values()) {
			result.addAll(lines);
		}
		return result;
	}
}
