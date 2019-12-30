package testing.saker.build.tests.utils;

import java.util.Map;

import javax.tools.ToolProvider;

import saker.build.util.java.JavaTools;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

/**
 * Tests that the Java tools can be retrieved in the current JVM.
 * <p>
 * The {@link ToolProvider} class is not on the class path by default on JDK9+, therefore it needs special handling.
 * <p>
 * Note: that was only the case when the testing task didn't set the platform classloader to the user classloader
 * parent.
 */
@SakerTest
public class JavaToolsTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		JavaTools.getSystemDocumentationTool();
		JavaTools.getSystemJavaCompiler();
		ToolProvider.getSystemJavaCompiler();
	}

}
