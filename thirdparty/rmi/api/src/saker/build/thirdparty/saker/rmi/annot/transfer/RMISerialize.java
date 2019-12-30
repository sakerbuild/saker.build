package saker.build.thirdparty.saker.rmi.annot.transfer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import saker.build.thirdparty.saker.rmi.io.writer.SerializeRMIObjectWriteHandler;

/**
 * The objects on the annotated RMI transfer point will be serialized.
 * <p>
 * Same as annotating the transfer point using
 * <code>{@link RMIWriter &#64;RMIWriter}({@link SerializeRMIObjectWriteHandler SerializeRMIObjectWriteHandler.class})</code>.
 * 
 * @see SerializeRMIObjectWriteHandler
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.PARAMETER })
public @interface RMISerialize {
}
