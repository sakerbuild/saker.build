package testing.saker.idesupport;

import java.util.Map;

import saker.build.file.path.SakerPath;
import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.MountPathIDEProperty;
import saker.build.ide.support.properties.ProviderMountIDEProperty;
import saker.build.ide.support.properties.SimpleIDEProjectProperties;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class IDEPathTest extends SakerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		SakerPath projectpath = SakerPath.valueOf("c:/path/to/project");
		{
			IDEProjectProperties defaults = SimpleIDEProjectProperties.getDefaultsInstance();

			assertEquals(SakerIDESupportUtils.projectPathToExecutionPath(defaults, projectpath,
					SakerPath.valueOf("saker.build")), SakerPath.valueOf("wd:/saker.build"));
			assertEquals(SakerIDESupportUtils.projectPathToExecutionPath(defaults, projectpath,
					projectpath.resolve("saker.build")), SakerPath.valueOf("wd:/saker.build"));

			assertEquals(SakerIDESupportUtils.projectPathToExecutionPath(defaults, projectpath,
					SakerPath.valueOf("sub/dir/file.txt")), SakerPath.valueOf("wd:/sub/dir/file.txt"));
			assertEquals(SakerIDESupportUtils.projectPathToExecutionPath(defaults, projectpath,
					projectpath.resolve("sub/dir/file.txt")), SakerPath.valueOf("wd:/sub/dir/file.txt"));

			assertEquals(SakerIDESupportUtils.executionPathToProjectRelativePath(defaults, projectpath,
					SakerPath.valueOf("wd:/saker.build")), SakerPath.valueOf("saker.build"));
			assertEquals(SakerIDESupportUtils.executionPathToProjectRelativePath(defaults, projectpath,
					SakerPath.valueOf("wd:/sub/dir/file.txt")), SakerPath.valueOf("sub/dir/file.txt"));
		}
		{
			//the working directory is under a subdirectory of a project relative mount
			SimpleIDEProjectProperties.Builder builder = SimpleIDEProjectProperties.builder();
			builder.setMounts(ImmutableUtils.makeImmutableHashSet(new ProviderMountIDEProperty[] {

					new ProviderMountIDEProperty("wd:", new MountPathIDEProperty("project", "/some/directory")),

			}));
			builder.setWorkingDirectory("wd:");
			IDEProjectProperties propssubdir = builder.build();

			assertEquals(SakerIDESupportUtils.projectPathToExecutionPath(propssubdir, projectpath,
					SakerPath.valueOf("saker.build")), null);
			assertEquals(SakerIDESupportUtils.projectPathToExecutionPath(propssubdir, projectpath,
					projectpath.resolve("saker.build")), null);

			assertEquals(SakerIDESupportUtils.projectPathToExecutionPath(propssubdir, projectpath,
					SakerPath.valueOf("some/directory/saker.build")), SakerPath.valueOf("wd:/saker.build"));
			assertEquals(SakerIDESupportUtils.projectPathToExecutionPath(propssubdir, projectpath,
					projectpath.resolve("some/directory/saker.build")), SakerPath.valueOf("wd:/saker.build"));

			assertEquals(SakerIDESupportUtils.executionPathToProjectRelativePath(propssubdir, projectpath,
					SakerPath.valueOf("wd:/saker.build")), SakerPath.valueOf("some/directory/saker.build"));
			assertEquals(
					SakerIDESupportUtils.executionPathToProjectRelativePath(propssubdir, projectpath,
							SakerPath.valueOf("wd:/subdir/file.txt")),
					SakerPath.valueOf("some/directory/subdir/file.txt"));
		}
	}

}
