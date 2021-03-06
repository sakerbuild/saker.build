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
package testing.saker.api;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;

import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.delta.BuildDelta;
import saker.build.task.identifier.TaskIdentifier;

@SuppressWarnings("unused")
public interface TaskTestMetric extends TestMetric {

	public default void runningProcessTask(List<String> runcmds) {
	}

	public default void runningTask(TaskIdentifier taskid, TaskFactory<?> taskfactory,
			Set<? extends BuildDelta> deltas) {
	}

	public default <R> void taskFinished(TaskIdentifier taskid, TaskFactory<R> taskfactory, R result,
			Map<Object, ?> taggedoutputs, Map<String, ?> metadatas) {
	}

	public default void taskLinesPrinted(TaskIdentifier taskid, List<String> lines) {
	}

	public default void taskAbandoned(TaskIdentifier taskIdentifier) {
	}

	public default void taskRetrievedFromCache(TaskIdentifier taskid) {
	}

	public default void taskPublishedToCache(TaskIdentifier taskid) {
	}

	public default TaskFactory<?> getInjectedTaskFactory(TaskName task) {
		return null;
	}

	public default void classPathLoadedAtPath(Path path) {
	}

	public default void classPathUnloadedAtPath(Path path) {
	}

	public default void serializationWarning(String classname) {
	}

	public default boolean isNativeWatcherEnabled() {
		return false;
	}

	public default void pathWatchingRegistered(Path path, WatchEvent.Kind<?>[] events,
			WatchEvent.Modifier... modifiers) {
	}

	public default void pathWatchingCancelled(Path path) {
	}

	public default boolean isSubtreeWatchingEnabled() {
		return true;
	}

	public default boolean isForceInnerTaskClusterInvocation(Object taskfactory) {
		return false;
	}

	public default boolean isForcedRMILocalFileProvider() {
		return false;
	}
}
