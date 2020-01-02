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
