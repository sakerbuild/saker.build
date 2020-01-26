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
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

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
public class OptionalContentSerializationTest extends SakerTestCase {

	public static class OptionalSerializable implements Serializable {
		private static final long serialVersionUID = 1L;

		public final Object val;

		public OptionalSerializable(Object val) {
			this.val = val;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((val == null) ? 0 : val.hashCode());
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
			OptionalSerializable other = (OptionalSerializable) obj;
			if (val == null) {
				if (other.val != null)
					return false;
			} else if (!val.equals(other.val))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "OptionalSerializable[" + (val != null ? "val=" + val : "") + "]";
		}
	}

	public static class OptionalExternalizable implements Externalizable {
		private static final long serialVersionUID = 1L;

		public Object val;

		public OptionalExternalizable() {
		}

		public OptionalExternalizable(Object val) {
			this.val = val;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(val);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			val = in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((val == null) ? 0 : val.hashCode());
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
			OptionalExternalizable other = (OptionalExternalizable) obj;
			if (val == null) {
				if (other.val != null)
					return false;
			} else if (!val.equals(other.val))
				return false;
			return true;
		}

	}

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
		ClassLoaderResolverRegistry registry = new ClassLoaderResolverRegistry();
		ClassLoader testclassloader = OptionalContentSerializationTest.class.getClassLoader();
		SingleClassLoaderResolver clresolver = new SingleClassLoaderResolver("cl", testclassloader);
		registry.register("tcl", clresolver);

		try (ContentWriterObjectOutput out = new ContentWriterObjectOutput(registry)) {
			out.writeObject(Optional.empty());
			out.writeObject(Optional.ofNullable(null));
			out.writeObject(Optional.ofNullable(1));
			out.writeObject(Optional.of(2));
			out.writeObject(Optional.of(new OptionalSerializable(3)));
			out.writeObject(Optional.of(new OptionalExternalizable(4)));
			out.drainTo((ByteSink) baos);
		}
		try (ContentReaderObjectInput in = new ContentReaderObjectInput(registry,
				new UnsyncByteArrayInputStream(baos.toByteArray()))) {
			assertEquals(Optional.empty(), in.readObject());
			assertEquals(Optional.ofNullable(null), in.readObject());
			assertEquals(Optional.ofNullable(1), in.readObject());
			assertEquals(Optional.of(2), in.readObject());
			assertEquals(Optional.of(new OptionalSerializable(3)), in.readObject());
			assertEquals(Optional.of(new OptionalExternalizable(4)), in.readObject());
		}
	}

}
