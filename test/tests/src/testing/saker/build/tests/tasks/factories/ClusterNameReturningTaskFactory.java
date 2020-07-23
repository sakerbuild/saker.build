package testing.saker.build.tests.tasks.factories;

import java.util.NavigableSet;

import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

public class ClusterNameReturningTaskFactory extends SelfStatelessTaskFactory<String> {
	private static final long serialVersionUID = 1L;

	@Override
	public NavigableSet<String> getCapabilities() {
		return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
	}

	@Override
	public String run(TaskContext taskcontext) throws Exception {
		String result = taskcontext.getExecutionContext().getEnvironment().getUserParameters()
				.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM);
		System.out.println("ClusterNameReturningTaskFactory.run() " + result);
		return result;
	}

}