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
package saker.build.thirdparty.saker.util.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.function.ObjectReaderFunction;
import saker.build.thirdparty.saker.util.io.function.ObjectWriterFunction;

/**
 * Class containing utility functions related to object and data serialization.
 * <p>
 * This class defines methods for complex object to read from and write to object streams. Any method that starts with
 * <code>readExternal</code> or <code>writeExternal</code> implement functionality that read or write the argument
 * object(s) to a specified stream in a given format. The format of the serialization is unified among the externalizing
 * functions:
 * <ul>
 * <li>For collections:
 * <ul>
 * <li>The {@linkplain Collection#size() size} of the collection is written to the stream as a
 * {@linkplain ObjectOutput#writeInt(int) raw int}.</li>
 * <li>If the collections is <code>null</code>, the size of <code>-1</code> is written to the stream, and no elements
 * are written.</li>
 * <li>Each element in the externalized collection is written out to the stream after each other.</li>
 * </ul>
 * </li>
 * <li>For maps:
 * <ul>
 * <li>The {@linkplain Map#size() size} of the map is written to the stream as a {@linkplain ObjectOutput#writeInt(int)
 * raw int}.</li>
 * <li>If the map is <code>null</code>, the size <code>-1</code> is written to the stream, and no map entries are
 * written.</li>
 * <li>Each entry in the externalized map is written out as key and pair objects separately, in this order.</li>
 * </ul>
 * </li>
 * <li>For iterables:
 * <ul>
 * <li>If the iterable is <code>null</code>, an implementation dependent sentinel enum object is written to the stream
 * to signal that.</li>
 * <li>An iterator is created, and every element encountered is written out to the output.</li>
 * <li>An end-of-iterable sentinel enum object is written to the output, to signal that no more elements are
 * present.</li>
 * </ul>
 * </li>
 * <li>For array:
 * <ul>
 * <li>Same as for collections.</li>
 * </ul>
 * </li>
 * </ul>
 * The size of the collections/maps are queried from them before any serialization is done. Keep in mind, if that they
 * are concurrent collections, this query may not have O(1) complexity.
 * <p>
 * The elements of collections/maps are written out in the order they are encountered by their iterators.
 * <p>
 * If the externalized collections are sorted by a comparator, such as {@link SortedSet} or {@link SortedMap} instances,
 * the utility functions will <b>not</b> write the comparator of the collections. Callers should manually write the
 * comparators of such collections to the stream if necessary. When appropriate, consider sorting these collections by
 * their natural order, so there will be no such problems.
 * <p>
 * If the collections are concurrently modified during the serialization, meaning that if the externalizing methods fail
 * to enumerate enough elements in respect to the queried size, or there are more elements after <i>size</i> amount of
 * them have been written out, a {@link SerializationConcurrentModificationException} will be thrown. The
 * {@linkplain SerializationConcurrentModificationException#getDifferenceCount() difference count} of the written
 * elements can be queried from the exception.
 * <p>
 * Callers may optionally handle such scenario, but they are recommended to ensure that this never occurs, and fail the
 * serialization completely instead. If this happens, make sure to cease all modifications to the externalized
 * collections by employing some sort of synchronization.
 * <p>
 * Some functions in this class define themselves to read a specific type of collection from an input, or read them as
 * immutable collections. Using this can greatly reduce the memory footprint of the read objects, and greatly improve
 * the reading performance of deserialization. However, users should use great care when calling these functions.
 * <p>
 * <b>Important:</b> If an external reading method name starts with <code>readExternalSorted</code>, then that method
 * expects the underlying deserialized elements to be sorted in a given order to work. If the underlying elements are
 * not sorted for the associated comparator, then the returned collection <b>will not work correctly</b>. When using
 * such methods, make sure that the elements were externalized in appropriate order, or use a deserializing function
 * that doesn't have this requirement. The reading methods will <b>not</b> throw any exception if the elements are
 * encountered out of order.
 * <p>
 * <b>Important:</b> The deserializing methods auto-cast the objects to the required element type. These casts are
 * unchecked, therefore callers may run into runtime errors if the deserialized objects have different type. If callers
 * are unsure about the underlying type of the objects, they should either check the types of the elements, or not use
 * serialization methods provided by this class.
 * <p>
 * The serializing and deserializing methods are interchangeable, meaning that if a collection of a type was written out
 * using one of the externalizing methods, then it may be deserialized by any collection deserializing methods in this
 * class. E.g. A {@link Set} is written out using {@link #writeExternalCollection(ObjectOutput, Collection)}. When it is
 * being deserialized, the caller can use {@link #readExternalCollection(Collection, ObjectInput)},
 * {@link #readExternalImmutableList(ObjectInput)}, {@link #readExternalHashSet(ObjectInput)}, or any other collection
 * reading methods... (Unless noted otherwise in the documentation.) This only works for same object structured types,
 * meaning that collections cannot be read back as maps, or iterables, and vice versa. (The above mentioned sorted
 * requirements still in place.)
 * <p>
 * Methods that use custom serialization for objects are only interchangeable with other functions that use the same
 * custom serialization functions. (Such as
 * {@link #writeExternalCollection(DataOutput, Collection, ObjectWriterFunction)},
 * {@link #writeExternalExternalizableCollection(ObjectOutput, Collection)},
 * {@link #writeExternalUTFCollection(DataOutput, Collection)}, etc...)
 * <p>
 * In the documentation of the externalizing methods, there will be an identifier for the format of the serialized data.
 * Check these identifiers in the methods to help identify the interchangeable methods.
 * <p>
 * If you're using methods in this class, make sure that you understand the inner workings, and implications of them.
 */
public class SerialUtils {
	private SerialUtils() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Writes an object to the object output.
	 * <p>
	 * Format identifer: <code>object</code>
	 * <p>
	 * This method calls {@link ObjectOutput#writeObject(Object)} with the argument. Although it is very simple, it is
	 * present as a counterpart of {@link #readExternalObject(ObjectInput)}.
	 * 
	 * @param out
	 *            The object output.
	 * @param object
	 *            The object to write.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output is <code>null</code>.
	 */
	public static void writeExternalObject(ObjectOutput out, Object object) throws IOException, NullPointerException {
		Objects.requireNonNull(out, "out");
		out.writeObject(object);
	}

	/**
	 * Reads an objects from the object input.
	 * <p>
	 * Format identifer: <code>object</code>
	 * <p>
	 * This method simply calls {@link ObjectInput#readObject()}, and uncheckedly casts it to the return type.
	 * 
	 * @param <E>
	 *            The return type.
	 * @param in
	 *            The object input.
	 * @return The deserialized object.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <E> E readExternalObject(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		return (E) in.readObject();
	}

	/**
	 * Writes an iterable to the object output.
	 * <p>
	 * Format identifier: <code>iterable-object</code>
	 * 
	 * @param out
	 *            The object output.
	 * @param iterable
	 *            The iterable to write.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output is <code>null</code>.
	 */
	public static void writeExternalIterable(ObjectOutput out, Iterable<?> iterable)
			throws IOException, NullPointerException {
		Objects.requireNonNull(out, "out");
		if (iterable == null) {
			out.writeObject(CommonSentinel.NULL_INPUT);
		}
		for (Object o : iterable) {
			out.writeObject(o);
		}
		out.writeObject(CommonSentinel.END_OF_OBJECTS);
	}

	/**
	 * Reads an iterable from the object input.
	 * <p>
	 * Format identifier: <code>iterable-object</code>
	 * <p>
	 * The elements of the returned iterable is uncheckedly casted to the result element type.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @return The deserialized iterable.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <E> Iterable<E> readExternalIterable(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		Object o = in.readObject();
		if (o == CommonSentinel.NULL_INPUT) {
			return null;
		}
		if (o == CommonSentinel.END_OF_OBJECTS) {
			return Collections.emptyList();
		}
		List<E> result = new ArrayList<>();
		result.add((E) o);
		while (true) {
			o = in.readObject();
			if (o == CommonSentinel.END_OF_OBJECTS) {
				break;
			}
			result.add((E) o);
		}
		return result;
	}

	/**
	 * Reads an iterable from the object input and adds the elements to the argument collection.
	 * <p>
	 * Format identifier: <code>iterable-object</code>
	 * <p>
	 * The element type of the deserialized objects are uncheckedly casted.
	 * 
	 * @param <C>
	 *            The type of the collection.
	 * @param <E>
	 *            The element type.
	 * @param coll
	 *            The collection to add the deserialized objects to.
	 * @param in
	 *            The object input.
	 * @return The argument collection, or <code>null</code> if a <code>null</code> iterable was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input or the collection is <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <C extends Collection<? super E>, E> C readExternalIterable(C coll, ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		Object o = in.readObject();
		if (o == CommonSentinel.NULL_INPUT) {
			return null;
		}
		if (o == CommonSentinel.END_OF_OBJECTS) {
			return coll;
		}
		Objects.requireNonNull(coll, "collection");
		coll.add((E) o);
		while (true) {
			o = in.readObject();
			if (o == CommonSentinel.END_OF_OBJECTS) {
				break;
			}
			coll.add((E) o);
		}
		return coll;
	}

	/**
	 * Writes a collection to the object output.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * 
	 * @param out
	 *            The object output.
	 * @param coll
	 *            The collection to write.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output is <code>null</code>.
	 * @throws SerializationConcurrentModificationException
	 *             If the size of the collection doesn't match the numer of elements encountered in it.
	 */
	public static void writeExternalCollection(ObjectOutput out, Collection<?> coll)
			throws IOException, NullPointerException, SerializationConcurrentModificationException {
		Objects.requireNonNull(out, "out");
		if (coll == null) {
			out.writeInt(-1);
			return;
		}
		int size = coll.size();
		out.writeInt(size);
		if (size > 0) {
			Iterator<?> it = coll.iterator();
			for (; it.hasNext();) {
				Object o = it.next();
				out.writeObject(o);
				--size;
			}
			checkCollectionConcurrentModificationsSize(size, it);
		}
	}

	/**
	 * Writes a collection to the object output using a custom serializer.
	 * <p>
	 * Format identifier: <code>collection-custom</code>
	 * 
	 * @param <E>
	 *            The type of the elements.
	 * @param <OUT>
	 *            The type of the object output.
	 * @param out
	 *            The object output.
	 * @param coll
	 *            The collection to write.
	 * @param writer
	 *            The custom serializer for each element object.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output or the writer is <code>null</code>.
	 * @throws SerializationConcurrentModificationException
	 *             If the size of the collection doesn't match the numer of elements encountered in it.
	 */
	public static <E, OUT extends DataOutput> void writeExternalCollection(OUT out, Collection<E> coll,
			ObjectWriterFunction<? super OUT, ? super E> writer)
			throws IOException, NullPointerException, SerializationConcurrentModificationException {
		Objects.requireNonNull(out, "out");
		if (coll == null) {
			out.writeInt(-1);
			return;
		}
		int size = coll.size();
		out.writeInt(size);
		if (size > 0) {
			Objects.requireNonNull(writer, "writer");
			Iterator<E> it = coll.iterator();
			for (; it.hasNext();) {
				E t = it.next();
				writer.apply(out, t);
				--size;
			}
			checkCollectionConcurrentModificationsSize(size, it);
		}
	}

	/**
	 * Writes a collection of externalizable elements to the object output.
	 * <p>
	 * Format identifier: <code>collection-externalizable</code>
	 * <p>
	 * The collection may not contain <code>null</code> elements.
	 * 
	 * @param out
	 *            The object output.
	 * @param coll
	 *            The collection to write.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output or any of the elements are <code>null</code>.
	 * @throws SerializationConcurrentModificationException
	 *             If the size of the collection doesn't match the numer of elements encountered in it.
	 */
	public static void writeExternalExternalizableCollection(ObjectOutput out,
			Collection<? extends Externalizable> coll)
			throws IOException, NullPointerException, SerializationConcurrentModificationException {
		Objects.requireNonNull(out, "out");
		if (coll == null) {
			out.writeInt(-1);
			return;
		}
		int size = coll.size();
		out.writeInt(size);
		if (size > 0) {
			Iterator<? extends Externalizable> it = coll.iterator();
			for (; it.hasNext();) {
				Externalizable extobj = it.next();
				Objects.requireNonNull(extobj, "externalizable element");
				extobj.writeExternal(out);
				--size;
			}
			checkCollectionConcurrentModificationsSize(size, it);
		}
	}

	/**
	 * Writes a collection of string elements to the object output.
	 * <p>
	 * Format identifier: <code>collection-utf</code>
	 * 
	 * @param out
	 *            The object output.
	 * @param coll
	 *            The collection to write.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output is <code>null</code>. If the object output doesn't allow <code>null</code> strings to
	 *             be written out, and any of the elements are <code>null</code>.
	 * @throws SerializationConcurrentModificationException
	 *             If the size of the collection doesn't match the numer of elements encountered in it.
	 */
	public static void writeExternalUTFCollection(DataOutput out, Collection<String> coll)
			throws IOException, NullPointerException, SerializationConcurrentModificationException {
		Objects.requireNonNull(out, "out");
		if (coll == null) {
			out.writeInt(-1);
			return;
		}
		int size = coll.size();
		out.writeInt(size);
		if (size > 0) {
			Iterator<String> it = coll.iterator();
			for (; it.hasNext();) {
				String s = it.next();
				out.writeUTF(s);
				--size;
			}
			checkCollectionConcurrentModificationsSize(size, it);
		}
	}

	/**
	 * Writes an array to the object output.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * 
	 * @param out
	 *            The object output.
	 * @param array
	 *            The array to write.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output is <code>null</code>.
	 */
	public static void writeExternalArray(ObjectOutput out, Object[] array) throws IOException, NullPointerException {
		Objects.requireNonNull(out, "out");
		if (array == null) {
			out.writeInt(-1);
			return;
		}
		out.writeInt(array.length);
		for (Object o : array) {
			out.writeObject(o);
		}
	}

	/**
	 * Writes a specific range of an array to the object output.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * 
	 * @param out
	 *            The object output.
	 * @param array
	 *            The array to write.
	 * @param offset
	 *            The offset index of the range.
	 * @param length
	 *            The number of objects to write starting from the offset index.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the length is negative.
	 * @throws IndexOutOfBoundsException
	 *             If the range does not fully reside in the array.
	 */
	public static void writeExternalArray(ObjectOutput out, Object[] array, int offset, int length)
			throws IOException, NullPointerException, IllegalArgumentException, IndexOutOfBoundsException {
		Objects.requireNonNull(out, "out");
		if (array == null) {
			out.writeInt(-1);
			return;
		}
		ArrayUtils.requireArrayRange(array, offset, length);
		int end = offset + length;
		out.writeInt(length);
		for (int i = offset; i != end; ++i) {
			out.writeObject(array[i]);
		}
	}

	/**
	 * Writes an array to the object output using the custom element serializer.
	 * <p>
	 * Format identifier: <code>collection-custom</code>
	 * 
	 * @param <E>
	 *            The element type.
	 * @param <OUT>
	 *            The type of the object output.
	 * @param out
	 *            The object output.
	 * @param array
	 *            The array to write.
	 * @param elemwriter
	 *            The custom serializer for each element object.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output, or the element writer is <code>null</code>.
	 */
	public static <E, OUT extends DataOutput> void writeExternalArray(OUT out, E[] array,
			ObjectWriterFunction<? super OUT, ? super E> elemwriter) throws IOException, NullPointerException {
		Objects.requireNonNull(out, "out");
		if (array == null) {
			out.writeInt(-1);
			return;
		}
		out.writeInt(array.length);
		if (array.length > 0) {
			Objects.requireNonNull(elemwriter, "writer");
			for (E e : array) {
				elemwriter.apply(out, e);
			}
		}
	}

	/**
	 * Writes a byte array to the object output.
	 * <p>
	 * Format identifier: <code>collection-byte</code>
	 * 
	 * @param out
	 *            The object output.
	 * @param array
	 *            The array to write.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output is <code>null</code>.
	 */
	public static void writeExternalByteArray(ObjectOutput out, byte[] array) throws IOException, NullPointerException {
		Objects.requireNonNull(out, "out");
		if (array == null) {
			out.writeInt(-1);
			return;
		}
		out.writeInt(array.length);
		out.write(array);
	}

	/**
	 * Writes the given range of the argument byte array to the object output.
	 * <p>
	 * Format identifier: <code>collection-byte</code>
	 * 
	 * @param out
	 *            The object output.
	 * @param array
	 *            The array to write.
	 * @param offset
	 *            The offset index of the range.
	 * @param length
	 *            The number of bytes to write starting from the offset index.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the length is negative.
	 * @throws IndexOutOfBoundsException
	 *             If the range does not fully reside in the array.
	 */
	public static void writeExternalByteArray(ObjectOutput out, byte[] array, int offset, int length)
			throws IOException, NullPointerException, IllegalArgumentException, IndexOutOfBoundsException {
		Objects.requireNonNull(out, "out");
		if (array == null) {
			out.writeInt(-1);
			return;
		}
		ArrayUtils.requireArrayRangeLength(array.length, offset, length);
		out.writeInt(length);
		out.write(array, offset, length);
	}

	/**
	 * Reads an immutable hash set from the object input.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * <p>
	 * The element type of the deserialized objects are uncheckedly casted.
	 * <p>
	 * The iteration order in the returned set might not be the same as order of the deserialized objects.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @return An immutable hash set, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 * @see HashSet
	 */
	@SuppressWarnings("unchecked")
	public static <E> Set<E> readExternalImmutableHashSet(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return Collections.emptySet();
		}
		if (size == 1) {
			return ImmutableUtils.singletonSet((E) in.readObject());
		}
		//XXX maybe implement a more efficient immutable hash set
		Set<E> set = new HashSet<>(size * 4 / 3 + 1, 0.75f);
		while (size-- > 0) {
			set.add((E) in.readObject());
		}
		return ImmutableUtils.unmodifiableSet(set);
	}

	/**
	 * Reads an immutable linked hash set from the object input.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * <p>
	 * The element type of the deserialized objects are uncheckedly casted.
	 * <p>
	 * The iteration order in the returned set is the same as order of the deserialized objects.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @return An immutable linked hash set, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 * @see LinkedHashSet
	 */
	@SuppressWarnings("unchecked")
	public static <E> Set<E> readExternalImmutableLinkedHashSet(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return Collections.emptySet();
		}
		if (size == 1) {
			return ImmutableUtils.singletonSet((E) in.readObject());
		}
		//XXX maybe implement a more efficient immutable linked hash set
		Set<E> set = new LinkedHashSet<>(size * 4 / 3 + 1, 0.75f);
		while (size-- > 0) {
			set.add((E) in.readObject());
		}
		return ImmutableUtils.unmodifiableSet(set);
	}

	/**
	 * Reads an immutable navigable set from the object input.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * <p>
	 * The element type of the deserialized objects are uncheckedly casted.
	 * <p>
	 * The returned set will be ordered by the natural order of the elements.
	 * <p>
	 * The elements in the stream are not required to be already in order.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @return An immutable navigable set, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static <E> NavigableSet<E> readExternalImmutableNavigableSet(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		return readExternalImmutableNavigableSet(in, null);
	}

	/**
	 * Reads an immutable navigable set with the given sorting order from the object input.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * <p>
	 * The element type of the deserialized objects are uncheckedly casted.
	 * <p>
	 * The returned set will be ordered using the argument comparator.
	 * <p>
	 * The elements in the stream are not required to be already in order.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @param comparator
	 *            The comparator that defines the order of the returned set.
	 * @return An immutable navigable set, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <E> NavigableSet<E> readExternalImmutableNavigableSet(ObjectInput in,
			Comparator<? super E> comparator) throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return ImmutableUtils.emptyNavigableSet(comparator);
		}
		if (size == 1) {
			return ImmutableUtils.singletonNavigableSet((E) in.readObject(), comparator);
		}
		//XXX maybe implement a more efficient immutable navigable set
		NavigableSet<E> set = new TreeSet<>(comparator);
		while (size-- > 0) {
			set.add((E) in.readObject());
		}
		return ImmutableUtils.unmodifiableNavigableSet(set);
	}

	/**
	 * Reads an immutable list from the object input.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * <p>
	 * The element type of the deserialized objects are uncheckedly casted.
	 * 
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @return An immutable list, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 * @see List
	 */
	@SuppressWarnings("unchecked")
	public static <E> List<E> readExternalImmutableList(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return Collections.emptyList();
		}
		if (size == 1) {
			return ImmutableUtils.singletonList((E) in.readObject());
		}
		Object[] items = new Object[size];
		for (int i = 0; i < size; i++) {
			items[i] = in.readObject();
		}
		return ImmutableUtils.asUnmodifiableArrayList((E[]) items);
	}

	/**
	 * Reads an immutable list from the object input using a custom deserializer for its elements.
	 * <p>
	 * Format identifier: <code>collection-custom</code>
	 * 
	 * @param <E>
	 *            The element type.
	 * @param <IN>
	 *            The type of the object input.
	 * @param in
	 *            The object input.
	 * @param reader
	 *            The custom deserializer for each element object.
	 * @return An immutable list, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, or the element reader is <code>null</code>.
	 * @see List
	 */
	@SuppressWarnings("unchecked")
	public static <E, IN extends DataInput> List<E> readExternalImmutableList(IN in,
			ObjectReaderFunction<? super IN, ? extends E> reader)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return Collections.emptyList();
		}
		Objects.requireNonNull(reader, "reader");
		if (size == 1) {
			return ImmutableUtils.singletonList(reader.apply(in));
		}
		Object[] items = new Object[size];
		for (int i = 0; i < size; i++) {
			items[i] = reader.apply(in);
		}
		return ImmutableUtils.asUnmodifiableArrayList((E[]) items);
	}

	/**
	 * Reads a tree set from the object input given that the deserialized objects are already in order.
	 * <p>
	 * <b>Important:</b> This method relies on that the deserialized objects are ordered by their natural order. If this
	 * requirement is violated, the returned set will not work correctly.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * <p>
	 * The element type of the deserialized objects are uncheckedly casted.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * readExternalSortedTreeSet(in, null);
	 * </pre>
	 * 
	 * If you're unsure whether the serialized entries are in appropriate order, use:
	 * 
	 * <pre>
	 * readExternalCollection(new TreeSet&lt;&gt;(), in);
	 * </pre>
	 * 
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @return A tree set, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 * @see TreeSet
	 */
	public static <E> TreeSet<E> readExternalSortedTreeSet(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return new TreeSet<>();
		}
		return ObjectUtils.createTreeSetFromSortedIterator(new ObjectReadingIterator<>(size, in), size);
//		return new TreeSet<>(new FakeSortedSet<>(ObjectUtils.onceIterable(new ObjectReadingIterator<>(size, in)), size));
	}

	/**
	 * Reads a tree set from the object input given that the deserialized objects are already in order for the argument
	 * comparator.
	 * <p>
	 * <b>Important:</b> This method relies on that the deserialized objects are ordered by the specified comparator. If
	 * this requirement is violated, the returned set will not work correctly.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * <p>
	 * The element type of the deserialized objects are uncheckedly casted.
	 * <p>
	 * If you're unsure whether the serialized entries are in appropriate order, use:
	 * 
	 * <pre>
	 * readExternalCollection(new TreeSet&lt;&gt;(comparator), in);
	 * </pre>
	 * 
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @param comparator
	 *            The comparator which defines the order of the serialized objects and the returned set.
	 *            <code>null</code> means the natural order.
	 * @return A tree set, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 * @see TreeSet
	 */
	public static <E> TreeSet<E> readExternalSortedTreeSet(ObjectInput in, Comparator<? super E> comparator)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return new TreeSet<>(comparator);
		}
		return ObjectUtils.createTreeSetFromSortedIterator(new ObjectReadingIterator<>(size, in), size, comparator);
//		return new TreeSet<>(new FakeSortedSet<>(ObjectUtils.onceIterable(new ObjectReadingIterator<>(size, in)), size, comparator));
	}

	/**
	 * Reads an immutable navigable set from the object input given that the deserialized objects are already in order.
	 * <p>
	 * <b>Important:</b> This method relies on that the deserialized objects are ordered by their natural order. If this
	 * requirement is violated, the returned set will not work correctly.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * <p>
	 * The element type of the deserialized objects are uncheckedly casted.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * readExternalSortedImmutableNavigableSet(in, null);
	 * </pre>
	 * 
	 * If you're unsure whether the serialized entries are in appropriate order, use:
	 * 
	 * <pre>
	 * ImmutableUtils.unmodifiableNavigableSet(readExternalCollection(new TreeSet&lt;&gt;(), in));
	 * </pre>
	 * 
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @return An immutable navigable set, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static <E> NavigableSet<E> readExternalSortedImmutableNavigableSet(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		//in NPE is tested in forwarded function
		return readExternalSortedImmutableNavigableSet(in, null);
	}

	/**
	 * Reads an immutable navigable set from the object input given that the deserialized objects are already in order
	 * for the argument comparator.
	 * <p>
	 * <b>Important:</b> This method relies on that the deserialized objects are ordered by the specified comparator. If
	 * this requirement is violated, the returned set will not work correctly.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * <p>
	 * The element type of the deserialized objects are uncheckedly casted.
	 * <p>
	 * If you're unsure whether the serialized entries are in appropriate order, use:
	 * 
	 * <pre>
	 * ImmutableUtils.unmodifiableNavigableSet(readExternalCollection(new TreeSet&lt;&gt;(comparator), in));
	 * </pre>
	 * 
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @param comparator
	 *            The comparator which defines the order of the serialized objects and the returned set.
	 *            <code>null</code> means the natural order.
	 * @return an immutable navigable set, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 * @see TreeSet
	 */
	@SuppressWarnings({ "unchecked" })
	public static <E> NavigableSet<E> readExternalSortedImmutableNavigableSet(ObjectInput in,
			Comparator<? super E> comparator) throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return ImmutableUtils.emptyNavigableSet(comparator);
		}
		if (size == 1) {
			return ImmutableUtils.singletonNavigableSet((E) in.readObject(), comparator);
		}
		Object[] items = new Object[size];
		for (int i = 0; i < size; i++) {
			items[i] = in.readObject();
		}
		return ImmutableUtils.unmodifiableNavigableSet((E[]) items, comparator);
	}

	/**
	 * Reads a collection from the object input and adds its elements to the argument collection.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * <p>
	 * The element type of the deserialized objects are uncheckedly casted.
	 * 
	 * @param <C>
	 *            The type of the collection.
	 * @param <E>
	 *            The element type.
	 * @param coll
	 *            The collection to add the deserialized objects to.
	 * @param in
	 *            The object input.
	 * @return The argument collection, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input or the collection is <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <C extends Collection<E>, E> C readExternalCollection(C coll, ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		Objects.requireNonNull(coll, "collection");
		if (size > 0) {
			while (size-- > 0) {
				coll.add((E) in.readObject());
			}
		}
		return coll;
	}

	/**
	 * Reads a collection from the object input, that is instantiated by the argument supplier.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * <p>
	 * The argument collection supplier can be used to construct an appropriate collection for a given number of
	 * elements. <br>
	 * Example use-case:
	 * 
	 * <pre>
	 * readExternalCollectionSize({@linkplain ArrayList}::new);
	 * </pre>
	 * <p>
	 * The element type of the deserialized objects are uncheckedly casted.
	 * 
	 * @param <C>
	 *            The type of the collection.
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @param collsupplier
	 *            The collection supplier for the count of elements to be deserialized.
	 * @return The deserialized collection. This is either <code>null</code> or the same collection returned by the
	 *             supplier.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, the collection supplier, or the created collection is <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <C extends Collection<E>, E> C readExternalCollectionSize(ObjectInput in,
			IntFunction<? extends C> collsupplier) throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		Objects.requireNonNull(collsupplier, "collection supplier");
		C coll = collsupplier.apply(size);
		Objects.requireNonNull(coll, "collection");
		while (size-- > 0) {
			coll.add((E) in.readObject());
		}
		return coll;
	}

	/**
	 * Reads a collection from the object input using a custom deserializer and adds its elements to the argument
	 * collection.
	 * <p>
	 * Format identifier: <code>collection-custom</code>
	 * <p>
	 * The caller is responsible for passing the reader function that reads the object in the same format as it was
	 * written out.
	 * 
	 * @param <C>
	 *            The type of the collection.
	 * @param <E>
	 *            The element type.
	 * @param <IN>
	 *            The type of the object input.
	 * @param coll
	 *            The collection to add the deserialized objects to.
	 * @param in
	 *            The object input.
	 * @param reader
	 *            The custom deserializer for each element object.
	 * @return The argument collection, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, the collection, or the reader is <code>null</code>.
	 */
	public static <C extends Collection<E>, E, IN extends DataInput> C readExternalCollection(C coll, IN in,
			ObjectReaderFunction<? super IN, ? extends E> reader)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		Objects.requireNonNull(coll, "collection");
		if (size > 0) {
			Objects.requireNonNull(reader, "reader");
			while (size-- > 0) {
				coll.add(reader.apply(in));
			}
		}
		return coll;
	}

	/**
	 * Reads a collection of externalizable elements and adds its elements to the argument collection.
	 * <p>
	 * Format identifier: <code>collection-externalizable</code>
	 * 
	 * @param <E>
	 *            The element type.
	 * @param <C>
	 *            The type of the collection.
	 * @param coll
	 *            The collection to add the deserialized objects to.
	 * @param in
	 *            The object input.
	 * @param elementsupplier
	 *            The supplier which instantiates the externalizable elements.
	 * @return The argument collection, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, the supplier, or the element returned by the supplier is <code>null</code>.
	 */
	public static <E extends Externalizable, C extends Collection<E>> C readExternalExternalizableCollection(C coll,
			ObjectInput in, Supplier<? extends E> elementsupplier)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		Objects.requireNonNull(coll, "collection");
		if (size > 0) {
			Objects.requireNonNull(elementsupplier, "element supplier");
			while (size-- > 0) {
				E item = elementsupplier.get();
				Objects.requireNonNull(item, "externalizable item");
				item.readExternal(in);
				coll.add(item);
			}
		}
		return coll;
	}

	/**
	 * Reads a collection of string elements from the object input.
	 * <p>
	 * Format identifier: <code>collection-utf</code>
	 * 
	 * @param <C>
	 *            The type of the collection.
	 * @param coll
	 *            The collection to add the deserialized objects to.
	 * @param in
	 *            The object input.
	 * @return The argument collection, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static <C extends Collection<? super String>> C readExternalUTFCollection(C coll, DataInput in)
			throws IOException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		Objects.requireNonNull(coll, "collection");
		while (size-- > 0) {
			coll.add(in.readUTF());
		}
		return coll;
	}

	/**
	 * Reads an {@link EnumSet} from the object input.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * 
	 * @param <E>
	 *            The element enum type.
	 * @param enumclass
	 *            The type of the element enums.
	 * @param in
	 *            The object input.
	 * @return An enum set, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws ClassCastException
	 *             If a deserialized object is not an instance of the enum type.
	 * @throws NullPointerException
	 *             If the input, or any of the deserialized elements are <code>null</code>.
	 */
	public static <E extends Enum<E>> EnumSet<E> readExternalEnumSetCollection(Class<E> enumclass, ObjectInput in)
			throws IOException, ClassNotFoundException, ClassCastException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		EnumSet<E> coll = EnumSet.noneOf(enumclass);
		while (size-- > 0) {
			@SuppressWarnings("unchecked")
			E elem = (E) in.readObject();
			Objects.requireNonNull(elem, "enumset element");
			coll.add(elem);
		}
		return coll;
	}

	/**
	 * Reads a {@link HashSet} from the object input.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * <p>
	 * The elements of the returned hash set is uncheckedly casted to the result element type.
	 * <p>
	 * Similar to:
	 * 
	 * <pre>
	 * readExternalCollection(new HashSet&lt;&gt;(), in);
	 * </pre>
	 * 
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @return A hash set, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static <E> HashSet<E> readExternalHashSet(ObjectInput in)
			throws ClassNotFoundException, IOException, NullPointerException {
		//in NPE tested in forwarded function
		//0.75 load factor, appropriately sized capacity
		return readExternalCollectionSize(in, s -> new HashSet<>(s * 4 / 3 + 1));
	}

	/**
	 * Reads a {@link LinkedHashSet} from the object input.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * <p>
	 * The elements of the returned linked hash set is uncheckedly casted to the result element type.
	 * <p>
	 * Similar to:
	 * 
	 * <pre>
	 * readExternalCollection(new LinkedHashSet&lt;&gt;(), in);
	 * </pre>
	 * 
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @return A linked hash set, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static <E> LinkedHashSet<E> readExternalLinkedHashSet(ObjectInput in)
			throws ClassNotFoundException, IOException, NullPointerException {
		//in NPE tested in forwarded function
		//0.75 load factor, appropriately sized capacity
		return readExternalCollectionSize(in, s -> new LinkedHashSet<>(s * 4 / 3 + 1));
	}

	/**
	 * Reads a {@link ArrayList} from the object input.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * <p>
	 * The elements of the returned array list is uncheckedly casted to the result element type.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * readExternalCollectionSize(in, ArrayList::new);
	 * </pre>
	 * 
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @return An array list, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static <E> ArrayList<E> readExternalArrayList(ObjectInput in)
			throws ClassNotFoundException, IOException, NullPointerException {
		//in NPE tested in forwarded function
		return readExternalCollectionSize(in, ArrayList::new);
	}

	/**
	 * Reads an array from the object input.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * 
	 * @param <E>
	 *            The element type.
	 * @param in
	 *            The object input.
	 * @param arraycreator
	 *            The function to create the result array for the requested size.
	 * @return An array, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, the array creator, or the created array is <code>null</code>.
	 * @throws ArrayStoreException
	 *             If a deserialized object cannot be stored in the created array.
	 */
	@SuppressWarnings("unchecked")
	public static <E> E[] readExternalArray(ObjectInput in, IntFunction<? extends E[]> arraycreator)
			throws IOException, ClassNotFoundException, NullPointerException, ArrayStoreException {
		Objects.requireNonNull(in, "in");
		int len = in.readInt();
		if (len < 0) {
			return null;
		}
		Objects.requireNonNull(arraycreator, "array creator");
		E[] result = arraycreator.apply(len);
		Objects.requireNonNull(result, "array");
		for (int i = 0; i < len; i++) {
			result[i] = (E) in.readObject();
		}
		return result;
	}

	/**
	 * Reads an array from the object input using a custom deserializer for its elements.
	 * <p>
	 * Format identifier: <code>collection-object</code>
	 * 
	 * @param <E>
	 *            The element type.
	 * @param <IN>
	 *            The type of the object input.
	 * @param in
	 *            The object input.
	 * @param arraycreator
	 *            The function to create the result array for the requested size.
	 * @param reader
	 *            The custom deserializer for each element object.
	 * @return An array, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, the array creator, the created array, or the element reader is <code>null</code>.
	 */
	public static <E, IN extends DataInput> E[] readExternalArray(IN in, IntFunction<? extends E[]> arraycreator,
			ObjectReaderFunction<? super IN, ? extends E> reader)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int len = in.readInt();
		if (len < 0) {
			return null;
		}
		Objects.requireNonNull(arraycreator, "array creator");
		E[] result = arraycreator.apply(len);
		Objects.requireNonNull(result, "created array");
		if (len > 0) {
			Objects.requireNonNull(reader, "reader");
			for (int i = 0; i < len; i++) {
				result[i] = reader.apply(in);
			}
		}
		return result;
	}

	/**
	 * Reads a byte array from the object input.
	 * <p>
	 * Format identifier: <code>collection-byte</code>
	 * 
	 * @param in
	 *            The object input.
	 * @return An array, or <code>null</code> if a <code>null</code> collection was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static byte[] readExternalByteArray(ObjectInput in) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int len = in.readInt();
		if (len < 0) {
			return null;
		}
		byte[] result = new byte[len];
		in.readFully(result);
		return result;
	}

	/**
	 * Writes a map to the object output.
	 * <p>
	 * Format identifier: <code>map-object-object</code>
	 * 
	 * @param out
	 *            The object output.
	 * @param map
	 *            The map to write.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output, the map entry set, or any of the entries are <code>null</code>.
	 * @throws SerializationConcurrentModificationException
	 *             If the size of the map doesn't match the numer of entries encountered in it.
	 */
	public static void writeExternalMap(ObjectOutput out, Map<?, ?> map)
			throws IOException, NullPointerException, SerializationConcurrentModificationException {
		Objects.requireNonNull(out, "out");
		if (map == null) {
			out.writeInt(-1);
			return;
		}
		int size = map.size();
		out.writeInt(size);
		if (size > 0) {
			Iterator<? extends Entry<?, ?>> it = Objects.requireNonNull(map.entrySet(), "map entry set").iterator();
			for (; it.hasNext();) {
				Entry<?, ?> entry = it.next();
				Objects.requireNonNull(entry, "map entry");

				out.writeObject(entry.getKey());
				out.writeObject(entry.getValue());
				--size;
			}
			checkCollectionConcurrentModificationsSize(size, it);
		}
	}

	/**
	 * Writes a map to the object output using the custom key and value serializers.
	 * <p>
	 * Format identifier: <code>map-custom-custom</code>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param <OUT>
	 *            The type of the object output.
	 * @param out
	 *            The object output.
	 * @param map
	 *            The map to write.
	 * @param keywriter
	 *            The custom serializer for each key object.
	 * @param valuewriter
	 *            The custom serializer for each value object.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output, the map entry set, any of the entries, the key writer, or the value writer is
	 *             <code>null</code>.
	 * @throws SerializationConcurrentModificationException
	 *             If the size of the map doesn't match the numer of entries encountered in it.
	 */
	public static <K, V, OUT extends DataOutput> void writeExternalMap(OUT out, Map<? extends K, ? extends V> map,
			ObjectWriterFunction<? super OUT, ? super K> keywriter,
			ObjectWriterFunction<? super OUT, ? super V> valuewriter)
			throws IOException, NullPointerException, SerializationConcurrentModificationException {
		Objects.requireNonNull(out, "out");
		if (map == null) {
			out.writeInt(-1);
			return;
		}
		int size = map.size();
		out.writeInt(size);
		if (size > 0) {
			Objects.requireNonNull(keywriter, "key writer");
			Objects.requireNonNull(valuewriter, "value writer");
			Iterator<? extends Entry<? extends K, ? extends V>> it = Objects
					.requireNonNull(map.entrySet(), "map entry set").iterator();
			for (; it.hasNext();) {
				Entry<? extends K, ? extends V> entry = it.next();
				Objects.requireNonNull(entry, "map entry");

				keywriter.apply(out, entry.getKey());
				valuewriter.apply(out, entry.getValue());
				--size;
			}
			checkCollectionConcurrentModificationsSize(size, it);
		}
	}

	/**
	 * Writes a map containing {@link Externalizable} keys and values to the object output.
	 * <p>
	 * Format identifier: <code>map-externalizable-externalizable</code>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param out
	 *            The object output.
	 * @param map
	 *            The map to write.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output, the map entry set, any of the entries, any of the keys, or any of the values are
	 *             <code>null</code>.
	 * @throws SerializationConcurrentModificationException
	 *             If the size of the map doesn't match the numer of entries encountered in it.
	 */
	public static <K extends Externalizable, V extends Externalizable> void writeExternalExternalizableMap(
			ObjectOutput out, Map<K, V> map)
			throws IOException, NullPointerException, SerializationConcurrentModificationException {
		Objects.requireNonNull(out, "out");
		if (map == null) {
			out.writeInt(-1);
			return;
		}
		int size = map.size();
		out.writeInt(size);
		if (size > 0) {
			Iterator<? extends Entry<K, V>> it = Objects.requireNonNull(map.entrySet(), "map entry set").iterator();
			for (; it.hasNext();) {
				Entry<K, V> entry = it.next();
				Objects.requireNonNull(entry, "map entry");
				K k = entry.getKey();
				Objects.requireNonNull(k, "entry key");
				k.writeExternal(out);

				V v = entry.getValue();
				Objects.requireNonNull(v, "entry value");
				v.writeExternal(out);
				--size;
			}
			checkCollectionConcurrentModificationsSize(size, it);
		}
	}

	/**
	 * Writes a map containing {@link Externalizable} keys to the object output.
	 * <p>
	 * Format identifier: <code>map-externalizable-object</code>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param out
	 *            The object output.
	 * @param map
	 *            The map to write.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output, the map entry set, any of the entries, or any of the keys are <code>null</code>.
	 * @throws SerializationConcurrentModificationException
	 *             If the size of the map doesn't match the numer of entries encountered in it.
	 */
	public static <K extends Externalizable> void writeExternalKeyExternalizableMap(ObjectOutput out, Map<K, ?> map)
			throws IOException, NullPointerException, SerializationConcurrentModificationException {
		Objects.requireNonNull(out, "out");
		if (map == null) {
			out.writeInt(-1);
			return;
		}
		int size = map.size();
		out.writeInt(size);
		if (size > 0) {
			Iterator<? extends Entry<K, ?>> it = Objects.requireNonNull(map.entrySet(), "map entry set").iterator();
			for (; it.hasNext();) {
				Entry<K, ?> entry = it.next();
				Objects.requireNonNull(entry, "map entry");
				K k = entry.getKey();
				Objects.requireNonNull(k, "entry key");
				k.writeExternal(out);
				out.writeObject(entry.getValue());
				--size;
			}
			checkCollectionConcurrentModificationsSize(size, it);
		}
	}

	/**
	 * Writes a map containing {@link Externalizable} values to the object output.
	 * <p>
	 * Format identifier: <code>map-object-externalizable</code>
	 * 
	 * @param <V>
	 *            The value type.
	 * @param out
	 *            The object output.
	 * @param map
	 *            The map to write.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the output, the map entry set, any of the entries, or any of the values are <code>null</code>.
	 * @throws SerializationConcurrentModificationException
	 *             If the size of the map doesn't match the numer of entries encountered in it.
	 */
	public static <V extends Externalizable> void writeExternalValueExternalizableMap(ObjectOutput out, Map<?, V> map)
			throws IOException, NullPointerException, SerializationConcurrentModificationException {
		Objects.requireNonNull(out, "out");
		if (map == null) {
			out.writeInt(-1);
			return;
		}
		int size = map.size();
		out.writeInt(size);
		if (size > 0) {
			Iterator<? extends Entry<?, V>> it = Objects.requireNonNull(map.entrySet(), "map entry set").iterator();
			for (; it.hasNext();) {
				Entry<?, V> entry = it.next();
				Objects.requireNonNull(entry, "map entry");
				out.writeObject(entry.getKey());
				V v = entry.getValue();
				Objects.requireNonNull(v, "entry value");
				v.writeExternal(out);
				--size;
			}
			checkCollectionConcurrentModificationsSize(size, it);
		}
	}

	/**
	 * Reads a map from the object input and puts its entries to the argument map.
	 * <p>
	 * Format identifier: <code>map-object-object</code>
	 * <p>
	 * The key and value types of the deserialized entries are uncheckedly casted.
	 * 
	 * @param <M>
	 *            The type of the map.
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map to add the deserialized entries to.
	 * @param in
	 *            The object input.
	 * @return The argument map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input or the map is <code>null</code>.
	 */
	public static <M extends Map<K, V>, K, V> M readExternalMap(M map, ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		Objects.requireNonNull(map, "map");
		readExternalMapEntries(map, in, size);
		return map;
	}

	/**
	 * Reads a map from the object input, that is instantiated by the argument supplier.
	 * <p>
	 * Format identifier: <code>map-object-object</code>
	 * <p>
	 * The argument map supplier can be used to construct an appropriate map for a given number of entries. <br>
	 * Example use-case:
	 * 
	 * <pre>
	 * readExternalMapSize(size -&gt; new {@link HashMap}&lt;&gt;(size * 4 / 3 + 1));
	 * </pre>
	 * <p>
	 * The key and value types of the deserialized entries are uncheckedly casted.
	 * 
	 * @param <M>
	 *            The type of the map.
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param in
	 *            The object input.
	 * @param mapsupplier
	 *            The map supplier for the count of elements to be deserialized.
	 * @return The deserialized map. This is either <code>null</code> or the same map returned by the
	 *             supplier.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, the map supplier, or the created map is <code>null</code>.
	 */
	public static <M extends Map<K, V>, K, V> M readExternalMapSize(ObjectInput in,
			IntFunction<? extends M> mapsupplier) throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		Objects.requireNonNull(mapsupplier, "map supplier");
		M map = mapsupplier.apply(size);
		Objects.requireNonNull(map, "map");
		readExternalMapEntries(map, in, size);
		return map;
	}

	/**
	 * Reads a map from the object input using a custom deserializer for its keys and values, and adds its entries to
	 * the argument map.
	 * <p>
	 * Format identifier: <code>map-custom-custom</code>
	 * <p>
	 * The caller is responsible for passing the reader functions that reads the objects in the same format as it was
	 * written out.
	 * 
	 * @param <M>
	 *            The type of the map.
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param <IN>
	 *            The type of the object input.
	 * @param map
	 *            The map to add the deserialized entries to.
	 * @param in
	 *            The object input.
	 * @param keyreader
	 *            The custom deserializer for each key object.
	 * @param valuereader
	 *            The custom deserializer for each value object.
	 * @return The argument map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, the map, the key reader, or the value reader is <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <M extends Map<K, V>, K, V, IN extends DataInput> M readExternalMap(M map, IN in,
			ObjectReaderFunction<? super IN, ? extends K> keyreader,
			ObjectReaderFunction<? super IN, ? extends V> valuereader)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size > 0) {
			Objects.requireNonNull(keyreader, "key reader");
			Objects.requireNonNull(valuereader, "value reader");
			while (size-- > 0) {
				K k = keyreader.apply(in);
				@SuppressWarnings("unchecked")
				V v = valuereader.apply(in);

				map.put(k, v);
			}
		}
		return map;
	}

	/**
	 * Reads a map containing {@link Externalizable} keys and values from the object input.
	 * <p>
	 * Format identifier: <code>map-externalizable-externalizable</code>
	 * 
	 * @param <M>
	 *            The type of the map.
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map to add the deserialized entries to.
	 * @param in
	 *            The object input.
	 * @param keysupplier
	 *            The supplier which instantiates the externalizable keys.
	 * @param valuesupplier
	 *            The supplier which instantiates the externalizable values.
	 * @return The argument map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, the key supplier, the value supplier, the map, the created keys, or the crated values
	 *             are <code>null</code>.
	 */
	public static <M extends Map<K, V>, K extends Externalizable, V extends Externalizable> M readExternalExternalizableMap(
			M map, ObjectInput in, Supplier<? extends K> keysupplier, Supplier<? extends V> valuesupplier)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size > 0) {
			Objects.requireNonNull(map, "map");
			Objects.requireNonNull(keysupplier, "key supplier");
			Objects.requireNonNull(valuesupplier, "value supplier");
			while (size-- > 0) {
				K k = keysupplier.get();
				Objects.requireNonNull(k, "externalizable key");
				k.readExternal(in);
				V v = valuesupplier.get();
				Objects.requireNonNull(v, "externalizable value");
				v.readExternal(in);

				map.put(k, v);
			}
		}
		return map;
	}

	/**
	 * Reads a map containing {@link Externalizable} keys from the object input.
	 * <p>
	 * Format identifier: <code>map-externalizable-object</code>
	 * <p>
	 * The key types of the deserialized entries are uncheckedly casted.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map to add the deserialized entries to.
	 * @param in
	 *            The object input.
	 * @param keysupplier
	 *            The supplier which instantiates the externalizable keys.
	 * @return The argument map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, the key supplier, the map, or the created keys are <code>null</code>.
	 */
	public static <K extends Externalizable, V> Map<K, V> readExternalKeyExternalizableMap(Map<K, V> map,
			ObjectInput in, Supplier<? extends K> keysupplier)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size > 0) {
			Objects.requireNonNull(map, "map");
			Objects.requireNonNull(keysupplier, "key supplier");
			while (size-- > 0) {
				K k = keysupplier.get();
				Objects.requireNonNull(k, "externalizable key");
				k.readExternal(in);
				@SuppressWarnings("unchecked")
				V v = (V) in.readObject();

				map.put(k, v);
			}
		}
		return map;
	}

	/**
	 * Reads a map containing {@link Externalizable} values from the object input.
	 * <p>
	 * Format identifier: <code>map-object-externalizable</code>
	 * <p>
	 * The value types of the deserialized entries are uncheckedly casted.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param map
	 *            The map to add the deserialized entries to.
	 * @param in
	 *            The object input.
	 * @param valuesupplier
	 *            The supplier which instantiates the externalizable values.
	 * @return The argument map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, the value supplier, the map, or the created values are <code>null</code>.
	 */
	public static <K, V extends Externalizable> Map<K, V> readExternalValueExternalizableMap(Map<K, V> map,
			ObjectInput in, Supplier<? extends V> valuesupplier)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size > 0) {
			Objects.requireNonNull(map, "map");
			Objects.requireNonNull(valuesupplier, "value supplier");
			while (size-- > 0) {
				@SuppressWarnings("unchecked")
				K k = (K) in.readObject();
				V v = valuesupplier.get();
				Objects.requireNonNull(v, "externalizable value");
				v.readExternal(in);

				map.put(k, v);
			}
		}
		return map;
	}

	/**
	 * Reads a {@link HashMap} from the object input.
	 * <p>
	 * Format identifier: <code>map-object-object</code>
	 * <p>
	 * The key and value types of the deserialized entries are uncheckedly casted.
	 * <p>
	 * Similar to:
	 * 
	 * <pre>
	 * readExternalMap(new HashMap&lt;&gt;(), in);
	 * </pre>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param in
	 *            The object input.
	 * @return A hash map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static <K, V> HashMap<K, V> readExternalHashMap(ObjectInput in)
			throws ClassNotFoundException, IOException, NullPointerException {
		//in NPE tested in forwarded function
		//0.75 load factor, appropriately sized capacity
		return readExternalMapSize(in, s -> new HashMap<>(s * 4 / 3 + 1));
	}

	/**
	 * Reads a {@link LinkedHashMap} from the object input.
	 * <p>
	 * Format identifier: <code>map-object-object</code>
	 * <p>
	 * The key and value types of the deserialized entries are uncheckedly casted.
	 * <p>
	 * Similar to:
	 * 
	 * <pre>
	 * readExternalMap(new LinkedHashMap&lt;&gt;(), in);
	 * </pre>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param in
	 *            The object input.
	 * @return A linked hash map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static <K, V> LinkedHashMap<K, V> readExternalLinkedHashMap(ObjectInput in)
			throws ClassNotFoundException, IOException, NullPointerException {
		//in NPE tested in forwarded function
		//0.75 load factor, appropriately sized capacity
		return readExternalMapSize(in, s -> new LinkedHashMap<>(s * 4 / 3 + 1));
	}

	/**
	 * Reads an immutable hash map from the object input.
	 * <p>
	 * Format identifier: <code>map-object-object</code>
	 * <p>
	 * The key and value types of the deserialized entries are uncheckedly casted.
	 * <p>
	 * The iteration order in the returned map might not be the same as order of the deserialized entries.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param in
	 *            The object input.
	 * @return An immutable hash map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 * @see HashMap
	 */
	public static <K, V> Map<K, V> readExternalImmutableHashMap(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return Collections.emptyMap();
		}
		if (size == 1) {
			@SuppressWarnings("unchecked")
			K k = (K) in.readObject();
			@SuppressWarnings("unchecked")
			V v = (V) in.readObject();
			return ImmutableUtils.singletonMap(k, v);
		}
		//XXX maybe implement a more efficient immutable hash map
		Map<K, V> map = new HashMap<>(size * 4 / 3 + 1, 0.75f);
		readExternalMapEntries(map, in, size);
		return ImmutableUtils.unmodifiableMap(map);
	}

	/**
	 * Reads an immutable linked hash map from the object input.
	 * <p>
	 * Format identifier: <code>map-object-object</code>
	 * <p>
	 * The key and value types of the deserialized entries are uncheckedly casted.
	 * <p>
	 * The iteration order in the returned map is the same as order of the deserialized entries.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param in
	 *            The object input.
	 * @return An immutable linked hash map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 * @see LinkedHashMap
	 */
	public static <K, V> Map<K, V> readExternalImmutableLinkedHashMap(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return Collections.emptyMap();
		}
		if (size == 1) {
			@SuppressWarnings("unchecked")
			K k = (K) in.readObject();
			@SuppressWarnings("unchecked")
			V v = (V) in.readObject();
			return ImmutableUtils.singletonMap(k, v);
		}
		//XXX maybe implement a more efficient immutable linked hash map
		Map<K, V> map = new LinkedHashMap<>(size * 4 / 3 + 1, 0.75f);
		readExternalMapEntries(map, in, size);
		return ImmutableUtils.unmodifiableMap(map);
	}

	/**
	 * Reads an immutable identity hash map from the object input.
	 * <p>
	 * Format identifier: <code>map-object-object</code>
	 * <p>
	 * The key and value types of the deserialized entries are uncheckedly casted.
	 * <p>
	 * The iteration order in the returned map might not be the same as order of the deserialized entries.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param in
	 *            The object input.
	 * @return An immutable identity hash map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 * @see IdentityHashMap
	 */
	public static <K, V> Map<K, V> readExternalImmutableIdentityHashMap(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return Collections.emptyMap();
		}
		if (size == 1) {
			@SuppressWarnings("unchecked")
			K k = (K) in.readObject();
			@SuppressWarnings("unchecked")
			V v = (V) in.readObject();
			return ImmutableUtils.singletonIdentityHashMap(k, v);
		}
		//XXX maybe implement a more efficient immutable identity hash map
		Map<K, V> map = new IdentityHashMap<>(size);
		readExternalMapEntries(map, in, size);
		return ImmutableUtils.unmodifiableMap(map);
	}

	/**
	 * Reads an immutable navigable map from the object input.
	 * <p>
	 * Format identifier: <code>map-object-object</code>
	 * <p>
	 * The key and value types of the deserialized entries are uncheckedly casted.
	 * <p>
	 * The returned map will be ordered by the natural order of the keys.
	 * <p>
	 * The entries in the stream are not required to be already in order.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param in
	 *            The object input.
	 * @return An immutable navigable map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> readExternalImmutableNavigableMap(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		return readExternalImmutableNavigableMap(in, null);
	}

	/**
	 * Reads an immutable navigable map from the object input.
	 * <p>
	 * Format identifier: <code>map-object-object</code>
	 * <p>
	 * The key and value types of the deserialized entries are uncheckedly casted.
	 * <p>
	 * The returned map will be ordered using the argument comparator.
	 * <p>
	 * The entries in the stream are not required to be already in order.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param in
	 *            The object input.
	 * @param comparator
	 *            The comparator that defines the order of the entries.
	 * @return An immutable navigable map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> readExternalImmutableNavigableMap(ObjectInput in,
			Comparator<? super K> comparator) throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return ImmutableUtils.emptyNavigableMap(comparator);
		}
		if (size == 1) {
			@SuppressWarnings("unchecked")
			K k = (K) in.readObject();
			@SuppressWarnings("unchecked")
			V v = (V) in.readObject();
			return ImmutableUtils.singletonNavigableMap(k, v, comparator);
		}
		//XXX maybe implement a more efficient immutable navigable map
		NavigableMap<K, V> map = new TreeMap<>(comparator);
		readExternalMapEntries(map, in, size);
		return ImmutableUtils.unmodifiableNavigableMap(map);
	}

	/**
	 * Reads a tree map from the object input given that the deserialized entries are already in order.
	 * <p>
	 * <b>Important:</b> This method relies on that the deserialized entries are ordered by their natural order. If this
	 * requirement is violated, the returned map will not work correctly.
	 * <p>
	 * Format identifier: <code>map-object-object</code>
	 * <p>
	 * The key and value types of the deserialized entries are uncheckedly casted.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * readExternalSortedTreeMap(in, null);
	 * </pre>
	 * 
	 * If you're unsure whether the serialized entries are in appropriate order, use:
	 * 
	 * <pre>
	 * readExternalMap(new TreeMap&lt;&gt;(), in);
	 * </pre>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param in
	 *            The object input.
	 * @return A tree map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static <K, V> TreeMap<K, V> readExternalSortedTreeMap(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		//in NPE is tested in forwarded function
		return readExternalSortedTreeMap(in, null);
	}

	/**
	 * Reads a tree map from the object input given that the deserialized entries are already in order for the argument
	 * comparator.
	 * <p>
	 * <b>Important:</b> This method relies on that the deserialized entries are ordered by the specified comparator. If
	 * this requirement is violated, the returned map will not work correctly.
	 * <p>
	 * Format identifier: <code>map-object-object</code>
	 * <p>
	 * The key and value types of the deserialized entries are uncheckedly casted.
	 * <p>
	 * If you're unsure whether the serialized entries are in appropriate order, use:
	 * 
	 * <pre>
	 * readExternalMap(new TreeMap&lt;&gt;(comparator), in);
	 * </pre>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param in
	 *            The object input.
	 * @param comparator
	 *            The comparator which defines the order of the serialized entries and the returned map.
	 *            <code>null</code> means the natural order.
	 * @return A tree map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static <K, V> TreeMap<K, V> readExternalSortedTreeMap(ObjectInput in, Comparator<? super K> comparator)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return new TreeMap<>(comparator);
		}
		return ObjectUtils.createTreeMapFromSortedIterator(new ObjectReadingEntryIterator<>(in, size), size);
	}

	/**
	 * Reads a tree map from the object input using a custom deserializer for its keys and values, given that the
	 * deserialized entries are already in order.
	 * <p>
	 * <b>Important:</b> This method relies on that the deserialized entries are ordered by their natural order. If this
	 * requirement is violated, the returned map will not work correctly.
	 * <p>
	 * Format identifier: <code>map-custom-custom</code>
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * readExternalSortedTreeMap(in, null, keyreader, valuereader);
	 * </pre>
	 * <p>
	 * If you're unsure whether the serialized entries are in appropriate order, use:
	 * 
	 * <pre>
	 * readExternalMap(new TreeMap&lt;&gt;(), in, keyreader, valuereader);
	 * </pre>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param <IN>
	 *            The type of the object input.
	 * @param in
	 *            The object input.
	 * @param keyreader
	 *            The custom deserializer for each key object.
	 * @param valuereader
	 *            The custom deserializer for each value object.
	 * @return A tree map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, the key reader, or the value reader is <code>null</code>.
	 */
	public static <K, V, IN extends DataInput> TreeMap<K, V> readExternalSortedTreeMap(IN in,
			ObjectReaderFunction<? super IN, ? extends K> keyreader,
			ObjectReaderFunction<? super IN, ? extends V> valuereader)
			throws IOException, ClassNotFoundException, NullPointerException {
		//in NPE is tested in forwarded function
		return readExternalSortedTreeMap(in, null, keyreader, valuereader);
	}

	/**
	 * Reads a tree map map from the object input using a custom deserializer for its keys and values, given that the
	 * deserialized entries are already in order for the argument comparator.
	 * <p>
	 * <b>Important:</b> This method relies on that the deserialized entries are ordered by the specified comparator. If
	 * this requirement is violated, the returned map will not work correctly.
	 * <p>
	 * Format identifier: <code>map-custom-custom</code>
	 * <p>
	 * If you're unsure whether the serialized entries are in appropriate order, use:
	 * 
	 * <pre>
	 * readExternalMap(new TreeMap&lt;&gt;(comparator), in, keyreader, valuereader);
	 * </pre>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param <IN>
	 *            The type of the object input.
	 * @param in
	 *            The object input.
	 * @param comparator
	 *            The comparator which defines the order of the serialized entries and the returned map.
	 *            <code>null</code> means the natural order.
	 * @param keyreader
	 *            The custom deserializer for each key object.
	 * @param valuereader
	 *            The custom deserializer for each value object.
	 * @return A tree map map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, the key reader, or the value reader is <code>null</code>.
	 */
	public static <K, V, IN extends DataInput> TreeMap<K, V> readExternalSortedTreeMap(IN in,
			Comparator<? super K> comparator, ObjectReaderFunction<? super IN, ? extends K> keyreader,
			ObjectReaderFunction<? super IN, ? extends V> valuereader)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return new TreeMap<>(comparator);
		}
		Objects.requireNonNull(keyreader, "key reader");
		Objects.requireNonNull(valuereader, "value reader");
		return ObjectUtils.createTreeMapFromSortedIterator(
				new FunctionedObjectReadingEntryIterator<>(in, size, keyreader, valuereader), size, comparator);
	}

	/**
	 * Reads an immutable navigable map from the object input given that the deserialized entries are already in order.
	 * <p>
	 * <b>Important:</b> This method relies on that the deserialized entries are ordered by their natural order. If this
	 * requirement is violated, the returned map will not work correctly.
	 * <p>
	 * Format identifier: <code>map-object-object</code>
	 * <p>
	 * The key and value types of the deserialized entries are uncheckedly casted.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * readExternalSortedImmutableNavigableMap(in, null);
	 * </pre>
	 * 
	 * If you're unsure whether the serialized entries are in appropriate order, use:
	 * 
	 * <pre>
	 * ImmutableUtils.unmodifiableNavigableMap(readExternalMap(new TreeMap&lt;&gt;(), in));
	 * </pre>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param in
	 *            The object input.
	 * @return An immutable navigable map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static <K, V> NavigableMap<K, V> readExternalSortedImmutableNavigableMap(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		//in NPE is tested in forwarded function
		return readExternalSortedImmutableNavigableMap(in, null);
	}

	/**
	 * Reads an immutable navigable map from the object input given that the deserialized entries are already in order
	 * for the argument comparator.
	 * <p>
	 * <b>Important:</b> This method relies on that the deserialized entries are ordered by the specified comparator. If
	 * this requirement is violated, the returned map will not work correctly.
	 * <p>
	 * Format identifier: <code>map-object-object</code>
	 * <p>
	 * The key and value types of the deserialized entries are uncheckedly casted.
	 * <p>
	 * If you're unsure whether the serialized entries are in appropriate order, use:
	 * 
	 * <pre>
	 * ImmutableUtils.unmodifiableNavigableMap(readExternalMap(new TreeMap&lt;&gt;(comparator), in));
	 * </pre>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param in
	 *            The object input.
	 * @param comparator
	 *            The comparator which defines the order of the serialized entries and the returned map.
	 *            <code>null</code> means the natural order.
	 * @return An immutable navigable map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <K, V> NavigableMap<K, V> readExternalSortedImmutableNavigableMap(ObjectInput in,
			Comparator<? super K> comparator) throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return ImmutableUtils.emptyNavigableMap(comparator);
		}
		if (size == 1) {
			K k = (K) in.readObject();
			V v = (V) in.readObject();
			return ImmutableUtils.singletonNavigableMap(k, v, comparator);
		}
		//XXX do interlaced, or entry based result
		Object[] keys = new Object[size];
		Object[] values = new Object[size];
		for (int i = 0; i < size; i++) {
			keys[i] = in.readObject();
			values[i] = in.readObject();
		}
		return ImmutableUtils.unmodifiableNavigableMap((K[]) keys, (V[]) values, comparator);
	}

	/**
	 * Reads an immutable navigable map from the object input using a custom deserializer for its keys and values, given
	 * that the deserialized entries are already in order.
	 * <p>
	 * <b>Important:</b> This method relies on that the deserialized entries are ordered by their natural order. If this
	 * requirement is violated, the returned map will not work correctly.
	 * <p>
	 * Format identifier: <code>map-custom-custom</code>
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * readExternalSortedImmutableNavigableMap(in, null, keyreader, valuereader);
	 * </pre>
	 * <p>
	 * If you're unsure whether the serialized entries are in appropriate order, use:
	 * 
	 * <pre>
	 * ImmutableUtils.unmodifiableNavigableMap(readExternalMap(new TreeMap&lt;&gt;(), in, keyreader, valuereader));
	 * </pre>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param <IN>
	 *            The type of the object input.
	 * @param in
	 *            The object input.
	 * @param keyreader
	 *            The custom deserializer for each key object.
	 * @param valuereader
	 *            The custom deserializer for each value object.
	 * @return An immutable navigable map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, the key reader, or the value reader is <code>null</code>.
	 */
	public static <K, V, IN extends DataInput> NavigableMap<K, V> readExternalSortedImmutableNavigableMap(IN in,
			ObjectReaderFunction<? super IN, ? extends K> keyreader,
			ObjectReaderFunction<? super IN, ? extends V> valuereader)
			throws IOException, ClassNotFoundException, NullPointerException {
		//in NPE is tested in forwarded function
		return readExternalSortedImmutableNavigableMap(in, null, keyreader, valuereader);
	}

	/**
	 * Reads an immutable navigable map from the object input using a custom deserializer for its keys and values, given
	 * that the deserialized entries are already in order for the argument comparator.
	 * <p>
	 * <b>Important:</b> This method relies on that the deserialized entries are ordered by the specified comparator. If
	 * this requirement is violated, the returned map will not work correctly.
	 * <p>
	 * Format identifier: <code>map-custom-custom</code>
	 * <p>
	 * If you're unsure whether the serialized entries are in appropriate order, use:
	 * 
	 * <pre>
	 * ImmutableUtils.unmodifiableNavigableMap(readExternalMap(new TreeMap&lt;&gt;(comparator), in, keyreader, valuereader));
	 * </pre>
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param <IN>
	 *            The type of the object input.
	 * @param in
	 *            The object input.
	 * @param comparator
	 *            The comparator which defines the order of the serialized entries and the returned map.
	 *            <code>null</code> means the natural order.
	 * @param keyreader
	 *            The custom deserializer for each key object.
	 * @param valuereader
	 *            The custom deserializer for each value object.
	 * @return An immutable navigable map, or <code>null</code> if a <code>null</code> map was deserialized.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input, the key reader, or the value reader is <code>null</code>.
	 */
	@SuppressWarnings({ "unchecked" })
	public static <K, V, IN extends DataInput> NavigableMap<K, V> readExternalSortedImmutableNavigableMap(IN in,
			Comparator<? super K> comparator, ObjectReaderFunction<? super IN, ? extends K> keyreader,
			ObjectReaderFunction<? super IN, ? extends V> valuereader)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		int size = in.readInt();
		if (size < 0) {
			return null;
		}
		if (size == 0) {
			return ImmutableUtils.emptyNavigableMap(comparator);
		}
		Objects.requireNonNull(keyreader, "key reader");
		Objects.requireNonNull(valuereader, "value reader");
		if (size == 1) {
			K k = keyreader.apply(in);
			V v = valuereader.apply(in);
			return ImmutableUtils.singletonNavigableMap(k, v, comparator);
		}
		//XXX do interlaced, or entry based result
		Object[] keys = new Object[size];
		Object[] values = new Object[size];
		for (int i = 0; i < size; i++) {
			keys[i] = keyreader.apply(in);
			values[i] = valuereader.apply(in);
		}
		return ImmutableUtils.unmodifiableNavigableMap((K[]) keys, (V[]) values, comparator);
	}

	/**
	 * Writes a stack trace element externally to the object output.
	 * <p>
	 * Format identifier: <code>stack_trace_element</code>
	 * <p>
	 * The format of an externalized stack trace element is the following:
	 * <ul>
	 * <li>{@linkplain StackTraceElement#getClassName() Class name}. (<code>utf</code>)</li>
	 * <li>{@linkplain StackTraceElement#getMethodName() Method name}. (<code>utf</code>)</li>
	 * <li>{@linkplain StackTraceElement#getFileName() File name}. (<code>object</code>)</li>
	 * <li>{@linkplain StackTraceElement#getLineNumber() Line number}. (<code>int</code>)</li>
	 * </ul>
	 * 
	 * @param out
	 *            The object output.
	 * @param elem
	 *            The stack trace element.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static void writeExternalStackTraceElement(ObjectOutput out, StackTraceElement elem)
			throws IOException, NullPointerException {
		Objects.requireNonNull(out, "out");
		Objects.requireNonNull(elem, "stack trace element");
		out.writeUTF(elem.getClassName());
		out.writeUTF(elem.getMethodName());
		out.writeObject(elem.getFileName());
		out.writeInt(elem.getLineNumber());
	}

	/**
	 * Reads an externalized stack trace element from the object input.
	 * <p>
	 * Format identifier: <code>stack_trace_element</code>
	 * <p>
	 * See {@link #writeExternalStackTraceElement(ObjectOutput, StackTraceElement)} for the exact internal format.
	 * 
	 * @param in
	 *            The object input.
	 * @return The deserialized stack trace element.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during serialization.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static StackTraceElement readExternalStackTraceElement(ObjectInput in)
			throws IOException, ClassNotFoundException, NullPointerException {
		Objects.requireNonNull(in, "in");
		String cname = in.readUTF();
		String methodname = in.readUTF();
		String filename = (String) in.readObject();
		int linenum = in.readInt();
		return new StackTraceElement(cname, methodname, filename, linenum);
	}

	/**
	 * Writes a <code>byte</code> to the argument buffer at the given offset.
	 * 
	 * @param v
	 *            The byte to write.
	 * @param buf
	 *            The <code>byte</code> buffer.
	 * @param offset
	 *            The offset to write the <code>byte</code> to.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the offset index is outside of the array.
	 */
	public static void writeByteToBuffer(byte v, byte[] buf, int offset)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(buf, "buffer");
		buf[offset] = v;
	}

	/**
	 * Writes a <code>short</code> to the argument buffer at the given offset.
	 * <p>
	 * The <code>short</code> is written in big-endian order.
	 * 
	 * @param v
	 *            The short to write.
	 * @param buf
	 *            The <code>byte</code> buffer.
	 * @param offset
	 *            The offset to write the <code>short</code> to.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the offset index, and the next 2 bytes are outside of the array.
	 */
	public static void writeShortToBuffer(short v, byte[] buf, int offset)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(buf, "buffer");
		buf[offset] = (byte) ((v >>> 8));
		buf[offset + 1] = (byte) ((v));
	}

	/**
	 * Writes a <code>int</code> to the argument buffer at the given offset.
	 * <p>
	 * The <code>int</code> is written in big-endian order.
	 * 
	 * @param v
	 *            The int to write.
	 * @param buf
	 *            The <code>byte</code> buffer.
	 * @param offset
	 *            The offset to write the <code>int</code> to.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the offset index, and the next 4 bytes are outside of the array.
	 */
	public static void writeIntToBuffer(int v, byte[] buf, int offset)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(buf, "buffer");
		buf[offset] = (byte) ((v >>> 24));
		buf[offset + 1] = (byte) ((v >>> 16));
		buf[offset + 2] = (byte) ((v >>> 8));
		buf[offset + 3] = (byte) ((v));
	}

	/**
	 * Writes a <code>long</code> to the argument buffer at the given offset.
	 * <p>
	 * The <code>long</code> is written in big-endian order.
	 * 
	 * @param v
	 *            The long to write.
	 * @param buf
	 *            The <code>byte</code> buffer.
	 * @param offset
	 *            The offset to write the <code>long</code> to.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the offset index, and the next 8 bytes are outside of the array.
	 */
	public static void writeLongToBuffer(long v, byte[] buf, int offset)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(buf, "buffer");
		buf[offset] = (byte) ((v >>> 56));
		buf[offset + 1] = (byte) ((v >>> 48));
		buf[offset + 2] = (byte) ((v >>> 40));
		buf[offset + 3] = (byte) ((v >>> 32));
		buf[offset + 4] = (byte) ((v >>> 24));
		buf[offset + 5] = (byte) ((v >>> 16));
		buf[offset + 6] = (byte) ((v >>> 8));
		buf[offset + 7] = (byte) ((v));
	}

	/**
	 * Writes a <code>char</code> to the argument buffer at the given offset.
	 * <p>
	 * The <code>char</code> is written in big-endian order.
	 * 
	 * @param v
	 *            The char to write.
	 * @param buf
	 *            The <code>byte</code> buffer.
	 * @param offset
	 *            The offset to write the <code>char</code> to.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the offset index, and the next 2 bytes are outside of the array.
	 */
	public static void writeCharToBuffer(char v, byte[] buf, int offset)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(buf, "buffer");
		buf[offset] = (byte) ((v >>> 8));
		buf[offset + 1] = (byte) ((v));
	}

	/**
	 * Reads a <code>byte</code> from the argument buffer at the given offset.
	 * 
	 * @param buf
	 *            The <code>byte</code> buffer.
	 * @param offset
	 *            The offset to read the <code>byte</code> from.
	 * @return The <code>byte</code> read from the given offset.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the offset index is outside of the array.
	 */
	public static byte readByteFromBuffer(byte[] buf, int offset)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(buf, "buffer");
		return buf[offset];
	}

	/**
	 * Reads a <code>short</code> from the argument buffer at the given offset.
	 * <p>
	 * The <code>short</code> is read in big-endian order.
	 * 
	 * @param buf
	 *            The <code>byte</code> buffer.
	 * @param offset
	 *            The offset to read the <code>short</code> from.
	 * @return The <code>short</code> read from the given offset.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the offset index and the next 2 bytes are outside of the array.
	 */
	public static short readShortFromBuffer(byte[] buf, int offset)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(buf, "buffer");
		short result = (short) (((buf[offset] & 0xFF) << 8) | (buf[offset + 1] & 0xFF));
		return result;
	}

	/**
	 * Reads a <code>int</code> from the argument buffer at the given offset.
	 * <p>
	 * The <code>int</code> is read in big-endian order.
	 * 
	 * @param buf
	 *            The <code>byte</code> buffer.
	 * @param offset
	 *            The offset to read the <code>itn</code> from.
	 * @return The <code>int</code> read from the given offset.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the offset index and the next 4 bytes are outside of the array.
	 */
	public static int readIntFromBuffer(byte[] buf, int offset) throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(buf, "buffer");
		int result = ((buf[offset]) << 24) | //
				((buf[offset + 1] & 0xFF) << 16) | //
				((buf[offset + 2] & 0xFF) << 8) | //
				(buf[offset + 3] & 0xFF);
		return result;
	}

	/**
	 * Reads a <code>long</code> from the argument buffer at the given offset.
	 * <p>
	 * The <code>long</code> is read in big-endian order.
	 * 
	 * @param buf
	 *            The <code>byte</code> buffer.
	 * @param offset
	 *            The offset to read the <code>long</code> from.
	 * @return The <code>long</code> read from the given offset.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the offset index and the next 8 bytes are outside of the array.
	 */
	public static long readLongFromBuffer(byte[] buf, int offset)
			throws NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(buf, "buffer");
		long result = (((long) buf[offset]) << 56) | //
				((long) (buf[offset + 1] & 0xFF) << 48) | //
				((long) (buf[offset + 2] & 0xFF) << 40) | //
				((long) (buf[offset + 3] & 0xFF) << 32) | //
				((long) (buf[offset + 4] & 0xFF) << 24) | //
				((long) (buf[offset + 5] & 0xFF) << 16) | //
				((long) (buf[offset + 6] & 0xFF) << 8) | //
				(buf[offset + 7] & 0xFF);
		return result;
	}

	/**
	 * Reads a <code>char</code> from the argument buffer at the given offset.
	 * <p>
	 * The <code>char</code> is read in big-endian order.
	 * 
	 * @param buf
	 *            The <code>byte</code> buffer.
	 * @param offset
	 *            The offset to read the <code>char</code> from.
	 * @return The <code>char</code> read from the given offset.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the offset index and the next 2 bytes are outside of the array.
	 */
	public static char readCharFromBuffer(byte[] buf, int offset)
			throws NullPointerException, IndexOutOfBoundsException {
		char result = (char) (((buf[offset] & 0xFF) << 8) | (buf[offset + 1] & 0xFF));
		return result;
	}

	private static <M extends Map<K, V>, V, K> void readExternalMapEntries(M map, ObjectInput in, int size)
			throws ClassNotFoundException, IOException, NullPointerException {
		while (size-- > 0) {
			@SuppressWarnings("unchecked")
			K k = (K) in.readObject();
			@SuppressWarnings("unchecked")
			V v = (V) in.readObject();

			map.put(k, v);
		}
	}

	private static void checkCollectionConcurrentModificationsSize(int size, Iterator<?> it)
			throws ConcurrentModificationException {
		if (size > 0) {
			throw new SerializationConcurrentModificationException(size);
		}
		if (it.hasNext()) {
			throw new SerializationConcurrentModificationException(-1);
		}
	}

	private enum CommonSentinel {
		NULL_INPUT,
		END_OF_OBJECTS;
	}

	private static class FunctionedObjectReadingEntryIterator<K, V, IN extends DataInput>
			implements Iterator<Entry<K, V>> {
		private final IN in;
		private int remaining;
		private final ObjectReaderFunction<? super IN, ? extends K> keyReader;
		private final ObjectReaderFunction<? super IN, ? extends V> valueReader;

		public FunctionedObjectReadingEntryIterator(IN in, int remaining,
				ObjectReaderFunction<? super IN, ? extends K> keyReader,
				ObjectReaderFunction<? super IN, ? extends V> valueReader) {
			this.in = in;
			this.remaining = remaining;
			this.keyReader = keyReader;
			this.valueReader = valueReader;
		}

		@Override
		public boolean hasNext() {
			return remaining > 0;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Entry<K, V> next() {
			try {
				K k = keyReader.apply(in);
				V v = valueReader.apply(in);
				return ImmutableUtils.makeImmutableMapEntry(k, v);
			} catch (ClassNotFoundException | IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
		}
	}

	private static class ObjectReadingIterator<T> implements Iterator<T> {
		private final ObjectInput in;
		private int remaining;

		public ObjectReadingIterator(int size, ObjectInput in) {
			this.remaining = size;
			this.in = in;
		}

		@Override
		public boolean hasNext() {
			return remaining > 0;
		}

		@SuppressWarnings("unchecked")
		@Override
		public T next() {
			try {
				return (T) in.readObject();
			} catch (ClassNotFoundException | IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
		}
	}

	private static class ObjectReadingEntryIterator<K, V> implements Iterator<Entry<K, V>> {
		private final ObjectInput in;
		private int remaining;

		public ObjectReadingEntryIterator(ObjectInput in, int remaining) {
			this.in = in;
			this.remaining = remaining;
		}

		@Override
		public boolean hasNext() {
			return remaining > 0;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Entry<K, V> next() {
			try {
				K k = (K) in.readObject();
				V v = (V) in.readObject();
				return ImmutableUtils.makeImmutableMapEntry(k, v);
			} catch (ClassNotFoundException | IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
		}
	}

}
