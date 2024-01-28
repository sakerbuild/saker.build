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
package testing.saker.build.tests.tasks.script;

import java.util.TreeMap;

import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class QualifierTaskInvocationTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected CollectingTestMetric createMetric() {
		CollectingTestMetric result = super.createMetric();
		TreeMap<TaskName, TaskFactory<?>> itf = ObjectUtils.newTreeMap(result.getInjectedTaskFactories());
		itf.put(TaskName.valueOf("my.task-v1"), new StringTaskFactory("v1"));
		itf.put(TaskName.valueOf("my.task-inline"), new StringTaskFactory("inline"));
		itf.put(TaskName.valueOf("my.task-q1-q2"), new StringTaskFactory("multi"));
		itf.put(TaskName.valueOf("my.task-assign"), new StringTaskFactory("assign"));
		result.setInjectedTaskFactories(itf);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("v1"), "v1");
		assertEquals(res.getTargetTaskResult("inline"), "inline");
		assertEquals(res.getTargetTaskResult("recinline"), "inline");
		assertEquals(res.getTargetTaskResult("multi"), "multi");
		assertEquals(res.getTargetTaskResult("assigninparam"), "assign");

		runScriptTask("build");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), setOf());
	}

}
