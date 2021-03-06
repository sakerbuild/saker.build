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

import saker.build.file.provider.RootFileProviderKey;
import saker.build.runtime.params.DatabaseConfiguration;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

@RMIWrap(TaskInvokerInformation.InvokerInvormationRMIWrapper.class)
public class TaskInvokerInformation {
	private RootFileProviderKey coordinatorProviderKey;
	private DatabaseConfiguration databaseConfiguration;

	public TaskInvokerInformation(RootFileProviderKey coordinatorProviderKey,
			DatabaseConfiguration databaseConfiguration) {
		this.coordinatorProviderKey = coordinatorProviderKey;
		this.databaseConfiguration = databaseConfiguration;
	}

	/**
	 * Gets the file provider key of the build coordinator machine.
	 * <p>
	 * That is the computer that runs the build.
	 * 
	 * @return The root file provider key.
	 */
	public RootFileProviderKey getCoordinatorProviderKey() {
		return coordinatorProviderKey;
	}

	/**
	 * Gets the database configuration of the build.
	 * 
	 * @return The database configuration.
	 */
	public DatabaseConfiguration getDatabaseConfiguration() {
		return databaseConfiguration;
	}

	protected static class InvokerInvormationRMIWrapper implements RMIWrapper {
		private TaskInvokerInformation info;

		public InvokerInvormationRMIWrapper() {
		}

		public InvokerInvormationRMIWrapper(TaskInvokerInformation info) {
			this.info = info;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeSerializedObject(info.coordinatorProviderKey);
			out.writeObject(info.databaseConfiguration);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			RootFileProviderKey providerkey = (RootFileProviderKey) in.readObject();
			DatabaseConfiguration dbconfig = (DatabaseConfiguration) in.readObject();
			info = new TaskInvokerInformation(providerkey, dbconfig);
		}

		@Override
		public Object resolveWrapped() {
			return info;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

	}
}
