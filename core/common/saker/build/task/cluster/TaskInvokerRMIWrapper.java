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
package saker.build.task.cluster;

import java.io.IOException;
import java.util.UUID;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.TaskInvocationManager.TaskInvocationContext;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

public class TaskInvokerRMIWrapper implements RMIWrapper, TaskInvoker {
	private TaskInvoker invoker;
	private UUID environmentIdentifier;

	public TaskInvokerRMIWrapper() {
	}

	public TaskInvokerRMIWrapper(TaskInvoker invoker) {
		this.invoker = invoker;
	}

	@Override
	public void writeWrapped(RMIObjectOutput out) throws IOException {
		out.writeRemoteObject(invoker);
		out.writeSerializedObject(invoker.getEnvironmentIdentifier());
	}

	@Override
	public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
		invoker = (TaskInvoker) in.readObject();
		environmentIdentifier = (UUID) in.readObject();
	}

	@Override
	public Object resolveWrapped() {
		return this;
	}

	@Override
	public Object getWrappedObject() {
		return invoker;
	}

	@Override
	public void run(ExecutionContext executioncontext, TaskInvokerInformation invokerinformation,
			TaskInvocationContext context) throws Exception {
		invoker.run(executioncontext, invokerinformation, context);
	}

	@Override
	public UUID getEnvironmentIdentifier() {
		return environmentIdentifier;
	}

}
