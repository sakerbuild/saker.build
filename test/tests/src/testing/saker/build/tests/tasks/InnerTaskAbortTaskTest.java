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
package testing.saker.build.tests.tasks;

import java.io.Externalizable;

import saker.build.task.InnerTaskResultHolder;
import saker.build.task.InnerTaskResults;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskInvocationConfiguration;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class InnerTaskAbortTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class InnerTaskStarter extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public InnerTaskStarter() {
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			InnerTaskResults<String> results = taskcontext.startInnerTask(getTaskFactory(), null);
			InnerTaskResultHolder<String> resholder = results.getNext();
			assertNonNull(resholder.getExceptionIfAny());
			assertEquals(resholder.getExceptionIfAny().getMessage(), "abort");
			assertInstanceOf(resholder.getExceptionIfAny(), UnsupportedOperationException.class);
			assertEquals(resholder.getResult(), "123");
			return null;
		}

		protected TaskFactory<String> getTaskFactory() {
			return new AbortingTaskFactory();
		}
	}

	public static class ShortInnerTaskStarter extends InnerTaskStarter {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public ShortInnerTaskStarter() {
		}

		@Override
		protected TaskFactory<String> getTaskFactory() {
			return new ShortAbortingTaskFactory();
		}
	}

	public static class AbortingTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.abortExecution(new UnsupportedOperationException("abort"));
			return "123";
		}
	}

	public static class ShortAbortingTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.abortExecution(new UnsupportedOperationException("abort"));
			return "123";
		}

		@Override
		public TaskInvocationConfiguration getInvocationConfiguration() {
			return TaskInvocationConfiguration.builder().setShort(true).build();
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new InnerTaskStarter());

		runTask("short", new ShortInnerTaskStarter());
	}

}
