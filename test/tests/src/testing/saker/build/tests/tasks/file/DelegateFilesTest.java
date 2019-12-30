package testing.saker.build.tests.tasks.file;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.DelegateSakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class DelegateFilesTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath INPUT_FILE_PATH = PATH_WORKING_DIRECTORY.resolve("file.txt");

	private static class CopierTaskFactory implements TaskFactory<Void>, Task<Void>, Externalizable {

		private static final long serialVersionUID = 1L;

		private int method;
		private SakerPath target;

		public CopierTaskFactory() {
		}

		public CopierTaskFactory(int method, SakerPath target) {
			this.method = method;
			this.target = target;
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			SakerFile src = taskcontext.getTaskUtilities().resolveAtPath(INPUT_FILE_PATH);
			SakerDirectory targetdir = taskcontext.getTaskUtilities().resolveDirectoryAtPathCreate(target.getParent());
			DelegateSakerFile delegatefile = new DelegateSakerFile(target.getFileName(), src);
			targetdir.add(delegatefile);
			switch (method) {
				case 0: {
					break;
				}
				case 1: {
					delegatefile.writeTo(StreamUtils.nullByteSink());
					break;
				}
				case 2: {
					delegatefile.openInputStream().close();
					break;
				}
				case 3: {
					delegatefile.synchronize();
					break;
				}
				case 4: {
					assertEquals(delegatefile.getBytes().toString(), src.getBytes().toString());
					break;
				}
				case 5: {
					assertEquals(delegatefile.getContent(), src.getContent());
					break;
				}
				default: {
					throw new UnsupportedOperationException(method + "");
				}
			}
			return null;
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeInt(method);
			out.writeObject(target);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			method = in.readInt();
			target = (SakerPath) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + method;
			result = prime * result + ((target == null) ? 0 : target.hashCode());
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
			CopierTaskFactory other = (CopierTaskFactory) obj;
			if (method != other.method)
				return false;
			if (target == null) {
				if (other.target != null)
					return false;
			} else if (!target.equals(other.target))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "CopierTaskFactory[method=" + method + ", " + (target != null ? "target=" + target : "") + "]";
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		files.putFile(INPUT_FILE_PATH, "content");

		runTask("m0", new CopierTaskFactory(0, PATH_BUILD_DIRECTORY.resolve("out0.txt")));
		assertException(IOException.class, () -> files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("out0.txt")));

		runTask("m1", new CopierTaskFactory(1, PATH_BUILD_DIRECTORY.resolve("out1.txt")));
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("out1.txt")).toString(), "content");
		runTask("m2", new CopierTaskFactory(2, PATH_BUILD_DIRECTORY.resolve("out2.txt")));
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("out2.txt")).toString(), "content");
		runTask("m3", new CopierTaskFactory(3, PATH_BUILD_DIRECTORY.resolve("out3.txt")));
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("out3.txt")).toString(), "content");
		runTask("m4", new CopierTaskFactory(4, PATH_BUILD_DIRECTORY.resolve("out4.txt")));
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("out4.txt")).toString(), "content");
		runTask("m5", new CopierTaskFactory(5, PATH_BUILD_DIRECTORY.resolve("out5.txt")));
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("out5.txt")).toString(), "content");
	}

}
