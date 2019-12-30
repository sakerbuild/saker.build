package saker.build.thirdparty.saker.rmi.io.writer;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.exception.RMIObjectTransferFailureException;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;

/**
 * Writes the object as serialized data.
 * <p>
 * The given object will be serialized using {@link ObjectOutputStream} and the resulting bytes will be transferred.
 * {@link ObjectInputStream} will be used to read the resulting data on the other side.
 * <p>
 * {@link Enum} instances will be transferred the same way as {@link EnumRMIObjectWriteHandler} would.
 * <p>
 * If the given object is not serializable then {@link RMIObjectTransferFailureException} is thrown.
 * 
 * @see RMISerialize
 * @see RMIObjectOutput#writeSerializedObject(Object)
 * @see ObjectOutputStream
 * @see ObjectInputStream
 */
public final class SerializeRMIObjectWriteHandler implements RMIObjectWriteHandler {
	/**
	 * Singleton instance of this class.
	 */
	public static final RMIObjectWriteHandler INSTANCE = new SerializeRMIObjectWriteHandler();

	/**
	 * Creates a new instance.
	 * <p>
	 * Use {@link RMIObjectWriteHandler#serialize()} instead.
	 */
	public SerializeRMIObjectWriteHandler() {
	}

	@Override
	public final ObjectWriterKind getKind() {
		return ObjectWriterKind.SERIALIZE;
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && this.getClass() == obj.getClass();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[]";
	}
}