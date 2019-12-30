package saker.build.thirdparty.saker.rmi.io.wrap;

import java.io.IOException;

import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.writer.WrapperRMIObjectWriteHandler;

/**
 * Class for specializing the serialization of an object over the RMI connection.
 * <p>
 * The purpose of this class is to fine grain the transfer of some objects over the RMI connection. Implementations of
 * this class must have a public default constructor, and must have at least one constructor which take a single
 * argument of the type which is being the subject of transferring. <br>
 * E.g. If a method is called with a {@link String} parameter, it can have a constructor with {@link String},
 * {@link CharSequence}, or other assignable classes as a single parameter. The object to be transferred must be
 * assignable to at least one of the constructor parameter types.
 * <p>
 * Serialization: <br>
 * 1. An instance of the {@link RMIWrapper} implementation is constructer using an appropriate constructor. <br>
 * 2. The class of the wrapper implementation is serialized over the RMI connection. <br>
 * 3. {@link #writeWrapped} is called to serialize the object which was passed in the constructor. <br>
 * <p>
 * Deserialization: <br>
 * 1. The wrapper class is read from the stream by the RMI runtime. <br>
 * 2. The wrapper is instantiated using the public default constructor. <br>
 * 3. {@link #readWrapped} is called to read the serialized data. <br>
 * 4. {@link #resolveWrapped} is called to retrieve the actual object to pass to the request. <br>
 * <p>
 * Instances of this interface should only be used to configure the transfer of object and should not be used in the RMI
 * runtime directly. I.e. do not pass instances of {@link RMIWrapper} as arguments, return values, etc... for method
 * calls. Any passed objects will be unwrapped using {@link #getWrappedObject()} regardless of use-case.
 * 
 * @see WrapperRMIObjectWriteHandler
 * @see RMIWrap
 */
public interface RMIWrapper {
	/**
	 * Writes the wrapped object to the RMI object output stream.
	 * <p>
	 * Important aspect of writing objects using wrappers is that in this method if any of the object writing method is
	 * called, then the non customizable aspects of the serializable will not be applied to the object that is being
	 * wrapped.
	 * <p>
	 * E.g. If an {@link RMIWrapper} is defined for an object type <i>T</i>, then calling
	 * {@link RMIObjectOutput#writeRemoteObject(Object)} will not cause the subject object with the type <i>T</i> to be
	 * written using an {@link RMIWrapper} again, but it will be actually written as a remote object.
	 * 
	 * @param out
	 *            The output stream to write to.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public void writeWrapped(RMIObjectOutput out) throws IOException;

	/**
	 * Reads the wrapped object from the RMI object input stream.
	 * 
	 * @param in
	 *            The input stream to read from.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class for a serialized object cannot be found.
	 */
	public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException;

	/**
	 * Resolves the wrapped object during deserialization.
	 * <p>
	 * The result of this method will be returned to the caller, or used during the RMI request.
	 * 
	 * @return The result of the object transfer.
	 */
	public Object resolveWrapped();

	/**
	 * Gets the wrapped object which should be serialized during RMI transfer.
	 * <p>
	 * If {@link #resolveWrapped()} returned <code>this</code> then this method will be called when the wrapper instance
	 * is serialized to an other endpoint. It is preferred to return a remote proxy to the previously wrapped object to
	 * pass back to the original endpoint when reverse request is made. Returning <code>this</code> from this method
	 * will result in the serialization of this wrapper through RMI.
	 * <p>
	 * If {@link #resolveWrapped()} did not return <code>this</code> then this method can freely return
	 * <code>null</code> or throw exceptions (e.g. {@link UnsupportedOperationException}) because it will never be
	 * called.
	 * 
	 * @return The object to serialize instead of <code>this</code>.
	 */
	public Object getWrappedObject();
}
