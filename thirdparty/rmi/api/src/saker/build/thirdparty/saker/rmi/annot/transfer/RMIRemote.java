package saker.build.thirdparty.saker.rmi.annot.transfer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import saker.build.thirdparty.saker.rmi.io.writer.RemoteRMIObjectWriteHandler;

/**
 * The objects on the annotated RMI transfer point will be transferred as remote proxies.
 * <p>
 * Same as annotating the transfer point using <code>@RMIWriter(RemoteRMIObjectWriteHandler.class)</code>.
 * 
 * @see RemoteRMIObjectWriteHandler
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.PARAMETER })
public @interface RMIRemote {
}
