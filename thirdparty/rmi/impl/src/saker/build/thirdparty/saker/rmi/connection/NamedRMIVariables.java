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
