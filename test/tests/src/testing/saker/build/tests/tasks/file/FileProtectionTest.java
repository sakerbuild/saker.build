package testing.saker.build.tests.tasks.file;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringFileOutputTaskFactory;

@SakerTest
public class FileProtectionTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		parameters.setProtectionWriteEnabledDirectories(Collections.emptySet());

		SakerPath buildoutpath = PATH_BUILD_DIRECTORY.resolve("buildout.txt");
		runTask("buildout", new StringFileOutputTaskFactory(buildoutpath, "content"));
		assertEquals(files.getAllBytes(buildoutpath).toString(), "content");

		assertTaskException(SecurityException.class, () -> runTask("workingout",
				new StringFileOutputTaskFactory(PATH_WORKING_DIRECTORY.resolve("workingout.txt"), "content")));

		SakerPath diroutpath = PATH_WORKING_DIRECTORY.resolve("dir/out.txt");
		SakerPath diroutpath2 = PATH_WORKING_DIRECTORY.resolve("dir/out2.txt");

		assertTaskException(SecurityException.class,
				() -> runTask("dirout", new StringFileOutputTaskFactory(diroutpath, "content")));

		parameters.setProtectionWriteEnabledDirectories(
				ImmutableUtils.singletonSet(SakerPathFiles.getPathKey(files, PATH_WORKING_DIRECTORY.resolve("dir"))));

		runTask("dirout", new StringFileOutputTaskFactory(diroutpath, "content"));
		assertEquals(files.getAllBytes(diroutpath).toString(), "content");

		runTask("dirout2", new StringFileOutputTaskFactory(diroutpath2, "content2"));
		assertEquals(files.getAllBytes(diroutpath2).toString(), "content2");

		//disallow some access
		parameters.setStandardInput(new UnsyncByteArrayInputStream("2\n".getBytes(StandardCharsets.UTF_8)));
		SakerPath disallowed = PATH_WORKING_DIRECTORY.resolve("disallowed");
		assertTaskException(SecurityException.class,
				() -> runTask("disallowout", new StringFileOutputTaskFactory(disallowed, "content")));

		//allow the access
		parameters.setStandardInput(new UnsyncByteArrayInputStream("1\n".getBytes(StandardCharsets.UTF_8)));
		runTask("disallowout", new StringFileOutputTaskFactory(disallowed, "content"));
		assertEquals(files.getAllBytes(disallowed).toString(), "content");

		parameters.setStandardInput(null);
	}

}
