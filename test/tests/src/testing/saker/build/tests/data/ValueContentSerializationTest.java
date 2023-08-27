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
package testing.saker.build.tests.data;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.classloader.SingleClassLoaderResolver;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.util.serial.ContentReaderObjectInput;
import saker.build.util.serial.ContentWriterObjectOutput;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class ValueContentSerializationTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
		ClassLoaderResolverRegistry registry = new ClassLoaderResolverRegistry(
				new SingleClassLoaderResolver("default", SakerPath.class.getClassLoader()));
		ClassLoader testclassloader = ValueContentSerializationTest.class.getClassLoader();
		SingleClassLoaderResolver clresolver = new SingleClassLoaderResolver("cl", testclassloader);
		registry.register("tcl", clresolver);

		List<Object> serials = new ArrayList<>();
		serials.add(Byte.valueOf("123"));
		serials.add(Byte.valueOf("-120"));
		serials.add(Short.valueOf("123"));
		serials.add(Short.valueOf("-120"));
		serials.add(Integer.valueOf("123"));
		serials.add(Integer.valueOf("-120"));
		serials.add(Integer.valueOf("123000"));
		serials.add(Integer.valueOf("123000000"));
		serials.add(Long.valueOf("123"));
		serials.add(Long.valueOf("-120"));
		serials.add(Float.valueOf("123"));
		serials.add(Float.valueOf("-120"));
		serials.add(Double.valueOf("123"));
		serials.add(Double.valueOf("-120"));
		serials.add(Character.valueOf('X'));
		serials.add(true);
		serials.add(false);

		serials.add(UUID.randomUUID());
		serials.add(UUID.randomUUID());
		serials.add(UUID.randomUUID());

		Date currentdate = new Date();
		serials.add(currentdate);
		serials.add(currentdate);
		serials.add(new Date(999456321L));

		//some duplicates
		serials.add(SakerPath.PATH_SLASH);
		serials.add(SakerPath.PATH_SLASH);
		serials.add(SakerPath.EMPTY);
		serials.add(SakerPath.EMPTY);
		serials.add(SakerPath.valueOf("/"));
		serials.add(SakerPath.valueOf("/path"));
		serials.add(SakerPath.valueOf("/home/user"));
		serials.add(SakerPath.valueOf("/home/user/folder"));

		serials.add(WildcardPath.valueOf("/*"));
		serials.add(WildcardPath.valueOf("/path/*/*.java"));
		serials.add(WildcardPath.valueOf("/home/*/folder/*"));
		serials.add(WildcardPath.valueOf("/home/user/folder"));

		serials.add(new URI("https://saker.build"));
		serials.add(new URI("https://saker.build/"));
		serials.add(new URI("https://saker.build/index.html"));

		try (ContentWriterObjectOutput out = new ContentWriterObjectOutput(registry)) {
			for (Object o : serials) {
				out.writeObject(o);
				out.writeObject(o);
			}

			out.drainTo((ByteSink) baos);
		}
		System.out.println("Size: " + baos.size());
		try (ContentReaderObjectInput in = new ContentReaderObjectInput(registry,
				new UnsyncByteArrayInputStream(baos.toByteArray()))) {
			for (Object o : serials) {
				Object read = in.readObject();
				assertEquals(o, read);
				assertIdentityEquals(read, in.readObject());
			}
		}
	}

}
