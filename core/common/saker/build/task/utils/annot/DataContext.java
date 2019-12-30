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
 * Field annotation for specifying a context of task parameters.
 * <p>
 * Fields annotated with {@link DataContext} will be visited during parameter initialization, and their fields annotated
 * as parameters will be initialized as well.
 * <p>
 * This annotation is recommended to be used when multiple tasks can have the same parameter signatures. Exporting the
 * common parameters into a data class and having it present in the tasks with the {@link DataContext} annotation can
 * greatly reduce code duplication.
 * 
 * @see SakerInput
 * @see TaskUtils#initParametersOfTask(TaskContext, Object, Map)
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DataContext {
	/**
	 * Gets if this field is auto-instantiateable.
	 * 
	 * @return <code>true</code>, if the corresponding field can be automatically instantiated during the assignment of
	 *             parameters.
	 */
	public boolean instantiate() default true;
}
