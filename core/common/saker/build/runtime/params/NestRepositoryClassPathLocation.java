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
package saker.build.runtime.params;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.MalformedURLException;
import java.net.URL;

import saker.apiextract.api.DefaultableBoolean;
import saker.apiextract.api.PublicApi;
import saker.build.runtime.classpath.ClassPathLoader;
import saker.build.runtime.classpath.ClassPathLocation;
import saker.build.runtime.classpath.HttpUrlJarFileClassPathLocation;

/**
 * {@link ClassPathLocation} implementation that loads the saker.nest repository.
 * <p>
 * The repository is downloaded as a JAR from the URL: {@value #DEFAULT_NEST_REPOSITORY_URL}
 * <p>
 * See also: <a class="javadoc-external-link" href="https://saker.build/saker.nest/index.html">Nest repository</a>
 * 
 * @see #getInstance()
 * @see NestRepositoryFactoryClassPathServiceEnumerator
 * @see https://nest.saker.build
 */
public class NestRepositoryClassPathLocation implements ClassPathLocation, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The version number of the saker.nest repository that is loaded as the default for the build executions.
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final String DEFAULT_VERSION = "0.8.0";

	/**
	 * The URL of the Nest repository.
	 */
	private static final String DEFAULT_NEST_REPOSITORY_URL = "https://api.nest.saker.build/bundle/download/saker.nest-v"
			+ DEFAULT_VERSION;

	private ClassPathLocation realClassPath;

	/**
	 * Gets an instance of this class path location.
	 * <p>
	 * The result may or may not be a singleton instance.
	 * <p>
	 * The returned class path location will load the saker.nest repository with the {@value #DEFAULT_VERSION} version.
	 * 
	 * @return An instance.
	 */
	public static ClassPathLocation getInstance() {
		NestRepositoryClassPathLocation result = new NestRepositoryClassPathLocation();
		try {
			result.realClassPath = new HttpUrlJarFileClassPathLocation(new URL(DEFAULT_NEST_REPOSITORY_URL));
		} catch (MalformedURLException e) {
			throw new AssertionError();
		}
		return result;
	}

	/**
	 * For {@link Externalizable}.
	 * 
	 * @deprecated Use {@link #getInstance()}.
	 */
	//deprecate so the compiler warns about calling it. Only getInstance() should be used to ensure 
	//compatibility between versions
	@Deprecated
	public NestRepositoryClassPathLocation() {
	}

	@Override
	public ClassPathLoader getLoader() throws IOException {
		return realClassPath.getLoader();
	}

	@Override
	public String getIdentifier() {
		//the identifier should be static in regards to the version of the nest repository.
		//this is in order to have the same storage directories even if the repository version is updated
		return "nest";
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(realClassPath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		realClassPath = (HttpUrlJarFileClassPathLocation) in.readObject();
	}

	@Override
	public int hashCode() {
		return getClass().getName().hashCode() ^ realClassPath.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NestRepositoryClassPathLocation other = (NestRepositoryClassPathLocation) obj;
		if (realClassPath == null) {
			if (other.realClassPath != null)
				return false;
		} else if (!realClassPath.equals(other.realClassPath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + realClassPath + "]";
	}

}
