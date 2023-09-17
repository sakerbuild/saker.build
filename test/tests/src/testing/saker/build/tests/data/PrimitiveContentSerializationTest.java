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

		byte[] bytes = new byte[] { -1, 0, 1, 2, 127, Byte.MIN_VALUE, Byte.MAX_VALUE, };
		short[] shorts = new short[] { 0, 1, 2, -1, -10, 0xFF, (short) 0xFFFF, (short) 0xFF00, 0x00FF, Short.MIN_VALUE,
				Short.MAX_VALUE, };
		int[] ints = new int[] { 0, 1, 2, -1, -10, 0xFF, 0xFFFF, 0xFFFFFF, 0xFFFFFFFF, 0xFFFFFF00, 0xFFFFFF12,
				0xFFFF1234, 0xFFFF0000, 0xFF000000, 0xFF123456, Integer.MIN_VALUE, Integer.MAX_VALUE, };
		long[] longs = new long[] { 0, 1, 2, -1, -10, 0xFF, 0xFFFF, 0xFFFFFF, 0xFFFFFFFF, 0xFFFFFFFFFFL,
				0xFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL, 0xFFFFFF00, 0xFFFFFF12, 0xFFFF1234, 0xFFFF0000,
				0xFF000000, 0xFF123456, 0xFFFFFFFFFFFFFF00L, 0xFFFFFFFFFFFFFF12L, 0xFFFFFFFFFFFF1234L,
				0xFFFFFFFFFFFF0000L, 0xFFFFFFFFFF000000L, 0xFFFFFFFFFF123456L, 0xffff2ae4f142bbceL, Long.MIN_VALUE,
				Long.MAX_VALUE };

		float[] floats = new float[] { -1234f, -1f, -0f, 0f, 1.0f, 1234f, Float.MAX_VALUE, Float.MIN_VALUE,
				Float.MIN_NORMAL, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, };
		double[] doubles = new double[] { -1234d, -1d, -0d, 0d, 1.0d, 1234d, Double.MAX_VALUE, Double.MIN_VALUE,
				Double.MIN_NORMAL, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, };

		char[] chars = new char[] { 0, 1, 1234, 0xABCD, 'a', 'b', '\0', Character.MIN_VALUE, Character.MAX_VALUE, };

		int[] randints = new int[200_000 * 2];
		long[] randlongs = new long[200_000 * 2];

		long seed = System.currentTimeMillis();
		System.out.println("Seed is: " + seed);
		Random random = new Random(seed);

		//add lower numbers to the randomed values as well, to have object relative backreferences
		for (int i = 0; i < randints.length; i += 2) {
			randints[i] = random.nextInt();
			randints[i + 1] = random.nextInt() & 0xFF;
		}
		for (int i = 0; i < randlongs.length; i += 2) {
			randlongs[i] = random.nextLong();
			randlongs[i + 1] = random.nextLong() & 0xFF;
		}

		Object[] arrays = new Object[] { new byte[] { 1, 2, 3 }, new short[] { 1, 2, 3 }, new int[] { 1, 2, 3 },
				new long[] { 1, 2, 3 }, new char[] { 1, 2, 3 }, new float[] { 1, 2, 3 }, new double[] { 1, 2, 3 },
				new boolean[] { true, false }, new String[] { "a", "b", "123" }, };

		byte[] bytedata = new byte[123];
		byte[] bytedata2 = new byte[256 + 123];
		byte[] bytedata3 = new byte[256 * 256 + 123];
		byte[] bytedata4 = new byte[256 * 256 * 256 + 123];
		random.nextBytes(bytedata);
		random.nextBytes(bytedata2);
		random.nextBytes(bytedata3);
		random.nextBytes(bytedata4);

		try (ContentWriterObjectOutput out = new ContentWriterObjectOutput(registry)) {
			out.writeBoolean(true);
			out.writeBoolean(false);
			out.writeObject(true);
			out.writeObject(false);

			for (byte v : bytes) {
				out.writeByte(v);
				out.writeObject(v);
			}
			for (short v : shorts) {
				out.writeShort(v);
				out.writeObject(v);
			}
			out.write(bytedata);

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
			for (long v : randlongs) {
				out.writeLong(v);
				out.writeObject(v);
			}

			for (float v : floats) {
				out.writeFloat(v);
				out.writeObject(v);
			}
			for (double v : doubles) {
				out.writeDouble(v);
				out.writeObject(v);
			}

			for (char v : chars) {
				out.writeChar(v);
				out.writeObject(v);
			}

			out.write(bytedata2);
			for (Object arr : arrays) {
				out.writeObject(arr);
			}

			out.write(bytedata3);
			out.write(bytedata4);

			out.writeObject("END");

			out.drainTo((ByteSink) baos);
		}
		System.out.println("Size: " + baos.size());
		try (ContentReaderObjectInput in = new ContentReaderObjectInput(registry,
				new UnsyncByteArrayInputStream(baos.toByteArray()))) {
			byte[] bytedataread = new byte[bytedata.length];
			byte[] bytedataread2 = new byte[bytedata2.length];
			byte[] bytedataread3 = new byte[bytedata3.length];
			byte[] bytedataread4 = new byte[bytedata4.length];

			assertTrue(in.readBoolean());
			assertFalse(in.readBoolean());
			assertEquals(true, in.readObject());
			assertEquals(false, in.readObject());

			for (byte v : bytes) {
				assertEquals(v, in.readByte());
				assertEquals(v, in.readObject());
			}
			for (short v : shorts) {
				assertEquals(v, in.readShort());
				assertEquals(v, in.readObject());
			}

			in.readFully(bytedataread);
			assertEquals(bytedata, bytedataread);

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
			for (long v : randlongs) {
				assertEquals(v, in.readLong());
				assertEquals(v, in.readObject());
			}
			for (float v : floats) {
				assertEquals(v, in.readFloat());
				assertEquals(v, in.readObject());
			}
			for (double v : doubles) {
				assertEquals(v, in.readDouble());
				assertEquals(v, in.readObject());
			}
			for (char v : chars) {
				assertEquals(v, in.readChar());
				assertEquals(v, in.readObject());
			}

			in.readFully(bytedataread2);
			assertEquals(bytedata2, bytedataread2);

			for (Object arr : arrays) {
				assertEquals(arr, in.readObject());
			}

			in.readFully(bytedataread3);
			assertEquals(bytedata3, bytedataread3);
			in.readFully(bytedataread4);
			assertEquals(bytedata4, bytedataread4);

			assertEquals("END", in.readObject());
		}
	}

}
