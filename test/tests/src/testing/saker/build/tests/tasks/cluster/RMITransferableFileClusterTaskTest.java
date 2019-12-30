package testing.saker.build.tests.tasks.cluster;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.NavigableSet;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.SakerFileBase;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.SerializableContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;

@SakerTest
public class RMITransferableFileClusterTaskTest extends ClusterBuildTestCase {
	private static final String OUTPUT_FILE_NAME = "out.txt";

	public static class RMITransferableSakerFile extends SakerFileBase {
		private String contents;

		public RMITransferableSakerFile(String name, String contents)
				throws NullPointerException, InvalidPathFormatException {
			super(name);
			this.contents = contents;
		}

		@Override
		public ContentDescriptor getContentDescriptor() {
			return new SerializableContentDescriptor(contents);
		}

		@Override
		public void writeToStreamImpl(OutputStream os) throws IOException, NullPointerException {
			os.write(contents.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public Class<? extends RMIWrapper> getRemoteExecutionRMIWrapper() {
			return TheFileRMIWrapper.class;
		}
	}

	public static class TheFileRMIWrapper implements RMIWrapper {
		private RMITransferableSakerFile file;

		public TheFileRMIWrapper() {
		}

		public TheFileRMIWrapper(RMITransferableSakerFile file) {
			this.file = file;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeUTF(file.getName());
			out.writeUTF(file.contents);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			String name = in.readUTF();
			String contents = in.readUTF();
			file = new RMITransferableSakerFile(name, contents);
		}

		@Override
		public Object resolveWrapped() {
			return file;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

	}

	public static class FileOutputtingTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String contents;

		/**
		 * For {@link Externalizable}.
		 */
		public FileOutputtingTaskFactory() {
		}

		public FileOutputtingTaskFactory(String contents) {
			this.contents = contents;
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new TestClusterNameExecutionEnvironmentSelector(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			assertEquals(
					taskcontext.getExecutionContext().getEnvironment().getUserParameters()
							.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM),
					ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);

			SakerDirectory bd = taskcontext.getTaskBuildDirectory();
			RMITransferableSakerFile addedfile = new RMITransferableSakerFile(OUTPUT_FILE_NAME, contents);
			SakerFile thefile = taskcontext.getTaskUtilities().addFile(bd, addedfile);
			taskcontext.getTaskUtilities().reportOutputFileDependency(null, thefile);
			assertNotIdentityEquals(thefile, addedfile);
			thefile.synchronize();
			return contents;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(contents);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			contents = in.readUTF();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((contents == null) ? 0 : contents.hashCode());
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
			FileOutputtingTaskFactory other = (FileOutputtingTaskFactory) obj;
			if (contents == null) {
				if (other.contents != null)
					return false;
			} else if (!contents.equals(other.contents))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "FileOutputtingTaskFactory[" + (contents != null ? "contents=" + contents : "") + "]";
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath filepath = PATH_BUILD_DIRECTORY.resolve(OUTPUT_FILE_NAME);

		runTask("main", new FileOutputtingTaskFactory("contents"));
		assertEquals(files.getAllBytes(filepath).toString(), "contents");

		runTask("main", new FileOutputtingTaskFactory("contents"));
		assertEmpty(getMetric().getRunTaskIdResults());

		files.delete(filepath);
		runTask("main", new FileOutputtingTaskFactory("contents"));
		assertNotEmpty(getMetric().getRunTaskIdResults());

		runTask("main", new FileOutputtingTaskFactory("contentsmod"));
		assertNotEmpty(getMetric().getRunTaskIdResults());
		assertEquals(files.getAllBytes(filepath).toString(), "contentsmod");
	}

}
