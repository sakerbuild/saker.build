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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.TreeMap;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.SakerLog;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;

@SakerTest
public class LogTaskContextScriptPositionTaskTest extends CollectingMetricEnvironmentTestCase {
	public static class LoggerTaskFactory implements TaskFactory<Void>, Task<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public LoggerTaskFactory() {
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			SakerLog.warning().taskScriptPosition(taskcontext).println("!message");
			return null;
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
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
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	private UnsyncByteArrayOutputStream out;

	@Override
	protected CollectingTestMetric createMetric() {
		CollectingTestMetric result = super.createMetric();
		TreeMap<TaskName, TaskFactory<?>> taskfactories = ObjectUtils.newTreeMap(result.getInjectedTaskFactories());
		taskfactories.put(TaskName.valueOf("logger.task"), new LoggerTaskFactory());
		result.setInjectedTaskFactories(taskfactories);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		out = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(out);
		runScriptTask("build");
		assertEquals(out.toString(), "[logger.task]saker.build:1:1-13: Warning: !message\n");

		out = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(out);
		runScriptTask("build");
		assertEquals(out.toString(), "[logger.task]saker.build:1:1-13: Warning: !message\n");

		files.putFile(PATH_WORKING_DIRECTORY.resolve("saker.build"),
				"\n" + files.getAllBytes(PATH_WORKING_DIRECTORY.resolve("saker.build")));
		out = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(out);
		runScriptTask("build");
		assertEquals(out.toString(), "[logger.task]saker.build:2:1-13: Warning: !message\n");
	}

}
