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

import java.util.function.Supplier;

import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.function.LazySupplier;

/**
 * {@link TaskResultDependencyHandle} that forwards its calls to the another instance that is lazily retrieved.
 */
public final class SupplierForwardingTaskResultDependencyHandle implements TaskResultDependencyHandle, Cloneable {
	private final LazySupplier<? extends TaskResultDependencyHandle> supplier;

	/**
	 * Constructs a new instance.
	 * 
	 * @param supplier
	 *            The {@link TaskResultDependencyHandle} supplier. This supplier will be wrapped into a
	 *            {@link LazySupplier} to ensure that it's only evaluated once.
	 */
	public SupplierForwardingTaskResultDependencyHandle(Supplier<? extends TaskResultDependencyHandle> supplier) {
		this.supplier = LazySupplier.of(supplier);
	}

	@Override
	public Object get() throws RuntimeException {
		return supplier.get().get();
	}

	@Override
	public void setTaskOutputChangeDetector(TaskOutputChangeDetector outputchangedetector)
			throws IllegalStateException, NullPointerException {
		TaskResultDependencyHandle handle = supplier.getIfComputed();
		if (handle == null) {
			throw new IllegalStateException("get() is not called before setting output change detector.");
		}
		handle.setTaskOutputChangeDetector(outputchangedetector);
	}

	@Override
	public TaskResultDependencyHandle clone() {
		TaskResultDependencyHandle handle = supplier.getIfComputed();
		if (handle != null) {
			//we can return the direct clone of the handle
			return handle.clone();
		}
		//use another clone class, that checks the state in setTaskOutputChangeDetector of this instance
		//(so we allow calling get() on this instance to allow setting the output change detector on the clones)
		return new SupplierForwardingTaskResultDependencyHandleClone(this.supplier);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + supplier + "]";
	}

	private static final class SupplierForwardingTaskResultDependencyHandleClone
			implements TaskResultDependencyHandle, Cloneable {
		private final LazySupplier<? extends TaskResultDependencyHandle> originalSupplier;
		private final LazySupplier<TaskResultDependencyHandle> lazyClone;

		public SupplierForwardingTaskResultDependencyHandleClone(
				LazySupplier<? extends TaskResultDependencyHandle> originalSupplier) {
			this.originalSupplier = originalSupplier;
			this.lazyClone = LazySupplier.of(() -> originalSupplier.get().clone());
		}

		@Override
		public Object get() throws RuntimeException {
			return lazyClone.get().get();
		}

		@Override
		public void setTaskOutputChangeDetector(TaskOutputChangeDetector outputchangedetector)
				throws IllegalStateException, NullPointerException {
			//get() call state check is on the original handle
			if (!originalSupplier.isComputed()) {
				throw new IllegalStateException("get() is not called before setting output change detector.");
			}
			//set the change detector through the cloned handle
			lazyClone.get().setTaskOutputChangeDetector(outputchangedetector);
		}

		@Override
		public TaskResultDependencyHandle clone() {
			return new SupplierForwardingTaskResultDependencyHandleClone(originalSupplier);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + lazyClone + "]";
		}
	}
}
