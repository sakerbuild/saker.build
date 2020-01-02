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
package saker.build.internal.scripting.language.task;

import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;

public abstract class SelfSakerTaskFactory extends SakerTaskFactory implements Task<SakerTaskResult> {
	private static final long serialVersionUID = 1L;

	@Override
	public final Task<? extends SakerTaskResult> createTask(ExecutionContext executioncontext) {
		return this;
	}
}
