package saker.build.thirdparty.saker.util.rmi.wrap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * {@link RMIWrapper} implementation that writes {@link Iterable} or {@link Iterator} instances and reads them as
 * {@link HashSet} instances.
 * <p>
 * The elements of the transferred iterable is written as serialized objects.
 * 
 * @see RMIObjectOutput#writeSerializedObject(Object)
 */
public class RMIHashSetSerializableElementWrapper implements RMIWrapper {
	private Iterable<?> iterable;

	/**
	 * Creates a new instance.
	 * <p>
	 * Users shouldn't instantiate this class manually, but leave that to the RMI runtime.
	 */
	public RMIHashSetSerializableElementWrapper() {
	}

	/**
	 * Creates a new instance for an iterable.
	 * <p>
	 * Users shouldn't instantiate this class manually, but leave that to the RMI runtime.
	 * 
	 * @param iterable
	 *            The iterable.
	 */
	public RMIHashSetSerializableElementWrapper(Iterable<?> iterable) {
		this.iterable = iterable;
	}

	/**
	 * Constructs a new instance for an iterator.
	 * <p>
	 * The remaining elements in the iterator are added to a new {@link ArrayList}, which will be transferred.
	 * <p>
	 * Users shouldn't instantiate this class manually, but leave that to the RMI runtime.
	 * 
	 * @param iterator
	 *            The iterator.
	 */
	public RMIHashSetSerializableElementWrapper(Iterator<?> iterator) {
		this.iterable = ObjectUtils.newArrayList(iterator);
	}

	@Override
	public Object getWrappedObject() {
		return iterable;
	}

	@Override
	public Object resolveWrapped() {
		return iterable;
	}

	@Override
	public void writeWrapped(RMIObjectOutput out) throws IOException {
		if (iterable == null) {
			out.writeObject(CommonSentinel.NULL_INPUT);
			return;
		}
		for (Object o : iterable) {
			out.writeSerializedObject(o);
		}
		out.writeObject(CommonSentinel.END_OF_OBJECTS);
	}

	@Override
	public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
		Object obj = in.readObject();
		if (obj == CommonSentinel.NULL_INPUT) {
			iterable = null;
			return;
		}
		Set<Object> l = new HashSet<>();
		do {
			if (obj == CommonSentinel.END_OF_OBJECTS) {
				break;
			}
			l.add(obj);
			obj = in.readObject();
		} while (true);
		this.iterable = l;
	}

}
