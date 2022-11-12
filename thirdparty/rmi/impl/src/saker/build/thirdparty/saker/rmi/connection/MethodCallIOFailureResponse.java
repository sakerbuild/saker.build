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
import saker.build.thirdparty.saker.rmi.exception.RMIIOFailureException;

class MethodCallIOFailureResponse extends MethodCallResponse {
	private final String message;
	private final Throwable exception;

	public MethodCallIOFailureResponse(boolean invokerThreadInterrupted, int deliveredInterruptRequestCount,
			String message, Throwable exception) {
		super(invokerThreadInterrupted, deliveredInterruptRequestCount, null);
		this.message = message;
		this.exception = exception;
	}

	@Override
	public Object getReturnValue() throws InvocationTargetException, RMICallFailedException {
		throw new RMIIOFailureException(message, exception);
	}

}
