package saker.build.task;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import saker.build.exception.PropertyComputationFailedException;
import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.util.cache.CacheKey;

public class IdentifierAccessDisablerSakerEnvironment implements SakerEnvironment {
	private SakerEnvironment environment;

	public IdentifierAccessDisablerSakerEnvironment(SakerEnvironment environment) {
		this.environment = environment;
	}

	@Override
	public UUID getEnvironmentIdentifier() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("This method may not be called for the associated operation.");
	}

	@Override
	public <T> T getEnvironmentPropertyCurrentValue(EnvironmentProperty<T> environmentproperty)
			throws NullPointerException, PropertyComputationFailedException {
		return environment.getEnvironmentPropertyCurrentValue(environmentproperty);
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

}
