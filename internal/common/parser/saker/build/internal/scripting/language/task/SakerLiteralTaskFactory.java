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
package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SimpleSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

public final class SakerLiteralTaskFactory extends SelfSakerTaskFactory
		implements DirectComputableSakerTaskFactory<SakerTaskResult> {
	private static final long serialVersionUID = 1L;

	protected Object value;

	/**
	 * For {@link Externalizable}.
	 */
	public SakerLiteralTaskFactory() {
	}

	/**
	 * Creates a new instance for the given object.
	 * <p>
	 * Note: only use this when it is known that the object is an actual literal constant, and not some other task
	 * result.
	 * 
	 * @param value
	 *            The object to enclose.
	 */
	public SakerLiteralTaskFactory(Object value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(String value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(Boolean value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(Character value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(Byte value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(Short value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(Integer value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(Long value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(Float value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(Double value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(BigDecimal value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(BigInteger value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(Number value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(SakerPath value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(UUID value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(Enum<?> value) {
		this.value = value;
	}

	public SakerLiteralTaskFactory(WildcardPath value) {
		this.value = value;
	}

	public Object getValue() {
		return value;
	}

	@Override
	protected boolean isShort() {
		return true;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) {
		SakerTaskResult result = directComputeTaskResult(taskcontext);
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	@Override
	public SakerTaskResult directComputeTaskResult(TaskContext taskcontext) {
		if (value instanceof SakerTaskResult) {
			return (SakerTaskResult) value;
		}
		return new SimpleSakerTaskResult<>(value);
	}

	@Override
	public boolean isDirectComputable() {
		return true;
	}

	@Override
	public TaskIdentifier createSubTaskIdentifier(SakerScriptTaskIdentifier parenttaskidentifier) {
		return new LiteralTaskIdentifier(parenttaskidentifier.getRootIdentifier(), value);
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return this;
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		return this;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((value == null) ? 0 : value.hashCode());
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
		SakerLiteralTaskFactory other = (SakerLiteralTaskFactory) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(literal:" + Objects.toString(value) + ")";
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(value);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		value = in.readObject();
	}

	public static class LiteralTaskIdentifier implements TaskIdentifier, Externalizable {
		private static final long serialVersionUID = 1L;

		//we need the root identifier, as the build system may detect changes if it is not included
		//    given the scenario when multiple files declare the same literals, like 123
		//    it would be started with a task identifier only containing the 123 value
		//    during incremental detection, the literal task would be restarted, but due to ordering
		//    issues, the other file may run the task rather than the previous
		//    this results in the working directories being different for the started literal tasks
		//    this will end in the literal task being reinvoked, as the task change is detected for it
		//    because the working directories are different.

		protected TaskIdentifier rootIdentifier;
		protected Object value;

		/**
		 * For {@link Externalizable}.
		 */
		public LiteralTaskIdentifier() {
		}

		public LiteralTaskIdentifier(TaskIdentifier rootIdentifier, Object value) {
			this.rootIdentifier = rootIdentifier;
			this.value = value;
		}

		public Object getValue() {
			return value;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(rootIdentifier);
			out.writeObject(value);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			rootIdentifier = (TaskIdentifier) in.readObject();
			value = in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + rootIdentifier.hashCode();
			result = prime * result + ((value == null) ? 0 : value.hashCode());
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
			LiteralTaskIdentifier other = (LiteralTaskIdentifier) obj;
			if (rootIdentifier == null) {
				if (other.rootIdentifier != null)
					return false;
			} else if (!rootIdentifier.equals(other.rootIdentifier))
				return false;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "(literal_tid:" + value + ")";
		}
	}
}
