package testing.saker.build.tests.tasks.file;

import java.io.IOException;

import saker.build.file.DelegateSakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

@SakerTest
public class NonMoveableFileTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath INPUTFILE_PATH = PATH_BUILD_DIRECTORY.resolve("in/in.txt");

	private static class TryMovingTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			SakerFile infile = taskcontext.getTaskUtilities().resolveAtPath(INPUTFILE_PATH);
			SakerDirectory builddir = taskcontext.getTaskBuildDirectory();
			SakerDirectory ndir = taskcontext.getTaskUtilities().resolveDirectoryAtPathCreate(builddir,
					SakerPath.valueOf("dir"));
			assertException(IllegalStateException.class, () -> ndir.add(infile));
			return "finished";
		}
	}

	private static class MovingTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			SakerFile infile = taskcontext.getTaskUtilities().resolveAtPath(INPUTFILE_PATH);
			SakerDirectory builddir = taskcontext.getTaskBuildDirectory();
			SakerDirectory ndir = taskcontext.getTaskUtilities().resolveDirectoryAtPathCreate(builddir,
					SakerPath.valueOf("dir"));

			SakerDirectory inparentdir = infile.getParent();
			infile.remove();
			DelegateSakerFile delegate = new DelegateSakerFile(infile);
			ndir.add(delegate);
			ndir.synchronize();
			inparentdir.synchronize();

			return "moved";
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath filepath = INPUTFILE_PATH;
		files.putFile(filepath, "content");

		runTask("main", new TryMovingTaskFactory());
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "finished");
		assertEquals(files.getAllBytes(INPUTFILE_PATH).toString(), "content");

		runTask("moving", new MovingTaskFactory());
		assertException(IOException.class, () -> files.getFileAttributes(filepath));
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("dir/" + INPUTFILE_PATH.getFileName())).toString(),
				"content");
	}
}
