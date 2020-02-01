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
package saker.build.thirdparty.saker.rmi.io;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import saker.build.thirdparty.saker.rmi.exception.RMIObjectTransferFailureException;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.rmi.io.writer.DefaultRMIObjectWriteHandler;

/**
 * Object stream class for writing objects to the RMI transfer stream.
 * <p>
 * Declared methods in this class can be used to precisely control the output of transferred objects.
 */
public interface RMIObjectOutput extends ObjectOutput {
	/**
	 * Writes the parameter object using the default write mechanism.
	 * 
	 * @see DefaultRMIObjectWriteHandler
	 */
	@Override
	public void writeObject(Object obj) throws IOException;

	/**
	 * Writes the parameter object as a remote object on the other endpoint.
	 * <p>
	 * The object will be read on the receiver side and any calls made to it will be called on the parameter local
	 * instance.
	 * <p>
	 * If the parameter is already a remote object on the other endpoint then the other side will receive the local
	 * instance to it. Therefore the received object will identity equal to the remote object denoted by this parameter
	 * instance.
	 * <p>
	 * Use {@link RMIObjectInput#readObject()} to read the object.
	 * 
	 * @param obj
	 *            The object to write a remove proxy of.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see RMIObjectInput#readObject()
	 */
	public void writeRemoteObject(Object obj) throws IOException;

	/**
	 * Writes the parameter object as serialized data.
	 * <p>
	 * The object will be serialized using {@link ObjectOutputStream} and resulting bytes will be transferred. If the
	 * parameter is a remote object, then it will not be serialized but received as the local instance on the other
	 * endpoint. {@link ObjectInputStream} will be used to read the transferred data.
	 * <p>
	 * Use {@link RMIObjectInput#readObject()} to read the object.
	 * 
	 * @param obj
	 *            The object to serialize.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see RMIObjectInput#readObject()
	 * @see ObjectOutputStream
	 * @see ObjectInputStream
	 */
	public void writeSerializedObject(Object obj) throws IOException;

	/**
	 * Writes the parameter using the specified wrapper class.
	 * <p>
	 * The serialization will occur as specified by {@link RMIWrapper}.
	 * <p>
	 * Use {@link RMIObjectInput#readObject()} to read the object.
	 * 
	 * @param obj
	 *            The object to write.
	 * @param wrapperclass
	 *            The wrapper class which handles the writing.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If wrapper class is <code>null</code>.
	 * @see RMIWrapper
	 */
	public void writeWrappedObject(Object obj, Class<? extends RMIWrapper> wrapperclass)
			throws IOException, NullPointerException;

	/**
	 * Writes the parameter as an enum.
	 * 
	 * @param obj
	 *            The object to write.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws RMIObjectTransferFailureException
	 *             If the object cannot be cast to an {@link Enum}.
	 */
	public void writeEnumObject(Object obj) throws IOException, RMIObjectTransferFailureException;
}
