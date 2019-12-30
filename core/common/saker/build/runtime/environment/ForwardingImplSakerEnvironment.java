package saker.build.runtime.environment;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.util.cache.CacheKey;

public class ForwardingImplSakerEnvironment implements SakerEnvironment {
	protected final SakerEnvironmentImpl environment;

	public ForwardingImplSakerEnvironment(SakerEnvironmentImpl environment) {
		this.environment = environment;
	}

	@Override
	public <T> T getEnvironmentPropertyCurrentValue(EnvironmentProperty<T> environmentproperty) {
		return environment.getEnvironmentPropertyCurrentValue(this, environmentproperty);
	}

	@Override
	public <DataType> DataType getCachedData(CacheKey<DataType, ?> key) throws Exception {
		return environment.getCachedData(key);
	}

	@Override
	public ClassPathLoadManager getClassPathManager() {
		return environment.getClassPathManager();
	}

	@Override
	public Map<String, String> getUserParameters() {
		return environment.getUserParameters();
	}

	@Override
	public ThreadGroup getEnvironmentThreadGroup() {
		return environment.getEnvironmentThreadGroup();
	}

	@Override
	public Path getEnvironmentJarPath() {
		return environment.getEnvironmentJarPath();
	}

	@Override
	public UUID getEnvironmentIdentifier() {
		return environment.getEnvironmentIdentifier();
	}
}
