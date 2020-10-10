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
package saker.build.util.data;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import saker.build.runtime.execution.SakerLog;
import saker.build.task.BuildTargetTaskResult;
import saker.build.task.TaskResultResolver;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.util.data.annotation.ConverterConfiguration;
import testing.saker.build.flag.TestFlag;

/**
 * Contextual information for convertion an object to a target type.
 * <p>
 * This class holds useful information and objects for converting an object to a target type.
 * <p>
 * The {@linkplain #getBaseClassLoader() base classloader} is the classloader to use when considering the receiver class
 * of the conversion. For example, it should be used when defining any {@link Proxy} objects, and the types should be
 * examined to be assignable in the context of the base classloader. (This means that two different versions of the same
 * target type class should be visible from the base classloader.)
 * <p>
 * The {@linkplain #getTaskResultResolver() task result resolver} should be used when encountering task execution
 * related data. It can be used to retrieve the results for a given task identifier. Data converters should handle the
 * case when they encounter instances of {@link StructuredTaskResult} or {@link BuildTargetTaskResult} as the value
 * object. The task result resolver may be <code>null</code> in which case it cannot be used.
 * <p>
 * The conversion context holds information about the generic locations and what converters should be applied for them.
 * See {@link ConverterConfiguration} for generic location example.
 * 
 * @see ConverterConfiguration
 * @see DataConverter
 */
public final class ConversionContext {
	protected ClassLoader baseClassLoader;
	protected TaskResultResolver taskResultResolver;
	protected Map<GenericArgumentLocation, ConverterConfiguration> genericConverters;
	protected GenericArgumentLocation currentGenericLocation = GenericArgumentLocation.INSTANCE_ROOT;

	ConversionContext(ClassLoader baseClassLoader, TaskResultResolver taskResultResolver,
			Map<GenericArgumentLocation, ConverterConfiguration> genericConverters) {
		this.baseClassLoader = baseClassLoader;
		this.taskResultResolver = taskResultResolver;
		this.genericConverters = genericConverters;
	}

	ConversionContext(ClassLoader baseClassLoader, TaskResultResolver taskResultResolver,
			Iterable<? extends ConverterConfiguration> converterconfigurations) {
		this.baseClassLoader = baseClassLoader;
		this.taskResultResolver = taskResultResolver;
		Iterator<? extends ConverterConfiguration> confit;
		if (converterconfigurations != null && (confit = converterconfigurations.iterator()).hasNext()) {
			genericConverters = new HashMap<>();
			do {
				ConverterConfiguration cc = confit.next();
				GenericArgumentLocation genericloc = new GenericArgumentLocation(cc.genericArgumentIndex());
				ConverterConfiguration prev = genericConverters.putIfAbsent(genericloc, cc);
				if (prev != null) {
					String msg = "Multiple converter configurations defined: " + converterconfigurations
							+ " with same generic argument location: " + genericloc;
					throw new ConversionFailedException(msg);
				}
			} while (confit.hasNext());
		} else {
			genericConverters = Collections.emptyMap();
		}
	}

	/**
	 * Gets the base classloader to use.
	 * 
	 * @return The base classloader.
	 */
	public ClassLoader getBaseClassLoader() {
		return baseClassLoader;
	}

	/**
	 * Gets the task result resolver.
	 * 
	 * @return The task result resolver or <code>null</code> if not available.
	 */
	public TaskResultResolver getTaskResultResolver() {
		return taskResultResolver;
	}

	/**
	 * Gets the user specified converter configuration for the given generic argument location.
	 * 
	 * @param argumentlocation
	 *            The generic argument location to get the converter configuration for.
	 * @return The converter configuration or <code>null</code> if not defined.
	 */
	public ConverterConfiguration getConverterConfiguration(GenericArgumentLocation argumentlocation) {
		return genericConverters.get(argumentlocation);
	}

	/**
	 * Gets the current converter configuration that this conversion context should use.
	 * <p>
	 * It is the same as calling:
	 * 
	 * <pre>
	 * conversioncontext.getConverterConfiguration(conversioncontext.getCurrentGenericLocation())
	 * </pre>
	 * 
	 * @return The current converter configuration or <code>null</code> if not defined.
	 */
	public ConverterConfiguration getCurrentConverterConfiguration() {
		return getConverterConfiguration(getCurrentGenericLocation());
	}

	/**
	 * Gets if there are any user defined converter configurations.
	 * <p>
	 * If there are no converter configurations defined, {@link #getConverterConfiguration(GenericArgumentLocation)} and
	 * {@link #getCurrentConverterConfiguration()} will always return <code>null</code>, irrelevant of generic argument
	 * location. (This is true for {@linkplain #genericChildContext(int) subcontexts} as well.)
	 * 
	 * @return <code>true</code> if there are converter configurations defined.
	 */
	public boolean isAnyConverterConfigurationsDefined() {
		return genericConverters != Collections.<GenericArgumentLocation, ConverterConfiguration>emptyMap();
	}

	/**
	 * Gets the current generic argument location that this conversion context is associated with.
	 * 
	 * @return The current generic argument location.
	 */
	public GenericArgumentLocation getCurrentGenericLocation() {
		return currentGenericLocation;
	}

	/**
	 * Cretes a conversion subcontext for the specified generic index.
	 * <p>
	 * The generic index will be appended to the current generic argument location, and a new subcontext will be created
	 * with it. This new context should be used when converting elements to the generic type.
	 * 
	 * @param genericindex
	 *            The generic index.
	 * @return A new conversion subcontext appended with the given generic index.
	 */
	public ConversionContext genericChildContext(int genericindex) {
		ConversionContext result = new ConversionContext(baseClassLoader, taskResultResolver, genericConverters);
		result.currentGenericLocation = this.currentGenericLocation.child(genericindex);
		return result;
	}
}
