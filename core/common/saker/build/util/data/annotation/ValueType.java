package saker.build.util.data.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for value based types.
 * <p>
 * Types marked with this annotation have the following properties:
 * <ul>
 * <li>They are immutable data holders, and their identity are not used.</li>
 * <li>The type properly implements the {@link Object#hashCode()} and {@link Object#equals(Object)} contract.</li>
 * <li>Instances of the type may be deduplicated if they equal. This means that if two instances equal, then they can be
 * replaced with a single instance without affecting the code in any way.</li>
 * </ul>
 * This annotation may be used by the build content serialization code to deduplicate objects, to reduce the size of the
 * written file.
 * 
 * @since saker.build 0.8.19
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ValueType {
}
