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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.classloader.SingleClassLoaderResolver;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.util.serial.ContentReaderObjectInput;
import saker.build.util.serial.ContentWriterObjectOutput;
import saker.build.util.serial.ObjectTypeException;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class StringContentSerializationTest extends SakerTestCase {

	private static final String STR_ABC = "abc";
	private static final String STR_ABC_WIDE = "abc\u1234";

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
		ClassLoaderResolverRegistry registry = new ClassLoaderResolverRegistry();
		ClassLoader testclassloader = StringContentSerializationTest.class.getClassLoader();
		SingleClassLoaderResolver clresolver = new SingleClassLoaderResolver("cl", testclassloader);
		registry.register("tcl", clresolver);

		List<String> randoms = new ArrayList<>();

		long seed = System.currentTimeMillis();
		System.out.println("Seed is: " + seed);
		Random random = new Random(seed);

		final int RANDCOUNT = 200_000;

		for (int i = 0; i < RANDCOUNT; i++) {
			//binary tostring
			//append a possibly 2 char wide character
			randoms.add(Long.toString(random.nextLong(), 2) + ((char) random.nextInt(0xFFFF)));
		}

		try (ContentWriterObjectOutput out = new ContentWriterObjectOutput(registry)) {
			out.writeObject(STR_ABC);
			out.writeObject(STR_ABC);
			out.writeUTF(STR_ABC);
			out.writeUTF(STR_ABC);

			out.writeObject(STR_ABC_WIDE);
			out.writeUTF(STR_ABC_WIDE);

			for (String s : randoms) {
				out.writeObject(s);
				out.writeUTF(s);
			}

			out.drainTo((ByteSink) baos);
		}
		System.out.println("Size: " + baos.size());
		try (ContentReaderObjectInput in = new ContentReaderObjectInput(registry,
				new UnsyncByteArrayInputStream(baos.toByteArray()))) {
			Object r1 = in.readObject();
			{
				Object r2 = in.readObject();
				assertEquals(STR_ABC, r1);
				assertIdentityEquals(r1, r2);
			}

			{
				assertException(ObjectTypeException.class, in::readObject);
				Object r3 = in.readUTF();
				Object r4 = in.readUTF();
				assertIdentityEquals(r1, r3);
				assertIdentityEquals(r1, r4);
			}
			{
				assertException(ObjectTypeException.class, in::readUTF);
				Object rw1 = in.readObject();
				Object rw2 = in.readUTF();
				assertEquals(STR_ABC_WIDE, rw1);
				assertIdentityEquals(rw1, rw2);
			}

			for (String s : randoms) {
				Object ro = in.readObject();
				String ru = in.readUTF();
				assertEquals(ru, ro);
				assertEquals(s, ru);
			}
		}
	}

}
