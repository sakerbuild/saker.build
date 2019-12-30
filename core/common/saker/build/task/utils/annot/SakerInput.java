package saker.build.task.utils.annot;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import saker.build.task.TaskContext;
import saker.build.task.utils.TaskUtils;

/**
 * Field annotation for marking the field as an input parameter for the enclosing task.
 * <p>
 * See {@link TaskUtils#initParametersOfTask(TaskContext, Object, Map)} for the algorithm of converting the parameters
 * to the appropriate types.
 * 
 * @see DataContext
 * @see TaskUtils#initParametersOfTask(TaskContext, Object, Map)
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SakerInput {

	/**
	 * The name(s) of the parameter to use.
	 * <p>
	 * Multiple names can be specified for the field.
	 * <p>
	 * By default, the name of the corresponding field will be used.
	 * 
	 * @return The names(s) of the parameter.
	 */
	String[] value() default {};

	/**
	 * Gets if the parameter is required.
	 * <p>
	 * If a parameter is required, but not present, an exception will be thrown.
	 * <p>
	 * <code>false</code> by default.
	 * 
	 * @return <code>true</code> if the parameter is required.
	 */
	boolean required() default false;

}
