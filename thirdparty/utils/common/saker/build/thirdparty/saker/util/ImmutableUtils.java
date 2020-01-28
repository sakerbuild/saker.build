package saker.build.thirdparty.saker.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
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
import java.util.RandomAccess;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import saker.apiextract.api.PublicApi;

/**
 * Utility class providing access to various immutability related functionality.
 * <p>
 * This class provides functions for creating immutable collections. Immutable collections hold their own backing
 * collections, and will create a copy of their argument, so unless otherwise noted in the creating function, modifying
 * the source collection/array will have no effect on the returned collections. All methods that have
 * <code>Immutable</code> in their name will create immutable collections, and create a copy of the objects passed to it
 * unless otherwise noted.
 * <p>
 * If <code>makeImmutable</code> functions are called with <code>null</code> argument, <code>null</code> will be
 * returned instead of empty immutable collections. (Unless otherwise noted in the documentation of the method.)
 * <p>
 * The class provides functions for creating unmodifiable view collections. That is, a wrapper collection is returned
 * for an argument that prevents modifications to the underlying collection. Usually, any modifications made to the
 * underlying collection through the passed argument will be visible through the unmodifiable view collection as well.
 * Methods that begin with <code>unmodifiable</code> in their name will create such unmodifiable views.
 * <p>
 * If <code>unmodifiable</code> methods are called with <code>null</code> argument, <code>null</code> will be returned,
 * unless otherwise noted.
 * <p>
 * This class provides functions for creating singleton collections. Singleton collections are immutable. Methods that
 * begin with <code>singleton</code> in their name will create such objects.
 * <p>
 * This class provides functions for creating empty collections. Empty collections are immutable. Methods that begin
 * with <code>empty</code> in their name will create such objects.
 * <p>
 * Generally, functions that return a specific interface will return an object that only implements that declared
 * interface. E.g. a function that returns {@link SortedSet} will not return an object that implements
 * {@link NavigableSet}. This is not a strict requirement, and users should not rely on this fact. ({@link List} may
 * implement {@link RandomAccess} as well.)
 * <p>
 * All returned stateless objects in this class are {@link Externalizable}, unless otherwise noted in the corresponding
 * method documentation.
 * <p>
 * In general it is useful to use the functions in this class instead of the ones provided by the Java standard library,
 * as the fact that the returned objects are {@link Externalizable}, can significantly improve performance in some
 * cases.
 * <p>
 * Creating immutable collections for sorted collections (sets and maps) can improve performance, as they are usually
 * backed by a random access array that takes up less space than a full {@link TreeMap} or such, and can provide more
 * efficient method implementations in some cases.
 */
@PublicApi
public class ImmutableUtils {
	//XXX implement more efficient immutable collections when appropriate
	//XXX add functions for handling ConcurrentMap and ConcurrentNavigableMap

	/**
	 * Creates an immutable list consisting of the argument objects.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The element objects.
	 * @return An immutable list or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <E> List<E> makeImmutableList(E[] objects) {
		if (objects == null) {
			return null;
		}
		if (objects.length == 0) {
			return emptyList();
		}
		if (objects.length == 1) {
			return singletonList(objects[0]);
		}
		return asUnmodifiableArrayList(objects.clone());
	}

	/**
	 * Creates an immutable list consisting of the argument objects.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The element objects.
	 * @return An immutable list or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <E> List<E> makeImmutableList(Iterable<? extends E> objects) {
		if (objects == null) {
			return null;
		}
		List<? extends E> narraylist = ObjectUtils.newArrayList(objects);
		if (narraylist.isEmpty()) {
			return emptyList();
		}
		if (narraylist.size() == 1) {
			return singletonList(narraylist.get(0));
		}
		return unmodifiableList(narraylist);
	}

	/**
	 * Creates an immutable list consisting of the argument objects.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The element objects.
	 * @return An immutable list or <code>null</code> if the argument is <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <E> List<E> makeImmutableList(Collection<? extends E> objects) {
		if (objects == null) {
			return null;
		}
		if (objects.isEmpty()) {
			return emptyList();
		}
		Object[] asarray = objects.toArray();
		if (asarray.length == 1) {
			return (List<E>) singletonList(asarray[0]);
		}
		return (List<E>) asUnmodifiableArrayList(asarray);
	}

	/**
	 * Creates an immutable hash set consisting of the argument objects.
	 * <p>
	 * The elements will be compared by {@linkplain Object#equals(Object) equality}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The element objects.
	 * @return An immutable hash set or <code>null</code> if the argument is <code>null</code>.
	 * @see HashSet
	 */
	public static <E> Set<E> makeImmutableHashSet(E[] objects) {
		if (objects == null) {
			return null;
		}
		if (objects.length == 0) {
			return emptySet();
		}
		if (objects.length == 1) {
			return singletonSet(objects[0]);
		}
		return unmodifiableSet(ObjectUtils.newHashSet(objects));
	}

	/**
	 * Creates an immutable hash set consisting of the argument objects.
	 * <p>
	 * The elements will be compared by {@linkplain Object#equals(Object) equality}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The element objects.
	 * @return An immutable hash set or <code>null</code> if the argument is <code>null</code>.
	 * @see HashSet
	 */
	public static <E> Set<E> makeImmutableHashSet(Iterable<? extends E> objects) {
		if (objects == null) {
			return null;
		}
		Set<? extends E> nset = ObjectUtils.newHashSet(objects);
		if (nset.isEmpty()) {
			return emptySet();
		}
		if (nset.size() == 1) {
			return singletonSet(nset.iterator().next());
		}
		return unmodifiableSet(nset);
	}

	/**
	 * Creates an immutable hash set consisting of the argument objects.
	 * <p>
	 * The elements will be compared by {@linkplain Object#equals(Object) equality}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The element objects.
	 * @return An immutable hash set or <code>null</code> if the argument is <code>null</code>.
	 * @see HashSet
	 */
	public static <E> Set<E> makeImmutableHashSet(Collection<? extends E> objects) {
		if (objects == null) {
			return null;
		}
		if (objects.isEmpty()) {
			return emptySet();
		}
		if (objects.size() == 1) {
			return singletonSet(objects.iterator().next());
		}
		return unmodifiableSet(ObjectUtils.newHashSet(objects));
	}

	/**
	 * Creates an immutable linked hash set consisting of the argument objects.
	 * <p>
	 * The linked hash set differs from the plain hash set that it has a predictable iteration order. This iteration
	 * order is the same as the argument objects. The iteration order stays the same between serialization and
	 * deserialization.
	 * <p>
	 * The resulting set implementation may not contain an actual linked data structure, but the same name is kept to
	 * keep the established semantic association with the preexisting class.
	 * <p>
	 * The elements will be compared by {@linkplain Object#equals(Object) equality}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The element objects.
	 * @return An immutable linked hash set or <code>null</code> if the argument is <code>null</code>.
	 * @see LinkedHashSet
	 */
	public static <E> Set<E> makeImmutableLinkedHashSet(E[] objects) {
		if (objects == null) {
			return null;
		}
		if (objects.length == 0) {
			return emptySet();
		}
		if (objects.length == 1) {
			return singletonSet(objects[0]);
		}
		return unmodifiableSet(ObjectUtils.newLinkedHashSet(objects));
	}

	/**
	 * Creates an immutable linked hash set consisting of the argument objects.
	 * <p>
	 * The linked hash set differs from the plain hash set that it has a predictable iteration order. This iteration
	 * order is the same as the argument objects. The iteration order stays the same between serialization and
	 * deserialization.
	 * <p>
	 * The resulting set implementation may not contain an actual linked data structure, but the same name is kept to
	 * keep the established semantic association with the preexisting class.
	 * <p>
	 * The elements will be compared by {@linkplain Object#equals(Object) equality}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The element objects.
	 * @return An immutable linked hash set or <code>null</code> if the argument is <code>null</code>.
	 * @see LinkedHashSet
	 */
	public static <E> Set<E> makeImmutableLinkedHashSet(Iterable<? extends E> objects) {
		if (objects == null) {
			return null;
		}
		Set<? extends E> nset = ObjectUtils.newLinkedHashSet(objects);
		if (nset.isEmpty()) {
			return emptySet();
		}
		if (nset.size() == 1) {
			return singletonSet(nset.iterator().next());
		}
		return unmodifiableSet(nset);
	}

	/**
	 * Creates an immutable linked hash set consisting of the argument objects.
	 * <p>
	 * The linked hash set differs from the plain hash set that it has a predictable iteration order. This iteration
	 * order is the same as the argument objects. The iteration order stays the same between serialization and
	 * deserialization.
	 * <p>
	 * The resulting set implementation may not contain an actual linked data structure, but the same name is kept to
	 * keep the established semantic association with the preexisting class.
	 * <p>
	 * The elements will be compared by {@linkplain Object#equals(Object) equality}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The element objects.
	 * @return An immutable linked hash set or <code>null</code> if the argument is <code>null</code>.
	 * @see LinkedHashSet
	 */
	public static <E> Set<E> makeImmutableLinkedHashSet(Collection<? extends E> objects) {
		if (objects == null) {
			return null;
		}
		if (objects.isEmpty()) {
			return emptySet();
		}
		if (objects.size() == 1) {
			return singletonSet(objects.iterator().next());
		}
		return unmodifiableSet(ObjectUtils.newLinkedHashSet(objects));
	}

	/**
	 * Creates an immutable identity hash set consisting of the argument objects.
	 * <p>
	 * The elements will be compared by identity equality. (I.e. The <code>==</code> operator.)
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The element objects.
	 * @return An immutable identity hash set or <code>null</code> if the argument is <code>null</code>.
	 * @see IdentityHashMap
	 */
	public static <E> Set<E> makeImmutableIdentityHashSet(E[] objects) {
		if (objects == null) {
			return null;
		}
		if (objects.length == 0) {
			return emptySet();
		}
		if (objects.length == 1) {
			return singletonIdentityHashSet(objects[0]);
		}
		return unmodifiableSet(ObjectUtils.newIdentityHashSet(objects));
	}

	/**
	 * Creates an immutable identity hash set consisting of the argument objects.
	 * <p>
	 * The elements will be compared by identity equality. (I.e. The <code>==</code> operator.)
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The element objects.
	 * @return An immutable identity hash set or <code>null</code> if the argument is <code>null</code>.
	 * @see IdentityHashMap
	 */
	public static <E> Set<E> makeImmutableIdentityHashSet(Iterable<? extends E> objects) {
		if (objects == null) {
			return null;
		}
		Set<? extends E> nset = ObjectUtils.newIdentityHashSet(objects);
		if (nset.isEmpty()) {
			return emptySet();
		}
		if (nset.size() == 1) {
			return singletonIdentityHashSet(nset.iterator().next());
		}
		return unmodifiableSet(nset);
	}

	/**
	 * Creates an immutable identity hash set consisting of the argument objects.
	 * <p>
	 * The elements will be compared by identity equality. (I.e. The <code>==</code> operator.)
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The element objects.
	 * @return An immutable identity hash set or <code>null</code> if the argument is <code>null</code>.
	 * @see IdentityHashMap
	 */
	public static <E> Set<E> makeImmutableIdentityHashSet(Collection<? extends E> objects) {
		if (objects == null) {
			return null;
		}
		if (objects.isEmpty()) {
			return emptySet();
		}
		if (objects.size() == 1) {
			return singletonIdentityHashSet(objects.iterator().next());
		}
		return unmodifiableSet(ObjectUtils.newIdentityHashSet(objects));
	}

	/**
	 * Creates a new immutable navigable set for the argument objects.
	 * <p>
	 * The returned set uses the natural order. If the argument contains duplicate elements, only one of them will be
	 * present in the resulting set.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The elements to construct a set of.
	 * @return An immutable navigable set based on the elements, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static <E> NavigableSet<E> makeImmutableNavigableSet(E[] objects) {
		if (objects == null) {
			return null;
		}
		if (objects.length == 0) {
			return emptyNavigableSet();
		}
		if (objects.length == 1) {
			return singletonNavigableSet(objects[0]);
		}
		return unmodifiableNavigableSet(ObjectUtils.addAll(new TreeSet<>(), objects));
	}

	/**
	 * Creates a new immutable navigable set for the argument objects and the the given comparator.
	 * <p>
	 * The returned set uses the given comparator for ordering. If the argument contains duplicate elements, only one of
	 * them will be present in the resulting set.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The elements to construct a set of.
	 * @param comparator
	 *            The comparator to order the created navigable set.
	 * @return An immutable navigable set based on the elements, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static <E> NavigableSet<E> makeImmutableNavigableSet(E[] objects, Comparator<? super E> comparator) {
		if (objects == null) {
			return null;
		}
		if (objects.length == 0) {
			return emptyNavigableSet(comparator);
		}
		if (objects.length == 1) {
			return singletonNavigableSet(objects[0], comparator);
		}
		return unmodifiableNavigableSet(ObjectUtils.addAll(new TreeSet<>(comparator), objects));
	}

	/**
	 * Creates a new immutable navigable set for the argument objects.
	 * <p>
	 * The returned set uses the natural order. If the argument contains duplicate elements, only one of them will be
	 * present in the resulting set.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The elements to construct a set of.
	 * @return An immutable navigable set based on the elements, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static <E> NavigableSet<E> makeImmutableNavigableSet(Collection<? extends E> objects) {
		if (objects == null) {
			return null;
		}
		if (objects.isEmpty()) {
			return emptyNavigableSet();
		}
		if (objects.size() == 1) {
			return singletonNavigableSet(objects.iterator().next());
		}
		return unmodifiableNavigableSet(new TreeSet<>(objects));
	}

	/**
	 * Creates a new immutable navigable set for the argument objects and the the given comparator.
	 * <p>
	 * The returned set uses the given comparator for ordering. If the argument contains duplicate elements, only one of
	 * them will be present in the resulting set.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The elements to construct a set of.
	 * @param comparator
	 *            The comparator to order the created navigable set.
	 * @return An immutable navigable set based on the elements, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static <E> NavigableSet<E> makeImmutableNavigableSet(Collection<? extends E> objects,
			Comparator<? super E> comparator) {
		if (objects == null) {
			return null;
		}
		if (objects.isEmpty()) {
			return emptyNavigableSet(comparator);
		}
		if (objects.size() == 1) {
			return singletonNavigableSet(objects.iterator().next(), comparator);
		}
		return unmodifiableNavigableSet(ObjectUtils.addAll(new TreeSet<>(comparator), objects));
	}

	/**
	 * Creates a new immutable navigable set for the argument objects.
	 * <p>
	 * The returned set has the same ordering as the argument.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param objects
	 *            The elements to construct a set of.
	 * @return An immutable navigable set based on the elements, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <E> NavigableSet<E> makeImmutableNavigableSet(SortedSet<E> objects) {
		if (objects == null) {
			return null;
		}
		if (objects.isEmpty()) {
			return emptyNavigableSet(objects.comparator());
		}
		Object[] asarray = objects.toArray();
		if (asarray.length == 1) {
			return singletonNavigableSet((E) asarray[0], objects.comparator());
		}
		return unmodifiableNavigableSet(asarray, (Comparator) objects.comparator());
	}

	/**
	 * Creates a new immutable hash map based on the argument map.
	 * <p>
	 * The keys will be compared by equality. (See {@link Objects#equals(Object, Object)} )
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map.
	 * @return An immutable hash map based on the entries, or <code>null</code> if the argument is <code>null</code>.
	 * @see HashMap
	 */
	public static <K, V> Map<K, V> makeImmutableHashMap(Map<? extends K, ? extends V> map) {
		if (map == null) {
			return null;
		}
		if (map.isEmpty()) {
			return emptyMap();
		}
		if (map.size() == 1) {
			Entry<? extends K, ? extends V> fentry = map.entrySet().iterator().next();
			return singletonMap(fentry.getKey(), fentry.getValue());
		}
		//XXX could be more efficient
		return unmodifiableMap(new HashMap<>(map));
	}

	/**
	 * Creates a new immutable identity hash map based on the argument map.
	 * <p>
	 * The keys will be compared by identity equality. (I.e. The <code>==</code> operator.)
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map.
	 * @return An immutable identity hash map based on the entries, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 * @see IdentityHashMap
	 */
	public static <K, V> Map<K, V> makeImmutableIdentityHashMap(Map<? extends K, ? extends V> map) {
		if (map == null) {
			return null;
		}
		if (map.isEmpty()) {
			return emptyMap();
		}
		if (map.size() == 1) {
			Entry<? extends K, ? extends V> fentry = map.entrySet().iterator().next();
			return singletonIdentityHashMap(fentry.getKey(), fentry.getValue());
		}
		//XXX could be more efficient
		return unmodifiableMap(new IdentityHashMap<>(map));
	}

	/**
	 * Creates a new immutable linked hash map based on the argument map.
	 * <p>
	 * The linked hash map differs from the plain hash map that it has a predictable iteration order. This iteration
	 * order is the same as the argument map. The iteration order stays the same between serialization and
	 * deserialization.
	 * <p>
	 * The resulting map implementation may not contain an actual linked data structure, but the same name is kept to
	 * keep the established semantic association with the preexisting class.
	 * <p>
	 * The keys will be compared by {@linkplain Object#equals(Object) equality}.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map.
	 * @return An immutable linked hash map based on the entries, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 * @see LinkedHashMap
	 */
	public static <K, V> Map<K, V> makeImmutableLinkedHashMap(Map<? extends K, ? extends V> map) {
		if (map == null) {
			return null;
		}
		if (map.isEmpty()) {
			return emptyMap();
		}
		if (map.size() == 1) {
			Entry<? extends K, ? extends V> fentry = map.entrySet().iterator().next();
			return singletonMap(fentry.getKey(), fentry.getValue());
		}
		//XXX could be more efficient performance and memory wise
		return unmodifiableMap(new LinkedHashMap<>(map));
	}

	/**
	 * Creates a new immutable navigable map based on the argument key-value arrays.
	 * <p>
	 * The arrays must have same length. Each key at a given will be mapped to the value in the value array at the same
	 * index.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param keys
	 *            The array of keys.
	 * @param values
	 *            The array of values.
	 * @return An immutable navigable map with entries constructed from the argument arrays.
	 * @throws IllegalArgumentException
	 *             If the arrays have different lengths.
	 * @throws NullPointerException
	 *             If any of the arrays are <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> makeImmutableNavigableMap(K[] keys, V[] values)
			throws IllegalArgumentException, NullPointerException {
		return makeImmutableNavigableMap(keys, values, null);
	}

	/**
	 * Creates a new immutable navigable map based on the argument key-value arrays and ordered by the specified
	 * comparator.
	 * <p>
	 * The arrays must have same length. Each key at a given will be mapped to the value in the value array at the same
	 * index.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param keys
	 *            The array of keys.
	 * @param values
	 *            The array of values.
	 * @param comparator
	 *            The comparator to order the created navigable map.
	 * @return An immutable navigable map with entries constructed from the argument arrays and comparator.
	 * @throws IllegalArgumentException
	 *             If the arrays have different lengths.
	 * @throws NullPointerException
	 *             If any of the arrays are <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> makeImmutableNavigableMap(K[] keys, V[] values,
			Comparator<? super K> comparator) throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(keys, "keys");
		Objects.requireNonNull(values, "values");
		if (keys.length != values.length) {
			throw new IllegalArgumentException("Arrays have different lengths: " + keys.length + " - " + values.length);
		}
		if (keys.length == 1) {
			return singletonNavigableMap(keys[0], values[0], comparator);
		}
		//XXX implement as interlaced array backed navigable map
		TreeMap<K, V> resmap = new TreeMap<>(comparator);
		for (int i = 0; i < keys.length; i++) {
			resmap.put(keys[i], values[i]);
		}
		return unmodifiableNavigableMap(resmap);
	}

	/**
	 * Creates a new immutable navigable map based on the argument key-value lists.
	 * <p>
	 * The lists must have same length. Each key at a given index will be mapped to the value in the value array at the
	 * same index.
	 * <p>
	 * The lists doesn't need to be {@link RandomAccess}, they are accessed by this method using an iterator, and the
	 * key-value pairs are matched accordingly.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param keys
	 *            The list of keys.
	 * @param values
	 *            The list of values.
	 * @return An immutable navigable map with entries constructed from the argument lists.
	 * @throws IllegalArgumentException
	 *             If the lists have different lengths.
	 * @throws NullPointerException
	 *             If any of the lists are <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> makeImmutableNavigableMap(List<? extends K> keys, List<? extends V> values)
			throws IllegalArgumentException, NullPointerException {
		return makeImmutableNavigableMap(keys, values, null);
	}

	/**
	 * Creates a new immutable navigable map based on the argument key-value lists and ordered by the specified
	 * comparator.
	 * <p>
	 * The lists must have same length. Each key at a given index will be mapped to the value in the value array at the
	 * same index.
	 * <p>
	 * The lists doesn't need to be {@link RandomAccess}, they are accessed by this method using an iterator, and the
	 * key-value pairs are matched accordingly.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param keys
	 *            The list of keys.
	 * @param values
	 *            The list of values.
	 * @param comparator
	 *            The comparator to order the created navigable map.
	 * @return An immutable navigable map with entries constructed from the argument lists and comparator.
	 * @throws IllegalArgumentException
	 *             If the lists have different lengths.
	 * @throws NullPointerException
	 *             If any of the lists are <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> makeImmutableNavigableMap(List<? extends K> keys, List<? extends V> values,
			Comparator<? super K> comparator) throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(keys, "keys");
		Objects.requireNonNull(values, "values");
		int klen = keys.size();
		int vlen = values.size();
		if (klen != vlen) {
			throw new IllegalArgumentException("Lists have different lengths: " + klen + " - " + vlen);
		}
		//XXX implement as interlaced array backed navigable map
		TreeMap<K, V> resmap = new TreeMap<>(comparator);
		Iterator<? extends V> vit = values.iterator();
		Iterator<? extends K> kit = keys.iterator();
		while (kit.hasNext()) {
			K k = kit.next();
			if (!vit.hasNext()) {
				throw new IllegalArgumentException("Lists have different lengths, more keys than values.");
			}
			V v = vit.next();
			resmap.put(k, v);
		}
		if (vit.hasNext()) {
			throw new IllegalArgumentException("Lists have different lengths, more values than keys.");
		}

		return unmodifiableNavigableMap(resmap);
	}

	/**
	 * Creates a new immutable navigable map for the argument map.
	 * <p>
	 * The returned map uses the natural order. If the argument contains duplicate keys, only one of them will be
	 * present in the resulting set.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map create the navigable map of.
	 * @return An immutable navigable map based on the entries, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> makeImmutableNavigableMap(Map<? extends K, ? extends V> map) {
		if (map == null) {
			return null;
		}
		if (map.isEmpty()) {
			return emptyNavigableMap();
		}
		if (map.size() == 1) {
			Entry<? extends K, ? extends V> fentry = map.entrySet().iterator().next();
			return singletonNavigableMap(fentry.getKey(), fentry.getValue());
		}
		return unmodifiableNavigableMap(new TreeMap<>(map));
	}

	/**
	 * Creates a new immutable navigable map for the argument map with the specified order.
	 * <p>
	 * The returned map has the ordering specified by the argument comparator. If the argument contains duplicate keys,
	 * only one of them will be present in the resulting set.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map create the navigable map of.
	 * @param comparator
	 *            The comparator that specifies the returned map ordering. Passing <code>null</code> means the natural
	 *            order.
	 * @return An immutable navigable map based on the entries, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> makeImmutableNavigableMap(Map<? extends K, ? extends V> map,
			Comparator<? super K> comparator) {
		if (comparator == null) {
			return makeImmutableNavigableMap(map);
		}
		if (map == null) {
			return null;
		}
		if (map.isEmpty()) {
			return emptyNavigableMap(comparator);
		}
		if (map.size() == 1) {
			Entry<? extends K, ? extends V> fentry = map.entrySet().iterator().next();
			return singletonNavigableMap(fentry.getKey(), fentry.getValue(), comparator);
		}
		NavigableMap<K, V> resultmap = new TreeMap<>(comparator);
		resultmap.putAll(map);
		return unmodifiableNavigableMap(resultmap);
	}

	/**
	 * Creates a new immutable navigable map for the argument map.
	 * <p>
	 * The returned map has the same ordering as the argument.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map create the navigable map of.
	 * @return An immutable navigable map based on the entries, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> makeImmutableNavigableMap(SortedMap<K, ? extends V> map) {
		if (map == null) {
			return null;
		}
		if (map.isEmpty()) {
			return emptyNavigableMap(map.comparator());
		}
		if (map.size() == 1) {
			Entry<K, ? extends V> fentry = map.entrySet().iterator().next();
			return singletonNavigableMap(fentry.getKey(), fentry.getValue(), map.comparator());
		}
		return unmodifiableNavigableMap(new TreeMap<>(map));
	}

	/**
	 * Creates a new immutable navigable map for the argument map.
	 * <p>
	 * The returned map has the same ordering as the argument.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map create the navigable map of.
	 * @return An immutable navigable map based on the entries, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> makeImmutableNavigableMap(NavigableMap<K, ? extends V> map) {
		if (map == null) {
			return null;
		}
		if (map.isEmpty()) {
			return emptyNavigableMap(map.comparator());
		}
		if (map.size() == 1) {
			Entry<K, ? extends V> fentry = map.firstEntry();
			return singletonNavigableMap(fentry.getKey(), fentry.getValue(), map.comparator());
		}
		return unmodifiableNavigableMap(new TreeMap<>(map));
	}

	/**
	 * Creates a new immutable map entry for the given key-value pair.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param key
	 *            The key.
	 * @param value
	 *            The value.
	 * @return An immutable map entry containing the arguments.
	 */
	public static <K, V> Map.Entry<K, V> makeImmutableMapEntry(K key, V value) {
		return new ImmutableMapEntry<>(key, value);
	}

	/**
	 * Creates a new immutable map entry for the given key-value pair.
	 * <p>
	 * The entry uses identity comparison semantics similar to {@link IdentityHashMap}.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param key
	 *            The key.
	 * @param value
	 *            The value.
	 * @return An immutable map entry containing the arguments.
	 */
	public static <K, V> Map.Entry<K, V> makeImmutableIdentityMapEntry(K key, V value) {
		return new ImmutableIdentityMapEntry<>(key, value);
	}

	/**
	 * Creates an immutable map entry for the given map entry.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param entry
	 *            The map entry to create an immutable instance based on.
	 * @return The created map entry, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <K, V> Map.Entry<K, V> makeImmutableMapEntry(Map.Entry<? extends K, ? extends V> entry) {
		if (entry == null) {
			return null;
		}
		return new ImmutableMapEntry<>(entry.getKey(), entry.getValue());
	}

	/**
	 * Creates an unmodifiable list view to the argument array.
	 * <p>
	 * This method works similarly to {@link #unmodifiableArrayList(Object[])}, but is vararg and differently named for
	 * convenience.
	 * <p>
	 * The method is named with similarity to {@link Arrays#asList(Object...)}, in order to explicitly differentiate
	 * this method from the other <code>unmodifiable</code> methods, as this uses a vararg parameter. Otherwise it could
	 * easily cause confusion when mistakenly invoked with various types of parameters, and result in some unexpected
	 * compilation errors which are avoidable this way.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param items
	 *            The array of elements.
	 * @return The unmodifiable view, or <code>null</code> if the argument is <code>null</code>.
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <E> List<E> asUnmodifiableArrayList(E... items) {
		if (items == null) {
			return null;
		}
		if (items.length == 0) {
			return emptyList();
		}
		return ImmutableArrayList.create(items);
	}

	/**
	 * Gets an unmodifiable view to the argument collection.
	 * <p>
	 * The {@link Collection}, {@link Set}, and {@link List} classes define different behaviour of their
	 * {@link Object#hashCode()} and {@link Object#equals(Object)} methods. In order to preserve their behaviour, this
	 * method checks the implemented interfaces of the argument object.
	 * <p>
	 * If the argument collection implements {@link Set}, and unmodifiable set will be returned.
	 * <p>
	 * If the argument collection implements {@link List}, and unmodifiable list will be returned.
	 * <p>
	 * In any other cases and unmodifiable collection will be returned that doesn't implement any subinterfaces of
	 * {@link Collection}. This means that comparing the resulting collection by hash code or equality may end in
	 * unexpected results. In this case some of the requirements mandatad by the hash code and equals functions are
	 * violated.
	 * 
	 * @param collection
	 *            The collection.
	 * @return The unmodifiable view, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <E> Collection<E> unmodifiableCollection(Collection<? extends E> collection) {
		if (collection == null) {
			return null;
		}
		if (collection instanceof Set) {
			return new UnmodifiableSet<>((Set<? extends E>) collection);
		}
		if (collection instanceof List) {
			return new UnmodifiableList<>((List<? extends E>) collection);
		}
		//no empty check, as we need a valid hashcode and equals
		return new UnmodifiableCollection<>(collection);
	}

	/**
	 * Gets an unmodifiable view to the argument list.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param list
	 *            The list.
	 * @return The unmodifiable view, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <E> List<E> unmodifiableList(List<? extends E> list) {
		if (list == null) {
			return null;
		}
		if (list instanceof RandomAccess) {
			return new UnmodifiableRandomAccessList<>(list);
		}
		return new UnmodifiableList<>(list);
	}

	/**
	 * Creates an unmodifiable list view to the argument array.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param items
	 *            The array of elements.
	 * @return The unmodifiable view, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <E> List<E> unmodifiableArrayList(E[] items) {
		if (items == null) {
			return null;
		}
		if (items.length == 0) {
			return emptyList();
		}
		return ImmutableArrayList.create(items);
	}

	/**
	 * Creates an unmodifiable list view to the argument array in the given range.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param items
	 *            The array of elements.
	 * @param start
	 *            The starting index of the range in the array. (inclusive)
	 * @param end
	 *            The end index for the range in the array. (exclusive)
	 * @return The unmodifiable view, or <code>null</code> if the argument is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of the arrays bounds.
	 */
	public static <E> List<E> unmodifiableArrayList(E[] items, int start, int end) throws IndexOutOfBoundsException {
		if (items == null) {
			return null;
		}
		ArrayUtils.requireArrayStartEndRange(items, start, end);
		if (start == end) {
			return emptyList();
		}
		return ImmutableRangeArrayList.create(items, start, end);
	}

	/**
	 * Creates an unmodifiable array list view to the argument array.
	 * <p>
	 * The argument should have an array type, which may be a primitive array.
	 * 
	 * @param array
	 *            The array of elements.
	 * @return The unmodifiable view, or <code>null</code> if the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is not an array.
	 */
	public static List<?> unmodifiableReflectionArrayList(Object array) throws IllegalArgumentException {
		if (array == null) {
			return null;
		}
		//the following call checks if an array
		int len = Array.getLength(array);
		if (len == 0) {
			return emptyList();
		}
		return new ImmutableReflectionArrayList(array, len);
	}

	/**
	 * Creates an unmodifiable array list view to the argument array in the specified range.
	 * <p>
	 * The argument should have an array type, which may be a primitive array.
	 * 
	 * @param array
	 *            The array of elements.
	 * @param offset
	 *            The offset where the returned view should start.
	 * @param length
	 *            The number of elements in the array view.
	 * @return The unmodifiable view, or <code>null</code> if the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is not an array.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static List<?> unmodifiableReflectionArrayList(Object array, int offset, int length)
			throws IllegalArgumentException, IndexOutOfBoundsException {
		if (array == null) {
			return null;
		}
		if (length == 0) {
			return emptyList();
		}
		int arraylen = Array.getLength(array);
		ArrayUtils.requireArrayRangeLength(arraylen, offset, length);
		return new ImmutableReflectionOffsetArrayList(array, offset, length);
	}

	/**
	 * Gets an unmodifiable view to the argument set.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param set
	 *            The set.
	 * @return The unmodifiable view, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <E> Set<E> unmodifiableSet(Set<? extends E> set) {
		if (set == null) {
			return null;
		}
		return new UnmodifiableSet<>(set);
	}

	/**
	 * Gets an unmodifiable view to the argument sorted set.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param set
	 *            The set.
	 * @return The unmodifiable view, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <E> SortedSet<E> unmodifiableSortedSet(SortedSet<E> set) {
		if (set == null) {
			return null;
		}
		return new UnmodifiableSortedSet<>(set);
	}

	/**
	 * Gets an unmodifiable view to the argument navigable set.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param set
	 *            The set.
	 * @return The unmodifiable view, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <E> NavigableSet<E> unmodifiableNavigableSet(NavigableSet<E> set) {
		if (set == null) {
			return null;
		}
		return new UnmodifiableNavigableSet<>(set);
	}

	/**
	 * Creates a new navigable set that is backed by the argument array of elements.
	 * <p>
	 * The elements <b>must</b> be {@linkplain ObjectUtils#isStrictlySorted(Iterable, Comparator) stricly ordered} by
	 * the {@linkplain Comparator#naturalOrder() natural order}.
	 * <p>
	 * Callers should not modify the contents of the argument array after the result set has been constructed.
	 * <p>
	 * If the above requirements are violated, the code that uses the returned set may experience unexpected results. If
	 * the elements are not stricly sorted, the lookup methods may not work properly in the returned set.
	 * <p>
	 * If you might possibly modify the argument array after this method returns, consider using
	 * {@link #makeImmutableNavigableSet(Object[])} instead, which creates its own copy of elements.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param elements
	 *            The backing elements for the created navigable set.
	 * @return An unmodifiable navigable set backed by the argument array, ordered by natural order.
	 */
	public static <E> NavigableSet<E> unmodifiableNavigableSet(E[] elements) {
		if (elements == null) {
			return null;
		}
		if (elements.length == 0) {
			return emptyNavigableSet();
		}
		return unmodifiableNavigableSet(elements, null);
	}

	/**
	 * Creates a new navigable set that is backed by the elements in the argument array region.
	 * <p>
	 * The elements <b>must</b> be {@linkplain ObjectUtils#isStrictlySorted(Iterable, Comparator) stricly ordered} by
	 * the {@linkplain Comparator#naturalOrder() natural order}.
	 * <p>
	 * Callers should not modify the contents of the argument array after the result set has been constructed.
	 * <p>
	 * If the above requirements are violated, the code that uses the returned set may experience unexpected results. If
	 * the elements are not stricly sorted, the lookup methods may not work properly in the returned set.
	 * <p>
	 * If you might possibly modify the argument array range after this method returns, consider using
	 * {@link #unmodifiableNavigableSet(Object[]) unmodifiableNavigableSet(Arrays.copyOfRange(elements, start, end))}
	 * instead, by manually creating an owned copy of the range.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param elements
	 *            The backing elements for the created navigable set.
	 * @param start
	 *            The start index of the specified range. (inclusive)
	 * @param end
	 *            The end index of the specified range. (exclusive)
	 * @return An unmodifiable navigable set backed by the argument array range, ordered by natural order.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static <E> NavigableSet<E> unmodifiableNavigableSet(E[] elements, int start, int end)
			throws IndexOutOfBoundsException {
		return unmodifiableNavigableSet(elements, start, end, null);
	}

	/**
	 * Creates a new navigable set that is backed by the argument array of elements.
	 * <p>
	 * The elements <b>must</b> be {@linkplain ObjectUtils#isStrictlySorted(Iterable, Comparator) stricly ordered} by
	 * the argument comparator.
	 * <p>
	 * Callers should not modify the contents of the argument array after the result set has been constructed.
	 * <p>
	 * If the above requirements are violated, the code that uses the returned set may experience unexpected results. If
	 * the elements are not stricly sorted, the lookup methods may not work properly in the returned set.
	 * <p>
	 * If you might possibly modify the argument array after this method returns, consider using
	 * {@link #makeImmutableNavigableSet(Object[], Comparator)} instead, which creates its own copy of elements.
	 * 
	 * @param elements
	 *            The backing elements for the created navigable set.
	 * @param comparator
	 *            The comparator for the ordering of the set.
	 * @return An unmodifiable navigable set backed by the argument array, ordered by the argument comparator.
	 */
	public static <E> NavigableSet<E> unmodifiableNavigableSet(E[] elements, Comparator<? super E> comparator) {
		if (elements == null) {
			return null;
		}
		if (elements.length == 0) {
			return emptyNavigableSet(comparator);
		}
		return ImmutableArrayNavigableSet.create(comparator, elements);
	}

	/**
	 * Creates a new navigable set that is backed by the elements in the argument array region.
	 * <p>
	 * The elements <b>must</b> be {@linkplain ObjectUtils#isStrictlySorted(Iterable, Comparator) stricly ordered} by
	 * the argument comparator.
	 * <p>
	 * Callers should not modify the contents of the argument array after the result set has been constructed.
	 * <p>
	 * If the above requirements are violated, the code that uses the returned set may experience unexpected results. If
	 * the elements are not stricly sorted, the lookup methods may not work properly in the returned set.
	 * <p>
	 * If you might possibly modify the argument array range after this method returns, consider using
	 * {@link #unmodifiableNavigableSet(Object[], Comparator) unmodifiableNavigableSet(Arrays.copyOfRange(elements,
	 * start, end), comparator)} instead, by manually creating an owned copy of the range.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param elements
	 *            The backing elements for the created navigable set.
	 * @param start
	 *            The start index of the specified range. (inclusive)
	 * @param end
	 *            The end index of the specified range. (exclusive)
	 * @param comparator
	 *            The comparator for the ordering of the set.
	 * @return An unmodifiable navigable set backed by the argument array range, ordered by natural order.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static <E> NavigableSet<E> unmodifiableNavigableSet(E[] elements, int start, int end,
			Comparator<? super E> comparator) throws IndexOutOfBoundsException {
		if (elements == null) {
			return null;
		}
		ArrayUtils.requireArrayStartEndRange(elements, start, end);
		if (start == end) {
			return emptyNavigableSet(comparator);
		}
		return ImmutableArrayNavigableSet.create(comparator, elements, start, end);
	}

	/**
	 * Creates a new navigable set that is backed by the argument list of elements.
	 * <p>
	 * The elements <b>must</b> be {@linkplain ObjectUtils#isStrictlySorted(Iterable, Comparator) stricly ordered} by
	 * the {@linkplain Comparator#naturalOrder() natural order}.
	 * <p>
	 * Callers should not modify the contents of the argument list after the result set has been constructed.
	 * <p>
	 * If the above requirements are violated, the code that uses the returned set may experience unexpected results. If
	 * the elements are not stricly sorted, the lookup methods may not work properly in the returned set.
	 * <p>
	 * If you might possibly modify the argument array after this method returns, consider using
	 * {@link #makeImmutableNavigableSet(Collection)} instead, which creates its own copy of elements.
	 * <p>
	 * The argument list should support random access indexing for better performance.
	 * 
	 * @param elements
	 *            The backing elements for the created navigable set.
	 * @return An unmodifiable navigable set backed by the argument list, ordered by natural order.
	 */
	public static <E> NavigableSet<E> unmodifiableNavigableSet(List<? extends E> elements) {
		if (elements == null) {
			return null;
		}
		return unmodifiableNavigableSet(elements, null);
	}

	/**
	 * Creates a new navigable set that is backed by the argument list of elements.
	 * <p>
	 * The elements <b>must</b> be {@linkplain ObjectUtils#isStrictlySorted(Iterable, Comparator) stricly ordered} by
	 * the argument comparator.
	 * <p>
	 * Callers should not modify the contents of the argument list after the result set has been constructed.
	 * <p>
	 * If the above requirements are violated, the code that uses the returned set may experience unexpected results. If
	 * the elements are not stricly sorted, the lookup methods may not work properly in the returned set.
	 * <p>
	 * If you might possibly modify the argument list after this method returns, consider using
	 * {@link #makeImmutableNavigableSet(Collection, Comparator)} instead, which creates its own copy of elements.
	 * <p>
	 * The argument list should support random access indexing for better performance.
	 * 
	 * @param elements
	 *            The backing elements for the created navigable set.
	 * @param comparator
	 *            The comparator for the ordering of the set.
	 * @return An unmodifiable navigable set backed by the argument list, ordered by the argument comparator.
	 */
	public static <E> NavigableSet<E> unmodifiableNavigableSet(List<? extends E> elements,
			Comparator<? super E> comparator) {
		if (elements == null) {
			return null;
		}
		return ImmutableListNavigableSet.create(comparator, elements);
	}

	/**
	 * Gets an unmodifiable view to the argument map.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map.
	 * @return The unmodifiable view, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <K, V> Map<K, V> unmodifiableMap(Map<? extends K, ? extends V> map) {
		if (map == null) {
			return null;
		}
		return new UnmodifiableMap<>(map);
	}

	/**
	 * Gets an unmodifiable view to the argument sorted map.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map.
	 * @return The unmodifiable view, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <K, V> SortedMap<K, V> unmodifiableSortedMap(SortedMap<K, ? extends V> map) {
		if (map == null) {
			return null;
		}
		return new UnmodifiableSortedMap<>(map);
	}

	/**
	 * Gets an unmodifiable view to the argument navigable map.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map.
	 * @return The unmodifiable view, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> unmodifiableNavigableMap(NavigableMap<K, ? extends V> map) {
		if (map == null) {
			return null;
		}
		return new UnmodifiableNavigableMap<>(map);
	}

	//doc: these methods are unsafe in regards of the sortedness of the keys
	/**
	 * Creates a new unmodifiable navigable map that is backed by the argument key and value arrays.
	 * <p>
	 * The keys should be {@linkplain ObjectUtils#isStrictlySorted(Iterable, Comparator) stricly ordered} by the
	 * {@linkplain Comparator#naturalOrder() natural order}.
	 * <p>
	 * The arrays must have same length. Each key at a given will be mapped to the value in the value array at the same
	 * index.
	 * <p>
	 * Callers should not modify the contents of the argument arrays after the result map has been constructed.
	 * <p>
	 * If the above requirements are violated, the code that uses the returned map may experience unexpected results. If
	 * the elements are not stricly sorted, the lookup methods may not work properly in the returned map.
	 * <p>
	 * If you might possibly modify the argument arrays after this method returns, consider using
	 * {@link #makeImmutableNavigableMap(Object[], Object[])} instead, which creates its own copy of elements.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type
	 * @param keys
	 *            The array of keys.
	 * @param values
	 *            The array of values.
	 * @return The unmodifiable navigable map view to the argument arrays ordered by natural order.
	 * @throws IllegalArgumentException
	 *             If the arrays have different lengths.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> unmodifiableNavigableMap(K[] keys, V[] values)
			throws IllegalArgumentException, NullPointerException {
		return unmodifiableNavigableMap(keys, values, null);
	}

	/**
	 * Creates a new unmodifiable navigable map that is backed by the argument key and value arrays.
	 * <p>
	 * The keys should be {@linkplain ObjectUtils#isStrictlySorted(Iterable, Comparator) stricly ordered} by the
	 * argument comparator.
	 * <p>
	 * The arrays must have same length. Each key at a given will be mapped to the value in the value array at the same
	 * index.
	 * <p>
	 * Callers should not modify the contents of the argument arrays after the result map has been constructed.
	 * <p>
	 * If the above requirements are violated, the code that uses the returned map may experience unexpected results. If
	 * the elements are not stricly sorted, the lookup methods may not work properly in the returned map.
	 * <p>
	 * If you might possibly modify the argument arrays after this method returns, consider using
	 * {@link #makeImmutableNavigableMap(Object[], Object[], Comparator)} instead, which creates its own copy of
	 * elements.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type
	 * @param keys
	 *            The array of keys.
	 * @param values
	 *            The array of values.
	 * @param comparator
	 *            The comparator that specifies the order of the elements.
	 * @return The unmodifiable navigable map view to the argument arrays ordered by the argument comparator.
	 * @throws IllegalArgumentException
	 *             If the arrays have different lengths.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> unmodifiableNavigableMap(K[] keys, V[] values,
			Comparator<? super K> comparator) throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(keys, "keys");
		Objects.requireNonNull(values, "values");
		if (keys.length != values.length) {
			throw new IllegalArgumentException("Arrays have different lengths: " + keys.length + " - " + values.length);
		}
		return ImmutableListsNavigableMap.create(comparator, asUnmodifiableArrayList(keys),
				asUnmodifiableArrayList(values));
	}

	/**
	 * Creates a new unmodifiable navigable map that is backed by the argument key and value lists.
	 * <p>
	 * The keys should be {@linkplain ObjectUtils#isStrictlySorted(Iterable, Comparator) stricly ordered} by the
	 * {@linkplain Comparator#naturalOrder() natural order}.
	 * <p>
	 * The lists must have same length. Each key at a given will be mapped to the value in the value list at the same
	 * index.
	 * <p>
	 * Callers should not modify the contents of the argument lists after the result map has been constructed.
	 * <p>
	 * If the above requirements are violated, the code that uses the returned map may experience unexpected results. If
	 * the elements are not stricly sorted, the lookup methods may not work properly in the returned map.
	 * <p>
	 * If you might possibly modify the argument lists after this method returns, consider using
	 * {@link #makeImmutableNavigableMap(List, List)} instead, which creates its own copy of elements.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type
	 * @param keys
	 *            The list of keys.
	 * @param values
	 *            The list of values.
	 * @return The unmodifiable navigable map view to the argument lists ordered by natural order.
	 * @throws IllegalArgumentException
	 *             If the lists have different lengths.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> unmodifiableNavigableMap(List<K> keys, List<V> values)
			throws IllegalArgumentException, NullPointerException {
		return unmodifiableNavigableMap(keys, values, null);
	}

	/**
	 * Creates a new unmodifiable navigable map that is backed by the argument key and value lists.
	 * <p>
	 * The keys should be {@linkplain ObjectUtils#isStrictlySorted(Iterable, Comparator) stricly ordered} by the
	 * argument comparator.
	 * <p>
	 * The lists must have same length. Each key at a given will be mapped to the value in the value list at the same
	 * index.
	 * <p>
	 * Callers should not modify the contents of the argument lists after the result map has been constructed.
	 * <p>
	 * If the above requirements are violated, the code that uses the returned map may experience unexpected results. If
	 * the elements are not stricly sorted, the lookup methods may not work properly in the returned map.
	 * <p>
	 * If you might possibly modify the argument lists after this method returns, consider using
	 * {@link #makeImmutableNavigableMap(List, List, Comparator)} instead, which creates its own copy of elements.
	 * <p>
	 * The argument lists should support random access indexing for better performance.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type
	 * @param keys
	 *            The list of keys.
	 * @param values
	 *            The list of values.
	 * @param comparator
	 *            The comparator that specifies the order of the elements.
	 * @return The unmodifiable navigable map view to the argument lists ordered by the argument comparator.
	 * @throws IllegalArgumentException
	 *             If the lists have different lengths.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> unmodifiableNavigableMap(List<K> keys, List<V> values,
			Comparator<? super K> comparator) throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(keys, "keys");
		Objects.requireNonNull(values, "values");
		int ks = keys.size();
		int vs = values.size();
		if (ks != vs) {
			throw new IllegalArgumentException("Lists have different lengths: " + ks + " - " + vs);
		}
		return ImmutableListsNavigableMap.create(comparator, keys, values);
	}

	/**
	 * Gets an unmodifiable view to the argument map entry.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param entry
	 *            The map entry.
	 * @return The unmodifiable view, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <K, V> Map.Entry<K, V> unmodifiableMapEntry(Map.Entry<? extends K, ? extends V> entry) {
		if (entry == null) {
			return null;
		}
		return new UnmodifiableMapEntry<>(entry);
	}

	/**
	 * Wraps the argument iterator into an unmodifiable iterator.
	 * <p>
	 * Calling {@link Iterator#remove()} on the returned iterator will throw an {@link UnsupportedOperationException}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param it
	 *            The iterator.
	 * @return The unmodifiable iterator, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <E> Iterator<E> unmodifiableIterator(Iterator<? extends E> it) {
		if (it == null) {
			return null;
		}
		return new UnmodifiableIterator<>(it);
	}

	/**
	 * Wraps the argument list iterator into an unmodifiable list iterator.
	 * <p>
	 * Calling modifying functions on the returned iteratir will throw an {@link UnsupportedOperationException}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param it
	 *            The iterator.
	 * @return The unmodifiable list iterator, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <E> ListIterator<E> unmodifiableListIterator(ListIterator<? extends E> it) {
		if (it == null) {
			return null;
		}
		return new UnmodifiableListIterator<>(it);
	}

	/**
	 * Wraps the argument map entry iterator into an unmodifiable iterator.
	 * <p>
	 * Calling {@link Iterator#remove()} on the returned iterator will throw an {@link UnsupportedOperationException}.
	 * <p>
	 * Calling {@link Map.Entry#setValue(Object)} on the entries returned by the iterator will also throw an
	 * {@link UnsupportedOperationException}.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param it
	 *            The iterator.
	 * @return The unmodifiable iterator, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static <K, V> Iterator<Map.Entry<K, V>> unmodifiableMapEntryIterator(
			Iterator<? extends Map.Entry<? extends K, ? extends V>> it) {
		if (it == null) {
			return null;
		}
		return new UnmodifiableMapEntryIterator<>(it);
	}

	/**
	 * Creates an immutable list with a single element.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param element
	 *            The element.
	 * @return The created list.
	 */
	public static <E> List<E> singletonList(E element) {
		return new SingletonList<>(element);
	}

	/**
	 * Creates an immutable set with a single element.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param element
	 *            The element.
	 * @return The created set.
	 */
	public static <E> Set<E> singletonSet(E element) {
		return new SingletonSet<>(element);
	}

	/**
	 * Creates an immutable set with a single element.
	 * <p>
	 * The set has the same element semantics as a {@link Set} made from {@link IdentityHashMap}. The elements are
	 * compared by identity (<code>==</code>) and the {@linkplain System#identityHashCode(Object) identity hash code} is
	 * used.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param element
	 *            The element.
	 * @return The created set.
	 */
	public static <E> Set<E> singletonIdentityHashSet(E element) {
		return new SingletonIdentityHashSet<>(element);
	}

	/**
	 * Creates an immutable sorted set with a single element.
	 * <p>
	 * The resulting set is ordered by natural order.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param element
	 *            The element.
	 * @return The created sorted set.
	 */
	public static <E> SortedSet<E> singletonSortedSet(E element) {
		return new SingletonSortedSet<>(element);
	}

	/**
	 * Creates an immutable sorted set with a single element and comparator.
	 * <p>
	 * The {@link SortedSet#comparator()} method will return the argument comparator.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param element
	 *            The element.
	 * @param comparator
	 *            The comparator for the created sorted set.
	 * @return The created sorted set.
	 */
	public static <E> SortedSet<E> singletonSortedSet(E element, Comparator<? super E> comparator) {
		if (comparator == null) {
			return new SingletonSortedSet<>(element);
		}
		return new ComparatorSingletonSortedSet<>(element, comparator);
	}

	/**
	 * Creates an immutable navigable set with a single element.
	 * <p>
	 * The resulting set is ordered by natural order.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param element
	 *            The element.
	 * @return The created navigable set.
	 */
	public static <E> NavigableSet<E> singletonNavigableSet(E element) {
		return new SingletonNavigableSet<>(element);
	}

	/**
	 * Creates an immutable navigable set with a single element and comparator.
	 * <p>
	 * The {@link NavigableSet#comparator()} method will return the argument comparator.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param element
	 *            The element.
	 * @param comparator
	 *            The comparator for the created navigable set.
	 * @return The created navigable set.
	 */
	public static <E> NavigableSet<E> singletonNavigableSet(E element, Comparator<? super E> comparator) {
		if (comparator == null) {
			return new SingletonNavigableSet<>(element);
		}
		return new ComparatorSingletonNavigableSet<>(element, comparator);
	}

	/**
	 * Creates an immutable map with a single entry.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param key
	 *            The key for the entry.
	 * @param value
	 *            The value mapped to the key.
	 * @return The created map.
	 */
	public static <K, V> Map<K, V> singletonMap(K key, V value) {
		return new SingletonMap<>(key, value);
	}

	/**
	 * Creates an immutable map with a single entry.
	 * <p>
	 * The returned map has the same semantics as {@link IdentityHashMap}.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param key
	 *            The key for the entry.
	 * @param value
	 *            The value mapped to the key.
	 * @return The created map.
	 */
	public static <K, V> Map<K, V> singletonIdentityHashMap(K key, V value) {
		return new SingletonIdentityHashMap<>(key, value);
	}

	/**
	 * Creates an immutable sorted map with a single entry.
	 * <p>
	 * The resulting map is ordered by natural order.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param key
	 *            The key for the entry.
	 * @param value
	 *            The value mapped to the key.
	 * @return The created sorted map.
	 */
	public static <K, V> SortedMap<K, V> singletonSortedMap(K key, V value) {
		return new SingletonSortedMap<>(key, value);
	}

	/**
	 * Creates an immutable sorted map with a single entry and comparator.
	 * <p>
	 * The {@link SortedMap#comparator()} method will return the argument comparator.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param key
	 *            The key for the entry.
	 * @param value
	 *            The value mapped to the key.
	 * @param comparator
	 *            The comparator for the created sorted map.
	 * @return The created sorted map.
	 */
	public static <K, V> SortedMap<K, V> singletonSortedMap(K key, V value, Comparator<? super K> comparator) {
		if (comparator == null) {
			return new SingletonSortedMap<K, V>(key, value);
		}
		return new ComparatorSingletonSortedMap<>(key, value, comparator);
	}

	/**
	 * Creates an immutable navigable map with a single entry.
	 * <p>
	 * The resulting map is ordered by natural order.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param key
	 *            The key for the entry.
	 * @param value
	 *            The value mapped to the key.
	 * @return The created navigable map.
	 */
	public static <K, V> NavigableMap<K, V> singletonNavigableMap(K key, V value) {
		return new SingletonNavigableMap<>(key, value);
	}

	/**
	 * Creates an immutable navigable map with a single entry and comparator.
	 * <p>
	 * The {@link NavigableMap#comparator()} method will return the argument comparator.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param key
	 *            The key for the entry.
	 * @param value
	 *            The value mapped to the key.
	 * @param comparator
	 *            The comparator for the created navigable map.
	 * @return The created navigable map.
	 */
	public static <K, V> NavigableMap<K, V> singletonNavigableMap(K key, V value, Comparator<? super K> comparator) {
		if (comparator == null) {
			return new SingletonNavigableMap<>(key, value);
		}
		return new ComparatorSingletonNavigableMap<>(key, value, comparator);
	}

	/**
	 * Creates an iterator that iterates over a single object.
	 * <p>
	 * The returned object is not {@link Externalizable}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param obj
	 *            The object to iterate over.
	 * @return The created singleton iterator.
	 */
	public static <E> Iterator<E> singletonIterator(E obj) {
		return new SingletonIterator<>(obj);
	}

	/**
	 * Creates an enumeration that enumerates only a single object.
	 * <p>
	 * The returned object is not {@link Externalizable}.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param value
	 *            The object to enumerate.
	 * @return The created singleton enumeration.
	 */
	public static <E> Enumeration<E> singletionEnumeration(E value) {
		return new SingletonEnumeration<>(value);
	}

	/**
	 * Gets an immutable empty sorted set that has the argument comparator.
	 * <p>
	 * The {@link SortedSet#comparator()} method will return the argument comparator.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param comparator
	 *            The comparator.
	 * @return The empty sorted set.
	 */
	public static <E> SortedSet<E> emptySortedSet(Comparator<? super E> comparator) {
		if (comparator == null) {
			return emptySortedSet();
		}
		return new ComparatorEmptySortedSet<>(comparator);
	}

	/**
	 * Gets an immutable empty navigable set that has the argument comparator.
	 * <p>
	 * The {@link NavigableSet#comparator()} method will return the argument comparator.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param comparator
	 *            The comparator.
	 * @return The empty navigable set.
	 */
	public static <E> NavigableSet<E> emptyNavigableSet(Comparator<? super E> comparator) {
		if (comparator == null) {
			return emptyNavigableSet();
		}
		return new ComparatorEmptyNavigableSet<>(comparator);
	}

	/**
	 * Gets an immutable empty sorted map that has the argument comparator.
	 * <p>
	 * The {@link SortedMap#comparator()} method will return the argument comparator.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param comparator
	 *            The comparator.
	 * @return The empty sorted map.
	 */
	public static <K, V> SortedMap<K, V> emptySortedMap(Comparator<? super K> comparator) {
		if (comparator == null) {
			return emptySortedMap();
		}
		return new ComparatorEmptySortedMap<>(comparator);
	}

	/**
	 * Gets an immutable empty navigable map that has the argument comparator.
	 * <p>
	 * The {@link NavigableMap#comparator()} method will return the argument comparator.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param comparator
	 *            The comparator.
	 * @return The empty navigable map.
	 */
	public static <K, V> NavigableMap<K, V> emptyNavigableMap(Comparator<? super K> comparator) {
		if (comparator == null) {
			return emptyNavigableMap();
		}
		return new ComparatorEmptyNavigableMap<>(comparator);
	}

	private ImmutableUtils() {
		throw new UnsupportedOperationException();
	}

	private static <E> List<E> emptyList() {
		return Collections.emptyList();
	}

	private static <E> Set<E> emptySet() {
		return Collections.emptySet();
	}

	@SuppressWarnings("unchecked")
	private static <E> SortedSet<E> emptySortedSet() {
		return (SortedSet<E>) EmptySortedSet.EMPTY_SORTED_SET;
	}

	@SuppressWarnings("unchecked")
	private static <E> NavigableSet<E> emptyNavigableSet() {
		return (NavigableSet<E>) EmptyNavigableSet.EMPTY_NAVIGABLE_SET;
	}

	private static <K, V> Map<K, V> emptyMap() {
		return Collections.emptyMap();
	}

	@SuppressWarnings("unchecked")
	private static <K, V> SortedMap<K, V> emptySortedMap() {
		return (SortedMap<K, V>) EmptySortedMap.EMPTY_SORTED_MAP;
	}

	@SuppressWarnings("unchecked")
	private static <K, V> NavigableMap<K, V> emptyNavigableMap() {
		return (NavigableMap<K, V>) EmptyNavigableMap.EMPTY_NAVIGABLE_MAP;
	}

	private static final class SingletonEnumeration<T> implements Enumeration<T> {
		private final T value;
		private boolean next = true;

		private SingletonEnumeration(T value) {
			this.value = value;
		}

		@Override
		public boolean hasMoreElements() {
			return next;
		}

		@Override
		public T nextElement() {
			next = false;
			return value;
		}
	}

	private static class SingletonSortedMap<K, V> extends SingletonMap<K, V> implements SortedMap<K, V> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public SingletonSortedMap() {
		}

		public SingletonSortedMap(K key, V value) {
			super(key, value);
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		protected Comparator<? super K> getComparator() {
			return (Comparator) Comparator.naturalOrder();
		}

		@Override
		public Comparator<? super K> comparator() {
			return null;
		}

		@SuppressWarnings("unchecked")
		@Override
		public final boolean containsKey(Object key) {
			return getComparator().compare(this.key, (K) key) == 0;
		}

		@Override
		public final SortedMap<K, V> subMap(K fromKey, K toKey) {
			Comparator<? super K> c = getComparator();
			int fromcmp = c.compare(key, fromKey);
			if (fromcmp < 0) {
				//our key is less than fromkey
				return emptySortedMap();
			}
			int tocmp = c.compare(key, toKey);
			if (tocmp >= 0) {
				return emptySortedMap();
			}
			return this;
		}

		@Override
		public final SortedMap<K, V> headMap(K toKey) {
			Comparator<? super K> c = getComparator();
			if (c.compare(key, toKey) < 0) {
				return this;
			}
			return emptySortedMap();
		}

		@Override
		public final SortedMap<K, V> tailMap(K fromKey) {
			Comparator<? super K> c = getComparator();
			if (c.compare(key, fromKey) >= 0) {
				return this;
			}
			return emptySortedMap();
		}

		@Override
		public final K firstKey() {
			return key;
		}

		@Override
		public final K lastKey() {
			return key;
		}

	}

	private static class ComparatorSingletonSortedMap<K, V> extends SingletonSortedMap<K, V> {
		private static final long serialVersionUID = 1L;

		protected Comparator<? super K> comparator;

		/**
		 * For {@link Externalizable}.
		 */
		public ComparatorSingletonSortedMap() {
		}

		public ComparatorSingletonSortedMap(K key, V value, Comparator<? super K> comparator) {
			super(key, value);
			this.comparator = comparator;
		}

		@Override
		public final Comparator<? super K> comparator() {
			return comparator;
		}

		@Override
		protected final Comparator<? super K> getComparator() {
			return comparator;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
			out.writeObject(comparator);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			comparator = (Comparator<? super K>) in.readObject();
		}
	}

	private static class SingletonNavigableMap<K, V> extends SingletonSortedMap<K, V> implements NavigableMap<K, V> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public SingletonNavigableMap() {
		}

		public SingletonNavigableMap(K key, V value) {
			super(key, value);
		}

		@Override
		public final Entry<K, V> lowerEntry(K key) {
			if (getComparator().compare(this.key, key) < 0) {
				return makeImmutableMapEntry(this.key, value);
			}
			return null;
		}

		@Override
		public final K lowerKey(K key) {
			if (getComparator().compare(this.key, key) < 0) {
				return this.key;
			}
			return null;
		}

		@Override
		public final Entry<K, V> floorEntry(K key) {
			if (getComparator().compare(this.key, key) <= 0) {
				return makeImmutableMapEntry(this.key, value);
			}
			return null;
		}

		@Override
		public final K floorKey(K key) {
			if (getComparator().compare(this.key, key) <= 0) {
				return this.key;
			}
			return null;
		}

		@Override
		public final Entry<K, V> ceilingEntry(K key) {
			if (getComparator().compare(this.key, key) >= 0) {
				return makeImmutableMapEntry(this.key, value);
			}
			return null;
		}

		@Override
		public final K ceilingKey(K key) {
			if (getComparator().compare(this.key, key) >= 0) {
				return this.key;
			}
			return null;
		}

		@Override
		public final Entry<K, V> higherEntry(K key) {
			if (getComparator().compare(this.key, key) > 0) {
				return makeImmutableMapEntry(this.key, value);
			}
			return null;
		}

		@Override
		public final K higherKey(K key) {
			if (getComparator().compare(this.key, key) > 0) {
				return this.key;
			}
			return null;
		}

		@Override
		public final Entry<K, V> firstEntry() {
			return makeImmutableMapEntry(this.key, value);
		}

		@Override
		public final Entry<K, V> lastEntry() {
			return makeImmutableMapEntry(this.key, value);
		}

		@Override
		public final Entry<K, V> pollFirstEntry() {
			throw new UnsupportedOperationException();
		}

		@Override
		public final Entry<K, V> pollLastEntry() {
			throw new UnsupportedOperationException();
		}

		@Override
		public final NavigableMap<K, V> descendingMap() {
			return this;
		}

		@Override
		public final NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
			Comparator<? super K> c = getComparator();
			int fromcmp = c.compare(key, fromKey);
			if (fromcmp < 0) {
				//our key is less than fromkey
				return emptyNavigableMap(this.comparator());
			}
			if (fromcmp == 0 && !fromInclusive) {
				return emptyNavigableMap(this.comparator());
			}
			int tocmp = c.compare(key, toKey);
			if (tocmp > 0) {
				return emptyNavigableMap(this.comparator());
			}
			if (tocmp == 0 && !toInclusive) {
				return emptyNavigableMap(this.comparator());
			}
			return this;
		}

		@Override
		public final NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
			int cmp = getComparator().compare(key, toKey);
			if (inclusive) {
				if (cmp <= 0) {
					return this;
				}
			} else {
				if (cmp < 0) {
					return this;
				}
			}
			return emptyNavigableMap(this.comparator());
		}

		@Override
		public final NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
			int cmp = getComparator().compare(key, fromKey);
			if (inclusive) {
				if (cmp >= 0) {
					return this;
				}
			} else {
				if (cmp > 0) {
					return this;
				}
			}
			return emptyNavigableMap(this.comparator());
		}

		@Override
		public NavigableSet<K> navigableKeySet() {
			return new SingletonNavigableSet<>(key);
		}

		@Override
		public NavigableSet<K> descendingKeySet() {
			return new SingletonNavigableSet<>(key);
		}

	}

	private static class ComparatorSingletonNavigableMap<K, V> extends SingletonNavigableMap<K, V> {
		private static final long serialVersionUID = 1L;

		private Comparator<? super K> comparator;

		/**
		 * For {@link Externalizable}.
		 */
		public ComparatorSingletonNavigableMap() {
		}

		public ComparatorSingletonNavigableMap(K key, V value, Comparator<? super K> comparator) {
			super(key, value);
			this.comparator = comparator;
		}

		@Override
		public final Comparator<? super K> comparator() {
			return comparator;
		}

		@Override
		protected final Comparator<? super K> getComparator() {
			return comparator;
		}

		@Override
		public final NavigableSet<K> navigableKeySet() {
			return new ComparatorSingletonNavigableSet<>(key, comparator);
		}

		@Override
		public final NavigableSet<K> descendingKeySet() {
			return new ComparatorSingletonNavigableSet<>(key, comparator);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
			out.writeObject(comparator);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			comparator = (Comparator<? super K>) in.readObject();
		}
	}

	private static class SingletonSortedSet<E> extends SingletonSet<E> implements SortedSet<E> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public SingletonSortedSet() {
		}

		public SingletonSortedSet(E elem) {
			super(elem);
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		protected Comparator<? super E> getComparator() {
			return (Comparator) Comparator.naturalOrder();
		}

		@Override
		public Comparator<? super E> comparator() {
			return null;
		}

		@SuppressWarnings("unchecked")
		@Override
		public final boolean contains(Object o) {
			return getComparator().compare((E) o, this.elem) == 0;
		}

		@Override
		public final SortedSet<E> subSet(E fromElement, E toElement) {
			Comparator<? super E> c = getComparator();
			int fromcmp = c.compare(elem, fromElement);
			if (fromcmp < 0) {
				//our key is less than fromkey
				return emptySortedSet();
			}
			int tocmp = c.compare(elem, toElement);
			if (tocmp >= 0) {
				return emptySortedSet();
			}
			return this;
		}

		@Override
		public final SortedSet<E> headSet(E toElement) {
			Comparator<? super E> c = getComparator();
			if (c.compare(elem, toElement) < 0) {
				return this;
			}
			return emptySortedSet();
		}

		@Override
		public final SortedSet<E> tailSet(E fromElement) {
			Comparator<? super E> c = getComparator();
			if (c.compare(elem, fromElement) >= 0) {
				return this;
			}
			return emptySortedSet();
		}

		@Override
		public final E first() {
			return elem;
		}

		@Override
		public final E last() {
			return elem;
		}

	}

	private static class ComparatorSingletonSortedSet<E> extends SingletonSortedSet<E> {
		private static final long serialVersionUID = 1L;

		protected Comparator<? super E> comparator;

		/**
		 * For {@link Externalizable}.
		 */
		public ComparatorSingletonSortedSet() {
		}

		public ComparatorSingletonSortedSet(E elem, Comparator<? super E> comparator) {
			super(elem);
			this.comparator = comparator;
		}

		@Override
		protected final Comparator<? super E> getComparator() {
			return comparator;
		}

		@Override
		public final Comparator<? super E> comparator() {
			return comparator;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
			out.writeObject(comparator);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			comparator = (Comparator<? super E>) in.readObject();
		}
	}

	private static class SingletonNavigableSet<E> extends SingletonSortedSet<E> implements NavigableSet<E> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public SingletonNavigableSet() {
		}

		public SingletonNavigableSet(E elem) {
			super(elem);
		}

		@Override
		public final E lower(E e) {
			if (getComparator().compare(this.elem, e) < 0) {
				return this.elem;
			}
			return null;
		}

		@Override
		public final E floor(E e) {
			if (getComparator().compare(this.elem, e) <= 0) {
				return this.elem;
			}
			return null;
		}

		@Override
		public final E ceiling(E e) {
			if (getComparator().compare(this.elem, e) >= 0) {
				return this.elem;
			}
			return null;
		}

		@Override
		public final E higher(E e) {
			if (getComparator().compare(this.elem, e) > 0) {
				return this.elem;
			}
			return null;
		}

		@Override
		public final E pollFirst() {
			throw new UnsupportedOperationException();
		}

		@Override
		public final E pollLast() {
			throw new UnsupportedOperationException();
		}

		@Override
		public final NavigableSet<E> descendingSet() {
			return this;
		}

		@Override
		public final Iterator<E> descendingIterator() {
			return iterator();
		}

		@Override
		public final NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			Comparator<? super E> c = getComparator();
			int fromcmp = c.compare(elem, fromElement);
			if (fromcmp < 0) {
				//our key is less than fromkey
				return emptyNavigableSet(this.comparator());
			}
			if (fromcmp == 0 && !fromInclusive) {
				return emptyNavigableSet(this.comparator());
			}
			int tocmp = c.compare(elem, toElement);
			if (tocmp > 0) {
				return emptyNavigableSet(this.comparator());
			}
			if (tocmp == 0 && !toInclusive) {
				return emptyNavigableSet(this.comparator());
			}
			return this;
		}

		@Override
		public final NavigableSet<E> headSet(E toElement, boolean inclusive) {
			int cmp = getComparator().compare(elem, toElement);
			if (inclusive) {
				if (cmp <= 0) {
					return this;
				}
			} else {
				if (cmp < 0) {
					return this;
				}
			}
			return emptyNavigableSet(this.comparator());
		}

		@Override
		public final NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
			int cmp = getComparator().compare(elem, fromElement);
			if (inclusive) {
				if (cmp >= 0) {
					return this;
				}
			} else {
				if (cmp > 0) {
					return this;
				}
			}
			return emptyNavigableSet(this.comparator());
		}
	}

	private static class ComparatorSingletonNavigableSet<E> extends SingletonNavigableSet<E> {
		private static final long serialVersionUID = 1L;

		protected Comparator<? super E> comparator;

		/**
		 * For {@link Externalizable}.
		 */
		public ComparatorSingletonNavigableSet() {
		}

		public ComparatorSingletonNavigableSet(E elem, Comparator<? super E> comparator) {
			super(elem);
			this.comparator = comparator;
		}

		@Override
		protected final Comparator<? super E> getComparator() {
			return comparator;
		}

		@Override
		public final Comparator<? super E> comparator() {
			return comparator;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
			out.writeObject(comparator);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			comparator = (Comparator<? super E>) in.readObject();
		}
	}

	private static class SingletonList<E> extends SingletonCollection<E> implements List<E>, RandomAccess {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public SingletonList() {
		}

		public SingletonList(E elem) {
			super(elem);
		}

		@Override
		public final E get(int index) {
			if (index != 0) {
				throw new IndexOutOfBoundsException(Integer.toString(index));
			}
			return elem;
		}

		@Override
		public final boolean addAll(int index, Collection<? extends E> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final int indexOf(Object o) {
			return Objects.equals(elem, o) ? 0 : -1;
		}

		@Override
		public final int lastIndexOf(Object o) {
			return Objects.equals(elem, o) ? 0 : -1;
		}

		@Override
		public final void sort(Comparator<? super E> c) {
		}

		@Override
		public final boolean removeIf(Predicate<? super E> filter) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final void replaceAll(UnaryOperator<E> operator) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final E set(int index, E element) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final void add(int index, E element) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final E remove(int index) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final ListIterator<E> listIterator() {
			return new SingletonListIterator<>(elem, 0);
		}

		@Override
		public final ListIterator<E> listIterator(int index) {
			if (index < 0 || index > 1) {
				throw new IndexOutOfBoundsException(Integer.toString(index));
			}
			return new SingletonListIterator<>(elem, index);
		}

		@Override
		public final List<E> subList(int fromIndex, int toIndex) {
			ArrayUtils.requireArrayStartEndRangeLength(1, fromIndex, toIndex);
			if (fromIndex == toIndex) {
				return emptyList();
			}
			if (toIndex == 0) {
				return emptyList();
			}
			//fromindex is less than toindex
			//toindex is 1
			return this;
		}

		@Override
		public final int hashCode() {
			E e = elem;
			return 31 + (e == null ? 0 : e.hashCode());
		}

		@Override
		public final boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof List)) {
				return false;
			}
			List<?> l = (List<?>) o;
			Iterator<?> it = l.iterator();
			if (!it.hasNext()) {
				return false;
			}
			Object e = it.next();
			if (it.hasNext()) {
				return false;
			}
			return Objects.equals(elem, e);
		}
	}

	private static class SingletonMap<K, V> implements Map<K, V>, Externalizable {
		private static final long serialVersionUID = 1L;

		protected K key;
		protected V value;

		/**
		 * For {@link Externalizable}.
		 */
		public SingletonMap() {
		}

		public SingletonMap(K key, V value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public Set<Entry<K, V>> entrySet() {
			return new SingletonSet<>(makeImmutableMapEntry(key, value));
		}

		@Override
		public final int size() {
			return 1;
		}

		@Override
		public final boolean isEmpty() {
			return false;
		}

		@Override
		public boolean containsKey(Object key) {
			return Objects.equals(this.key, key);
		}

		@Override
		public boolean containsValue(Object value) {
			return Objects.equals(this.value, value);
		}

		@Override
		public final V get(Object key) {
			if (containsKey(key)) {
				return value;
			}
			return null;
		}

		@Override
		public final V getOrDefault(Object key, V defaultValue) {
			if (containsKey(key)) {
				return value;
			}
			return defaultValue;
		}

		@Override
		public final V put(K key, V value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final V remove(Object key) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final boolean remove(Object key, Object value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final void putAll(Map<? extends K, ? extends V> m) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final void clear() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Set<K> keySet() {
			return new SingletonSet<>(key);
		}

		@Override
		public Collection<V> values() {
			return new SingletonSet<>(value);
		}

		@Override
		public final V putIfAbsent(K key, V value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final boolean replace(K key, V oldValue, V newValue) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final V replace(K key, V value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final void forEach(BiConsumer<? super K, ? super V> action) {
			action.accept(key, value);
		}

		@Override
		public final void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(key);
			out.writeObject(value);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			key = (K) in.readObject();
			value = (V) in.readObject();
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(key) ^ Objects.hashCode(value);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof Map)) {
				return false;
			}
			Map<?, ?> m = (Map<?, ?>) obj;
			Iterator<? extends Map.Entry<?, ?>> it = m.entrySet().iterator();
			if (!it.hasNext()) {
				return false;
			}
			Map.Entry<?, ?> entry = it.next();
			if (it.hasNext()) {
				return false;
			}
			return Objects.equals(key, entry.getKey()) && Objects.equals(value, entry.getValue());
		}

		@Override
		public final String toString() {
			return "{" + key + "=" + value + "}";
		}
	}

	private static class SingletonIdentityHashMap<K, V> extends SingletonMap<K, V> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public SingletonIdentityHashMap() {
		}

		public SingletonIdentityHashMap(K key, V value) {
			super(key, value);
		}

		@Override
		public Set<Entry<K, V>> entrySet() {
			return new SingletonSet<>(makeImmutableIdentityMapEntry(key, value));
		}

		@Override
		public boolean containsKey(Object key) {
			return this.key == key;
		}

		@Override
		public boolean containsValue(Object value) {
			return this.value == value;
		}

		@Override
		public Set<K> keySet() {
			return new SingletonIdentityHashSet<>(key);
		}

		@Override
		public Collection<V> values() {
			return new SingletonIdentityHashSet<>(value);
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(key) ^ System.identityHashCode(value);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof Map)) {
				return false;
			}
			Map<?, ?> m = (Map<?, ?>) obj;
			Iterator<? extends Map.Entry<?, ?>> it = m.entrySet().iterator();
			if (!it.hasNext()) {
				return false;
			}
			Map.Entry<?, ?> entry = it.next();
			if (it.hasNext()) {
				return false;
			}
			return key == entry.getKey() && value == entry.getValue();
		}
	}

	private static class SingletonCollection<E> implements Collection<E>, Externalizable {
		private static final long serialVersionUID = 1L;

		protected E elem;

		/**
		 * For {@link Externalizable}.
		 */
		public SingletonCollection() {
		}

		public SingletonCollection(E elem) {
			this.elem = elem;
		}

		@Override
		public final int size() {
			return 1;
		}

		@Override
		public final boolean isEmpty() {
			return false;
		}

		@Override
		public boolean contains(Object o) {
			return Objects.equals(elem, o);
		}

		@Override
		public final Iterator<E> iterator() {
			return new SingletonIterator<>(elem);
		}

		@Override
		public final Object[] toArray() {
			return new Object[] { elem };
		}

		@Override
		public final void forEach(Consumer<? super E> action) {
			action.accept(elem);
		}

		@SuppressWarnings("unchecked")
		@Override
		public final <T> T[] toArray(T[] a) {
			if (a.length == 0) {
				T[] copy = Arrays.copyOf(a, 1);
				copy[0] = (T) elem;
				return copy;
			}
			a[0] = (T) elem;
			if (a.length > 1) {
				a[1] = null;
			}
			return a;
		}

		@Override
		public final boolean add(E e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			Object o = this.elem;
			if (o == null) {
				for (Object co : c) {
					if (co != null) {
						return false;
					}
				}
				return true;
			}
			for (Object co : c) {
				if (!o.equals(co)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public final boolean addAll(Collection<? extends E> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final void clear() {
			throw new UnsupportedOperationException();
		}

		@Override
		public final String toString() {
			return "[" + elem + "]";
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(elem);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			elem = (E) in.readObject();
		}

	}

	private static class SingletonSet<E> extends SingletonCollection<E> implements Set<E> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public SingletonSet() {
		}

		public SingletonSet(E elem) {
			super(elem);
		}

		@Override
		public final boolean removeIf(Predicate<? super E> filter) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final int hashCode() {
			return Objects.hashCode(elem);
		}

		@Override
		public final boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Set)) {
				return false;
			}
			Set<?> s = (Set<?>) o;
			Iterator<?> it = s.iterator();
			if (!it.hasNext()) {
				return false;
			}
			Object e = it.next();
			if (it.hasNext()) {
				return false;
			}
			return Objects.equals(elem, e);
		}
	}

	private static class SingletonIdentityHashSet<E> extends SingletonCollection<E> implements Set<E> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public SingletonIdentityHashSet() {
		}

		public SingletonIdentityHashSet(E elem) {
			super(elem);
		}

		@Override
		public boolean contains(Object o) {
			return o == elem;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			Object o = this.elem;
			if (o == null) {
				for (Object co : c) {
					if (co != null) {
						return false;
					}
				}
				return true;
			}
			for (Object co : c) {
				if (o != co) {
					return false;
				}
			}
			return true;
		}

		@Override
		public final boolean removeIf(Predicate<? super E> filter) {
			throw new UnsupportedOperationException();
		}

		@Override
		public final int hashCode() {
			return System.identityHashCode(elem);
		}

		@Override
		public final boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Set)) {
				return false;
			}
			Set<?> s = (Set<?>) o;
			Iterator<?> it = s.iterator();
			if (!it.hasNext()) {
				return false;
			}
			Object e = it.next();
			if (it.hasNext()) {
				return false;
			}
			return elem == e;
		}
	}

	@SuppressWarnings("unchecked")
	private static final class SingletonIterator<T> implements Iterator<T> {
		private static final Object SENTINEL = new Object();

		private Object value;

		public SingletonIterator(T value) {
			this.value = value;
		}

		@Override
		public boolean hasNext() {
			return value != SENTINEL;
		}

		@Override
		public T next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			T res = (T) value;
			value = SENTINEL;
			return res;
		}

		@Override
		public void forEachRemaining(Consumer<? super T> action) {
			Objects.requireNonNull(action, "action");
			if (hasNext()) {
				action.accept((T) value);
				value = SENTINEL;
			}
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (hasNext() ? value : "") + "]";
		}
	}

	@SuppressWarnings("unchecked")
	private static final class SingletonListIterator<T> implements ListIterator<T> {
		private Object value;
		private int index;

		public SingletonListIterator(Object value, int index) {
			this.value = value;
			this.index = index;
		}

		@Override
		public boolean hasNext() {
			return index == 0;
		}

		@Override
		public T next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			index = 1;
			return (T) value;
		}

		@Override
		public void forEachRemaining(Consumer<? super T> action) {
			Objects.requireNonNull(action, "action");
			if (hasNext()) {
				action.accept((T) value);
				index = 1;
			}
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (hasNext() ? value : "") + "]";
		}

		@Override
		public boolean hasPrevious() {
			return index == 1;
		}

		@Override
		public T previous() {
			if (!hasPrevious()) {
				throw new NoSuchElementException();
			}
			index = 0;
			return (T) value;
		}

		@Override
		public int nextIndex() {
			return index;
		}

		@Override
		public int previousIndex() {
			return index - 1;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void set(T e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void add(T e) {
			throw new UnsupportedOperationException();
		}
	}
}
