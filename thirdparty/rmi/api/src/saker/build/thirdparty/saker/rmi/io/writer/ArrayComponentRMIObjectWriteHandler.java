package saker.build.thirdparty.saker.rmi.io.writer;

import java.util.Objects;

import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.exception.RMIObjectTransferFailureException;

/**
 * Writes the object as an array with the given component write handler.
 * <p>
 * The given object is transferred as an array, and the component writer will be applied for every element in the array.
 * <p>
 * If the given object is not an array then {@link RMIObjectTransferFailureException} is thrown.
 * <p>
 * In order to use this class with {@link RMIWriter}, subclass it, provide a no-arg default constructor which sets the
 * appropriate {@link RMIObjectWriteHandler} for the component writer, and use it as a value for the annotation.
 * 
 * @see RMIWriter
 */
public class ArrayComponentRMIObjectWriteHandler implements RMIObjectWriteHandler {
	/**
	 * The component write handler.
	 */
	protected final RMIObjectWriteHandler componentWriter;

	/**
	 * Creates a new instance with the specified component writer.
	 * 
	 * @param componentWriter
	 *            The component write handler.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ArrayComponentRMIObjectWriteHandler(RMIObjectWriteHandler componentWriter) throws NullPointerException {
		Objects.requireNonNull(componentWriter, "component writer");
		this.componentWriter = componentWriter;
	}

	@Override
	public final ObjectWriterKind getKind() {
		return ObjectWriterKind.ARRAY_COMPONENT;
	}

	/**
	 * Gets the write handler for the array components.
	 * 
	 * @return The component write handler.
	 */
	public final RMIObjectWriteHandler getComponentWriter() {
		return componentWriter;
	}

	@Override
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((componentWriter == null) ? 0 : componentWriter.hashCode());
		return result;
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ArrayComponentRMIObjectWriteHandler other = (ArrayComponentRMIObjectWriteHandler) obj;
		if (componentWriter == null) {
			if (other.componentWriter != null)
				return false;
		} else if (!componentWriter.equals(other.componentWriter))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" + (componentWriter != null ? "componentWriter=" + componentWriter : "")
				+ "]";
	}

}
