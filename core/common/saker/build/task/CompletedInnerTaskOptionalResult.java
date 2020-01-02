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

public class CompletedInnerTaskOptionalResult<R> implements InnerTaskResultHolder<R>, Externalizable {
	private static final long serialVersionUID = 1L;

	private R result;

	/**
	 * For {@link Externalizable}.
	 */
	public CompletedInnerTaskOptionalResult() {
	}

	public CompletedInnerTaskOptionalResult(R result) {
		this.result = result;
	}

	@Override
	public R getResult() throws InnerTaskExecutionException {
		return result;
	}

	@Override
	public Throwable getExceptionIfAny() {
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(result);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		result = (R) in.readObject();
	}
}