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

import java.io.IOException;
import java.io.Serializable;
import java.util.NavigableSet;
import java.util.Set;
import java.util.UUID;

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.rmi.wrap.RMISerializableWrapper;

/**
 * Specifies the execution parameters for newly started inner tasks.
 * 
 * @see TaskContext#startInnerTask(TaskFactory, InnerTaskExecutionParameters)
 */
@RMIWrap(InnerTaskExecutionParameters.InnerParametersRMIWrapper.class)
public final class InnerTaskExecutionParameters {
	private int clusterDuplicateFactor = 0;
	private boolean duplicationCancellable = false;
	private TaskDuplicationPredicate duplicationPredicate = null;
	private NavigableSet<UUID> allowedClusterEnvironmentIdentifiers = null;

	/**
	 * @since 0.8.8
	 */
	private int maxEnvironmentFactor;

	/**
	 * Creates a new instance with the default parameters.
	 */
	public InnerTaskExecutionParameters() {
	}

	/**
	 * Gets the cluster duplicate factor.
	 * <p>
	 * The cluster duplicate factor determines that how many clusters the inner task should be duplicated to. By default
	 * it is 0.
	 * <p>
	 * If the value is negative, the inner task will be duplicated to all suitable clusters.
	 * <p>
	 * If the value is 0 or 1, the inner task will be run on a single suitable environment.
	 * <p>
	 * If the value is greater than 1, the task will be duplicated to <b>at most</b> the specified number of
	 * environments. If there are less suitable environments than the duplicate factor, the task will run only the
	 * suitable environments without raising an error.
	 * <p>
	 * Generally users should set this to either -1 (to use all possible clusters), or 0 (to execute only in a single
	 * environment). However, if they already know how many units of execution the inner tasks will execute, they are
	 * recommended to set this value to the upper bound of the number of inner task invocations expected. In some cases
	 * this can improve the performance, as tasks won't be duplicated unnecessarily.
	 * <p>
	 * If an inner task uses non-zero duplication factor, it also needs to report the
	 * {@linkplain TaskFactory#CAPABILITY_REMOTE_DISPATCHABLE remote dispatchable} capability.
	 * 
	 * @return The cluster duplicate factor.
	 */
	public int getClusterDuplicateFactor() {
		return clusterDuplicateFactor;
	}

	/**
	 * Sets the cluster duplicate factor.
	 * 
	 * @param clusterDuplicateFactor
	 *            The duplicate factor.
	 * @see #getClusterDuplicateFactor()
	 */
	public void setClusterDuplicateFactor(int clusterDuplicateFactor) {
		this.clusterDuplicateFactor = clusterDuplicateFactor;
	}

	/**
	 * Gets the maximum build environment factor.
	 * <p>
	 * The maximum build environment factor specifies how many times a task should be duplicated on a given build
	 * environment. As inner tasks can be duplicated to run on a given environment with multiple concurrent instances,
	 * the maximum environment factor limits this parallelism.
	 * <p>
	 * Using the maximum environment factor can be useful if you have other concurrency limiting factors that are not
	 * feasible to be limited via {@linkplain TaskFactory#getRequestedComputationTokenCount() computation tokens}.
	 * <p>
	 * A good example for using this if your inner tasks use a shared separate JVM to perform its operations. In that
	 * case setting the maximum environment factor to 1 can be useful to limit too many concurrent inner tasks.
	 * <p>
	 * Generally, the value of this property is either set to 1 or be unlimited.
	 * {@linkplain TaskFactory#getRequestedComputationTokenCount() Computation tokens} are more useful for limiting
	 * concurrency for greater numbers.
	 * <p>
	 * If the maximum environment factor is 0 or negative, it is considered to be not limited.
	 * <p>
	 * Note that if this value is greater than 0, it is not guaranteed that a given task will be duplicated at least
	 * that many times.
	 * 
	 * @return The environment factor.
	 * @since 0.8.8
	 */
	public int getMaxEnvironmentFactor() {
		return maxEnvironmentFactor;
	}

	/**
	 * Sets the maximum environment factor.
	 * 
	 * @param maxEnvironmentFactor
	 *            The environment factor.
	 * @see #getMaxEnvironmentFactor()
	 * @since 0.8.8
	 */
	public void setMaxEnvironmentFactor(int maxEnvironmentFactor) {
		this.maxEnvironmentFactor = maxEnvironmentFactor;
	}

	/**
	 * Checks if the duplication of the inner task is cancellable.
	 * <p>
	 * By default, inner tasks are not duplication cancellable.
	 * <p>
	 * If the duplication of an inner task is set to be cancellable, the duplication will abort if it is
	 * {@linkplain InnerTaskResults#cancelDuplicationOptionally() requested manually} or the enclosing task finishes.
	 * <p>
	 * When the duplication is cancelled, no more inner tasks will be invoked, even if the
	 * {@linkplain #getDuplicationPredicate() duplication predicate} returns <code>true</code>.
	 * 
	 * @return <code>true</code> if the duplication is cancellable.
	 */
	public boolean isDuplicationCancellable() {
		return duplicationCancellable;
	}

	/**
	 * Sets if the duplication of the inner task is cancellable.
	 * 
	 * @param duplicationCancellable
	 *            <code>true</code> if the duplication is cancellable.
	 * @see #isDuplicationCancellable()
	 */
	public void setDuplicationCancellable(boolean duplicationCancellable) {
		this.duplicationCancellable = duplicationCancellable;
	}

	/**
	 * Gets the duplication predicate of the associated inner task.
	 * <p>
	 * The duplication predicate is used to determine if the given inner task should be invoked once more.
	 * <p>
	 * If a duplication predicate is not set, the inner task will be duplicated to the clusters based on the
	 * {@linkplain #getClusterDuplicateFactor() cluster duplicate factor}, and it will be invoked once for each
	 * duplication.
	 * 
	 * @return The duplication predicate or <code>null</code> if not set.
	 * @see TaskDuplicationPredicate
	 */
	public TaskDuplicationPredicate getDuplicationPredicate() {
		return duplicationPredicate;
	}

	//TODO it should be ensured that if a predicate returns false once, it will return false for all remaining calls

	/**
	 * Sets the duplication predicate for the inner task.
	 * <p>
	 * If the inner task is used with remote dispatching, the duplication predicate will not be transferred anywhere. It
	 * will be invoked on the caller machine, and there will only exist a single instance of the argument duplication
	 * predicate.
	 * <p>
	 * The RMI transfer properties for the runtime type of the argument is ignored.
	 * 
	 * @param duplicationpredicate
	 *            The duplication predicate or <code>null</code> to unset.
	 * @see #getDuplicationPredicate()
	 */
	public void setDuplicationPredicate(TaskDuplicationPredicate duplicationpredicate) {
		if (duplicationpredicate == null) {
			this.duplicationPredicate = null;
		} else {
			//wrap into an other predicate, so it doesn't overwrite RMI transfer, and always written as a remote
			this.duplicationPredicate = () -> duplicationpredicate.shouldInvokeOnceMore();
		}
	}

	/**
	 * Sets the duplcation predicate for the inner task in a way that the predicate is transferred to each invocation
	 * cluster.
	 * <p>
	 * If remote dispatching is involved with the starting of the inner task, the argument object will be serialized to
	 * each cluster that is a suitable environment and candidate for invoking the inner task.
	 * <p>
	 * The argument predicate must be {@link Serializable}.
	 * 
	 * @param duplicationpredicate
	 *            The duplication predicate or <code>null</code> to unset.
	 * @see #getDuplicationPredicate()
	 */
	public void setDuplicationPredicateForEachCluster(TaskDuplicationPredicate duplicationpredicate) {
		if (duplicationpredicate == null) {
			this.duplicationPredicate = null;
		} else {
			this.duplicationPredicate = new EachClusterDuplicationPredicate(duplicationpredicate);
		}
	}

	/**
	 * Sets the duplicate predicate for the inner task in a way that the predicate is transferred to the coordinator
	 * machine.
	 * <p>
	 * If remote dispatching is involved with the starting of the inner task, the argument object will be serialized to
	 * the coordinator machine for the build execution. The coordinator machine is the computer that executes the build
	 * itself.
	 * <p>
	 * If the inner task is being started from an already remote dispatched enclosing task, and the inner task is to be
	 * remote dispatched to multiple clusters, it can be beneficial to use this function that transfers the predicate to
	 * the coordinator machine. Having the predicate on the coordinator can reduce the network hops required to call the
	 * predicate method.
	 * <p>
	 * The argument predicate must be {@link Serializable}.
	 * 
	 * @param duplicationpredicate
	 *            The duplication predicate or <code>null</code> to unset.
	 * @see #getDuplicationPredicate()
	 */
	public void setDuplicationPredicateOnCoordinator(TaskDuplicationPredicate duplicationpredicate) {
		if (duplicationpredicate == null) {
			this.duplicationPredicate = null;
		} else {
			this.duplicationPredicate = new CoordinatorDuplicationPredicate(duplicationpredicate);
		}
	}

	/**
	 * Gets the set of allowed cluster environment identifiers.
	 * <p>
	 * Setting the allowed cluster environment identifiers can be used to limit the suitable environments for the inner
	 * task execution. The build system will only consider environments for suitability checking if their identifiers
	 * are contained in the returned set.
	 * <p>
	 * If the identifiers are <code>null</code> the build system will consider all environments for execution.
	 * <p>
	 * The build system will throw an exception if the identifiers set is non-<code>null</code>, but empty.
	 * <p>
	 * Using this parameter can be beneficial when callers want the inner task to be executed on the same machine as the
	 * caller task:
	 * 
	 * <pre>
	 * params.setAllowedClusterEnvironmentIdentifiers(Collections.singleton(environment.getEnvironmentIdentifier()));
	 * </pre>
	 * 
	 * Other use-cases may include running different inner tasks after each other where the first tasks will collect the
	 * associated environment identifiers as well. These use-cases are generally require more sophisticated operations
	 * to work correctly, and is not recommended. The parameter is represented as a set to allow such behaviour, for
	 * future compatibility and extensibility.
	 * 
	 * @return The set of allowed environment identifiers or <code>null</code> if not set.
	 * @see SakerEnvironment#getEnvironmentIdentifier()
	 */
	public Set<UUID> getAllowedClusterEnvironmentIdentifiers() {
		return allowedClusterEnvironmentIdentifiers;
	}

	/**
	 * Sets the allowed cluster environments which can be used for the inner task execution.
	 * 
	 * @param allowedClusterEnvironmentIdentifiers
	 *            The identifiers of the environments.
	 * @throws IllegalArgumentException
	 *             If the set contains <code>null</code>.
	 * @see #getAllowedClusterEnvironmentIdentifiers()
	 * @see SakerEnvironment#getEnvironmentIdentifier()
	 */
	public void setAllowedClusterEnvironmentIdentifiers(Set<UUID> allowedClusterEnvironmentIdentifiers)
			throws IllegalArgumentException {
		if (allowedClusterEnvironmentIdentifiers == null) {
			this.allowedClusterEnvironmentIdentifiers = null;
		} else {
			if (allowedClusterEnvironmentIdentifiers.contains(null)) {
				throw new IllegalArgumentException("Allowed cluster environment identifiers set contains null. "
						+ allowedClusterEnvironmentIdentifiers);
			}
			this.allowedClusterEnvironmentIdentifiers = ImmutableUtils
					.makeImmutableNavigableSet(allowedClusterEnvironmentIdentifiers);
		}
	}

	@RMIWrap(RMISerializableWrapper.class)
	static final class CoordinatorDuplicationPredicate implements TaskDuplicationPredicate, Serializable {
		private static final long serialVersionUID = 1L;

		private TaskDuplicationPredicate predicate;

		public CoordinatorDuplicationPredicate(TaskDuplicationPredicate predicate) {
			this.predicate = predicate;
		}

		@Override
		public boolean shouldInvokeOnceMore() throws RuntimeException {
			throw new UnsupportedOperationException();
		}

		public TaskDuplicationPredicate getPredicate() {
			return predicate;
		}
	}

	@RMIWrap(RMISerializableWrapper.class)
	private static final class EachClusterDuplicationPredicate implements TaskDuplicationPredicate, Serializable {
		private static final long serialVersionUID = 1L;

		private TaskDuplicationPredicate predicate;

		public EachClusterDuplicationPredicate(TaskDuplicationPredicate predicate) {
			this.predicate = predicate;
		}

		@Override
		public boolean shouldInvokeOnceMore() throws RuntimeException {
			return predicate.shouldInvokeOnceMore();
		}

	}

	protected static final class InnerParametersRMIWrapper implements RMIWrapper {
		private InnerTaskExecutionParameters params;

		public InnerParametersRMIWrapper() {
		}

		public InnerParametersRMIWrapper(InnerTaskExecutionParameters params) {
			this.params = params;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeInt(params.clusterDuplicateFactor);
			out.writeRemoteObject(params.duplicationPredicate);
			out.writeBoolean(params.duplicationCancellable);
			SerialUtils.writeExternalCollection(out, params.allowedClusterEnvironmentIdentifiers,
					(o, e) -> o.writeSerializedObject(e));
			out.writeInt(params.maxEnvironmentFactor);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			params = new InnerTaskExecutionParameters();
			params.clusterDuplicateFactor = in.readInt();
			params.duplicationPredicate = (TaskDuplicationPredicate) in.readObject();
			params.duplicationCancellable = in.readBoolean();
			params.allowedClusterEnvironmentIdentifiers = SerialUtils.readExternalSortedImmutableNavigableSet(in);
			params.maxEnvironmentFactor = in.readInt();
		}

		@Override
		public Object resolveWrapped() {
			return params;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

	}
}
