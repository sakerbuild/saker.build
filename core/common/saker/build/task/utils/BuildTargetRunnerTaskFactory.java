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
package saker.build.task.utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.BuildTargetTask;
import saker.build.task.BuildTargetTaskFactory;
import saker.build.task.BuildTargetTaskResult;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.BuildFileTargetTaskIdentifier;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * {@link TaskFactory} for running a {@linkplain BuildTargetTaskFactory build target}.
 * <p>
 * This task implementation can be used to run a build target task with the given parameters.
 * <p>
 * The {@link BuildFileTargetTaskIdentifier} class should be used as a task identifier when starting a task of this
 * type.
 * <p>
 * The {@link BuildTargetBootstrapperTaskFactory} class can be used to start a task that properly executes the script
 * parsing, dependency reporting, and starting an instance of this task.
 * 
 * @see BuildTargetBootstrapperTaskFactory
 */
@PublicApi
public final class BuildTargetRunnerTaskFactory
		implements TaskFactory<BuildTargetTaskResult>, Task<BuildTargetTaskResult>, Externalizable {
	private static final long serialVersionUID = 1L;

	private BuildTargetTaskFactory realFactory;
	private NavigableMap<String, ? extends TaskIdentifier> targetParameters;

	/**
	 * For {@link Externalizable}.
	 */
	public BuildTargetRunnerTaskFactory() {
	}

	/**
	 * Creates a new instance with the given build target factory and task parameters.
	 * 
	 * @param buildTargetFactory
	 *            The build target task factory to invoke.
	 * @param targetParameters
	 *            The target parameters to pass to the invoked target. May be <code>null</code> or empty.
	 * @throws NullPointerException
	 */
	public BuildTargetRunnerTaskFactory(BuildTargetTaskFactory buildTargetFactory,
			Map<String, ? extends TaskIdentifier> targetParameters) throws NullPointerException {
		Objects.requireNonNull(buildTargetFactory, "build target factory");
		this.realFactory = buildTargetFactory;
		//protective copy and immutabilization
		this.targetParameters = ObjectUtils.isNullOrEmpty(targetParameters) ? Collections.emptyNavigableMap()
				: ImmutableUtils.makeImmutableNavigableMap(targetParameters);
	}

	@Override
	public BuildTargetTaskResult run(TaskContext taskcontext) throws Exception {
		BuildTargetTask realtask = realFactory.createTask(taskcontext.getExecutionContext());
		if (realtask == null) {
			throw new NullPointerException("Task factory created null task: " + realFactory.getClass().getName());
		}
		realtask.initParameters(taskcontext, targetParameters);
		return realtask.run(taskcontext);
	}

	@Override
	public Task<? extends BuildTargetTaskResult> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + realFactory.hashCode();
		result = prime * result + targetParameters.hashCode();
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
		BuildTargetRunnerTaskFactory other = (BuildTargetRunnerTaskFactory) obj;
		if (realFactory == null) {
			if (other.realFactory != null)
				return false;
		} else if (!realFactory.equals(other.realFactory))
			return false;
		if (targetParameters == null) {
			if (other.targetParameters != null)
				return false;
		} else if (!targetParameters.equals(other.targetParameters))
			return false;
		return true;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(realFactory);
		SerialUtils.writeExternalMap(out, targetParameters);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		realFactory = (BuildTargetTaskFactory) in.readObject();
		targetParameters = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (realFactory != null ? "realFactory=" + realFactory + ", " : "")
				+ (targetParameters != null ? "targetParameters=" + targetParameters : "") + "]";
	}
}
