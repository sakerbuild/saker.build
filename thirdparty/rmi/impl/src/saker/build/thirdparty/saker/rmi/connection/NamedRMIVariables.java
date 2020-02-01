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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

class NamedRMIVariables extends RMIVariables {
	private static final AtomicIntegerFieldUpdater<NamedRMIVariables> AIFU_referenceCount = AtomicIntegerFieldUpdater
			.newUpdater(NamedRMIVariables.class, "referenceCount");

	private final String name;

	@SuppressWarnings("unused")
	private volatile int referenceCount = 1;

	NamedRMIVariables(String name, int localIdentifier, int remoteIdentifier, RMIConnection connection) {
		super(localIdentifier, remoteIdentifier, connection);
		this.name = name;
	}

	void increaseReference() {
		AIFU_referenceCount.incrementAndGet(this);
	}

	@Override
	public void close() {
		if (AIFU_referenceCount.decrementAndGet(this) <= 0) {
			super.close();
		}
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" + name + "]";
	}
}
