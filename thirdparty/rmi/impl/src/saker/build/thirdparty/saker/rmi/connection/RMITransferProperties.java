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
package saker.build.thirdparty.saker.rmi.connection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Class which specifies the transfer properties to use during the RMI connection.
 * <p>
 * This class is immutable and the {@link Builder} should be use for its construction.
 * 
 * @see MethodTransferProperties
 * @see ConstructorTransferProperties
 * @see ClassTransferProperties
 */
public class RMITransferProperties extends RMITransferPropertiesHolder {
	protected Map<Executable, ExecutableTransferProperties<?>> executableProperties;
	protected Map<Class<?>, ClassTransferProperties<?>> classProperties;

	RMITransferProperties() {
		this(new HashMap<>(), new HashMap<>());
	}

	RMITransferProperties(Map<Executable, ExecutableTransferProperties<?>> executableProperties,
			Map<Class<?>, ClassTransferProperties<?>> classProperties) {
		this.executableProperties = executableProperties;
		this.classProperties = classProperties;
	}

	@Override
	@SuppressWarnings("unchecked")
	public MethodTransferProperties getExecutableProperties(Method method) {
		return (MethodTransferProperties) retrieveExecutableProperty(method);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <C> ConstructorTransferProperties<C> getExecutableProperties(Constructor<C> constructor) {
		return (ConstructorTransferProperties<C>) retrieveExecutableProperty(constructor);
	}

	@Override
	public <C> ClassTransferProperties<C> getClassProperties(Class<C> clazz) {
		return retrieveClassProperties(clazz);
	}

	private ExecutableTransferProperties<?> retrieveExecutableProperty(Method method) {
		return executableProperties.get(method);
	}

	private ExecutableTransferProperties<?> retrieveExecutableProperty(Constructor<?> constructor) {
		return executableProperties.get(constructor);
	}

	@SuppressWarnings("unchecked")
	private <C> ClassTransferProperties<C> retrieveClassProperties(Class<C> clazz) {
		return (ClassTransferProperties<C>) classProperties.get(clazz);
	}

	/**
	 * Creates a new builder instance for this class.
	 * 
	 * @return The builder instance.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder class for the enclosing transfer properties.
	 */
	public static final class Builder {
		private RMITransferProperties result = new RMITransferProperties();

		/**
		 * Creates a new builder instance.
		 * <p>
		 * Use {@link RMITransferProperties#builder()} instead.
		 */
		Builder() {
		}

		/**
		 * Adds/overwrites the given properties to the result.
		 * 
		 * @param properties
		 *            The properties to add.
		 * @return <code>this</code>
		 */
		public Builder add(MethodTransferProperties properties) {
			result.executableProperties.put(properties.getExecutable(), properties);
			return this;
		}

		/**
		 * Adds/overwrites the given properties to the result.
		 * 
		 * @param properties
		 *            The properties to add.
		 * @return <code>this</code>
		 */
		public Builder add(ConstructorTransferProperties<?> properties) {
			result.executableProperties.put(properties.getExecutable(), properties);
			return this;
		}

		/**
		 * Adds/overwrites the given properties to the result.
		 * 
		 * @param properties
		 *            The properties to add.
		 * @return <code>this</code>
		 */
		public Builder add(ClassTransferProperties<?> properties) {
			result.classProperties.put(properties.getType(), properties);
			return this;
		}

		/**
		 * Builds the transfer properties and consumes <code>this</code> builder.
		 * <p>
		 * The builder cannot be reused after calling this method.
		 * 
		 * @return The created transfer properties.
		 */
		public RMITransferProperties build() {
			RMITransferProperties res = this.result;
			this.result = null;
			return res;
		}
	}

}
