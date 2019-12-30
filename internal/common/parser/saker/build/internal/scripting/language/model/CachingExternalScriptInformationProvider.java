package saker.build.internal.scripting.language.model;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.scripting.model.info.LiteralInformation;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.LazySupplier;

public class CachingExternalScriptInformationProvider implements ExternalScriptInformationProvider {
	private final ExternalScriptInformationProvider provider;
	private LazySupplier<Map<TaskName, ? extends TaskInformation>> nullKeyWordTasks;
	private LazySupplier<Map<TaskName, ? extends TaskInformation>> nullKeyWordTaskInformations;
	private ConcurrentSkipListMap<String, Map<TaskName, ? extends TaskInformation>> tasks = new ConcurrentSkipListMap<>();
	private ConcurrentSkipListMap<TaskName, Map<TaskName, ? extends TaskInformation>> taskInformations = new ConcurrentSkipListMap<>();

	private ConcurrentSkipListMap<TaskNameParameterEntry, Map<TaskName, ? extends TaskParameterInformation>> taskParameterInformations = new ConcurrentSkipListMap<>();

	public CachingExternalScriptInformationProvider(ExternalScriptInformationProvider provider) {
		this.provider = provider;
		this.nullKeyWordTasks = LazySupplier.of(() -> provider.getTasks(null));
		this.nullKeyWordTaskInformations = LazySupplier.of(() -> provider.getTaskInformation(null));
	}

	@Override
	public Map<TaskName, ? extends TaskInformation> getTasks(String tasknamekeyword) {
		if (tasknamekeyword == null) {
			return nullKeyWordTasks.get();
		}
		return tasks.computeIfAbsent(tasknamekeyword, this.provider::getTasks);
	}

	@Override
	public Map<TaskName, ? extends TaskInformation> getTaskInformation(TaskName taskname) {
		if (taskname == null) {
			return nullKeyWordTaskInformations.get();
		}
		return taskInformations.computeIfAbsent(taskname, this.provider::getTaskInformation);
	}

	@Override
	public Map<TaskName, ? extends TaskParameterInformation> getTaskParameterInformation(TaskName taskname,
			String parametername) {
		return taskParameterInformations.computeIfAbsent(new TaskNameParameterEntry(taskname, parametername),
				k -> this.provider.getTaskParameterInformation(k.taskName, k.parameterName));
	}

	@Override
	public Collection<? extends LiteralInformation> getLiterals(String literalkeyword, TypeInformation typecontext) {
		// no caching for this
		return this.provider.getLiterals(literalkeyword, typecontext);
	}

	@Override
	public LiteralInformation getLiteralInformation(String literal, TypeInformation typecontext) {
		// no caching for this
		return this.provider.getLiteralInformation(literal, typecontext);
	}

	private static class TaskNameParameterEntry implements Comparable<TaskNameParameterEntry> {
		final TaskName taskName;
		final String parameterName;

		public TaskNameParameterEntry(TaskName taskName, String parameterName) {
			this.taskName = taskName;
			this.parameterName = parameterName;
		}

		@Override
		public int compareTo(TaskNameParameterEntry o) {
			int cmp = ObjectUtils.compareNullsFirst(this.taskName, o.taskName);
			if (cmp != 0) {
				return cmp;
			}
			cmp = ObjectUtils.compareNullsFirst(this.parameterName, o.parameterName);
			if (cmp != 0) {
				return cmp;
			}
			return 0;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((parameterName == null) ? 0 : parameterName.hashCode());
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
			TaskNameParameterEntry other = (TaskNameParameterEntry) obj;
			if (parameterName == null) {
				if (other.parameterName != null)
					return false;
			} else if (!parameterName.equals(other.parameterName))
				return false;
			if (taskName == null) {
				if (other.taskName != null)
					return false;
			} else if (!taskName.equals(other.taskName))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "TaskNameParameterEntry[" + (taskName != null ? "taskName=" + taskName + ", " : "")
					+ (parameterName != null ? "parameterName=" + parameterName : "") + "]";
		}
	}
}
