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
package testing.saker.build.tests.tasks.cluster;

import java.util.Set;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.build.flag.TestFlag;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;

public abstract class ClusterBuildTestCase extends CollectingMetricEnvironmentTestCase {
	public static final String DEFAULT_CLUSTER_NAME = "cluster";

	@Override
	public void executeRunning() throws Exception {
		CollectingTestMetric tm = new CollectingTestMetric();
		TestFlag.set(tm);
		assertEmpty(tm.getLoadedClassPaths());
		super.executeRunning();
		assertEmpty(tm.getLoadedClassPaths());
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setClusterNames(ImmutableUtils.singletonSet(DEFAULT_CLUSTER_NAME)).build();
	}

}
