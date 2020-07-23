package testing.saker.build.tests.tasks.factories;

import java.util.NavigableSet;
import java.util.TreeSet;

import saker.build.thirdparty.saker.util.ObjectUtils;

public class LocalPreferringClusterNameReturningTaskFactory extends ClusterNameReturningTaskFactory {
	private static final long serialVersionUID = 1L;

	@Override
	public NavigableSet<String> getCapabilities() {
		TreeSet<String> result = ObjectUtils.newTreeSet(super.getCapabilities());
		result.add(CAPABILITY_PREFERS_LOCAL_ENVIRONMENT);
		return result;
	}
}