package testing.saker.build.tests.file;

import java.util.Map;

import saker.build.daemon.files.DaemonPath;
import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class DaemonPathTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		test("/", null, SakerPath.valueOf("/"));
		test("/home", null, SakerPath.valueOf("/home"));
		test("c:/", null, SakerPath.valueOf("c:/"));
		test("client:/c:", "client", SakerPath.valueOf("c:/"));
		test("client:/c:/", "client", SakerPath.valueOf("c:/"));
		test("client:/c:/sub", "client", SakerPath.valueOf("c:/sub"));
		test("client:/", null, SakerPath.valueOf("client:/"));
		test("client://", "client", SakerPath.valueOf("/"));
		test("client:////", "client", SakerPath.valueOf("/"));
		test("client://home", "client", SakerPath.valueOf("/home"));
		test("c:/home", null, SakerPath.valueOf("c:/home"));
	}

	private static void test(String daemonpath, String clientname, SakerPath path) {
		DaemonPath dp = DaemonPath.valueOf(daemonpath);
		assertEquals(dp.getClientName(), clientname);
		assertEquals(dp.getPath(), path);
	}

}
