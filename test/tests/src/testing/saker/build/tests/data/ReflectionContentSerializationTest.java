package testing.saker.build.tests.data;

import java.util.AbstractList;
import java.util.Map;

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
public class ReflectionContentSerializationTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
		ClassLoaderResolverRegistry registry = new ClassLoaderResolverRegistry();
		ClassLoader testclassloader = ReflectionContentSerializationTest.class.getClassLoader();
		SingleClassLoaderResolver clresolver = new SingleClassLoaderResolver("cl", testclassloader);
		registry.register("tcl", clresolver);

		try (ContentWriterObjectOutput out = new ContentWriterObjectOutput(registry)) {
			out.writeObject(AbstractList.class);
			out.writeObject(AbstractList.class.getDeclaredField("modCount"));
			out.writeObject(AbstractList.class.getDeclaredMethod("clear"));
			out.writeObject(AbstractList.class.getDeclaredConstructor());
			out.writeObject(testclassloader);
			out.drainTo((ByteSink) baos);
		}
		try (ContentReaderObjectInput in = new ContentReaderObjectInput(registry,
				new UnsyncByteArrayInputStream(baos.toByteArray()))) {
			assertEquals(in.readObject(), AbstractList.class);
			assertEquals(in.readObject(), AbstractList.class.getDeclaredField("modCount"));
			assertEquals(in.readObject(), AbstractList.class.getDeclaredMethod("clear"));
			assertEquals(in.readObject(), AbstractList.class.getDeclaredConstructor());
			assertEquals(in.readObject(), testclassloader);
		}
	}

}
