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
package saker.build.launching;

import java.util.Map;
import java.util.TreeMap;

import saker.build.daemon.DaemonLaunchParameters;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.runtime.ArgumentResolutionException;

class EnvironmentParamContext {
	private static final String PARAM_NAME_EU = "-EU";

	private Map<String, String> environmentUserParameters = new TreeMap<>();

	/**
	 * <pre>
	 * Specifies an environment user parameter for the build environment.
	 * 
	 * The build environment will be constructed with the specified user parameters.
	 * The user parameters may be arbitrary key-value pairs that can be used to
	 * configure different aspects of the build environment. They are usually used
	 * to specify the properties of the machine the environment is running on.
	 * E.g. tooling install locations, version informations, etc...
	 * 
	 * Can be used multiple times to define multiple entries.
	 * </pre>
	 */
	@Parameter(PARAM_NAME_EU)
	public void environmentUserParameter(String key, String value) {
		if (environmentUserParameters.containsKey(key)) {
			throw new ArgumentResolutionException("Environment user parameter specified multiple times: " + key,
					PARAM_NAME_EU);
		}
		environmentUserParameters.put(key, value);
	}

	/**
	 * <pre>
	 * Sets the thread factor for the build environment.
	 * 
	 * The thread factor is a hint for the build environment to set the recommended
	 * number of threads when dealing with multi-threaded worker threads.
	 * 
	 * If unspecified, 0, or negative, the thread factor will be determined in an
	 * implementation dependent manner. (Usually based on the number of cores the CPU has.)
	 * </pre>
	 */
	@Parameter("-thread-factor")
	public int threadFactor;

	public Map<String, String> getEnvironmentUserParameters() {
		return environmentUserParameters;
	}

	public int getThreadFactor() {
		return threadFactor;
	}

	public void applyToBuilder(DaemonLaunchParameters.Builder builder) {
		builder.setUserParameters(environmentUserParameters);
		builder.setThreadFactor(threadFactor);
	}
}
