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
package testing.saker.build.tests.utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.classloader.SingleClassLoaderResolver;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThrowingConsumer;
import saker.build.util.serial.ContentReaderObjectInput;
import saker.build.util.serial.ContentWriterObjectOutput;
import saker.build.util.serial.ObjectReadException;
import saker.build.util.serial.ObjectTypeException;
import saker.build.util.serial.ObjectWriteException;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.build.flag.TestFlag;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.TestUtils;

@SakerTest
public class ContentWriterTest extends SakerTestCase {
	private final ClassLoaderResolverRegistry registry = new ClassLoaderResolverRegistry(
			new SingleClassLoaderResolver("testcl", ContentWriterTest.class.getClassLoader()));

	private static class FailReader implements Externalizable {
		private static final long serialVersionUID = 1L;

		public FailReader() {
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			throw new RuntimeException("failread");
		}
	}

	private static class FailWriter implements Externalizable {
		private static final long serialVersionUID = 1L;

		public FailWriter() {
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			throw new RuntimeException("failwrite");
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	private static class PartiallyReading implements Externalizable {
		private static final long serialVersionUID = 1L;

		private Object first;
		private Object second;

		public PartiallyReading() {
		}

		public PartiallyReading(Object first, Object second) {
			this.first = first;
			this.second = second;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(first);
			out.writeObject(second);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			first = in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((first == null) ? 0 : first.hashCode());
			result = prime * result + ((second == null) ? 0 : second.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			PartiallyReading other = (PartiallyReading) obj;
			if (first == null) {
				if (other.first != null)
					return false;
			} else if (!first.equals(other.first))
				return false;
			if (second == null) {
				if (other.second != null)
					return false;
			} else if (!second.equals(other.second))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "PartiallyReading [first=" + first + ", second=" + second + "]";
		}
	}

	private static class SuccessfulSerializer implements Externalizable {
		private static final long serialVersionUID = 1L;
		private Object obj;

		public SuccessfulSerializer() {
		}

		public SuccessfulSerializer(Object obj) {
			this.obj = obj;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(obj);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			obj = in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((obj == null) ? 0 : obj.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SuccessfulSerializer other = (SuccessfulSerializer) obj;
			if (this.obj == null) {
				if (other.obj != null)
					return false;
			} else if (!this.obj.equals(other.obj))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "SuccessfullSerializer [obj=" + obj + "]";
		}

	}

	private enum MyEnum {
		VALUE,
		ANONYMVALUE {
		};
	}

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		CollectingTestMetric metric = new CollectingTestMetric();
		TestFlag.set(metric);

		testWriteReadEquality(TestUtils.treeMapBuilder().put("a", "b").put("c", "d").build());
		testWriteReadEquality(TestUtils.hashMapBuilder().put("a", "b").put("c", "d").build());
		testWriteReadEquality(TestUtils.mapBuilder(new LinkedHashMap<>()).put("a", "b").put("c", "d").build());
		testWriteReadEquality(TestUtils.mapBuilder(new ConcurrentSkipListMap<>()).put("a", "b").put("c", "d").build());
		testWriteReadEquality(TestUtils.mapBuilder(new ConcurrentHashMap<>()).put("a", "b").put("c", "d").build());

		testWriteReadEquality(ObjectUtils.addAll(new ArrayList<>(), "a", "b"));
		testWriteReadEquality(ObjectUtils.addAll(new LinkedList<>(), "a", "b"));
		testWriteReadEquality(ObjectUtils.addAll(new CopyOnWriteArrayList<>(), "a", "b"));
		testWriteReadEquality(ObjectUtils.addAll(new HashSet<>(), "a", "b"));
		testWriteReadEquality(ObjectUtils.addAll(new LinkedHashSet<>(), "a", "b"));
		testWriteReadEquality(ObjectUtils.addAll(new ConcurrentSkipListSet<>(), "a", "b"));
		testWriteReadEquality(ObjectUtils.addAll(ConcurrentHashMap.newKeySet(), "a", "b"));

		testWriteReadEquality(TestUtils.mapBuilder(new EnumMap<>(MyEnum.class)).build());
		testWriteReadEquality(TestUtils.mapBuilder(new EnumMap<>(MyEnum.class)).put(MyEnum.VALUE, 1).build());
		testWriteReadEquality(TestUtils.mapBuilder(new EnumMap<>(MyEnum.class)).put(MyEnum.ANONYMVALUE, 2).build());

		testWriteReadEquality(Collections.singleton(1));
		testWriteReadEquality(Collections.singletonList(1));
		testWriteReadEquality(Collections.singletonMap(1, 2));

		testWriteReadEquality("");

		testWriteReadEquality(new URI("https://example.com"));

		testFailRead();
		testFailWrite();

		testPartialRead();

		testClassNotFound();

		testDifferentTypeRead();

		testRawBytesIO();
		testRawCharsIO();

		testPartialPreRead();

		testCorruptWriteObjectLookup();
		testCorruptReadObjectLookup();

		testReducedStringsIO();
		testReducedIntegersIO();

		assertEmpty(metric.getWarnedSerializations());
	}

	private void testReducedStringsIO() throws Exception {
		testWriteReadEquality("abc\u123456789");
		testWriteReadEquality("abcd");

		String[] strings = new String[1024 * 4];
		SecureRandom rand = new SecureRandom();
		char[] chars = new char[32];
		for (int i = 0; i < strings.length; i++) {
			int slen = rand.nextInt(chars.length);
			for (int j = 0; j < chars.length; j++) {
				chars[j] = (char) (rand.nextInt() & 0xFFFF);
			}
			strings[i] = new String(chars, 0, slen);
			//test with .writeObject();
			testWriteReadEquality(strings[i]);
		}
		testIO(out -> {
			for (int i = 0; i < chars.length; i++) {
				out.writeUTF(strings[i]);
			}
		}, in -> {
			for (int i = 0; i < chars.length; i++) {
				assertEquals(strings[i], in.readUTF());
			}
		});
	}

	private void testReducedIntegersIO() throws Exception {
		int[] ints = new int[1024 * 32];
		SecureRandom rand = new SecureRandom();
		for (int i = 0; i < ints.length; i++) {
			ints[i] = rand.nextInt();
		}
		testIO(out -> {
			for (int i = 0; i < ints.length; i++) {
				out.writeInt(ints[i]);
			}
		}, in -> {
			for (int i = 0; i < ints.length; i++) {
				assertEquals(ints[i], in.readInt());
			}
		});
	}

	private void testCorruptReadObjectLookup() throws Exception {
		testIO(out -> {
			SuccessfulSerializer succa = new SuccessfulSerializer("a");
			SuccessfulSerializer succb = new SuccessfulSerializer("b");
			SuccessfulSerializer succc = new SuccessfulSerializer("c");
			out.writeObject(Arrays.asList(succa, succb, new FailReader(), succc));
			out.writeObject("a");
			out.writeObject(succa);
			out.writeObject(succb);
			out.writeObject(succc);
		}, in -> {
			try {
				in.readObject();
				fail();
			} catch (ObjectReadException e) {
			}
			assertEquals(in.readObject(), "a");
			SuccessfulSerializer rsucca = (SuccessfulSerializer) in.readObject();
			SuccessfulSerializer rsuccb = (SuccessfulSerializer) in.readObject();
			SuccessfulSerializer rsuccc = (SuccessfulSerializer) in.readObject();
			assertEquals(rsucca.obj, "a");
			assertEquals(rsuccb.obj, "b");
			assertEquals(rsuccc.obj, "c");
		});
	}

	private void testCorruptWriteObjectLookup() throws Exception {
		testIO(out -> {
			SuccessfulSerializer succa = new SuccessfulSerializer("a");
			SuccessfulSerializer succb = new SuccessfulSerializer("b");
			SuccessfulSerializer succc = new SuccessfulSerializer("c");
			try {
				out.writeObject(Arrays.asList(succa, succb, new FailWriter(), succc));
				fail();
			} catch (ObjectWriteException e) {
			}
			out.writeObject(succa);
			out.writeObject(succb);
			out.writeObject(succc);
		}, in -> {
			try {
				in.readObject();
				fail();
			} catch (ObjectWriteException e) {
			}
			SuccessfulSerializer rsucca = (SuccessfulSerializer) in.readObject();
			SuccessfulSerializer rsuccb = (SuccessfulSerializer) in.readObject();
			SuccessfulSerializer rsuccc = (SuccessfulSerializer) in.readObject();
			assertEquals(rsucca.obj, "a");
			assertEquals(rsuccb.obj, "b");
			assertEquals(rsuccc.obj, "c");
		});
	}

	private void testPartialPreRead() throws Exception {
		testIO(out -> {
			SuccessfulSerializer second = new SuccessfulSerializer("second");
			out.writeObject(new PartiallyReading(new SuccessfulSerializer("first"), second));
			out.writeObject(second);
			out.writeObject(new PartiallyReading(second, null));
		}, in -> {
			in.readObject();

			Object readsecond = in.readObject();
			assertEquals(readsecond, new SuccessfulSerializer("second"));
			PartiallyReading pr = (PartiallyReading) in.readObject();
			assertIdentityEquals(pr.first, readsecond);
		});
	}

	private void testDifferentTypeRead() throws Exception {
		testIO(out -> {
			out.writeObject("a");
		}, in -> {
			assertException(ObjectTypeException.class, () -> in.read());
			assertException(ObjectTypeException.class, () -> in.readBoolean());
			assertException(ObjectTypeException.class, () -> in.readByte());
			assertException(ObjectTypeException.class, () -> in.readChar());
			assertException(ObjectTypeException.class, () -> in.readDouble());
			assertException(ObjectTypeException.class, () -> in.readFloat());
			assertException(ObjectTypeException.class, () -> in.readInt());
			assertException(ObjectTypeException.class, () -> in.readLong());
			assertException(ObjectTypeException.class, () -> in.readShort());
			assertException(ObjectTypeException.class, () -> in.readUnsignedByte());
			assertException(ObjectTypeException.class, () -> in.readUnsignedShort());
			assertException(ObjectTypeException.class, () -> in.readUTF());
			assertEquals(in.readObject(), "a");
		});
		testIO(out -> {
			out.writeUTF("a");
		}, in -> {
			assertException(ObjectTypeException.class, () -> in.read());
			assertException(ObjectTypeException.class, () -> in.readBoolean());
			assertException(ObjectTypeException.class, () -> in.readByte());
			assertException(ObjectTypeException.class, () -> in.readChar());
			assertException(ObjectTypeException.class, () -> in.readDouble());
			assertException(ObjectTypeException.class, () -> in.readFloat());
			assertException(ObjectTypeException.class, () -> in.readInt());
			assertException(ObjectTypeException.class, () -> in.readLong());
			assertException(ObjectTypeException.class, () -> in.readShort());
			assertException(ObjectTypeException.class, () -> in.readUnsignedByte());
			assertException(ObjectTypeException.class, () -> in.readUnsignedShort());
			assertException(ObjectTypeException.class, () -> in.readObject());
			assertEquals(in.readUTF(), "a");
		});
	}

	private void testRawCharsIO() throws Exception {
		testIO(out -> {
			out.writeChar('a');
			out.writeChar('b');
		}, in -> {
			assertObjectTypeExceptionsForChar(in);
			assertEquals(in.readChar(), 'a');
			assertObjectTypeExceptionsForChar(in);
			assertEquals(in.readChar(), 'b');
		});
		testIO(out -> {
			out.writeChars("ab");
		}, in -> {
			assertObjectTypeExceptionsForChar(in);
			assertEquals(in.readChar(), 'a');
			assertObjectTypeExceptionsForChar(in);
			assertEquals(in.readChar(), 'b');
		});
	}

	private void testRawBytesIO() throws Exception {
		testIO(out -> {
			out.write(1);
		}, in -> {
			assertObjectTypeExceptionsForByte(in);
			assertEquals(in.read(), 1);
		});
		testIO(out -> {
			out.write(-10);
		}, in -> {
			assertEquals(in.readByte(), (byte) -10);
		});
		testIO(out -> {
			out.write(new byte[] { 1, 2 });
		}, in -> {
			assertObjectTypeExceptionsForByte(in);
			assertEquals(in.read(), 1);

			assertObjectTypeExceptionsForByte(in);
			assertEquals(in.read(), 2);
		});
		testIO(out -> {
			out.write(new byte[] { 1, 2 });
		}, in -> {
			assertEquals(in.readByte(), (byte) 1);
			assertObjectTypeExceptionsForByte(in);
			assertEquals(in.readByte(), (byte) 2);
		});
		testIO(out -> {
			out.write(new byte[] { 1, 2 });
		}, in -> {
			byte[] b = new byte[2];
			in.readFully(b);
			assertEquals(b, new byte[] { 1, 2 });
		});
		testIO(out -> {
			out.write(1);
			out.write(2);
		}, in -> {
			assertObjectTypeExceptionsForByte(in);
			byte[] b = new byte[2];
			in.readFully(b);
			assertEquals(b, new byte[] { 1, 2 });
		});
	}

	private static void assertObjectTypeExceptionsForByte(ContentReaderObjectInput in) throws AssertionError {
		assertException(ObjectTypeException.class, () -> in.readObject());
		assertException(ObjectTypeException.class, () -> in.readBoolean());
		assertException(ObjectTypeException.class, () -> in.readChar());
		assertException(ObjectTypeException.class, () -> in.readDouble());
		assertException(ObjectTypeException.class, () -> in.readFloat());
		assertException(ObjectTypeException.class, () -> in.readInt());
		assertException(ObjectTypeException.class, () -> in.readLong());
		assertException(ObjectTypeException.class, () -> in.readShort());
		assertException(ObjectTypeException.class, () -> in.readUnsignedShort());
		assertException(ObjectTypeException.class, () -> in.readUTF());
	}

	private static void assertObjectTypeExceptionsForChar(ContentReaderObjectInput in) throws AssertionError {
		assertException(ObjectTypeException.class, () -> in.readObject());
		assertException(ObjectTypeException.class, () -> in.readBoolean());
		assertException(ObjectTypeException.class, () -> in.readByte());
		assertException(ObjectTypeException.class, () -> in.read());
		assertException(ObjectTypeException.class, () -> in.read(new byte[1]));
		assertException(ObjectTypeException.class, () -> in.readFully(new byte[1]));
		assertException(ObjectTypeException.class, () -> in.readDouble());
		assertException(ObjectTypeException.class, () -> in.readFloat());
		assertException(ObjectTypeException.class, () -> in.readInt());
		assertException(ObjectTypeException.class, () -> in.readLong());
		assertException(ObjectTypeException.class, () -> in.readShort());
		assertException(ObjectTypeException.class, () -> in.readUnsignedShort());
		assertException(ObjectTypeException.class, () -> in.readUTF());
	}

	private void testClassNotFound() throws Exception {
		ClassLoader cl = TestUtils.createClassLoaderForClasses(SuccessfulSerializer.class);
		Class<?> serclass = cl.loadClass(SuccessfulSerializer.class.getName());
		SingleClassLoaderResolver resolver = new SingleClassLoaderResolver("cl", cl);
		registry.register("cnferesolver", resolver);
		ByteArrayRegion outbytes = testWriteIO(out -> {
			out.writeObject(serclass);
			out.writeObject(
					ReflectUtils.invokeConstructor(ReflectUtils.getConstructorAssert(serclass, Object.class), "x"));
			out.writeObject("a");
		});
		registry.unregister("cnferesolver", resolver);
		testReadIO(outbytes, in -> {
			try {
				in.readObject();
				fail();
			} catch (ClassNotFoundException e) {
			}
			try {
				in.readObject();
				fail();
			} catch (ClassNotFoundException e) {
			}
			assertEquals(in.readObject(), "a");
		});
	}

	private void testPartialRead() throws Exception {
		testIO(out -> {
			out.writeObject(new PartiallyReading("a", "b"));
			out.writeObject("a");
		}, in -> {
			assertEquals(in.readObject(), new PartiallyReading("a", null));
			assertEquals(in.readObject(), "a");
		});
	}

	private void testFailWrite() throws Throwable {
		testIO(out -> {
			Exception e = assertException(ObjectWriteException.class, () -> out.writeObject(new FailWriter()));
			assertEquals(e.getCause().getMessage(), "failwrite");
			out.writeObject("a");
		}, in -> {
			try {
				in.readObject();
				fail();
			} catch (ObjectWriteException e) {
			}
			assertEquals(in.readObject(), "a");
		});
	}

	private void testFailRead() throws Throwable {
		testIO(out -> {
			out.writeObject(new FailReader());
			out.writeObject("a");
		}, in -> {
			Exception e = assertException(ObjectReadException.class, () -> in.readObject());
			assertEquals(e.getCause().getMessage(), "failread");
			assertEquals(in.readObject(), "a");
		});
	}

	private void testWriteReadEquality(Object... objects) throws AssertionError, IOException, ClassNotFoundException {
		CollectingTestMetric metric = new CollectingTestMetric();
		TestFlag.set(metric);
		try {
			Object[] readobjects = testObjectIO(objects);
			assertEquals(readobjects, objects);
			for (int i = 0; i < readobjects.length; i++) {
				assertSameClass(readobjects[i], objects[i]);
			}
			assertEmpty(metric.getWarnedSerializations());
		} finally {
			TestFlag.set(null);
		}
	}

	private Object[] testObjectIO(Object... obj) throws IOException, ClassNotFoundException {
		try (ContentWriterObjectOutput out = new ContentWriterObjectOutput(registry)) {
			for (int i = 0; i < obj.length; i++) {
				out.writeObject(obj[i]);
			}
			UnsyncByteArrayOutputStream bytes = new UnsyncByteArrayOutputStream();
			out.drainTo((OutputStream) bytes);
			UnsyncByteArrayInputStream is = new UnsyncByteArrayInputStream(bytes.toByteArrayRegion());
			try (ContentReaderObjectInput in = new ContentReaderObjectInput(registry, is)) {
				Object[] res = new Object[obj.length];
				for (int i = 0; i < res.length; i++) {
					res[i] = in.readObject();
				}
				return res;
			}
		}
	}

	private void testIO(ThrowingConsumer<ContentWriterObjectOutput> writer,
			ThrowingConsumer<ContentReaderObjectInput> reader) throws Exception {
		ByteArrayRegion outbytes = testWriteIO(writer);
		testReadIO(outbytes, reader);
	}

	private void testReadIO(ByteArrayRegion outbytes, ThrowingConsumer<ContentReaderObjectInput> reader)
			throws Exception, IOException {
		UnsyncByteArrayInputStream is = new UnsyncByteArrayInputStream(outbytes);
		try (ContentReaderObjectInput in = new ContentReaderObjectInput(registry, is)) {
			reader.accept(in);
		}
	}

	private ByteArrayRegion testWriteIO(ThrowingConsumer<ContentWriterObjectOutput> writer)
			throws Exception, IOException {
		ByteArrayRegion outbytes;
		try (ContentWriterObjectOutput out = new ContentWriterObjectOutput(registry)) {
			writer.accept(out);

			UnsyncByteArrayOutputStream bytes = new UnsyncByteArrayOutputStream();
			out.drainTo((OutputStream) bytes);

			outbytes = bytes.toByteArrayRegion();
		}
		return outbytes;
	}
}
