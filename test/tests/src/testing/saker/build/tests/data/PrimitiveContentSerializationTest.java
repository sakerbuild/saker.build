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

import java.util.Map;
import java.util.Random;

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
public class PrimitiveContentSerializationTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
		ClassLoaderResolverRegistry registry = new ClassLoaderResolverRegistry();
		ClassLoader testclassloader = PrimitiveContentSerializationTest.class.getClassLoader();
		SingleClassLoaderResolver clresolver = new SingleClassLoaderResolver("cl", testclassloader);
		registry.register("tcl", clresolver);

		int[] ints = new int[] { 0, 1, 2, -1, -10, 0xFF, 0xFFFF, 0xFFFFFF, 0xFFFFFFFF, 0xFFFFFF00, 0xFFFFFF12,
				0xFFFF1234, 0xFFFF0000, 0xFF000000, 0xFF123456 };
		long[] longs = new long[] { 0, 1, 2, -1, -10, 0xFF, 0xFFFF, 0xFFFFFF, 0xFFFFFFFF, 0xFFFFFFFFFFL,
				0xFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL, 0xFFFFFF00, 0xFFFFFF12, 0xFFFF1234, 0xFFFF0000,
				0xFF000000, 0xFF123456, 0xFFFFFFFFFFFFFF00L, 0xFFFFFFFFFFFFFF12L, 0xFFFFFFFFFFFF1234L,
				0xFFFFFFFFFFFF0000L, 0xFFFFFFFFFF000000L, 0xFFFFFFFFFF123456L, };

		int[] randints = new int[200_000];

		long seed = System.currentTimeMillis();
		System.out.println("Seed is: " + seed);
		Random random = new Random(seed);

		for (int i = 0; i < randints.length; i++) {
			randints[i] = random.nextInt();
		}

		try (ContentWriterObjectOutput out = new ContentWriterObjectOutput(registry)) {
			out.writeBoolean(true);
			out.writeBoolean(false);
			out.writeObject(true);
			out.writeObject(false);

			for (int v : ints) {
				out.writeInt(v);
				out.writeObject(v);
			}
			for (int v : randints) {
				out.writeInt(v);
				out.writeObject(v);
			}
			for (long v : longs) {
				out.writeLong(v);
				out.writeObject(v);
			}

			out.drainTo((ByteSink) baos);
		}
		System.out.println("Size: " + baos.size());
		try (ContentReaderObjectInput in = new ContentReaderObjectInput(registry,
				new UnsyncByteArrayInputStream(baos.toByteArray()))) {
			assertTrue(in.readBoolean());
			assertFalse(in.readBoolean());
			assertEquals(true, in.readObject());
			assertEquals(false, in.readObject());

			for (int v : ints) {
				assertEquals(v, in.readInt());
				assertEquals(v, in.readObject());
			}
			for (int v : randints) {
				assertEquals(v, in.readInt());
				assertEquals(v, in.readObject());
			}
			for (long v : longs) {
				assertEquals(v, in.readLong());
				assertEquals(v, in.readObject());
			}
		}
	}

}
