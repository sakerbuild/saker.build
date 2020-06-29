/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package testing.saker.build.tests.file;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerFileProvider;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.rmi.connection.RMIOptions;
import saker.build.thirdparty.saker.rmi.connection.RMIServer;
import saker.build.thirdparty.saker.rmi.connection.RMIVariables;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.api.TaskTestMetric;
import testing.saker.build.flag.TestFlag;
import testing.saker.build.tests.EnvironmentTestCase;

@SakerTest
public class LocalFileProviderRMITest extends SakerTestCase {

	public static SakerFileProvider getLocalFileProvider() {
		return LocalFileProvider.getInstance();
	}

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		TaskTestMetric metric = new TaskTestMetric() {
			@Override
			public boolean isForcedRMILocalFileProvider() {
				return true;
			}
		};
		TestFlag.set(metric);

		Class<?> bufferingclass = Class.forName(LocalFileProvider.class.getName() + "$RMIBufferedFileInputByteSource",
				false, LocalFileProvider.class.getClassLoader());

		SakerPath workingdir = SakerPath.valueOf(
				EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(getClass().getName().replace('.', '/')));

		SakerPath builddir = SakerPath.valueOf(
				EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(getClass().getName().replace('.', '/')));

		byte[] longcontent = new byte[64 * 1024];
		for (int i = 0; i < longcontent.length; i++) {
			longcontent[i] = (byte) ('0' + i);
		}

		SakerPath longcontentpath = builddir.resolve("longcontent.txt");
		getLocalFileProvider().createDirectories(longcontentpath.getParent());
		getLocalFileProvider().writeToFile(new UnsyncByteArrayInputStream(longcontent), longcontentpath);

		RMIOptions opt = new RMIOptions();
		opt.classLoader(this.getClass().getClassLoader());
		try (RMIServer server = new RMIServer() {

			@Override
			protected RMIOptions getRMIOptionsForAcceptedConnection(Socket acceptedsocket, int protocolversion)
					throws IOException, RuntimeException {
				return opt;
			}

		}) {
			ThreadUtils.startDaemonThread(() -> {
				server.acceptConnections();
			});

			try (RMIConnection connection = opt.connect(server.getLocalSocketAddress());
					RMIVariables vars = connection.newVariables()) {
				SakerFileProvider fp = (SakerFileProvider) vars
						.invokeRemoteStaticMethod(LocalFileProviderRMITest.class.getMethod("getLocalFileProvider"));
				System.out.println("LocalFileProviderRMITest.runTest() " + fp);

				try (ByteSource in = fp.openInput(workingdir.resolve("empty.txt"))) {
					System.out.println("LocalFileProviderRMITest.runTest() " + in);
					assertEquals(in.getClass(), bufferingclass);
					assertTrue((boolean) ReflectUtils.getFieldValue(bufferingclass.getDeclaredField("closed"), in));
					assertNull(ReflectUtils.getFieldValue(bufferingclass.getDeclaredField("buffer"), in));
				}
				try (ByteSource in = fp.openInput(workingdir.resolve("withcontent.txt"))) {
					System.out.println("LocalFileProviderRMITest.runTest() " + in);
					assertEquals(in.getClass(), bufferingclass);
					assertTrue((boolean) ReflectUtils.getFieldValue(bufferingclass.getDeclaredField("closed"), in));
					assertNonNull(ReflectUtils.getFieldValue(bufferingclass.getDeclaredField("buffer"), in));
					assertEquals(in.read(8 * 1024).toString(), "123");
				}
				try (ByteSource in = fp.openInput(longcontentpath)) {
					System.out.println("LocalFileProviderRMITest.runTest() " + in);
					assertEquals(in.getClass(), bufferingclass);
					assertFalse((boolean) ReflectUtils.getFieldValue(bufferingclass.getDeclaredField("closed"), in));
					assertNonNull(ReflectUtils.getFieldValue(bufferingclass.getDeclaredField("buffer"), in));

					assertEquals(StreamUtils.readSourceFully(in).copyOptionally(), longcontent);
					assertTrue((boolean) ReflectUtils.getFieldValue(bufferingclass.getDeclaredField("closed"), in));
				}

			}
		}

		System.out.println("LocalFileProviderRMITest.runTest() " + metric);
	}

}
