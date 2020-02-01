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

import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.exception.RMIInvalidConfigurationException;
import saker.build.thirdparty.saker.rmi.io.writer.RMIObjectWriteHandler;
import saker.build.thirdparty.saker.rmi.io.writer.WrapperRMIObjectWriteHandler;

/**
 * Properties class for specifying the transfer mechanism of a given type over RMI.
 * <p>
 * This class can be used to specify the write handler for a given type if it is encountered during RMI object transfer.
 * The specified write handler will be used if an instance of the given type is getting transferred.
 * <p>
 * By using these properties you can avoid annotating every method that handles instances of a given type and can
 * specify a generic write handler for every occurrence.
 * <p>
 * These properties are only used if the transferred object is exactly the same type as the specified type. If you
 * specify transfer properties for a class that is an interface or annotation, that is most likely an error and the
 * properties will not be used.
 * <p>
 * If transfer properties for a type is set, and it is getting transferred over a method which also has write handler
 * set for the given transfer point, then the write handler for the type properties will be used instead of the one
 * specified for the method.
 * <p>
 * You can use {@link RMIWriter} annotation to specify the write handler for the annotated class.
 * 
 * @param <C>
 *            The type of the class.
 * @see RMITransferProperties
 * @see RMIWriter
 */
public final class ClassTransferProperties<C> {
	protected final Class<C> type;
	protected final RMIObjectWriteHandler writeHandler;

	/**
	 * Creates a new instance with the given class and write handler.
	 * 
	 * @param type
	 *            The type the properties apply to.
	 * @param writeHandler
	 *            The write handler to use when an instance of the type is encountered.
	 */
	public ClassTransferProperties(Class<C> type, RMIObjectWriteHandler writeHandler) {
		this.type = type;
		this.writeHandler = writeHandler;
	}

	/**
	 * Creates class transfer properties based on annotations for the given class.
	 * <p>
	 * The annotations on the parameter class are examined and a transfer configuration is created based on them.
	 * 
	 * @param <C>
	 *            The type of the class.
	 * @param clazz
	 *            The class to generate the properties for.
	 * @return The parsed properties or <code>null</code> if there are no related annotations.
	 */
	public static <C> ClassTransferProperties<C> createForAnnotations(Class<C> clazz) {
		RMIWriter annot = clazz.getAnnotation(RMIWriter.class);
		RMIWrap wrapannot = clazz.getAnnotation(RMIWrap.class);
		if (annot != null) {
			if (wrapannot != null) {
				throw new RMIInvalidConfigurationException(
						"Multiple transfer annotations present: " + annot + " - " + wrapannot);
			}
			RMIObjectWriteHandler writehandler = ExecutableTransferProperties
					.getObjectWriteHandlerFromAnnotation(annot);
			return new ClassTransferProperties<>(clazz, writehandler);
		}
		if (wrapannot != null) {
			WrapperRMIObjectWriteHandler writehandler;
			try {
				writehandler = new WrapperRMIObjectWriteHandler(wrapannot.value());
			} catch (IllegalArgumentException e) {
				throw new RMIInvalidConfigurationException("Invalid RMIWrap specification: " + wrapannot, e);
			}
			return new ClassTransferProperties<>(clazz, writehandler);
		}
		return null;
	}

	/**
	 * Gets the type the properties apply to.
	 * 
	 * @return The type.
	 */
	public Class<C> getType() {
		return type;
	}

	/**
	 * Gets the write handler to use when an instance of the type is encountered.
	 * 
	 * @return The write handler.
	 */
	public RMIObjectWriteHandler getWriteHandler() {
		return writeHandler;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + type + ": " + writeHandler + "]";
	}

}
