package saker.build.runtime.environment;

import saker.build.trace.InternalBuildTrace;

public interface InternalSakerEnvironment extends SakerEnvironment {
	public <T> T internalGetEnvironmentPropertyCurrentValue(SakerEnvironment environment,
			EnvironmentProperty<T> environmentproperty, InternalBuildTrace btrace);
}
