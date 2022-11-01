package testing.saker.build.tests.tasks.cluster;

import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;

import saker.build.file.SakerDirectory;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskExecutionUtilities;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestClusterNameExecutionEnvironmentSelector;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

@SakerTest
public class ClusterRemoteContextObjectsTest extends ClusterBuildTestCase {
	public static class ClusterRunningTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		public ClusterRunningTaskFactory() {
		}

		@Override
		@SuppressWarnings("deprecation")
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		@SuppressWarnings("deprecation")
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new TestClusterNameExecutionEnvironmentSelector(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			ExecutionContext executioncontext = taskcontext.getExecutionContext();
			//check that we're running on the cluster
			assertEquals(
					executioncontext.getEnvironment().getUserParameters()
							.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM),
					ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);

			TaskExecutionUtilities taskutils = taskcontext.getTaskUtilities();

			//check for various objects that they're not remote object
			//this is relevant so using them doesn't have too big performance cost
			assertFalse(RMIConnection.isRemoteObject(taskcontext));
			assertFalse(RMIConnection.isRemoteObject(taskcontext.getFileDeltas()));
			assertFalse(RMIConnection.isRemoteObject(taskcontext.getNonFileDeltas()));
			assertFalse(RMIConnection.isRemoteObject(taskcontext.getTaskId()));

			assertFalse(RMIConnection.isRemoteObject(taskutils));
			assertIdentityEquals(taskcontext, taskutils.getTaskContext());

			assertFalse(RMIConnection.isRemoteObject(executioncontext));
			NavigableMap<String, ? extends SakerDirectory> rootdirs = executioncontext.getRootDirectories();
			assertFalse(RMIConnection.isRemoteObject(rootdirs));
			assertFalse(RMIConnection.isRemoteObject(executioncontext.getRootDirectoryNames()));
			assertFalse(RMIConnection.isRemoteObject(executioncontext.getScriptConfiguration()));
			assertFalse(RMIConnection.isRemoteObject(executioncontext.getRepositoryConfiguration()));
			assertFalse(RMIConnection.isRemoteObject(executioncontext.getPathConfiguration()));
			assertFalse(RMIConnection.isRemoteObject(executioncontext.getUserParameters()));

			assertEquals(executioncontext.getRootDirectoryNames(), rootdirs.navigableKeySet());

			assertFalse(RMIConnection.isRemoteObject(rootdirs.entrySet()));
			assertFalse(RMIConnection.isRemoteObject(rootdirs.keySet()));
			assertFalse(RMIConnection.isRemoteObject(rootdirs.values()));
			for (Entry<String, ? extends SakerDirectory> entry : rootdirs.entrySet()) {
				SakerDirectory dir = entry.getValue();
				System.out.println(entry.getKey() + " - " + dir.getName());
				System.out.println("    " + dir.getContentDescriptor());
				assertFalse(RMIConnection.isRemoteObject(dir.getContentDescriptor()));
			}

			return "abc";
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new ClusterRunningTaskFactory());
	}

}
