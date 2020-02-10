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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import saker.apiextract.api.ExcludeApi;
import saker.build.file.content.CommonContentDescriptorSupplier;
import saker.build.file.content.ContentDescriptorSupplier;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Configuration class for specifying how the changes in the contents of the files should be determined.
 * <p>
 * The build system can detect changes in the used files in different ways. This configuration object allows specifying
 * how the contents of different files should be treated. By default, the build system uses the last modification time
 * and the size of the file to detect if a file changed between build executions.
 * <p>
 * The behaviour of the content detection can be specified using the {@link ContentDescriptorSupplier} interface. Common
 * implementations of it is available in the {@link CommonContentDescriptorSupplier} enumeration.
 * <p>
 * The class allows specifying the content descriptor supplier implementations for {@linkplain WildcardPath wildcard
 * patterns} on given file providers. This means that the content descriptor supplier will be chosen based on the path
 * to the file. If there is a matching wildcard for the path, the the associated content descriptor supplier is used to
 * determine the contents of the file. If no matching wildcard is found, then the specified fallback content descriptor
 * supplier is used.
 * <p>
 * If a path matches multiple wildcards, the associated content descriptor supplier to the first matching wildcard will
 * be used that was added to the configuration.
 */
@RMIWrap(DatabaseConfiguration.ConfigurationRMIWrapper.class)
public final class DatabaseConfiguration {

	/**
	 * Configuration object specifying a wildcard path and an associated content descriptor supplier.
	 * <p>
	 * If the wildcard match the queried path, the associated content descriptor supplier will be used for determining
	 * the contents of the underlying file.
	 */
	@RMIWrap(ContentConfigRMIWrapper.class)
	@ExcludeApi
	public final static class ContentDescriptorConfiguration {
		protected WildcardPath wildcard;
		protected ContentDescriptorSupplier descriptorSupplier;

		protected ContentDescriptorConfiguration(WildcardPath wildcard, ContentDescriptorSupplier descriptorSupplier) {
			this.wildcard = wildcard;
			this.descriptorSupplier = descriptorSupplier;
		}

		public WildcardPath getWildcard() {
			return wildcard;
		}

		public ContentDescriptorSupplier getContentDescriptorSupplier() {
			return descriptorSupplier;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (wildcard != null ? "wildcard=" + wildcard + ", " : "")
					+ (descriptorSupplier != null ? "descriptorSupplier=" + descriptorSupplier : "") + "]";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((descriptorSupplier == null) ? 0 : descriptorSupplier.hashCode());
			result = prime * result + ((wildcard == null) ? 0 : wildcard.hashCode());
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
			ContentDescriptorConfiguration other = (ContentDescriptorConfiguration) obj;
			if (descriptorSupplier == null) {
				if (other.descriptorSupplier != null)
					return false;
			} else if (!descriptorSupplier.equals(other.descriptorSupplier))
				return false;
			if (wildcard == null) {
				if (other.wildcard != null)
					return false;
			} else if (!wildcard.equals(other.wildcard))
				return false;
			return true;
		}
	}

	/**
	 * Builder class for {@link DatabaseConfiguration}.
	 * <p>
	 * The builder is single use, it cannot be reused after callig {@link #build()}.
	 */
	public static final class Builder {
		private Map<RootFileProviderKey, Set<ContentDescriptorConfiguration>> configurations = new HashMap<>();
		private ContentDescriptorSupplier fallbackContentSupplier;

		protected Builder() {
			this(DEFAULT_FALLBACK_CONTENT_DESCRIPTOR_SUPPLIER);
		}

		protected Builder(ContentDescriptorSupplier fallbackContentSupplier) {
			this.fallbackContentSupplier = fallbackContentSupplier;
		}

		/**
		 * Adds the specified wildcard pattern and associated content descriptor supplier for the given provider key.
		 * 
		 * @param provider
		 *            The file provider to associated the wildcard and content descriptor supplier with.
		 * @param wildcard
		 *            The wildcard.
		 * @param descriptorsupplier
		 *            The content descriptor supplier.
		 * @return <code>this</code>
		 * @throws NullPointerException
		 *             If any of the arguments are <code>null</code>.
		 */
		public Builder add(RootFileProviderKey provider, WildcardPath wildcard,
				ContentDescriptorSupplier descriptorsupplier) throws NullPointerException {
			Objects.requireNonNull(provider, "provider key");
			Objects.requireNonNull(descriptorsupplier, "wildcard");
			Objects.requireNonNull(descriptorsupplier, "descriptor supplier");

			Map<RootFileProviderKey, Set<ContentDescriptorConfiguration>> configs = configurations;
			if (configs == null) {
				throw new IllegalStateException("Builder already used.");
			}

			configs.computeIfAbsent(provider, Functionals.linkedHashSetComputer())
					.add(new ContentDescriptorConfiguration(wildcard, descriptorsupplier));
			return this;
		}

		/**
		 * Builds the {@link DatabaseConfiguration}.
		 * <p>
		 * The builder is no longer useable if this call succeeds.
		 * 
		 * @return The constructed database configuration.
		 */
		public DatabaseConfiguration build() {
			Map<RootFileProviderKey, Set<ContentDescriptorConfiguration>> configs = configurations;
			if (configs == null) {
				throw new IllegalStateException("Builder already used.");
			}
			DatabaseConfiguration result = new DatabaseConfiguration(ImmutableUtils.unmodifiableMap(configs),
					fallbackContentSupplier);
			this.configurations = null;
			return result;
		}
	}

	private static final ContentDescriptorSupplier DEFAULT_FALLBACK_CONTENT_DESCRIPTOR_SUPPLIER = CommonContentDescriptorSupplier.FILE_ATTRIBUTES;
	private static final DatabaseConfiguration DEFAULT_CONFIGURATION = new DatabaseConfiguration(Collections.emptyMap(),
			DEFAULT_FALLBACK_CONTENT_DESCRIPTOR_SUPPLIER);

	private Map<RootFileProviderKey, Set<ContentDescriptorConfiguration>> configurations;
	private ContentDescriptorSupplier fallbackContentSupplier;

	private DatabaseConfiguration(Map<RootFileProviderKey, Set<ContentDescriptorConfiguration>> configurations,
			ContentDescriptorSupplier fallbackContentSupplier) {
		this.configurations = configurations;
		this.fallbackContentSupplier = fallbackContentSupplier;
	}

	/**
	 * Gets the default {@link ContentDescriptorSupplier} for paths which are not covered by the current
	 * {@link DatabaseConfiguration} for the execution.
	 * <p>
	 * By default, the build system uses the file attributes to determine the content descriptor. (File size, and last
	 * modification time)
	 * 
	 * @return The default fallback content descriptor supplier.
	 */
	public static ContentDescriptorSupplier getDefaultFallbackContentDescriptorSupplier() {
		return DEFAULT_FALLBACK_CONTENT_DESCRIPTOR_SUPPLIER;
	}

	/**
	 * Gets the default database configuration that uses the default content descriptor supplier for all files.
	 * 
	 * @return The default configuration.
	 * @see #getDefaultFallbackContentDescriptorSupplier()
	 */
	public static DatabaseConfiguration getDefault() {
		return DEFAULT_CONFIGURATION;
	}

	/**
	 * Creates a new builder with the default content descriptor supplier as a fallback supplier.
	 * 
	 * @return The new builder.
	 */
	public static DatabaseConfiguration.Builder builder() {
		return builder(DEFAULT_FALLBACK_CONTENT_DESCRIPTOR_SUPPLIER);
	}

	/**
	 * Creates a new builder with the given fallback content descriptor supplier.
	 * 
	 * @param fallbackContentSupplier
	 *            The fallback supplier.
	 * @return The new builder.
	 * @see #getFallbackContentSupplier()
	 */
	public static DatabaseConfiguration.Builder builder(ContentDescriptorSupplier fallbackContentSupplier) {
		return new Builder(fallbackContentSupplier);
	}

	/**
	 * Gets the content descriptor supplier to use for the given path at the specified file provider.
	 * <p>
	 * If no wildcard matches the path in this configuration, the {@linkplain #getFallbackContentSupplier() fallback
	 * supplier} is returned.
	 * 
	 * @param providerkey
	 *            The provider key identifying the file provider.
	 * @param path
	 *            The path.
	 * @return The content descriptor supplier to use.
	 */
	public ContentDescriptorSupplier getContentDescriptorSupplier(RootFileProviderKey providerkey, SakerPath path) {
		Set<ContentDescriptorConfiguration> confs = configurations.get(providerkey);
		if (confs != null) {
			for (ContentDescriptorConfiguration cdc : confs) {
				if (cdc.wildcard.includes(path)) {
					return cdc.descriptorSupplier;
				}
			}
		}
		return fallbackContentSupplier;
	}

	/**
	 * Gets the fallback content descriptor supplier.
	 * <p>
	 * The fallback supplier is used when no defined wildcard path matches the path currently being queried.
	 * 
	 * @return The fallback supplier.
	 */
	public ContentDescriptorSupplier getFallbackContentSupplier() {
		return fallbackContentSupplier;
	}

	@ExcludeApi
	public Map<RootFileProviderKey, Set<ContentDescriptorConfiguration>> internalGetConfigurations() {
		return configurations;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((configurations == null) ? 0 : configurations.hashCode());
		result = prime * result + ((fallbackContentSupplier == null) ? 0 : fallbackContentSupplier.hashCode());
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
		DatabaseConfiguration other = (DatabaseConfiguration) obj;
		if (configurations == null) {
			if (other.configurations != null)
				return false;
		} else if (!configurations.equals(other.configurations))
			return false;
		if (fallbackContentSupplier == null) {
			if (other.fallbackContentSupplier != null)
				return false;
		} else if (!fallbackContentSupplier.equals(other.fallbackContentSupplier))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + configurations + "]";
	}

	protected static final class ConfigurationRMIWrapper implements RMIWrapper {
		private DatabaseConfiguration configuration;

		public ConfigurationRMIWrapper() {
		}

		public ConfigurationRMIWrapper(DatabaseConfiguration pathConfiguration) {
			this.configuration = pathConfiguration;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeObject(configuration.fallbackContentSupplier);
			SerialUtils.writeExternalMap(out, configuration.configurations, RMIObjectOutput::writeSerializedObject,
					SerialUtils::writeExternalCollection);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			ContentDescriptorSupplier defaultcontentsupplier = (ContentDescriptorSupplier) in.readObject();
			Map<RootFileProviderKey, Set<ContentDescriptorConfiguration>> configurations = Collections
					.unmodifiableMap(SerialUtils.readExternalMap(new HashMap<>(), in, SerialUtils::readExternalObject,
							SerialUtils::readExternalImmutableLinkedHashSet));

			configuration = new DatabaseConfiguration(configurations, defaultcontentsupplier);
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object resolveWrapped() {
			return configuration;
		}
	}

	protected static final class ContentConfigRMIWrapper implements RMIWrapper {
		private ContentDescriptorConfiguration contentConfig;

		public ContentConfigRMIWrapper() {
		}

		public ContentConfigRMIWrapper(ContentDescriptorConfiguration contentConfig) {
			this.contentConfig = contentConfig;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeObject(contentConfig.wildcard);
			out.writeSerializedObject(contentConfig.descriptorSupplier);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			WildcardPath wc = (WildcardPath) in.readObject();
			ContentDescriptorSupplier descsupplier = (ContentDescriptorSupplier) in.readObject();
			contentConfig = new ContentDescriptorConfiguration(wc, descsupplier);
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object resolveWrapped() {
			return contentConfig;
		}

	}
}
