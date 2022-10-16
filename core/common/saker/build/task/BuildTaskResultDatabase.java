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
package saker.build.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;

import saker.build.file.path.SakerPath;
import saker.build.ide.configuration.IDEConfiguration;
import saker.build.ide.configuration.SimpleIDEConfiguration;
import saker.build.scripting.ScriptInformationProvider;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.trace.InternalBuildTraceImpl;
import testing.saker.build.flag.TestFlag;

public class BuildTaskResultDatabase implements Externalizable {
	private static final long serialVersionUID = 1L;

	private static final BuildTaskResultDatabase EMPTY_INSTANCE = new BuildTaskResultDatabase();
	static {
		EMPTY_INSTANCE.taskIdTaskResults = Collections.emptyMap();
		EMPTY_INSTANCE.abandonedTaskIdResults = Collections.emptyMap();
		EMPTY_INSTANCE.cacheableTaskIdResults = Collections.emptyMap();
		EMPTY_INSTANCE.scriptInformationProviders = Collections.emptyNavigableMap();
		EMPTY_INSTANCE.ideConfigs = Collections.emptySet();
	}

	protected Map<TaskIdentifier, TaskExecutionResult<?>> taskIdTaskResults;
	protected NavigableMap<SakerPath, ScriptInformationProvider> scriptInformationProviders;

	protected transient Map<TaskIdentifier, TaskExecutionResult<?>> abandonedTaskIdResults;
	protected transient Map<TaskIdentifier, TaskExecutionResult<?>> cacheableTaskIdResults;

	private transient Collection<IDEConfiguration> ideConfigs = null;

	/**
	 * For {@link Externalizable}.
	 */
	public BuildTaskResultDatabase() {
	}

	public static BuildTaskResultDatabase empty() {
		return EMPTY_INSTANCE;
	}

	public static BuildTaskResultDatabase create(Map<TaskIdentifier, TaskExecutionResult<?>> taskIdTaskResults,
			Map<TaskIdentifier, TaskExecutionResult<?>> abandonedTaskIdResults,
			Map<TaskIdentifier, TaskExecutionResult<?>> cacheableTaskIdResults,
			NavigableMap<SakerPath, ScriptInformationProvider> scriptInformationProviders) {
		BuildTaskResultDatabase result = new BuildTaskResultDatabase();
		result.taskIdTaskResults = new HashMap<>(taskIdTaskResults);
		result.abandonedTaskIdResults = new HashMap<>(abandonedTaskIdResults);
		result.cacheableTaskIdResults = new HashMap<>(cacheableTaskIdResults);
		result.scriptInformationProviders = ImmutableUtils.makeImmutableNavigableMap(scriptInformationProviders);
		return result;
	}

	public Collection<? extends IDEConfiguration> getIDEConfigurations() {
		if (ideConfigs != null) {
			return ideConfigs;
		}
		Collection<IDEConfiguration> result = new HashSet<>();
		for (TaskExecutionResult<?> taskres : taskIdTaskResults.values()) {
			Collection<IDEConfiguration> ideconfigs = taskres.getIDEConfigurations();
			if (!ideconfigs.isEmpty()) {
				for (IDEConfiguration idec : ideconfigs) {
					result.add(new SimpleIDEConfiguration(idec));
				}
			}
		}
		ideConfigs = result;
		return result;
	}

	public NavigableMap<SakerPath, ? extends ScriptInformationProvider> getScriptInformationProviders() {
		return scriptInformationProviders;
	}

	public Map<TaskIdentifier, TaskExecutionResult<?>> getTaskIdTaskResults() {
		return taskIdTaskResults;
	}

	public Map<TaskIdentifier, TaskExecutionResult<?>> getAbandonedTaskIdResults() {
		return abandonedTaskIdResults;
	}

	public Map<TaskIdentifier, TaskExecutionResult<?>> takeAbandonedTaskIdResults() {
		Map<TaskIdentifier, TaskExecutionResult<?>> result = abandonedTaskIdResults;
		this.abandonedTaskIdResults = Collections.emptyMap();
		return result;
	}

	public Map<TaskIdentifier, TaskExecutionResult<?>> takeCacheableTaskIdResults() {
		Map<TaskIdentifier, TaskExecutionResult<?>> result = cacheableTaskIdResults;
		this.cacheableTaskIdResults = Collections.emptyMap();
		return result;
	}

	public Set<TaskIdentifier> getTaskIds() {
		return ImmutableUtils.unmodifiableSet(taskIdTaskResults.keySet());
	}

	public Object getTaskResult(TaskIdentifier taskid) {
		TaskExecutionResult<?> ter = taskIdTaskResults.get(taskid);
		if (ter == null) {
			throw new IllegalArgumentException("Task result not found for id: " + taskid);
		}
		List<Throwable> abortexceptions = ter.getAbortExceptions();
		Throwable failexc = ter.getFailCauseException();
		if (failexc != null || !ObjectUtils.isNullOrEmpty(abortexceptions)) {
			throw TaskExecutionManager.createFailException(taskid, failexc, abortexceptions);
		}
		return ter.getOutput();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		Map<TaskIdentifier, TaskExecutionResult<?>> results = taskIdTaskResults;
		out.writeInt(results.size());
		for (Entry<TaskIdentifier, TaskExecutionResult<?>> entry : results.entrySet()) {
			out.writeObject(entry.getKey());
			out.writeObject(entry.getValue());
		}
		SerialUtils.writeExternalMap(out, scriptInformationProviders);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.taskIdTaskResults = new HashMap<>();
		int resc = in.readInt();
		while (resc-- > 0) {
			TaskIdentifier taskid;
			try {
				taskid = (TaskIdentifier) in.readObject();
			} catch (ClassNotFoundException | IOException | ClassCastException e) {
				//catch classcastexception too, if the class for the key is modified to no longer extend it
				//failed to read key
				//read the value from the stream, so next entry can be read
				try {
					in.readObject();
				} catch (ClassNotFoundException | IOException e2) {
					e.addSuppressed(e2);
					//failed to read value, continue to the next entry
				}
				if (TestFlag.ENABLED) {
					System.err.println(getClass().getSimpleName() + " readExternal TaskIdentifier: " + e);
				}
				InternalBuildTraceImpl.serializationException(e);
				continue;
			}
			TaskExecutionResult<?> valexcres;
			try {
				valexcres = (TaskExecutionResult<?>) in.readObject();
			} catch (ClassNotFoundException | IOException e) {
				if (TestFlag.ENABLED) {
					System.err.println(getClass().getSimpleName() + " readExternal TaskExecutionResult: " + e);
				}
				InternalBuildTraceImpl.serializationException(e);
				//failed to read value
				continue;
			}
			//successfully read key and value, put it in the map
			taskIdTaskResults.put(taskid, valexcres);
		}
		this.scriptInformationProviders = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		this.abandonedTaskIdResults = Collections.emptyMap();
		this.cacheableTaskIdResults = Collections.emptyMap();
	}

}
