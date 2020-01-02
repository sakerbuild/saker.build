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
package saker.build.task;

import saker.build.task.TaskInvocationManager.TaskInvocationContext;

public class ForwardingTaskInvoker implements TaskInvoker {
	protected final TaskInvoker subject;

	public ForwardingTaskInvoker(TaskInvoker subject) {
		this.subject = subject;
	}

	@Override
	public void run(TaskInvocationContext context) throws InterruptedException {
		subject.run(context);
	}
}