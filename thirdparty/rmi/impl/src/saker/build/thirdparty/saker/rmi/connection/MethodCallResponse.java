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

class MethodCallResponse extends InterruptStatusTrackingRequestResponse {
	private final Object returnValue;

	public MethodCallResponse(boolean invokerThreadInterrupted, int deliveredInterruptRequestCount,
			Object returnValue) {
		super(invokerThreadInterrupted, deliveredInterruptRequestCount);
		this.returnValue = returnValue;
	}

	public Object getReturnValue() throws InvocationTargetException, RMICallFailedException {
		return returnValue;
	}
}