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
import java.net.URL;
import java.util.Objects;
import java.util.regex.Pattern;

import saker.apiextract.api.DefaultableBoolean;
import saker.apiextract.api.PublicApi;
import saker.build.file.path.SakerPath;
import saker.build.runtime.classpath.ClassPathLoader;
import saker.build.runtime.classpath.ClassPathLocation;
import saker.build.runtime.classpath.HttpUrlJarFileClassPathLocation;

/**
 * {@link ClassPathLocation} implementation that loads the saker.nest repository.
 * <p>
 * The repository is downloaded as a JAR from the URL:
 * <code>https://api.nest.saker.build/bundle/download/saker.nest-vVERSION</code>
 * <p>
 * See also: <a class="javadoc-external-link" href="https://saker.build/saker.nest/index.html">saker.nest repository</a>
 * 
 * @see #getInstance()
 * @see NestRepositoryFactoryClassPathServiceEnumerator
 * @see https://nest.saker.build
 */
public class NestRepositoryClassPathLocation implements ClassPathLocation, Externalizable {
	private static final long serialVersionUID = 1L;

	private static final Pattern PATTERN_VERSION_NUMBER = Pattern.compile("(0|([1-9][0-9]*))(\\.(0|([1-9][0-9]*)))*");

	/**
	 * The version number of the saker.nest repository that is loaded as the default for the build executions.
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final String DEFAULT_VERSION = "0.8.3";

	private String version;

	/**
	 * Gets an instance of this classpath location.
	 * <p>
	 * The result may or may not be a singleton instance.
	 * <p>
	 * The returned classpath location will load the saker.nest repository with the {@value #DEFAULT_VERSION} version.
	 * 
	 * @return An instance.
	 */
	public static ClassPathLocation getInstance() {
		return new NestRepositoryClassPathLocation(DEFAULT_VERSION);
	}

	/**
	 * Gets an instance of the saker.nest repository classpath location for the given version.
	 * <p>
	 * The classpath will download the repository release with the specified version.
	 * 
	 * @param version
	 *            The version of the repository.
	 * @return The classpath location.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is not a valid version number.
	 * @since saker.build 0.8.1
	 */
	public static ClassPathLocation getInstance(String version) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(version, "version");
		if (!PATTERN_VERSION_NUMBER.matcher(version).matches()) {
			throw new IllegalArgumentException("Invalid version number: " + version);
		}
		return new NestRepositoryClassPathLocation(version);
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

	private NestRepositoryClassPathLocation(String version) {
		this.version = version;
	}

	/**
	 * Gets the version of the saker.nest repository that this class path location refers to.
	 * 
	 * @return The version.
	 */
	public String getVersion() {
		return version;
	}

	@Override
	public ClassPathLoader getLoader() throws IOException {
		return HttpUrlJarFileClassPathLocation.createClassPathLoader(
				new URL("https://api.nest.saker.build/bundle/download/saker.nest-v" + version),
				SakerPath.valueOf(version));
	}

	@Override
	public String getIdentifier() {
		//the identifier should be static in regards to the version of the nest repository.
		//this is in order to have the same storage directories even if the repository version is updated
		return "nest";
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(version);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		version = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		return getClass().getName().hashCode() ^ version.hashCode();
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
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + version + "]";
	}

}
