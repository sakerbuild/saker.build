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
package testing.saker.build.tests.tasks;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;

public abstract class SelfStatelessTaskFactory<R> extends StatelessTaskFactory<R> implements Task<R> {
	private static final long serialVersionUID = 1L;

	@Override
	public final Task<? extends R> createTask(ExecutionContext executioncontext) {
		return this;
	}

}
