/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
