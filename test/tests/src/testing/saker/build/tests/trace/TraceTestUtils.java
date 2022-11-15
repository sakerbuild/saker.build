package testing.saker.build.tests.trace;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.trace.InternalBuildTraceImpl;

public class TraceTestUtils {
	private TraceTestUtils() {
		throw new UnsupportedOperationException();
	}

	public static Object getTraceField(ProviderHolderPathKey pathkey, Object... fieldpath) throws IOException {
		Map<String, Object> bt = readBuildTrace(pathkey);
		return getTraceField(bt, fieldpath);
	}

	public static Object getTraceField(Map<String, Object> bt, Object... fieldpath) {
		Object c = bt;
		for (Object o : fieldpath) {
			if (o instanceof String) {
				Map<?, ?> map = (Map<?, ?>) c;
				c = map.get(o);
				if (c == null) {
					throw new NoSuchElementException(o.toString() + " keys: " + map.keySet());
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
			case InternalBuildTraceImpl.TYPE_EXCEPTION_STACKTRACE: {
				return new ExceptionStackTraceHolder(readString(is));
			}
			case InternalBuildTraceImpl.TYPE_EXCEPTION_DETAIL: {
				@SuppressWarnings("unchecked")
				Map<String, Object> detailobj = (Map<String, Object>) readObject(is);
				if (Integer.valueOf(0).equals(detailobj.get("exc_context_id"))) {
					return convertToExceptionDetail(detailobj);
				}
				return detailobj;
			}
			default: {
				throw new IllegalArgumentException("Unknown type: " + type);
			}
		}
	}

	private static ExceptionDetailHolder convertToExceptionDetail(Map<String, Object> detailobj) {
		return convertToExceptionDetailImpl(detailobj, new TreeMap<>());
	}

	@SuppressWarnings("unchecked")
	private static ExceptionDetailHolder convertToExceptionDetailImpl(Map<String, Object> detailobj,
			Map<Integer, ExceptionDetailHolder> exceptions) {
		if (detailobj == null) {
			return null;
		}
		Integer circref = (Integer) detailobj.get("circular_reference");
		if (circref != null) {
			ExceptionDetailHolder res = exceptions.get(circref);
			if (res == null) {
				throw new IllegalArgumentException(
						"Circular reference not found with id: " + circref + " in " + exceptions);
			}
			return res;
		}
		int contextid = (int) detailobj.get("exc_context_id");
		ExceptionDetailHolder result = new ExceptionDetailHolder();
		if (exceptions.put(contextid, result) != null) {
			throw new IllegalArgumentException("Duplicate exception with context id: " + contextid);
		}
		result.contextId = contextid;
		if (contextid == 0) {
			//stacktrace is only written for the top level exception
			result.stackTrace = (String) detailobj.get("stacktrace");
			Objects.requireNonNull(result.stackTrace, "stakctrace");
		}
		result.cause = convertToExceptionDetailImpl((Map<String, Object>) detailobj.get("cause"), exceptions);
		List<?> suppressed = (List<?>) detailobj.get("suppressed");
		if (suppressed != null) {
			result.suppressed = new ExceptionDetailHolder[suppressed.size()];
			for (int i = 0; i < result.suppressed.length; i++) {
				result.suppressed[i] = convertToExceptionDetailImpl((Map<String, Object>) suppressed.get(i),
						exceptions);
			}
		}
		return result;
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

	public static final class ExceptionStackTraceHolder {
		public final String stackTrace;

		public ExceptionStackTraceHolder(String stackTrace) {
			this.stackTrace = stackTrace;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((stackTrace == null) ? 0 : stackTrace.hashCode());
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
			ExceptionStackTraceHolder other = (ExceptionStackTraceHolder) obj;
			if (stackTrace == null) {
				if (other.stackTrace != null)
					return false;
			} else if (!stackTrace.equals(other.stackTrace))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "ExceptionStackTraceHolder[" + stackTrace.replace("\n", "\\n").replace("\r", "\\r") + "]";
		}
	}

	public static final class ExceptionDetailHolder {
		private transient int contextId;
		private String stackTrace;
		private ExceptionDetailHolder cause;
		private ExceptionDetailHolder[] suppressed;

		private ExceptionDetailHolder() {
		}

		public ExceptionDetailHolder(String stackTrace) {
			this.stackTrace = stackTrace;
		}

		public String getStackTrace() {
			return stackTrace;
		}

		public ExceptionDetailHolder getCause() {
			return cause;
		}

		public ExceptionDetailHolder[] getSuppressed() {
			return suppressed;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + contextId;
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
			ExceptionDetailHolder other = (ExceptionDetailHolder) obj;
			return checkEquals(other, new IdentityHashMap<>());
		}

		private boolean checkEquals(ExceptionDetailHolder other,
				IdentityHashMap<ExceptionDetailHolder, Set<ExceptionDetailHolder>> checkedexceptions) {
			if (other == null) {
				return false;
			}
			if (!checkedexceptions.computeIfAbsent(this, x -> ObjectUtils.newIdentityHashSet()).add(other)) {
				//already checked, or is being recursively checked now
				return true;
			}
			if (!Objects.equals(this.stackTrace, other.stackTrace)) {
				return false;
			}
			if (this.cause != null) {
				if (other.cause == null) {
					return false;
				}
				if (!this.cause.checkEquals(other.cause, checkedexceptions)) {
					return false;
				}
			} else if (other.cause != null) {
				return false;
			}
			if (this.suppressed != null) {
				if (other.suppressed == null) {
					return false;
				}
				if (this.suppressed.length != other.suppressed.length) {
					return false;
				}
				for (int i = 0; i < suppressed.length; i++) {
					if (!this.suppressed[i].checkEquals(other.suppressed[i], checkedexceptions)) {
						return false;
					}
				}
			} else if (other.suppressed != null) {
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ExceptionDetailHolder[contextId=");
			builder.append(contextId);
			builder.append(", stackTrace=");
			builder.append(stackTrace);
			builder.append("]");
			return builder.toString();
		}

	}
}
