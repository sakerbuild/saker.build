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
import java.nio.charset.StandardCharsets;
import java.util.NavigableSet;

import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionUtilities;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.tasks.cluster.ClusterBuildTestCase;

public class TestRepoFileOutputTaskFactory implements TaskFactory<Void>, Externalizable {
	private static final class TaskImplementation implements ParameterizableTask<Void> {
		@SakerInput(required = true)
		private SakerPath Path;
		@SakerInput(required = true)
		private String Content;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			ActualRemoteTaskFactory tf = new ActualRemoteTaskFactory(Path, Content);
			taskcontext.getTaskUtilities().runTask(tf, tf);
			return null;
		}
	}

	private static final class ActualRemoteTaskFactory
			implements TaskFactory<Void>, Task<Void>, Externalizable, TaskIdentifier {
		private static final long serialVersionUID = 1L;

		private SakerPath Path;
		private String Content;

		/**
		 * For {@link Externalizable}.
		 */
		public ActualRemoteTaskFactory() {
		}

		public ActualRemoteTaskFactory(SakerPath path, String content) {
			Path = path;
			Content = content;
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			if (!taskcontext.getExecutionContext().getEnvironment().getUserParameters()
					.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM)
					.equals(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME)) {
				throw new AssertionError();
			}
			TaskExecutionUtilities taskutils = taskcontext.getTaskUtilities();
			SakerDirectory targetdir = taskutils.resolveDirectoryAtPathCreate(Path.getParent());
			SakerFile file = new ByteArraySakerFile(Path.getFileName(), Content.getBytes(StandardCharsets.UTF_8));
			file = taskutils.addFile(targetdir, file);
			if (!RMIConnection.isRemoteObject(file)) {
				throw new AssertionError("file is expected to be remote");
			}
			taskutils.reportOutputFileDependency(null, file);
			targetdir.synchronize();
			return null;
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(Path);
			out.writeObject(Content);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			Path = (SakerPath) in.readObject();
			Content = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((Content == null) ? 0 : Content.hashCode());
			result = prime * result + ((Path == null) ? 0 : Path.hashCode());
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
			ActualRemoteTaskFactory other = (ActualRemoteTaskFactory) obj;
			if (Content == null) {
				if (other.Content != null)
					return false;
			} else if (!Content.equals(other.Content))
				return false;
			if (Path == null) {
				if (other.Path != null)
					return false;
			} else if (!Path.equals(other.Path))
				return false;
			return true;
		}

	}

	private static final long serialVersionUID = 1L;

	public TestRepoFileOutputTaskFactory() {
	}

	@Override
	public Task<? extends Void> createTask(ExecutionContext executioncontext) {
		return new TaskImplementation();
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && this.getClass() == obj.getClass();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

}