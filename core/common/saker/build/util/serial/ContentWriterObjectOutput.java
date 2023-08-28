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
package saker.build.util.serial;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.DataOutputUnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.function.IOBiConsumer;
import saker.build.thirdparty.saker.util.io.function.ObjectReaderFunction;
import saker.build.trace.InternalBuildTraceImpl;
import saker.build.util.data.annotation.ValueType;
import testing.saker.build.flag.TestFlag;

public class ContentWriterObjectOutput implements ObjectOutput {
	static final int C_BYTE = 1;
	static final int C_CHAR = C_BYTE + 1;

	//The base <code>C_SHORT_N</code> value, to which the number of bytes can be added to get the command value.
	//E.g.: C_SHORT_BASE + 2 == C_SHORT_2
	static final int C_SHORT_BASE = C_CHAR;
	static final int C_SHORT_1 = C_CHAR + 1;
	static final int C_SHORT_2 = C_SHORT_1 + 1;

	//The base <code>C_INT_N</code> value, to which the number of bytes can be added to get the command value.
	//E.g.: C_INT_BASE + 2 == C_INT_2
	static final int C_INT_BASE = C_SHORT_2;
	static final int C_INT_1 = C_INT_BASE + 1;
	static final int C_INT_2 = C_INT_BASE + 2;
	static final int C_INT_3 = C_INT_BASE + 3;
	static final int C_INT_4 = C_INT_BASE + 4;

	static final int C_INT_F_1 = C_INT_4 + 1;
	static final int C_INT_F_2 = C_INT_F_1 + 1;
	static final int C_INT_F_3 = C_INT_F_2 + 1;

	static final int C_INT_ZERO = C_INT_F_3 + 1;
	static final int C_INT_NEGATIVE_ONE = C_INT_ZERO + 1;
	static final int C_INT_ONE = C_INT_NEGATIVE_ONE + 1;

	//The base <code>C_LONG_N</code> value, to which the number of EVEN bytes can be added to get the command value.
	//E.g.: C_LONG_BASE + (4 / 2) == C_LONG_4
	//      to write a long as 4 bytes
	static final int C_LONG_BASE = C_INT_ONE;
	static final int C_LONG_2 = C_LONG_BASE + 1;
	static final int C_LONG_4 = C_LONG_BASE + 2;
	static final int C_LONG_6 = C_LONG_BASE + 3;
	static final int C_LONG_8 = C_LONG_BASE + 4;

	static final int C_LONG_F_2 = C_LONG_8 + 1;
	static final int C_LONG_F_4 = C_LONG_F_2 + 1;
	static final int C_LONG_F_6 = C_LONG_F_4 + 1;
	static final int C_LONG_ZERO = C_LONG_F_6 + 1;
	static final int C_LONG_NEGATIVE_ONE = C_LONG_ZERO + 1;

	static final int C_FLOAT = C_LONG_NEGATIVE_ONE + 1;
	static final int C_DOUBLE = C_FLOAT + 1;

	static final int C_BOOLEAN_TRUE = C_DOUBLE + 1;
	static final int C_BOOLEAN_FALSE = C_BOOLEAN_TRUE + 1;

	static final int C_UTF = C_BOOLEAN_FALSE + 1;
	//The base <code>C_UTF_IDX_N</code> value, to which the number of bytes can be added to get the command value.
	//E.g.: C_UTF_IDX_BASE + 2 == C_UTF_IDX_2
	static final int C_UTF_IDX_BASE = C_UTF;
	static final int C_UTF_IDX_1 = C_UTF_IDX_BASE + 1;
	static final int C_UTF_IDX_2 = C_UTF_IDX_BASE + 2;
	static final int C_UTF_IDX_3 = C_UTF_IDX_BASE + 3;
	static final int C_UTF_IDX_4 = C_UTF_IDX_BASE + 4;
	static final int C_UTF_LOWBYTES = C_UTF_IDX_4 + 1;
	static final int C_UTF_PREFIXED = C_UTF_LOWBYTES + 1;
	static final int C_UTF_PREFIXED_LOWBYTES = C_UTF_PREFIXED + 1;

	static final int C_BYTEARRAY = C_UTF_PREFIXED_LOWBYTES + 1;
	static final int C_CHARS = C_BYTEARRAY + 1;

	static final int C_OBJECT_CLASSLOADER = C_CHARS + 1;
	static final int C_OBJECT_ARRAY = C_OBJECT_CLASSLOADER + 1;
	//externalizable with 1 byte length value
	static final int C_OBJECT_EXTERNALIZABLE_1 = C_OBJECT_ARRAY + 1;
	//externalizable with 4 byte length value
	static final int C_OBJECT_EXTERNALIZABLE_4 = C_OBJECT_EXTERNALIZABLE_1 + 1;

	//The base <code>C_OBJECT_IDX_N</code> value, to which the number of bytes can be added to get the command value.
	//E.g.: C_OBJECT_IDX_BASE + 2 == C_OBJECT_IDX_2
	static final int C_OBJECT_IDX_BASE = C_OBJECT_EXTERNALIZABLE_4;
	static final int C_OBJECT_IDX_1 = C_OBJECT_IDX_BASE + 1;
	static final int C_OBJECT_IDX_2 = C_OBJECT_IDX_BASE + 2;
	static final int C_OBJECT_IDX_3 = C_OBJECT_IDX_BASE + 3;
	static final int C_OBJECT_IDX_4 = C_OBJECT_IDX_BASE + 4;
	static final int C_OBJECT_REL_1 = C_OBJECT_IDX_BASE + 5;
	static final int C_OBJECT_REL_2 = C_OBJECT_IDX_BASE + 6;

	static final int C_OBJECT_NULL = C_OBJECT_REL_2 + 1;
	static final int C_OBJECT_SERIALIZABLE = C_OBJECT_NULL + 1;
	static final int C_OBJECT_TYPE = C_OBJECT_SERIALIZABLE + 1;
	static final int C_OBJECT_VALUE = C_OBJECT_TYPE + 1;
	static final int C_OBJECT_ENUM = C_OBJECT_VALUE + 1;
	static final int C_OBJECT_ARRAY_ERROR = C_OBJECT_ENUM + 1;
	static final int C_OBJECT_EXTERNALIZABLE_ERROR = C_OBJECT_ARRAY_ERROR + 1;
	static final int C_OBJECT_SERIALIZABLE_ERROR = C_OBJECT_EXTERNALIZABLE_ERROR + 1;
	static final int C_OBJECT_CUSTOM_SERIALIZABLE = C_OBJECT_SERIALIZABLE_ERROR + 1;
	static final int C_OBJECT_CUSTOM_SERIALIZABLE_ERROR = C_OBJECT_CUSTOM_SERIALIZABLE + 1;

	static final int C_OBJECT_UTF = C_OBJECT_CUSTOM_SERIALIZABLE_ERROR + 1;
	//The base <code>C_OBJECT_UTF_IDX_N</code> value, to which the number of bytes can be added to get the command value.
	//E.g.: C_OBJECT_UTF_IDX_BASE + 2 == C_OBJECT_UTF_IDX_2
	static final int C_OBJECT_UTF_IDX_BASE = C_OBJECT_UTF;
	static final int C_OBJECT_UTF_IDX_1 = C_OBJECT_UTF_IDX_BASE + 1;
	static final int C_OBJECT_UTF_IDX_2 = C_OBJECT_UTF_IDX_BASE + 2;
	static final int C_OBJECT_UTF_IDX_3 = C_OBJECT_UTF_IDX_BASE + 3;
	static final int C_OBJECT_UTF_IDX_4 = C_OBJECT_UTF_IDX_BASE + 4;
	static final int C_OBJECT_UTF_LOWBYTES = C_OBJECT_UTF_IDX_4 + 1;
	static final int C_OBJECT_UTF_PREFIXED = C_OBJECT_UTF_LOWBYTES + 1;
	static final int C_OBJECT_UTF_PREFIXED_LOWBYTES = C_OBJECT_UTF_PREFIXED + 1;

	static final int C_OBJECT_PROXY = C_OBJECT_UTF_PREFIXED_LOWBYTES + 1;

	static final int C_MAX_COMMAND_VALUE = 66;
	static {
		if (TestFlag.ENABLED) {
			//check that the last command value equals to the max command value constants
			if (C_MAX_COMMAND_VALUE != C_OBJECT_PROXY) {
				throw new AssertionError();
			}
		}
	}

	private static final int UTF_PREFIX_MIN_LEN = 8;
	private static final int MAX_RAWVARINT_SIZE = 5;

	static String getCommandTypeInfo(int cmd) {
		switch (cmd) {
			case C_BYTE:
			case C_BYTEARRAY:
				return "byte";
			case C_CHAR:
				return "char";
			case C_SHORT_1:
			case C_SHORT_2:
				return "short";
			case C_INT_4:
			case C_INT_3:
			case C_INT_2:
			case C_INT_1:
			case C_INT_F_3:
			case C_INT_F_2:
			case C_INT_F_1:
			case C_INT_ZERO:
			case C_INT_ONE:
			case C_INT_NEGATIVE_ONE:
				return "int";
			case C_LONG_8:
			case C_LONG_2:
			case C_LONG_4:
			case C_LONG_6:
			case C_LONG_F_2:
			case C_LONG_F_4:
			case C_LONG_F_6:
			case C_LONG_ZERO:
			case C_LONG_NEGATIVE_ONE:
				return "long";
			case C_FLOAT:
				return "float";
			case C_DOUBLE:
				return "double";
			case C_BOOLEAN_FALSE:
			case C_BOOLEAN_TRUE:
				return "boolean";
			case C_UTF:
			case C_UTF_IDX_4:
			case C_UTF_IDX_1:
			case C_UTF_IDX_2:
			case C_UTF_IDX_3:
			case C_UTF_LOWBYTES:
			case C_UTF_PREFIXED:
			case C_UTF_PREFIXED_LOWBYTES:
				return "UTF";
			case C_CHARS:
				return "char";
			case C_OBJECT_REL_2:
			case C_OBJECT_REL_1:
			case C_OBJECT_IDX_4:
			case C_OBJECT_IDX_3:
			case C_OBJECT_IDX_2:
			case C_OBJECT_IDX_1:
			case C_OBJECT_VALUE:
			case C_OBJECT_CUSTOM_SERIALIZABLE:
			case C_OBJECT_CUSTOM_SERIALIZABLE_ERROR:
				return "object";
			case C_OBJECT_UTF:
			case C_OBJECT_UTF_IDX_4:
			case C_OBJECT_UTF_IDX_1:
			case C_OBJECT_UTF_IDX_2:
			case C_OBJECT_UTF_IDX_3:
			case C_OBJECT_UTF_LOWBYTES:
			case C_OBJECT_UTF_PREFIXED:
			case C_OBJECT_UTF_PREFIXED_LOWBYTES:
				return "object (String)";
			case C_OBJECT_ARRAY:
			case C_OBJECT_ARRAY_ERROR:
				return "object (array)";
			case C_OBJECT_EXTERNALIZABLE_1:
			case C_OBJECT_EXTERNALIZABLE_4:
			case C_OBJECT_EXTERNALIZABLE_ERROR:
				return "object (Externalizable)";
			case C_OBJECT_NULL:
				return "object (null)";
			case C_OBJECT_SERIALIZABLE:
			case C_OBJECT_SERIALIZABLE_ERROR:
				return "object (Serializable)";
			case C_OBJECT_TYPE:
				return "object (class)";
			case C_OBJECT_ENUM:
				return "object (enum)";
			case C_OBJECT_CLASSLOADER:
				return "object (classloader)";
			case C_OBJECT_PROXY:
				return "object (proxy)";
			default: {
				return "<unknown>";
			}
		}
	}

	static final Map<Class<?>, ObjectReaderFunction<ContentReaderObjectInput, Object>> SERIALIZABLE_CLASS_READERS = new HashMap<>();
	static final Map<Class<?>, IOBiConsumer<?, ContentWriterObjectOutput>> SERIALIZABLE_CLASS_WRITERS = new HashMap<>();
	static {
		//XXX read the sorted collection in a sorted way
		SERIALIZABLE_CLASS_WRITERS.put(TreeSet.class, (TreeSet<?> v, ContentWriterObjectOutput writer) -> {
			writer.writeObject(v.comparator());
			SerialUtils.writeExternalCollection(writer, v);
		});
		SERIALIZABLE_CLASS_READERS.put(TreeSet.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				@SuppressWarnings("unchecked")
				Comparator<Object> comparator = (Comparator<Object>) reader.readObject();
				TreeSet<Object> res = new TreeSet<>(comparator);
				reader.setSerializedObject(idx, res);
				SerialUtils.readExternalCollection(res, reader);
				return res;
			} catch (Exception e) {
				SerializedObject<TreeSet<Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read TreeSet.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
		SERIALIZABLE_CLASS_WRITERS.put(ConcurrentSkipListSet.class,
				(ConcurrentSkipListSet<?> v, ContentWriterObjectOutput writer) -> {
					writer.writeObject(v.comparator());
					SerialUtils.writeExternalCollection(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(ConcurrentSkipListSet.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				@SuppressWarnings("unchecked")
				Comparator<Object> comparator = (Comparator<Object>) reader.readObject();
				ConcurrentSkipListSet<Object> res = new ConcurrentSkipListSet<>(comparator);
				reader.setSerializedObject(idx, res);
				SerialUtils.readExternalCollection(res, reader);
				return res;
			} catch (Exception e) {
				SerializedObject<ConcurrentSkipListSet<Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read ConcurrentSkipListSet.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
		SERIALIZABLE_CLASS_WRITERS.put(TreeMap.class, (TreeMap<?, ?> v, ContentWriterObjectOutput writer) -> {
			writer.writeObject(v.comparator());
			SerialUtils.writeExternalMap(writer, v);
		});
		SERIALIZABLE_CLASS_READERS.put(TreeMap.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				@SuppressWarnings("unchecked")
				Comparator<Object> comparator = (Comparator<Object>) reader.readObject();
				TreeMap<Object, Object> res = new TreeMap<>(comparator);
				reader.setSerializedObject(idx, res);
				SerialUtils.readExternalMap(res, reader);
				return res;
			} catch (Exception e) {
				SerializedObject<TreeMap<Object, Object>> serialobj;
				serialobj = new FailedSerializedObject<>(() -> new ObjectReadException("Failed to read TreeMap.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
		SERIALIZABLE_CLASS_WRITERS.put(ConcurrentSkipListMap.class,
				(ConcurrentSkipListMap<?, ?> v, ContentWriterObjectOutput writer) -> {
					writer.writeObject(v.comparator());
					SerialUtils.writeExternalMap(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(ConcurrentSkipListMap.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				@SuppressWarnings("unchecked")
				Comparator<Object> comparator = (Comparator<Object>) reader.readObject();
				ConcurrentSkipListMap<Object, Object> res = new ConcurrentSkipListMap<>(comparator);
				reader.setSerializedObject(idx, res);
				SerialUtils.readExternalMap(res, reader);
				return res;
			} catch (Exception e) {
				SerializedObject<ConcurrentSkipListMap<Object, Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read ConcurrentSkipListMap.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
		SERIALIZABLE_CLASS_WRITERS.put(HashMap.class, (HashMap<?, ?> v, ContentWriterObjectOutput writer) -> {
			SerialUtils.writeExternalMap(writer, v);
		});
		SERIALIZABLE_CLASS_READERS.put(HashMap.class, reader -> {
			HashMap<Object, Object> res = new HashMap<>();
			int idx = reader.addSerializedObject(res);
			try {
				SerialUtils.readExternalMap(res, reader);
				return res;
			} catch (Exception e) {
				SerializedObject<HashMap<Object, Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read HashMap.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
		//identity hash map is not supported, due to the value internalization
		SERIALIZABLE_CLASS_WRITERS.put(LinkedHashMap.class,
				(LinkedHashMap<?, ?> v, ContentWriterObjectOutput writer) -> {
					SerialUtils.writeExternalMap(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(LinkedHashMap.class, reader -> {
			LinkedHashMap<Object, Object> res = new LinkedHashMap<>();
			int idx = reader.addSerializedObject(res);
			try {
				SerialUtils.readExternalMap(res, reader);
				return res;
			} catch (Exception e) {
				SerializedObject<LinkedHashMap<Object, Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read LinkedHashMap.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
		SERIALIZABLE_CLASS_WRITERS.put(ConcurrentHashMap.class,
				(ConcurrentHashMap<?, ?> v, ContentWriterObjectOutput writer) -> {
					SerialUtils.writeExternalMap(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(ConcurrentHashMap.class, reader -> {
			ConcurrentHashMap<Object, Object> res = new ConcurrentHashMap<>();
			int idx = reader.addSerializedObject(res);
			try {
				SerialUtils.readExternalMap(res, reader);
				return res;
			} catch (Exception e) {
				SerializedObject<ConcurrentHashMap<Object, Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read ConcurrentHashMap.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
		SERIALIZABLE_CLASS_WRITERS.put(HashSet.class, (HashSet<?> v, ContentWriterObjectOutput writer) -> {
			SerialUtils.writeExternalCollection(writer, v);
		});
		SERIALIZABLE_CLASS_READERS.put(HashSet.class, reader -> {
			HashSet<Object> res = new HashSet<>();
			int idx = reader.addSerializedObject(res);
			try {
				SerialUtils.readExternalCollection(res, reader);
				return res;
			} catch (Exception e) {
				SerializedObject<HashSet<Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read HashSet.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
		SERIALIZABLE_CLASS_WRITERS.put(LinkedHashSet.class, (LinkedHashSet<?> v, ContentWriterObjectOutput writer) -> {
			SerialUtils.writeExternalCollection(writer, v);
		});
		SERIALIZABLE_CLASS_READERS.put(LinkedHashSet.class, reader -> {
			LinkedHashSet<Object> res = new LinkedHashSet<>();
			int idx = reader.addSerializedObject(res);
			try {
				SerialUtils.readExternalCollection(res, reader);
				return res;
			} catch (Exception e) {
				SerializedObject<LinkedHashSet<Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read LinkedHashSet.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
		Class<?> concurrenthashkeysetviewclass = ConcurrentHashMap.newKeySet().getClass();
		SERIALIZABLE_CLASS_WRITERS.put(concurrenthashkeysetviewclass, (Set<?> v, ContentWriterObjectOutput writer) -> {
			SerialUtils.writeExternalCollection(writer, v);
		});
		SERIALIZABLE_CLASS_READERS.put(concurrenthashkeysetviewclass, reader -> {
			Set<Object> res = ConcurrentHashMap.newKeySet();
			int idx = reader.addSerializedObject(res);
			try {
				SerialUtils.readExternalCollection(res, reader);
				return res;
			} catch (Exception e) {
				SerializedObject<Set<Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read ConcurrentHashMap.KeySetView.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
		SERIALIZABLE_CLASS_WRITERS.put(ArrayList.class, (ArrayList<?> v, ContentWriterObjectOutput writer) -> {
			SerialUtils.writeExternalCollection(writer, v);
		});
		SERIALIZABLE_CLASS_READERS.put(ArrayList.class, reader -> {
			ArrayList<Object> res = new ArrayList<>();
			int idx = reader.addSerializedObject(res);
			try {
				SerialUtils.readExternalCollection(res, reader);
				return res;
			} catch (Exception e) {
				SerializedObject<ArrayList<Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read ArrayList.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
		SERIALIZABLE_CLASS_WRITERS.put(LinkedList.class, (LinkedList<?> v, ContentWriterObjectOutput writer) -> {
			SerialUtils.writeExternalCollection(writer, v);
		});
		SERIALIZABLE_CLASS_READERS.put(LinkedList.class, reader -> {
			LinkedList<Object> res = new LinkedList<>();
			int idx = reader.addSerializedObject(res);
			try {
				SerialUtils.readExternalCollection(res, reader);
				return res;
			} catch (Exception e) {
				SerializedObject<LinkedList<Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read LinkedList.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
		SERIALIZABLE_CLASS_WRITERS.put(CopyOnWriteArrayList.class,
				(CopyOnWriteArrayList<?> v, ContentWriterObjectOutput writer) -> {
					SerialUtils.writeExternalCollection(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(CopyOnWriteArrayList.class, reader -> {
			CopyOnWriteArrayList<Object> res = new CopyOnWriteArrayList<>();
			int idx = reader.addSerializedObject(res);
			try {
				Object[] readobjects = SerialUtils.readExternalArray(reader, Object[]::new);
				ObjectUtils.addAll(res, readobjects);
				return res;
			} catch (Exception e) {
				SerializedObject<CopyOnWriteArrayList<Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read CopyOnWriteArrayList.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});

		//EnumSet handling is omitted, as it only contains enums
		//    and can be serialized with the default mechanism
		SERIALIZABLE_CLASS_WRITERS.put(EnumMap.class, (EnumMap<?, ?> v, ContentWriterObjectOutput writer) -> {
			Class<? extends Enum<?>> enumtype = ObjectUtils.getEnumMapEnumType(v);
			writer.writeObject(enumtype);
			SerialUtils.writeExternalMap(writer, v);
		});
		SERIALIZABLE_CLASS_READERS.put(EnumMap.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				@SuppressWarnings("unchecked")
				Class<? extends Enum<?>> entype = (Class<? extends Enum<?>>) reader.readObject();
				@SuppressWarnings({ "rawtypes", "unchecked" })
				EnumMap<?, ?> res = new EnumMap(entype);
				reader.setSerializedObject(idx, res);
				SerialUtils.readExternalMap(res, reader);
				return res;
			} catch (Exception e) {
				SerializedObject<?> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read EnumMap.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});

		addEmptyCollectionReaderWriter(Collections.emptyList());
		addEmptyCollectionReaderWriter(Collections.emptySet());
		addEmptyCollectionReaderWriter(Collections.emptySortedSet());
		addEmptyCollectionReaderWriter(Collections.emptyNavigableSet());
		addEmptyCollectionReaderWriter(Collections.emptyEnumeration());
		addEmptyCollectionReaderWriter(Collections.emptyMap());
		addEmptyCollectionReaderWriter(Collections.emptySortedMap());
		addEmptyCollectionReaderWriter(Collections.emptyNavigableMap());

		Object singletontestelement = new Object();
		Set<Object> singletonset = Collections.singleton(singletontestelement);
		Class<?> singletonsetclass = singletonset.getClass();
		SERIALIZABLE_CLASS_WRITERS.put(singletonsetclass, (Set<?> v, ContentWriterObjectOutput writer) -> {
			writer.writeObject(v.iterator().next());
		});
		SERIALIZABLE_CLASS_READERS.put(singletonsetclass, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				Object obj = reader.readObject();
				Set<Object> res = Collections.singleton(obj);
				reader.setSerializedObject(idx, res);
				return res;
			} catch (Exception e) {
				SerializedObject<Set<Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read singleton set.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});

		List<Object> singletonlist = Collections.singletonList(singletontestelement);
		Class<?> singletonlistclass = singletonlist.getClass();
		SERIALIZABLE_CLASS_WRITERS.put(singletonlistclass, (List<?> v, ContentWriterObjectOutput writer) -> {
			writer.writeObject(v.get(0));
		});
		SERIALIZABLE_CLASS_READERS.put(singletonlistclass, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				Object obj = reader.readObject();
				List<Object> res = Collections.singletonList(obj);
				reader.setSerializedObject(idx, res);
				return res;
			} catch (Exception e) {
				SerializedObject<List<Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read singleton list.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});

		Map<Object, Object> singletonmap = Collections.singletonMap(singletontestelement, singletontestelement);
		Class<?> singletonmapclass = singletonmap.getClass();
		SERIALIZABLE_CLASS_WRITERS.put(singletonmapclass, (Map<?, ?> v, ContentWriterObjectOutput writer) -> {
			Entry<?, ?> entry = v.entrySet().iterator().next();
			writer.writeObject(entry.getKey());
			writer.writeObject(entry.getValue());
		});
		SERIALIZABLE_CLASS_READERS.put(singletonmapclass, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				Object key = reader.readObject();
				Object value = reader.readObject();
				Map<Object, Object> res = Collections.singletonMap(key, value);
				reader.setSerializedObject(idx, res);
				return res;
			} catch (Exception e) {
				SerializedObject<Map<Object, Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read singleton map.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});

		List<Object> aslist = Arrays.asList(singletontestelement, singletontestelement, singletontestelement);
		Class<?> astlistclass = aslist.getClass();
		SERIALIZABLE_CLASS_WRITERS.put(astlistclass, (List<?> v, ContentWriterObjectOutput writer) -> {
			int size = v.size();
			writer.writeInt(size);
			for (int i = 0; i < size; i++) {
				writer.writeObject(v.get(i));
			}
		});
		SERIALIZABLE_CLASS_READERS.put(astlistclass, reader -> {
			int size;
			try {
				size = reader.readInt();
			} catch (Exception e) {
				SerializedObject<List<Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read elements.", e));
				reader.addSerializedObject(serialobj);
				return serialobj.get();
			}
			Object[] array = new Object[size];
			List<Object> res = Arrays.asList(array);
			int serialidx = reader.addSerializedObject(res);
			try {
				for (int i = 0; i < size; i++) {
					array[i] = reader.readObject();
				}
				return res;
			} catch (Exception e) {
				SerializedObject<List<Object>> serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read elements.", e));
				reader.setSerializedObject(serialidx, serialobj);
				return serialobj.get();
			}
		});

		//need custom serialization for the reverseorder comparator as it is not backed by enum
		Comparator<Object> reverseordercomparator = Collections.reverseOrder();
		@SuppressWarnings("rawtypes")
		Class<? extends Comparator> reverseorderclass = reverseordercomparator.getClass();
		Comparator<Object> reverseorderserializedobject = reverseordercomparator;
		SERIALIZABLE_CLASS_WRITERS.put(reverseorderclass, (v, writer) -> {
		});
		SERIALIZABLE_CLASS_READERS.put(reverseorderclass, reader -> {
			reader.addSerializedObject(reverseorderserializedobject);
			return reverseordercomparator;
		});

		SERIALIZABLE_CLASS_WRITERS.put(Method.class, (Method v, ContentWriterObjectOutput writer) -> {
			writer.writeObject(v.getDeclaringClass());
			writer.writeUTF(v.getName());
			Class<?>[] paramtypes = v.getParameterTypes();
			writer.writeInt(paramtypes.length);
			for (int i = 0; i < paramtypes.length; i++) {
				writer.writeObject(paramtypes[i]);
			}
		});
		SERIALIZABLE_CLASS_READERS.put(Method.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				Class<?> declaringclass = (Class<?>) reader.readObject();
				String name = reader.readUTF();
				int ptlen = reader.readInt();
				Class<?>[] paramtypes = new Class<?>[ptlen];
				for (int i = 0; i < paramtypes.length; i++) {
					paramtypes[i] = (Class<?>) reader.readObject();
				}
				Method res = declaringclass.getDeclaredMethod(name, paramtypes);
				reader.setSerializedObject(idx, res);
				return res;
			} catch (Exception e) {
				SerializedObject<Method> serialobj = new FailedSerializedObject<>(
						() -> new SerializationReflectionException("Failed to read Method.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
		SERIALIZABLE_CLASS_WRITERS.put(Constructor.class, (Constructor<?> v, ContentWriterObjectOutput writer) -> {
			writer.writeObject(v.getDeclaringClass());
			Class<?>[] paramtypes = v.getParameterTypes();
			writer.writeInt(paramtypes.length);
			for (int i = 0; i < paramtypes.length; i++) {
				writer.writeObject(paramtypes[i]);
			}
		});
		SERIALIZABLE_CLASS_READERS.put(Constructor.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				Class<?> declaringclass = (Class<?>) reader.readObject();
				int ptlen = reader.readInt();
				Class<?>[] paramtypes = new Class<?>[ptlen];
				for (int i = 0; i < paramtypes.length; i++) {
					paramtypes[i] = (Class<?>) reader.readObject();
				}
				Constructor<?> res = declaringclass.getDeclaredConstructor(paramtypes);
				reader.setSerializedObject(idx, res);
				return res;
			} catch (Exception e) {
				SerializedObject<Constructor<?>> serialobj = new FailedSerializedObject<>(
						() -> new SerializationReflectionException("Failed to read Constructor.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
		SERIALIZABLE_CLASS_WRITERS.put(Field.class, (Field v, ContentWriterObjectOutput writer) -> {
			writer.writeObject(v.getDeclaringClass());
			writer.writeUTF(v.getName());
		});
		SERIALIZABLE_CLASS_READERS.put(Field.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				Class<?> declaringclass = (Class<?>) reader.readObject();
				String name = reader.readUTF();
				Field res = declaringclass.getDeclaredField(name);
				reader.setSerializedObject(idx, res);
				return res;
			} catch (Exception e) {
				SerializedObject<Field> serialobj = new FailedSerializedObject<>(
						() -> new SerializationReflectionException("Failed to read Field.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});

		SERIALIZABLE_CLASS_WRITERS.put(Optional.class, (Optional<?> v, ContentWriterObjectOutput writer) -> {
			writer.writeObject(v.orElse(null));
		});
		SERIALIZABLE_CLASS_READERS.put(Optional.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				Optional<Object> res = Optional.ofNullable(reader.readObject());
				reader.setSerializedObject(idx, res);
				return res;
			} catch (Exception e) {
				SerializedObject<Optional<?>> serialobj = new FailedSerializedObject<>(
						() -> new SerializationReflectionException("Failed to read Optional.", e));
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
	}

	private static void addEmptyCollectionReaderWriter(Object emptycoll) {
		Class<?> emptycollclass = emptycoll.getClass();
		//instantiate this outside of the lambdas, so they are reused
		SERIALIZABLE_CLASS_WRITERS.put(emptycollclass, (v, writer) -> {
		});
		SERIALIZABLE_CLASS_READERS.put(emptycollclass, reader -> {
			reader.addSerializedObject(emptycoll);
			return emptycoll;
		});
	}

	static final Map<Class<?>, IOBiConsumer<?, ContentWriterObjectOutput>> VALUE_CLASS_WRITERS = new HashMap<>();
	static final Map<String, ObjectReaderFunction<ContentReaderObjectInput, ?>> VALUE_CLASS_READERS = new TreeMap<>();
	static {
		VALUE_CLASS_WRITERS.put(UUID.class,
				(UUID v, ContentWriterObjectOutput writer) -> writer.out.writeUTF(v.toString()));
		VALUE_CLASS_READERS.put(UUID.class.getName(), reader -> {
			try {
				UUID res = UUID.fromString(reader.state.in.readUTF());
				reader.addSerializedObject(res);
				return res;
			} catch (Exception e) {
				SerializedObject<UUID> serialobj = new SerializationProtocolFailedSerializedObject<>(
						"Failed to read UUID.", e);
				reader.addSerializedObject(serialobj);
				return serialobj.get();
			}
		});
		VALUE_CLASS_WRITERS.put(Date.class,
				(Date v, ContentWriterObjectOutput writer) -> writer.out.writeLong(v.getTime()));
		VALUE_CLASS_READERS.put(Date.class.getName(), reader -> {
			try {
				long time = reader.state.in.readLong();
				Date res = new Date(time);

				reader.addSerializedObject(res);
				return res;
			} catch (Exception e) {
				SerializedObject<Date> serialobj = new SerializationProtocolFailedSerializedObject<>(
						"Failed to read Date.", e);
				reader.addSerializedObject(serialobj);
				return serialobj.get();
			}
		});

		VALUE_CLASS_WRITERS.put(Byte.class, (Byte v, ContentWriterObjectOutput writer) -> writer.out.writeByte(v));
		VALUE_CLASS_READERS.put(Byte.class.getName(), reader -> {
			try {
				Byte res = reader.state.in.readByte();
				reader.addSerializedObject(res);
				return res;
			} catch (Exception e) {
				SerializedObject<Byte> serialobj = new SerializationProtocolFailedSerializedObject<>(
						"Failed to read Byte.", e);
				reader.addSerializedObject(serialobj);
				return serialobj.get();
			}
		});
		VALUE_CLASS_WRITERS.put(Short.class, (Short v, ContentWriterObjectOutput writer) -> writer.out.writeShort(v));
		VALUE_CLASS_READERS.put(Short.class.getName(), reader -> {
			try {
				Short res = reader.state.in.readShort();
				reader.addSerializedObject(res);
				return res;
			} catch (Exception e) {
				SerializedObject<Short> serialobj = new SerializationProtocolFailedSerializedObject<>(
						"Failed to read Short.", e);
				reader.addSerializedObject(serialobj);
				return serialobj.get();
			}
		});
		VALUE_CLASS_WRITERS.put(Integer.class,
				(Integer v, ContentWriterObjectOutput writer) -> writer.writeRawVarInt(v));
		VALUE_CLASS_READERS.put(Integer.class.getName(), reader -> {
			try {
				Integer res = reader.readRawVarInt();
				reader.addSerializedObject(res);
				return res;
			} catch (Exception e) {
				SerializedObject<Integer> serialobj = new SerializationProtocolFailedSerializedObject<>(
						"Failed to read Integer.", e);
				reader.addSerializedObject(serialobj);
				return serialobj.get();
			}
		});
		VALUE_CLASS_WRITERS.put(Long.class, (Long v, ContentWriterObjectOutput writer) -> writer.out.writeLong(v));
		VALUE_CLASS_READERS.put(Long.class.getName(), reader -> {
			try {
				Long res = reader.state.in.readLong();
				reader.addSerializedObject(res);
				return res;
			} catch (Exception e) {
				SerializedObject<Long> serialobj = new SerializationProtocolFailedSerializedObject<>(
						"Failed to read Long.", e);
				reader.addSerializedObject(serialobj);
				return serialobj.get();
			}
		});
		VALUE_CLASS_WRITERS.put(Float.class, (Float v, ContentWriterObjectOutput writer) -> writer.out.writeFloat(v));
		VALUE_CLASS_READERS.put(Float.class.getName(), reader -> {
			try {
				Float res = reader.state.in.readFloat();
				reader.addSerializedObject(res);
				return res;
			} catch (Exception e) {
				SerializedObject<Float> serialobj = new SerializationProtocolFailedSerializedObject<>(
						"Failed to read Float.", e);
				reader.addSerializedObject(serialobj);
				return serialobj.get();
			}
		});
		VALUE_CLASS_WRITERS.put(Double.class,
				(Double v, ContentWriterObjectOutput writer) -> writer.out.writeDouble(v));
		VALUE_CLASS_READERS.put(Double.class.getName(), reader -> {
			try {
				Double res = reader.state.in.readDouble();
				reader.addSerializedObject(res);
				return res;
			} catch (Exception e) {
				SerializedObject<Double> serialobj = new SerializationProtocolFailedSerializedObject<>(
						"Failed to read Double.", e);
				reader.addSerializedObject(serialobj);
				return serialobj.get();
			}
		});
		VALUE_CLASS_WRITERS.put(Character.class,
				(Character v, ContentWriterObjectOutput writer) -> writer.out.writeChar(v));
		VALUE_CLASS_READERS.put(Character.class.getName(), reader -> {
			try {
				Character res = reader.state.in.readChar();
				reader.addSerializedObject(res);
				return res;
			} catch (Exception e) {
				SerializedObject<Character> serialobj = new SerializationProtocolFailedSerializedObject<>(
						"Failed to read Character.", e);
				reader.addSerializedObject(serialobj);
				return serialobj.get();
			}
		});
		VALUE_CLASS_WRITERS.put(Boolean.class,
				(Boolean v, ContentWriterObjectOutput writer) -> writer.out.writeBoolean(v));
		VALUE_CLASS_READERS.put(Boolean.class.getName(), reader -> {
			try {
				Boolean res = reader.state.in.readBoolean();
				reader.addSerializedObject(res);
				return res;
			} catch (Exception e) {
				SerializedObject<Boolean> serialobj = new SerializationProtocolFailedSerializedObject<>(
						"Failed to read Boolean.", e);
				reader.addSerializedObject(serialobj);
				return serialobj.get();
			}
		});

		VALUE_CLASS_WRITERS.put(SakerPath.class,
				(SakerPath v, ContentWriterObjectOutput writer) -> writer.writeUTF(v.toString()));
		VALUE_CLASS_READERS.put(SakerPath.class.getName(), reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				SakerPath res = SakerPath.valueOf(reader.readUTF());
				reader.setSerializedObject(idx, res);
				return res;
			} catch (Exception e) {
				SerializedObject<SakerPath> serialobj = new SerializationProtocolFailedSerializedObject<>(
						"Failed to read SakerPath.", e);
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});

		VALUE_CLASS_WRITERS.put(WildcardPath.class,
				(WildcardPath v, ContentWriterObjectOutput writer) -> writer.writeUTF(v.toString()));
		VALUE_CLASS_READERS.put(WildcardPath.class.getName(), reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				WildcardPath res = WildcardPath.valueOf(reader.readUTF());
				reader.setSerializedObject(idx, res);
				return res;
			} catch (Exception e) {
				SerializedObject<SakerPath> serialobj = new SerializationProtocolFailedSerializedObject<>(
						"Failed to read WildcardPath.", e);
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});

		VALUE_CLASS_WRITERS.put(URI.class, (URI v, ContentWriterObjectOutput writer) -> writer.writeUTF(v.toString()));
		VALUE_CLASS_READERS.put(URI.class.getName(), reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			try {
				URI res = new URI(reader.readUTF());
				reader.setSerializedObject(idx, res);
				return res;
			} catch (Exception e) {
				SerializedObject<URI> serialobj = new SerializationProtocolFailedSerializedObject<>(
						"Failed to read URI.", e);
				reader.setSerializedObject(idx, serialobj);
				return serialobj.get();
			}
		});
	}

	private static final Map<Class<?>, BiConsumer<? super DataOutputUnsyncByteArrayOutputStream, ?>> ARRAY_WRITERS = new HashMap<>();
	static {
		ARRAY_WRITERS.put(byte.class,
				(BiConsumer<DataOutputUnsyncByteArrayOutputStream, byte[]>) DataOutputUnsyncByteArrayOutputStream::write);
		ARRAY_WRITERS.put(short.class,
				(BiConsumer<DataOutputUnsyncByteArrayOutputStream, short[]>) DataOutputUnsyncByteArrayOutputStream::write);
		ARRAY_WRITERS.put(int.class,
				(BiConsumer<DataOutputUnsyncByteArrayOutputStream, int[]>) DataOutputUnsyncByteArrayOutputStream::write);
		ARRAY_WRITERS.put(long.class,
				(BiConsumer<DataOutputUnsyncByteArrayOutputStream, long[]>) DataOutputUnsyncByteArrayOutputStream::write);
		ARRAY_WRITERS.put(float.class,
				(BiConsumer<DataOutputUnsyncByteArrayOutputStream, float[]>) DataOutputUnsyncByteArrayOutputStream::write);
		ARRAY_WRITERS.put(double.class,
				(BiConsumer<DataOutputUnsyncByteArrayOutputStream, double[]>) DataOutputUnsyncByteArrayOutputStream::write);
		ARRAY_WRITERS.put(char.class,
				(BiConsumer<DataOutputUnsyncByteArrayOutputStream, char[]>) DataOutputUnsyncByteArrayOutputStream::write);
		ARRAY_WRITERS.put(boolean.class,
				(BiConsumer<DataOutputUnsyncByteArrayOutputStream, boolean[]>) DataOutputUnsyncByteArrayOutputStream::write);
	}

	private final DirectWritableDataOutputUnsyncByteArrayOutputStream out = new DirectWritableDataOutputUnsyncByteArrayOutputStream(
			1024 * 8);

	private final IdentityHashMap<Object, Integer> objectIndices = new IdentityHashMap<>();
	private final Map<Class<?>, BuiltinValueWriterCache> builtinValueWriters = new HashMap<>();
	private final NavigableMap<String, InternedValue<String>> stringInternalizer = new TreeMap<>();

	/**
	 * Maps to a {@link Map} that contains the interned value mapping if the given type is a value type.
	 */
	private final Map<Class<?>, Optional<Map<Object, Integer>>> valueTypeInternMaps = new HashMap<>();

	private final ClassLoaderResolver registry;

	private final Set<Class<?>> warnedClasses = new HashSet<>();

	private char[] charWriteBuffer = ObjectUtils.EMPTY_CHAR_ARRAY;

	{
		for (Entry<Class<?>, IOBiConsumer<?, ContentWriterObjectOutput>> entry : VALUE_CLASS_WRITERS.entrySet()) {
			@SuppressWarnings("unchecked")
			IOBiConsumer<Object, ContentWriterObjectOutput> writer = (IOBiConsumer<Object, ContentWriterObjectOutput>) entry
					.getValue();
			builtinValueWriters.put(entry.getKey(), new BuiltinValueWriterCache(writer));
		}
	}

	public ContentWriterObjectOutput(ClassLoaderResolver registry) {
		this.registry = registry;
	}

	public void writeNull() {
		out.writeByte(C_OBJECT_NULL);
	}

	@Override
	public void writeBoolean(boolean v) throws IOException {
		out.writeByte(v ? C_BOOLEAN_TRUE : C_BOOLEAN_FALSE);
	}

	@Override
	public void writeByte(int v) throws IOException {
		writeMultiBytes((byte) C_BYTE, (byte) v);
	}

	private void writeMultiBytes(byte b1, byte b2) {
		final DirectWritableDataOutputUnsyncByteArrayOutputStream out = this.out;
		int offset = out.ensureSpace(2);
		final byte[] buf = out.getBuffer();

		buf[offset++] = b1;
		buf[offset++] = b2;

		out.setCount(offset);
	}

	private void writeMultiBytes(byte b1, byte b2, byte b3) {
		final DirectWritableDataOutputUnsyncByteArrayOutputStream out = this.out;
		int offset = out.ensureSpace(3);
		final byte[] buf = out.getBuffer();

		buf[offset++] = b1;
		buf[offset++] = b2;
		buf[offset++] = b3;

		out.setCount(offset);
	}

	private void writeMultiBytes(byte b1, byte b2, byte b3, byte b4) {
		final DirectWritableDataOutputUnsyncByteArrayOutputStream out = this.out;
		int offset = out.ensureSpace(4);
		final byte[] buf = out.getBuffer();

		buf[offset++] = b1;
		buf[offset++] = b2;
		buf[offset++] = b3;
		buf[offset++] = b4;

		out.setCount(offset);
	}

	private void writeMultiBytes(byte b1, byte b2, byte b3, byte b4, byte b5) {
		final DirectWritableDataOutputUnsyncByteArrayOutputStream out = this.out;
		int offset = out.ensureSpace(5);
		final byte[] buf = out.getBuffer();

		buf[offset++] = b1;
		buf[offset++] = b2;
		buf[offset++] = b3;
		buf[offset++] = b4;
		buf[offset++] = b5;

		out.setCount(offset);
	}

	private void writeByteShort(byte b, int v) {
		writeMultiBytes(b, (byte) ((v >>> 8)), (byte) ((v)));
	}

	private void writeByteShort(byte b, long v) {
		writeMultiBytes(b, (byte) ((v >>> 8)), (byte) ((v)));
	}

	private void writeByteShort(byte b, short v) {
		writeMultiBytes(b, (byte) ((v >>> 8)), (byte) ((v)));
	}

	private void writeByteTriInt(byte b, int v) {
		writeMultiBytes(b, (byte) ((v >>> 16)), (byte) ((v >>> 8)), (byte) ((v)));
	}

	private void writeByteInt(byte b, int v) {
		writeMultiBytes(b, (byte) ((v >>> 24)), (byte) ((v >>> 16)), (byte) ((v >>> 8)), (byte) ((v)));
	}

	@Override
	public void writeShort(int v) throws IOException {
		if ((v & 0xFF00) == 0x0000) {
			writeMultiBytes((byte) C_SHORT_1, (byte) v);
		} else {
			writeByteShort((byte) C_SHORT_2, v);
		}
	}

	@Override
	public void writeChar(int v) throws IOException {
		writeByteShort((byte) C_CHAR, v);
	}

	void writeRawVarInt(int v) throws IOException {
		final DirectWritableDataOutputUnsyncByteArrayOutputStream out = this.out;
		int offset = out.ensureSpace(MAX_RAWVARINT_SIZE);
		final byte[] buf = out.getBuffer();

		offset = writeRawIntToBuf(v, offset, buf);

		out.setCount(offset);
	}

	private static int writeRawIntToBuf(int v, int offset, final byte[] buf) {
		while (true) {
			int wb = v & 0x7F;
			v = v >>> 7;
			if (v != 0) {
				//still more bytes, add the continue flag
				buf[offset++] = (byte) (wb | 0x80);
				continue;
			}
			//last byte to be written
			buf[offset++] = (byte) wb;
			break;
		}
		return offset;
	}

	@Override
	public void writeInt(int v) throws IOException {
		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		switch (v & 0xFFFF_0000) {
			case 0x0000_0000: {
				//starts with 0x0000_xxxx
				switch (v & 0x0000_FF00) {
					case 0x0000_0000: {
						//starts with 0x0000_00xx
						switch (v) {
							case 0: {
								out.writeByte(C_INT_ZERO);
								break;
							}
							case 1: {
								out.writeByte(C_INT_ONE);
								break;
							}
							default: {
								writeMultiBytes((byte) C_INT_1, (byte) v);
								break;
							}
						}
						break;
					}
					default: {
						writeByteInt((byte) C_INT_4, v);
						break;
					}
				}
				break;
			}
			case 0xFFFF0000: {
				//starts with 0xFFFF_xxxx
				switch (v & 0x0000_FF00) {
					case 0x0000FF00: {
						//starts with 0xFFFF_FFxx
						if (v == -1) {
							out.writeByte(C_INT_NEGATIVE_ONE);
						} else {
							writeMultiBytes((byte) C_INT_F_1, (byte) v);
						}
						break;
					}
					default: {
						writeByteShort((byte) C_INT_F_2, v);
						break;
					}
				}
				break;
			}
			default: {
				switch (v & 0xFF00_0000) {
					case 0xFF00_0000: {
						//starts with 0xFFxx_xxxx
						writeByteTriInt((byte) C_INT_F_3, v);
						break;
					}
					case 0x0000_0000: {
						//starts with 0x00xx_xxxx
						writeByteTriInt((byte) C_INT_3, v);
						break;
					}
					default: {
						//full int
						writeByteInt((byte) C_INT_4, v);
						break;
					}
				}
				break;
			}
		}
	}

	@Override
	public void writeLong(long v) throws IOException {
		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		long top4 = v & 0xFFFFFFFF_00000000L;
		if (top4 == 0x00000000_00000000L) {
			//starts with 0x00000000_xxxxxxxx
			long bottop2 = v & 0x00000000_FFFF0000L;
			if (bottop2 == 0x00000000_00000000L) {
				//starts with 0x00000000_0000xxxx
				if (v == 0) {
					out.writeByte(C_LONG_ZERO);
				} else {
					writeByteShort((byte) C_LONG_2, v);
				}
			} else {
				out.writeByte(C_LONG_8);
				out.writeLong(v);
			}
		} else if (top4 == 0xFFFFFFFF00000000L) {
			//starts with 0xFFFFFFFF_xxxxxxxx
			long bottop2 = v & 0x00000000_FFFF0000L;
			if (bottop2 == 0x00000000_FFFF0000L) {
				//starts with 0xFFFFFFFF_FFFFxxxx
				if (v == -1) {
					out.writeByte(C_LONG_NEGATIVE_ONE);
				} else {
					writeByteShort((byte) C_LONG_F_2, v);
				}
			} else {
				writeByteInt((byte) C_LONG_F_4, (int) v);
			}
		} else {
			long toptop2 = v & 0xFFFF0000_00000000L;
			if (toptop2 == 0xFFFF0000_00000000L) {
				//starts with 0xFFFFxxxx_xxxxxxxx
				out.writeByte(C_LONG_F_6);
				out.writeShort((short) (v >>> 32));
				out.writeInt((int) v);
			} else if (toptop2 == 0x00000000_00000000L) {
				//starts with 0x0000xxxx_xxxxxxxx
				out.writeByte(C_LONG_6);
				out.writeShort((short) (v >>> 32));
				out.writeInt((int) v);
			} else {
				//full long
				out.writeByte(C_LONG_8);
				out.writeLong(v);
			}
		}
	}

	@Override
	public void writeFloat(float v) throws IOException {
		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		out.writeByte(C_FLOAT);
		out.writeFloat(v);
	}

	@Override
	public void writeDouble(double v) throws IOException {
		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		out.writeByte(C_DOUBLE);
		out.writeDouble(v);
	}

	@Override
	public void writeBytes(String s) throws IOException {
		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		out.writeByte(C_BYTEARRAY);
		writeInt(s.length() * 2);
		out.writeBytes(s);
	}

	@Override
	public void writeChars(String s) throws IOException {
		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		out.writeByte(C_CHARS);
		writeInt(s.length());
		out.writeChars(s);
	}

	public static boolean isLowBytesChars(char[] chars, int index, int length) {
		int end = index + length;
		while (index < end) {
			char c = chars[index++];
			if ((c & 0xFF00) != 0) {
				//there is at least one bit set in the higher byte of the char
				return false;
			}
		}
		return true;
	}

	private UTFPrefixInfo getBetterPrefix(String s, int slen, Entry<String, InternedValue<String>> first,
			Entry<String, InternedValue<String>> second) {
		if (first == null) {
			first = second;
			if (first == null) {
				return null;
			}
		}
		//Note: regionMatches has length check for UTF_PREFIX_MIN_LEN in it
		String firststr = first.getKey();
		if (s.regionMatches(0, firststr, 0, UTF_PREFIX_MIN_LEN)) {
			int firstcommon = getPrefixCommonCharCountWithBuffer(slen, firststr, firststr.length(), UTF_PREFIX_MIN_LEN);
			if (second == null) {
				return new UTFPrefixInfo(first.getValue(), firstcommon);
			}
			String secondstr = second.getKey();
			if (s.regionMatches(0, secondstr, 0, firstcommon + 1)) {
				//the second one matches at least one more common character
				int secondcommon = getPrefixCommonCharCountWithBuffer(slen, secondstr, secondstr.length(),
						firstcommon + 1);
				return new UTFPrefixInfo(second.getValue(), secondcommon);
			}
			return new UTFPrefixInfo(first.getValue(), firstcommon);
		}
		if (second != null) {
			String secondstr = second.getKey();
			if (s.regionMatches(0, secondstr, 0, UTF_PREFIX_MIN_LEN)) {
				int secondcommon = getPrefixCommonCharCountWithBuffer(slen, secondstr, secondstr.length(),
						UTF_PREFIX_MIN_LEN);
				return new UTFPrefixInfo(second.getValue(), secondcommon);
			}
		}
		return null;
	}

	@Override
	public void writeUTF(String s) throws IOException {
		Objects.requireNonNull(s, "utf string");

		writeUTFImpl(s, false);
	}

	private void writeUTFImpl(String s, boolean objwrite) throws SerializationProtocolException, IOException {
		NavigableMap<String, InternedValue<String>> internalizer = this.stringInternalizer;

		Entry<String, InternedValue<String>> floorentry = internalizer.floorEntry(s);
		if (floorentry != null && floorentry.getKey().equals(s)) {
			writeIndexObjectCommandWithCommandBase(floorentry.getValue().index,
					objwrite ? C_OBJECT_UTF_IDX_BASE : C_UTF_IDX_BASE);
			return;
		}

		int slen = ensureCharWriteBuffer(s);
		int idx = internalizer.size();
		InternedValue<?> prev = internalizer.putIfAbsent(s, new InternedValue<>(s, idx));
		if (prev != null) {
			//sanity check
			throw new SerializationProtocolException("Internal Error: String is already present: " + s + " with index: "
					+ prev.index + " for new index: " + idx);
		}

		if (slen > UTF_PREFIX_MIN_LEN) {
			//attempt prefixing if the written string is longer than the min requirement
			UTFPrefixInfo prefixinfo = getBetterPrefix(s, slen, floorentry, internalizer.higherEntry(s));

			if (prefixinfo != null) {
				//both strings have a common starting characters
				writePrefixedUtfData(prefixinfo, slen, objwrite);
				return;
			}
		}

		writeUtfData(slen, objwrite);
	}

	private int getPrefixCommonCharCountWithBuffer(int slen, String otherstr, int olen, int startidx) {
		final char[] charbuffer = charWriteBuffer;
		int common = startidx;
		for (int minlen = Math.min(slen, olen); common < minlen; common++) {
			if (charbuffer[common] != otherstr.charAt(common)) {
				//found the first different char
				break;
			}
		}
		return common;
	}

	private int ensureCharWriteBuffer(String s) {
		int slen = s.length();
		ensureCharWriteBuffer(s, slen);
		return slen;
	}

	private void ensureCharWriteBuffer(String s, int slen) {
		if (charWriteBuffer.length < slen) {
			charWriteBuffer = new char[Math.max(charWriteBuffer.length * 2, slen)];
		}
		s.getChars(0, slen, charWriteBuffer, 0);
	}

	private void writeUtfData(int slen, boolean objwrite) throws IOException {
		final char[] charbuffer = charWriteBuffer;
		final DirectWritableDataOutputUnsyncByteArrayOutputStream out = this.out;

		int offset;
		if (isLowBytesChars(charbuffer, 0, slen)) {
			offset = out.ensureSpace((1 + MAX_RAWVARINT_SIZE) + slen);
			final byte[] buf = out.getBuffer();

			buf[offset++] = (byte) (objwrite ? C_OBJECT_UTF_LOWBYTES : C_UTF_LOWBYTES);
			offset = writeRawIntToBuf(slen, offset, buf);
			for (int i = 0; i < slen; i++) {
				buf[offset++] = (byte) charbuffer[i];
			}

		} else {
			offset = out.ensureSpace((1 + MAX_RAWVARINT_SIZE) + slen * 2);
			final byte[] buf = out.getBuffer();

			buf[offset++] = (byte) (objwrite ? C_OBJECT_UTF : C_UTF);
			offset = writeRawIntToBuf(slen, offset, buf);
			for (int i = 0; i < slen; i++) {
				char c = charbuffer[i];
				buf[offset++] = (byte) ((c >>> 8));
				buf[offset++] = (byte) (c);
			}
		}
		out.setCount(offset);
	}

	private void writePrefixedUtfData(UTFPrefixInfo prefixinfo, int slen, boolean objwrite) throws IOException {
		final int common = prefixinfo.common;

		final int writecount = slen - common;
		final DirectWritableDataOutputUnsyncByteArrayOutputStream out = this.out;
		char[] charbuffer = charWriteBuffer;
		int offset;
		if (isLowBytesChars(charbuffer, common, writecount)) {
			offset = out.ensureSpace((1 + MAX_RAWVARINT_SIZE * 3) + writecount);
			final byte[] buf = out.getBuffer();

			buf[offset++] = (byte) (objwrite ? C_OBJECT_UTF_PREFIXED_LOWBYTES : C_UTF_PREFIXED_LOWBYTES);
			offset = writeRawIntToBuf(prefixinfo.prefix.index, offset, buf);
			offset = writeRawIntToBuf(common, offset, buf);
			offset = writeRawIntToBuf(writecount, offset, buf);
			for (int i = 0; i < writecount; i++) {
				buf[offset++] = (byte) charbuffer[common + i];
			}
		} else {
			offset = out.ensureSpace((1 + MAX_RAWVARINT_SIZE * 3) + writecount * 2);
			final byte[] buf = out.getBuffer();

			buf[offset++] = (byte) (objwrite ? C_OBJECT_UTF_PREFIXED : C_UTF_PREFIXED);
			offset = writeRawIntToBuf(prefixinfo.prefix.index, offset, buf);
			offset = writeRawIntToBuf(common, offset, buf);
			offset = writeRawIntToBuf(writecount, offset, buf);
			for (int i = 0; i < writecount; i++) {
				char c = charbuffer[common + i];
				buf[offset++] = (byte) ((c >>> 8));
				buf[offset++] = (byte) (c);
			}
		}
		out.setCount(offset);
	}

	private void writeIndexObjectCommandWithCommandBase(int oidx, int idxcommandbase) {
		if ((oidx & 0xFFFF0000) != 0) {
			if ((oidx & 0xFF000000) != 0) {
				writeByteInt((byte) (idxcommandbase + 4), oidx);
			} else {
				writeByteTriInt((byte) (idxcommandbase + 3), oidx);
			}
		} else {
			if ((oidx & 0x0000FF00) != 0) {
				writeByteShort((byte) (idxcommandbase + 2), oidx);
			} else {
				writeMultiBytes((byte) (idxcommandbase + 1), (byte) oidx);
			}
		}
	}

	private void writeIndexObjectCommandWithCommandBaseAndRelative(int oidx, int idxcommandbase, int relativeindex) {
		if ((oidx & 0xFFFF0000) != 0) {
			if ((oidx & 0xFF000000) != 0) {
				writeByteInt((byte) (idxcommandbase + 4), oidx);
			} else {
				int relidx = relativeindex - oidx;
				if (relidx <= 0xFFFF) {
					//relative value fits in at most two bytes, write that instead
					if (relidx <= 0xFF) {
						//single byte relative
						writeMultiBytes((byte) (idxcommandbase + 5), (byte) relidx);
					} else {
						//two byte relative
						writeByteShort((byte) (idxcommandbase + 6), relidx);
					}
				} else {
					writeByteTriInt((byte) (idxcommandbase + 3), oidx);
				}
			}
		} else {
			if ((oidx & 0x0000FF00) != 0) {
				int relidx = relativeindex - oidx;
				if (relidx <= 0xFF) {
					//relative value fits in a single byte, write that instead
					writeMultiBytes((byte) (idxcommandbase + 5), (byte) relidx);
				} else {
					writeByteShort((byte) (idxcommandbase + 2), oidx);
				}
			} else {
				writeMultiBytes((byte) (idxcommandbase + 1), (byte) oidx);
			}
		}
	}

	private static final class InternedValueComputer<V> implements Function<Object, InternedValue<V>> {
		protected final V obj;

		protected boolean computed;

		public InternedValueComputer(V obj) {
			this.obj = obj;
		}

		@Override
		public InternedValue<V> apply(Object k) {
			computed = true;
			return new InternedValue<>(obj, -1);
		}
	}

	@Override
	public void writeObject(Object obj) throws IOException {
		if (obj == null) {
			out.writeByte(C_OBJECT_NULL);
			return;
		}
		if (obj instanceof String) {
			writeUTFImpl((String) obj, true);
			return;
		}
		Integer oidx = objectIndices.get(obj);
		if (oidx != null) {
			writeIndexObjectCommandWithCommandBaseAndRelative(oidx, C_OBJECT_IDX_BASE, objectIndices.size());
			return;
		}
		Class<? extends Object> objclass = obj.getClass();
		BuiltinValueWriterCache objwritercache = builtinValueWriters.get(objclass);
		if (objwritercache != null) {
			InternedValueComputer<Object> writer = new InternedValueComputer<>(obj);
			InternedValue<Object> internvalue = objwritercache.cache.computeIfAbsent(obj, writer);
			if (writer.computed) {
				//a newly added serialized object
				try {
					out.writeByte(C_OBJECT_VALUE);
					writeUTF(objclass.getName());
				} catch (Exception e) {
					// remove the object from the internalized map, as we failed to proceed with writing the value
					// (although this shouldn't fail, as we only write strings and primitives in this try-catch block.)
					objwritercache.cache.remove(obj, internvalue);
					throw e;
				}
				internvalue.index = addSerializedObject(obj);
				objwritercache.writer.accept(obj, ContentWriterObjectOutput.this);
				return;
			}
			//the value was already interned
			writeIndexObjectCommandWithCommandBaseAndRelative(internvalue.index, C_OBJECT_IDX_BASE,
					objectIndices.size());
			return;
		}
		if (objclass.isArray()) {
			writeArrayImplWithCommandImpl(obj, objclass);
			return;
		}
		if (objclass == Class.class) {
			writeTypeWithCommandImpl((Class<?>) obj);
			return;
		}
		if (ReflectUtils.isEnumOrEnumAnonymous(objclass)) {
			writeEnumWithCommandImpl(obj, objclass);
			return;
		}
		if (obj instanceof Externalizable) {
			if (Proxy.isProxyClass(objclass)) {
				writeProxyObjectWithCommand(obj);
				return;
			}
			writeExternalizableWithCommandImpl((Externalizable) obj, objclass);
			return;
		}
		if (obj instanceof ClassLoader) {
			writeClassLoaderWithCommandImpl((ClassLoader) obj);
			return;
		}
		@SuppressWarnings("unchecked")
		IOBiConsumer<Object, ContentWriterObjectOutput> serwriter = (IOBiConsumer<Object, ContentWriterObjectOutput>) SERIALIZABLE_CLASS_WRITERS
				.get(objclass);
		if (serwriter != null) {
			writeCustomSerializableWithCommand(obj, objclass, serwriter);
			return;
		}
		if (Proxy.isProxyClass(objclass)) {
			writeProxyObjectWithCommand(obj);
			return;
		}
		//do not warn for enumsets
		if (!EnumSet.class.isAssignableFrom(objclass) && !(obj instanceof Throwable) && warnedClasses.add(objclass)) {
			//shouldnt warn in case of serializing a throwable instance, or enum set
			if (TestFlag.ENABLED) {
				TestFlag.metric().serializationWarning(objclass.getName());
			}
			InternalBuildTraceImpl.serializationWarningMessage(
					"Object with class: " + objclass.getName() + " is not Externalizable. Serializing instead.");
		}

		writeSerializableWithCommand(obj);
	}

	@Override
	public void write(int b) throws IOException {
		writeMultiBytes((byte) C_BYTE, (byte) b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if (len == 0) {
			return;
		}
		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		out.writeByte(C_BYTEARRAY);
		writeInt(len);
		out.write(b, off, len);
	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public void close() throws IOException {
		//call flush (possibly subclassed) so data is drained
		flush();
	}

	public void drainTo(OutputStream os) throws IOException {
		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		out.writeTo(os);
		out.reset();
	}

	public void drainTo(ByteSink os) throws IOException {
		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		out.writeTo(os);
		out.reset();
	}

	public ByteArrayRegion drainToBytes() {
		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		ByteArrayRegion result = out.toByteArrayRegion();
		out.reset();
		return result;
	}

	public void reset() {
		out.reset();
	}

	private void writeProxyObjectWithCommand(Object proxy) throws IOException {
		addSerializedObject(proxy);

		out.writeByte(C_OBJECT_PROXY);

		Class<? extends Object> proxyclass = proxy.getClass();
		writeExternalClassLoader(proxyclass.getClassLoader());
		Class<?>[] interfaces = proxyclass.getInterfaces();
		writeInt(interfaces.length);
		for (int i = 0; i < interfaces.length; i++) {
			writeTypeWithCommandOrIdx(interfaces[i]);
		}
		InvocationHandler ih = Proxy.getInvocationHandler(proxy);
		writeObject(ih);
	}

	private int addSerializedObject(Object obj) throws SerializationProtocolException {
		IdentityHashMap<Object, Integer> indices = this.objectIndices;
		int idx = indices.size();
		Integer prev = indices.putIfAbsent(obj, idx);
		if (prev != null) {
			throw new SerializationProtocolException("Internal Error: Object is already present: " + obj
					+ " with index: " + prev + " for new index: " + idx);
		}
		return idx;
	}

	private void writeEnumWithCommandImpl(Object obj, Class<? extends Object> objclass) throws IOException {
		if (objclass.isAnonymousClass()) {
			objclass = objclass.getSuperclass();
		}

		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		out.writeByte(C_OBJECT_ENUM);
		writeTypeWithCommandOrIdx(objclass);
		Enum<?> e = (Enum<?>) obj;
		out.writeStringLengthChars(e.name());
		addSerializedObject(obj);
	}

	private void writeExternalizableWithCommandImpl(Externalizable obj, Class<? extends Object> objclass)
			throws IOException {
		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		Optional<Map<Object, Integer>> valtype = valueTypeInternMaps.computeIfAbsent(objclass,
				objc -> objc.isAnnotationPresent(ValueType.class) ? Optional.of(new HashMap<>()) : Optional.empty());
		Map<Object, Integer> valinternmap = valtype.orElse(null);

		if (valinternmap != null) {
			//if this is a value type, check if it's interned, and write the index instead
			Integer internindex = valinternmap.get(obj);
			if (internindex != null) {
				writeIndexObjectCommandWithCommandBaseAndRelative(internindex, C_OBJECT_IDX_BASE, objectIndices.size());
				return;
			}
		}

		int startsize = out.size();
		out.writeByte(C_OBJECT_EXTERNALIZABLE_4);
		writeTypeWithCommandOrIdx(objclass);

		int objidx = addSerializedObject(obj);

		int sizeintpos = out.size();
		//length written later
		out.writeInt(0);
		int lencalcstartpos = out.size();
		try {
			obj.writeExternal(this);
		} catch (Exception e) {
			out.replaceByte(C_OBJECT_EXTERNALIZABLE_ERROR, startsize);
			int c = out.size() - lencalcstartpos;
			out.replaceInt(c, sizeintpos);

			//don't need to handle the object table corruption, as the output is pre-read when deserialized
			throw new ObjectWriteException("Failed to write Externalizable object. (" + obj.getClass().getName() + ")",
					e);
		} catch (Throwable e) {
			out.replaceByte(C_OBJECT_EXTERNALIZABLE_ERROR, startsize);
			int c = out.size() - lencalcstartpos;
			out.replaceInt(c, sizeintpos);
			throw e;
		}
		int c = out.size() - lencalcstartpos;
		if (c <= 255) {
			//use a single byte length field to reduce the many unnecessary 00 00 00 bytes in the serialized stream
			//as in case of lot of small externalizable objects, it can take a lot of space
			//so the following:
			//    LENMSB LENB2 LENB3 LENLSB DATA DATA DATA...
			//becomes
			//    LEN DATA DATA DATA...
			out.replaceByte(c, sizeintpos);
			out.replaceByte(C_OBJECT_EXTERNALIZABLE_1, startsize);

			//move the serialized data
			byte[] buf = out.getBuffer();
			System.arraycopy(buf, lencalcstartpos, buf, sizeintpos + 1, c);

			//remove the extra 3 bytes from the end
			out.reduceSize(out.size() - 3);
		} else {
			out.replaceInt(c, sizeintpos);
		}
		if (valinternmap != null) {
			valinternmap.putIfAbsent(obj, objidx);
		}
	}

	private void writeArrayImplWithCommandImpl(Object obj, Class<? extends Object> objclass) throws IOException {
		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		int startsize = out.size();
		out.writeByte(C_OBJECT_ARRAY);
		Class<?> componenttype = objclass.getComponentType();
		writeTypeWithCommandOrIdx(objclass);
		addSerializedObject(obj);
		int len = Array.getLength(obj);
		int lenpos = out.size();
		out.writeInt(len);
		int i = 0;
		try {
			@SuppressWarnings("unchecked")
			BiConsumer<? super DataOutputUnsyncByteArrayOutputStream, Object> writer = (BiConsumer<? super DataOutputUnsyncByteArrayOutputStream, Object>) ARRAY_WRITERS
					.get(componenttype);
			if (writer != null) {
				writer.accept(out, obj);
			} else {
				Object[] oarray = (Object[]) obj;
				for (; i < len; i++) {
					try {
						writeObject(oarray[i]);
					} catch (Exception e) {
						throw new ObjectWriteException("Failed to write array element at index: " + i, e);
					}
				}
			}
		} catch (Exception e) {
			out.replaceByte(C_OBJECT_ARRAY_ERROR, startsize);
			out.replaceInt(i, lenpos);
			throw new ObjectWriteException("Failed to write array object.", e);
		}
	}

	private Integer writeTypeWithCommandOrIdx(Class<?> objclass) throws IOException {
		Integer typeidx = objectIndices.get(objclass);
		if (typeidx == null) {
			writeTypeWithCommandImpl(objclass);
		} else {
			writeIndexObjectCommandWithCommandBaseAndRelative(typeidx, C_OBJECT_IDX_BASE, objectIndices.size());
		}
		return typeidx;
	}

	private void writeTypeWithCommandImpl(Class<?> objclass) throws IOException {
		out.writeByte(C_OBJECT_TYPE);
		writeExternalClass(objclass);
		addSerializedObject(objclass);
	}

	private void writeExternalClass(Class<?> clazz) throws IOException {
		writeExternalClassLoader(clazz.getClassLoader());

		String className = clazz.getName();
		writeUTF(className);
	}

	private void writeClassLoaderWithCommandImpl(ClassLoader cl) throws IOException {
		out.writeByte(C_OBJECT_CLASSLOADER);
		writeExternalClassLoader(cl);
	}

	private void writeExternalClassLoader(ClassLoader cl) throws IOException {
		String classLoaderResolverId = registry.getClassLoaderIdentifier(cl);
		if (classLoaderResolverId == null) {
			classLoaderResolverId = "";
		}
		writeUTF(classLoaderResolverId);
	}

	private void writeCustomSerializableWithCommand(Object obj, Class<?> clazz,
			IOBiConsumer<Object, ContentWriterObjectOutput> serwriter) throws IOException {
		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		int startsize = out.size();
		out.writeByte(C_OBJECT_CUSTOM_SERIALIZABLE);
		writeTypeWithCommandOrIdx(clazz);
		addSerializedObject(obj);

		int sizeintpos = out.size();
		//length filled later
		out.writeInt(0);
		int lencalcstartpos = out.size();
		try {
			serwriter.accept(obj, this);
		} catch (Exception e) {
			out.replaceByte(C_OBJECT_CUSTOM_SERIALIZABLE_ERROR, startsize);
			//don't need to handle the object table corruption, as the output is pre-read when deserialized
			throw new ObjectWriteException("Failed to write object.", e);
		} finally {
			int c = out.size() - lencalcstartpos;
			out.replaceInt(c, sizeintpos);
		}
	}

	private void writeSerializableWithCommand(Object obj) throws IOException {
		final DataOutputUnsyncByteArrayOutputStream out = this.out;
		addSerializedObject(obj);
		int startsize = out.size();
		out.writeByte(C_OBJECT_SERIALIZABLE);
		int lenintpos = out.size();
		//length filled later
		out.writeInt(0);
		int datacountstartpos = out.size();
		try (ObjectOutputStream oos = new ClassLoaderResolverObjectOutputStream(registry, out)) {
			oos.writeObject(obj);
		} catch (Exception e) {
			//failed to write, undo writings
			out.replaceByte(C_OBJECT_SERIALIZABLE_ERROR, startsize);
			throw new ObjectWriteException("Failed to write Serializable object. (" + obj.getClass().getName() + ")",
					e);
		} finally {
			int c = out.size() - datacountstartpos;
			out.replaceInt(c, lenintpos);
		}
	}

	private static final class InternedValue<T> {
		protected final T value;
		protected int index;

		public InternedValue(T value, int index) {
			this.value = value;
			this.index = index;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder(getClass().getSimpleName());
			builder.append("[value=");
			builder.append(value);
			builder.append(", index=");
			builder.append(index);
			builder.append("]");
			return builder.toString();
		}
	}

	private static final class UTFPrefixInfo {
		protected final InternedValue<String> prefix;
		protected final int common;

		public UTFPrefixInfo(InternedValue<String> prefix, int common) {
			this.prefix = prefix;
			this.common = common;
		}
	}

	private static final class DirectWritableDataOutputUnsyncByteArrayOutputStream
			extends DataOutputUnsyncByteArrayOutputStream {
		public DirectWritableDataOutputUnsyncByteArrayOutputStream() {
			super();
		}

		public DirectWritableDataOutputUnsyncByteArrayOutputStream(int size) throws NegativeArraySizeException {
			super(size);
		}

		public void increaseCount(int count) {
			this.count += count;
		}

		public void setCount(int count) {
			this.count = count;
		}

		/**
		 * @param bytecount
		 *            The additional bytes needed.
		 * @return The current count in the buffer.
		 */
		public int ensureSpace(int bytecount) {
			int cnt = this.count;
			ensureCapacity(cnt + bytecount);
			return cnt;
		}
	}

	private static final class BuiltinValueWriterCache {
		IOBiConsumer<Object, ContentWriterObjectOutput> writer;
		Map<Object, InternedValue<Object>> cache = new HashMap<>();

		public BuiltinValueWriterCache(IOBiConsumer<Object, ContentWriterObjectOutput> writer) {
			this.writer = writer;
		}
	}

}