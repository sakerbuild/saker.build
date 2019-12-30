package testing.saker;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public abstract class SakerTestCase {

	public abstract void runTest(Map<String, String> parameters) throws Throwable;

	@Override
	public String toString() {
		return "Test: " + this.getClass().getName();
	}

	public static String deepToString(Object o) {
		if (o == null) {
			return "null";
		}
		if (o.getClass().isArray()) {
			if (o.getClass().getComponentType().isPrimitive()) {
				StringBuilder sb = new StringBuilder();
				sb.append('[');
				int length = Array.getLength(o);
				for (int i = 0; i < length; i++) {
					sb.append(Array.get(o, i));
					if (i + 1 < length) {
						sb.append(", ");
					}
				}
				sb.append(']');
				return sb.toString();
			}
			return Arrays.deepToString((Object[]) o);
		}
		return o.toString();
	}

	public static RuntimeException fail(String message) {
		throw new AssertionError(message);
	}

	public static RuntimeException fail() {
		throw new AssertionError();
	}

	public static RuntimeException fail(Throwable cause) {
		throw new AssertionError(cause);
	}

	public static RuntimeException fail(String message, Throwable cause) {
		throw new AssertionError(message, cause);
	}

	private static String classOfString(Object o) {
		if (o == null) {
			return "";
		}
		return o.getClass().getName() + ":";
	}

	public static RuntimeException assertionFailed(Object actual, Object expected) throws AssertionError {
		throw new AssertionError("Assertion failed: " + classOfString(actual) + deepToString(actual) + " with expected "
				+ classOfString(expected) + deepToString(expected));
	}

	public static RuntimeException assertionFailed(Object actual, Object expected, String message)
			throws AssertionError {
		throw new AssertionError("Assertion failed: " + classOfString(actual) + deepToString(actual) + " with expected "
				+ classOfString(expected) + deepToString(expected) + (message == null ? "" : " (" + message + ")"));
	}

	public static RuntimeException assertionFailed(Object associatedobject) throws AssertionError {
		throw new AssertionError(
				"Assertion failed: " + classOfString(associatedobject) + deepToString(associatedobject));
	}

	public static RuntimeException assertionFailed(Object associatedobject, String message) throws AssertionError {
		throw new AssertionError("Assertion failed: " + classOfString(associatedobject) + deepToString(associatedobject)
				+ (message == null ? "" : " (" + message + ")"));
	}

	public static void assertIdentityEquals(Object l, Object r) throws AssertionError {
		assertIdentityEquals(l, r, (String) null);
	}

	public static void assertIdentityEquals(Object l, Object r, String message) throws AssertionError {
		assertIdentityEquals(l, r, () -> message);
	}

	public static void assertIdentityEquals(Object l, Object r, Supplier<String> message) throws AssertionError {
		if (l != r) {
			assertionFailed(l, r, message.get());
		}
	}

	public static void assertNotIdentityEquals(Object l, Object r) throws AssertionError {
		assertNotIdentityEquals(l, r, (String) null);
	}

	public static void assertNotIdentityEquals(Object l, Object r, String message) throws AssertionError {
		assertNotIdentityEquals(l, r, () -> message);
	}

	public static void assertNotIdentityEquals(Object l, Object r, Supplier<String> message) throws AssertionError {
		if (l == r) {
			assertionFailed(l, r, message.get());
		}
	}

	public static void assertEquals(Object l, Object r, Supplier<String> message) throws AssertionError {
		if (!Objects.deepEquals(l, r)) {
			assertionFailed(l, r, message.get());
		}
		if (!Objects.deepEquals(r, l)) {
			assertionFailed(r, l, "Equals is not symmetric.");
		}
	}

	public static void assertEquals(Object l, Object r, String message) throws AssertionError {
		assertEquals(l, r, () -> message);
	}

	public static void assertEquals(Object l, Object r) throws AssertionError {
		assertEquals(l, r, (String) null);
	}

	public static void assertNotEquals(Object l, Object r) throws AssertionError {
		if (Objects.deepEquals(l, r)) {
			assertionFailed(l, r);
		}
		if (Objects.deepEquals(r, l)) {
			assertionFailed(r, l, "Equals is not symmetric.");
		}
	}

	public static void assertSameClass(Object l, Object r) throws AssertionError {
		Class<? extends Object> lc = l == null ? null : l.getClass();
		Class<? extends Object> rc = r == null ? null : r.getClass();
		if (lc != rc) {
			assertionFailed("Objects have different classes: " + lc + " - " + rc);
		}
	}

	public static void assertNotSameClass(Object l, Object r) throws AssertionError {
		Class<? extends Object> lc = l == null ? null : l.getClass();
		Class<? extends Object> rc = r == null ? null : r.getClass();
		if (lc == rc) {
			assertionFailed("Objects have same classes: " + lc + " - " + rc);
		}
	}

	public static void assertTrue(boolean bool, String message) throws AssertionError {
		if (!bool) {
			assertionFailed(bool, message);
		}
	}

	public static void assertTrue(boolean bool) throws AssertionError {
		assertTrue(bool, null);
	}

	public static void assertFalse(boolean bool, String message) throws AssertionError {
		if (bool) {
			assertionFailed(bool, message);
		}
	}

	public static void assertFalse(boolean bool) throws AssertionError {
		assertFalse(bool, null);
	}

	public static void assertNull(Object obj, String message) throws AssertionError {
		if (obj != null) {
			assertionFailed(obj, message);
		}
	}

	public static void assertNull(Object obj) throws AssertionError {
		if (obj != null) {
			assertionFailed(obj, "Object is not null.");
		}
	}

	public static void assertNonNull(Object obj, String message) throws AssertionError {
		if (obj == null) {
			assertionFailed(obj, message);
		}
	}

	public static void assertNonNull(Object obj) throws AssertionError {
		if (obj == null) {
			assertionFailed(obj, "Object is null.");
		}
	}

	public static <O, T extends O> void assertInstanceOf(O obj, Class<T> clazz) throws AssertionError {
		if (!clazz.isInstance(obj)) {
			assertionFailed(obj, obj + " is not instance of " + clazz);
		}
	}

	public static <O, T extends O> void assertNotInstanceOf(O obj, Class<T> clazz) throws AssertionError {
		if (clazz.isInstance(obj)) {
			assertionFailed(obj, obj + " is instance of " + clazz);
		}
	}

	public static MapAssertion assertMap(Map<?, ?> map) {
		return new MapAssertion(map);
	}

	public static void assertEmpty(Object[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length > 0) {
			throw new AssertionError("Not empty. (" + Arrays.deepToString(array) + ")");
		}
	}

	public static void assertEmpty(char[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length > 0) {
			throw new AssertionError("Not empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertEmpty(boolean[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length > 0) {
			throw new AssertionError("Not empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertEmpty(byte[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length > 0) {
			throw new AssertionError("Not empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertEmpty(short[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length > 0) {
			throw new AssertionError("Not empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertEmpty(int[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length > 0) {
			throw new AssertionError("Not empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertEmpty(long[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length > 0) {
			throw new AssertionError("Not empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertEmpty(float[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length > 0) {
			throw new AssertionError("Not empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertEmpty(double[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length > 0) {
			throw new AssertionError("Not empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertEmpty(Iterable<?> iterable) throws AssertionError {
		assertNonNull(iterable, "iterable");
		Iterator<?> it = iterable.iterator();
		assertNonNull(it, "iterable.iterator()");
		if (it.hasNext()) {
			throw new AssertionError("Not empty. (" + iterable + ")");
		}
	}

	public static void assertEmpty(Collection<?> coll) throws AssertionError {
		assertNonNull(coll, "coll");
		if (!coll.isEmpty()) {
			throw new AssertionError("Not empty. (" + coll + ")");
		}
	}

	public static void assertEmpty(Map<?, ?> map) throws AssertionError {
		assertNonNull(map, "map");
		if (!map.isEmpty()) {
			throw new AssertionError("Not empty. (" + map + ")");
		}
	}

	public static void assertNotEmpty(Object[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length == 0) {
			throw new AssertionError("Empty. (" + Arrays.deepToString(array) + ")");
		}
	}

	public static void assertNotEmpty(char[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length == 0) {
			throw new AssertionError("Empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertNotEmpty(boolean[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length == 0) {
			throw new AssertionError("Empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertNotEmpty(byte[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length == 0) {
			throw new AssertionError("Empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertNotEmpty(short[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length == 0) {
			throw new AssertionError("Empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertNotEmpty(int[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length == 0) {
			throw new AssertionError("Empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertNotEmpty(long[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length == 0) {
			throw new AssertionError("Empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertNotEmpty(float[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length == 0) {
			throw new AssertionError("Empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertNotEmpty(double[] array) throws AssertionError {
		assertNonNull(array, "array");
		if (array.length == 0) {
			throw new AssertionError("Empty. (" + Arrays.toString(array) + ")");
		}
	}

	public static void assertNotEmpty(Iterable<?> iterable) throws AssertionError {
		assertNonNull(iterable, "iterable");
		Iterator<?> it = iterable.iterator();
		assertNonNull(it, "iterable.iterator()");
		if (!it.hasNext()) {
			throw new AssertionError("Empty. (" + iterable + ")");
		}
	}

	public static void assertNotEmpty(Collection<?> coll) throws AssertionError {
		assertNonNull(coll, "coll");
		if (coll.isEmpty()) {
			throw new AssertionError("Empty. (" + coll + ")");
		}
	}

	public static void assertNotEmpty(Map<?, ?> map) throws AssertionError {
		assertNonNull(map, "map");
		if (map.isEmpty()) {
			throw new AssertionError("Empty. (" + map + ")");
		}
	}

	@SuppressWarnings("unchecked")
	public static <T extends Throwable> T assertException(Class<T> exceptionclass, ExceptionAssertion runner)
			throws AssertionError {
		try {
			runner.run();
		} catch (Throwable e) {
			if (exceptionclass.isInstance(e)) {
				return (T) e;
			}
			throw new AssertionError(
					"Failed to catch exception: " + exceptionclass.getName() + " caught: " + e.getClass().getName(), e);
		}
		throw new AssertionError("Failed to catch exception: " + exceptionclass.getName() + " caught no exceptions.");
	}

	public static Throwable assertException(String exceptionclassname, ExceptionAssertion runner)
			throws AssertionError {
		Throwable caught = null;
		try {
			runner.run();
		} catch (Throwable e) {
			if (hasSuperTypeName(e.getClass(), exceptionclassname)) {
				return e;
			}
			caught = e;
		}
		throw new AssertionError("Failed to catch exception: " + exceptionclassname
				+ (caught == null ? "" : " caught: " + caught.getClass().getName()), caught);
	}

	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <T> Set<T> setOf(T... objects) {
		Set<T> result = new HashSet<>(objects.length * 2);
		for (T o : objects) {
			result.add(o);
		}
		return result;
	}

	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <T> List<T> listOf(T... objects) {
		ArrayList<T> result = new ArrayList<>(objects.length);
		for (T o : objects) {
			result.add(o);
		}
		return result;
	}

	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <T> T[] arrayOf(T... objects) {
		return objects;
	}

	public static ExceptionAssertion unwrapInvocationTargetException(ExceptionAssertion r) {
		return () -> {
			try {
				r.run();
			} catch (InvocationTargetException e) {
				throw e.getTargetException();
			}
		};
	}

	@FunctionalInterface
	public interface ExceptionAssertion {
		public void run() throws Throwable;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static class MapAssertion {
		private static final Object DOES_NOT_CONTAIN_DEFAULT = new Object();

		private Map map;
		private Set<Object> testedItems = Collections.newSetFromMap(new IdentityHashMap<>());

		public MapAssertion(Map<?, ?> map) {
			assertNonNull(map, "map");
			this.map = map;
		}

		public MapAssertion containsKey(Object k) throws AssertionError {
			if (!map.containsKey(k)) {
				throw new AssertionError("Map doesn't contain key: " + k);
			}
			testedItems.add(k);
			return this;
		}

		public MapAssertion contains(Object k, Object v) throws AssertionError {
			Object got = map.getOrDefault(k, DOES_NOT_CONTAIN_DEFAULT);
			if (got == DOES_NOT_CONTAIN_DEFAULT) {
				throw new AssertionError("Map doesn't contain key: " + k + " in " + map);
			}
			assertEquals(got, v, "Different value for key: " + k);
			testedItems.add(k);
			return this;
		}

		public void noRemaining() throws AssertionError {
			if (testedItems.size() == map.size()) {
				return;
			}
			Map<?, ?> remain = new IdentityHashMap<>(map);
			remain.keySet().removeAll(testedItems);
			throw new AssertionError("Remaining items: " + remain);
		}
	}

	private static boolean hasSuperTypeName(Class<?> clazz, String cname) {
		if (clazz == null) {
			return false;
		}
		if (clazz.getName().equals(cname)) {
			return true;
		}
		if (hasSuperTypeName(clazz.getSuperclass(), cname)) {
			return true;
		}
		for (Class<?> itf : clazz.getInterfaces()) {
			if (hasSuperTypeName(itf, cname)) {
				return true;
			}
		}
		return false;
	}
}
