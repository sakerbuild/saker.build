package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.SakerFileBase;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.classloader.FilteringClassLoader;
import saker.build.thirdparty.saker.util.classloader.SingleClassLoaderResolver;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.TestUtils;
import testing.saker.build.tests.tasks.factories.CustomStringContentDescriptor;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;

@SakerTest
public class CustomContentFileModifyTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final ClassLoader CUSTOMLOADER = TestUtils.createClassLoaderForClasses(
			new FilteringClassLoader(ContentDescriptor.class.getClassLoader(),
					ImmutableUtils.singletonNavigableSet(ContentDescriptor.class.getName())),
			CustomStringContentDescriptor.class);

	public static class CustomFileCreatorTaskFactory implements TaskFactory<Void>, Externalizable {

		private static final long serialVersionUID = 1L;

		private SakerPath inputFilePath;
		private String outputFileName;

		public CustomFileCreatorTaskFactory() {
		}

		public CustomFileCreatorTaskFactory(SakerPath inputFilePath, String outputFileName) {
			this.inputFilePath = inputFilePath;
			this.outputFileName = outputFileName;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(inputFilePath);
			out.writeObject(outputFileName);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			inputFilePath = (SakerPath) in.readObject();
			outputFileName = (String) in.readObject();
		}

		@Override
		public Task<Void> createTask(ExecutionContext excontext) {
			return new Task<Void>() {
				@Override
				public Void run(TaskContext context) {
					try {
						SakerFile inputfile = context.getTaskUtilities().resolveAtPath(inputFilePath);
						String strcontent = inputfile.getContent();
						SakerFile addedfile = new SakerFileBase(outputFileName) {
							@Override
							public ContentDescriptor getContentDescriptor() {
								try {
									return (ContentDescriptor) CUSTOMLOADER
											.loadClass(CustomStringContentDescriptor.class.getName())
											.getConstructor(String.class).newInstance(strcontent);
								} catch (Exception e) {
									throw new RuntimeException(e);
								}
							}

							@Override
							public void writeToStreamImpl(OutputStream os) throws IOException {
								os.write(strcontent.getBytes(StandardCharsets.UTF_8));
							}
						};

						SakerDirectory builddir = context.getTaskBuildDirectory();
						builddir.add(addedfile);
						addedfile.synchronize();
						context.getTaskUtilities().startTaskFuture(strTaskId("sub"),
								new FileStringContentTaskFactory(addedfile.getSakerPath()));
						context.reportInputFileDependency(null, inputFilePath, inputfile.getContentDescriptor());
						context.reportOutputFileDependency(null, addedfile.getSakerPath(),
								addedfile.getContentDescriptor());
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
					return null;
				}
			};
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((inputFilePath == null) ? 0 : inputFilePath.hashCode());
			result = prime * result + ((outputFileName == null) ? 0 : outputFileName.hashCode());
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
			CustomFileCreatorTaskFactory other = (CustomFileCreatorTaskFactory) obj;
			if (inputFilePath == null) {
				if (other.inputFilePath != null)
					return false;
			} else if (!inputFilePath.equals(other.inputFilePath))
				return false;
			if (outputFileName == null) {
				if (other.outputFileName != null)
					return false;
			} else if (!outputFileName.equals(other.outputFileName))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "CustomFileCreatorTaskFactory ["
					+ (inputFilePath != null ? "inputFilePath=" + inputFilePath + ", " : "")
					+ (outputFileName != null ? "outputFileName=" + outputFileName : "") + "]";
		}
	}

	private final SingleClassLoaderResolver clresolver = new SingleClassLoaderResolver("contentedesc.cl", CUSTOMLOADER);

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		//never use test project, as we need to reread the database for each run
		//use a private environment
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations()).setUseProject(false)
				.setEnvironmentStorageDirectory(null).build();
	}

	@Override
	protected void runTestImpl() throws Throwable {
		environment.getClassLoaderResolverRegistry().register("testusercl", clresolver);

		SakerPath resultfilepath = PATH_BUILD_DIRECTORY.resolve("output.txt");
		SakerPath inputpath = PATH_WORKING_DIRECTORY.resolve("input.txt");
		files.putFile(inputpath, "input");

		CustomFileCreatorTaskFactory task = new CustomFileCreatorTaskFactory(inputpath, "output.txt");

		runTask("task", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("task", "sub"));
		assertEquals(files.getAllBytes(resultfilepath).toString(), "input");

		runTask("task", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());
		assertEquals(files.getAllBytes(resultfilepath).toString(), "input");

		files.putFile(inputpath, "modified");
		runTask("task", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("task", "sub"));
		assertEquals(files.getAllBytes(resultfilepath).toString(), "modified");

		//only the task is run, as its output file was modified
		//the output file is regenerated, and sub doesn't require to be run, as its input is the same
		files.putFile(resultfilepath, "resultmodified");
		runTask("task", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("task"));
		assertEquals(files.getAllBytes(resultfilepath).toString(), "modified");

		//clear the class resolver, so the content descriptor is not found
		//both tasks are rerun, as both have the not found content descriptor as their dependency somewhere
		environment.getClassLoaderResolverRegistry().unregister("testusercl", clresolver);
		runTask("task", task);
		assertEquals(files.getAllBytes(resultfilepath).toString(), "modified");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("task", "sub"));
	}

}
