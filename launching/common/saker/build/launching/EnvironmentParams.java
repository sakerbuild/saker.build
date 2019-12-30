package saker.build.launching;

import java.util.Map;
import java.util.TreeMap;

import saker.build.daemon.DaemonLaunchParameters;
import sipka.cmdline.api.Parameter;

class EnvironmentParams {
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
	@Parameter("-EU")
	public void environmentUserParameter(String key, String value) {
		if (environmentUserParameters.containsKey(key)) {
			throw new IllegalArgumentException("Environment user parameter specified multiple times: " + key);
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
