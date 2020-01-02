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
package saker.build.task.exception;

import saker.build.task.ParameterizableTask;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.annot.SakerInput;

/**
 * Exception signaling that a required parameter was not provided by the caller.
 * 
 * @see ParameterizableTask
 * @see SakerInput#required()
 */
public class MissingRequiredParameterException extends TaskParameterException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskParameterException#TaskParameterException(String, TaskIdentifier)
	 */
	public MissingRequiredParameterException(String message, TaskIdentifier taskIdentifier) {
		super(message, taskIdentifier);
	}

}
