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
import java.util.function.Consumer;

import saker.build.file.path.SakerPath;
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
import testing.saker.build.flag.TestFlag;

public class ContentWriterObjectOutput implements ObjectOutput {
	static final int C_BYTE = 1;
	static final int C_CHAR = 2;
	static final int C_SHORT = 3;
	static final int C_INT = 4;
	static final int C_LONG = 5;
	static final int C_FLOAT = 6;
	static final int C_DOUBLE = 7;
	static final int C_BOOLEAN = 8;
	static final int C_UTF = 9;
	static final int C_CHARS = 10;
	static final int C_BYTEARRAY = 11;
	static final int C_OBJECT_CLASSLOADER = 12;
	static final int C_OBJECT_ARRAY = 13;
	static final int C_OBJECT_EXTERNALIZABLE = 14;
	static final int C_OBJECT_IDX = 15;
	static final int C_OBJECT_NULL = 16;
	static final int C_OBJECT_SERIALIZABLE = 17;
	static final int C_OBJECT_TYPE = 18;
	static final int C_OBJECT_VALUE = 19;
	static final int C_OBJECT_ENUM = 20;
	static final int C_OBJECT_ARRAY_ERROR = 21;
	static final int C_OBJECT_EXTERNALIZABLE_ERROR = 22;
	static final int C_OBJECT_SERIALIZABLE_ERROR = 23;
	static final int C_OBJECT_CUSTOM_SERIALIZABLE = 24;
	static final int C_OBJECT_CUSTOM_SERIALIZABLE_ERROR = 25;
	static final int C_UTF_IDX = 26;
	static final int C_UTF_LOWBYTES = 27;
	static final int C_INT_3 = 28;
	static final int C_INT_2 = 29;
	static final int C_INT_1 = 30;
	static final int C_OBJECT_UTF = 31;
	static final int C_OBJECT_IDX_3 = 32;
	static final int C_OBJECT_IDX_2 = 33;
	static final int C_OBJECT_IDX_1 = 34;
	static final int C_OBJECT_PROXY = 35;
	static final int C_UTF_IDX_3 = 36;
	static final int C_UTF_IDX_2 = 37;
	static final int C_UTF_IDX_1 = 38;
	static final int C_LONG_2 = 39;
	static final int C_LONG_4 = 40;
	static final int C_LONG_6 = 41;
	static final int C_INT_F_3 = 42;
	static final int C_INT_F_2 = 43;
	static final int C_INT_F_1 = 44;
	static final int C_LONG_F_6 = 45;
	static final int C_LONG_F_4 = 46;
	static final int C_LONG_F_2 = 47;
	static final int C_INT_ZERO = 48;
	static final int C_INT_NEGATIVE_ONE = 49;
	static final int C_LONG_ZERO = 50;
	static final int C_LONG_NEGATIVE_ONE = 51;
	static final int C_UTF_PREFIXED = 52;
	static final int C_UTF_PREFIXED_LOWBYTES = 53;
	static final int C_INT_ONE = 54;

	static final int C_OBJECT_UTF_IDX = 55;
	static final int C_OBJECT_UTF_IDX_1 = 56;
	static final int C_OBJECT_UTF_IDX_2 = 57;
	static final int C_OBJECT_UTF_IDX_3 = 58;
	static final int C_OBJECT_UTF_LOWBYTES = 59;
	static final int C_OBJECT_UTF_PREFIXED = 60;
	static final int C_OBJECT_UTF_PREFIXED_LOWBYTES = 61;

	static final int C_MAX_COMMAND_VALUE = 61;

	private static final int UTF_PREFIX_MIN_LEN = 8;

	static String getCommandTypeInfo(int cmd) {
		switch (cmd) {
			case C_BYTE:
			case C_BYTEARRAY:
				return "byte";
			case C_CHAR:
				return "char";
			case C_SHORT:
				return "short";
			case C_INT:
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
			case C_LONG:
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
			case C_BOOLEAN:
				return "boolean";
			case C_UTF:
			case C_UTF_IDX:
			case C_UTF_IDX_1:
			case C_UTF_IDX_2:
			case C_UTF_IDX_3:
			case C_UTF_LOWBYTES:
			case C_UTF_PREFIXED:
			case C_UTF_PREFIXED_LOWBYTES:
				return "UTF";
			case C_CHARS:
				return "char";
			case C_OBJECT_IDX:
			case C_OBJECT_IDX_3:
			case C_OBJECT_IDX_2:
			case C_OBJECT_IDX_1:
			case C_OBJECT_VALUE:
			case C_OBJECT_CUSTOM_SERIALIZABLE:
			case C_OBJECT_CUSTOM_SERIALIZABLE_ERROR:
				return "object";
			case C_OBJECT_UTF:
			case C_OBJECT_UTF_IDX:
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
			case C_OBJECT_EXTERNALIZABLE:
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
		SERIALIZABLE_CLASS_WRITERS.put(TreeSet.class,
				(IOBiConsumer<TreeSet<?>, ContentWriterObjectOutput>) (v, writer) -> {
					writer.writeObject(v.comparator());
					SerialUtils.writeExternalCollection(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(TreeSet.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			SerializedObject<TreeSet<Object>> serialobj;
			try {
				@SuppressWarnings("unchecked")
				Comparator<Object> comparator = (Comparator<Object>) reader.readObject();
				TreeSet<Object> resset = new TreeSet<>(comparator);
				serialobj = new PresentSerializedObject<>(resset);
				reader.setSerializedObject(idx, serialobj);
				SerialUtils.readExternalCollection(resset, reader);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(() -> new ObjectReadException("Failed to read TreeSet.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});
		SERIALIZABLE_CLASS_WRITERS.put(ConcurrentSkipListSet.class,
				(IOBiConsumer<ConcurrentSkipListSet<?>, ContentWriterObjectOutput>) (v, writer) -> {
					writer.writeObject(v.comparator());
					SerialUtils.writeExternalCollection(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(ConcurrentSkipListSet.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			SerializedObject<ConcurrentSkipListSet<Object>> serialobj;
			try {
				@SuppressWarnings("unchecked")
				Comparator<Object> comparator = (Comparator<Object>) reader.readObject();
				ConcurrentSkipListSet<Object> resset = new ConcurrentSkipListSet<>(comparator);
				serialobj = new PresentSerializedObject<>(resset);
				reader.setSerializedObject(idx, serialobj);
				SerialUtils.readExternalCollection(resset, reader);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read ConcurrentSkipListSet.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});
		SERIALIZABLE_CLASS_WRITERS.put(TreeMap.class,
				(IOBiConsumer<TreeMap<?, ?>, ContentWriterObjectOutput>) (v, writer) -> {
					writer.writeObject(v.comparator());
					SerialUtils.writeExternalMap(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(TreeMap.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			SerializedObject<TreeMap<Object, Object>> serialobj;
			try {
				@SuppressWarnings("unchecked")
				Comparator<Object> comparator = (Comparator<Object>) reader.readObject();
				TreeMap<Object, Object> resmap = new TreeMap<>(comparator);
				serialobj = new PresentSerializedObject<>(resmap);
				reader.setSerializedObject(idx, serialobj);
				SerialUtils.readExternalMap(resmap, reader);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(() -> new ObjectReadException("Failed to read TreeMap.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});
		SERIALIZABLE_CLASS_WRITERS.put(ConcurrentSkipListMap.class,
				(IOBiConsumer<ConcurrentSkipListMap<?, ?>, ContentWriterObjectOutput>) (v, writer) -> {
					writer.writeObject(v.comparator());
					SerialUtils.writeExternalMap(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(ConcurrentSkipListMap.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			SerializedObject<ConcurrentSkipListMap<Object, Object>> serialobj;
			try {
				@SuppressWarnings("unchecked")
				Comparator<Object> comparator = (Comparator<Object>) reader.readObject();
				ConcurrentSkipListMap<Object, Object> resmap = new ConcurrentSkipListMap<>(comparator);
				serialobj = new PresentSerializedObject<>(resmap);
				reader.setSerializedObject(idx, serialobj);
				SerialUtils.readExternalMap(resmap, reader);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read ConcurrentSkipListMap.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});
		SERIALIZABLE_CLASS_WRITERS.put(HashMap.class,
				(IOBiConsumer<HashMap<?, ?>, ContentWriterObjectOutput>) (v, writer) -> {
					SerialUtils.writeExternalMap(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(HashMap.class, reader -> {
			HashMap<Object, Object> resset = new HashMap<>();
			SerializedObject<HashMap<Object, Object>> serialobj = new PresentSerializedObject<>(resset);
			int idx = reader.addSerializedObject(serialobj);
			try {
				SerialUtils.readExternalMap(resset, reader);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(() -> new ObjectReadException("Failed to read HashMap.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});
		//identity hash map is not supported, due to the value internalization
		SERIALIZABLE_CLASS_WRITERS.put(LinkedHashMap.class,
				(IOBiConsumer<LinkedHashMap<?, ?>, ContentWriterObjectOutput>) (v, writer) -> {
					SerialUtils.writeExternalMap(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(LinkedHashMap.class, reader -> {
			LinkedHashMap<Object, Object> resset = new LinkedHashMap<>();
			SerializedObject<LinkedHashMap<Object, Object>> serialobj = new PresentSerializedObject<>(resset);
			int idx = reader.addSerializedObject(serialobj);
			try {
				SerialUtils.readExternalMap(resset, reader);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read LinkedHashMap.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});
		SERIALIZABLE_CLASS_WRITERS.put(ConcurrentHashMap.class,
				(IOBiConsumer<ConcurrentHashMap<?, ?>, ContentWriterObjectOutput>) (v, writer) -> {
					SerialUtils.writeExternalMap(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(ConcurrentHashMap.class, reader -> {
			ConcurrentHashMap<Object, Object> resset = new ConcurrentHashMap<>();
			SerializedObject<ConcurrentHashMap<Object, Object>> serialobj = new PresentSerializedObject<>(resset);
			int idx = reader.addSerializedObject(serialobj);
			try {
				SerialUtils.readExternalMap(resset, reader);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read ConcurrentHashMap.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});
		SERIALIZABLE_CLASS_WRITERS.put(HashSet.class,
				(IOBiConsumer<HashSet<?>, ContentWriterObjectOutput>) (v, writer) -> {
					SerialUtils.writeExternalCollection(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(HashSet.class, reader -> {
			HashSet<Object> resset = new HashSet<>();
			SerializedObject<HashSet<Object>> serialobj = new PresentSerializedObject<>(resset);
			int idx = reader.addSerializedObject(serialobj);
			try {
				SerialUtils.readExternalCollection(resset, reader);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(() -> new ObjectReadException("Failed to read HashSet.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});
		SERIALIZABLE_CLASS_WRITERS.put(LinkedHashSet.class,
				(IOBiConsumer<LinkedHashSet<?>, ContentWriterObjectOutput>) (v, writer) -> {
					SerialUtils.writeExternalCollection(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(LinkedHashSet.class, reader -> {
			LinkedHashSet<Object> resset = new LinkedHashSet<>();
			SerializedObject<LinkedHashSet<Object>> serialobj = new PresentSerializedObject<>(resset);
			int idx = reader.addSerializedObject(serialobj);
			try {
				SerialUtils.readExternalCollection(resset, reader);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read LinkedHashSet.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});
		Class<?> concurrenthashkeysetviewclass = ConcurrentHashMap.newKeySet().getClass();
		SERIALIZABLE_CLASS_WRITERS.put(concurrenthashkeysetviewclass,
				(IOBiConsumer<Set<?>, ContentWriterObjectOutput>) (v, writer) -> {
					SerialUtils.writeExternalCollection(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(concurrenthashkeysetviewclass, reader -> {
			Set<Object> resset = ConcurrentHashMap.newKeySet();
			SerializedObject<Set<Object>> serialobj = new PresentSerializedObject<>(resset);
			int idx = reader.addSerializedObject(serialobj);
			try {
				SerialUtils.readExternalCollection(resset, reader);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read ConcurrentHashMap.KeySetView.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});
		SERIALIZABLE_CLASS_WRITERS.put(ArrayList.class,
				(IOBiConsumer<ArrayList<?>, ContentWriterObjectOutput>) (v, writer) -> {
					SerialUtils.writeExternalCollection(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(ArrayList.class, reader -> {
			ArrayList<Object> reslist = new ArrayList<>();
			SerializedObject<ArrayList<Object>> serialobj = new PresentSerializedObject<>(reslist);
			int idx = reader.addSerializedObject(serialobj);
			try {
				SerialUtils.readExternalCollection(reslist, reader);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(() -> new ObjectReadException("Failed to read ArrayList.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});
		SERIALIZABLE_CLASS_WRITERS.put(LinkedList.class,
				(IOBiConsumer<LinkedList<?>, ContentWriterObjectOutput>) (v, writer) -> {
					SerialUtils.writeExternalCollection(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(LinkedList.class, reader -> {
			LinkedList<Object> reslist = new LinkedList<>();
			SerializedObject<LinkedList<Object>> serialobj = new PresentSerializedObject<>(reslist);
			int idx = reader.addSerializedObject(serialobj);
			try {
				SerialUtils.readExternalCollection(reslist, reader);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read LinkedList.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});
		SERIALIZABLE_CLASS_WRITERS.put(CopyOnWriteArrayList.class,
				(IOBiConsumer<CopyOnWriteArrayList<?>, ContentWriterObjectOutput>) (v, writer) -> {
					SerialUtils.writeExternalCollection(writer, v);
				});
		SERIALIZABLE_CLASS_READERS.put(CopyOnWriteArrayList.class, reader -> {
			CopyOnWriteArrayList<Object> reslist = new CopyOnWriteArrayList<>();
			SerializedObject<CopyOnWriteArrayList<Object>> serialobj = new PresentSerializedObject<>(reslist);
			int idx = reader.addSerializedObject(serialobj);
			try {
				Object[] readobjects = SerialUtils.readExternalArray(reader, Object[]::new);
				ObjectUtils.addAll(reslist, readobjects);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read CopyOnWriteArrayList.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});

		//EnumSet handling is omitted, as it only contains enums
		//    and can be serialized with the default mechanism
		SERIALIZABLE_CLASS_WRITERS.put(EnumMap.class,
				((IOBiConsumer<EnumMap<?, ?>, ContentWriterObjectOutput>) (v, writer) -> {
					Class<? extends Enum<?>> enumtype = ObjectUtils.getEnumMapEnumType(v);
					writer.writeObject(enumtype);
					SerialUtils.writeExternalMap(writer, v);
				}));
		SERIALIZABLE_CLASS_READERS.put(EnumMap.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			SerializedObject<?> serialobj;
			try {
				@SuppressWarnings("unchecked")
				Class<? extends Enum<?>> entype = (Class<? extends Enum<?>>) reader.readObject();
				@SuppressWarnings({ "rawtypes", "unchecked" })
				EnumMap<?, ?> result = new EnumMap(entype);
				serialobj = new PresentSerializedObject<>(result);
				reader.setSerializedObject(idx, serialobj);
				SerialUtils.readExternalMap(result, reader);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(() -> new ObjectReadException("Failed to read EnumMap.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
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
		SERIALIZABLE_CLASS_WRITERS.put(singletonsetclass,
				(IOBiConsumer<Set<?>, ContentWriterObjectOutput>) (v, writer) -> {
					writer.writeObject(v.iterator().next());
				});
		SERIALIZABLE_CLASS_READERS.put(singletonsetclass, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			SerializedObject<Set<Object>> serialobj;
			try {
				Object obj = reader.readObject();
				Set<Object> result = Collections.singleton(obj);
				serialobj = new PresentSerializedObject<>(result);
				reader.setSerializedObject(idx, serialobj);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read singleton set.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});

		List<Object> singletonlist = Collections.singletonList(singletontestelement);
		Class<?> singletonlistclass = singletonlist.getClass();
		SERIALIZABLE_CLASS_WRITERS.put(singletonlistclass,
				(IOBiConsumer<List<?>, ContentWriterObjectOutput>) (v, writer) -> {
					writer.writeObject(v.get(0));
				});
		SERIALIZABLE_CLASS_READERS.put(singletonlistclass, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			SerializedObject<List<Object>> serialobj;
			try {
				Object obj = reader.readObject();
				List<Object> result = Collections.singletonList(obj);
				serialobj = new PresentSerializedObject<>(result);
				reader.setSerializedObject(idx, serialobj);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read singleton list.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});

		Map<Object, Object> singletonmap = Collections.singletonMap(singletontestelement, singletontestelement);
		Class<?> singletonmapclass = singletonmap.getClass();
		SERIALIZABLE_CLASS_WRITERS.put(singletonmapclass,
				(IOBiConsumer<Map<?, ?>, ContentWriterObjectOutput>) (v, writer) -> {
					Entry<?, ?> entry = v.entrySet().iterator().next();
					writer.writeObject(entry.getKey());
					writer.writeObject(entry.getValue());
				});
		SERIALIZABLE_CLASS_READERS.put(singletonmapclass, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			SerializedObject<Map<Object, Object>> serialobj;
			try {
				Object key = reader.readObject();
				Object value = reader.readObject();
				Map<Object, Object> result = Collections.singletonMap(key, value);
				serialobj = new PresentSerializedObject<>(result);
				reader.setSerializedObject(idx, serialobj);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new ObjectReadException("Failed to read singleton map.", e));
				reader.setSerializedObject(idx, serialobj);
			}
			return serialobj.get();
		});

		List<Object> aslist = Arrays.asList(singletontestelement, singletontestelement, singletontestelement);
		Class<?> astlistclass = aslist.getClass();
		SERIALIZABLE_CLASS_WRITERS.put(astlistclass, (IOBiConsumer<List<?>, ContentWriterObjectOutput>) (v, writer) -> {
			int size = v.size();
			writer.writeInt(size);
			for (int i = 0; i < size; i++) {
				writer.writeObject(v.get(i));
			}
		});
		SERIALIZABLE_CLASS_READERS.put(astlistclass, reader -> {
			SerializedObject<List<Object>> serialobj;
			int size;
			try {
				size = reader.readInt();
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(() -> new ObjectReadException("Failed to read elements.", e));
				reader.addSerializedObject(serialobj);
				return serialobj.get();
			}
			Object[] array = new Object[size];
			List<Object> result = Arrays.asList(array);
			serialobj = new PresentSerializedObject<>(result);
			int serialidx = reader.addSerializedObject(serialobj);
			try {
				for (int i = 0; i < size; i++) {
					array[i] = reader.readObject();
				}
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(() -> new ObjectReadException("Failed to read elements.", e));
				reader.setSerializedObject(serialidx, serialobj);
			}
			return serialobj.get();
		});

		//need custom serialization for the reverseorder comparator as it is not backed by enum
		Comparator<Object> reverseordercomparator = Collections.reverseOrder();
		@SuppressWarnings("rawtypes")
		Class<? extends Comparator> reverseorderclass = reverseordercomparator.getClass();
		PresentSerializedObject<Comparator<Object>> reverseorderserializedobject = new PresentSerializedObject<>(
				reverseordercomparator);
		SERIALIZABLE_CLASS_WRITERS.put(reverseorderclass, (IOBiConsumer<?, ContentWriterObjectOutput>) (v, writer) -> {
		});
		SERIALIZABLE_CLASS_READERS.put(reverseorderclass, reader -> {
			reader.addSerializedObject(reverseorderserializedobject);
			return reverseordercomparator;
		});

		SERIALIZABLE_CLASS_WRITERS.put(Method.class, (IOBiConsumer<Method, ContentWriterObjectOutput>) (v, writer) -> {
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
			SerializedObject<Method> serialobj;
			try {
				Class<?> declaringclass = (Class<?>) reader.readObject();
				String name = reader.readUTF();
				int ptlen = reader.readInt();
				Class<?>[] paramtypes = new Class<?>[ptlen];
				for (int i = 0; i < paramtypes.length; i++) {
					paramtypes[i] = (Class<?>) reader.readObject();
				}
				Method result = declaringclass.getDeclaredMethod(name, paramtypes);
				serialobj = new PresentSerializedObject<>(result);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationReflectionException("Failed to read Method.", e));
			}
			reader.setSerializedObject(idx, serialobj);
			return serialobj.get();
		});
		SERIALIZABLE_CLASS_WRITERS.put(Constructor.class,
				(IOBiConsumer<Constructor<?>, ContentWriterObjectOutput>) (v, writer) -> {
					writer.writeObject(v.getDeclaringClass());
					Class<?>[] paramtypes = v.getParameterTypes();
					writer.writeInt(paramtypes.length);
					for (int i = 0; i < paramtypes.length; i++) {
						writer.writeObject(paramtypes[i]);
					}
				});
		SERIALIZABLE_CLASS_READERS.put(Constructor.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			SerializedObject<Constructor<?>> serialobj;
			try {
				Class<?> declaringclass = (Class<?>) reader.readObject();
				int ptlen = reader.readInt();
				Class<?>[] paramtypes = new Class<?>[ptlen];
				for (int i = 0; i < paramtypes.length; i++) {
					paramtypes[i] = (Class<?>) reader.readObject();
				}
				Constructor<?> result = declaringclass.getDeclaredConstructor(paramtypes);
				serialobj = new PresentSerializedObject<>(result);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationReflectionException("Failed to read Constructor.", e));
			}
			reader.setSerializedObject(idx, serialobj);
			return serialobj.get();
		});
		SERIALIZABLE_CLASS_WRITERS.put(Field.class, (IOBiConsumer<Field, ContentWriterObjectOutput>) (v, writer) -> {
			writer.writeObject(v.getDeclaringClass());
			writer.writeUTF(v.getName());
		});
		SERIALIZABLE_CLASS_READERS.put(Field.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			SerializedObject<Field> serialobj;
			try {
				Class<?> declaringclass = (Class<?>) reader.readObject();
				String name = reader.readUTF();
				Field result = declaringclass.getDeclaredField(name);
				serialobj = new PresentSerializedObject<>(result);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationReflectionException("Failed to read Field.", e));
			}
			reader.setSerializedObject(idx, serialobj);
			return serialobj.get();
		});

		SERIALIZABLE_CLASS_WRITERS.put(Optional.class,
				(IOBiConsumer<Optional<?>, ContentWriterObjectOutput>) (v, writer) -> {
					writer.writeObject(v.orElse(null));
				});
		SERIALIZABLE_CLASS_READERS.put(Optional.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			SerializedObject<Optional<?>> serialobj;
			try {
				Object obj = reader.readObject();
				serialobj = new PresentSerializedObject<>(Optional.ofNullable(obj));
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationReflectionException("Failed to read Optional.", e));
			}
			reader.setSerializedObject(idx, serialobj);
			return serialobj.get();
		});
	}

	private static void addEmptyCollectionReaderWriter(Object emptycoll) {
		Class<?> emptycollclass = emptycoll.getClass();
		//instantiate this outside of the lambdas, so they are reused
		PresentSerializedObject<Object> collpresentobject = new PresentSerializedObject<>(emptycoll);
		SERIALIZABLE_CLASS_WRITERS.put(emptycollclass, (v, writer) -> {
		});
		SERIALIZABLE_CLASS_READERS.put(emptycollclass, reader -> {
			reader.addSerializedObject(collpresentobject);
			return emptycoll;
		});
	}

	static final Map<Class<?>, IOBiConsumer<?, ContentWriterObjectOutput>> VALUE_CLASS_WRITERS = new HashMap<>();
	static final Map<Class<?>, ObjectReaderFunction<ContentReaderObjectInput, ?>> VALUE_CLASS_READERS = new HashMap<>();
	static {
//		VALUE_CLASS_WRITERS.put(String.class,
//				(IOBiConsumer<String, ContentWriterObjectOutput>) (v, writer) -> writer.out.writeStringLengthChars(v));
//		VALUE_CLASS_READERS.put(String.class, reader -> {
//			SerializedObject<String> serialobj;
//			try {
//				String res = DataInputUnsyncByteArrayInputStream.readStringLengthChars(reader.state.in);
//				serialobj = new PresentSerializedObject<>(res);
//			} catch (Exception e) {
//				serialobj = new FailedSerializedObject<>(
//						() -> new SerializationProtocolException("Failed to read String.", e));
//			}
//			reader.addSerializedObject(serialobj);
//			return serialobj.get();
//		});

		VALUE_CLASS_WRITERS.put(UUID.class,
				(IOBiConsumer<UUID, ContentWriterObjectOutput>) (v, writer) -> writer.out.writeUTF(v.toString()));
		VALUE_CLASS_READERS.put(UUID.class, reader -> {
			SerializedObject<UUID> serialobj;
			try {
				UUID res = UUID.fromString(reader.state.in.readUTF());
				serialobj = new PresentSerializedObject<>(res);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationProtocolException("Failed to read UUID.", e));
			}
			reader.addSerializedObject(serialobj);
			return serialobj.get();
		});
		VALUE_CLASS_WRITERS.put(Date.class,
				(IOBiConsumer<Date, ContentWriterObjectOutput>) (v, writer) -> writer.out.writeLong(v.getTime()));
		VALUE_CLASS_READERS.put(Date.class, reader -> {
			SerializedObject<Date> serialobj;
			try {
				long time = reader.state.in.readLong();
				serialobj = new PresentSerializedObject<>(new Date(time));
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationProtocolException("Failed to read Date.", e));
			}
			reader.addSerializedObject(serialobj);
			return serialobj.get();
		});

		VALUE_CLASS_WRITERS.put(Byte.class,
				(IOBiConsumer<Byte, ContentWriterObjectOutput>) (v, writer) -> writer.out.writeByte(v));
		VALUE_CLASS_READERS.put(Byte.class, reader -> {
			SerializedObject<Byte> serialobj;
			try {
				Byte res = reader.state.in.readByte();
				serialobj = new PresentSerializedObject<>(res);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationProtocolException("Failed to read Byte.", e));
			}
			reader.addSerializedObject(serialobj);
			return serialobj.get();
		});
		VALUE_CLASS_WRITERS.put(Short.class,
				(IOBiConsumer<Short, ContentWriterObjectOutput>) (v, writer) -> writer.out.writeShort(v));
		VALUE_CLASS_READERS.put(Short.class, reader -> {
			SerializedObject<Short> serialobj;
			try {
				Short res = reader.state.in.readShort();
				serialobj = new PresentSerializedObject<>(res);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationProtocolException("Failed to read Short.", e));
			}
			reader.addSerializedObject(serialobj);
			return serialobj.get();
		});
		VALUE_CLASS_WRITERS.put(Integer.class,
				(IOBiConsumer<Integer, ContentWriterObjectOutput>) (v, writer) -> writer.writeRawVarInt(v));
		VALUE_CLASS_READERS.put(Integer.class, reader -> {
			SerializedObject<Integer> serialobj;
			try {
				Integer res = reader.readRawVarInt();
				serialobj = new PresentSerializedObject<>(res);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationProtocolException("Failed to read Integer.", e));
			}
			reader.addSerializedObject(serialobj);
			return serialobj.get();
		});
		VALUE_CLASS_WRITERS.put(Long.class,
				(IOBiConsumer<Long, ContentWriterObjectOutput>) (v, writer) -> writer.out.writeLong(v));
		VALUE_CLASS_READERS.put(Long.class, reader -> {
			SerializedObject<Long> serialobj;
			try {
				Long res = reader.state.in.readLong();
				serialobj = new PresentSerializedObject<>(res);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationProtocolException("Failed to read Long.", e));
			}
			reader.addSerializedObject(serialobj);
			return serialobj.get();
		});
		VALUE_CLASS_WRITERS.put(Float.class,
				(IOBiConsumer<Float, ContentWriterObjectOutput>) (v, writer) -> writer.out.writeFloat(v));
		VALUE_CLASS_READERS.put(Float.class, reader -> {
			SerializedObject<Float> serialobj;
			try {
				Float res = reader.state.in.readFloat();
				serialobj = new PresentSerializedObject<>(res);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationProtocolException("Failed to read Float.", e));
			}
			reader.addSerializedObject(serialobj);
			return serialobj.get();
		});
		VALUE_CLASS_WRITERS.put(Double.class,
				(IOBiConsumer<Double, ContentWriterObjectOutput>) (v, writer) -> writer.out.writeDouble(v));
		VALUE_CLASS_READERS.put(Double.class, reader -> {
			SerializedObject<Double> serialobj;
			try {
				Double res = reader.state.in.readDouble();
				serialobj = new PresentSerializedObject<>(res);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationProtocolException("Failed to read Double.", e));
			}
			reader.addSerializedObject(serialobj);
			return serialobj.get();
		});
		VALUE_CLASS_WRITERS.put(Character.class,
				(IOBiConsumer<Character, ContentWriterObjectOutput>) (v, writer) -> writer.out.writeChar(v));
		VALUE_CLASS_READERS.put(Character.class, reader -> {
			SerializedObject<Character> serialobj;
			try {
				Character res = reader.state.in.readChar();
				serialobj = new PresentSerializedObject<>(res);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationProtocolException("Failed to read Character.", e));
			}
			reader.addSerializedObject(serialobj);
			return serialobj.get();
		});
		VALUE_CLASS_WRITERS.put(Boolean.class,
				(IOBiConsumer<Boolean, ContentWriterObjectOutput>) (v, writer) -> writer.out.writeBoolean(v));
		VALUE_CLASS_READERS.put(Boolean.class, reader -> {
			SerializedObject<Boolean> serialobj;
			try {
				Boolean res = reader.state.in.readBoolean();
				serialobj = new PresentSerializedObject<>(res);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationProtocolException("Failed to read Boolean.", e));
			}
			reader.addSerializedObject(serialobj);
			return serialobj.get();
		});

		VALUE_CLASS_WRITERS.put(SakerPath.class,
				(IOBiConsumer<SakerPath, ContentWriterObjectOutput>) (v, writer) -> writer.writeUTF(v.toString()));
		VALUE_CLASS_READERS.put(SakerPath.class, reader -> {
			int idx = reader.addSerializedObject(UnavailableSerializedObject.instance());
			SerializedObject<SakerPath> serialobj;
			try {
				SakerPath res = SakerPath.valueOf(reader.readUTF());
				serialobj = new PresentSerializedObject<>(res);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationProtocolException("Failed to read SakerPath.", e));
			}
			reader.setSerializedObject(idx, serialobj);
			return serialobj.get();
		});

		VALUE_CLASS_WRITERS.put(URI.class,
				(IOBiConsumer<URI, ContentWriterObjectOutput>) (v, writer) -> writer.out.writeUTF(v.toString()));
		VALUE_CLASS_READERS.put(URI.class, reader -> {
			SerializedObject<URI> serialobj;
			try {
				URI res = new URI(reader.state.in.readUTF());
				serialobj = new PresentSerializedObject<>(res);
			} catch (Exception e) {
				serialobj = new FailedSerializedObject<>(
						() -> new SerializationProtocolException("Failed to read URI.", e));
			}
			reader.addSerializedObject(serialobj);
			return serialobj.get();
		});
	}
	private final DataOutputUnsyncByteArrayOutputStream out = new DataOutputUnsyncByteArrayOutputStream();

	private final IdentityHashMap<Object, Integer> objectIndices = new IdentityHashMap<>();
	private final Map<Object, Object> valueInternalizer = new HashMap<>();
	private final NavigableMap<String, InternedString> stringInternalizer = new TreeMap<>();

	private final ClassLoaderResolver registry;

	private final Set<Class<?>> warnedClasses = new HashSet<>();

	private Map<Class<?>, Consumer<?>> arrayWriters = new HashMap<>();
	{
		arrayWriters.put(byte.class, (Consumer<byte[]>) out::write);
		arrayWriters.put(short.class, (Consumer<short[]>) out::write);
		arrayWriters.put(int.class, (Consumer<int[]>) out::write);
		arrayWriters.put(long.class, (Consumer<long[]>) out::write);
		arrayWriters.put(float.class, (Consumer<float[]>) out::write);
		arrayWriters.put(double.class, (Consumer<double[]>) out::write);
		arrayWriters.put(char.class, (Consumer<char[]>) out::write);
		arrayWriters.put(boolean.class, (Consumer<boolean[]>) out::write);
	}

	private char[] charWriteBuffer = ObjectUtils.EMPTY_CHAR_ARRAY;

	public ContentWriterObjectOutput(ClassLoaderResolver registry) {
		this.registry = registry;
	}

	public void writeNull() {
		out.writeByte(C_OBJECT_NULL);
	}

	@Override
	public void writeBoolean(boolean v) throws IOException {
		out.writeByte(C_BOOLEAN);
		out.writeBoolean(v);
	}

	@Override
	public void writeByte(int v) throws IOException {
		out.writeByte(C_BYTE);
		out.writeByte(v);
	}

	@Override
	public void writeShort(int v) throws IOException {
		out.writeByte(C_SHORT);
		out.writeShort(v);
	}

	@Override
	public void writeChar(int v) throws IOException {
		out.writeByte(C_CHAR);
		out.writeChar(v);
	}

	void writeRawVarInt(int v) throws IOException {
		while (true) {
			int wb = v & 0x7F;
			v = v >>> 7;
			if (v != 0) {
				//still more bytes, add the continue flag
				out.writeByte(wb | 0x80);
				continue;
			}
			//last byte to be written
			out.writeByte(wb);
			break;
		}
	}

	@Override
	public void writeInt(int v) throws IOException {
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
								out.writeByte(C_INT_1);
								out.writeByte(v);
								break;
							}
						}
						break;
					}
					default: {
						out.writeByte(C_INT);
						out.writeInt(v);
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
							out.writeByte(C_INT_F_1);
							out.writeByte(v);
						}
						break;
					}
					default: {
						out.writeByte(C_INT_F_2);
						out.writeShort(v);
						break;
					}
				}
				break;
			}
			default: {
				switch (v & 0xFF00_0000) {
					case 0xFF00_0000: {
						//starts with 0xFFxx_xxxx
						out.writeByte(C_INT_F_3);
						out.writeByte(v >>> 16);
						out.writeShort(v);
						break;
					}
					case 0x0000_0000: {
						//starts with 0x00xx_xxxx
						out.writeByte(C_INT_3);
						out.writeByte(v >>> 16);
						out.writeShort(v);
						break;
					}
					default: {
						//full int
						out.writeByte(C_INT);
						out.writeInt(v);
						break;
					}
				}
				break;
			}
		}
	}

	@Override
	public void writeLong(long v) throws IOException {
		long top4 = v & 0xFFFFFFFF_00000000L;
		if (top4 == 0x00000000_00000000L) {
			//starts with 0x00000000_xxxxxxxx
			long bottop2 = v & 0x00000000_FFFF0000L;
			if (bottop2 == 0x00000000_00000000L) {
				//starts with 0x00000000_0000xxxx
				if (v == 0) {
					out.writeByte(C_LONG_ZERO);
				} else {
					out.writeByte(C_LONG_2);
					out.writeShort((short) v);
				}
			} else {
				out.writeByte(C_LONG);
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
					out.writeByte(C_LONG_F_2);
					out.writeShort((short) v);
				}
			} else {
				out.writeByte(C_LONG_F_4);
				out.writeInt((int) v);
			}
		} else {
			long toptop2 = v & 0xFFFF0000_00000000L;
			if (toptop2 == 0xFFFF0000_00000000L) {
				//starts with 0xFFFFxxxx_xxxxxxxx
				out.writeByte(C_LONG_F_6);
				out.writeShort((int) (v >>> 32));
				out.writeInt((int) v);
			} else if (toptop2 == 0x00000000_00000000L) {
				//starts with 0x0000xxxx_xxxxxxxx
				out.writeByte(C_LONG_6);
				out.writeShort((int) (v >>> 32));
				out.writeInt((int) v);
			} else {
				//full long
				out.writeByte(C_LONG);
				out.writeLong(v);
			}
		}
	}

	@Override
	public void writeFloat(float v) throws IOException {
		out.writeByte(C_FLOAT);
		out.writeFloat(v);
	}

	@Override
	public void writeDouble(double v) throws IOException {
		out.writeByte(C_DOUBLE);
		out.writeDouble(v);
	}

	@Override
	public void writeBytes(String s) throws IOException {
		out.writeByte(C_BYTEARRAY);
		writeInt(s.length() * 2);
		out.writeBytes(s);
	}

	@Override
	public void writeChars(String s) throws IOException {
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

	private UTFPrefixInfo getBetterPrefix(String s, int slen, Entry<String, InternedString> first,
			Entry<String, InternedString> second) {
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
		Entry<String, InternedString> floorentry = stringInternalizer.floorEntry(s);
		if (floorentry != null && floorentry.getKey().equals(s)) {
			writeUtfWithIndexCommand(floorentry.getValue().index, objwrite);
			return;
		}

		int slen = ensureCharWriteBuffer(s);
		int idx = addSerializedObject(s);
		stringInternalizer.put(s, new InternedString(s, idx));

		if (slen > UTF_PREFIX_MIN_LEN) {
			//attempt prefixing if the written string is longer than the min requirement
			UTFPrefixInfo prefixinfo = getBetterPrefix(s, slen, floorentry, stringInternalizer.higherEntry(s));

			if (prefixinfo != null) {
				//both strings have a common starting characters
				int common = prefixinfo.common;

				int writecount = slen - common;
				if (isLowBytesChars(charWriteBuffer, common, writecount)) {
					out.ensureCapacity(out.size() + (1 + 5 + 5 + 5) + writecount);
					out.writeByte(objwrite ? C_OBJECT_UTF_PREFIXED_LOWBYTES : C_UTF_PREFIXED_LOWBYTES);
					writeRawVarInt(prefixinfo.prefix.index);
					writeRawVarInt(common);
					writeRawVarInt(writecount);
					for (int i = 0; i < writecount; i++) {
						out.writeByte(charWriteBuffer[common + i]);
					}
				} else {
					out.ensureCapacity(out.size() + (1 + 5 + 5 + 5) + writecount * 2);
					out.writeByte(objwrite ? C_OBJECT_UTF_PREFIXED : C_UTF_PREFIXED);
					writeRawVarInt(prefixinfo.prefix.index);
					writeRawVarInt(common);
					writeRawVarInt(writecount);
					out.write(charWriteBuffer, common, writecount);
				}
				return;
			}
		}

		writeUtfData(slen, objwrite);
	}

	private int getPrefixCommonCharCountWithBuffer(int slen, String otherstr, int olen, int startidx) {
		int common = startidx;
		for (int minlen = Math.min(slen, olen); common < minlen; common++) {
			if (charWriteBuffer[common] != otherstr.charAt(common)) {
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
		final char[] buffer = charWriteBuffer;
		final DataOutputUnsyncByteArrayOutputStream out = this.out;

		if (isLowBytesChars(buffer, 0, slen)) {
			out.ensureCapacity(out.size() + (1 + 5) + slen);
			out.writeByte(objwrite ? C_OBJECT_UTF_LOWBYTES : C_UTF_LOWBYTES);
			writeRawVarInt(slen);
			for (int i = 0; i < slen; i++) {
				out.writeByte(buffer[i]);
			}
		} else {
			out.writeByte(objwrite ? C_OBJECT_UTF : C_UTF);
			writeRawVarInt(slen);
			out.write(buffer, 0, slen);
		}
	}

	private void writeUtfWithIndexCommand(Integer oidx, boolean objwrite) {
		if ((oidx & 0xFFFF0000) != 0) {
			if ((oidx & 0xFF000000) != 0) {
				out.writeByte(objwrite ? C_OBJECT_UTF_IDX : C_UTF_IDX);
				out.writeInt(oidx);
			} else {
				out.writeByte(objwrite ? C_OBJECT_UTF_IDX_3 : C_UTF_IDX_3);
				out.writeByte(oidx >>> 16);
				out.writeShort(oidx);
			}
		} else {
			if ((oidx & 0x0000FF00) != 0) {
				out.writeByte(objwrite ? C_OBJECT_UTF_IDX_2 : C_UTF_IDX_2);
				out.writeShort(oidx);
			} else {
				out.writeByte(objwrite ? C_OBJECT_UTF_IDX_1 : C_UTF_IDX_1);
				out.writeByte(oidx);
			}
		}
	}

	private void writeObjectIdxWithCommandImpl(int oidx) {
		if ((oidx & 0xFFFF0000) != 0) {
			if ((oidx & 0xFF000000) != 0) {
				out.writeByte(C_OBJECT_IDX);
				out.writeInt(oidx);
			} else {
				out.writeByte(C_OBJECT_IDX_3);
				out.writeByte(oidx >>> 16);
				out.writeShort(oidx);
			}
		} else {
			if ((oidx & 0x0000FF00) != 0) {
				out.writeByte(C_OBJECT_IDX_2);
				out.writeShort(oidx);
			} else {
				out.writeByte(C_OBJECT_IDX_1);
				out.writeByte(oidx);
			}
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
			writeObjectIdxWithCommandImpl(oidx);
			return;
		}
		Class<? extends Object> objclass = obj.getClass();
		@SuppressWarnings("unchecked")
		IOBiConsumer<Object, ContentWriterObjectOutput> objwriter = (IOBiConsumer<Object, ContentWriterObjectOutput>) VALUE_CLASS_WRITERS
				.get(objclass);
		if (objwriter != null) {
			Object prev = valueInternalizer.putIfAbsent(obj, obj);
			if (prev != null) {
				writeObjectIdxWithCommandImpl(objectIndices.get(prev));
				return;
			}
			out.writeByte(C_OBJECT_VALUE);
			writeTypeWithCommandOrIdx(objclass);
			addSerializedObject(obj);
			objwriter.accept(obj, this);
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
			out.writeByte(C_OBJECT_ENUM);
			writeEnumImpl(obj, objclass);
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
		//do not warn for enumsets
		if (Proxy.isProxyClass(objclass)) {
			writeProxyObjectWithCommand(obj);
			return;
		}
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
		out.writeByte(C_BYTE);
		out.write(b);
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
		out.writeByte(C_BYTEARRAY);
		writeInt(len);
		out.write(b, off, len);
	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public void close() throws IOException {
	}

	public void drainTo(OutputStream os) throws IOException {
		out.writeTo(os);
		out.reset();
	}

	public void drainTo(ByteSink os) throws IOException {
		out.writeTo(os);
		out.reset();
	}

	public ByteArrayRegion drainToBytes() {
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
		int idx = objectIndices.size();
		Integer prev = objectIndices.putIfAbsent(obj, idx);
		if (prev != null) {
			throw new SerializationProtocolException("Internal Error: Object is already present: " + obj);
		}
		return idx;
	}

	private void writeEnumImpl(Object obj, Class<? extends Object> objclass) throws IOException {
		if (objclass.isAnonymousClass()) {
			objclass = objclass.getSuperclass();
		}
		writeTypeWithCommandOrIdx(objclass);
		Enum<?> e = (Enum<?>) obj;
		out.writeStringLengthChars(e.name());
		addSerializedObject(obj);
	}

	private void writeExternalizableWithCommandImpl(Externalizable obj, Class<? extends Object> objclass)
			throws IOException {
		int startsize = out.size();
		out.writeByte(C_OBJECT_EXTERNALIZABLE);
		writeTypeWithCommandOrIdx(objclass);

		addSerializedObject(obj);

		int sizeintpos = out.size();
		//length written later
		out.writeInt(0);
		int lencalcstartpos = out.size();
		try {
			obj.writeExternal(this);
		} catch (Exception e) {
			out.replaceByte(C_OBJECT_EXTERNALIZABLE_ERROR, startsize);
			//don't need to handle the object table corruption, as the output is pre-read when deserialized
			throw new ObjectWriteException("Failed to write Externalizable object. (" + obj.getClass().getName() + ")",
					e);
		} finally {
			int c = out.size() - lencalcstartpos;
			out.replaceInt(c, sizeintpos);
		}
	}

	private void writeArrayImplWithCommandImpl(Object obj, Class<? extends Object> objclass) throws IOException {
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
			Consumer<Object> writer = (Consumer<Object>) arrayWriters.get(componenttype);
			if (writer != null) {
				writer.accept(obj);
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
			writeObjectIdxWithCommandImpl(typeidx);
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

	private static final class InternedString {
		protected final String value;
		protected final int index;

		public InternedString(String value, int index) {
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
		protected final InternedString prefix;
		protected final int common;

		public UTFPrefixInfo(InternedString prefix, int common) {
			this.prefix = prefix;
			this.common = common;
		}
	}

}