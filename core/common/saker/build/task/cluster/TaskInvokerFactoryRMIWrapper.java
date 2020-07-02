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
import saker.build.task.TaskInvoker;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

public class TaskInvokerFactoryRMIWrapper implements RMIWrapper, TaskInvokerFactory {
	private TaskInvokerFactory factory;
	private UUID environmentIdentifier;

	public TaskInvokerFactoryRMIWrapper() {
	}

	public TaskInvokerFactoryRMIWrapper(TaskInvokerFactory factory) {
		this.factory = factory;
	}

	@Override
	public void writeWrapped(RMIObjectOutput out) throws IOException {
		out.writeRemoteObject(factory);
		out.writeSerializedObject(factory.getEnvironmentIdentifier());
	}

	@Override
	public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
		factory = (TaskInvokerFactory) in.readObject();
		environmentIdentifier = (UUID) in.readObject();
	}

	@Override
	public Object resolveWrapped() {
		return this;
	}

	@Override
	public Object getWrappedObject() {
		return factory;
	}

	@Override
	public TaskInvoker createTaskInvoker(ExecutionContext executioncontext, TaskInvokerInformation invokerinformation)
			throws IOException, NullPointerException {
		return factory.createTaskInvoker(executioncontext, invokerinformation);
	}

	@Override
	public UUID getEnvironmentIdentifier() {
		return environmentIdentifier;
	}

}
