package saker.build.thirdparty.saker.rmi.io.writer;

import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;

/**
 * Writes the object based the selection of the subclass implementation.
 * <p>
 * Subclasses which extend this class can choose what kind of transferring strategy should be used to transfer the given
 * object. <br>
 * The strategy can be choosen based on the object instance and the target type.
 * <p>
 * In order to use this class with {@link RMIWriter}, subclass it, provide a no-arg default constructor, and use it as
 * the value for the annotation.
 */
public abstract class SelectorRMIObjectWriteHandler implements RMIObjectWriteHandler {

	/**
	 * Creates a new instance.
	 */
	protected SelectorRMIObjectWriteHandler() {
	}

	@Override
	public final ObjectWriterKind getKind() {
		return ObjectWriterKind.SELECTOR;
	}

	/**
	 * Selects the write handler to use for the given object and target type.
	 * 
	 * @param obj
	 *            The object to transfer.
	 * @param targettype
	 *            The target type of the object transfer.
	 * @return The object write handler to use to transfer the object.
	 */
	public abstract RMIObjectWriteHandler selectWriteHandler(Object obj, Class<?> targettype);
}
