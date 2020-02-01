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
package saker.build.thirdparty.saker.util.function;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * Utility class containing various helper functions for dealing with functional interfaces.
 * <p>
 * Every object returned from the functions in this class is serializable, unless noted otherwise. They are often not
 * serializable, if they're dealing with stateful objects. (E.g. with iterators, enumerations)
 * 
 * @see FunctionalInterface
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public final class Functionals {

	private static final class SupplierIterable<T> implements Iterable<T>, Externalizable {
		private static final long serialVersionUID = 1L;

		private Supplier<? extends Iterator<T>> itsupplier;

		private SupplierIterable(Supplier<? extends Iterator<T>> itsupplier) {
			this.itsupplier = itsupplier;
		}

		@Override
		public Iterator<T> iterator() {
			return itsupplier.get();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(itsupplier);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			itsupplier = (Supplier<? extends Iterator<T>>) in.readObject();
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + itsupplier + "]";
		}

	}

	private static final class IdentityEqualsPredicate<T> implements Predicate<T>, Externalizable {
		private static final long serialVersionUID = 1L;

		private Object o;

		/**
		 * For {@link Externalizable}.
		 */
		public IdentityEqualsPredicate() {
		}

		private IdentityEqualsPredicate(Object o) {
			this.o = o;
		}

		@Override
		public boolean test(T x) {
			return o == x;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(o);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			o = in.readObject();
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + o + "]";
		}

	}

	private static final class SneakyThrowingRunnable implements Runnable, Externalizable {
		private static final long serialVersionUID = 1L;

		private ThrowingRunnable run;

		public SneakyThrowingRunnable() {
		}

		private SneakyThrowingRunnable(ThrowingRunnable run) {
			this.run = run;
		}

		@SuppressWarnings("unchecked")
		private static <T extends Throwable> RuntimeException sneakyThrowImpl(Throwable t) throws T {
			throw (T) t;
		}

		@Override
		public void run() {
			try {
				run.run();
			} catch (Throwable e) {
				throw sneakyThrowImpl(e);
			}
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(run);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			run = (ThrowingRunnable) in.readObject();
		}
	}

	private static final class EntryKeyComparator<K, V, E extends Map.Entry<K, V>>
			implements Comparator<E>, Externalizable {
		private static final long serialVersionUID = 1L;

		private Comparator<? super K> comparator;

		/**
		 * For {@link Externalizable}.
		 */
		public EntryKeyComparator() {
		}

		private EntryKeyComparator(Comparator<? super K> comparator) {
			this.comparator = comparator;
		}

		@Override
		public int compare(E l, E r) {
			return comparator.compare(l.getKey(), r.getKey());
		}

		@Override
		public Comparator<E> reversed() {
			Comparator<? super K> cmp = comparator;
			if (cmp == null) {
				return new EntryKeyComparator<>((Comparator) Comparator.reverseOrder());
			}
			return new EntryKeyComparator<>(cmp.reversed());
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(comparator);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			comparator = (Comparator<? super K>) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((comparator == null) ? 0 : comparator.hashCode());
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
			EntryKeyComparator other = (EntryKeyComparator) obj;
			if (comparator == null) {
				if (other.comparator != null)
					return false;
			} else if (!comparator.equals(other.comparator))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + comparator + "]";
		}
	}

	private static final class EmptyEnumSetComputer<T, E extends Enum<E>>
			implements Function<T, EnumSet<E>>, Externalizable {
		private static final long serialVersionUID = 1L;

		private Class<E> enumClass;

		/**
		 * For {@link Externalizable}.
		 */
		public EmptyEnumSetComputer() {
		}

		private EmptyEnumSetComputer(Class<E> enumclass) {
			this.enumClass = enumclass;
		}

		@Override
		public EnumSet<E> apply(T x) {
			return EnumSet.noneOf(enumClass);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(enumClass);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			enumClass = (Class<E>) in.readObject();
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + enumClass + "]";
		}
	}

	private static final class EmptyEnumMapComputer<T, K extends Enum<K>, V>
			implements Function<T, EnumMap<K, V>>, Externalizable {
		private static final long serialVersionUID = 1L;

		private Class<K> enumClass;

		/**
		 * For {@link Externalizable}.
		 */
		public EmptyEnumMapComputer() {
		}

		private EmptyEnumMapComputer(Class<K> enumtype) {
			this.enumClass = enumtype;
		}

		@Override
		public EnumMap<K, V> apply(T x) {
			return new EnumMap<>(enumClass);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(enumClass);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			enumClass = (Class<K>) in.readObject();
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + enumClass + "]";
		}
	}

	private static final class DefaultedSupplier<T> implements Supplier<T>, Externalizable {
		private static final long serialVersionUID = 1L;

		private T defaultvalue;
		private Supplier<? extends T> supplier;

		/**
		 * For {@link Externalizable}.
		 */
		public DefaultedSupplier() {
		}

		private DefaultedSupplier(T defaultvalue, Supplier<? extends T> supplier) {
			this.defaultvalue = defaultvalue;
			this.supplier = supplier;
		}

		@Override
		public T get() {
			T got = supplier.get();
			if (got != null) {
				return got;
			}
			return defaultvalue;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(defaultvalue);
			out.writeObject(supplier);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			defaultvalue = (T) in.readObject();
			supplier = (Supplier<? extends T>) in.readObject();
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[defaultvalue=" + defaultvalue + ", supplier=" + supplier + "]";
		}
	}

	private static final class SupplierComputerFunction<K, T> implements Function<K, T>, Externalizable {
		private static final long serialVersionUID = 1L;

		private Supplier<? extends T> supplier;

		/**
		 * For {@link Externalizable}.
		 */
		public SupplierComputerFunction() {
		}

		private SupplierComputerFunction(Supplier<? extends T> supplier) {
			this.supplier = supplier;
		}

		@Override
		public T apply(K k) {
			return supplier.get();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(supplier);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			supplier = (Supplier<? extends T>) in.readObject();
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[supplier=" + supplier + "]";
		}

	}

	private static final class ValueSupplier<T> implements Supplier<T>, Externalizable {
		private static final long serialVersionUID = 1L;

		private T val;

		/**
		 * For {@link Externalizable}.
		 */
		public ValueSupplier() {
		}

		private ValueSupplier(T val) {
			this.val = val;
		}

		@Override
		public T get() {
			return val;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(val);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			val = (T) in.readObject();
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
			ValueSupplier other = (ValueSupplier) obj;
			if (val == null) {
				if (other.val != null)
					return false;
			} else if (!val.equals(other.val))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + val + "]";
		}
	}

	private static enum FixedPredicate implements Predicate<Object> {
		ALWAYS(true),
		NEVER(false);

		private final boolean result;

		private FixedPredicate(boolean result) {
			this.result = result;
		}

		@Override
		public boolean test(Object t) {
			return result;
		}

		@Override
		public Predicate<Object> negate() {
			return result ? NEVER : ALWAYS;
		}

		@Override
		public Predicate<Object> and(Predicate<? super Object> other) {
			return !result ? NEVER : other;
		}

		@Override
		public Predicate<Object> or(Predicate<? super Object> other) {
			return result ? ALWAYS : other;
		}
	}

	private static enum NullFunctional implements Supplier<Object>, Consumer<Object>, BiConsumer<Object, Object>,
			TriConsumer<Object, Object, Object>, Runnable, BiFunction<Object, Object, Object> {
		INSTANCE;

		@Override
		public Object get() {
			return null;
		}

		@Override
		public void accept(Object t, Object u, Object v) {
		}

		@Override
		public void accept(Object t, Object u) {
		}

		@Override
		public void accept(Object t) {
		}

		@Override
		public void run() {
		}

		@Override
		public Object apply(Object t, Object u) {
			return null;
		}
	}

	private static enum Functions implements Function {
		IDENTITY {
			@Override
			public Object apply(Object t) {
				return t;
			}
		},
		NULL {
			@Override
			public Object apply(Object t) {
				return null;
			}
		},
		NEW_ARRAYLIST {
			@Override
			public Object apply(Object t) {
				return new ArrayList<>();
			}
		},
		NEW_HASHSET {
			@Override
			public Object apply(Object t) {
				return new HashSet<>();
			}
		},
		NEW_LINKEDHASHSET {
			@Override
			public Object apply(Object t) {
				return new LinkedHashSet<>();
			}
		},
		NEW_IDENTITYHASHMAP {
			@Override
			public Object apply(Object t) {
				return new IdentityHashMap<>();
			}
		},
		NEW_IDENTITYHASHSET {
			@Override
			public Object apply(Object t) {
				return ObjectUtils.newIdentityHashSet();
			}
		},
		NEW_HASHMAP {
			@Override
			public Object apply(Object t) {
				return new HashMap<>();
			}
		},
		NEW_LINKEDHASHMAP {
			@Override
			public Object apply(Object t) {
				return new LinkedHashMap<>();
			}
		},
		NEW_TREEMAP {
			@Override
			public Object apply(Object t) {
				return new TreeMap<>();
			}
		},
		NEW_TREESET {
			@Override
			public Object apply(Object t) {
				return new TreeSet<>();
			}
		},
		NEW_CONCURRENTSKIPLISTMAP {
			@Override
			public Object apply(Object t) {
				return new ConcurrentSkipListMap<>();
			}
		},
		NEW_CONCURRENTSKIPLISTSET {
			@Override
			public Object apply(Object t) {
				return new ConcurrentSkipListSet<>();
			}
		},
		NEW_CONCURRENTHASHMAP {
			@Override
			public Object apply(Object t) {
				return new ConcurrentHashMap<>();
			}
		},
		NEW_SIZED_ARRAYLIST_OR_EMPTY {
			@Override
			public Object apply(Object t) {
				int size = ((Integer) t).intValue();
				return size == 0 ? Collections.emptyList() : new ArrayList<>(size);
			}
		},
		NEW_SIZED_ARRAYLIST {
			@Override
			public Object apply(Object t) {
				int size = ((Integer) t).intValue();
				return new ArrayList<>(size);
			}
		},
		NEW_OBJECT {
			@Override
			public Object apply(Object t) {
				return new Object();
			}
		}
	}

	private static enum NullableNaturalComparator implements Comparator {
		NULLS_FIRST {
			@Override
			public int compare(Object o1, Object o2) {
				return ObjectUtils.compareNullsFirst((Comparable) o1, (Comparable) o2);
			}
		},
		NULLS_LAST {
			@Override
			public int compare(Object o1, Object o2) {
				return ObjectUtils.compareNullsLast((Comparable) o1, (Comparable) o2);
			}
		};
	}

	private static final Comparator ENTRY_KEY_NATURAL_COMPARATOR = entryKeyComparator(Comparator.naturalOrder());

	private Functionals() {
		throw new UnsupportedOperationException();
	}

	/**
	 * @return A {@link Runnable} that does nothing.
	 */
	public static Runnable nullRunnable() {
		return NullFunctional.INSTANCE;
	}

	/**
	 * @param <T>
	 *            The type of the predicate argument.
	 * @return A {@link Predicate} that always returns <code>true</code>.
	 */
	public static <T> Predicate<T> alwaysPredicate() {
		return (Predicate<T>) FixedPredicate.ALWAYS;
	}

	/**
	 * @param <T>
	 *            The type of the predicate argument.
	 * @return A {@link Predicate} that always returns <code>false</code>.
	 */
	public static <T> Predicate<T> neverPredicate() {
		return (Predicate<T>) FixedPredicate.NEVER;
	}

	/**
	 * @param <T>
	 *            The type of the supplied object.
	 * @return A {@link Supplier} that always returns <code>null</code>.
	 */
	public static <T> Supplier<T> nullSupplier() {
		return (Supplier<T>) NullFunctional.INSTANCE;
	}

	/**
	 * @param <T>
	 *            The type of the consumer argument.
	 * @return A {@link Consumer} that does nothing.
	 */
	public static <T> Consumer<T> nullConsumer() {
		return (Consumer<T>) NullFunctional.INSTANCE;
	}

	/**
	 * @param <T>
	 *            The type of the first consumer argument.
	 * @param <U>
	 *            The type of the second consumer argument.
	 * @return A {@link BiConsumer} that does nothing.
	 */
	public static <T, U> BiConsumer<T, U> nullBiConsumer() {
		return (BiConsumer<T, U>) NullFunctional.INSTANCE;
	}

	/**
	 * @param <T>
	 *            The type of the function argument.
	 * @param <R>
	 *            The type of the function result.
	 * @return A {@link Function} that does nothing.
	 */
	public static <T, R> Function<T, R> nullFunction() {
		return Functions.NULL;
	}

	/**
	 * @param <T>
	 *            The type of the first function argument.
	 * @param <U>
	 *            The type of the second function argument.
	 * @param <R>
	 *            The type of the function result.
	 * @return A {@link BiFunction} that does nothing.
	 */
	public static <T, U, R> BiFunction<T, U, R> nullBiFunction() {
		return (BiFunction<T, U, R>) NullFunctional.INSTANCE;
	}

	/**
	 * @param <T>
	 *            The type of the function argument.
	 * @param <R>
	 *            The type of the function result.
	 * @return A {@link Function} that returns the argument it applies to.
	 */
	public static <T extends R, R> Function<T, R> identityFunction() {
		return Functions.IDENTITY;
	}

	/**
	 * Gets computer that creates an {@link Object} regardless of the argument it applies to.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @return The computer.
	 */
	public static <T> Function<T, Object> objectComputer() {
		return Functions.NEW_OBJECT;
	}

	/**
	 * Gets computer that creates an {@link ArrayList} regardless of the argument it applies to.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @param <E>
	 *            The element type.
	 * @return The computer.
	 */
	public static <T, E> Function<T, ArrayList<E>> arrayListComputer() {
		return Functions.NEW_ARRAYLIST;
	}

	/**
	 * Gets computer that creates an {@link HashSet} regardless of the argument it applies to.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @param <E>
	 *            The element type.
	 * @return The computer.
	 */
	public static <T, E> Function<T, HashSet<E>> hashSetComputer() {
		return Functions.NEW_HASHSET;
	}

	/**
	 * Gets computer that creates an {@link LinkedHashSet} regardless of the argument it applies to.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @param <E>
	 *            The element type.
	 * @return The computer.
	 */
	public static <T, E> Function<T, LinkedHashSet<E>> linkedHashSetComputer() {
		return Functions.NEW_LINKEDHASHSET;
	}

	/**
	 * Gets computer that creates an {@link TreeSet} regardless of the argument it applies to.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @param <E>
	 *            The element type.
	 * @return The computer.
	 */
	public static <T, E> Function<T, TreeSet<E>> treeSetComputer() {
		return Functions.NEW_TREESET;
	}

	/**
	 * Gets computer that creates an {@link ConcurrentSkipListSet} regardless of the argument it applies to.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @param <E>
	 *            The element type.
	 * @return The computer.
	 */
	public static <T, E> Function<T, ConcurrentSkipListSet<E>> concurrentSkipListSetComputer() {
		return Functions.NEW_CONCURRENTSKIPLISTSET;
	}

	/**
	 * Gets a computer that creates an {@link EnumSet} regardless of the argument it applies to.
	 * <p>
	 * The enum set will have the argument enum class as its element type.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @param <E>
	 *            The enum type.
	 * @param enumtype
	 *            The enum class.
	 * @return The computer.
	 */
	public static <T, E extends Enum<E>> Function<T, EnumSet<E>> enumSetComputer(Class<E> enumtype) {
		return new EmptyEnumSetComputer<>(enumtype);
	}

	/**
	 * Gets computer that creates an identity hash set regardless of the argument it applies to.
	 * <p>
	 * The identity hash set is created by calling
	 * <code>ObjectUtils.{@link ObjectUtils#newSetFromMap(Map) setFromMap}(new {@link IdentityHashMap}&lt;&gt;())</code>.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @param <E>
	 *            The element type.
	 * @return The computer.
	 */
	public static <T, E> Function<T, Set<E>> identityHashSetComputer() {
		return Functions.NEW_IDENTITYHASHSET;
	}

	/**
	 * Gets computer that creates an {@link IdentityHashMap} regardless of the argument it applies to.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @return The computer.
	 */
	public static <T, K, V> Function<T, IdentityHashMap<K, V>> identityHashMapComputer() {
		return Functions.NEW_IDENTITYHASHMAP;
	}

	/**
	 * Gets computer that creates an {@link HashMap} regardless of the argument it applies to.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @return The computer.
	 */
	public static <T, K, V> Function<T, HashMap<K, V>> hashMapComputer() {
		return Functions.NEW_HASHMAP;
	}

	/**
	 * Gets computer that creates an {@link LinkedHashMap} regardless of the argument it applies to.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @return The computer.
	 */
	public static <T, K, V> Function<T, LinkedHashMap<K, V>> linkedHashMapComputer() {
		return Functions.NEW_LINKEDHASHMAP;
	}

	/**
	 * Gets computer that creates an {@link TreeMap} regardless of the argument it applies to.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @return The computer.
	 */
	public static <T, K, V> Function<T, TreeMap<K, V>> treeMapComputer() {
		return Functions.NEW_TREEMAP;
	}

	/**
	 * Gets computer that creates an {@link ConcurrentSkipListMap} regardless of the argument it applies to.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @return The computer.
	 */
	public static <T, K, V> Function<T, ConcurrentSkipListMap<K, V>> concurrentSkipListMapComputer() {
		return Functions.NEW_CONCURRENTSKIPLISTMAP;
	}

	/**
	 * Gets computer that creates an {@link ConcurrentHashMap} regardless of the argument it applies to.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @return The computer.
	 */
	public static <T, K, V> Function<T, ConcurrentHashMap<K, V>> concurrentHashMapComputer() {
		return Functions.NEW_CONCURRENTHASHMAP;
	}

	/**
	 * Gets a computer that creates an {@link EnumMap} regardless of the argument it applies to.
	 * <p>
	 * The enum map will have the argument enum class as its key type.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * 
	 * @param <T>
	 *            The type of the function argument.
	 * @param <K>
	 *            The key enum type.
	 * @param <V>
	 *            The value type.
	 * @return The computer.
	 */
	public static <T, K extends Enum<K>, V> Function<T, EnumMap<K, V>> enumMapComputer(Class<K> enumtype) {
		return new EmptyEnumMapComputer<>(enumtype);
	}

	/**
	 * Converts the argument {@link Supplier} to a computer function.
	 * <p>
	 * Computers can be used in {@link Map#computeIfAbsent(Object, Function)} method.
	 * <p>
	 * The returned computer will call {@link Supplier#get()} regardless of the argument it applies to.
	 * 
	 * @param <K>
	 *            The type of the function argument.
	 * @param <T>
	 *            The type of the supplied object.
	 * @param supplier
	 *            The supplier.
	 * @return The computer.
	 */
	public static <K, T> Function<K, T> toComputer(Supplier<? extends T> supplier) {
		return new SupplierComputerFunction<>(supplier);
	}

	/**
	 * Gets a {@link Supplier} that always returns the same value.
	 * 
	 * @param <T>
	 *            The type of the argument.
	 * @param val
	 *            The value.
	 * @return The supplier.
	 */
	public static <T> Supplier<T> valSupplier(T val) {
		return new ValueSupplier<>(val);
	}

	/**
	 * Gets a function that creates a list with for the capacity it is applied to.
	 * <p>
	 * The result of the function is an immutable empty list if the capacity is 0, else it is a list that can hold at
	 * least up to capacity number of elements.
	 * 
	 * @param <T>
	 *            The array list element type.
	 * @return A creator function.
	 */
	public static <T> Function<Integer, List<T>> sizedArrayListOrEmptyCreator() {
		return Functions.NEW_SIZED_ARRAYLIST_OR_EMPTY;
	}

	/**
	 * Gets a function that creates an {@link ArrayList} with for the capacity it is applied to.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * Function&lt;Integer, ArrayList&lt;T&gt;&gt; creator = ArrayList::new;
	 * </pre>
	 * 
	 * @param <T>
	 *            The array list element type.
	 * @return A creator function.
	 */
	public static <T> Function<Integer, ArrayList<T>> sizedArrayListCreator() {
		return Functions.NEW_SIZED_ARRAYLIST;
	}

	/**
	 * Creates a supplier that will return the specified default value if the supplier returns <code>null</code>.
	 * <p>
	 * The returned supplier will return the result of the argument supplier if non-<code>null</code>, else the
	 * specified default value. If the supplier argument is <code>null</code>, the default value will always be
	 * returned.
	 * 
	 * @param <T>
	 *            The type of the supplied object.
	 * @param supplier
	 *            The supplier.
	 * @param defaultvalue
	 *            The default value to use.
	 * @return The defaulting supplier.
	 */
	public static <T> Supplier<T> defaultedSupplier(Supplier<? extends T> supplier, T defaultvalue) {
		if (supplier == null) {
			return valSupplier(defaultvalue);
		}
		return new DefaultedSupplier<>(defaultvalue, supplier);
	}

	/**
	 * Gets a predicate that compares its argument to this function argument by identity.
	 * 
	 * @param <T>
	 *            The type that the predicate tests.
	 * @param o
	 *            The object to identity compare the tested object to.
	 * @return The predicate.
	 */
	public static <T> Predicate<T> identityEqualsPredicate(Object o) {
		return new IdentityEqualsPredicate<>(o);
	}

	/**
	 * Converts the argument iterator to a {@link Supplier}.
	 * <p>
	 * The returned supplier will return <code>null</code> if there are no more elements in the iterator. If the
	 * iterator contains <code>null</code> elements, the code that uses the created supplier may not work properly.
	 * <p>
	 * The returned supplier is not serializable.
	 * 
	 * @param <T>
	 *            The element type.
	 * @param iterator
	 *            The iterator.
	 * @return The created supplier.
	 */
	public static <T> Supplier<T> toSupplier(Iterator<? extends T> iterator) {
		return () -> {
			if (!iterator.hasNext()) {
				return null;
			}
			return iterator.next();
		};
	}

	/**
	 * Converts the argument iterator to a {@link Supplier}.
	 * <p>
	 * Same as {@link #toSupplier(Iterator)}, but the {@link Supplier#get()} calls of the returned supplier is
	 * synchronized on itself.
	 * <p>
	 * The returned supplier is not serializable.
	 * 
	 * @param <T>
	 *            The element type.
	 * @param iterator
	 *            The iterator.
	 * @return The created supplier.
	 */
	public static <T> Supplier<T> toSynchronizedSupplier(Iterator<? extends T> iterator) {
		return new Supplier<T>() {
			@Override
			public synchronized T get() {
				if (!iterator.hasNext()) {
					return null;
				}
				return iterator.next();
			}
		};
	}

	/**
	 * Converts the argument iterator to a {@link Supplier} that removes the elements after retrieving them.
	 * <p>
	 * The returned supplier will return <code>null</code> if there are no more elements in the iterator. If the
	 * iterator contains <code>null</code> elements, the code that uses the created supplier may not work properly.
	 * <p>
	 * The returned supplier will call {@link Iterator#remove()} after retrieving each element. If the iterator is
	 * unmodifiable, then the exception will be propagated to the caller.
	 * <p>
	 * The returned supplier is not serializable.
	 * 
	 * @param <T>
	 *            The element type.
	 * @param iterator
	 *            The iterator.
	 * @return The created supplier.
	 */
	public static <T> Supplier<T> toRemovingSupplier(Iterator<? extends T> iterator) {
		return () -> {
			if (!iterator.hasNext()) {
				return null;
			}
			T result = iterator.next();
			iterator.remove();
			return result;
		};
	}

	/**
	 * Converts the argument iterator to a {@link Supplier} that removes the elements after retrieving them.
	 * <p>
	 * Same as {@link #toRemovingSupplier(Iterator)}, but the {@link Supplier#get()} calls of the returned supplier is
	 * synchronized on itself.
	 * <p>
	 * The returned supplier is not serializable.
	 * 
	 * @param <T>
	 *            The element type.
	 * @param iterator
	 *            The iterator.
	 * @return The created supplier.
	 */
	public static <T> Supplier<T> toSynchronizedRemovingSupplier(Iterator<? extends T> iterator) {
		return new Supplier<T>() {
			@Override
			public synchronized T get() {
				if (!iterator.hasNext()) {
					return null;
				}
				T result = iterator.next();
				iterator.remove();
				return result;
			}
		};
	}

	/**
	 * Converts the argument enumeration to a {@link Supplier}.
	 * <p>
	 * The returned supplier will return <code>null</code> if there are no more elements in the enumeration. If the
	 * iterator contains <code>null</code> elements, the code that uses the created supplier may not work properly.
	 * <p>
	 * The returned supplier is not serializable.
	 * 
	 * @param <T>
	 *            The element type.
	 * @param enumeration
	 *            The enumeration.
	 * @return The created supplier.
	 */
	public static <T> Supplier<T> toSupplier(Enumeration<? extends T> enumeration) {
		return new Supplier<T>() {
			@Override
			public T get() {
				if (!enumeration.hasMoreElements()) {
					return null;
				}
				return enumeration.nextElement();
			}
		};
	}

	/**
	 * Converts the argument enumeration to a {@link Supplier}.
	 * <p>
	 * Same as {@link #toSupplier(Enumeration)}, but the {@link Supplier#get()} calls of the returned supplier is
	 * synchronized on itself.
	 * <p>
	 * The returned supplier is not serializable.
	 * 
	 * @param <T>
	 *            The element type.
	 * @param enumeration
	 *            The enumeration.
	 * @return The created supplier.
	 */
	public static <T> Supplier<T> toSynchronizedSupplier(Enumeration<? extends T> enumeration) {
		return new Supplier<T>() {
			@Override
			public synchronized T get() {
				if (!enumeration.hasMoreElements()) {
					return null;
				}
				return enumeration.nextElement();
			}
		};
	}

	/**
	 * Converts a supplier of iterators to an {@link Iterable}.
	 * <p>
	 * The returned iterable will call {@link Supplier#get()} for every iterator it creates.
	 * 
	 * @param <T>
	 *            The element type.
	 * @param itsupplier
	 *            The iterator supplier.
	 * @return The converted iterable.
	 */
	public static <T> Iterable<T> toIterable(Supplier<? extends Iterator<T>> itsupplier) {
		return new SupplierIterable<>(itsupplier);
	}

	/**
	 * Converts the argument comparator to a {@link Map.Entry} key comparator.
	 * <p>
	 * If the argument is <code>null</code>, {@link #entryKeyNaturalComparator()} is returned.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param <E>
	 *            The map entry type.
	 * @param comparator
	 *            The key comparator.
	 * @return A comparator that compares the entries by their keys using the argument comparator.
	 */
	public static <K, V, E extends Map.Entry<K, V>> Comparator<E> entryKeyComparator(Comparator<? super K> comparator) {
		if (comparator == null) {
			return ENTRY_KEY_NATURAL_COMPARATOR;
		}
		return new EntryKeyComparator<>(comparator);
	}

	/**
	 * Gets a comparator functional that compares its {@link Map.Entry} argument by the natural order of their keys.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param <E>
	 *            The map entry type.
	 * @return The comparator.
	 */
	public static <K, V, E extends Map.Entry<K, V>> Comparator<E> entryKeyNaturalComparator() {
		return ENTRY_KEY_NATURAL_COMPARATOR;
	}

	/**
	 * Gets a comparator that sorts its non-<code>null</code> arguments by natural order, else the <code>null</code>
	 * values first.
	 * 
	 * @param <T>
	 *            The type of the compared objects.
	 * @return The comparator.
	 * @see ObjectUtils#compareNullsFirst(Comparable, Comparable)
	 */
	public static <T extends Comparable<? super T>> Comparator<T> nullsFirstNaturalComparator() {
		return NullableNaturalComparator.NULLS_FIRST;
	}

	/**
	 * Gets a comparator that sorts its non-<code>null</code> arguments by natural order, else the <code>null</code>
	 * values last.
	 * 
	 * @param <T>
	 *            The type of the compared objects.
	 * @return The comparator.
	 * @see ObjectUtils#compareNullsLast(Comparable, Comparable)
	 */
	public static <T extends Comparable<? super T>> Comparator<T> nullsLastNaturalComparator() {
		return NullableNaturalComparator.NULLS_LAST;
	}

	/**
	 * Converts the argument throwing runnable to a simple {@link Runnable} by rethrowing any exceptions sneakily.
	 * <p>
	 * This method should be <b>very</b> rarely used if ever. Any exception that is thrown by the
	 * {@link ThrowingRunnable#run()} method will be rethrown out of the returned {@link Runnable#run()} method. If the
	 * thrown exception is checked, the caller of the {@link Runnable#run()} method may not be able to actually catch
	 * the exception.
	 * 
	 * @param run
	 *            The throwing runnable.
	 * @return The sneakly throwing created runnable.
	 */
	public static Runnable sneakyThrowingRunnable(ThrowingRunnable run) {
		return new SneakyThrowingRunnable(run);
	}
}
