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
import java.util.Set;

import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;

class UnknownNewInstanceResponse extends NewInstanceResponse {
	private final Set<Class<?>> interfaces;

	public UnknownNewInstanceResponse(boolean invokerThreadInterrupted, int deliveredInterruptRequestCount,
			int remoteId, Set<Class<?>> interfaces) {
		super(invokerThreadInterrupted, deliveredInterruptRequestCount, remoteId);
		this.interfaces = interfaces;
	}

	@Override
	public Set<Class<?>> getInterfaces() throws InvocationTargetException, RMICallFailedException {
		return interfaces;
	}
}