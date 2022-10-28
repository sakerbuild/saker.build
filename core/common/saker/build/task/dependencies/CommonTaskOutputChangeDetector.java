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
package saker.build.task.dependencies;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.build.task.TaskContext;
import saker.build.task.TaskDependencyFuture;
import saker.build.task.TaskFuture;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

/**
 * Utility class providing access to common {@link TaskOutputChangeDetector} implementations.
 * 
 * @see TaskContext#reportSelfTaskOutputChangeDetector(TaskOutputChangeDetector)
 * @see TaskDependencyFuture#setTaskOutputChangeDetector(TaskOutputChangeDetector)
 * @see TaskResultDependencyHandle#setTaskOutputChangeDetector(TaskOutputChangeDetector)
 */
public class CommonTaskOutputChangeDetector {

	/**
	 * Treats the task output as always changed.
	 * <p>
	 * This is the default {@link TaskOutputChangeDetector} for task dependencies. When a task reports this as the
	 * output change detector, it will trigger a task change delta if the input task was rerun, and it themself detects
	 * that it has changed.
	 * 
	 * @see TaskContext#reportSelfTaskOutputChangeDetector(TaskOutputChangeDetector)
	 * @see TaskFuture#get()
	 */
	public static final TaskOutputChangeDetector ALWAYS = new StaticTaskOutputChangeDetector(true);
	/**
	 * Treats the task output as never changed.
	 */
	public static final TaskOutputChangeDetector NEVER = new StaticTaskOutputChangeDetector(false);

	/**
	 * Expects the given task output to be an {@linkplain Class#isArray() array}.
	 * <p>
	 * If the task output is <code>null</code>, a change is detected.
	 */
	public static final TaskOutputChangeDetector IS_ARRAY = new ArrayTypeTaskOutputChangeDetector(true);
	/**
	 * Expects the given task output to be <b>not</b> an {@linkplain Class#isArray() array}.
	 * <p>
	 * If the task output is <code>null</code>, a change is <b>not</b> detected.
	 */
	public static final TaskOutputChangeDetector IS_NOT_ARRAY = new ArrayTypeTaskOutputChangeDetector(false);
	
	/**
	 * Expects the task output to be <code>null</code>.
	 * 
	 * @since saker.build 0.8.12
	 */
	public static final TaskOutputChangeDetector IS_NULL = new EqualityTaskOutputChangeDetector(null);

	/**
	 * Gets a task output change detector that expects the task output to be an instance of the given type.
	 * 
	 * @param type
	 *            The type to check if the task output is instance of.
	 * @return The output change detector.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see Class#isInstance(Object)
	 */
	public static TaskOutputChangeDetector isInstanceOf(Class<?> type) throws NullPointerException {
		Objects.requireNonNull(type, "type");
		return new InstanceOfTaskOutputChangeDetector(type, true);
	}

	/**
	 * Gets a task output change detector that expects the task to have a given type.
	 * 
	 * @param type
	 *            The type to check if the output for.
	 * @return The output change detector.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static TaskOutputChangeDetector isSameClass(Class<?> type) throws NullPointerException {
		Objects.requireNonNull(type, "type");
		return new SameClassTaskOutputChangeDetector(type);
	}

	/**
	 * Gets a task output change detector that expects the task output to be <b>not</b> an instance of the given type.
	 * 
	 * @param type
	 *            The type to check if the task output is instance of.
	 * @return The output change detector.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see Class#isInstance(Object)
	 */
	public static TaskOutputChangeDetector notInstanceOf(Class<?> type) throws NullPointerException {
		Objects.requireNonNull(type, "type");
		return new InstanceOfTaskOutputChangeDetector(type, false);
	}

	/**
	 * Gets a task output change detector that examines if the task output is instance of the given type.
	 * <p>
	 * A change is detected if the task output is instance of the given type and it is not expected, or if it is not an
	 * instance of the type and it is expected.
	 * 
	 * @param type
	 *            The type to check if the task output is instance of.
	 * @param expectedinstanceof
	 *            Whether or not to expect the task output to be an instance of the specified type.
	 * @return The output change detector.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see Class#isInstance(Object)
	 */
	public static TaskOutputChangeDetector instanceOf(Class<?> type, boolean expectedinstanceof)
			throws NullPointerException {
		Objects.requireNonNull(type, "type");
		return new InstanceOfTaskOutputChangeDetector(type, expectedinstanceof);
	}

	/**
	 * Gets a task output change detector that expects the task output to equal to the argument.
	 * 
	 * @param obj
	 *            The object to check the equality for. May be <code>null</code>.
	 * @return The output change detector.
	 */
	//cannot be named equals as the Object.equals method has same signature
	public static TaskOutputChangeDetector equalsTo(Object obj) {
		return new EqualityTaskOutputChangeDetector(obj);
	}

	private CommonTaskOutputChangeDetector() {
		throw new UnsupportedOperationException();
	}

	private static final class ArrayTypeTaskOutputChangeDetector implements TaskOutputChangeDetector, Externalizable {
		private static final long serialVersionUID = 1L;

		private boolean expectedIsArray;

		/**
		 * For {@link Externalizable}.
		 */
		public ArrayTypeTaskOutputChangeDetector() {
		}

		public ArrayTypeTaskOutputChangeDetector(boolean expectedIsArray) {
			this.expectedIsArray = expectedIsArray;
		}

		@Override
		public boolean isChanged(Object taskoutput) {
			if (taskoutput == null) {
				return false != expectedIsArray;
			}
			return taskoutput.getClass().isArray() != expectedIsArray;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeBoolean(expectedIsArray);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			expectedIsArray = in.readBoolean();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (expectedIsArray ? 1231 : 1237);
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
			ArrayTypeTaskOutputChangeDetector other = (ArrayTypeTaskOutputChangeDetector) obj;
			if (expectedIsArray != other.expectedIsArray)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[expectedIsArray=" + expectedIsArray + "]";
		}
	}

	private static final class InstanceOfTaskOutputChangeDetector implements TaskOutputChangeDetector, Externalizable {
		private static final long serialVersionUID = 1L;

		private Class<?> type;
		private boolean expectedInstanceOf;

		/**
		 * For {@link Externalizable}.
		 */
		public InstanceOfTaskOutputChangeDetector() {
		}

		public InstanceOfTaskOutputChangeDetector(Class<?> type, boolean expectedInstanceOf) {
			this.type = type;
			this.expectedInstanceOf = expectedInstanceOf;
		}

		@Override
		public boolean isChanged(Object taskoutput) {
			return type.isInstance(taskoutput) != expectedInstanceOf;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(type);
			out.writeBoolean(expectedInstanceOf);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			type = (Class<?>) in.readObject();
			expectedInstanceOf = in.readBoolean();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (expectedInstanceOf ? 1231 : 1237);
			result = prime * result + type.getName().hashCode();
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
			InstanceOfTaskOutputChangeDetector other = (InstanceOfTaskOutputChangeDetector) obj;
			if (expectedInstanceOf != other.expectedInstanceOf)
				return false;
			if (type == null) {
				if (other.type != null)
					return false;
			} else if (!type.equals(other.type))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (type != null ? "type=" + type + ", " : "")
					+ "expectedInstanceOf=" + expectedInstanceOf + "]";
		}
	}

	private static final class SameClassTaskOutputChangeDetector implements TaskOutputChangeDetector, Externalizable {
		private static final long serialVersionUID = 1L;

		private Class<?> type;

		/**
		 * For {@link Externalizable}.
		 */
		public SameClassTaskOutputChangeDetector() {
		}

		public SameClassTaskOutputChangeDetector(Class<?> type) {
			this.type = type;
		}

		@Override
		public boolean isChanged(Object taskoutput) {
			if (taskoutput == null) {
				return true;
			}
			return taskoutput.getClass() != type;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(type);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			type = (Class<?>) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + type.getName().hashCode();
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
			SameClassTaskOutputChangeDetector other = (SameClassTaskOutputChangeDetector) obj;
			if (type == null) {
				if (other.type != null)
					return false;
			} else if (!type.equals(other.type))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (type != null ? "type=" + type + ", " : "") + "]";
		}
	}

	private static final class StaticTaskOutputChangeDetector implements TaskOutputChangeDetector, Externalizable {
		private static final long serialVersionUID = 1L;

		private boolean changed;

		/**
		 * For {@link Externalizable}.
		 */
		public StaticTaskOutputChangeDetector() {
		}

		public StaticTaskOutputChangeDetector(boolean changed) {
			this.changed = changed;
		}

		@Override
		public boolean isChanged(Object taskoutput) {
			return changed;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeBoolean(changed);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			changed = in.readBoolean();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (changed ? 1231 : 1237);
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
			StaticTaskOutputChangeDetector other = (StaticTaskOutputChangeDetector) obj;
			if (changed != other.changed)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + changed + "]";
		}
	}
}
