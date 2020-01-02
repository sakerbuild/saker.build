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
