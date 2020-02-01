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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import saker.build.thirdparty.saker.rmi.annot.transfer.RMIRemote;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.exception.RMIInvalidConfigurationException;
import saker.build.thirdparty.saker.rmi.io.writer.RMIObjectWriteHandler;
import saker.build.thirdparty.saker.util.ReflectUtils;

/**
 * Common properties superclass for specifying transfer mechanism of a given executable method or constructor.
 * <p>
 * Parameter write handlers can be specified for every executable. This class is immutable and a builder is recommended
 * to construct instances.
 * 
 * @param <MT>
 *            The type of the executable.
 * @see Method
 * @see Constructor
 * @see MethodTransferProperties
 * @see ConstructorTransferProperties
 */
abstract class ExecutableTransferProperties<MT extends Executable> {
	private static final RMIObjectWriteHandler[] EMPTY_WRITE_HANDLER_ARRAY = {};
	protected MT executable;
	protected RMIObjectWriteHandler[] parameterWriters;

	protected ExecutableTransferProperties() {
	}

	protected ExecutableTransferProperties(MT executable, RMIObjectWriteHandler[] parameterWriters) {
		Objects.requireNonNull(executable, "executable");
		Objects.requireNonNull(parameterWriters, "parametertWriters");

		if (executable.getParameterCount() != parameterWriters.length) {
			throw new IllegalArgumentException(
					"Parameter count mismatch: " + executable.getParameterCount() + " - " + parameterWriters.length);
		}
		this.executable = executable;
		this.parameterWriters = parameterWriters;
	}

	/**
	 * Returns the specified write handler for the parameter at the given index.
	 * 
	 * @param index
	 *            The index of the parameter.
	 * @return The specified write handler.
	 */
	public RMIObjectWriteHandler getParameterWriter(int index) {
		return parameterWriters[index];
	}

	/**
	 * Gets the executable which this properties are for.
	 * 
	 * @return The executable.
	 */
	public MT getExecutable() {
		return executable;
	}

	/**
	 * Checks if the properties defined by this instance equals to the ones specified by the parameter.
	 * <p>
	 * Equality is not checked for the enclosed executable. Two properties can equal regardless off the executable they
	 * are defined for.
	 * 
	 * @param other
	 *            The other properties.
	 * @return <code>true</code> if the two properties equal.
	 */
	public boolean propertiesEquals(ExecutableTransferProperties<?> other) {
		if (!Arrays.equals(parameterWriters, other.parameterWriters))
			return false;
		return true;
	}

	protected static RMIObjectWriteHandler createWriteHandlerWithAnnotations(RMIWriter writerannot,
			boolean serializepresent, boolean remotepresent, RMIWrap wrap, Class<?> targettype)
			throws RMIInvalidConfigurationException {
		if (RMIStream.isNotCustomizableSerializeType(targettype)
				&& (writerannot != null || serializepresent || remotepresent || wrap != null)) {
			throw new RMIInvalidConfigurationException("Transfer type is not customizable: " + targettype);
		}

		if (writerannot != null) {
			if (serializepresent || remotepresent || wrap != null) {
				throw new RMIInvalidConfigurationException(
						"Other transfer configuration annotations are present apart from "
								+ RMIWriter.class.getSimpleName());
			}
			return getObjectWriteHandlerFromAnnotation(writerannot);
		}
		if (serializepresent) {
			if (remotepresent || wrap != null) {
				throw new RMIInvalidConfigurationException(
						"Other transfer configuration annotations are present apart from "
								+ RMISerialize.class.getSimpleName());
			}
			return RMIObjectWriteHandler.serialize();
		}
		if (remotepresent) {
			if (wrap != null) {
				throw new RMIInvalidConfigurationException(
						"Other transfer configuration annotations are present apart from "
								+ RMIRemote.class.getSimpleName());
			}
			if (targettype != Object.class && targettype != RemoteProxyObject.class) {
				if (!targettype.isInterface()) {
					throw new RMIInvalidConfigurationException("Cannot write type as remote: " + targettype);
				}
			}
			return RMIObjectWriteHandler.remote();
		}
		if (wrap != null) {
			try {
				return RMIObjectWriteHandler.wrapper(wrap.value());
			} catch (IllegalArgumentException e) {
				throw new RMIInvalidConfigurationException("Invalid RMIWrap specification: " + wrap, e);
			}
		}
		return RMIObjectWriteHandler.defaultWriter();
	}

	/**
	 * Creates write handlers for each parameter on the given executable.
	 * 
	 * @param executable
	 *            The executable to create parameter write handlers for.
	 * @return The array of write handlers. Length is the same as parameter count.
	 * @throws RMIInvalidConfigurationException
	 *             In case of invalid configuration.
	 */
	public static RMIObjectWriteHandler[] createParameterWriteHandlers(Executable executable)
			throws RMIInvalidConfigurationException {
		Class<?>[] paramtypes = executable.getParameterTypes();
		if (paramtypes.length == 0) {
			return EMPTY_WRITE_HANDLER_ARRAY;
		}
		RMIObjectWriteHandler[] result = new RMIObjectWriteHandler[paramtypes.length];
		Annotation[][] parameterannotations = executable.getParameterAnnotations();
		for (int i = 0; i < parameterannotations.length; i++) {
			Annotation[] annots = parameterannotations[i];

			try {
				result[i] = createTypeWriteHandler(paramtypes[i], annots);
			} catch (RMIInvalidConfigurationException e) {
				throw new RMIInvalidConfigurationException("Failed to create parameter write handler for parameter[" + i
						+ "] with type " + paramtypes[i] + " on " + executable, e);
			}
		}
		return result;
	}

	/**
	 * Creates a write handler based on the given annotations.
	 * 
	 * @param targettype
	 *            The target type to create the write handler for.
	 * @param annots
	 *            The annotations to base the created write handler on.
	 * @return The write handler.
	 * @throws RMIInvalidConfigurationException
	 *             In case of invalid configuration.
	 */
	public static RMIObjectWriteHandler createTypeWriteHandler(Class<?> targettype, Annotation[] annots)
			throws RMIInvalidConfigurationException {
		RMIWriter writerannot = ReflectUtils.getAnnotation(annots, RMIWriter.class);
		boolean serializepresent = ReflectUtils.getAnnotation(annots, RMISerialize.class) != null;
		boolean remotepresent = ReflectUtils.getAnnotation(annots, RMIRemote.class) != null;
		RMIWrap wrap = ReflectUtils.getAnnotation(annots, RMIWrap.class);

		return createWriteHandlerWithAnnotations(writerannot, serializepresent, remotepresent, wrap, targettype);
	}

	@SuppressWarnings("unchecked")
	static RMIObjectWriteHandler getObjectWriteHandlerFromAnnotation(RMIWriter writerannot)
			throws RMIInvalidConfigurationException {
		Class<? extends RMIObjectWriteHandler> writerhandlerclass = writerannot.value();
		try {
			return ReflectUtils.newInstance(writerhandlerclass);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			throw new RMIInvalidConfigurationException(
					"Failed to instantiate " + writerannot + " with value: " + writerhandlerclass, e);
		}
	}

}
