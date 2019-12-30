package saker.build.launching;

import java.io.InputStream;

import saker.build.thirdparty.saker.util.io.StreamUtils;

/**
 * <pre>
 * Prints the licenses of the included software used by saker.build.
 * </pre>
 */
public class LicensesCommand {
	public void call() throws Exception {
		try (InputStream in = Main.class.getClassLoader().getResourceAsStream("META-INF/BUNDLED-LICENSES")) {
			StreamUtils.copyStream(in, System.out);
		}
	}
}
