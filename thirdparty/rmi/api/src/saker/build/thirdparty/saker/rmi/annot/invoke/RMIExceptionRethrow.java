package saker.build.thirdparty.saker.rmi.annot.invoke;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;

/**
 * Annotation to define an exception type to rethrow instead of an RMI error. (Subclass of {@link RMIRuntimeException})
 * <p>
 * If an RMI error occurs and a subclass of {@link RMIRuntimeException} would be thrown then annotating a method with
 * this can be used to replace the thrown exception. The specified type must have a constructor with a single
 * {@link Throwable} or {@link RMIRuntimeException} parameter.
 * <p>
 * If the exception type specified in {@link #value()} is a checked exception, then the annotated method must declare it
 * or any of its superclass in its <code>throws</code> clause.
 * <p>
 * This annotation can be used to signal exceptions to the caller using a different kind. E.g. If I/O operations are
 * done over RMI, then rethrowing RMI errors using {@link IOException} can be a good idea.
 * <p>
 * If a method is annotated with {@link RMIForbidden} then no calls will be made over the connection and the exception
 * specified using this annotation will be directly thrown.
 * 
 * @see RMIForbidden
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RMIExceptionRethrow {
	/**
	 * The type of the exception that should be rethrown.
	 * 
	 * @return The exception type.
	 */
	public Class<? extends Throwable> value();
}
