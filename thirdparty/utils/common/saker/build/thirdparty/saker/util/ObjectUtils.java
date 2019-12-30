package saker.build.thirdparty.saker.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntBiFunction;

import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.TriConsumer;
import saker.build.thirdparty.saker.util.function.TriPredicate;
import saker.build.thirdparty.saker.util.io.StreamUtils;

/**
 * Class containing general utility functions for handling various objects.
 */
public class ObjectUtils {
	/**
	 * Singleton instance of an empty <code>byte</code> array.
	 */
	public static final byte[] EMPTY_BYTE_ARRAY = {};
	/**
	 * Singleton instance of an empty <code>short</code> array.
	 */
	public static final short[] EMPTY_SHORT_ARRAY = {};
	/**
	 * Singleton instance of an empty <code>int</code> array.
	 */
	public static final int[] EMPTY_INT_ARRAY = {};
	/**
	 * Singleton instance of an empty <code>long</code> array.
	 */
	public static final long[] EMPTY_LONG_ARRAY = {};
	/**
	 * Singleton instance of an empty <code>float</code> array.
	 */
	public static final float[] EMPTY_FLOAT_ARRAY = {};
	/**
	 * Singleton instance of an empty <code>double</code> array.
	 */
	public static final double[] EMPTY_DOUBLE_ARRAY = {};
	/**
	 * Singleton instance of an empty <code>char</code> array.
	 */
	public static final char[] EMPTY_CHAR_ARRAY = {};
	/**
	 * Singleton instance of an empty <code>boolean</code> array.
	 */
	public static final boolean[] EMPTY_BOOLEAN_ARRAY = {};
	/**
	 * Singleton instance of an empty {@link Object} array.
	 */
	public static final Object[] EMPTY_OBJECT_ARRAY = {};
	/**
	 * Singleton instance of an empty {@link String} array.
	 */
	public static final String[] EMPTY_STRING_ARRAY = {};
	/**
	 * Singleton instance of an empty {@link Class} array.
	 */
	public static final Class<?>[] EMPTY_CLASS_ARRAY = {};
	/**
	 * Singleton instance of an empty {@link Throwable} array.
	 */
	public static final Throwable[] EMPTY_THROWABLE_ARRAY = {};
	/**
	 * Singleton instance of an empty {@link StackTraceElement} array.
	 */
	public static final StackTraceElement[] EMPTY_STACK_TRACE_ELEMENT_ARRAY = {};

	/**
	 * Checks if the argument string is <code>null</code> or {@linkplain String#isEmpty() empty}.
	 * 
	 * @param str
	 *            The string.
	 * @return <code>true</code> if the argument is <code>null</code> or empty.
	 */
	public static boolean isNullOrEmpty(String str) {
		return str == null || str.isEmpty();
	}

	/**
	 * Checks if the argument character sequence is <code>null</code> or empty.
	 * <p>
	 * The emptiness is determined using the {@linkplain CharSequence#length() length} of the sequence.
	 * 
	 * @param str
	 *            The char sequence.
	 * @return <code>true</code> if the argument is <code>null</code> or empty.
	 */
	public static boolean isNullOrEmpty(CharSequence str) {
		return str == null || str.length() == 0;
	}

	/**
	 * Checks if the argument string is <code>null</code> or {@linkplain Map#isEmpty() empty}.
	 * 
	 * @param map
	 *            The map.
	 * @return <code>true</code> if the argument is <code>null</code> or empty.
	 */
	public static boolean isNullOrEmpty(Map<?, ?> map) {
		return map == null || map.isEmpty();
	}

	/**
	 * Checks if the argument string is <code>null</code> or {@linkplain Collection#isEmpty() empty}.
	 * 
	 * @param collection
	 *            The collection.
	 * @return <code>true</code> if the argument is <code>null</code> or empty.
	 */
	public static boolean isNullOrEmpty(Collection<?> collection) {
		return collection == null || collection.isEmpty();
	}

	/**
	 * Checks if the argument iterable is <code>null</code> or empty.
	 * <p>
	 * This method includes creating an iterator for the given iterable.
	 * 
	 * @param iterable
	 *            The iterable.
	 * @return <code>true</code> if the argument is <code>null</code> or empty.
	 */
	public static boolean isNullOrEmpty(Iterable<?> iterable) {
		return iterable == null || !iterable.iterator().hasNext();
	}

	/**
	 * Checks if the argument array is <code>null</code>, or empty (i.e. has a length of 0).
	 * 
	 * @param array
	 *            The array.
	 * @return <code>true</code> if the argument is <code>null</code> or empty.
	 */
	public static boolean isNullOrEmpty(char[] array) {
		return array == null || array.length == 0;
	}

	/**
	 * Checks if the argument array is <code>null</code>, or empty (i.e. has a length of 0).
	 * 
	 * @param array
	 *            The array.
	 * @return <code>true</code> if the argument is <code>null</code> or empty.
	 */
	public static boolean isNullOrEmpty(boolean[] array) {
		return array == null || array.length == 0;
	}

	/**
	 * Checks if the argument array is <code>null</code>, or empty (i.e. has a length of 0).
	 * 
	 * @param array
	 *            The array.
	 * @return <code>true</code> if the argument is <code>null</code> or empty.
	 */
	public static boolean isNullOrEmpty(byte[] array) {
		return array == null || array.length == 0;
	}

	/**
	 * Checks if the argument array is <code>null</code>, or empty (i.e. has a length of 0).
	 * 
	 * @param array
	 *            The array.
	 * @return <code>true</code> if the argument is <code>null</code> or empty.
	 */
	public static boolean isNullOrEmpty(short[] array) {
		return array == null || array.length == 0;
	}

	/**
	 * Checks if the argument array is <code>null</code>, or empty (i.e. has a length of 0).
	 * 
	 * @param array
	 *            The array.
	 * @return <code>true</code> if the argument is <code>null</code> or empty.
	 */
	public static boolean isNullOrEmpty(int[] array) {
		return array == null || array.length == 0;
	}

	/**
	 * Checks if the argument array is <code>null</code>, or empty (i.e. has a length of 0).
	 * 
	 * @param array
	 *            The array.
	 * @return <code>true</code> if the argument is <code>null</code> or empty.
	 */
	public static boolean isNullOrEmpty(long[] array) {
		return array == null || array.length == 0;
	}

	/**
	 * Checks if the argument array is <code>null</code>, or empty (i.e. has a length of 0).
	 * 
	 * @param array
	 *            The array.
	 * @return <code>true</code> if the argument is <code>null</code> or empty.
	 */
	public static boolean isNullOrEmpty(float[] array) {
		return array == null || array.length == 0;
	}

	/**
	 * Checks if the argument array is <code>null</code>, or empty (i.e. has a length of 0).
	 * 
	 * @param array
	 *            The array.
	 * @return <code>true</code> if the argument is <code>null</code> or empty.
	 */
	public static boolean isNullOrEmpty(double[] array) {
		return array == null || array.length == 0;
	}

	/**
	 * Checks if the argument array is <code>null</code>, or empty (i.e. has a length of 0).
	 * 
	 * @param array
	 *            The array.
	 * @return <code>true</code> if the argument is <code>null</code> or empty.
	 */
	public static boolean isNullOrEmpty(Object[] array) {
		return array == null || array.length == 0;
	}

	/**
	 * Checks if the argument optional is <code>null</code>, or contains no value.
	 * 
	 * @param optional
	 *            The optional.
	 * @return <code>true</code> if the argument is <code>null</code>, or {@link Optional#isPresent()} is
	 *             <code>false</code>.
	 */
	public static boolean isNullOrEmpty(Optional<?> optional) {
		return optional == null || !optional.isPresent();
	}

	/**
	 * Checks if the first argument is <code>null</code> and if it is, returns the default value.
	 * 
	 * @param <T>
	 *            The type of the arguments.
	 * @param first
	 *            The object to check nullability for.
	 * @param defaultvalue
	 *            The object to return if the object is <code>null</code>.
	 * @return The first argument if it is non-<code>null</code>, else the default value.
	 */
	public static <T> T nullDefault(T first, T defaultvalue) {
		if (first == null) {
			return defaultvalue;
		}
		return first;
	}

	/**
	 * Checks if the first argument is <code>null</code> and if it is, returns the value supplied by the default
	 * supplier.
	 * 
	 * @param <T>
	 *            The type of the arguments.
	 * @param test
	 *            The object to test <code>null</code> value for.
	 * @param defaultvalue
	 *            The supplier to compute the return value if the test object is <code>null</code>.
	 * @return The test object is non-<code>null</code>, else the computed value by the supplier.
	 */
	public static <T> T nullDefault(T test, Supplier<? extends T> defaultvalue) {
		return test == null ? defaultvalue.get() : test;
	}

	/**
	 * Checks if the argument is non-<code>null</code> and represents the <code>true</code> {@link Boolean}.
	 * 
	 * @param b
	 *            The boolean.
	 * @return <code>true</code> if the argument is the <code>true</code> boolean.
	 */
	public static boolean isTrue(Boolean b) {
		return b != null && b.booleanValue();
	}

	/**
	 * Checks if the argument is non-<code>null</code> and represents the <code>false</code> {@link Boolean}.
	 * 
	 * @param b
	 *            The boolean.
	 * @return <code>true</code> if the argument is the <code>false</code> boolean.
	 */
	public static boolean isFalse(Boolean b) {
		return b != null && !b.booleanValue();
	}

	/**
	 * Checks if the argument boxed <code>boolean</code> is <code>null</code> and returns the default value if it is.
	 * 
	 * @param value
	 *            The <code>boolean</code>.
	 * @param defaultvalue
	 *            The default value to return if the boxed primitive is <code>null</code>.
	 * @return The value of the {@link Boolean} argument if non-<code>null</code>, else the default argument.
	 */
	public static boolean defaultize(Boolean value, boolean defaultvalue) {
		if (value == null) {
			return defaultvalue;
		}
		return value;
	}

	/**
	 * Checks if the argument boxed <code>byte</code> is <code>null</code> and returns the default value if it is.
	 * 
	 * @param value
	 *            The <code>byte</code>.
	 * @param defaultvalue
	 *            The default value to return if the boxed primitive is <code>null</code>.
	 * @return The value of the {@link Byte} argument if non-<code>null</code>, else the default argument.
	 */
	public static byte defaultize(Byte value, byte defaultvalue) {
		if (value == null) {
			return defaultvalue;
		}
		return value;
	}

	/**
	 * Checks if the argument boxed <code>short</code> is <code>null</code> and returns the default value if it is.
	 * 
	 * @param value
	 *            The <code>short</code>.
	 * @param defaultvalue
	 *            The default value to return if the boxed primitive is <code>null</code>.
	 * @return The value of the {@link Short} argument if non-<code>null</code>, else the default argument.
	 */
	public static short defaultize(Short value, short defaultvalue) {
		if (value == null) {
			return defaultvalue;
		}
		return value;
	}

	/**
	 * Checks if the argument boxed <code>int</code> is <code>null</code> and returns the default value if it is.
	 * 
	 * @param value
	 *            The <code>int</code>.
	 * @param defaultvalue
	 *            The default value to return if the boxed primitive is <code>null</code>.
	 * @return The value of the {@link Integer} argument if non-<code>null</code>, else the default argument.
	 */
	public static int defaultize(Integer value, int defaultvalue) {
		if (value == null) {
			return defaultvalue;
		}
		return value;
	}

	/**
	 * Checks if the argument boxed <code>long</code> is <code>null</code> and returns the default value if it is.
	 * 
	 * @param value
	 *            The <code>long</code>.
	 * @param defaultvalue
	 *            The default value to return if the boxed primitive is <code>null</code>.
	 * @return The value of the {@link Long} argument if non-<code>null</code>, else the default argument.
	 */
	public static long defaultize(Long value, long defaultvalue) {
		if (value == null) {
			return defaultvalue;
		}
		return value;
	}

	/**
	 * Checks if the argument boxed <code>float</code> is <code>null</code> and returns the default value if it is.
	 * 
	 * @param value
	 *            The <code>float</code>.
	 * @param defaultvalue
	 *            The default value to return if the boxed primitive is <code>null</code>.
	 * @return The value of the {@link Float} argument if non-<code>null</code>, else the default argument.
	 */
	public static float defaultize(Float value, float defaultvalue) {
		if (value == null) {
			return defaultvalue;
		}
		return value;
	}

	/**
	 * Checks if the argument boxed <code>double</code> is <code>null</code> and returns the default value if it is.
	 * 
	 * @param value
	 *            The <code>double</code>.
	 * @param defaultvalue
	 *            The default value to return if the boxed primitive is <code>null</code>.
	 * @return The value of the {@link Double} argument if non-<code>null</code>, else the default argument.
	 */
	public static double defaultize(Double value, double defaultvalue) {
		if (value == null) {
			return defaultvalue;
		}
		return value;
	}

	/**
	 * Checks if the argument boxed <code>char</code> is <code>null</code> and returns the default value if it is.
	 * 
	 * @param value
	 *            The <code>char</code>.
	 * @param defaultvalue
	 *            The default value to return if the boxed primitive is <code>null</code>.
	 * @return The value of the {@link Character} argument if non-<code>null</code>, else the default argument.
	 */
	public static char defaultize(Character value, char defaultvalue) {
		if (value == null) {
			return defaultvalue;
		}
		return value;
	}

	/**
	 * Counts the number of <code>null</code> objects in the argument array.
	 * <p>
	 * If the argument itself is <code>null</code>, 0 is returned.
	 * 
	 * @param objects
	 *            The objects to examine.
	 * @return The number of <code>null</code> elements found.
	 */
	public static int nullObjectCount(Object... objects) {
		if (objects == null) {
			return 0;
		}
		int c = 0;
		for (Object o : objects) {
			if (o == null) {
				++c;
			}
		}
		return c;
	}

	/**
	 * Counts the number of <code>null</code> objects in the argument iterable.
	 * <p>
	 * If the argument itself is <code>null</code>, 0 is returned.
	 * 
	 * @param objects
	 *            The iterable of objects to examine.
	 * @return The number of <code>null</code> elements found.
	 */
	public static int nullObjectCount(Iterable<?>... objects) {
		if (objects == null) {
			return 0;
		}
		int c = 0;
		for (Object o : objects) {
			if (o == null) {
				++c;
			}
		}
		return c;
	}

	/**
	 * Counts the number of non-<code>null</code> objects in the argument array.
	 * <p>
	 * If the argument itself is <code>null</code>, 0 is returned.
	 * 
	 * @param objects
	 *            The objects to examine.
	 * @return The number of non-<code>null</code> elements found.
	 */
	public static int nonNullObjectCount(Object... objects) {
		if (objects == null) {
			return 0;
		}
		int c = 0;
		for (Object o : objects) {
			if (o != null) {
				++c;
			}
		}
		return c;
	}

	/**
	 * Counts the number of non-<code>null</code> objects in the argument iterable.
	 * <p>
	 * If the argument itself is <code>null</code>, 0 is returned.
	 * 
	 * @param objects
	 *            The iterable of objects to examine.
	 * @return The number of non-<code>null</code> elements found.
	 */
	public static int nonNullObjectCount(Iterable<?> objects) {
		if (objects == null) {
			return 0;
		}
		int c = 0;
		for (Object o : objects) {
			if (o != null) {
				++c;
			}
		}
		return c;
	}

	/**
	 * Checks if there are any non-<code>null</code> values among the argument.
	 * <p>
	 * If the argument array is <code>null</code>, <code>false</code> is returned.
	 * 
	 * @param objects
	 *            The objects to examine.
	 * @return <code>true</code> if there's at least one non-<code>null</code> element in the argument.
	 */
	public static boolean hasNonNull(Object... objects) {
		if (objects == null) {
			return false;
		}
		for (Object o : objects) {
			if (o != null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if there are any non-<code>null</code> values among the argument.
	 * <p>
	 * If the argument iterable is <code>null</code>, <code>false</code> is returned.
	 * 
	 * @param objects
	 *            The objects to examine.
	 * @return <code>true</code> if there's at least one non-<code>null</code> element in the argument.
	 */
	public static boolean hasNonNull(Iterable<?> objects) {
		if (objects == null) {
			return false;
		}
		for (Object o : objects) {
			if (o != null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if there are any <code>null</code> values among the argument.
	 * <p>
	 * If the argument array is <code>null</code>, <code>false</code> is returned.
	 * 
	 * @param objects
	 *            The objects to examine.
	 * @return <code>true</code> if there's at least one <code>null</code> element in the argument.
	 */
	public static boolean hasNull(Object... objects) {
		if (objects == null) {
			return false;
		}
		for (Object o : objects) {
			if (o == null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if there are any <code>null</code> values among the argument.
	 * <p>
	 * If the argument iterable is <code>null</code>, <code>false</code> is returned.
	 * 
	 * @param objects
	 *            The objects to examine.
	 * @return <code>true</code> if there's at least one <code>null</code> element in the argument.
	 */
	public static boolean hasNull(Iterable<?> objects) {
		if (objects == null) {
			return false;
		}
		for (Object o : objects) {
			if (o == null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Gets the referenced object by the argument reference, if it is non-<code>null</code>.
	 * <p>
	 * This method simply calls {@link Reference#get()} if the reference is non-<code>null</code>.
	 * 
	 * @param <T>
	 *            The type of the referenced object.
	 * @param ref
	 *            The reference.
	 * @return The referenced object, or <code>null</code> if the reference is <code>null</code>.
	 */
	public static <T> T getReference(Reference<? extends T> ref) {
		return ref == null ? null : ref.get();
	}

	/**
	 * Gets the value from a supplier, if it is non-<code>null</code>.
	 * <p>
	 * This method simply calls {@link Supplier#get()} if the supplier is non-<code>null</code>.
	 * 
	 * @param <T>
	 *            The type of the supplied value.
	 * @param supplier
	 *            The supplier.
	 * @return The result of {@link Supplier#get()} or <code>null</code> if the supplier is <code>null</code>.
	 */
	public static <T> T getSupplier(Supplier<? extends T> supplier) {
		return supplier == null ? null : supplier.get();
	}

	/**
	 * Gets the value of an optional if it is non-<code>null</code>, and is present.
	 * 
	 * @param <T>
	 *            The value type.
	 * @param optional
	 *            The optional.
	 * @return The value of the optional, or <code>null</code> if it is not present, or the optional is
	 *             <code>null</code>.
	 * @see Optional#isPresent()
	 */
	public static <T> T getOptional(Optional<? extends T> optional) {
		return optional == null ? null : optional.orElse(null);
	}

	/**
	 * Gets the value mapped to the given key in a map, if the map is non-<code>null</code>.
	 * <p>
	 * This method simply calls {@link Map#get(Object)} if the map is non-<code>null</code>.
	 * 
	 * @param <V>
	 *            The value type of the map.
	 * @param map
	 *            The map.
	 * @param key
	 *            The key.
	 * @return The value mapped to the key, or <code>null</code> if the map is <code>null</code>, no mapping is present,
	 *             or the key is mapped to <code>null</code>.
	 */
	public static <V> V getMapValue(Map<?, V> map, Object key) {
		return map == null ? null : map.get(key);
	}

	/**
	 * Gets the value mapped to the given key in a map, or the default if the key is not present in the map or if the
	 * map is non-<code>null</code>.
	 * <p>
	 * This method simply calls {@link Map#getOrDefault(Object, Object)} if the map is non-<code>null</code>. If it's
	 * <code>null</code>, the default value is returned.
	 * 
	 * @param <V>
	 *            The value type of the map.
	 * @param map
	 *            The map.
	 * @param key
	 *            The key.
	 * @param defaultval
	 *            The default value.
	 * @return The value mapped to the key, or the default value if the map is <code>null</code> or no mapping is
	 *             present.
	 */
	public static <V> V getMapValueOrDefault(Map<?, V> map, Object key, V defaultval) {
		return map == null ? defaultval : map.getOrDefault(key, defaultval);
	}

	/**
	 * Gets the element of a list at the specified index.
	 * <p>
	 * If the list is <code>null</code>, or the index is out of range, <code>null</code> is returned.
	 * <p>
	 * If the list is concurrently modified while this method is called, the possible {@link IndexOutOfBoundsException}
	 * is relayed to the caller. (This is usually not an issue.)
	 * 
	 * @param <E>
	 *            The element type.
	 * @param list
	 *            The list to get the element of.
	 * @param index
	 *            The index to get the element at.
	 * @return The element at the given index or <code>null</code> if there's none.
	 */
	public static <E> E getListElement(List<? extends E> list, int index) {
		return list == null || index < 0 || index >= list.size() ? null : list.get(index);
	}

	/**
	 * Gets the entry in a navigable map that has the specified key.
	 * <p>
	 * The entry is located by taking the {@linkplain NavigableMap#floorEntry(Object) floor entry} for the key, and
	 * comparing the key of the resulted entry to the argument. If they match, it is returned.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map.
	 * @param key
	 *            The entry key to search for.
	 * @return The found entry or <code>null</code> if the map is <code>null</code>, or not found.
	 */
	public static <K, V> Entry<K, V> getEntry(NavigableMap<K, V> map, K key) {
		if (map == null) {
			return null;
		}
		Entry<K, V> entry = map.floorEntry(key);
		if (entry == null) {
			return null;
		}
		if (getComparator(map).compare(entry.getKey(), key) == 0) {
			return entry;
		}
		return null;
	}

	/**
	 * Gets the key that is present in the navigable map and equals to the specified key argument.
	 * <p>
	 * The key is located by resolving the {@linkplain NavigableMap#floorKey(Object) floor key} for the argument key,
	 * and comparing it to the argument. If they match, it is returned.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param map
	 *            The map.
	 * @param key
	 *            The key to search for.
	 * @return The found key or <code>null</code> if the map is <code>null</code>, or not found.
	 */
	public static <K> K getExactKey(NavigableMap<K, ?> map, K key) {
		if (map == null) {
			return null;
		}
		K got = map.floorKey(key);
		if (got == null) {
			return null;
		}
		if (getComparator(map).compare(got, key) == 0) {
			return got;
		}
		return null;
	}

	/**
	 * Validation method for requiring that the argument iterable contains no <code>null</code> elements.
	 * 
	 * @param <E>
	 *            The type of the elements.
	 * @param <C>
	 *            The type of the iterable.
	 * @param iterable
	 *            The iterable.
	 * @return The argument iterable.
	 * @throws NullPointerException
	 *             If the argument or any elements are<code>null</code>.
	 */
	public static <E, C extends Iterable<E>> C requireNonNullElements(C iterable) throws NullPointerException {
		Objects.requireNonNull(iterable, "iterable");
		for (Object o : iterable) {
			if (o == null) {
				throw new NullPointerException("null element found.");
			}
		}
		return iterable;
	}

	/**
	 * Validation method for requiring that the argument array contains no <code>null</code> elements.
	 * 
	 * @param <E>
	 *            The type of the elements.
	 * @param objects
	 *            The array.
	 * @return The argument array.
	 * @throws NullPointerException
	 *             If the array or any elements are <code>null</code>.
	 */
	public static <E> E[] requireNonNullElements(E[] objects) throws NullPointerException {
		Objects.requireNonNull(objects, "objects");
		for (int i = 0; i < objects.length; i++) {
			Object o = objects[i];
			if (o == null) {
				throw new NullPointerException("null element found. (index " + i + ")");
			}
		}
		return objects;
	}

	/**
	 * Validation method for requiring that the argument map contains no <code>null</code> values.
	 * <p>
	 * The keys of the map is not examined.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param <M>
	 *            The map type.
	 * @param map
	 *            The map.
	 * @return The argument map.
	 * @throws NullPointerException
	 *             If the map, the entries, or any value is <code>null</code>.
	 */
	public static <K, V, M extends Map<K, V>> M requireNonNullValues(M map) throws NullPointerException {
		Objects.requireNonNull(map, "map");
		for (Entry<?, ?> entry : map.entrySet()) {
			Objects.requireNonNull(entry, "entry");
			if (entry.getValue() == null) {
				throw new NullPointerException("null value found for key: " + entry.getKey());
			}
		}
		return map;
	}

	/**
	 * Validation method for requiring that the argument map contains no <code>null</code> keys or values.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param <M>
	 *            The map type.
	 * @param map
	 *            The map.
	 * @return The argument map.
	 * @throws NullPointerException
	 *             If the map, the entries, the keys or values are <code>null</code>.
	 */
	public static <K, V, M extends Map<K, V>> M requireNonNullEntryKeyValues(M map) throws NullPointerException {
		Objects.requireNonNull(map, "map");
		for (Entry<?, ?> entry : map.entrySet()) {
			Objects.requireNonNull(entry, "entry");
			if (entry.getKey() == null) {
				throw new NullPointerException("null key found for value: " + entry.getValue());
			}
			if (entry.getValue() == null) {
				throw new NullPointerException("null value found for key: " + entry.getKey());
			}
		}
		return map;
	}

	/**
	 * Gets the comparator for a sorted map.
	 * <p>
	 * If the map is sorted by natural order (i.e. {@link SortedMap#comparator()} returns <code>null</code>),
	 * {@link Comparator#naturalOrder()} is returned.
	 * 
	 * @param <K>
	 *            The key type of the map.
	 * @param map
	 *            The sorted map.
	 * @return The comparator.
	 * @throws NullPointerException
	 *             If the map is <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <K> Comparator<? super K> getComparator(SortedMap<K, ?> map) throws NullPointerException {
		Objects.requireNonNull(map, "map");
		Comparator<? super K> cmp = map.comparator();
		if (cmp == null) {
			return (Comparator<? super K>) Comparator.naturalOrder();
		}
		return cmp;
	}

	/**
	 * Gets the comparator for a sorted set.
	 * <p>
	 * If the set is sorted by natural order (i.e. {@link SortedSet#comparator()} returns <code>null</code>),
	 * {@link Comparator#naturalOrder()} is returned.
	 * 
	 * @param <E>
	 *            The element type of the set.
	 * @param set
	 *            The sorted set.
	 * @return The comparator.
	 * @throws NullPointerException
	 *             If the set is <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <E> Comparator<? super E> getComparator(SortedSet<E> set) throws NullPointerException {
		Objects.requireNonNull(set, "set");
		Comparator<? super E> cmp = set.comparator();
		if (cmp == null) {
			return (Comparator<? super E>) Comparator.naturalOrder();
		}
		return cmp;
	}

	/**
	 * Checks if the argument comparator represents the natural order.
	 * 
	 * @param comparator
	 *            The comparator
	 * @return <code>true</code> if the argument comparator is <code>null</code> or equals to the
	 *             {@linkplain Comparator#naturalOrder() natural order comparator}.
	 */
	public static boolean isNaturalOrder(Comparator<?> comparator) {
		return comparator == null || comparator.equals(Comparator.naturalOrder());
	}

	/**
	 * Comparator function that sorts non-<code>null</code> arguments by natural order, else the <code>null</code>
	 * values first.
	 * 
	 * @param <T>
	 *            The type of the compared objects.
	 * @param l
	 *            The left object.
	 * @param r
	 *            The right object.
	 * @return The comparison result.
	 * @see Comparator#compare(Object, Object)
	 * @see Functionals#nullsFirstNaturalComparator()
	 */
	public static <T extends Comparable<? super T>> int compareNullsFirst(T l, T r) {
		if (l == r) {
			return 0;
		}
		if (l == null) {
			return -1;
		}
		if (r == null) {
			return 1;
		}
		return l.compareTo(r);
	}

	/**
	 * Comparator function that sorts non-<code>null</code> arguments by natural order, else the <code>null</code>
	 * values last.
	 * 
	 * @param l
	 *            The left object.
	 * @param r
	 *            The right object.
	 * @return The comparison result.
	 * @see Comparator#compare(Object, Object)
	 * @see Functionals#nullsLastNaturalComparator()
	 */
	public static <T extends Comparable<? super T>> int compareNullsLast(T l, T r) {
		if (l == r) {
			return 0;
		}
		if (l == null) {
			return 1;
		}
		if (r == null) {
			return -1;
		}
		return l.compareTo(r);
	}

	/**
	 * Validation method for checking if the comparator represents the natural order.
	 * 
	 * @param <C>
	 *            The comparator type.
	 * @param comparator
	 *            The comparator.
	 * @return The comparator.
	 * @throws IllegalArgumentException
	 *             If the comparator does not equal the natural order.
	 * @see #isNaturalOrder(Comparator)
	 */
	public static <C extends Comparator<?>> C requireNaturalOrder(C comparator) throws IllegalArgumentException {
		if (!isNaturalOrder(comparator)) {
			throw new IllegalArgumentException("Not sorted by natural order.");
		}
		return comparator;
	}

	/**
	 * Validation method for checking if a sorted set is ordered by the natural order.
	 * 
	 * @param <S>
	 *            The set type.
	 * @param set
	 *            The set.
	 * @return The argument set.
	 * @throws IllegalArgumentException
	 *             If the set is not ordered by natural order.
	 * @throws NullPointerException
	 *             If the set is <code>null</code>.
	 * @see #requireNaturalOrder(Comparator)
	 */
	public static <S extends SortedSet<?>> S requireNaturalOrder(S set)
			throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(set, "set");
		requireNaturalOrder(set.comparator());
		return set;
	}

	/**
	 * Validation method for checking if a sorted map is ordered by the natural order.
	 * 
	 * @param <M>
	 *            The map type.
	 * @param map
	 *            The map.
	 * @return The argument map.
	 * @throws IllegalArgumentException
	 *             If the map is not ordered by natural order.
	 * @throws NullPointerException
	 *             If the map is <code>null</code>.
	 * @see #requireNaturalOrder(Comparator)
	 */
	public static <M extends SortedMap<?, ?>> M requireNaturalOrder(M map)
			throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(map, "map");
		requireNaturalOrder(map.comparator());
		return map;
	}

	/**
	 * Validation method for requiring that the argument sorted set is sorted by the given comparator.
	 * <p>
	 * The comparators of the set and the argument are compared by equality. If the comparator instances doesn't
	 * implement {@link Object#equals(Object)} properly, this method will most likely fail.
	 * 
	 * @param <S>
	 *            The set type.
	 * @param set
	 *            The set.
	 * @param comparator
	 *            The compare to check for or <code>null</code> to require the natural order.
	 * @return The argument set.
	 * @throws IllegalArgumentException
	 *             If the argument set is not sorted by the given comparator.
	 * @throws NullPointerException
	 *             If the set is <code>null</code>.
	 */
	public static <S extends SortedSet<?>> S requireComparator(S set, Comparator<?> comparator)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(set, "set");
		Comparator<?> setcomp = set.comparator();
		requireSameComparators(setcomp, comparator);
		return set;
	}

	/**
	 * Validation method for requiring that the argument sorted map is sorted by the given comparator.
	 * <p>
	 * The comparators of the map and the argument are compared by equality. If the comparator instances doesn't
	 * implement {@link Object#equals(Object)} properly, this method will most likely fail.
	 * 
	 * @param <M>
	 *            The map type.
	 * @param map
	 *            The map.
	 * @param comparator
	 *            The compare to check for or <code>null</code> to require the natural order.
	 * @return The argument map.
	 * @throws IllegalArgumentException
	 *             If the argument map is not sorted by the given comparator.
	 * @throws NullPointerException
	 *             If the map is <code>null</code>.
	 */
	public static <M extends SortedMap<?, ?>> M requireComparator(M map, Comparator<?> comparator)
			throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(map, "map");
		Comparator<?> mapcomp = map.comparator();
		requireSameComparators(mapcomp, comparator);
		return map;
	}

	/**
	 * Validation method for ensuring that two sorted sets have the same order.
	 * <p>
	 * The comparators of the sorted sets are compared by equality.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param firstset
	 *            The first set.
	 * @param secondset
	 *            The second set.
	 * @return The comparator that defines their orders. Never <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the comparators are different.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <E> Comparator<? super E> requireSameComparators(SortedSet<E> firstset, SortedSet<?> secondset)
			throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(firstset, "first set");
		Objects.requireNonNull(secondset, "second set");
		Comparator<? super E> cmp1 = firstset.comparator();
		Comparator<?> cmp2 = secondset.comparator();
		return requireSameComparators(cmp1, cmp2);
	}

	/**
	 * Validation method for ensuring that two sorted maps have the same order.
	 * <p>
	 * The comparators of the sorted maps are compared by equality.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param firstmap
	 *            The first map.
	 * @param secondmap
	 *            The second map.
	 * @return The comparator that defines their orders. Never <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the comparators are different.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <K> Comparator<? super K> requireSameComparators(SortedMap<K, ?> firstmap, SortedMap<?, ?> secondmap)
			throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(firstmap, "first map");
		Objects.requireNonNull(secondmap, "second map");
		Comparator<? super K> cmp1 = firstmap.comparator();
		Comparator<?> cmp2 = secondmap.comparator();
		return requireSameComparators(cmp1, cmp2);
	}

	/**
	 * Validation method for ensuring that the two comparators define the same order.
	 * <p>
	 * The comparators of are compared by equality. <code>null</code> arguments are treated as to denote the
	 * {@linkplain Comparator#naturalOrder() natural order}.
	 * 
	 * @param <T>
	 *            The type of the compared objects.
	 * @param cmp1
	 *            The first comparator.
	 * @param cmp2
	 *            The second comparator.
	 * @return The comparator that defines the order. Never <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the comparators are different.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> Comparator<T> requireSameComparators(Comparator<T> cmp1, Comparator<?> cmp2)
			throws IllegalArgumentException {
		if (cmp1 == null) {
			Comparator naturalorder = Comparator.naturalOrder();
			if (cmp2 == null) {
				return naturalorder;
			}
			if (cmp2.equals(naturalorder)) {
				return naturalorder;
			}
			//mismatch
		} else if (cmp2 == null) {
			Comparator naturalorder = Comparator.naturalOrder();
			if (cmp1.equals(naturalorder)) {
				return naturalorder;
			}
			//mismatch
		} else if (cmp1.equals(cmp2)) {
			return cmp1;
		}
		//mismatch
		throw new IllegalArgumentException("Different comparators: " + cmp1 + " - " + cmp2);
	}

	/**
	 * Checks if the argument iterable is sorted by the order specified by the given comparator.
	 * <p>
	 * If the comparator is <code>null</code>, the natural order is checked.
	 * <p>
	 * This method checks for weak ordering, meaning that if two consecutive elements compare to be equal, the iterable
	 * is still considered to be sorted.
	 * 
	 * @param <E>
	 *            The type of the elements.
	 * @param iterable
	 *            The iterable.
	 * @param comparator
	 *            The comparator.
	 * @return <code>true</code> if the elements in the iterable are sorted.
	 * @see #isStrictlySorted(Iterable, Comparator)
	 */
	@SuppressWarnings("unchecked")
	public static <E> boolean isSorted(Iterable<? extends E> iterable, Comparator<? super E> comparator) {
		Objects.requireNonNull(iterable, "iterable");
		if (comparator == null) {
			comparator = (Comparator<? super E>) Comparator.naturalOrder();
		}
		Iterator<? extends E> it = iterable.iterator();
		if (!it.hasNext()) {
			return true;
		}
		E item = it.next();
		while (it.hasNext()) {
			E n = it.next();
			if (comparator.compare(item, n) > 0) {
				return false;
			}
			item = n;
		}
		return true;
	}

	/**
	 * Checks if the argument iterable is strictly sorted by the order specified by the given comparator.
	 * <p>
	 * If the comparator is <code>null</code>, the natural order is checked.
	 * <p>
	 * This method checks for strict ordering, meaning that if two consecutive elements compare to be equal, the
	 * iterable is <b>not</b> considered to be sorted.
	 * 
	 * @param <E>
	 *            The type of the elements.
	 * @param iterable
	 *            The iterable.
	 * @param comparator
	 *            The comparator.
	 * @return <code>true</code> if the elements in the iterable are strictly sorted.
	 * @see #isSorted(Iterable, Comparator)
	 */
	@SuppressWarnings("unchecked")
	public static <E> boolean isStrictlySorted(Iterable<? extends E> iterable, Comparator<? super E> comparator) {
		Objects.requireNonNull(iterable, "iterable");
		if (comparator == null) {
			comparator = (Comparator<? super E>) Comparator.naturalOrder();
		}
		Iterator<? extends E> it = iterable.iterator();
		if (!it.hasNext()) {
			return true;
		}
		E item = it.next();
		while (it.hasNext()) {
			E n = it.next();
			if (comparator.compare(item, n) >= 0) {
				return false;
			}
			item = n;
		}
		return true;
	}

	/**
	 * Checks if the keys of the argument entry iterable is sorted by the order specified by the given comparator.
	 * <p>
	 * If the comparator is <code>null</code>, the natural order is checked.
	 * <p>
	 * This method checks for weak ordering, meaning that if two consecutive keys compare to be equal, the iterable is
	 * still considered to be sorted.
	 * 
	 * @param <K>
	 *            The type of the keys.
	 * @param entries
	 *            The iterable of entries to examine.
	 * @param comparator
	 *            The comparator.
	 * @return <code>true</code> if the keys in the entry iterable are sorted.
	 * @see #isStrictlySortedEntries(Iterable, Comparator)
	 */
	@SuppressWarnings("unchecked")
	public static <K> boolean isSortedEntries(Iterable<? extends Entry<? extends K, ?>> entries,
			Comparator<? super K> comparator) {
		Objects.requireNonNull(entries, "entries");
		if (comparator == null) {
			comparator = (Comparator<? super K>) Comparator.naturalOrder();
		}
		Iterator<? extends Entry<? extends K, ?>> it = entries.iterator();
		if (!it.hasNext()) {
			return true;
		}
		Entry<? extends K, ?> item = it.next();
		while (it.hasNext()) {
			Entry<? extends K, ?> n = it.next();
			if (comparator.compare(item.getKey(), n.getKey()) > 0) {
				return false;
			}
			item = n;
		}
		return true;
	}

	/**
	 * Checks if the keys of the argument entry iterable is strictly sorted by the order specified by the given
	 * comparator.
	 * <p>
	 * If the comparator is <code>null</code>, the natural order is checked.
	 * <p>
	 * This method checks for strict ordering, meaning that if two consecutive keys compare to be equal, the iterable is
	 * <b>not</b> considered to be sorted.
	 * 
	 * @param <K>
	 *            The type of the keys.
	 * @param entries
	 *            The iterable of entries to examine.
	 * @param comparator
	 *            The comparator.
	 * @return <code>true</code> if the keys in the entry iterable are stricly sorted.
	 * @see #isSortedEntries(Iterable, Comparator)
	 */
	@SuppressWarnings("unchecked")
	public static <K> boolean isStrictlySortedEntries(Iterable<? extends Entry<? extends K, ?>> entries,
			Comparator<? super K> comparator) {
		Objects.requireNonNull(entries, "entries");
		if (comparator == null) {
			comparator = (Comparator<? super K>) Comparator.naturalOrder();
		}
		Iterator<? extends Entry<? extends K, ?>> it = entries.iterator();
		if (!it.hasNext()) {
			return true;
		}
		Entry<? extends K, ?> item = it.next();
		while (it.hasNext()) {
			Entry<? extends K, ?> n = it.next();
			if (comparator.compare(item.getKey(), n.getKey()) >= 0) {
				return false;
			}
			item = n;
		}
		return true;
	}

	/**
	 * Gets the class of the argument if non-<code>null</code>.
	 * 
	 * @param <T>
	 *            The type of the argument.
	 * @param object
	 *            The object to get the class of.
	 * @return The class of the object, or <code>null</code> if the object is <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <T> Class<? extends T> classOf(T object) {
		return object == null ? null : (Class<? extends T>) object.getClass();
	}

	/**
	 * Gets the class of the argument if non-<code>null</code>, else returns a default value.
	 * 
	 * @param <T>
	 *            The type of the argument.
	 * @param object
	 *            The object to get the class of.
	 * @param defaultvalue
	 *            The class to return if the object is <code>null</code>.
	 * @return The class of the object, or the default value if the object is <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <T> Class<? extends T> classOf(T object, Class<? extends T> defaultvalue) {
		return object == null ? defaultvalue : (Class<? extends T>) object.getClass();
	}

	/**
	 * Gets the classes of the elements in the argument array.
	 * <p>
	 * This method will determine the class type of each element in the argument in the same manner as
	 * {@link #classOf(Object, Class)}.
	 * 
	 * @param array
	 *            The array to get the element types of.
	 * @param defaultclass
	 *            The class type to set for an element if it is <code>null</code>.
	 * @return An array of classes which correspond to the classes of each element in the array, or <code>null</code> if
	 *             the array is <code>null</code>.
	 */
	public static Class<?>[] classOfArrayElements(Object[] array, Class<?> defaultclass) {
		if (array == null) {
			return null;
		}
		Class<?>[] result = new Class<?>[array.length];
		for (int i = 0; i < array.length; i++) {
			result[i] = classOf(array[i], defaultclass);
		}
		return result;
	}

	/**
	 * Gets the classes of the elements in the argument array.
	 * <p>
	 * This method will determine the class type of each element in the argument in the same manner as
	 * {@link #classOf(Object)}.
	 * 
	 * @param array
	 *            The array to get the element types of.
	 * @return An array of classes which correspond to the classes of each element in the array, or <code>null</code> if
	 *             the array is <code>null</code>.
	 */
	public static Class<?>[] classOfArrayElements(Object[] array) {
		return classOfArrayElements(array, null);
	}

	/**
	 * Gets the class name of the argument object.
	 * <p>
	 * If the argument is <code>null</code>, <code>null</code> is returned.
	 * 
	 * @param obj
	 *            The object.
	 * @return The class name or <code>null</code> if the object is <code>null</code>.
	 * @see Class#getName()
	 */
	public static String classNameOf(Object obj) {
		return obj == null ? null : obj.getClass().getName();
	}

	/**
	 * Checks if the argument objects are an instance of the same class.
	 * <p>
	 * The class of the arguments will be retrieved, and compared for equality.
	 * <p>
	 * If both of the arguments are <code>null</code>, <code>true</code> will be returned. If only one of the arguments
	 * are <code>null</code>, false is returned.
	 * 
	 * @param first
	 *            The first object.
	 * @param second
	 *            The second object.
	 * @return <code>true</code> if they are an instance of the same class.
	 */
	public static boolean isSameClass(Object first, Object second) {
		if (first == second) {
			return true;
		}
		if (first == null) {
			return false;
		}
		return second != null && first.getClass() == second.getClass();
	}

	/**
	 * Moves the argument iterator until an object is encountered that equals the argument.
	 * <p>
	 * If this method returns <code>true</code>, the iterator will point after the object has been encountered. Calling
	 * {@link Iterator#remove()} will remove an object that equals to the given argument.
	 * 
	 * @param it
	 *            The iterator to iterate with.
	 * @param obj
	 *            The object to search.
	 * @return <code>true</code> if an object was found that equals to the argument.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code>.
	 */
	public static boolean iterateUntil(Iterator<?> it, Object obj) throws NullPointerException {
		Objects.requireNonNull(it, "iterator");
		if (obj == null) {
			while (it.hasNext()) {
				if (it.next() == null) {
					return true;
				}
			}
		} else {
			while (it.hasNext()) {
				if (obj.equals(it.next())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Moves the argument iterator backwards until an object is encountered that equals the argument.
	 * <p>
	 * If this method returns <code>true</code>, the iterator will point before the object has been encountered. Calling
	 * {@link Iterator#remove()} will remove an object that equals to the given argument.
	 * <p>
	 * This method works the same way as {@link #iterateUntil(Iterator, Object)}, but takes a {@link ListIterator} as
	 * its argument, and iterates using {@link ListIterator#previous()} instead of {@link Iterator#next()}.
	 * 
	 * @param it
	 *            The iterator to iterate with.
	 * @param obj
	 *            The object to search.
	 * @return <code>true</code> if an object was found that equals to the argument.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code>.
	 */
	public static boolean iteratePrevUntil(ListIterator<?> it, Object obj) throws NullPointerException {
		Objects.requireNonNull(it, "iterator");
		if (obj == null) {
			while (it.hasPrevious()) {
				if (it.previous() == null) {
					return true;
				}
			}
		} else {
			while (it.hasPrevious()) {
				if (obj.equals(it.previous())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Finds the index of the first occurrence of a given object in an iterable.
	 * <p>
	 * The object will be compared using {@link Object#equals(Object)}.
	 * 
	 * @param iterable
	 *            The iterable to search in.
	 * @param object
	 *            The object to search for.
	 * @return The index at which the object was found, or -1 if not found.
	 * @throws NullPointerException
	 *             If the iterable is <code>null</code>.
	 */
	public static int indexOfIterable(Iterable<?> iterable, Object object) throws NullPointerException {
		Objects.requireNonNull(iterable, "iterable");
		int i = 0;
		if (object == null) {
			for (Object o : iterable) {
				if (o == null) {
					return i;
				}
				++i;
			}
		} else {
			for (Object o : iterable) {
				if (object.equals(o)) {
					return i;
				}
				++i;
			}
		}
		return -1;
	}

	/**
	 * Counts the number of elements in the iterable.
	 * <p>
	 * The value of the elements are not examined, <code>null</code> elements also count towards the result.
	 * 
	 * @param iterable
	 *            The iterable.
	 * @return The number of elements in the iterable. 0 is returned if the iterable is <code>null</code>.
	 */
	public static int sizeOfIterable(Iterable<?> iterable) {
		if (iterable == null) {
			return 0;
		}
		int c = 0;
		Iterator<?> it = iterable.iterator();
		while (it.hasNext()) {
			it.next();
			++c;
		}
		return c;
	}

	/**
	 * Counts the number of remaining elements in the iterator.
	 * <p>
	 * The value of the elements are not examined, <code>null</code> elements also count towards the result.
	 * 
	 * @param it
	 *            The iterator.
	 * @return The number of remaining elements in the iterator. 0 is returned if the iterator is <code>null</code>.
	 */
	public static int sizeOfIterator(Iterator<?> it) {
		if (it == null) {
			return 0;
		}
		int c = 0;
		while (it.hasNext()) {
			it.next();
			++c;
		}
		return c;
	}

	/**
	 * Converts the argument iterator to an once iterable.
	 * <p>
	 * The {@link Iterable#iterator()} function of the returned iterable can only be called once, else
	 * {@link IllegalStateException} will be thrown. The returned iterator will be the same as the argument to this
	 * method.
	 * 
	 * @param <E>
	 *            The type of the returned elements.
	 * @param iterator
	 *            The iterator.
	 * @return The wrapping once iterable.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code>.
	 */
	public static <E> Iterable<E> onceIterable(Iterator<E> iterator) throws NullPointerException {
		Objects.requireNonNull(iterator, "iterator");
		return new OnceIterable<>(iterator);
	}

	/**
	 * Gets an iterator that iterates over the argument array.
	 * 
	 * @param <T>
	 *            The element type.
	 * @param array
	 *            The array.
	 * @return An iterator or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <T> Iterator<T> iterator(T[] array) {
		if (array == null) {
			return null;
		}
		return new ArrayIterator<>(array);
	}

	/**
	 * Calls {@link Iterable#iterator()} on the argument if non-<code>null</code>.
	 * 
	 * @param <T>
	 *            The element type.
	 * @param iterable
	 *            The iterable.
	 * @return An iterator from the argument or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <T> Iterator<T> iterator(Iterable<T> iterable) {
		if (iterable == null) {
			return null;
		}
		return iterable.iterator();
	}

	/**
	 * Removes the first element from an iterable if present.
	 * <p>
	 * This method will use {@link Iterator#hasNext()} to determine if there is an element in the iterable, and remove
	 * it if there is one.
	 * 
	 * @param iterable
	 *            The iterable to remove the first element of.
	 * @return The removed element or <code>null</code> if there was no such element. (<code>null</code> will be
	 *             returned even if the first element is <code>null</code>.)
	 * @throws NullPointerException
	 *             If the iterable is <code>null</code>.
	 */
	public static <T> T removeFirstElement(Iterable<? extends T> iterable) throws NullPointerException {
		Objects.requireNonNull(iterable, "iterable");
		Iterator<? extends T> it = iterable.iterator();
		if (it.hasNext()) {
			T result = it.next();
			it.remove();
			return result;
		}
		return null;
	}

	/**
	 * Removes the first element from an iterable, or throws an exception if there is no first element.
	 * <p>
	 * This method will not call the {@link Iterator#hasNext()} method, and relies on the iterator to throw a
	 * {@link NoSuchElementException} if there is no first element. As per the documentation of the {@link Iterator}
	 * class, all of them should obey this behaviour.
	 * 
	 * @param iterable
	 *            The iterable to remove the first element of.
	 * @return The removed element.
	 * @throws NoSuchElementException
	 *             If there is no elements in the iterable.
	 * @throws NullPointerException
	 *             If the iterable is <code>null</code>.
	 */
	public static <T> T removeFirstElementThrow(Iterable<? extends T> iterable)
			throws NoSuchElementException, NullPointerException {
		Objects.requireNonNull(iterable, "iterable");
		Iterator<? extends T> it = iterable.iterator();
		//next() throws if there is no element
		T result = it.next();
		it.remove();
		return result;
	}

	/**
	 * Searches for the first occurrence of an object in an iterable, and removes it.
	 * <p>
	 * This method will iterate over the argument iterable, and find the first occurrence in it, that equals to the
	 * argument object. If found, {@link Iterator#remove()} is called to remove it, and <code>true</code> is returned.
	 * 
	 * @param iterable
	 *            The iterable to search the object in.
	 * @param object
	 *            The object to search. May be <code>null</code>.
	 * @return <code>true</code> if an element was removed.
	 * @throws NullPointerException
	 *             If the iterable is <code>null</code>.
	 */
	public static boolean removeFirstOccurrence(Iterable<?> iterable, Object object) throws NullPointerException {
		Objects.requireNonNull(iterable, "iterable");
		if (object == null) {
			for (Iterator<?> it = iterable.iterator(); it.hasNext();) {
				Object o = it.next();
				if (o == null) {
					it.remove();
					return true;
				}
			}
		} else {
			for (Iterator<?> it = iterable.iterator(); it.hasNext();) {
				Object o = it.next();
				if (object.equals(o)) {
					it.remove();
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Finds the object at the given index in the argument iterable, and removes it.
	 * <p>
	 * This method will iterate over the argument iterable until the element at the given index is reached. Then it will
	 * call {@link Iterator#remove()} and return <code>true</code>.
	 * <p>
	 * If the specified index is greater than the number of objects in the iterable, no elements will be removed, and
	 * <code>false</code> is returned.
	 * 
	 * @param iterable
	 *            The iterable.
	 * @param index
	 *            The index at which the element should be removed.
	 * @return <code>true</code> if the element at the index was removed.
	 * @throws NullPointerException
	 *             If the iterable is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the index is negative.
	 */
	public static boolean removeAtIndex(Iterable<?> iterable, int index)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(iterable, "iterable");
		if (index < 0) {
			throw new IllegalArgumentException("Negative index: " + index);
		}
		Iterator<?> it = iterable.iterator();
		for (int i = 0; it.hasNext(); ++i) {
			it.next();
			if (i == index) {
				it.remove();
				return true;
			}
		}
		return false;
	}

	/**
	 * Calculates the hash of the elements contained in the argument based on the method specified by
	 * {@link List#hashCode()}.
	 * 
	 * @param iterable
	 *            The iterable of elements.
	 * @return The hash.
	 * @throws NullPointerException
	 *             If the iterable is <code>null</code>.
	 */
	public static int listHash(Iterable<?> iterable) throws NullPointerException {
		Objects.requireNonNull(iterable, "iterable");
		Iterator<?> it = iterable.iterator();
		return listHash(it);
	}

	/**
	 * Calculates the hash of the elements contained in the argument based on the method specified by
	 * {@link List#hashCode()}.
	 * 
	 * @param it
	 *            The iterator of elements.
	 * @return The hash.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code>.
	 */
	public static int listHash(Iterator<?> it) throws NullPointerException {
		Objects.requireNonNull(it, "iterator");
		int hash = 1;
		while (it.hasNext()) {
			Object e = it.next();
			hash = 31 * hash + (e == null ? 0 : e.hashCode());
		}
		return hash;
	}

	/**
	 * Calculates the hash of the elements contained in the argument based on the method specified by
	 * {@link Set#hashCode()}.
	 * 
	 * @param iterable
	 *            The iterable of elements.
	 * @return The hash.
	 * @throws NullPointerException
	 *             If the iterable is <code>null</code>.
	 */
	public static int setHash(Iterable<?> iterable) throws NullPointerException {
		Objects.requireNonNull(iterable, "iterable");
		Iterator<?> i = iterable.iterator();
		return setHash(i);
	}

	/**
	 * Calculates the hash of the elements contained in the argument based on the method specified by
	 * {@link Set#hashCode()}.
	 * 
	 * @param it
	 *            The iterator of elements.
	 * @return The hash.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code>.
	 */
	public static int setHash(Iterator<?> it) throws NullPointerException {
		Objects.requireNonNull(it, "iterator");
		int hash = 0;
		while (it.hasNext()) {
			Object obj = it.next();
			if (obj != null) {
				hash += obj.hashCode();
			}
		}
		return hash;
	}

	/**
	 * Calculates the hash of the entries contained in the argument based on the method specified by
	 * {@link Map#hashCode()}.
	 * 
	 * @param map
	 *            The map of elements.
	 * @return The hash.
	 * @throws NullPointerException
	 *             If the map is <code>null</code>.
	 */
	public static int mapHash(Map<?, ?> map) throws NullPointerException {
		Objects.requireNonNull(map, "map");
		Iterator<? extends Entry<?, ?>> iterator = map.entrySet().iterator();
		return mapHash(iterator);
	}

	/**
	 * Calculates the hash of the entries contained in the argument based on the method specified by
	 * {@link Map#hashCode()}.
	 * 
	 * @param entryiterable
	 *            An iterable of map entries.
	 * @return The hash.
	 * @throws NullPointerException
	 *             If the iterable is <code>null</code>.
	 */
	public static int mapHash(Iterable<? extends Map.Entry<?, ?>> entryiterable) throws NullPointerException {
		Objects.requireNonNull(entryiterable, "entry iterable");
		Iterator<? extends Entry<?, ?>> iterator = entryiterable.iterator();
		return mapHash(iterator);
	}

	/**
	 * Calculates the hash of the entries contained in the argument based on the method specified by
	 * {@link Map#hashCode()}.
	 * 
	 * @param iterator
	 *            An iterator of map entries.
	 * @return The hash.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code>.
	 */
	public static int mapHash(Iterator<? extends Entry<?, ?>> iterator) throws NullPointerException {
		Objects.requireNonNull(iterator, "entry iterator");
		int h = 0;
		while (iterator.hasNext()) {
			Entry<?, ?> entry = iterator.next();
			//since we're calculating manually, use our entry hasher as well
			h += mapEntryHash(entry);
		}
		return h;
	}

	/**
	 * Calculates the hash of the argument map entry based on the method specified by {@link Map.Entry#hashCode()}.
	 * 
	 * @param entry
	 *            The map entry.
	 * @return The hash.
	 * @throws NullPointerException
	 *             If the entry is <code>null</code>.
	 */
	public static int mapEntryHash(Map.Entry<?, ?> entry) throws NullPointerException {
		Objects.requireNonNull(entry, "entry");
		Object key = entry.getKey();
		Object value = entry.getValue();
		return mapEntryHash(key, value);
	}

	/**
	 * Calculates the hash of the argument key-value map entry based on the method specified by
	 * {@link Map.Entry#hashCode()}.
	 * 
	 * @param key
	 *            The key.
	 * @param value
	 *            The value.
	 * @return The hash.
	 */
	public static int mapEntryHash(Object key, Object value) {
		return (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
	}

	/**
	 * Checks the argument maps for equality.
	 * <p>
	 * This method checks if the arguments have the same size, and each key in the first map is mapped to the same value
	 * in the second as in the first.
	 * <p>
	 * The method handles nullability of the arguments. If both of the arguments are <code>null</code>,
	 * <code>true</code> is returned.
	 * 
	 * @param first
	 *            The first map.
	 * @param second
	 *            The second map.
	 * @return <code>true</code> if they equal.
	 */
	public static boolean mapsEqual(Map<?, ?> first, Map<?, ?> second) {
		if (first == second) {
			return true;
		}
		if (first == null || second == null) {
			return false;
		}
		if (second.size() != first.size()) {
			return false;
		}
		try {
			Iterator<? extends Entry<?, ?>> i = first.entrySet().iterator();
			while (i.hasNext()) {
				Entry<?, ?> e = i.next();
				Object key = e.getKey();
				Object value = e.getValue();

				//use getOrDefault() to avoid querying the second map twice for get() and containsKey()
				//the getOrDefault() call should be safe, because the maps shouldn't type check the default value
				//    therefore the following raw cast is valid.
				@SuppressWarnings({ "unchecked", "rawtypes" })
				Object secondval = ((Map) second).getOrDefault(key, MAP_GET_DEFAULT_NOT_PRESENT_PLACEHOLDER);
				if (secondval == MAP_GET_DEFAULT_NOT_PRESENT_PLACEHOLDER) {
					//not present in the second
					return false;
				}
				if (!Objects.equals(value, secondval)) {
					return false;
				}
			}
		} catch (ClassCastException | NullPointerException ignored) {
			//in case of internal map implementation errors
			return false;
		}
		return true;
	}

	/**
	 * Checks the argument sets for equality.
	 * <p>
	 * This method checks if the arguments have the same size, and the first set contains all elements from the second
	 * set.
	 * <p>
	 * The method handles nullability of the arguments. If both of the arguments are <code>null</code>,
	 * <code>true</code> is returned.
	 * 
	 * @param first
	 *            The first set.
	 * @param second
	 *            The second set.
	 * @return <code>true</code> if they equal.
	 */
	public static boolean setsEqual(Set<?> first, Set<?> second) {
		if (first == second) {
			return true;
		}
		if (first == null || second == null) {
			return false;
		}
		if (second.size() != first.size())
			return false;
		try {
			return first.containsAll(second);
		} catch (ClassCastException | NullPointerException ignored) {
			//in case of internal set implementation errors
			return false;
		}
	}

	/**
	 * Checks the argument lists for equality.
	 * <p>
	 * This method will examine if both lists have the same size and their elements at the same indexes are equal.
	 * <p>
	 * Note: this method doesn't call {@link List#size()} to shortcut the equality check, but iterate over the lists at
	 * the same time.
	 * 
	 * @param first
	 *            The first list.
	 * @param second
	 *            The second list.
	 * @return <code>true</code> if they equal.
	 */
	public static boolean listsEqual(List<?> first, List<?> second) {
		if (first == second) {
			return true;
		}
		if (first == null || second == null) {
			return false;
		}
		Iterator<?> it1 = first.iterator();
		Iterator<?> it2 = second.iterator();
		while (it1.hasNext()) {
			if (!it2.hasNext()) {
				return false;
			}
			if (!Objects.equals(it1.next(), it2.next())) {
				return false;
			}
		}
		if (it2.hasNext()) {
			return false;
		}
		return true;
	}

	/**
	 * Function that checks the arguments for deep equality.
	 * <p>
	 * If at least one argument is not an array, then they will be simply compared using {@link Object#equals(Object)}.
	 * <p>
	 * When both arguments are arrays, they will be checked for deep equality for each of their elements. This method
	 * handles if the arrays have primitive component types. If they have reference component types then each of the
	 * elements will be compared by deep equality accordingly.
	 * <p>
	 * This method works similarly to {@link Arrays#deepEquals(Object[], Object[])}, but handles the case when the
	 * objects are not array to begin with.
	 * 
	 * @param left
	 *            The first object.
	 * @param right
	 *            The second object.
	 * @return <code>true</code> if the two objects equal.
	 */
	public static boolean deepEquals(Object left, Object right) {
		if (left == null) {
			return right == null;
		}
		if (right == null) {
			return false;
		}
		Class<? extends Object> lc = left.getClass();
		Class<? extends Object> rc = right.getClass();
		if (lc.isArray()) {
			if (rc.isArray()) {
				return ArrayUtils.arraysDeepEqual(left, right);
			}
			return false;
		}
		if (rc.isArray()) {
			return false;
		}
		return left.equals(right);
	}

	/**
	 * Checks if the argument objects {@linkplain Object#equals(Object) equal}.
	 * <p>
	 * If the {@link Object#equals(Object)} method throws a {@link RuntimeException}, <code>false</code> is returned.
	 * 
	 * @param left
	 *            The first object.
	 * @param right
	 *            The second object.
	 * @return <code>true</code> if the objects equal and no exception was thrown.
	 */
	public static boolean equalsExcCheck(Object left, Object right) {
		try {
			return Objects.equals(left, right);
		} catch (RuntimeException e) {
			return false;
		}
	}

	/**
	 * Creates a string representation of the argument map.
	 * <p>
	 * Each entry in the map will be displayed in a comma separated list between curly braces. (<code>{}</code>)
	 * <p>
	 * The method creates a string representation of the map that is semantically same as the usual map representations.
	 * 
	 * @param map
	 *            The map.
	 * @return The string representation of the map.
	 * @see AbstractMap#toString()
	 */
	public static String mapToString(Map<?, ?> map) {
		if (map.isEmpty()) {
			return "{}";
		}
		Iterator<? extends Entry<?, ?>> it = map.entrySet().iterator();
		StringBuilder sb = new StringBuilder();
		sb.append('{');
		while (true) {
			Entry<?, ?> e = it.next();
			Object key = e.getKey();
			Object value = e.getValue();
			if (key == map) {
				sb.append("(this map)=");
			} else {
				sb.append(key);
				sb.append('=');
			}
			if (value == map) {
				sb.append("(this map)");
			} else {
				sb.append(value);
			}
			if (!it.hasNext()) {
				break;
			}
			sb.append(", ");
		}
		return sb.append('}').toString();
	}

	/**
	 * Creates a string representation of the argument iterable.
	 * <p>
	 * Each element in the argument will be displayed in a comma separated list between square brackets.
	 * (<code>[]</code>)
	 * <p>
	 * The method creates a string representation of the iterable that is semantically same as the usual collection
	 * representations.
	 * 
	 * @param elements
	 *            The elements.
	 * @return The string representation of the iterable.
	 * @see AbstractCollection#toString()
	 */
	public static String collectionToString(Iterable<?> elements) {
		Iterator<?> it = elements.iterator();
		if (!it.hasNext()) {
			return "[]";
		}
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		while (true) {
			Object e = it.next();
			if (e == elements) {
				if (!it.hasNext()) {
					sb.append("(this collection)");
					break;
				}
				sb.append("(this collection), ");
			} else {
				sb.append(e);
				if (!it.hasNext()) {
					break;
				}
				sb.append(", ");
			}
		}
		return sb.append(']').toString();
	}

	/**
	 * Checks if the argument iterable contains the specified object.
	 * <p>
	 * This method iterates over the iterable and searches for the specified object. {@link Object#equals(Object)} is to
	 * check equality.
	 * 
	 * @param iterable
	 *            The iterable.
	 * @param object
	 *            The object.
	 * @return <code>true</code> if the iterable is non-<code>null</code> and the object was found.
	 */
	public static boolean contains(Iterable<?> iterable, Object object) {
		if (iterable == null) {
			return false;
		}
		if (object == null) {
			for (Object o : iterable) {
				if (o == null) {
					return true;
				}
			}
		} else {
			for (Object o : iterable) {
				if (object.equals(o)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Checks if the argument collection is non-<code>null</code> and contains the given object.
	 * <p>
	 * This method checks if the collection is non-<code>null</code>, and if so, calls
	 * {@link Collection#contains(Object)} on it with the given object.
	 * 
	 * @param collection
	 *            The collection.
	 * @param object
	 *            The object.
	 * @return If the argument collection contains the object.
	 */
	public static boolean contains(Collection<?> collection, Object object) {
		return collection != null && collection.contains(object);
	}

	/**
	 * Checks if the argument map contains a key for the given object.
	 * 
	 * @param map
	 *            The map.
	 * @param object
	 *            The object to check.
	 * @return <code>true</code> if the map is non-<code>null</code> and contains a key for the object.
	 * @see Map#containsKey(Object)
	 */
	public static boolean containsKey(Map<?, ?> map, Object object) {
		return map != null && map.containsKey(object);
	}

	/**
	 * Checks if the argument map contains a value for the given object.
	 * 
	 * @param map
	 *            The map.
	 * @param object
	 *            The object to check.
	 * @return <code>true</code> if the map is non-<code>null</code> and contains a value for the object.
	 * @see Map#containsValue(Object)
	 */
	public static boolean containsValue(Map<?, ?> map, Object object) {
		return map != null && map.containsValue(object);
	}

	/**
	 * Checks if the argument haystack collection contains any element from the needle elements.
	 * <p>
	 * The method will iterate over the needle objects and check if it is contained in the argument haystack. If the
	 * needle is found, <code>true</code> is returned.
	 * <p>
	 * If the haystack argument is <code>null</code>, <code>false</code> is returned.
	 * 
	 * @param haystack
	 *            The haystack collection.
	 * @param needle
	 *            The needle iterable to search for.
	 * @return <code>true</code> if a needle object was found.
	 * @throws NullPointerException
	 *             If the needle argument is <code>null</code>.
	 */
	public static boolean containsAny(Collection<?> haystack, Iterable<?> needle) throws NullPointerException {
		Objects.requireNonNull(needle, "needle");
		return containsAny(haystack, needle.iterator());
	}

	/**
	 * Checks if the argument haystack collection contains any element from the needle iterator.
	 * <p>
	 * The method will iterate over the needle iterator and check if it an element is contained in the argument
	 * haystack. If the needle is found, <code>true</code> is returned.
	 * <p>
	 * If the haystack argument is <code>null</code>, <code>false</code> is returned.
	 * 
	 * @param haystack
	 *            The haystack collection.
	 * @param needleit
	 *            The needle iterator to search for the elements.
	 * @return <code>true</code> if a needle object was found.
	 * @throws NullPointerException
	 *             If the needle argument is <code>null</code>.
	 */
	public static boolean containsAny(Collection<?> haystack, Iterator<?> needleit) throws NullPointerException {
		if (haystack == null) {
			return false;
		}
		Objects.requireNonNull(needleit, "needle iterator");
		while (needleit.hasNext()) {
			if (haystack.contains(needleit.next())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the argument haystack map contains any key from the needle elements.
	 * <p>
	 * If the haystack argument is <code>null</code>, <code>false</code> is returned.
	 * 
	 * @param haystack
	 *            The map.
	 * @param needle
	 *            The keys to search for.
	 * @return <code>true</code> if any needle element is present as a key in the haystack map.
	 * @throws NullPointerException
	 *             If the needle argument is <code>null</code>.
	 */
	public static boolean containsAnyKey(Map<?, ?> haystack, Iterable<?> needle) throws NullPointerException {
		if (haystack == null) {
			return false;
		}
		Objects.requireNonNull(needle, "needle");
		for (Object o : needle) {
			if (haystack.containsKey(o)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the argument haystack map contains any value from the needle elements.
	 * <p>
	 * If the haystack argument is <code>null</code>, <code>false</code> is returned.
	 * 
	 * @param haystack
	 *            The map.
	 * @param needle
	 *            The values to search for.
	 * @return <code>true</code> if any needle element is present as a value in the haystack map.
	 * @throws NullPointerException
	 *             If the needle argument is <code>null</code>.
	 */
	public static boolean containsAnyValue(Map<?, ?> haystack, Iterable<?> needle) throws NullPointerException {
		if (haystack == null) {
			return false;
		}
		Objects.requireNonNull(needle, "needle");
		for (Object o : needle) {
			if (haystack.containsValue(o)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the argument haystack collection contains any element from the needle elements, and returns it if
	 * found.
	 * <p>
	 * The method will iterate over the needle objects and check if it is contained in the argument haystack. If the
	 * needle is found, it is returned.
	 * <p>
	 * If no needles are found, <code>null</code> is returned. Note that if any needle object is <code>null</code>, a
	 * return value of <code>null</code> can mean either that it was found or that it was not.
	 * <p>
	 * If the haystack argument is <code>null</code>, <code>null</code> is returned.
	 * 
	 * @param haystack
	 *            The haystack collection.
	 * @param needle
	 *            The needle iterable to search for.
	 * @return An object from the needle iterable if it was found, or <code>null</code> if none was found.
	 * @throws NullPointerException
	 *             If the needle argument is <code>null</code>.
	 */
	public static <R> R getContainsAny(Collection<?> haystack, Iterable<R> needle) throws NullPointerException {
		if (haystack == null) {
			return null;
		}
		Objects.requireNonNull(haystack, "haystack");
		Objects.requireNonNull(needle, "needle");
		for (R o : needle) {
			if (haystack.contains(o)) {
				return o;
			}
		}
		return null;
	}

	/**
	 * Transforms the elements of the given array using the transformer function.
	 * <p>
	 * This method will call the transformer function for each element in the array, and replace them with the result of
	 * the function.
	 * <p>
	 * If the argument array is <code>null</code>, this method returns <code>null</code>.
	 * 
	 * @param array
	 *            The array to transform.
	 * @param transformer
	 *            The element transformer function.
	 * @return The argument array.
	 * @throws NullPointerException
	 *             If the transformer is <code>null</code>.
	 */
	public static <T> T[] transformArray(T[] array, Function<? super T, ? extends T> transformer)
			throws NullPointerException {
		if (array == null) {
			return null;
		}
		if (array.length > 0) {
			Objects.requireNonNull(transformer, "transformer");
			for (int i = 0; i < array.length; i++) {
				array[i] = transformer.apply(array[i]);
			}
		}
		return array;
	}

	/**
	 * Wraps the argument iterable into a new one that creates iterators which transform the elements using the given
	 * function.
	 * <p>
	 * The returned iterable will create iterators that transform each element using the given function.
	 * <p>
	 * If the transformer is <code>null</code>, a {@link NullPointerException} might be thrown when an iterator is
	 * constructed from the result.
	 * 
	 * @param iterable
	 *            The iterable to transform.
	 * @param transformer
	 *            The iterator element transformer.
	 * @return The transforming iterable, or <code>null</code> if the iterable is <code>null</code>.
	 * @see #transformIterator(Iterator, Function)
	 */
	public static <T, E> Iterable<E> transformIterable(Iterable<? extends T> iterable,
			Function<? super T, ? extends E> transformer) {
		if (iterable == null) {
			return null;
		}
		return () -> transformIterator(iterable.iterator(), transformer);
	}

	/**
	 * Wraps the argument iterator into a new one that transforms the elements using the given function.
	 * <p>
	 * The returned iterator will use the argument function to transform each element, and return the resulting object.
	 * 
	 * @param iterator
	 *            The iterator to transform.
	 * @param transformer
	 *            The iterator element transformer.
	 * @return The transforming iterator, or <code>null</code> if the iterator is <code>null</code>.
	 * @throws NullPointerException
	 *             If the iterator is non-<code>null</code> and the transformer is <code>null</code>.
	 */
	public static <T, E> Iterator<E> transformIterator(Iterator<? extends T> iterator,
			Function<? super T, ? extends E> transformer) throws NullPointerException {
		if (iterator == null) {
			return null;
		}
		Objects.requireNonNull(transformer, "transformer");
		return new FunctionTransformingIterator<>(iterator, transformer);
	}

	/**
	 * Gets a reversed view of the argument list.
	 * <p>
	 * The returned list is modifiable, and will return the elements in the reverse order as the argument. Any
	 * modifications made to the returned list will be propagated accordingly to the original list.
	 * <p>
	 * Calling this with a list that was reversed with this method, will return the original list. The following is
	 * <code>true</code>:
	 * 
	 * <pre>
	 * ObjectUtils.reversed(list) == ObjectUtils.reversed(ObjectUtils.reversed(list))
	 * </pre>
	 * 
	 * @param <E>
	 *            The element type.
	 * @param list
	 *            The list to reverse.
	 * @return The reversed view of the argument list, or <code>null</code> if the argument was <code>null</code>.
	 */
	public static <E> List<E> reversedList(List<E> list) {
		if (list == null) {
			return null;
		}
		if (list instanceof ReversingList) {
			return ((ReversingList<E>) list).list;
		}
		return new ReversingList<>(list);
	}

	/**
	 * Creates a new array list and adds each element of the argument array.
	 * <p>
	 * If the argument collection is <code>null</code>, no elements are added.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The array of elements.
	 * @return The created array list.
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <E> ArrayList<E> newArrayList(E... objects) {
		if (objects == null) {
			return new ArrayList<>();
		}
		ArrayList<E> result = new ArrayList<>(objects.length);
		for (E o : objects) {
			result.add(o);
		}
		return result;
	}

	/**
	 * Creates a new array list and adds each remaining element from the argument iterator.
	 * <p>
	 * If the argument iterator is <code>null</code>, no elements are added.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The iterator of elements.
	 * @return The created array list.
	 */
	public static <E> ArrayList<E> newArrayList(Iterator<? extends E> objects) {
		if (objects == null) {
			return new ArrayList<>();
		}
		ArrayList<E> result = new ArrayList<>();
		objects.forEachRemaining(result::add);
		return result;
	}

	/**
	 * Creates a new array list and adds each element of the argument iterable.
	 * <p>
	 * If the argument iterable is <code>null</code>, no elements are added.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The iterable of elements.
	 * @return The created array list.
	 */
	public static <E> ArrayList<E> newArrayList(Iterable<? extends E> objects) {
		ArrayList<E> result = new ArrayList<>();
		if (objects != null) {
			for (E o : objects) {
				result.add(o);
			}
		}
		return result;
	}

	/**
	 * Creates a new array list and adds each element of the argument collection.
	 * <p>
	 * If the argument collection is <code>null</code>, no elements are added.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The collection of elements.
	 * @return The created array list.
	 */
	public static <E> ArrayList<E> newArrayList(Collection<? extends E> objects) {
		return objects == null ? new ArrayList<>() : new ArrayList<>(objects);
	}

	/**
	 * Creates a new array list and adds the elements of the first, and then the second collection.
	 * <p>
	 * If any of the collections is <code>null</code>, no elements are added for them.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param firstobjects
	 *            The first collection of objects to add.
	 * @param secondobjects
	 *            The second collection of objects to add.
	 * @return The created array list.
	 */
	public static <E> ArrayList<E> newArrayList(Collection<? extends E> firstobjects,
			Collection<? extends E> secondobjects) {
		if (isNullOrEmpty(firstobjects)) {
			return newArrayList(secondobjects);
		}
		if (isNullOrEmpty(secondobjects)) {
			return new ArrayList<>(firstobjects);
		}
		ArrayList<E> result = new ArrayList<>(firstobjects.size() + secondobjects.size());
		result.addAll(firstobjects);
		result.addAll(secondobjects);
		return result;
	}

	/**
	 * Creates a new {@link HashSet} and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned set will be empty.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The objects.
	 * @return The newly created hash set.
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <E> HashSet<E> newHashSet(E... objects) {
		return addAll(new HashSet<>(objects.length * 2), objects);
	}

	/**
	 * Creates a new {@link HashSet} and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned set will be empty.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The objects.
	 * @return The newly created hash set.
	 */
	public static <E> HashSet<E> newHashSet(Iterable<? extends E> objects) {
		return addAll(new HashSet<>(), objects);
	}

	/**
	 * Creates a new {@link HashSet} and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned set will be empty.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The objects.
	 * @return The newly created hash set.
	 */
	public static <E> HashSet<E> newHashSet(Collection<? extends E> objects) {
		return objects == null ? new HashSet<>() : new HashSet<>(objects);
	}

	/**
	 * Creates a new identity hash set.
	 * <p>
	 * The identity hash set is created by calling {@link #newSetFromMap(Map)} with a new {@link IdentityHashMap}.
	 * 
	 * @return The newly created identity hash set.
	 * @see IdentityHashMap
	 */
	public static <E> Set<E> newIdentityHashSet() {
		return newSetFromMap(new IdentityHashMap<>());
	}

	/**
	 * Creates a new identity hash set and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned set will be empty.
	 * <p>
	 * The identity hash set is created by calling {@link #newSetFromMap(Map)} with a new {@link IdentityHashMap}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The objects.
	 * @return The newly created identity hash set.
	 * @see IdentityHashMap
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <E> Set<E> newIdentityHashSet(E... objects) {
		return addAll(newIdentityHashSet(), objects);
	}

	/**
	 * Creates a new identity hash set and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned set will be empty.
	 * <p>
	 * The identity hash set is created by calling {@link #newSetFromMap(Map)} with a new {@link IdentityHashMap}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The objects.
	 * @return The newly created identity hash set.
	 * @see IdentityHashMap
	 */
	public static <E> Set<E> newIdentityHashSet(Iterable<? extends E> objects) {
		return addAll(newIdentityHashSet(), objects);
	}

	/**
	 * Creates a new identity hash set and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned set will be empty.
	 * <p>
	 * The identity hash set is created by calling {@link #newSetFromMap(Map)} with a new {@link IdentityHashMap}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The objects.
	 * @return The newly created identity hash set.
	 * @see IdentityHashMap
	 */
	public static <E> Set<E> newIdentityHashSet(Collection<? extends E> objects) {
		return addAll(newIdentityHashSet(), objects);
	}

	/**
	 * Creates a new identity hash set and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned set will be empty.
	 * <p>
	 * The identity hash set is created by calling {@link #newSetFromMap(Map)} with a new {@link IdentityHashMap}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The objects.
	 * @return The newly created identity hash set.
	 * @see IdentityHashMap
	 */
	public static <E> Set<E> newIdentityHashSet(Set<? extends E> objects) {
		//XXX maybe improve creation performance by providing a map mapped to booleans as an argument
		return addAll(newIdentityHashSet(), objects);
	}

	/**
	 * Creates a new {@link LinkedHashSet} and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned set will be empty.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The objects.
	 * @return The newly created linked hash set.
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <E> LinkedHashSet<E> newLinkedHashSet(E... objects) {
		return addAll(new LinkedHashSet<>(), objects);
	}

	/**
	 * Creates a new {@link LinkedHashSet} and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned set will be empty.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The objects.
	 * @return The newly created linked hash set.
	 */
	public static <E> LinkedHashSet<E> newLinkedHashSet(Iterable<? extends E> objects) {
		return addAll(new LinkedHashSet<>(), objects);
	}

	/**
	 * Creates a new {@link LinkedHashSet} and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned set will be empty.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The objects.
	 * @return The newly created linked hash set.
	 */
	public static <E> LinkedHashSet<E> newLinkedHashSet(Collection<? extends E> objects) {
		return objects == null ? new LinkedHashSet<>() : new LinkedHashSet<>(objects);
	}

	/**
	 * Creates a new {@link TreeSet} and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned set will be empty.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The objects.
	 * @return The newly created tree set.
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <E> TreeSet<E> newTreeSet(E... objects) {
		return addAll(new TreeSet<>(), objects);
	}

	/**
	 * Creates a new {@link TreeSet} and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned set will be empty.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The objects.
	 * @return The newly created tree set.
	 */
	public static <E> TreeSet<E> newTreeSet(Iterable<? extends E> objects) {
		return addAll(new TreeSet<>(), objects);
	}

	/**
	 * Creates a new {@link TreeSet} and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned set will be empty.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The objects.
	 * @return The newly created tree set.
	 */
	public static <E> TreeSet<E> newTreeSet(Collection<? extends E> objects) {
		return objects == null ? new TreeSet<>() : new TreeSet<>(objects);
	}

	/**
	 * Creates a new {@link TreeSet} and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned set will be empty.
	 * <p>
	 * The returned tree set will have the same ordering as the argument sorted set.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The objects.
	 * @return The newly created tree set.
	 */
	public static <E> TreeSet<E> newTreeSet(SortedSet<E> objects) {
		return objects == null ? new TreeSet<>() : new TreeSet<E>(objects);
	}

	/**
	 * Creates a new {@link HashMap} and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned map will be empty.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map entries to initialize the result with.
	 * @return The newly created hash map.
	 */
	public static <K, V> HashMap<K, V> newHashMap(Map<? extends K, ? extends V> map) {
		return map == null ? new HashMap<>() : new HashMap<>(map);
	}

	/**
	 * Creates a new {@link LinkedHashMap} and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned map will be empty.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map entries to initialize the result with.
	 * @return The newly created linked hash map.
	 */
	public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(Map<? extends K, ? extends V> map) {
		return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
	}

	/**
	 * Creates a new {@link IdentityHashMap} and initializes it with the argument objects.
	 * <p>
	 * If the argument is <code>null</code>, the returned map will be empty.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map entries to initialize the result with.
	 * @return The newly created identity hash map.
	 */
	public static <K, V> IdentityHashMap<K, V> newIdentityHashMap(Map<? extends K, ? extends V> map) {
		return map == null ? new IdentityHashMap<>() : new IdentityHashMap<>(map);
	}

	/**
	 * Creates a new {@link TreeMap} and initializes it with the argument map entries.
	 * <p>
	 * If the argument is <code>null</code>, the returned map will be empty.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map to initialize the result with.
	 * @return The newly created tree map.
	 */
	public static <K, V> TreeMap<K, V> newTreeMap(Map<? extends K, ? extends V> map) {
		return map == null ? new TreeMap<>() : new TreeMap<>(map);
	}

	/**
	 * Creates a new {@link TreeMap} and initializes it with the argument map entries.
	 * <p>
	 * If the argument is <code>null</code>, the returned map will be empty.
	 * <p>
	 * The returned tree set will have the same ordering as the argument sorted map.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map to initialize the result with.
	 * @return The newly created tree map.
	 */
	public static <K, V> TreeMap<K, V> newTreeMap(SortedMap<K, ? extends V> map) {
		return map == null ? new TreeMap<>() : new TreeMap<>(map);
	}

	/**
	 * Creates a new set that is backed by the argument map.
	 * <p>
	 * This method creates a set the same way as {@link Collections#newSetFromMap(Map)}, but the returned set is
	 * {@link Externalizable}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param map
	 *            The set backing map.
	 * @return The set that is backed by the argument map or <code>null</code> if the argument map is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument map is not empty.
	 */
	public static <E> Set<E> newSetFromMap(Map<E, ? super Boolean> map) throws IllegalArgumentException {
		if (map == null) {
			return null;
		}
		if (!map.isEmpty()) {
			throw new IllegalArgumentException("Map is not empty.");
		}
		return new SetFromMap<>(map);
	}

	/**
	 * Adds all the remaining elements in the argument iterator to the specified collecion.
	 * <p>
	 * If the argument iterator is <code>null</code>, no elements are added to the collection.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param <CollType>
	 *            The type of the collection.
	 * @param collection
	 *            The collection.
	 * @param iterator
	 *            The iterator of elements to add.
	 * @return The argument collection.
	 * @throws NullPointerException
	 *             If the collection is <code>null</code>. (May be only thrown when the iterator is
	 *             non-<code>null</code>.)
	 */
	public static <E, CollType extends Collection<? super E>> CollType addAll(CollType collection,
			Iterator<? extends E> iterator) throws NullPointerException {
		if (iterator != null) {
			Objects.requireNonNull(collection, "collection");
			iterator.forEachRemaining(collection::add);
		}
		return collection;
	}

	/**
	 * Adds all the elements in the argument iterable to the specified collecion.
	 * <p>
	 * If the argument iterable is <code>null</code>, no elements are added to the collection.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param <CollType>
	 *            The type of the collection.
	 * @param collection
	 *            The collection.
	 * @param objects
	 *            The iterable of elements to add.
	 * @return The argument collection.
	 * @throws NullPointerException
	 *             If the collection is <code>null</code>. (May be only thrown when the iterable is
	 *             non-<code>null</code>.)
	 */
	public static <E, CollType extends Collection<? super E>> CollType addAll(CollType collection,
			Iterable<? extends E> objects) throws NullPointerException {
		if (objects != null) {
			Objects.requireNonNull(collection, "collection");
			objects.forEach(collection::add);
		}
		return collection;
	}

	/**
	 * Adds all the elements in the argument collection to the specified collecion.
	 * <p>
	 * If the argument collection is <code>null</code>, no elements are added to the target collection.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param <CollType>
	 *            The type of the collection.
	 * @param collection
	 *            The target collection to add the elements to.
	 * @param objects
	 *            The collection of elements to add.
	 * @return The target collection.
	 * @throws NullPointerException
	 *             If the target collection is <code>null</code>. (May be only thrown when the source collection is
	 *             non-<code>null</code> and non-empty.)
	 */
	public static <E, CollType extends Collection<? super E>> CollType addAll(CollType collection,
			Collection<? extends E> objects) throws NullPointerException {
		if (!isNullOrEmpty(objects)) {
			Objects.requireNonNull(collection, "collection");
			collection.addAll(objects);
		}
		return collection;
	}

	/**
	 * Adds all the elements in the specified iterable to the argument collecion.
	 * <p>
	 * If the argument array is <code>null</code>, no elements are added to the collection.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param <CollType>
	 *            The type of the collection.
	 * @param collection
	 *            The collection.
	 * @param objects
	 *            The array of elements to add.
	 * @return The argument collection.
	 * @throws NullPointerException
	 *             If the collection is <code>null</code>. (May be only thrown when the array is non-<code>null</code>
	 *             and non-empty.)
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <E, CollType extends Collection<? super E>> CollType addAll(CollType collection, E... objects)
			throws NullPointerException {
		if (isNullOrEmpty(objects)) {
			return collection;
		}
		Objects.requireNonNull(collection, "collection");
		if (objects.length == 1) {
			collection.add(objects[0]);
			return collection;
		}
		//use addAll instead of iterating over the array, so the collection can do its own verifications more efficiently
		//   e.g. if its a synchronized collection, its better
		//   constructing this throwaway wrapper collection is not considered a big deal
		collection.addAll(ImmutableArrayList.create(objects));
		return collection;
	}

	/**
	 * Comparison function for comparing elements of iterables.
	 * <p>
	 * Works the same ways as {@link #compareIterators(Iterator, Iterator, ToIntBiFunction)}, but the iterators are
	 * instantiated in this function.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * compareIterators(comparables1.iterator(), comparables2.iterator(), comparator);
	 * </pre>
	 * 
	 * @param <LType>
	 *            The left iterable element type.
	 * @param <RType>
	 *            The right iterable element type.
	 * @param left
	 *            The left iterable of comparables.
	 * @param right
	 *            The right iterable of comparables.
	 * @param comparator
	 *            The comparator for the elements.
	 * @return The comparison result.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @see Comparable#compareTo(Object)
	 */
	public static <LType, RType> int compareIterables(Iterable<? extends LType> left, Iterable<? extends RType> right,
			ToIntBiFunction<? super LType, ? super RType> comparator) throws NullPointerException {
		Objects.requireNonNull(left, "left");
		Objects.requireNonNull(right, "right");
		return compareIterators(left.iterator(), right.iterator(), comparator);
	}

	/**
	 * Comparison function for comparing iterators.
	 * <p>
	 * This function will iterate over both iterators simultaneously and compare each pair of elements accordingly.
	 * <p>
	 * The argument comparator works the same way as {@link Comparator}, but takes elements with different types as
	 * argument. If the comparator is <code>null</code>, the elements are compared by the
	 * {@linkplain Comparable#compareTo(Object) natural order} of theirs.
	 * <p>
	 * If a mismatching element is found, the result of that comparison is returned from the function.
	 * <p>
	 * If one of the iterator has no more elements, that iterator will be ordered first. I.e. the shorter iterators are
	 * ordered first.
	 * <p>
	 * If neither iterators have next elements after all comparisons, 0 is returned.
	 * 
	 * @param <LType>
	 *            The left iterator element type.
	 * @param <RType>
	 *            The right iterator element type.
	 * @param left
	 *            The left iterator.
	 * @param right
	 *            The right iterator.
	 * @param comparator
	 *            The comparator for the elements.
	 * @return The comparison result.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @see Comparable#compareTo(Object)
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <LType, RType> int compareIterators(Iterator<? extends LType> left, Iterator<? extends RType> right,
			ToIntBiFunction<? super LType, ? super RType> comparator) throws NullPointerException {
		Objects.requireNonNull(left, "left");
		Objects.requireNonNull(right, "right");
		if (comparator == null) {
			comparator = ((Comparator) Comparator.naturalOrder())::compare;
		}

		while (left.hasNext()) {
			if (!right.hasNext()) {
				return 1;
			}
			LType n1 = left.next();
			RType n2 = right.next();
			int cmp = comparator.applyAsInt(n1, n2);
			if (cmp != 0) {
				return cmp;
			}
		}
		return right.hasNext() ? -1 : 0;
	}

	/**
	 * Comparison function for comparing the elements of arrays.
	 * <p>
	 * This function will iterate over the elements of the argument arrays and compare them after each other. Each
	 * element is going to be compared to the element at the same index in the other array.
	 * <p>
	 * The argument comparator works the same way as {@link Comparator}, but takes elements with different types as
	 * argument. If the comparator is <code>null</code>, the elements are compared by the
	 * {@linkplain Comparable#compareTo(Object) natural order} of theirs.
	 * <p>
	 * The comparison function finishes early, if it finds non-equal elements, in which case the function will return
	 * the result of the first mismatching elements.
	 * <p>
	 * If all checked elements are equal, then the shorter arrays will be ordered first if they have different lengths.
	 * <p>
	 * The method will only check elements up to the length of the shorter array.
	 * 
	 * @param <LType>
	 *            The left array element type.
	 * @param <RType>
	 *            The right array element type.
	 * @param left
	 *            The left array.
	 * @param right
	 *            The right array.
	 * @param comparator
	 *            The comparator for the elements.
	 * @return The comparison result.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @see Comparable#compareTo(Object)
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <LType, RType> int compareArrays(LType[] left, RType[] right,
			ToIntBiFunction<? super LType, ? super RType> comparator) throws NullPointerException {
		Objects.requireNonNull(left, "left");
		Objects.requireNonNull(right, "right");
		if (comparator == null) {
			comparator = ((Comparator) Comparator.naturalOrder())::compare;
		}

		int l1 = left.length;
		int l2 = right.length;
		int i = 0;
		while (i < l1) {
			if (i >= l2) {
				return 1;
			}
			LType n1 = left[i];
			RType n2 = right[i];
			int cmp = comparator.applyAsInt(n1, n2);
			if (cmp != 0) {
				return cmp;
			}
			++i;
		}
		if (i < l2) {
			return -1;
		}
		return 0;
	}

	/**
	 * Comparison function for comparing sorted sets with the same ordering.
	 * <p>
	 * This function works the same way as {@link #compareIterables(Iterable, Iterable, ToIntBiFunction)}, but
	 * determines the element comparator based on the comparators of the maps.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param left
	 *            The left set.
	 * @param right
	 *            The right set.
	 * @return The comparison result. 0 if the maps compare to be equal, negative if the left map is ordered first,
	 *             positive if the right map is ordered first.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the sets are ordered differently.
	 * @see {@link #requireSameComparators(SortedSet, SortedSet)}
	 */
	public static <E> int compareOrderedSets(SortedSet<? extends E> left, SortedSet<? extends E> right)
			throws NullPointerException, IllegalArgumentException {
		//safe unchecked cast, as both comparators in the maps have Comparator<? super Key> type
		@SuppressWarnings("unchecked")
		Comparator<? super E> comparator = (Comparator<? super E>) requireSameComparators(left, right);
		//we can get the iterators here, as the null checks are done in this function
		return compareIterators(left.iterator(), right.iterator(), comparator::compare);
	}

	/**
	 * Comparison function for comparing sorted maps with the same ordering.
	 * <p>
	 * This function works the same way as {@link #compareOrderedMaps(Map, Map, ToIntBiFunction, ToIntBiFunction)}, but
	 * determines the key comparator based on the comparators of the argument maps.
	 * 
	 * @param <Key>
	 *            The key type of the maps.
	 * @param <LType>
	 *            The value type of the left map.
	 * @param <RType>
	 *            The value type of the right map.
	 * @param left
	 *            The left map.
	 * @param right
	 *            The right map.
	 * @param valuecomparator
	 *            The comparator function for the values of the maps.
	 * @return The comparison result. 0 if the maps compare to be equal, negative if the left map is ordered first,
	 *             positive if the right map is ordered first.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the maps are ordered differently.
	 * @see #requireSameComparators(SortedMap, SortedMap)
	 */
	public static <Key, LType, RType> int compareOrderedMaps(SortedMap<? extends Key, ? extends LType> left,
			SortedMap<? extends Key, ? extends RType> right,
			ToIntBiFunction<? super LType, ? super RType> valuecomparator)
			throws NullPointerException, IllegalArgumentException {
		//safe unchecked cast, as both comparators in the maps have Comparator<? super Key> type
		@SuppressWarnings("unchecked")
		Comparator<? super Key> comparator = (Comparator<? super Key>) requireSameComparators(left, right);
		return compareOrderedMaps(left, right, comparator::compare, valuecomparator);
	}

	/**
	 * Comparison function for comparing maps based on their iteration order and values.
	 * <p>
	 * Note: this method is not suitable for comparing unordered maps like {@link HashMap}.
	 * <p>
	 * This method will iterate over the arguments maps and compare the keys of their entries. If the key comparison
	 * yields a non-zero result, it is returned as the comparion result of the function. If the keys compare to be
	 * equal, the values of the maps are compared using the value comparator argument. If the value comparions yields
	 * non-zero result, it is returned as the result of the function. If they equal, the next entry will be processed
	 * the same way.
	 * <p>
	 * When there are no more entries in either of the maps, the one that has less entries will be ordered first. If
	 * both maps have the same length, and all of their entries compare to be equal, this function will return 0 as the
	 * comparison result.
	 * <p>
	 * This method is suitable for comparing maps only, if the maps have a deterministic iteration order. It is most
	 * suitable for sorted maps (E.g. {@link TreeMap}) or maps that have a predictable iteration order (E.g.
	 * {@link LinkedHashMap}).
	 * 
	 * @param <LKey>
	 *            The key type of the left map.
	 * @param <RKey>
	 *            The key type of the right map.
	 * @param <LType>
	 *            The value type of the left map.
	 * @param <RType>
	 *            The value type of the right map.
	 * @param left
	 *            The left map.
	 * @param right
	 *            The right map.
	 * @param keycomparator
	 *            The comparator function for comparing the keys of the maps.
	 * @param valuecomparator
	 *            The comparator function for the values of the maps.
	 * @return The comparison result. 0 if the maps compare to be equal, negative if the left map is ordered first,
	 *             positive if the right map is ordered first.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <LKey, RKey, LType, RType> int compareOrderedMaps(Map<? extends LKey, ? extends LType> left,
			Map<? extends RKey, ? extends RType> right, ToIntBiFunction<? super LKey, ? super RKey> keycomparator,
			ToIntBiFunction<? super LType, ? super RType> valuecomparator) throws NullPointerException {
		Objects.requireNonNull(left, "left");
		Objects.requireNonNull(right, "right");
		if (left == right) {
			return 0;
		}
		Objects.requireNonNull(keycomparator, "comparator");
		Objects.requireNonNull(valuecomparator, "value comparator");
		Iterator<? extends Entry<? extends LKey, ? extends LType>> lit = Objects
				.requireNonNull(left.entrySet(), "left entry set").iterator();
		Iterator<? extends Entry<? extends RKey, ? extends RType>> rit = Objects
				.requireNonNull(right.entrySet(), "right entry set").iterator();
		Objects.requireNonNull(lit, "left iterator");
		Objects.requireNonNull(rit, "right iterator");
		while (lit.hasNext()) {
			if (!rit.hasNext()) {
				return 1;
			}
			Entry<? extends LKey, ? extends LType> ln = lit.next();
			Entry<? extends RKey, ? extends RType> rn = rit.next();
			Objects.requireNonNull(ln, "left entry");
			Objects.requireNonNull(rn, "right entry");
			int keycmp = keycomparator.applyAsInt(ln.getKey(), rn.getKey());
			if (keycmp != 0) {
				return keycmp;
			}
			int valcmp = valuecomparator.applyAsInt(ln.getValue(), rn.getValue());
			if (valcmp != 0) {
				return valcmp;
			}
		}
		return !rit.hasNext() ? 0 : -1;
	}

	/**
	 * Checks the argument objects for equality using the given comparator predicate.
	 * <p>
	 * If the objects are same by identity, <code>true</code> is returned without calling the comparator.
	 * <p>
	 * If any of the objects are <code>null</code> (and the other is not <code>null</code>), then <code>false</code> is
	 * returned.
	 * <p>
	 * Else, the comparator predicate will be called with the arguments.
	 * 
	 * @param <LType>
	 *            The type of the left object.
	 * @param <RType>
	 *            The type of the right.
	 * @param left
	 *            The first object.
	 * @param right
	 *            The second object.
	 * @param comparator
	 *            The comparator predicate to check if the objects equal.
	 * @return <code>true</code> if the objects are same by identity, or the comparator returned <code>true</code>.
	 * @throws NullPointerException
	 */
	public static <LType, RType> boolean objectsEquals(LType left, RType right,
			BiPredicate<? super LType, ? super RType> comparator) throws NullPointerException {
		if (left == right) {
			return true;
		}
		if (left == null || right == null) {
			return false;
		}
		Objects.requireNonNull(comparator, "comparator");
		return comparator.test(left, right);
	}

	/**
	 * Same as calling {@link #iterablesOrderedEquals(Iterable, Iterable, BiPredicate)
	 * iterablesEquals}<code>(left, right, {@link Objects#equals(Object) Objects::equals}</code>).
	 * 
	 * @param <LType>
	 *            The element types of the first iterable.
	 * @param <RType>
	 *            The element types of the second iterable.
	 * @param left
	 *            The first iterable.
	 * @param right
	 *            The second iterable.
	 * @return <code>true</code> if the iterables are considered to be equal.
	 */
	public static <LType, RType> boolean iterablesOrderedEquals(Iterable<? extends LType> left,
			Iterable<? extends RType> right) {
		return iterablesOrderedEquals(left, right, Objects::equals);
	}

	/**
	 * Checks if the argument iterables equal by checking the equality of the elements in iteration order using the
	 * given function.
	 * <p>
	 * Note: this method is not suitable for checking the equality of unordered collections like {@link HashSet}.
	 * <p>
	 * If the iterables have different sizes, this method will return <code>false</code>.
	 * <p>
	 * The method handles nullability of the arguments.
	 * <p>
	 * Example usage:
	 * 
	 * <pre>
	 * ObjectUtils.collectionEquals(iterable1, iterable2, Objects::equals);
	 * </pre>
	 * 
	 * @param <LType>
	 *            The element types of the first iterable.
	 * @param <RType>
	 *            The element types of the second iterable.
	 * @param left
	 *            The first iterable.
	 * @param right
	 *            The second iterable.
	 * @param comparator
	 *            The function to determine if two elements are equal.
	 * @return <code>true</code> if the iterables are considered to be equal.
	 * @throws NullPointerException
	 *             If the comparator function is <code>null</code>.
	 */
	public static <LType, RType> boolean iterablesOrderedEquals(Iterable<? extends LType> left,
			Iterable<? extends RType> right, BiPredicate<? super LType, ? super RType> comparator)
			throws NullPointerException {
		if (left == right) {
			return true;
		}
		if (left == null || right == null) {
			return false;
		}
		return iterablesEqualsNonNull(left, right, comparator);
	}

	/**
	 * Same as calling {@link #collectionOrderedEquals(Collection, Collection, BiPredicate)
	 * collectionEquals}<code>(left, right, {@link Objects#equals(Object) Objects::equals}</code>).
	 * 
	 * @param <LType>
	 *            The element types of the first collection.
	 * @param <RType>
	 *            The element types of the second collection.
	 * @param left
	 *            The first collection.
	 * @param right
	 *            The second collection.
	 * @return <code>true</code> if the collections are considered to be equal.
	 */
	public static <LType, RType> boolean collectionOrderedEquals(Collection<? extends LType> left,
			Collection<? extends RType> right) {
		return collectionOrderedEquals(left, right, Objects::equals);
	}

	/**
	 * Checks if the argument collections equal by checking the equality of the elements in iteration order using the
	 * given function.
	 * <p>
	 * Note: this method is not suitable for checking the equality of unordered collections like {@link HashSet}.
	 * <p>
	 * If the collections have different sizes, this method will return <code>false</code>.
	 * <p>
	 * The method handles nullability of the arguments.
	 * <p>
	 * This method differs from {@link #iterablesOrderedEquals(Iterable, Iterable, BiPredicate)} only by checking the
	 * size equality before starting to iterate over the collections. This can result in faster determination if
	 * inequality. Callers should keep this in mind when passing arguments that provide a {@link Collection#size()}
	 * method which has greater complexity than O(1) (E.g. some concurrent collections may do this).
	 * <p>
	 * Example usage:
	 * 
	 * <pre>
	 * ObjectUtils.collectionEquals(coll1, coll2, Objects::equals);
	 * </pre>
	 * 
	 * @param <LType>
	 *            The element types of the first collection.
	 * @param <RType>
	 *            The element types of the second collection.
	 * @param left
	 *            The first collection.
	 * @param right
	 *            The second collection.
	 * @param comparator
	 *            The function to determine if two elements are equal.
	 * @return <code>true</code> if the collections are considered to be equal.
	 * @throws NullPointerException
	 *             If the comparator function is <code>null</code>.
	 */
	public static <LType, RType> boolean collectionOrderedEquals(Collection<? extends LType> left,
			Collection<? extends RType> right, BiPredicate<? super LType, ? super RType> comparator)
			throws NullPointerException {
		if (left == right) {
			return true;
		}
		if (left == null || right == null) {
			return false;
		}
		if (left.size() != right.size()) {
			return false;
		}
		return iterablesEqualsNonNull((Iterable<? extends LType>) left, right, comparator);
	}

	/**
	 * Same as calling {@link #mapOrderedEquals(Map, Map, BiPredicate)
	 * orderedMapEquals}<code>(left, right, {@link Objects#equals(Object) Objects::equals}</code>).
	 * 
	 * @param <Key>
	 *            The key type.
	 * @param <LType>
	 *            The value types of the first map.
	 * @param <RType>
	 *            The value types of the second map.
	 * @param left
	 *            The first map.
	 * @param right
	 *            The second map.
	 * @return <code>true</code> if the maps are considered to be equal.
	 */
	public static <Key, LType, RType> boolean mapOrderedEquals(Map<? extends Key, ? extends LType> left,
			Map<? extends Key, ? extends RType> right) {
		return mapOrderedEquals(left, right, Objects::equals);
	}

	/**
	 * Checks if the argument maps equal by checking the equality of the keys and values in iteration order using the
	 * given function.
	 * <p>
	 * Note: this method is not suitable for checking the equality of unordered maps like {@link HashMap}.
	 * <p>
	 * This method will simultaneously iterate over the entries of both maps and compare them for equality. This method
	 * is only suitable for checking equality of maps which iterate over the entries in a deterministic order. Examples
	 * for this are sorted maps (E.g. {@link TreeMap}), or maps that preserve their order in some ways (E.g.
	 * {@link LinkedHashMap}).
	 * <p>
	 * If the maps have different sizes, this method will return <code>false</code>.
	 * <p>
	 * The method handles nullability of the arguments.
	 * <p>
	 * The method checks the sizes of the maps before starting the iteration, so callers should keep this in mind when
	 * passing arguments that provide a {@link Map#size()} method which has greater complexity than O(1) (E.g. some
	 * {@linkplain ConcurrentMap concurrent maps}).
	 * <p>
	 * Example usage:
	 * 
	 * <pre>
	 * ObjectUtils.orderedMapEquals(map1, map2, Objects::equals);
	 * </pre>
	 * 
	 * @param <Key>
	 *            The key type.
	 * @param <LType>
	 *            The value types of the first map.
	 * @param <RType>
	 *            The value types of the second map.
	 * @param left
	 *            The first map.
	 * @param right
	 *            The second map.
	 * @param comparator
	 *            The function to determine if two values are equal.
	 * @return <code>true</code> if the maps are considered to be equal.
	 * @throws NullPointerException
	 *             If the comparator function is <code>null</code>.
	 */
	public static <Key, LType, RType> boolean mapOrderedEquals(Map<? extends Key, ? extends LType> left,
			Map<? extends Key, ? extends RType> right, BiPredicate<? super LType, ? super RType> comparator)
			throws NullPointerException {
		if (left == right) {
			return true;
		}
		if (left == null || right == null) {
			return false;
		}
		if (left.size() != right.size()) {
			return false;
		}
		Objects.requireNonNull(comparator, "comparator");
		Iterator<? extends Entry<? extends Key, ? extends LType>> lit = Objects
				.requireNonNull(left.entrySet(), "left entry set").iterator();
		Iterator<? extends Entry<? extends Key, ? extends RType>> rit = Objects
				.requireNonNull(right.entrySet(), "right entry set").iterator();
		Objects.requireNonNull(lit, "left iterator");
		Objects.requireNonNull(rit, "right iterator");
		while (lit.hasNext()) {
			if (!rit.hasNext()) {
				return false;
			}
			Entry<? extends Key, ? extends LType> ln = lit.next();
			Entry<? extends Key, ? extends RType> rn = rit.next();
			Objects.requireNonNull(ln, "left entry");
			Objects.requireNonNull(rn, "right entry");
			if (!Objects.equals(ln.getKey(), rn.getKey())) {
				return false;
			}
			if (!comparator.test(ln.getValue(), rn.getValue())) {
				return false;
			}
		}
		return !rit.hasNext();
	}

	/**
	 * Iterates over the given iterables assuming that both of them are sorted by the natural order.
	 * <p>
	 * This function will iterate both iterables simultaneously in ascending {@linkplain Comparator#naturalOrder()
	 * natural order}, and will pair the matching elements in the iterables.
	 * <p>
	 * During iteration, each element is compared to the current element of the other iterator. Based on the comparison
	 * result, the function will advance the iterator that returned the element that compared to be less than the other.
	 * If the comparison resulted in equality, both iterators are advanced.
	 * <p>
	 * The action argument will be called for each element in the iterables. If a matching pair was not found for an
	 * element in an iterable, <code>null</code> will be passed as the argument for the action for the respective
	 * parameter. When a match is found, both arguments are present for the associated iterable.
	 * <p>
	 * If an iterator finishes early, the remaining elements will be iterated over in the other iterator, and the
	 * actions will be called.
	 * <p>
	 * For proper operation, the caller <b>must</b> ensure that the elements in both iterables are sorted by the natural
	 * order. If this requirement is violated, the pairing algorithm will not work, however, all the iterables will
	 * still be fully iterated, and the action will be called for all elements, but the calls will be semantically
	 * incorrect. This function doesn't ensure that the iterables are ordered, and no runtime exception will be thrown
	 * if this requirement is violated.
	 * 
	 * @param <L>
	 *            The type of the left elements. Must be comparable to the right elements.
	 * @param <R>
	 *            The type of the right elements.
	 * @param left
	 *            The iterable of the left elements.
	 * @param right
	 *            The iterable of the right elements.
	 * @param action
	 *            The action to call for the iterated elements.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	@SuppressWarnings("rawtypes")
	public static <L extends Comparable<? super R>, R> void iterateOrderedIterables(Iterable<? extends L> left,
			Iterable<? extends R> right, BiConsumer<? super L, ? super R> action) throws NullPointerException {
		//the raw types warning in the following line is suppressed
		//it is not an issue, as the left element implement a comparable for the right
		//therefore the created bifunction will be (l, r) -> l.compareTo(r), where the first argument is L, and the second is R
		//the same is created with the following method reference, but we use the method reference instead of the above lambda, as its better byte code
		//the following commented line compiles as well, and is semantically the same
		//    iterateSortedIterables(left, right, (L l, R r) -> l.compareTo(r), consumer);
		iterateOrderedIterables(left, right, Comparable::compareTo, action);
	}

	/**
	 * Iterates over the given iterables assuming that both of them are sorted by the specified comparator function.
	 * <p>
	 * This function will iterate both iterables simultaneously in the order defined by the argument comparator
	 * function, and will pair the matching elements in the iterables.
	 * <p>
	 * During iteration, each element is compared to the current element of the other iterator. Based on the comparison
	 * result, the function will advance the iterator that returned the element that compared to be less than the other.
	 * If the comparison resulted in equality, both iterators are advanced.
	 * <p>
	 * The action argument will be called for each element in the iterables. If a matching pair was not found for an
	 * element in an iterable, <code>null</code> will be passed as the argument for the action for the respective
	 * parameter. When a match is found, both arguments are present for the associated iterable.
	 * <p>
	 * If an iterator finishes early, the remaining elements will be iterated over in the other iterator, and the
	 * actions will be called.
	 * <p>
	 * For proper operation, the caller <b>must</b> ensure that the elements in both iterables are sorted by the
	 * specified order. If this requirement is violated, the pairing algorithm will not work, however, all the iterables
	 * will still be fully iterated, and the action will be called for all elements, but the calls will be semantically
	 * incorrect. This function doesn't ensure that the iterables are ordered, and no runtime exception will be thrown
	 * if this requirement is violated.
	 * 
	 * @param <L>
	 *            The type of the left elements.
	 * @param <R>
	 *            The type of the right elements.
	 * @param left
	 *            The iterable of the left elements.
	 * @param right
	 *            The iterable of the right elements.
	 * @param comparator
	 *            The comparator function that compares the left and right elements. It should return 0 if the elements
	 *            compare to be equal, negative if the left is less than the right, or positive if the right is less
	 *            than the left. See {@link Comparator#compare(Object, Object)}.
	 * @param action
	 *            The action to call for the iterated elements.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <L, R> void iterateOrderedIterables(Iterable<? extends L> left, Iterable<? extends R> right,
			ToIntBiFunction<? super L, ? super R> comparator, BiConsumer<? super L, ? super R> action)
			throws NullPointerException {
		Iterator<? extends L> lit = left.iterator();
		Iterator<? extends R> rit = right.iterator();
		iterateOrderedIterators(lit, rit, comparator, action);
	}

	/**
	 * Iterates over the given iterators assuming that both of them are sorted by the natural order.
	 * <p>
	 * This function will advance both iterators simultaneously in ascending {@linkplain Comparator#naturalOrder()
	 * natural order}, and will pair the matching elements in the iterators.
	 * <p>
	 * During iteration, each element is compared to the current element of the other iterator. Based on the comparison
	 * result, the function will advance the iterator that returned the element that compared to be less than the other.
	 * If the comparison resulted in equality, both iterators are advanced.
	 * <p>
	 * The action argument will be called for each element in the iterators. If a matching pair was not found for an
	 * element in an iterator, <code>null</code> will be passed as the argument for the action for the respective
	 * parameter. When a match is found, both arguments are present for the associated iterator.
	 * <p>
	 * If an iterator finishes early, the remaining elements will be iterated over in the other iterator, and the
	 * actions will be called.
	 * <p>
	 * For proper operation, the caller <b>must</b> ensure that the elements in both iterators are sorted by the natural
	 * order. If this requirement is violated, the pairing algorithm will not work, however, all the iterators will
	 * still be fully iterated, and the action will be called for all elements, but the calls will be semantically
	 * incorrect. This function doesn't ensure that the iterators are ordered, and no runtime exception will be thrown
	 * if this requirement is violated.
	 * 
	 * @param <L>
	 *            The type of the left elements. Must be comparable to the right elements.
	 * @param <R>
	 *            The type of the right elements.
	 * @param left
	 *            The iterator of the left elements.
	 * @param right
	 *            The iterator of the right elements.
	 * @param action
	 *            The action to call for the iterated elements.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	@SuppressWarnings("rawtypes")
	//see iterateSortedIterables for suppression reason
	public static <L extends Comparable<? super R>, R> void iterateOrderedIterators(Iterator<? extends L> left,
			Iterator<? extends R> right, BiConsumer<? super L, ? super R> action) throws NullPointerException {
		iterateOrderedIterators(left, right, Comparable::compareTo, action);
	}

	/**
	 * Iterates over the given iterators assuming that both of them are sorted by the specified comparator function.
	 * <p>
	 * This function will advance both iterators simultaneously in the order defined by the argument comparator
	 * function, and will pair the matching elements in the iterators.
	 * <p>
	 * During iteration, each element is compared to the current element of the other iterator. Based on the comparison
	 * result, the function will advance the iterator that returned the element that compared to be less than the other.
	 * If the comparison resulted in equality, both iterators are advanced.
	 * <p>
	 * The action argument will be called for each element in the iterators. If a matching pair was not found for an
	 * element in an iterator, <code>null</code> will be passed as the argument for the action for the respective
	 * parameter. When a match is found, both arguments are present for the associated iterator.
	 * <p>
	 * If an iterator finishes early, the remaining elements will be iterated over in the other iterator, and the
	 * actions will be called.
	 * <p>
	 * For proper operation, the caller <b>must</b> ensure that the elements in both iterators are sorted by the the
	 * specified order. If this requirement is violated, the pairing algorithm will not work, however, all the iterators
	 * will still be fully iterated, and the action will be called for all elements, but the calls will be semantically
	 * incorrect. This function doesn't ensure that the iterators are ordered, and no runtime exception will be thrown
	 * if this requirement is violated.
	 * 
	 * @param <L>
	 *            The type of the left elements.
	 * @param <R>
	 *            The type of the right elements.
	 * @param lit
	 *            The iterator of the left elements.
	 * @param rit
	 *            The iterator of the right elements.
	 * @param comparator
	 *            The comparator function that compares the left and right elements. It should return 0 if the elements
	 *            compare to be equal, negative if the left is less than the right, or positive if the right is less
	 *            than the left. See {@link Comparator#compare(Object, Object)}.
	 * @param action
	 *            The action to call for the iterated elements.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <L, R> void iterateOrderedIterators(Iterator<? extends L> lit, Iterator<? extends R> rit,
			ToIntBiFunction<? super L, ? super R> comparator, BiConsumer<? super L, ? super R> action)
			throws NullPointerException {
		Objects.requireNonNull(lit, "left iterator");
		Objects.requireNonNull(rit, "right iterator");
		Objects.requireNonNull(comparator, "comparator");
		Objects.requireNonNull(action, "action");
		outer_loop:
		while (lit.hasNext()) {
			L litem = lit.next();
			if (rit.hasNext()) {
				R ritem = rit.next();
				while (true) {
					int cmp = comparator.applyAsInt(litem, ritem);
					if (cmp == 0) {
						action.accept(litem, ritem);
						break;
					}
					if (cmp < 0) {
						//left is less than right
						action.accept(litem, null);
						if (!lit.hasNext()) {
							//no more left items
							action.accept(null, ritem);
							break outer_loop;
						}
						litem = lit.next();
						continue;
					}
					//cmp > 0
					//left is greater than right
					action.accept(null, ritem);
					if (!rit.hasNext()) {
						action.accept(litem, null);
						break;
					}
					ritem = rit.next();
				}
			} else {
				action.accept(litem, null);
				while (lit.hasNext()) {
					action.accept(lit.next(), null);
				}
				//return as the right iterator doesn't have any remaining items, no need to check the last consumer loop
				return;
			}
		}
		while (rit.hasNext()) {
			action.accept(null, rit.next());
		}
	}
	//XXX create break and dual functions for iterating over non-entries

	/**
	 * Iterates over the given iterables of map entries assuming that both of them are sorted by the specified
	 * comparator.
	 * <p>
	 * Works the same way as {@link #iterateOrderedEntryIteratorsBreak(Iterator, Iterator, Comparator, TriPredicate)}
	 * with creating the iterators from the argument iterables, and never breaking the iteration.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <VL>
	 *            The left value type.
	 * @param <VR>
	 *            The right value type.
	 * @param left
	 *            The left iterable of entries.
	 * @param right
	 *            The right iterable of entries.
	 * @param comparator
	 *            The comparator function for the entry keys.
	 * @param action
	 *            The action to execute for the entries.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <K, VL, VR> void iterateOrderedEntryIterables(
			Iterable<? extends Entry<? extends K, ? extends VL>> left,
			Iterable<? extends Entry<? extends K, ? extends VR>> right, Comparator<? super K> comparator,
			TriConsumer<? super K, ? super VL, ? super VR> action) throws NullPointerException {
		Objects.requireNonNull(action, "action");
		iterateOrderedEntryIterablesBreak(left, right, comparator, new ConsumerForwardingAlwaysTriPredicate<>(action));
	}

	/**
	 * Iterates over the given iterables of map entries assuming that both of them are sorted by the specified
	 * comparator, optionally breaking the iteration.
	 * <p>
	 * Works the same way as {@link #iterateOrderedEntryIteratorsBreak(Iterator, Iterator, Comparator, TriPredicate)}
	 * with creating the iterators from the argument iterables.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <VL>
	 *            The left value type.
	 * @param <VR>
	 *            The right value type.
	 * @param left
	 *            The left iterable of entries.
	 * @param right
	 *            The right iterable of entries.
	 * @param comparator
	 *            The comparator function for the entry keys.
	 * @param action
	 *            The action to execute for the entries. Return <code>false</code> from it to break the iteration.
	 * @return <code>true</code> if the iterables were fully iterated over, <code>false</code> if the action returned
	 *             <code>false</code> for any invocation.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <K, VL, VR> boolean iterateOrderedEntryIterablesBreak(
			Iterable<? extends Entry<? extends K, ? extends VL>> left,
			Iterable<? extends Entry<? extends K, ? extends VR>> right, Comparator<? super K> comparator,
			TriPredicate<? super K, ? super VL, ? super VR> action) throws NullPointerException {
		Objects.requireNonNull(left, "left entries");
		Objects.requireNonNull(right, "right entries");
		return iterateOrderedEntryIteratorsBreak(left.iterator(), right.iterator(), comparator, action);
	}

	/**
	 * Iterates over the given iterables of map entries assuming that both of them are sorted by the specified
	 * comparator, and calling the given action for paired entries only.
	 * <p>
	 * Works the same way as
	 * {@link #iterateOrderedEntryIteratorsDualBreak(Iterator, Iterator, Comparator, TriPredicate)} with creating the
	 * iterators from the argument iterables, and never breaking the iteration.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <VL>
	 *            The left value type.
	 * @param <VR>
	 *            The right value type.
	 * @param left
	 *            The left iterable of entries.
	 * @param right
	 *            The right iterable of entries.
	 * @param comparator
	 *            The comparator function for the entry keys.
	 * @param action
	 *            The action to execute for the paired entries.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <K, VL, VR> void iterateOrderedEntryIterablesDual(
			Iterable<? extends Entry<? extends K, ? extends VL>> left,
			Iterable<? extends Entry<? extends K, ? extends VR>> right, Comparator<? super K> comparator,
			TriConsumer<? super K, ? super VL, ? super VR> action) throws NullPointerException {
		Objects.requireNonNull(action, "action");
		iterateOrderedEntryIterablesDualBreak(left, right, comparator, (t, l, r) -> {
			action.accept(t, l, r);
			return true;
		});
	}

	/**
	 * Iterates over the given iterables of map entries assuming that both of them are sorted by the specified
	 * comparator, and calling the given action for paired entries only, optionally breaking the iteration.
	 * <p>
	 * Works the same way as
	 * {@link #iterateOrderedEntryIteratorsDualBreak(Iterator, Iterator, Comparator, TriPredicate)} with creating the
	 * iterators from the argument iterables.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <VL>
	 *            The left value type.
	 * @param <VR>
	 *            The right value type.
	 * @param left
	 *            The left iterable of entries.
	 * @param right
	 *            The right iterable of entries.
	 * @param comparator
	 *            The comparator function for the entry keys.
	 * @param action
	 *            The action to execute for the entries. Return <code>false</code> from it to break the iteration.
	 * @return <code>true</code> if the iterables were fully iterated over, <code>false</code> if the action returned
	 *             <code>false</code> for any invocation.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <K, VL, VR> boolean iterateOrderedEntryIterablesDualBreak(
			Iterable<? extends Entry<? extends K, ? extends VL>> left,
			Iterable<? extends Entry<? extends K, ? extends VR>> right, Comparator<? super K> comparator,
			TriPredicate<? super K, ? super VL, ? super VR> action) throws NullPointerException {
		Objects.requireNonNull(left, "left entries");
		Objects.requireNonNull(right, "right entries");
		return iterateOrderedEntryIteratorsDualBreak(left.iterator(), right.iterator(), comparator, action);
	}

	/**
	 * Iterates over the given iterators of map entries assuming that both of them are sorted by the specified
	 * comparator.
	 * <p>
	 * Works the same way as {@link #iterateOrderedEntryIteratorsBreak(Iterator, Iterator, Comparator, TriPredicate)}
	 * without breaking the iteration.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <VL>
	 *            The left value type.
	 * @param <VR>
	 *            The right value type.
	 * @param left
	 *            The left iterator of entries.
	 * @param right
	 *            The right iterator of entries.
	 * @param comparator
	 *            The comparator function for the entry keys.
	 * @param action
	 *            The action to execute for the entries.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <K, VL, VR> void iterateOrderedEntryIterators(
			Iterator<? extends Entry<? extends K, ? extends VL>> left,
			Iterator<? extends Entry<? extends K, ? extends VR>> right, Comparator<? super K> comparator,
			TriConsumer<? super K, ? super VL, ? super VR> action) throws NullPointerException {
		Objects.requireNonNull(action, "action");
		iterateOrderedEntryIteratorsBreak(left, right, comparator, new ConsumerForwardingAlwaysTriPredicate<>(action));
	}

	/**
	 * Iterates over the given iterators of map entries assuming that both of them are sorted by the specified
	 * comparator, optionally breaking the iteration.
	 * <p>
	 * This method advances the argument iterator simultaneously in the order defined by the argument comparator
	 * function, and will pair the matching entries in the iterators.
	 * <p>
	 * During iteration, each entry key is compared to the current key of the other iterator. Based on the comparison
	 * result, the function will advance the iterator that returned the key that is less than the other. If the
	 * comparison resulted in equality, both iterators are advanecd.
	 * <p>
	 * The action argument will be called for each entry in the iterators. If a matching pair was not found for an entry
	 * in an iterator, <code>null</code> will be passed as the value argument for the action for the respective
	 * parameter. When a match is found, both value arguments are present for the associated iterator. Note that a
	 * <code>null</code> argument for a value doesn't necessarily mean that the entry is not present in the other
	 * iterator, but only that the associated value for the given key is <code>null</code> for that entry. Callers
	 * should ensure that the entries doesn't contain <code>null</code> values if this can cause disruption.
	 * <p>
	 * If an iterator finishes early, the remaining entries will be iterated over in the other iterator, and the actions
	 * will be called.
	 * <p>
	 * For proper operation, the caller <b>must</b> ensure that the entries in both iterators are sorted by the the
	 * specified order. If this requirement is violated, the pairing algorithm will not work, however, all the iterators
	 * will still be fully iterated, and the action will be called for all entries, but the calls will be semantically
	 * incorrect. This function doesn't ensure that the iterators are ordered, and no runtime exception will be thrown
	 * if this requirement is violated.
	 * <p>
	 * The first argument of the passed action will be the key that is associated with the second and third arguments.
	 * The second argument is the value that was found in the left iterator, and the third is the value that was found
	 * in the right iterator.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <VL>
	 *            The left value type.
	 * @param <VR>
	 *            The right value type.
	 * @param left
	 *            The left iterator of entries.
	 * @param right
	 *            The right iterator of entries.
	 * @param comparator
	 *            The comparator function for the entry keys.
	 * @param action
	 *            The action to execute for the entries.
	 * @return <code>true</code> if the iterators were fully iterated over, <code>false</code> if the action returned
	 *             <code>false</code> for any invocation.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <K, VL, VR> boolean iterateOrderedEntryIteratorsBreak(
			Iterator<? extends Entry<? extends K, ? extends VL>> left,
			Iterator<? extends Entry<? extends K, ? extends VR>> right, Comparator<? super K> comparator,
			TriPredicate<? super K, ? super VL, ? super VR> action) throws NullPointerException {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(comparator, "comparator");
		Objects.requireNonNull(left, "left iterator");
		Objects.requireNonNull(right, "right iterator");
		outer_loop:
		while (left.hasNext()) {
			Entry<? extends K, ? extends VL> litem = left.next();
			K lkey = litem.getKey();
			if (right.hasNext()) {
				Entry<? extends K, ? extends VR> ritem = right.next();
				while (true) {
					K ritemkey = ritem.getKey();
					int cmp = comparator.compare(lkey, ritemkey);
					if (cmp == 0) {
						if (!action.test(lkey, litem.getValue(), ritem.getValue())) {
							return false;
						}
						//go to next left item
						break;
					}
					if (cmp < 0) {
						//left is less than right
						//right item is missing for key
						if (!action.test(lkey, litem.getValue(), null)) {
							return false;
						}
						if (!left.hasNext()) {
							if (!action.test(ritemkey, null, ritem.getValue())) {
								return false;
							}
							break outer_loop;
						}
						litem = left.next();
						lkey = litem.getKey();
						continue;
					}
					//cmp > 0
					//right item is less than left
					//left item is missing for right key
					if (!action.test(ritemkey, null, ritem.getValue())) {
						return false;
					}
					if (!right.hasNext()) {
						if (!action.test(lkey, litem.getValue(), null)) {
							return false;
						}
						break;
					}
					ritem = right.next();
					//continue loop
				}
			} else {
				//rit has no more
				if (!action.test(lkey, litem.getValue(), null)) {
					return false;
				}
				while (left.hasNext()) {
					Entry<? extends K, ? extends VL> lsingle = left.next();
					if (!action.test(lsingle.getKey(), lsingle.getValue(), null)) {
						return false;
					}
				}
				//return as the right iterator doesn't have any remaining items, no need to check the last consumer loop
				return true;
			}
		}
		while (right.hasNext()) {
			Entry<? extends K, ? extends VR> rsingle = right.next();
			if (!action.test(rsingle.getKey(), null, rsingle.getValue())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Iterates over the given iterators of map entries assuming that both of them are sorted by the specified
	 * comparator, and calling the given action for paired entries only.
	 * <p>
	 * Works the same way as
	 * {@link #iterateOrderedEntryIteratorsDualBreak(Iterator, Iterator, Comparator, TriPredicate)} without breaking the
	 * iteration.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <VL>
	 *            The left value type.
	 * @param <VR>
	 *            The right value type.
	 * @param left
	 *            The left iterator of entries.
	 * @param right
	 *            The right iterator of entries.
	 * @param comparator
	 *            The comparator function for the entry keys.
	 * @param action
	 *            The action to execute for the paired entries.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <K, VL, VR> void iterateOrderedEntryIteratorsDual(
			Iterator<? extends Entry<? extends K, ? extends VL>> left,
			Iterator<? extends Entry<? extends K, ? extends VR>> right, Comparator<? super K> comparator,
			TriConsumer<? super K, ? super VL, ? super VR> action) throws NullPointerException {
		Objects.requireNonNull(action, "action");
		iterateOrderedEntryIteratorsDualBreak(left, right, comparator,
				new ConsumerForwardingAlwaysTriPredicate<>(action));
	}

	/**
	 * Iterates over the given iterators of map entries assuming that both of them are sorted by the specified
	 * comparator, calling the action only for matching entries, optionally breaking the iteration.
	 * <p>
	 * This method advances the argument iterator simultaneously in the order defined by the argument comparator
	 * function, and will pair the matching entries in the iterators.
	 * <p>
	 * During iteration, each entry key is compared to the current key of the other iterator. Based on the comparison
	 * result, the function will advance the iterator that returned the key that is less than the other. If the
	 * comparison resulted in equality, both iterators are advanecd.
	 * <p>
	 * The action argument will be called for each <b>matched</b> entry in the iterators. If a matching pair was not
	 * found for an entry in an iterator, the action is not called. When a match is found, both value arguments are
	 * present for the associated iterator. A <code>null</code> argument in the action means that the associated value
	 * for the entry was <code>null</code>.
	 * <p>
	 * If an iterator finishes early, the remaining entries are <b>not</b> iterated over.
	 * <p>
	 * For proper operation, the caller <b>must</b> ensure that the entries in both iterators are sorted by the the
	 * specified order. If this requirement is violated, the pairing algorithm will not work, and actions will most
	 * likely not called appropriately.
	 * <p>
	 * The first argument of the passed action will be the key that is associated with the second and third arguments.
	 * The second argument is the value that was found in the left iterator, and the third is the value that was found
	 * in the right iterator.
	 * <p>
	 * This method works similarly to
	 * {@link #iterateOrderedEntryIteratorsBreak(Iterator, Iterator, Comparator, TriPredicate)}, but only calls the
	 * action if an entry has been successfully paired.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <VL>
	 *            The left value type.
	 * @param <VR>
	 *            The right value type.
	 * @param left
	 *            The left iterator of entries.
	 * @param right
	 *            The right iterator of entries.
	 * @param comparator
	 *            The comparator function for the entry keys.
	 * @param action
	 *            The action to execute for the entries.
	 * @return <code>true</code> if the iterators were fully iterated over, <code>false</code> if the action returned
	 *             <code>false</code> for any invocation.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <K, VL, VR> boolean iterateOrderedEntryIteratorsDualBreak(
			Iterator<? extends Entry<? extends K, ? extends VL>> left,
			Iterator<? extends Entry<? extends K, ? extends VR>> right, Comparator<? super K> comparator,
			TriPredicate<? super K, ? super VL, ? super VR> action) throws NullPointerException {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(comparator, "comparator");
		Objects.requireNonNull(left, "left iterator");
		Objects.requireNonNull(right, "right iterator");
		outer_loop:
		while (left.hasNext()) {
			Entry<? extends K, ? extends VL> litem = left.next();
			K lkey = litem.getKey();
			if (right.hasNext()) {
				Entry<? extends K, ? extends VR> ritem = right.next();
				while (true) {
					int cmp = comparator.compare(lkey, ritem.getKey());
					if (cmp == 0) {
						if (!action.test(lkey, litem.getValue(), ritem.getValue())) {
							//end it
							return false;
						}
						//go to next left item
						break;
					}
					if (cmp < 0) {
						//left is less than right
						//right item is missing for key
						if (!left.hasNext()) {
							break outer_loop;
						}
						litem = left.next();
						lkey = litem.getKey();
						continue;
					}
					//cmp > 0
					//right item is less than left
					//left item is missing for right key
					if (!right.hasNext()) {
						break;
					}
					ritem = right.next();
					//continue loop
				}
			} else {
				//rit has no more
				return true;
			}
		}
		//no last loop, as no pairs are found for the remaining right elements
		return true;
	}

	/**
	 * Iterates over the entries of the argument sorted maps assuming that both of them are sorted by the same order.
	 * <p>
	 * Works the same way ax {@link #iterateOrderedEntryIterables(Iterable, Iterable, Comparator, TriConsumer)}, with
	 * the comparator taken from the maps.
	 * <p>
	 * If the maps are not ordered the same way, an {@link IllegalArgumentException} is thrown.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <VL>
	 *            The left value type.
	 * @param <VR>
	 *            The right value type.
	 * @param left
	 *            The left map.
	 * @param right
	 *            The right map.
	 * @param action
	 *            The action to execute for the entries.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the maps are not ordered the same way.
	 * @see #requireSameComparators(SortedMap, SortedMap)
	 */
	public static <K, VL, VR> void iterateSortedMapEntries(SortedMap<? extends K, ? extends VL> left,
			SortedMap<? extends K, ? extends VR> right, TriConsumer<? super K, ? super VL, ? super VR> action)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(action, "action");
		iterateSortedMapEntriesBreak(left, right, new ConsumerForwardingAlwaysTriPredicate<>(action));
	}

	/**
	 * Iterates over the entries of the argument sorted maps assuming that both of them are sorted by the same order,
	 * optionally breaking the iteration.
	 * <p>
	 * Works the same way ax {@link #iterateOrderedEntryIterablesBreak(Iterable, Iterable, Comparator, TriPredicate)},
	 * with the comparator taken from the maps.
	 * <p>
	 * If the maps are not ordered the same way, an {@link IllegalArgumentException} is thrown.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <VL>
	 *            The left value type.
	 * @param <VR>
	 *            The right value type.
	 * @param left
	 *            The left map.
	 * @param right
	 *            The right map.
	 * @param action
	 *            The action to execute for the entries. Return <code>false</code> from it to break the iteration.
	 * @return <code>true</code> if the maps were fully iterated over, <code>false</code> if the action returned
	 *             <code>false</code> for any invocation.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the maps are not ordered the same way.
	 * @see #requireSameComparators(SortedMap, SortedMap)
	 */
	public static <K, VL, VR> boolean iterateSortedMapEntriesBreak(SortedMap<? extends K, ? extends VL> left,
			SortedMap<? extends K, ? extends VR> right, TriPredicate<? super K, ? super VL, ? super VR> action)
			throws NullPointerException, IllegalArgumentException {
		//safe unchecked cast, as both comparators in the maps have Comparator<? super T> type
		@SuppressWarnings("unchecked")
		Comparator<? super K> comparator = (Comparator<? super K>) requireSameComparators(left, right);
		return iterateOrderedEntryIterablesBreak(left.entrySet(), right.entrySet(), comparator, action);
	}

	/**
	 * Iterates over the entries of the argument sorted maps assuming that both of them are sorted by the same order,
	 * and calling the given action for paired entries only.
	 * <p>
	 * Works the same way ax {@link #iterateOrderedEntryIterablesDual(Iterable, Iterable, Comparator, TriConsumer)},
	 * with the comparator taken from the maps.
	 * <p>
	 * If the maps are not ordered the same way, an {@link IllegalArgumentException} is thrown.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <VL>
	 *            The left value type.
	 * @param <VR>
	 *            The right value type.
	 * @param left
	 *            The left map.
	 * @param right
	 *            The right map.
	 * @param action
	 *            The action to execute for the paired entries.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the maps are not ordered the same way.
	 * @see #requireSameComparators(SortedMap, SortedMap)
	 */
	public static <K, VL, VR> void iterateSortedMapEntriesDual(SortedMap<? extends K, ? extends VL> left,
			SortedMap<? extends K, ? extends VR> right, TriConsumer<? super K, ? super VL, ? super VR> action)
			throws NullPointerException, IllegalArgumentException {
		//safe unchecked cast, as both comparators in the maps have Comparator<? super T> type
		@SuppressWarnings("unchecked")
		Comparator<? super K> comparator = (Comparator<? super K>) requireSameComparators(left, right);
		iterateOrderedEntryIterablesDual(left.entrySet(), right.entrySet(), comparator, action);
	}

	/**
	 * Iterates over the entries of the argument sorted maps assuming that both of them are sorted by the same order,
	 * and calling the given action for paired entries only, optionally breaking the iteration.
	 * <p>
	 * Works the same way ax
	 * {@link #iterateOrderedEntryIterablesDualBreak(Iterable, Iterable, Comparator, TriPredicate)}, with the comparator
	 * taken from the maps.
	 * <p>
	 * If the maps are not ordered the same way, an {@link IllegalArgumentException} is thrown.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <VL>
	 *            The left value type.
	 * @param <VR>
	 *            The right value type.
	 * @param left
	 *            The left map.
	 * @param right
	 *            The right map.
	 * @param action
	 *            The action to execute for the paired entries. Return <code>false</code> from it to break the
	 *            iteration.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the maps are not ordered the same way.
	 * @see #requireSameComparators(SortedMap, SortedMap)
	 */
	public static <K, VL, VR> void iterateSortedMapEntriesDualBreak(SortedMap<? extends K, ? extends VL> left,
			SortedMap<? extends K, ? extends VR> right, TriPredicate<? super K, ? super VL, ? super VR> action)
			throws NullPointerException, IllegalArgumentException {
		//safe unchecked cast, as both comparators in the maps have Comparator<? super T> type
		@SuppressWarnings("unchecked")
		Comparator<? super K> comparator = (Comparator<? super K>) requireSameComparators(left, right);
		iterateOrderedEntryIterablesDualBreak(left.entrySet(), right.entrySet(), comparator, action);
	}

	//XXX single value map for ConcurrentMap and ConcurrentNavigableMap

	/**
	 * Creates a new map view that has the same keys as the argument set, and all keys are mapped to the argument value.
	 * <p>
	 * The returned map is partially modifiable, meaning that only some modifications will succeed. Removals are
	 * guaranteed to work, but insertions into the map may fail in some cases. Callers shouldn't rely on the put
	 * operations to work.
	 * <p>
	 * Putting a key-pair into the map may only work if the newly added entry value is identically the same as the
	 * mapped single value.
	 * <p>
	 * The {@link Entry#setValue(Object)} is most likely unsupported regardless of the set value.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param set
	 *            The set that contains the keys for the returned map.
	 * @param value
	 *            The value to map the keys to.
	 * @return A map view that is backed by the given key set and value.
	 * @throws NullPointerException
	 *             If the set is <code>null</code>.
	 */
	public static <K, V> Map<K, V> singleValueMap(Set<K> set, V value) throws NullPointerException {
		Objects.requireNonNull(set, "set");
		return new SingleValueKeySetMap<>(set, value);
	}

	/**
	 * Creates a new sorted map view that has the same keys as the argument set, and all keys are mapped to the argument
	 * value.
	 * <p>
	 * The returned map is partially modifiable, meaning that only some modifications will succeed. Removals are
	 * guaranteed to work, but insertions into the map may fail in some cases. Callers shouldn't rely on the put
	 * operations to work.
	 * <p>
	 * Putting a key-pair into the map may only work if the newly added entry value is identically the same as the
	 * mapped single value.
	 * <p>
	 * The {@link Entry#setValue(Object)} is most likely unsupported regardless of the set value.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param set
	 *            The set that contains the keys for the returned map.
	 * @param value
	 *            The value to map the keys to.
	 * @return A sorted map view that is backed by the given key set and value.
	 * @throws NullPointerException
	 *             If the set is <code>null</code>.
	 */
	public static <K, V> SortedMap<K, V> singleValueMap(SortedSet<K> set, V value) throws NullPointerException {
		Objects.requireNonNull(set, "set");
		return new SingleValueKeySetSortedMap<>(set, value);
	}

	/**
	 * Creates a new navigable map view that has the same keys as the argument set, and all keys are mapped to the
	 * argument value.
	 * <p>
	 * The returned map is partially modifiable, meaning that only some modifications will succeed. Removals are
	 * guaranteed to work, but insertions into the map may fail in some cases. Callers shouldn't rely on the put
	 * operations to work.
	 * <p>
	 * Putting a key-pair into the map may only work if the newly added entry value is identically the same as the
	 * mapped single value.
	 * <p>
	 * The {@link Entry#setValue(Object)} is most likely unsupported regardless of the set value.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param set
	 *            The set that contains the keys for the returned map.
	 * @param value
	 *            The value to map the keys to.
	 * @return A navigable map view that is backed by the given key set and value.
	 * @throws NullPointerException
	 *             If the set is <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> singleValueMap(NavigableSet<K> set, V value) throws NullPointerException {
		Objects.requireNonNull(set, "set");
		return new SingleValueKeySetNavigableMap<>(set, value);
	}

	// XXX testflag based sortedness and count checking in the below methods
	/**
	 * Creates a {@link TreeSet} based on the sorted elements in the argument iterator.
	 * <p>
	 * The elements iterated by the iterator <b>must</b> be
	 * {@linkplain ObjectUtils#isStrictlySorted(Iterable, Comparator) stricly ordered} by natural order.
	 * <p>
	 * The number of elements iterated over must be the same as the <code>size</code> argument.
	 * <p>
	 * The above requirements are not checked by this function. Violating the above requirements will result in the
	 * returned set not working properly.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * createTreeSetFromSortedIterator(it, size, null);
	 * </pre>
	 * 
	 * @param <E>
	 *            The element type.
	 * @param it
	 *            The iterator for the elements.
	 * @param size
	 *            The number of elements remaining in the iterator.
	 * @return The created set that contains the elements in the iterator.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code>.
	 */
	public static <E> TreeSet<E> createTreeSetFromSortedIterator(Iterator<? extends E> it, int size)
			throws NullPointerException {
		return createTreeSetFromSortedIterator(it, size, null);
	}

	/**
	 * Creates a {@link TreeSet} based on the sorted elements in the argument iterator.
	 * <p>
	 * The elements iterated by the iterator <b>must</b> be
	 * {@linkplain ObjectUtils#isStrictlySorted(Iterable, Comparator) stricly ordered} by the argument comparator.
	 * <p>
	 * The number of elements iterated over must be the same as the <code>size</code> argument.
	 * <p>
	 * The above requirements are not checked by this function. Violating the above requirements will result in the
	 * returned set not working properly.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param it
	 *            The iterator for the elements.
	 * @param size
	 *            The number of elements remaining in the iterator.
	 * @param comparator
	 *            The comparator defining the order of the elements or <code>null</code> to use the natural order.
	 * @return The created set that contains the elements in the iterator.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code>.
	 */
	public static <E> TreeSet<E> createTreeSetFromSortedIterator(Iterator<? extends E> it, int size,
			Comparator<? super E> comparator) throws NullPointerException {
		return new TreeSet<>(new FakeSortedSet<>(ObjectUtils.onceIterable(it), size, comparator));
	}

	/**
	 * Creates a {@link TreeMap} based on the sorted entries in the argument iterator.
	 * <p>
	 * The entries iterated by the iterator <b>must</b> be
	 * {@linkplain ObjectUtils#isStrictlySortedEntries(Iterable, Comparator) stricly ordered} by natural order.
	 * <p>
	 * The number of entries iterated over must be the same as the <code>size</code> argument.
	 * <p>
	 * The above requirements are not checked by this function. Violating the above requirements will result in the
	 * returned map not working properly.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * createTreeMapFromSortedIterator(it, size, null);
	 * </pre>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param it
	 *            The iterator for the entries.
	 * @param size
	 *            The number of entries remaining in the iterator.
	 * @return The created map that contains the entries in the iterator.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code>.
	 */
	public static <K, V> TreeMap<K, V> createTreeMapFromSortedIterator(
			Iterator<? extends Map.Entry<? extends K, ? extends V>> it, int size) throws NullPointerException {
		return createTreeMapFromSortedIterator(it, size, null);
	}

	/**
	 * Creates a {@link TreeMap} based on the sorted entries in the argument iterator.
	 * <p>
	 * The entries iterated by the iterator <b>must</b> be
	 * {@linkplain ObjectUtils#isStrictlySortedEntries(Iterable, Comparator) stricly ordered} by the argument
	 * comparator.
	 * <p>
	 * The number of entries iterated over must be the same as the <code>size</code> argument.
	 * <p>
	 * The above requirements are not checked by this function. Violating the above requirements will result in the
	 * returned map not working properly.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param it
	 *            The iterator for the entries.
	 * @param size
	 *            The number of elements remaining in the iterator.
	 * @param comparator
	 *            The comparator defining the order of the entries or <code>null</code> to use the natural order.
	 * @return The created map that contains the entries in the iterator.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code>.
	 */
	public static <K, V> TreeMap<K, V> createTreeMapFromSortedIterator(
			Iterator<? extends Map.Entry<? extends K, ? extends V>> it, int size, Comparator<? super K> comparator)
			throws NullPointerException {
		Objects.requireNonNull(it, "iterator");
		return new TreeMap<>(new FakeSortedEntryMap<>(ObjectUtils.onceIterable(it), size, comparator));
	}

	/**
	 * Creates an immutable {@link NavigableMap} based on the sorted entries in the argument iterator.
	 * <p>
	 * The entries iterated by the iterator <b>must</b> be
	 * {@linkplain ObjectUtils#isStrictlySortedEntries(Iterable, Comparator) stricly ordered} by natural order.
	 * <p>
	 * The number of entries iterated over must be the same as the <code>size</code> argument.
	 * <p>
	 * The sortedness requirement is not checked by this function. Violating that requirement will result in the
	 * returned map not working properly.
	 * <p>
	 * This method consumes <code>size</code> number of elements from the iterator. If it contains more elements, those
	 * elements will be still retrievable from the iterator after this method returns.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param it
	 *            The iterator for the entries.
	 * @param size
	 *            The number of elements remaining in the iterator.
	 * @return The created map that contains the entries in the iterator.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code> or any of the entries it returns is <code>null</code>.
	 * @throws NoSuchElementException
	 *             If there are fewer elements in the iterator than the specified size.
	 */
	public static <K, V> NavigableMap<K, V> createImmutableNavigableMapFromSortedIterator(
			Iterator<? extends Map.Entry<? extends K, ? extends V>> it, int size)
			throws NullPointerException, NoSuchElementException {
		return createImmutableNavigableMapFromSortedIterator(it, size, null);
	}

	/**
	 * Creates an immutable {@link NavigableMap} based on the sorted entries in the argument iterator.
	 * <p>
	 * The entries iterated by the iterator <b>must</b> be
	 * {@linkplain ObjectUtils#isStrictlySortedEntries(Iterable, Comparator) stricly ordered} by the argument
	 * comparator.
	 * <p>
	 * The number of entries iterated over must be the same as the <code>size</code> argument.
	 * <p>
	 * The sortedness requirement is not checked by this function. Violating that requirement will result in the
	 * returned map not working properly.
	 * <p>
	 * This method consumes <code>size</code> number of elements from the iterator. If it contains more elements, those
	 * elements will be still retrievable from the iterator after this method returns.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param it
	 *            The iterator for the entries.
	 * @param size
	 *            The number of elements remaining in the iterator.
	 * @param comparator
	 *            The comparator defining the order of the entries or <code>null</code> to use the natural order.
	 * @return The created map that contains the entries in the iterator.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code> or any of the entries it returns is <code>null</code>.
	 * @throws NoSuchElementException
	 *             If there are fewer elements in the iterator than the specified size.
	 */
	public static <K, V> NavigableMap<K, V> createImmutableNavigableMapFromSortedIterator(
			Iterator<? extends Map.Entry<? extends K, ? extends V>> it, int size, Comparator<? super K> comparator)
			throws NullPointerException, NoSuchElementException {
		Objects.requireNonNull(it, "iterator");
		@SuppressWarnings("unchecked")
		K[] keys = (K[]) new Object[size];
		@SuppressWarnings("unchecked")
		V[] values = (V[]) new Object[size];
		int i = 0;
		while (i < size) {
			//next() will throw NoSuchElementException if there's fewer elements
			Entry<? extends K, ? extends V> entry = it.next();
			keys[i] = entry.getKey();
			values[i] = entry.getValue();
			++i;
		}
		return ImmutableUtils.unmodifiableNavigableMap(keys, values, comparator);
	}

	/**
	 * Creates a {@link ConcurrentSkipListMap} based on the sorted entries in the argument iterator.
	 * <p>
	 * The entries iterated by the iterator <b>must</b> be
	 * {@linkplain ObjectUtils#isStrictlySortedEntries(Iterable, Comparator) stricly ordered} by natural order.
	 * <p>
	 * The above requirement is not checked by this function. Violating it will result in the returned map not working
	 * properly.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * createConcurrentSkipListMapFromSortedIterator(it, null);
	 * </pre>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param it
	 *            The iterator for the entries.
	 * @return The created map that contains the entries in the iterator.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code>.
	 */
	public static <K, V> ConcurrentSkipListMap<K, V> createConcurrentSkipListMapFromSortedIterator(
			Iterator<? extends Map.Entry<? extends K, ? extends V>> it) throws NullPointerException {
		return createConcurrentSkipListMapFromSortedIterator(it, null);
	}

	/**
	 * Creates a {@link ConcurrentSkipListMap} based on the sorted entries in the argument iterator.
	 * <p>
	 * The entries iterated by the iterator <b>must</b> be
	 * {@linkplain ObjectUtils#isStrictlySortedEntries(Iterable, Comparator) stricly ordered} by the argument
	 * comparator.
	 * <p>
	 * The above requirement is not checked by this function. Violating it will result in the returned map not working
	 * properly.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param it
	 *            The iterator for the entries.
	 * @param comparator
	 *            The comparator defining the order of the entries or <code>null</code> to use the natural order.
	 * @return The created map that contains the entries in the iterator.
	 * @throws NullPointerException
	 *             If the iterator is <code>null</code>.
	 */
	public static <K, V> ConcurrentSkipListMap<K, V> createConcurrentSkipListMapFromSortedIterator(
			Iterator<? extends Map.Entry<? extends K, ? extends V>> it, Comparator<? super K> comparator)
			throws NullPointerException {
		//the size is not used during the construction of ConcurrentSkipListMap, it is fine to set it to 0
		return new ConcurrentSkipListMap<>(new FakeSortedEntryMap<>(ObjectUtils.onceIterable(it), 0, comparator));
	}

	/**
	 * Clones the argument object using the specified cloner function.
	 * <p>
	 * This method returns <code>null</code>, if the argument object is <code>null</code>, else calls the cloner
	 * function with its as the argument.
	 * 
	 * @param <T>
	 *            The type of the object.
	 * @param object
	 *            The object to clone.
	 * @param cloner
	 *            The cloner function for non-<code>null</code> object.
	 * @return The cloned object or <code>null</code> if the object was <code>null</code>, or the cloner function
	 *             returned <code>null</code>.
	 * @throws NullPointerException
	 *             If the cloner function is <code>null</code>.
	 */
	public static <T> T clone(T object, Function<? super T, ? extends T> cloner) throws NullPointerException {
		if (object == null) {
			return null;
		}
		Objects.requireNonNull(cloner, "cloner");
		return cloner.apply(object);
	}

	/**
	 * Clones the argument array using the specified cloner function.
	 * <p>
	 * This method returns <code>null</code> if the argument array is <code>null</code>, else calls the cloner function
	 * for each element in the source array and puts them into a new array.
	 * 
	 * @param <E>
	 *            The element type of the array.
	 * @param array
	 *            The array to clone.
	 * @param cloner
	 *            The cloner function for the elements.
	 * @return The cloned array or <code>null</code> if the object was <code>null</code>.
	 * @throws NullPointerException
	 *             If the cloner function is <code>null</code>.
	 */
	public static <E> E[] cloneArray(E[] array, Function<? super E, ? extends E> cloner) throws NullPointerException {
		if (array == null) {
			return null;
		}
		Objects.requireNonNull(cloner, "element cloner");

		@SuppressWarnings("unchecked")
		E[] result = (E[]) Array.newInstance(array.getClass().getComponentType(), array.length);
		for (int i = 0; i < result.length; i++) {
			result[i] = cloner.apply(array[i]);
		}
		return result;
	}

	/**
	 * Clones the argument array to the target component type using the specified cloner function.
	 * <p>
	 * This method returns <code>null</code> if the argument array is <code>null</code>, else calls the cloner function
	 * for each element in the source array and puts them into a new array with the specified component type.
	 * 
	 * @param <E>
	 *            The element type of the source array.
	 * @param <T>
	 *            The element type of the target array.
	 * @param array
	 *            The array to clone.
	 * @param targetcomponenttype
	 *            The component type of the returned array.
	 * @param cloner
	 *            The cloner function for the elements.
	 * @return The cloned array or <code>null</code> if the object was <code>null</code>.
	 * @throws NullPointerException
	 *             If the cloner function or the target component type is <code>null</code>.
	 */
	public static <E, T> T[] cloneArray(E[] array, Class<T> targetcomponenttype,
			Function<? super E, ? extends T> cloner) throws NullPointerException {
		if (array == null) {
			return null;
		}
		Objects.requireNonNull(targetcomponenttype, "target component type");
		Objects.requireNonNull(cloner, "element cloner");

		@SuppressWarnings("unchecked")
		T[] result = (T[]) Array.newInstance(targetcomponenttype, array.length);
		for (int i = 0; i < result.length; i++) {
			result[i] = cloner.apply(array[i]);
		}
		return result;
	}

	/**
	 * Clones the argument collection by creating an array list that contains the same items.
	 * <p>
	 * This method returns <code>null</code>, if the argument is <code>null</code>.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param coll
	 *            The collection to clone.
	 * @return A newly created {@link ArrayList} or <code>null</code> if the argument was <code>null</code>.
	 * @see ArrayList#ArrayList(Collection)
	 */
	public static <E> ArrayList<E> cloneArrayList(Collection<? extends E> coll) {
		if (coll == null) {
			return null;
		}
		return new ArrayList<>(coll);
	}

	/**
	 * Clones the argument collection and its elements into an array list.
	 * <p>
	 * This method returns <code>null</code>, if the argument is <code>null</code>.
	 * <p>
	 * A new {@link ArrayList} will be created, and the element cloner function will be applied to each element in the
	 * original collection. The cloned elements will be added to the new list.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param coll
	 *            The collection to clone.
	 * @param elementcloner
	 *            The element cloner function that is applied to each element.
	 * @return A newly created {@link ArrayList} or <code>null</code> if the argument was <code>null</code>.
	 * @throws NullPointerException
	 *             If the element cloner is <code>null</code>.
	 */
	public static <E> ArrayList<E> cloneArrayList(Collection<? extends E> coll,
			Function<? super E, ? extends E> elementcloner) throws NullPointerException {
		if (coll == null) {
			return null;
		}
		Objects.requireNonNull(elementcloner, "element cloner");

		ArrayList<E> result = new ArrayList<>(coll.size());
		for (E e : coll) {
			if (e == null) {
				result.add(null);
			} else {
				result.add(elementcloner.apply(e));
			}
		}
		return result;
	}

	/**
	 * Clones the argument collection by creating an tree set that contains the same items.
	 * <p>
	 * This method returns <code>null</code>, if the argument is <code>null</code>.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param coll
	 *            The collection to clone.
	 * @return A newly created {@link TreeSet} or <code>null</code> if the argument was <code>null</code>.
	 * @see TreeSet#TreeSet(Collection)
	 */
	public static <E> TreeSet<E> cloneTreeSet(Collection<? extends E> coll) {
		if (coll == null) {
			return null;
		}
		return new TreeSet<>(coll);
	}

	/**
	 * Clones the argument sorted set by creating an tree set with the same order and items.
	 * <p>
	 * This method returns <code>null</code>, if the argument is <code>null</code>.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param coll
	 *            The collection to clone.
	 * @return A newly created {@link TreeSet} or <code>null</code> if the argument was <code>null</code>.
	 * @see TreeSet#TreeSet(SortedSet))
	 */
	public static <E> TreeSet<E> cloneTreeSet(SortedSet<E> coll) {
		if (coll == null) {
			return null;
		}
		return new TreeSet<>(coll);
	}

	/**
	 * Clones the argument map by creating a tree map that contains the same entries.
	 * <p>
	 * This method returns <code>null</code>, if the argument is <code>null</code>.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map to clone.
	 * @return A newly created {@link TreeMap} or <code>null</code> if the argument was <code>null</code>.
	 * @see TreeMap#TreeMap(Map)
	 */
	public static <K, V> TreeMap<K, V> cloneTreeMap(Map<? extends K, ? extends V> map) {
		if (map == null) {
			return null;
		}
		return new TreeMap<>(map);
	}

	/**
	 * Clones the argument sorted map by creating a tree map with the same order and entries.
	 * <p>
	 * This method returns <code>null</code>, if the argument is <code>null</code>.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map to clone.
	 * @return A newly created {@link TreeMap} or <code>null</code> if the argument was <code>null</code>.
	 * @see TreeMap#TreeMap(SortedMap)
	 */
	public static <K, V> TreeMap<K, V> cloneTreeMap(SortedMap<K, ? extends V> map) {
		if (map == null) {
			return null;
		}
		return new TreeMap<>(map);
	}

	/**
	 * Clones the argument map and its entries into a tree map.
	 * <p>
	 * This method returns <code>null</code>, if the argument is <code>null</code>.
	 * <p>
	 * A new {@link TreeMap} will be created, and the key and value cloner functions will be applied to each entry in
	 * the original map. The cloned entries will be put into the new map.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map to clone.
	 * @param keycloner
	 *            The key cloner function that is applied to each key.
	 * @param valuecloner
	 *            The value cloner function that is applied to each value.
	 * @return A newly created {@link TreeMap} or <code>null</code> if the argument was <code>null</code>.
	 * @throws NullPointerException
	 *             If the key or value cloner is <code>null</code>.
	 */
	public static <K, V> TreeMap<K, V> cloneTreeMap(Map<? extends K, ? extends V> map,
			Function<? super K, ? extends K> keycloner, Function<? super V, ? extends V> valuecloner)
			throws NullPointerException {
		if (map == null) {
			return null;
		}
		Objects.requireNonNull(keycloner, "key cloner");
		Objects.requireNonNull(valuecloner, "value cloner");

		TreeMap<K, V> result = new TreeMap<>();
		for (Entry<? extends K, ? extends V> entry : map.entrySet()) {
			K k = entry.getKey();
			V v = entry.getValue();
			result.put(k == null ? null : keycloner.apply(k), v == null ? null : valuecloner.apply(v));
		}
		return result;
	}

	/**
	 * Clones the argument map by creating a linked hash map that contains the same entries.
	 * <p>
	 * This method returns <code>null</code>, if the argument is <code>null</code>.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map to clone.
	 * @return A newly created {@link LinkedHashMap} or <code>null</code> if the argument was <code>null</code>.
	 * @see LinkedHashMap#LinkedHashMap(Map)
	 */
	public static <K, V> LinkedHashMap<K, V> cloneLinkedHashMap(Map<? extends K, ? extends V> map) {
		if (map == null) {
			return null;
		}
		return new LinkedHashMap<>(map);
	}

	/**
	 * Clones the argument map by creating a hash map that contains the same entries.
	 * <p>
	 * This method returns <code>null</code>, if the argument is <code>null</code>.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map to clone.
	 * @return A newly created {@link HashMap} or <code>null</code> if the argument was <code>null</code>.
	 * @see HashMap#HashMap(Map)
	 */
	public static <K, V> HashMap<K, V> cloneHashMap(Map<? extends K, ? extends V> map) {
		if (map == null) {
			return null;
		}
		return new HashMap<>(map);
	}

	/**
	 * Clones the argument collection by creating a new {@link LinkedHashSet}.
	 * <p>
	 * The {@link LinkedHashSet} is initialized using the {@link LinkedHashSet#LinkedHashSet(Collection)
	 * LinkedHashSet(Collection&lt;E&gt;)} constructor.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param coll
	 *            The collection to clone.
	 * @return A newly created {@link LinkedHashSet} or <code>null</code> if the argument was <code>null</code>.
	 */
	public static <E> LinkedHashSet<E> cloneLinkedHashSet(Collection<? extends E> coll) {
		if (coll == null) {
			return null;
		}
		return new LinkedHashSet<>(coll);
	}

	/**
	 * Creates an {@link EnumSet} for the argument enum class and values.
	 * <p>
	 * This method can be useful when creating a clone of collections that contain enumeration values.
	 * <p>
	 * The enumeration class argument is required for proper operation, as in order to construct an {@link EnumSet}, the
	 * enum class is required. If the argument collection is empty, this would not be available.
	 * 
	 * @param <E>
	 *            The type of the element enums.
	 * @param enumclass
	 *            The enum type.
	 * @param values
	 *            The values to initialize the resulting enum set with.
	 * @return The created enum set, or <code>null</code> if the argument collection is <code>null</code>.
	 */
	public static <E extends Enum<E>> EnumSet<E> cloneEnumSet(Class<E> enumclass, Collection<E> values) {
		if (values == null) {
			return null;
		}
		if (values.isEmpty()) {
			return EnumSet.noneOf(enumclass);
		}
		return EnumSet.copyOf(values);
	}

	/**
	 * Sneakily throws the argument throwable.
	 * <p>
	 * This method can be used to throw checked exceptions in a function context that doesn't declare it. In general,
	 * this functionality should be very rarely used, and one should not create an API that sneakily throws exceptions,
	 * as callers cannot catch them by explicitly declaring it.
	 * <p>
	 * A good use-case for this:
	 * 
	 * <pre>
	 * private Object calculate(Object k) throws IOException {
	 * 	return something;
	 * }
	 * 
	 * public Object computeSomething(Object key) {
	 * 	try {
	 * 		return map.computeIfAbsent(key, k -&gt; {
	 * 			try {
	 * 				return calculate(k);
	 * 			} catch (IOException e) {
	 * 				throw ObjectUtils.sneakyThrow(e);
	 * 			}
	 * 		});
	 * 	} catch (IOException e) {
	 * 		// the exception sneakily thrown by the computer function is caught by ourself.
	 * 		return null;
	 * 	}
	 * }
	 * </pre>
	 * 
	 * In the above example, we throw the {@link IOException} sneakily, and catch it in the enclosing code block. This
	 * is necessary, as the computer function doesn't allow for a checked exception to be thrown. In this case we deem
	 * that it is acceptable to use sneaky throwing, but callers should make sure that they catch the exception as well.
	 * <p>
	 * It is a good practice, that if a code sneakily throws some exception, the maintainer of the enclosing code should
	 * either explicitly catch the sneakily thrown exception, or declare it in the API method declaration.
	 * <p>
	 * Throwing the return value of the {@link #sneakyThrow(Throwable)} function is useful so the Java compiler doesn't
	 * issue an error that the method doesn't have a return value. That throw declaration actually has no effect, as the
	 * exception is thrown inside the {@link #sneakyThrow(Throwable)} function.
	 * 
	 * @param t
	 *            The throwable to throw.
	 * @return Never returns properly.
	 */
	public static RuntimeException sneakyThrow(Throwable t) {
		sneakyThrowImpl(t);
		return null;
	}

	/**
	 * Gets the enumeration type of the argument {@link EnumMap}.
	 * <p>
	 * The method will retrieve the first key of the map and return the enum class of it. The method handles if the enum
	 * key is an anonymous inner class, and returns the enclosing enumeration type appropriately.
	 * <p>
	 * If the argument map is empty, the method will attempt to serialize the argument map to an
	 * {@link ObjectOutputStream}. It will intercept the type serialization and will return the found enum type. It is
	 * strongly recommended that the argument has the type {@link EnumMap}, and is not a subclass of it. This method is
	 * somewhat a hack, but no {@link EnumMap} implementation has failed with it yet, and is not expected to. If the
	 * enum type fails to be detected, an {@link UnsupportedOperationException} is thrown.
	 * 
	 * @param enummap
	 *            The enum map.
	 * @return The enum class type or <code>null</code> if the argument is <code>null</code>.
	 * @throws UnsupportedOperationException
	 *             If the enum type detection failed. (Usually never happens.)
	 */
	@SuppressWarnings("unchecked")
	public static Class<? extends Enum<?>> getEnumMapEnumType(EnumMap<?, ?> enummap)
			throws UnsupportedOperationException {
		if (enummap == null) {
			return null;
		}
		if (!enummap.isEmpty()) {
			Enum<?> firstkey = enummap.keySet().iterator().next();
			Class<?> firstkeyclass = firstkey.getClass();
			if (firstkeyclass.isAnonymousClass()) {
				//if the enum value is an anonymous inner class
				firstkeyclass = firstkeyclass.getSuperclass();
			}
			return (Class<? extends Enum<?>>) firstkeyclass;
		}

		class EnumClassFoundException extends RuntimeException {
			private static final long serialVersionUID = 1L;

			Class<?> enumType;

			public EnumClassFoundException(Class<?> enumtype) {
				super(null, null, false, false);
				this.enumType = enumtype;
			}
		}

		try (ObjectOutputStream oos = new ObjectOutputStream(StreamUtils.nullOutputStream()) {
			@Override
			protected void annotateClass(Class<?> cl) throws IOException {
				if (cl.isEnum()) {
					//the key type may never be an anonymous inner class, so no need to get the super
					throw new EnumClassFoundException(cl);
				}
			}
		}) {
			oos.writeObject(enummap);
		} catch (EnumClassFoundException e) {
			return (Class<? extends Enum<?>>) e.enumType;
		} catch (Exception e) {
			throw new UnsupportedOperationException("Failed to determine EnumMap key type.", e);
		}
		throw new UnsupportedOperationException("Failed to determine EnumMap key type.");
	}

	private static final Object MAP_GET_DEFAULT_NOT_PRESENT_PLACEHOLDER = new Object();

	private ObjectUtils() {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("unchecked")
	private static <T extends Throwable> RuntimeException sneakyThrowImpl(Throwable t) throws T {
		throw (T) t;
	}

	private static <RType, LType> boolean iterablesEqualsNonNull(Iterable<? extends LType> l,
			Iterable<? extends RType> r, BiPredicate<? super LType, ? super RType> comparator) {
		Objects.requireNonNull(comparator, "comparator");
		Iterator<? extends LType> lit = l.iterator();
		Iterator<? extends RType> rit = r.iterator();
		Objects.requireNonNull(lit, "left iterator");
		Objects.requireNonNull(rit, "right iterator");
		while (lit.hasNext()) {
			if (!rit.hasNext()) {
				return false;
			}
			if (!comparator.test(lit.next(), rit.next())) {
				return false;
			}
		}
		return !rit.hasNext();
	}

	private static final class ConsumerForwardingAlwaysTriPredicate<T, VL, VR> implements TriPredicate<T, VL, VR> {
		private final TriConsumer<? super T, ? super VL, ? super VR> action;

		private ConsumerForwardingAlwaysTriPredicate(TriConsumer<? super T, ? super VL, ? super VR> action) {
			this.action = action;
		}

		@Override
		public boolean test(T t, VL vl, VR vr) {
			action.accept(t, vl, vr);
			return true;
		}
	}
}
