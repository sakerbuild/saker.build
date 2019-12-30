package testing.saker.build.tests.data;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map.Entry;

import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.classloader.PathClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.classloader.SingleClassLoaderResolver;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.util.data.DataConverterUtils;
import saker.build.util.serial.ContentReaderObjectInput;
import saker.build.util.serial.ContentWriterObjectOutput;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.build.tests.data.adapt.BaseItf;
import testing.saker.build.tests.data.adapt.BaseItfImpl;

@SakerTest
public class InterfaceAdaptationTest extends CollectingMetricEnvironmentTestCase {
	@SuppressWarnings("unused")
	public static interface AdaptTestInterfaceVisitor {
		public default void visit(AdaptTestInterface itf) {
		}

		public default void visitDupl(AdaptTestInterface itf) {
		}

	}

	public static interface AdaptTestInterface {
		public void accept(AdaptTestInterfaceVisitor visitor);

		public void acceptDupl(AdaptTestInterfaceVisitor visitor);
	}

	public static interface AdditionalInterface {

	}

	private static class AdaptTestImpl implements AdaptTestInterface, AdditionalInterface {
		public AdaptTestImpl() {
		}

		@Override
		public void accept(AdaptTestInterfaceVisitor visitor) {
			visitor.visit(this);
		}

		@Override
		public void acceptDupl(AdaptTestInterfaceVisitor visitor) {
			visitor.visitDupl(new AdaptTestImpl());
		}
	}

	private static class SerialAdaptTestImpl extends AdaptTestImpl implements Serializable {
		private static final long serialVersionUID = 1L;

		public SerialAdaptTestImpl() {
		}
	}

	private static class ExternalAdaptTestImpl extends AdaptTestImpl implements Externalizable {
		private static final long serialVersionUID = 1L;

		public ExternalAdaptTestImpl() {
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		modifiedsTest();

		adaptTest();

		testSerialization();

		testEquality();
		
		testIdentity();
	}

	public interface IdentityItf {
		public void assertIdentity(Object obj);
	}

	public static class IdentityImpl implements IdentityItf {
		@Override
		public void assertIdentity(Object obj) {
			if (this != obj) {
				throw new AssertionError();
			}
		}
	}

	private void testIdentity() throws Exception {
		ClassLoader cl = TestUtils.createClassLoaderForClasses(IdentityItf.class, IdentityImpl.class);
		Class<?> implclass = Class.forName(IdentityImpl.class.getName(), false, cl);
		Object instance = ReflectUtils.getConstructorAssert(implclass).newInstance();

		IdentityItf adapted = (IdentityItf) DataConverterUtils.adaptInterface(this.getClass().getClassLoader(), instance);
		adapted.assertIdentity(adapted);
	}

	public interface EqualitySuperItf {
		public int getVal();
	}

	public static class EqualityImpl implements EqualitySuperItf {
		private int val;

		public EqualityImpl(int val) {
			this.val = val;
		}

		@Override
		public int getVal() {
			return val;
		}

		@Override
		public int hashCode() {
			return val;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof EqualitySuperItf)) {
				return false;
			}
			EqualitySuperItf other = (EqualitySuperItf) obj;
			if (val != other.getVal())
				return false;
			return true;
		}

	}

	private void testEquality() throws Exception {
		ClassLoader cl = TestUtils.createClassLoaderForClasses(EqualityImpl.class, EqualitySuperItf.class);
		Class<?> eqimplclass = Class.forName(EqualityImpl.class.getName(), false, cl);
		Object cleqimpl1 = ReflectUtils.getConstructorAssert(eqimplclass, int.class).newInstance(123);
		Object cleqimpl2 = ReflectUtils.getConstructorAssert(eqimplclass, int.class).newInstance(123);

		assertEquals(cleqimpl1, cleqimpl2);

		EqualityImpl localeqimpl1 = new EqualityImpl(123);
		EqualityImpl localeqimpl2 = new EqualityImpl(123);
		assertNotEquals(cleqimpl1, localeqimpl1);
		assertNotInstanceOf(cleqimpl1, EqualitySuperItf.class);

		EqualitySuperItf adaptedcleqimpl1 = (EqualitySuperItf) DataConverterUtils
				.adaptInterface(this.getClass().getClassLoader(), cleqimpl1);
		EqualitySuperItf adaptedcleqimpl2 = (EqualitySuperItf) DataConverterUtils
				.adaptInterface(this.getClass().getClassLoader(), cleqimpl2);
		assertEquals(localeqimpl1, adaptedcleqimpl1);
		assertEquals(adaptedcleqimpl1, localeqimpl1);

		assertEquals(adaptedcleqimpl1, adaptedcleqimpl2);
		assertEquals(adaptedcleqimpl2, adaptedcleqimpl1);

		Object localadapted1 = DataConverterUtils.adaptInterface(cl, localeqimpl1);
		Object localadapted2 = DataConverterUtils.adaptInterface(cl, localeqimpl2);
		assertEquals(cleqimpl1, localadapted1);
		assertEquals(localadapted1, cleqimpl1);

		assertEquals(localadapted1, localadapted2);
		assertEquals(localadapted2, localadapted1);
	}

	private void testSerialization() throws Exception {
		ClassLoader cl = TestUtils.createClassLoaderForClasses(AdaptTestInterface.class, AdaptTestImpl.class,
				AdditionalInterface.class, SerialAdaptTestImpl.class, AdaptTestInterfaceVisitor.class,
				ExternalAdaptTestImpl.class);
		Class<?> serialremoteclass = Class.forName(SerialAdaptTestImpl.class.getName(), false, cl);
		Class<?> externalremoteclass = Class.forName(ExternalAdaptTestImpl.class.getName(), false, cl);

		AdaptTestInterface adapted = (AdaptTestInterface) DataConverterUtils
				.adaptInterface(this.getClass().getClassLoader(), ReflectUtils.newInstance(serialremoteclass));
		AdaptTestInterface externaladapted = (AdaptTestInterface) DataConverterUtils
				.adaptInterface(this.getClass().getClassLoader(), ReflectUtils.newInstance(externalremoteclass));

		UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();

		SingleClassLoaderResolver defclresolver = new SingleClassLoaderResolver("def",
				DataConverterUtils.class.getClassLoader());
		SingleClassLoaderResolver clresolver1 = new SingleClassLoaderResolver("cl", cl);
		SingleClassLoaderResolver clresolver2 = new SingleClassLoaderResolver("cl",
				InterfaceAdaptationTest.class.getClassLoader());

		ClassLoaderResolverRegistry registry = new ClassLoaderResolverRegistry(defclresolver);
		registry.register("cl1", clresolver1);
		registry.register("cl2", clresolver2);

		try (ContentWriterObjectOutput out = new ContentWriterObjectOutput(registry)) {
			out.writeObject(adapted);
			out.drainTo((ByteSink) baos);
		}
		try (ContentReaderObjectInput in = new ContentReaderObjectInput(registry,
				new UnsyncByteArrayInputStream(baos.toByteArrayRegion()))) {
			AdaptTestInterface read = (AdaptTestInterface) in.readObject();
			assertNotEquals(read.getClass(), SerialAdaptTestImpl.class);
			assertTrue(Proxy.isProxyClass(read.getClass()));
		}
		baos.reset();

		try (ContentWriterObjectOutput out = new ContentWriterObjectOutput(registry)) {
			out.writeObject(externaladapted);
			out.drainTo((ByteSink) baos);
		}
		try (ContentReaderObjectInput in = new ContentReaderObjectInput(registry,
				new UnsyncByteArrayInputStream(baos.toByteArrayRegion()))) {
			AdaptTestInterface read = (AdaptTestInterface) in.readObject();
			assertNotEquals(read.getClass(), ExternalAdaptTestImpl.class);
			assertInstanceOf(read, Externalizable.class);
			assertTrue(Proxy.isProxyClass(read.getClass()));
		}
	}

	private void adaptTest() throws InstantiationException, IllegalAccessException, InvocationTargetException,
			NoSuchMethodException, ClassNotFoundException {
		ClassLoader cl = TestUtils.createClassLoaderForClasses(AdaptTestInterface.class, AdaptTestImpl.class,
				AdditionalInterface.class, AdaptTestInterfaceVisitor.class);
		Class<?> remoteclass = Class.forName(AdaptTestImpl.class.getName(), false, cl);

		assertIdentityEquals(AdaptTestImpl.class,
				DataConverterUtils.adaptInterface(this.getClass().getClassLoader(), remoteclass));

		AdaptTestInterface adapted = (AdaptTestInterface) DataConverterUtils
				.adaptInterface(this.getClass().getClassLoader(), ReflectUtils.newInstance(remoteclass));
		AdaptTestImpl localimpl = new AdaptTestImpl();
		assertInstanceOf(adapted, AdditionalInterface.class);
		localimpl.accept(new AdaptTestInterfaceVisitor() {
			@Override
			public void visit(AdaptTestInterface itf) {
				itf.toString();
				assertInstanceOf(itf, AdditionalInterface.class);
			}
		});
		localimpl.acceptDupl(new AdaptTestInterfaceVisitor() {
			@Override
			public void visitDupl(AdaptTestInterface itf) {
				itf.toString();
				assertInstanceOf(itf, AdditionalInterface.class);
			}
		});

		assertTrue(adapted.equals(adapted));
	}

	private void modifiedsTest() throws Exception {
		ClassLoader cl = new MultiDataClassLoader(new PathClassLoaderDataFinder(getWorkingDirectory().resolve("src")));

		Object baseitfimplinstance = Class.forName(BaseItfImpl.class.getName(), false, cl).getConstructor()
				.newInstance();
		assertException(ClassCastException.class, () -> ((BaseItf) baseitfimplinstance).toString());

		BaseItf localbaseitfinstance = (BaseItf) DataConverterUtils.adaptInterface(BaseItf.class.getClassLoader(),
				baseitfimplinstance);
		assertEquals(baseitfimplinstance.toString(), localbaseitfinstance.toString());
		assertEquals(baseitfimplinstance.hashCode(), localbaseitfinstance.hashCode());

		localbaseitfinstance.voidMethod();
		localbaseitfinstance.voidIntMethod(0);
		localbaseitfinstance.onlyLocalDefaultMethod();

		assertEquals(localbaseitfinstance.integerMethod(), 1);
		assertEquals(localbaseitfinstance.numberMethod(), 1L);

		//calling a class parametered method is not supported for adapted interfaces, only if the parameter is null
		BaseItfImpl oneimpl = new BaseItfImpl();

		assertException(UnsupportedOperationException.class, () -> localbaseitfinstance.baseItfImplMethod(oneimpl));
		localbaseitfinstance.baseItfImplMethod(null);

		localbaseitfinstance.baseItfMethod(oneimpl);
		localbaseitfinstance.baseItfReturning();

		localbaseitfinstance.baseItfMethodArray(new BaseItf[] { oneimpl });
		localbaseitfinstance.baseItfMethodArray(new BaseItf[] {});
		localbaseitfinstance.baseItfReturningArray();

		assertTrue(localbaseitfinstance.forward(localbaseitfinstance) == localbaseitfinstance);
		assertTrue(localbaseitfinstance.forward(oneimpl) == oneimpl);

		assertTrue(localbaseitfinstance.objectForward(localbaseitfinstance) == localbaseitfinstance);
		assertTrue(localbaseitfinstance.objectForward(oneimpl) == oneimpl);

		localbaseitfinstance.iterabler().iterator().next().baseItfReturning();
		localbaseitfinstance.iterablerIterabler().iterator().next().iterator().next().baseItfReturning();

		localbaseitfinstance.lister().iterator().next().baseItfReturning();
		localbaseitfinstance.listerLister().iterator().next().iterator().next().baseItfReturning();

		localbaseitfinstance.coller().iterator().next().baseItfReturning();
		localbaseitfinstance.collerColler().iterator().next().iterator().next().baseItfReturning();

		localbaseitfinstance.seter().iterator().next().baseItfReturning();
		localbaseitfinstance.seterSeter().iterator().next().iterator().next().baseItfReturning();

		Entry<BaseItf, BaseItf> e = localbaseitfinstance.maper().entrySet().iterator().next();
		e.getKey().baseItfReturning();
		e.getValue().baseItfReturning();
	}

}
