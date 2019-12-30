package saker.build.thirdparty.saker.rmi.io.writer;

import java.io.Externalizable;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;

/**
 * Writes the object using the default mechanism.
 * <p>
 * This write strategy is the default for transferring objects through an RMI connection.
 * <p>
 * After examining the object for non customizable aspects of the transferring (see {@link RMIObjectWriteHandler}
 * documentation), the following rules apply to transferring objects:
 * <p>
 * In the following, the target type is the receiver type of the transferred object during transfer. <br>
 * E.g. if the method is <code>void method(Number n)</code> and the parameter is an instance of {@link Integer}, then
 * the target type is {@link Number}, and the object type is {@link Integer}.
 * <ol>
 * <li>If the target type is an array, then the object is transferred as an array, and its elements are transferred with
 * a target type of the component type of the target type. <br>
 * E.g. <code>void method(Number[] n)</code> with parameter {@link Integer}<code>[]</code>, the elements will be
 * transferred with target type of {@link Number}.</li>
 * <li>If the object is an array, then it will be transferred as an array, and its elements are transferred with a
 * target type of the component type of the array type. <br>
 * E.g. <code>void method(Object n)</code> with parameter {@link Integer}<code>[]</code>, the elements will be
 * transferred with target type of {@link Integer}.</li>
 * <li>If the object is an {@link Enum} instance, then it will be transferred by name. See
 * {@link EnumRMIObjectWriteHandler}.</li>
 * <li>If the object is a {@link ClassLoader} instance, then it will be transferred by name using the specified
 * classloader resolver settings.</li>
 * <li>If the object is a {@link Class}, {@link Method}, {@link Constructor} or {@link Field} instance, then it will be
 * transferred by their enclosing reflectional information, and will be looked up on the other endpoint.</li>
 * <li>If the target type is an interface, or <code>RemoteProxyObject</code> then it will be written as a remote object.
 * See {@link RemoteRMIObjectWriteHandler}. <br>
 * E.g. <code>void method(Runnable run)</code> with parameter of a lambda
 * <code>() -&gt; System.out.println("hello")</code>, will be transferred as a remote object. Any calls on the received
 * {@link Runnable} will result in printing <code>"hello"</code> to the stdout on the client side.</li>
 * <li>Regardless of target type, the object will be tried to be written as an {@link Externalizable} object. This will
 * only happen if the given object is an instance of {@link Externalizable}. <br>
 * The object is not written using {@link ObjectOutputStream}, but the {@link RMIObjectOutput} interface will be used to
 * call {@link Externalizable#writeExternal}. Any {@link ObjectOutput#writeObject} calls will use the default object
 * writing strategy, and {@link Object} target type.</li>
 * <li>As a fallback mechanism, the RMI runtime will write the given object as a remote to the other side. This means
 * that if the actual target type can not accept an interface, then the RMI request will likely fail. This can be
 * considered an undefined behavior and the user should attempt to properly choose the interfaces and instances for RMI
 * use. <br>
 * This fallback mechanism can be used as well to transfer {@link Externalizable} instances and some remote object with
 * it.</li>
 * </ol>
 */
public final class DefaultRMIObjectWriteHandler implements RMIObjectWriteHandler {
	/**
	 * Singleton instance of this class.
	 */
	public static final RMIObjectWriteHandler INSTANCE = new DefaultRMIObjectWriteHandler();

	/**
	 * Creates a new instance.
	 * <p>
	 * Use {@link RMIObjectWriteHandler#defaultWriter()} instead.
	 */
	public DefaultRMIObjectWriteHandler() {
	}

	@Override
	public final ObjectWriterKind getKind() {
		return ObjectWriterKind.DEFAULT;
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