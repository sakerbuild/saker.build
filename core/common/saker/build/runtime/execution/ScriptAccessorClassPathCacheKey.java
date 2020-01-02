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
package saker.build.runtime.execution;

import java.io.IOException;
import java.util.Iterator;

import saker.build.runtime.classpath.ClassPathEnumerationError;
import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.runtime.classpath.ClassPathLoadManager.ClassPathLock;
import saker.build.runtime.classpath.ClassPathLocation;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.util.cache.CacheKey;

public class ScriptAccessorClassPathCacheKey
		implements CacheKey<ScriptAccessorClassPathData, ScriptAccessorClassPathResource> {
	private ScriptProviderLocation providerLocator;
	private transient ClassPathLoadManager classPathManager;

	public ScriptAccessorClassPathCacheKey(ScriptProviderLocation providerLocator,
			ClassPathLoadManager classPathManager) {
		this.providerLocator = providerLocator;
		this.classPathManager = classPathManager;
	}

	@Override
	public ScriptAccessorClassPathResource allocate() throws IOException {
		ClassPathLocation cplocation = providerLocator.getClassPathLocation();
		ClassPathLock lock;
		MultiDataClassLoader classLoader;
		if (cplocation == null) {
			lock = null;
			classLoader = null;
		} else {
			lock = classPathManager.loadClassPath(cplocation);
			classLoader = new MultiDataClassLoader(SakerEnvironment.class.getClassLoader(),
					lock.getClassLoaderDataFinder());
		}
		return new ScriptAccessorClassPathResource(lock, classLoader);
	}

	@Override
	public ScriptAccessorClassPathData generate(ScriptAccessorClassPathResource resource)
			throws ClassPathEnumerationError {
		ClassLoader cl = resource.classLoader;
		if (cl == null) {
			cl = SakerEnvironment.class.getClassLoader();
		}
		Iterator<? extends ScriptAccessProvider> it = providerLocator.getScriptProviderEnumerator().getServices(cl)
				.iterator();
		if (!it.hasNext()) {
			throw new ClassPathEnumerationError("No script providers found in: " + resource.classLoader);
		}
		ScriptAccessProvider provider = it.next();
		try {
			if (it.hasNext()) {
				//issue a warning if multiple script providers are found in a classpath, even though only one is used/expected 
				System.err.println("Warning: Multiple script providers found in classloader: " + resource.classLoader);
			}
		} catch (ClassPathEnumerationError e) {
		}
		return new ScriptAccessorClassPathData(provider);
	}

	@Override
	public boolean validate(ScriptAccessorClassPathData data, ScriptAccessorClassPathResource resource) {
		return true;
	}

	@Override
	public long getExpiry() {
		return 5 * DateUtils.MS_PER_MINUTE;
	}

	@Override
	public void close(ScriptAccessorClassPathData data, ScriptAccessorClassPathResource resource) throws Exception {
		IOUtils.close(resource);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((providerLocator == null) ? 0 : providerLocator.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ScriptAccessorClassPathCacheKey other = (ScriptAccessorClassPathCacheKey) obj;
		if (providerLocator == null) {
			if (other.providerLocator != null)
				return false;
		} else if (!providerLocator.equals(other.providerLocator))
			return false;
		return true;
	}

}