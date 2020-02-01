package testing.saker.build.tests.release;

import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import saker.build.meta.ManifestNames;
import saker.build.meta.Versions;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.EnvironmentTestCase;

@SakerTest
public class VersionMetaDataTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		try (JarFile jf = new JarFile(EnvironmentTestCase.getSakerJarPath().toFile())) {
			Manifest mf = jf.getManifest();
			assertEquals(Versions.VERSION_STRING_FULL, mf.getMainAttributes().getValue(ManifestNames.VERSION));
		}
	}

}
