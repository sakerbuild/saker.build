package testing.saker.build.tests.tasks.repo.testrepo;

import java.io.IOException;
import java.util.UUID;

import saker.build.runtime.repository.BuildRepository;
import saker.build.runtime.repository.RepositoryBuildEnvironment;
import saker.build.runtime.repository.RepositoryEnvironment;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.classloader.SingleClassLoaderResolver;

public class TestRepo implements SakerRepository {
	private final class BuildRepositoryImpl implements BuildRepository {
		private final RepositoryBuildEnvironment environment;

		private BuildRepositoryImpl(RepositoryBuildEnvironment environment) {
			this.environment = environment;
			environment.getClassLoaderResolverRegistry().register(CLASS_RESOLVER_ID, classLoaderResolver);
			System.out.println("TestRepo.BuildRepositoryImpl.BuildRepositoryImpl() " + environment.getIdentifier());
		}

		@Override
		public void close() throws IOException {
			System.out.println("TestRepo.BuildRepositoryImpl.close() " + environment.getIdentifier());
			environment.getClassLoaderResolverRegistry().unregister(CLASS_RESOLVER_ID, classLoaderResolver);
		}

		@Override
		public TaskFactory<?> lookupTask(TaskName identifier) throws TaskNotFoundException {
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
					//XXX is this even used?
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
}
