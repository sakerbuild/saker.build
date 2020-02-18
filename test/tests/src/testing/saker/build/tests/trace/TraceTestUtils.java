package testing.saker.build.tests.trace;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.trace.InternalBuildTraceImpl;

public class TraceTestUtils {
	private TraceTestUtils() {
		throw new UnsupportedOperationException();
	}

	public static Object getTraceField(ProviderHolderPathKey pathkey, Object... fieldpath) throws IOException {
		Map<String, Object> bt = readBuildTrace(pathkey);
		Object c = bt;
		for (Object o : fieldpath) {
			if (o instanceof String) {
				c = ((Map<?, ?>) c).get(o);
				if (c == null) {
					throw new NoSuchElementException(o.toString());
				}
				continue;
			}
			if (o instanceof Number) {
				c = ((List<?>) c).get(((Number) o).intValue());
				continue;
			}
			throw new IllegalArgumentException("Unknown build trace filed path: " + o);
		}
		return c;
	}

	public static Map<String, Object> readBuildTrace(ProviderHolderPathKey pathkey) throws IOException {
		try (ByteSource in = pathkey.getFileProvider().openInput(pathkey.getPath());
				DataInputStream datain = new DataInputStream(ByteSource.toInputStream(in))) {
			return readBuildTrace(datain);
		}
	}

	private static String readString(DataInputStream is) throws IOException {
		int len = is.readInt();
		StringBuilder sb = new StringBuilder(len);
		while (len-- > 0) {
			sb.append(is.readChar());
		}
		return sb.toString();
	}

	private static Object readObject(DataInputStream is) throws IOException {
		byte type = is.readByte();
		switch (type) {
			case InternalBuildTraceImpl.TYPE_BYTE: {
				return is.readByte();
			}
			case InternalBuildTraceImpl.TYPE_SHORT: {
				return is.readShort();
			}
			case InternalBuildTraceImpl.TYPE_INT: {
				return is.readInt();
			}
			case InternalBuildTraceImpl.TYPE_LONG: {
				return is.readLong();
			}
			case InternalBuildTraceImpl.TYPE_OBJECT: {
				int len = is.readInt();
				Map<String, Object> result = new LinkedHashMap<>();
				while (len-- > 0) {
					String fname = readString(is);
					Object val = readObject(is);
					Object prev = result.put(fname, val);
					if (prev != null) {
						throw new IllegalArgumentException("Duplicate entries: " + fname + ": " + prev + " - " + val);
					}
				}
				return result;
			}
			case InternalBuildTraceImpl.TYPE_ARRAY: {
				int len = is.readInt();
				ArrayList<Object> result = new ArrayList<>(len);
				while (len-- > 0) {
					result.add(readObject(is));
				}
				return result;
			}
			case InternalBuildTraceImpl.TYPE_STRING: {
				return readString(is);
			}
			case InternalBuildTraceImpl.TYPE_NULL: {
				return null;
			}
			case InternalBuildTraceImpl.TYPE_ARRAY_NULL_BOUNDED: {
				ArrayList<Object> result = new ArrayList<>();
				while (true) {
					Object o = readObject(is);
					if (o == null) {
						break;
					}
					result.add(o);
				}
				return result;
			}
			case InternalBuildTraceImpl.TYPE_OBJECT_EMPTY_BOUNDED: {
				Map<String, Object> result = new LinkedHashMap<>();
				while (true) {
					String fname = readString(is);
					if ("".equals(fname)) {
						break;
					}
					Object val = readObject(is);
					Object prev = result.put(fname, val);
					if (prev != null) {
						throw new IllegalArgumentException("Duplicate entries: " + fname + ": " + prev + " - " + val);
					}
				}
				return result;
			}
			case InternalBuildTraceImpl.TYPE_BYTE_ARRAY: {
				int len = is.readInt();
				byte[] array = new byte[len];
				is.readFully(array);
				return array;
			}
			case InternalBuildTraceImpl.TYPE_BOOLEAN_TRUE: {
				return true;
			}
			case InternalBuildTraceImpl.TYPE_BOOLEAN_FALSE: {
				return false;
			}
			case InternalBuildTraceImpl.TYPE_FLOAT_AS_STRING: {
				return Float.parseFloat(readString(is));
			}
			case InternalBuildTraceImpl.TYPE_DOUBLE_AS_STRING: {
				return Double.parseDouble(readString(is));
			}
			default: {
				throw new IllegalArgumentException("Unknown type: " + type);
			}
		}
	}

	public static Map<String, Object> readBuildTrace(DataInputStream is) throws IOException {
		int magic = is.readInt();
		if (magic != InternalBuildTraceImpl.MAGIC) {
			throw new IllegalArgumentException("Invalid magic: " + Integer.toHexString(magic));
		}
		int format = is.readInt();
		if (format != InternalBuildTraceImpl.FORMAT_VERSION) {
			throw new IllegalArgumentException("Invalid format:  " + format);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		while (true) {
			String fname;
			try {
				fname = readString(is);
			} catch (EOFException e) {
				break;
			}
			Object o = readObject(is);
			Object prev = result.put(fname, o);
			if (prev != null) {
				throw new IllegalArgumentException("Duplicate entries: " + fname + ": " + prev + " - " + o);
			}

		}
		return result;
	}

}
