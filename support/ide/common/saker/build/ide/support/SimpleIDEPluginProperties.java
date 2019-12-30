package saker.build.ide.support;

import java.util.Map.Entry;
import java.util.Set;

import saker.build.ide.support.properties.IDEPluginProperties;
import saker.build.thirdparty.saker.util.ImmutableUtils;

public class SimpleIDEPluginProperties implements IDEPluginProperties {
	private static final IDEPluginProperties EMPTY = new SimpleIDEPluginProperties();

	public static IDEPluginProperties empty() {
		return EMPTY;
	}

	private String storageDirectory;
	private Set<? extends Entry<String, String>> userParameters;

	private SimpleIDEPluginProperties() {
	}

	private SimpleIDEPluginProperties(IDEPluginProperties copy) {
		this.storageDirectory = copy.getStorageDirectory();
		this.userParameters = SakerIDEPlugin.unmodifiablizeEntrySet(copy.getUserParameters());
	}

	private final void unmodifiablize() {
		this.userParameters = ImmutableUtils.unmodifiableSet(this.userParameters);
	}

	@Override
	public String getStorageDirectory() {
		return storageDirectory;
	}

	@Override
	public Set<? extends Entry<String, String>> getUserParameters() {
		return userParameters;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder builder(IDEPluginProperties copy) {
		return new Builder(copy);
	}

	public static final class Builder {
		private SimpleIDEPluginProperties result;

		public Builder() {
			result = new SimpleIDEPluginProperties();
		}

		public Builder(IDEPluginProperties copy) {
			result = new SimpleIDEPluginProperties(copy);
		}

		public Builder setStorageDirectory(String storageDirectory) {
			result.storageDirectory = storageDirectory;
			return this;
		}

		public Builder setUserParameters(Set<? extends Entry<String, String>> userParameters) {
			result.userParameters = SakerIDEPlugin.unmodifiablizeEntrySet(userParameters);
			return this;
		}

		public IDEPluginProperties build() {
			SimpleIDEPluginProperties res = this.result;
			this.result = null;
			res.unmodifiablize();
			return res;
		}

		public IDEPluginProperties buildReuse() {
			SimpleIDEPluginProperties res = new SimpleIDEPluginProperties(this.result);
			res.unmodifiablize();
			return res;
		}
	}
}
