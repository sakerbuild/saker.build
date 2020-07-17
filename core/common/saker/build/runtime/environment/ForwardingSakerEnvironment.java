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
package saker.build.runtime.environment;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.util.cache.CacheKey;

public class ForwardingSakerEnvironment implements SakerEnvironment {
	private final SakerEnvironment environment;

	public ForwardingSakerEnvironment(SakerEnvironment environment) {
		this.environment = environment;
	}

	@Override
	public <T> T getEnvironmentPropertyCurrentValue(EnvironmentProperty<T> environmentproperty) {
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

	@Override
	public UUID getEnvironmentIdentifier() {
		return environment.getEnvironmentIdentifier();
	}
}
