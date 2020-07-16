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
package testing.saker.build.tests.tasks.repo.testrepo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.UUID;

import saker.build.runtime.repository.BuildRepository;
import saker.build.runtime.repository.RepositoryBuildEnvironment;
import saker.build.runtime.repository.RepositoryEnvironment;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.rmi.io.writer.RemoteRMIObjectWriteHandler;
import saker.build.thirdparty.saker.util.classloader.SingleClassLoaderResolver;

public class TestRepo implements SakerRepository {
	private final class BuildRepositoryImpl implements BuildRepository {
		private final RepositoryBuildEnvironment environment;

		private SharedStub shared;

		public BuildRepositoryImpl(RepositoryBuildEnvironment environment) {
			this.environment = environment;
			environment.getClassLoaderResolverRegistry().register(CLASS_RESOLVER_ID, classLoaderResolver);
			String repoid = environment.getIdentifier();
			System.out.println("TestRepo.BuildRepositoryImpl.BuildRepositoryImpl() " + repoid);
			if (Boolean.parseBoolean(environment.getUserParameters().get("use-shared"))) {
				System.out.println("TestRepo.BuildRepositoryImpl.BuildRepositoryImpl() remote cluster: "
						+ environment.isRemoteCluster());
				if (!environment.isRemoteCluster()) {
					if (environment.getSharedObject("shared-key") != null) {
						throw new AssertionError();
					}
					environment.setSharedObject("shared-key", new TestSharedObject(repoid));
					if (environment.getSharedObject("shared-key") == null) {
						throw new AssertionError();
					}
					environment.setSharedObject("shared-stub", new SharedStubImpl());
				} else {
					try {
						environment.setSharedObject("test-set", "abc");
						throw new AssertionError("shouldnt be able to set value");
					} catch (UnsupportedOperationException e) {
						// expected
					}
				}

				System.out.println("TestRepo.BuildRepositoryImpl.BuildRepositoryImpl() test remote cluster: "
						+ environment.isRemoteCluster());
				Object so = environment.getSharedObject("shared-key");
				System.out.println("TestRepo.BuildRepositoryImpl.BuildRepositoryImpl() got shared-key");
				if (!new TestSharedObject(repoid).equals(so)) {
					throw new AssertionError(so);
				}
				System.out.println("TestRepo.BuildRepositoryImpl.BuildRepositoryImpl() " + so);
				this.shared = (SharedStub) environment.getSharedObject("shared-stub");
				if (RMIConnection.isRemoteObject(shared) != environment.isRemoteCluster()) {
					throw new AssertionError(
							"Invalid remote state: " + shared + " for " + environment.isRemoteCluster());
				}
				String stubid = shared.getId();
				if (!environment.getIdentifier().equals(stubid)) {
					throw new AssertionError("Not eq: " + stubid);
				}
				shared.duplicate(new TestSharedObject("abc"));
			}
		}

		@Override
		public Object detectChanges() {
			if (this.shared != null) {
				//requery the object to check that they still RMI transfer properly
				environment.getSharedObject("shared-key");
				this.shared = (SharedStub) this.environment.getSharedObject("shared-stub");

				//test that its callable
				shared.duplicate(new TestSharedObject("abc"));
			}
			return BuildRepository.super.detectChanges();
		}

		@Override
		public void close() throws IOException {
			System.out.println("TestRepo.BuildRepositoryImpl.close() " + environment.getIdentifier());
			environment.getClassLoaderResolverRegistry().unregister(CLASS_RESOLVER_ID, classLoaderResolver);
		}

		@Override
		public TaskFactory<?> lookupTask(TaskName identifier) throws TaskNotFoundException {
			if (environment.isRemoteCluster()) {
				//task lookup should happen on the coordinator
				throw new AssertionError();
			}
			if (!identifier.getTaskQualifiers().isEmpty()) {
				throw new TaskNotFoundException(identifier);
			}
			String userparamtaskname = environment.getUserParameters()
					.get(environment.getIdentifier() + ".userparamtask.name");
			if (userparamtaskname != null) {
				if (identifier.getName().equals(userparamtaskname)) {
					String userparamtaskval = environment.getUserParameters()
							.get(environment.getIdentifier() + ".userparamtask.value");
					return new TestRepoUserParamTask(userparamtaskval);
				}
			}
			switch (identifier.getName()) {
				case "test.task": {
					return new TestTask();
				}
				case "test.remote.task": {
					return new RemoteDispatchableTestTask();
				}
				case "test.file.output": {
					return new TestRepoFileOutputTaskFactory();
				}
				default: {
					break;
				}
			}
			throw new TaskNotFoundException(identifier);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "_" + VERSION;
		}

		@RMIWriter(RemoteRMIObjectWriteHandler.class)
		public class SharedStubImpl implements SharedStub {
			@Override
			public String getId() {
				return environment.getIdentifier();
			}

		}
	}

	public static final int VERSION;

	static {
		int v = 1;
		try {
			new AppendedRepositoryClass();
			v = 2;
		} catch (Throwable e) {
		}
		VERSION = v;
	}

	private static final String CLASS_RESOLVER_ID = "testrepo.tasks." + VERSION;

	private SingleClassLoaderResolver classLoaderResolver = new SingleClassLoaderResolver("testrepo.classes",
			TestRepo.class.getClassLoader());

	private volatile boolean closed = false;
	private final UUID REPO_UUID = UUID.randomUUID();

	public TestRepo(RepositoryEnvironment environment) {
		System.out.println("TestRepo.TestRepo() " + VERSION);
	}

	@Override
	public BuildRepository createBuildRepository(RepositoryBuildEnvironment environment) {
		checkClosed();
		return new BuildRepositoryImpl(environment);
	}

	@Override
	public void executeAction(String... arguments) throws Exception {
		checkClosed();
		System.setProperty(arguments[0], arguments[1]);
		System.setProperty("repo_uuid", REPO_UUID.toString());
	}

	@Override
	public void close() throws IOException {
		System.out.println("TestRepo.close() " + VERSION);
		closed = true;
	}

	private void checkClosed() {
		if (closed) {
			throw new IllegalStateException("closed");
		}
	}

	public static class TestSharedObject implements Externalizable {
		private static final long serialVersionUID = 1L;

		private String value;

		public TestSharedObject() {
		}

		public TestSharedObject(String value) {
			this.value = value;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(value);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((value == null) ? 0 : value.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TestSharedObject other = (TestSharedObject) obj;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "TestSharedObject[" + (value != null ? "value=" + value : "") + "]";
		}
	}

	public interface SharedStub {
		public String getId();

		public default TestSharedObject duplicate(TestSharedObject in) {
			return new TestSharedObject(in.value + in.value);
		}
	}

}
