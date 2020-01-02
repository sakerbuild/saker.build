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
package saker.build.internal.scripting.language.exc;

import saker.build.task.exception.TaskExecutionException;
import saker.build.task.identifier.TaskIdentifier;

public class SakerScriptEvaluationException extends TaskExecutionException {
	private static final long serialVersionUID = 1L;

	public SakerScriptEvaluationException(String message, TaskIdentifier taskIdentifier) {
		super(message, taskIdentifier);
	}

	protected SakerScriptEvaluationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace, TaskIdentifier taskIdentifier) {
		super(message, cause, enableSuppression, writableStackTrace, taskIdentifier);
	}

	public SakerScriptEvaluationException(String message, Throwable cause, TaskIdentifier taskIdentifier) {
		super(message, cause, taskIdentifier);
	}

	public SakerScriptEvaluationException(TaskIdentifier taskIdentifier) {
		super(taskIdentifier);
	}

	public SakerScriptEvaluationException(Throwable cause, TaskIdentifier taskIdentifier) {
		super(cause, taskIdentifier);
	}

}
