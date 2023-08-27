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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Externalizable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.classloader.SingleClassLoaderResolver;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.util.data.annotation.ValueType;
import saker.build.util.serial.ContentReaderObjectInput;
import saker.build.util.serial.ContentWriterObjectOutput;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class ValueTypeContentSerializationTest extends SakerTestCase {

	@ValueType
	private static class MyValueType implements Externalizable {
		private static final long serialVersionUID = 1L;

		private int val;

		/**
		 * For {@link Externalizable}.
		 */
		public MyValueType() {
		}

		public MyValueType(int val) {
			this.val = val;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeInt(val);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			val = in.readInt();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + val;
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
			MyValueType other = (MyValueType) obj;
			if (val != other.val)
				return false;
			return true;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("MyValueType[val=");
			builder.append(val);
			builder.append("]");
			return builder.toString();
		}
	}

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
		ClassLoaderResolverRegistry registry = new ClassLoaderResolverRegistry(
				new SingleClassLoaderResolver("default", SakerPath.class.getClassLoader()));
		ClassLoader testclassloader = ValueTypeContentSerializationTest.class.getClassLoader();
		SingleClassLoaderResolver clresolver = new SingleClassLoaderResolver("cl", testclassloader);
		registry.register("tcl", clresolver);

		try (ContentWriterObjectOutput out = new ContentWriterObjectOutput(registry)) {
			out.writeObject(new MyValueType(1));
			out.writeObject(new MyValueType(1));
			out.writeObject(new MyValueType(1));
			out.writeObject(new MyValueType(2));
			out.writeObject(new MyValueType(3));
			out.writeObject(new MyValueType(3));

			out.drainTo((ByteSink) baos);
		}
		System.out.println("Size: " + baos.size());
		try (ContentReaderObjectInput in = new ContentReaderObjectInput(registry,
				new UnsyncByteArrayInputStream(baos.toByteArray()))) {
			Object v1 = in.readObject();
			assertIdentityEquals(v1, in.readObject());
			assertIdentityEquals(v1, in.readObject());
			Object v2 = in.readObject();
			Object v3 = in.readObject();
			assertIdentityEquals(v3, in.readObject());
			assertNotEquals(v1, v2);
			assertNotEquals(v1, v3);
			assertNotEquals(v2, v3);
		}
	}

}
