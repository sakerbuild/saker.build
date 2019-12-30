package saker.build.runtime.project;

import java.util.Map;
import java.util.Objects;

import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.ForwardingImplSakerEnvironment;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.util.cache.CacheKey;

public class CacheRecordingForwardingSakerEnvironment extends ForwardingImplSakerEnvironment {
	protected final SakerExecutionCache cache;

	public CacheRecordingForwardingSakerEnvironment(SakerEnvironmentImpl environment, SakerExecutionCache cache) {
		super(environment);
		this.cache = cache;
	}

	@Override
	public <T> T getEnvironmentPropertyCurrentValue(EnvironmentProperty<T> environmentproperty) {
		Objects.requireNonNull(environmentproperty, "property");
		cache.recordEnvironmentPropertyAccess(environmentproperty);
		return super.getEnvironmentPropertyCurrentValue(environmentproperty);
	}

	@Override
	public <DataType> DataType getCachedData(CacheKey<DataType, ?> key) throws Exception {
		cache.recordCacheKeyAccess(key);
		return super.getCachedData(key);
	}

	@Override
	public ClassPathLoadManager getClassPathManager() {
		return super.getClassPathManager();
	}

	@Override
	public Map<String, String> getUserParameters() {
		return super.getUserParameters();
	}
}
