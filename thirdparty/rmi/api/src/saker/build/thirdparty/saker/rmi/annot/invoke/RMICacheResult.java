package saker.build.thirdparty.saker.rmi.annot.invoke;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Remote call result of annotated methods will be cached and further calls will be not issued to the remote endpoint.
 * <p>
 * Methods can be annotated with this class to tell the RMI runtime to cache the result of the calls to it. It is
 * generally useful if the result of a method call has no side effects and will be the same if called multiple times.
 * <p>
 * Only the first call will be issued over the connection and further calls will receive the result of the first call.
 * If the first one is still pending, the further calls will wait for it to finish.
 * <p>
 * The annotated methods do not require to have no parameters, but note that the result is not cached based on the
 * parameters. <br>
 * If a method is called with a parameter value of A and later with the value B, the second call with value B will
 * receive the result of the call with A.
 * <p>
 * The result is only cached if the call succeeds and throws no exceptions. If an annotated method throws an exception,
 * later calls to the method will be issued to the remote endpoint.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RMICacheResult {
}
