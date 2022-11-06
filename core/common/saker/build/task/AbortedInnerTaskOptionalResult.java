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

import saker.build.task.exception.InnerTaskExecutionException;

public class AbortedInnerTaskOptionalResult<R> implements InnerTaskResultHolder<R>, Externalizable {
	private static final long serialVersionUID = 1L;

	private String message;
	private Throwable cause;
	private R result;

	/**
	 * For {@link Externalizable}.
	 */
	public AbortedInnerTaskOptionalResult() {
	}

	public AbortedInnerTaskOptionalResult(R result, Throwable cause) {
		this.result = result;
		this.cause = cause;
	}

	public AbortedInnerTaskOptionalResult(R result, String message, Throwable cause) {
		this.result = result;
		this.message = message;
		this.cause = cause;
	}

	@Override
	public R getResult() throws InnerTaskExecutionException {
		return result;
	}

	@Override
	public Throwable getExceptionIfAny() {
		return cause;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(result);
		out.writeObject(message);
		out.writeObject(cause);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		result = (R) in.readObject();
		message = (String) in.readObject();
		cause = (Throwable) in.readObject();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(getClass().getSimpleName());
		builder.append("[result=");
		builder.append(result);
		builder.append(", message=");
		builder.append(message);
		builder.append(", cause=");
		builder.append(cause);
		builder.append("]");
		return builder.toString();
	}

}