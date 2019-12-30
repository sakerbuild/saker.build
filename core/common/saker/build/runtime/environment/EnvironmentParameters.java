package saker.build.runtime.environment;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class EnvironmentParameters {
	private Path sakerJar;
	private Path storageDirectory;
	private int threadFactor = -1;
	private ThreadGroup parentThreadGroup;
	private Map<String, String> userParameters = Collections.emptyNavigableMap();

	private EnvironmentParameters(Path sakerJar) {
		Objects.requireNonNull(sakerJar, "sakerJar");
		this.sakerJar = sakerJar;
	}

	public Path getSakerJar() {
		return sakerJar;
	}

	public Path getStorageDirectory() {
		return storageDirectory;
	}

	public int getThreadFactor() {
		return threadFactor;
	}

	public int getThreadFactorDefaulted() {
		int factor = threadFactor;
		if (factor <= 0) {
			return getDefaultThreadFactor();
		}
		return threadFactor;
	}

	public ThreadGroup getEnvironmentThreadGroupParent() {
		return parentThreadGroup;
	}

	public Map<String, String> getUserParameters() {
		return userParameters;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (sakerJar != null ? "sakerJar=" + sakerJar + ", " : "")
				+ (storageDirectory != null ? "storageDirectory=" + storageDirectory + ", " : "") + "threadFactor="
				+ threadFactor + ", "
				+ (parentThreadGroup != null ? "parentThreadGroup=" + parentThreadGroup + ", " : "")
				+ (userParameters != null ? "userParameters=" + userParameters : "") + "]";
	}

	public static int getDefaultThreadFactor() {
		return Runtime.getRuntime().availableProcessors() * 3 / 2;
	}

	public static Builder builder(Path sakerjar) {
		return new Builder(sakerjar);
	}

	public static class Builder {
		private EnvironmentParameters result;

		public Builder(Path sakerJar) {
			result = new EnvironmentParameters(sakerJar);
		}

		public Builder setStorageDirectory(Path storageDirectory) {
			result.storageDirectory = storageDirectory;
			return this;
		}

		public Builder setThreadFactor(int threadFactor) {
			result.threadFactor = threadFactor;
			return this;
		}

		public Builder setEnvironmentThreadGroupParent(ThreadGroup parentThreadGroup) {
			result.parentThreadGroup = parentThreadGroup;
			return this;
		}

		public Builder setUserParameters(Map<String, String> userParameters) {
			if (ObjectUtils.isNullOrEmpty(userParameters)) {
				result.userParameters = Collections.emptyNavigableMap();
			} else {
				result.userParameters = ImmutableUtils.makeImmutableNavigableMap(userParameters);
			}
			return this;
		}

		public EnvironmentParameters build() {
			EnvironmentParameters res = result;
			this.result = null;
			return res;
		}
	}
}
