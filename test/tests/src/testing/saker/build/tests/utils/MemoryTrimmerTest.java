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
package testing.saker.build.tests.utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskResultCollection;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.cache.MemoryTrimmer;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;

@SakerTest
public class MemoryTrimmerTest extends CollectingMetricEnvironmentTestCase {
	private static class TrimmableResult {
		private String value;

		public TrimmableResult(String value) {
			this.value = value;
		}
	}

	private static class TrimmableTaskFactory
			implements TaskFactory<TrimmableResult>, Task<TrimmableResult>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public TrimmableResult run(TaskContext taskcontext) throws Exception {
			TrimmableResult result = new TrimmableResult("untrimmed");
			MemoryTrimmer.add(result, v -> {
				v.value = "trimmed";
			});
			return result;
		}

		@Override
		public Task<? extends TrimmableResult> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		TrimmableTaskFactory main = new TrimmableTaskFactory();

		TaskResultCollection res = runTask("main", main);
		project.waitExecutionFinalization();

		TrimmableResult trimres = (TrimmableResult) res.getTaskResult(strTaskId("main"));
		assertEquals(trimres.value, "trimmed");
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		//always use project
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations()).setUseProject(true).build();
	}

}
