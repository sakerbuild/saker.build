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
package saker.build.thirdparty.saker.rmi.connection;

import java.lang.reflect.InvocationTargetException;

import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;

class NewInstanceFailedResponse extends NewInstanceResponse {
	private final Throwable exception;

	public NewInstanceFailedResponse(boolean invokerThreadInterrupted, int deliveredInterruptRequestCount,
			Throwable exception) {
		super(invokerThreadInterrupted, deliveredInterruptRequestCount, RMIVariables.NO_OBJECT_ID);
		this.exception = exception;
	}

	@Override
	public int getRemoteId() throws InvocationTargetException, RMICallFailedException {
		if (exception instanceof InvocationTargetException) {
			exception.fillInStackTrace();
			throw (InvocationTargetException) exception;
		}
		throw new RMICallFailedException(exception);
	}
}
