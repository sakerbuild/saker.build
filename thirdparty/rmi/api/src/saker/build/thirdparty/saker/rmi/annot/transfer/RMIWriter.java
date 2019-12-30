package saker.build.thirdparty.saker.rmi.annot.transfer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import saker.build.thirdparty.saker.rmi.io.writer.DefaultRMIObjectWriteHandler;
import saker.build.thirdparty.saker.rmi.io.writer.RMIObjectWriteHandler;

/**
 * Annotation to specify the {@link RMIObjectWriteHandler} for a given object transfer point.
 * <p>
 * This annotation applies to method parameters and results. Annotating a type with this will result in the RMI runtime
 * using the specified {@link RMIObjectWriteHandler} to transfer the denoted object. E.g.:
 * 
 * <pre>
 * interface Stub {
 * 	// The result of the method call will be serialized over RMI
 * 	&#64;RMIWriter(SerializeRMIObjectWriteHandler.class)
 * 	Number parse(String s);
 * }
 * </pre>
 * 
 * Without any annotation, the given transfer point will use the default transfer mechanism. (see
 * {@link DefaultRMIObjectWriteHandler})
 * <p>
 * This annotation can be placed on types to configure class transfer properties automatically.
 * 
 * @see RMIObjectWriteHandler
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE })
public @interface RMIWriter {
	/**
	 * The specified write handler to use during transfer.
	 * <p>
	 * The write handler must have a default constructor.
	 * 
	 * @return The write handler to use.
	 */
	public Class<? extends RMIObjectWriteHandler> value();
}
