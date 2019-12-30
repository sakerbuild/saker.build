package saker.build.util.serial;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;

import saker.build.runtime.execution.SakerLog;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.io.DataInputUnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.LimitInputStream;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.function.IOFunction;
import saker.build.thirdparty.saker.util.io.function.ObjectReaderFunction;
import testing.saker.build.flag.TestFlag;

public class ContentReaderObjectInput implements ObjectInput {
	private static final NavigableSet<Integer> EXPECTED_COMMANDS_UTF = ImmutableUtils.makeImmutableNavigableSet(
			new Integer[] { ContentWriterObjectOutput.C_UTF, ContentWriterObjectOutput.C_UTF_LOWBYTES,
					ContentWriterObjectOutput.C_UTF_IDX, ContentWriterObjectOutput.C_UTF_IDX_1,
					ContentWriterObjectOutput.C_UTF_IDX_2, ContentWriterObjectOutput.C_UTF_IDX_3 });

	private static final NavigableSet<Integer> EXPECTED_COMMANDS_INT = ImmutableUtils
			.makeImmutableNavigableSet(new Integer[] { ContentWriterObjectOutput.C_INT,
					ContentWriterObjectOutput.C_INT_1, ContentWriterObjectOutput.C_INT_2,
					ContentWriterObjectOutput.C_INT_3, ContentWriterObjectOutput.C_INT_F_1,
					ContentWriterObjectOutput.C_INT_F_2, ContentWriterObjectOutput.C_INT_F_3,
					ContentWriterObjectOutput.C_INT_ZERO, ContentWriterObjectOutput.C_INT_NEGATIVE_ONE });

	private static final NavigableSet<Integer> EXPECTED_COMMANDS_LONG = ImmutableUtils
			.makeImmutableNavigableSet(new Integer[] { ContentWriterObjectOutput.C_LONG,
					ContentWriterObjectOutput.C_LONG_2, ContentWriterObjectOutput.C_LONG_4,
					ContentWriterObjectOutput.C_LONG_6, ContentWriterObjectOutput.C_LONG_F_2,
					ContentWriterObjectOutput.C_LONG_F_4, ContentWriterObjectOutput.C_LONG_F_6,
					ContentWriterObjectOutput.C_LONG_ZERO, ContentWriterObjectOutput.C_LONG_NEGATIVE_ONE });

	private static final NavigableSet<Integer> EXPECTED_COMMANDS_BYTE = ImmutableUtils.makeImmutableNavigableSet(
			new Integer[] { ContentWriterObjectOutput.C_BYTE, ContentWriterObjectOutput.C_BYTEARRAY });

	private static final NavigableSet<Integer> EXPECTED_COMMANDS_CHAR = ImmutableUtils.makeImmutableNavigableSet(
			new Integer[] { ContentWriterObjectOutput.C_CHAR, ContentWriterObjectOutput.C_CHARS });

	private static final NavigableSet<Integer> EXPECTED_COMMANDS_OBJECT = ImmutableUtils
			.makeImmutableNavigableSet(new Integer[] { ContentWriterObjectOutput.C_OBJECT_IDX,
					ContentWriterObjectOutput.C_OBJECT_IDX_3, ContentWriterObjectOutput.C_OBJECT_IDX_2,
					ContentWriterObjectOutput.C_OBJECT_IDX_1, ContentWriterObjectOutput.C_OBJECT_ARRAY,
					ContentWriterObjectOutput.C_OBJECT_ARRAY_ERROR, ContentWriterObjectOutput.C_OBJECT_CLASSLOADER,
					ContentWriterObjectOutput.C_OBJECT_CUSTOM_SERIALIZABLE,
					ContentWriterObjectOutput.C_OBJECT_CUSTOM_SERIALIZABLE_ERROR,
					ContentWriterObjectOutput.C_OBJECT_ENUM, ContentWriterObjectOutput.C_OBJECT_EXTERNALIZABLE,
					ContentWriterObjectOutput.C_OBJECT_EXTERNALIZABLE_ERROR, ContentWriterObjectOutput.C_OBJECT_NULL,
					ContentWriterObjectOutput.C_OBJECT_SERIALIZABLE,
					ContentWriterObjectOutput.C_OBJECT_SERIALIZABLE_ERROR, ContentWriterObjectOutput.C_OBJECT_TYPE,
					ContentWriterObjectOutput.C_OBJECT_VALUE, ContentWriterObjectOutput.C_OBJECT_UTF,
					ContentWriterObjectOutput.C_OBJECT_PROXY, });
	private static final NavigableSet<Integer> EXPECTED_COMMANDS_TYPE = ImmutableUtils
			.makeImmutableNavigableSet(new Integer[] { ContentWriterObjectOutput.C_OBJECT_TYPE,
					ContentWriterObjectOutput.C_OBJECT_IDX, ContentWriterObjectOutput.C_OBJECT_IDX_3,
					ContentWriterObjectOutput.C_OBJECT_IDX_2, ContentWriterObjectOutput.C_OBJECT_IDX_1 });

	static class ReadState {
		final DataInputStream in;
		private final InputStream actualInput;

		int byteContentRemaining = 0;
		/**
		 * Number of <code>char</code>s, not bytes.
		 */
		int charContentRemaining = 0;

		private int readCommand = 0;
		private String commandFailReason = null;

		public ReadState(InputStream in) {
			this.actualInput = in;
			this.in = new DataInputStream(in);
		}

		private void expectRawBytesReading() throws IOException {
			if (charContentRemaining > 0) {
				throw new ObjectTypeException(
						"Raw char contents wasn't fully read. (" + charContentRemaining + " remaining)");
			}
		}

		private void expectRawCharsReading() throws IOException {
			if (byteContentRemaining > 0) {
				throw new ObjectTypeException(
						"Raw byte contents wasn't fully read. (" + byteContentRemaining + " remaining)");
			}
		}

		private void readCommandInternal() throws IOException {
			if (byteContentRemaining > 0) {
				throw new ObjectTypeException(
						"Raw byte contents wasn't fully read. (" + byteContentRemaining + " remaining)");
			}
			if (charContentRemaining > 0) {
				throw new ObjectTypeException(
						"Raw char contents wasn't fully read. (" + charContentRemaining + " remaining)");
			}
			if (this.readCommand < 0) {
				throw new EOFException("Failed to read next serialization command. (" + commandFailReason + ")");
			}
			if (this.readCommand == 0) {
				int readcmd = in.read();
				if (readcmd < 0) {
					this.readCommand = -1;
					commandFailReason = "End of stream";
					throw new EOFException("Failed to read next serialization command. (" + commandFailReason + ")");
				}
				if (readcmd == 0 || readcmd > ContentWriterObjectOutput.C_MAX_COMMAND_VALUE) {
					this.readCommand = -1;
					commandFailReason = "Unrecognized command: " + readcmd;
					throw new SerializationProtocolException(
							"Invalid next serialization command. (" + commandFailReason + ")");
				}
				this.readCommand = readcmd;
			}
		}

		private int expectCommands(Set<Integer> expectedcommands) throws IOException {
			readCommandInternal();
			int readcmd = this.readCommand;
			if (expectedcommands.contains(readcmd)) {
				this.readCommand = 0;
				return readcmd;
			}
			throw new ObjectTypeException("Different type expected to be read from stream. ("
					+ ContentWriterObjectOutput.getCommandTypeInfo(readcmd) + ")");
		}

		private void expectCommand(int expectedcmd) throws IOException {
			readCommandInternal();
			if (this.readCommand == expectedcmd) {
				this.readCommand = 0;
				return;
			}
			throw new ObjectTypeException("Different type expected to be read from stream. ("
					+ ContentWriterObjectOutput.getCommandTypeInfo(this.readCommand) + ")");
		}

		private int takeCommandIfAny() {
			if (this.readCommand < 0) {
				return -1;
			}
			if (this.readCommand != 0) {
				int readcmd = this.readCommand;
				this.readCommand = 0;
				return readcmd;
			}
			int readcmd;
			try {
				readcmd = in.read();
			} catch (IOException e) {
				this.readCommand = -1;
				return -1;
			}
			if (readcmd < 0) {
				commandFailReason = "End of stream";
				this.readCommand = -1;
				return -1;
			}
			if (readcmd == 0 || readcmd > ContentWriterObjectOutput.C_MAX_COMMAND_VALUE) {
				this.readCommand = -1;
				commandFailReason = "Unrecognized command: " + readcmd;
				return -1;
			}
			return readcmd;
		}
	}

	private static final Map<Class<?>, IOFunction<DataInput, Object>> primitiveArrayElementReaders = new HashMap<>();
	static {
		primitiveArrayElementReaders.put(byte.class, DataInput::readByte);
		primitiveArrayElementReaders.put(short.class, DataInput::readShort);
		primitiveArrayElementReaders.put(int.class, DataInput::readInt);
		primitiveArrayElementReaders.put(long.class, DataInput::readLong);
		primitiveArrayElementReaders.put(float.class, DataInput::readFloat);
		primitiveArrayElementReaders.put(double.class, DataInput::readDouble);
		primitiveArrayElementReaders.put(char.class, DataInput::readChar);
		primitiveArrayElementReaders.put(boolean.class, DataInput::readBoolean);
	}

	ReadState state;

	private List<SerializedObject<?>> serializedObjects = new ArrayList<>();

	private final ClassLoaderResolver registry;

	private final Set<Class<?>> warnedNotFullyReadClasses = new HashSet<>();

	private byte[] charBytesReadBuffer = ObjectUtils.EMPTY_BYTE_ARRAY;
	private char[] charReadBuffer = ObjectUtils.EMPTY_CHAR_ARRAY;

	public ContentReaderObjectInput(ClassLoaderResolver registry, InputStream rawinput) {
		this.state = new ReadState(new DataInputStream(rawinput));
		this.registry = registry;
	}

	@Override
	public void readFully(byte[] b) throws IOException {
		readFully(b, 0, b.length);
	}

	@Override
	public void readFully(byte[] b, int off, int len) throws IOException {
		state.expectRawBytesReading();
		int read = StreamUtils.readFillObjectInputBytes(this, b, off, len);
		if (read < len) {
			throw new EOFException();
		}
	}

	@Override
	public int skipBytes(int n) throws IOException {
		return (int) skip(n);
	}

	@Override
	public boolean readBoolean() throws IOException {
		state.expectCommand(ContentWriterObjectOutput.C_BOOLEAN);
		return state.in.readBoolean();
	}

	@Override
	public byte readByte() throws IOException {
		int r = read();
		if (r < 0) {
			throw new EOFException();
		}
		return (byte) r;
	}

	@Override
	public int readUnsignedByte() throws IOException {
		state.expectCommand(ContentWriterObjectOutput.C_BYTE);
		return state.in.readUnsignedByte();
	}

	@Override
	public short readShort() throws IOException {
		state.expectCommand(ContentWriterObjectOutput.C_SHORT);
		return state.in.readShort();
	}

	@Override
	public int readUnsignedShort() throws IOException {
		state.expectCommand(ContentWriterObjectOutput.C_SHORT);
		return state.in.readUnsignedShort();
	}

	@Override
	public char readChar() throws IOException {
		state.expectRawCharsReading();
		if (state.charContentRemaining > 0) {
			--state.charContentRemaining;
			return state.in.readChar();
		}
		while (true) {
			int cmd = state.expectCommands(EXPECTED_COMMANDS_CHAR);
			switch (cmd) {
				case ContentWriterObjectOutput.C_CHAR: {
					return state.in.readChar();
				}
				case ContentWriterObjectOutput.C_CHARS: {
					int rccount = readInt();
					if (rccount <= 0) {
						continue;
					}
					state.charContentRemaining = rccount - 1;
					return state.in.readChar();
				}
				default: {
					throw new AssertionError(cmd);
				}
			}
		}
	}

	@Override
	public int readInt() throws IOException {
		int cmd = state.expectCommands(EXPECTED_COMMANDS_INT);
		switch (cmd) {
			case ContentWriterObjectOutput.C_INT_1: {
				return readIntImpl1();
			}
			case ContentWriterObjectOutput.C_INT_2: {
				return readIntImpl2();
			}
			case ContentWriterObjectOutput.C_INT_3: {
				return readIntImpl3();
			}
			case ContentWriterObjectOutput.C_INT: {
				return readIntImpl();
			}
			case ContentWriterObjectOutput.C_INT_F_1: {
				return readIntImpl1() | 0xFFFF_FF00;
			}
			case ContentWriterObjectOutput.C_INT_F_2: {
				return readIntImpl2() | 0xFFFF_0000;
			}
			case ContentWriterObjectOutput.C_INT_F_3: {
				return readIntImpl3() | 0xFF00_0000;
			}
			case ContentWriterObjectOutput.C_INT_ZERO: {
				return 0;
			}
			case ContentWriterObjectOutput.C_INT_NEGATIVE_ONE: {
				return -1;
			}
			default: {
				throw new AssertionError(cmd);
			}
		}
	}

	@Override
	public long readLong() throws IOException {
		int cmd = state.expectCommands(EXPECTED_COMMANDS_LONG);
		switch (cmd) {
			case ContentWriterObjectOutput.C_LONG_2: {
				return readLongImpl2();
			}
			case ContentWriterObjectOutput.C_LONG_4: {
				return readLongImpl4();
			}
			case ContentWriterObjectOutput.C_LONG_6: {
				return readLongImpl6();
			}
			case ContentWriterObjectOutput.C_LONG: {
				return readLongImpl();
			}
			case ContentWriterObjectOutput.C_LONG_F_2: {
				return readLongImpl2() | 0xFFFFFFFF_FFFF0000L;
			}
			case ContentWriterObjectOutput.C_LONG_F_4: {
				return readLongImplNegative4();
			}
			case ContentWriterObjectOutput.C_LONG_F_6: {
				return readLongImplNegative6();
			}
			case ContentWriterObjectOutput.C_LONG_ZERO: {
				return 0L;
			}
			case ContentWriterObjectOutput.C_LONG_NEGATIVE_ONE: {
				return -1;
			}
			default: {
				throw new AssertionError(cmd);
			}
		}

	}

	private int readIntImpl() throws IOException {
		return state.in.readInt();
	}

	private int readIntImpl3() throws IOException {
		return ((state.in.readUnsignedByte() << 16) | state.in.readUnsignedShort());
	}

	private int readIntImpl2() throws IOException {
		return state.in.readUnsignedShort();
	}

	private int readIntImpl1() throws IOException {
		return state.in.readUnsignedByte();
	}

	private long readLongImpl() throws IOException {
		return state.in.readLong();
	}

	private long readLongImpl2() throws IOException {
		return state.in.readUnsignedShort();
	}

	private long readLongImpl4() throws IOException {
		return ((long) state.in.readInt()) & 0xFFFFFFFFL;
	}

	private long readLongImpl6() throws IOException {
		return (((long) state.in.readUnsignedShort()) << 32) | (((long) state.in.readInt()) & 0xFFFFFFFFL);
	}

	private long readLongImplNegative4() throws IOException {
		return ((long) state.in.readInt()) | 0xFFFFFFFF_00000000L;
	}

	private long readLongImplNegative6() throws IOException {
		return ((((long) state.in.readUnsignedShort()) << 32) | ((long) state.in.readInt())) | 0xFFFF0000_00000000L;
	}

	@Override
	public float readFloat() throws IOException {
		state.expectCommand(ContentWriterObjectOutput.C_FLOAT);
		return state.in.readFloat();
	}

	@Override
	public double readDouble() throws IOException {
		state.expectCommand(ContentWriterObjectOutput.C_DOUBLE);
		return state.in.readDouble();
	}

	@Override
	public String readLine() throws IOException {
		throw new SerializationProtocolException("readLine() unsupported.");
	}

	@Override
	public String readUTF() throws IOException {
		int cmd = state.expectCommands(EXPECTED_COMMANDS_UTF);
		switch (cmd) {
			case ContentWriterObjectOutput.C_UTF: {
				return readUTFImpl();
			}
			case ContentWriterObjectOutput.C_UTF_LOWBYTES: {
				return readUTFLowBytesImpl();
			}
			case ContentWriterObjectOutput.C_UTF_IDX: {
				int idx = readIntImpl();
				return getUtfWithIndex(idx);
			}
			case ContentWriterObjectOutput.C_UTF_IDX_3: {
				int idx = readIntImpl3();
				return getUtfWithIndex(idx);
			}
			case ContentWriterObjectOutput.C_UTF_IDX_2: {
				int idx = readIntImpl2();
				return getUtfWithIndex(idx);
			}
			case ContentWriterObjectOutput.C_UTF_IDX_1: {
				int idx = readIntImpl1();
				return getUtfWithIndex(idx);
			}
			default: {
				throw new AssertionError(cmd);
			}
		}
	}

	private String getUtfWithIndex(int idx) throws SerializationProtocolException, IOException {
		try {
			if (idx >= serializedObjects.size()) {
				throw new SerializationProtocolException(
						"Invalid serialized object index: " + idx + " for size: " + serializedObjects.size());
			}
			return (String) serializedObjects.get(idx).get();
		} catch (ClassNotFoundException e) {
			throw new SerializationProtocolException("Unexpected type failure.", e);
		}
	}

	private String readUTFLowBytesImpl() throws IOException {
		try {
			int slen = readInt();
			if (charBytesReadBuffer.length < slen) {
				charBytesReadBuffer = new byte[Math.max(charBytesReadBuffer.length * 2, slen)];
			}
			state.in.readFully(charBytesReadBuffer, 0, slen);
			if (charReadBuffer.length < slen) {
				charReadBuffer = new char[Math.max(charReadBuffer.length * 2, slen)];
			}
			for (int i = 0; i < slen; i++) {
				charReadBuffer[i] = (char) (charBytesReadBuffer[i] & 0xFF);
			}

			String utf = new String(charReadBuffer, 0, slen);
			addSerializedObject(new PresentSerializedObject<>(utf));
			return utf;
		} catch (IOException e) {
			FailedSerializedObject<?> serializedobj = new FailedSerializedObject<>(() -> {
				IOException eofe = new SerializationProtocolException("Failed to fully read UTF.");
				eofe.initCause(e);
				return eofe;
			});
			addSerializedObject(serializedobj);
			throw e;
		}
	}

	private String readUTFImpl() throws IOException {
		try {
			int slen = readInt();
			if (charBytesReadBuffer.length < slen * 2) {
				charBytesReadBuffer = new byte[Math.max(charBytesReadBuffer.length * 2, slen * 2)];
			}
			state.in.readFully(charBytesReadBuffer, 0, slen * 2);
			if (charReadBuffer.length < slen) {
				charReadBuffer = new char[Math.max(charReadBuffer.length * 2, slen)];
			}
			for (int i = 0; i < slen; i++) {
				charReadBuffer[i] = (char) (((charBytesReadBuffer[i * 2] & 0xFF) << 8)
						| (charBytesReadBuffer[i * 2 + 1] & 0xFF));
			}

			String utf = new String(charReadBuffer, 0, slen);
			addSerializedObject(new PresentSerializedObject<>(utf));
			return utf;
		} catch (IOException e) {
			FailedSerializedObject<?> serializedobj = new FailedSerializedObject<>(() -> {
				IOException eofe = new SerializationProtocolException("Failed to fully read UTF.");
				eofe.initCause(e);
				return eofe;
			});
			addSerializedObject(serializedobj);
			throw e;
		}
	}

	@Override
	public int read() throws IOException {
		state.expectRawBytesReading();
		if (state.byteContentRemaining > 0) {
			--state.byteContentRemaining;
			return state.in.read();
		}
		while (true) {
			int cmd = state.expectCommands(EXPECTED_COMMANDS_BYTE);
			switch (cmd) {
				case ContentWriterObjectOutput.C_BYTE: {
					return state.in.read();
				}
				case ContentWriterObjectOutput.C_BYTEARRAY: {
					int rccount = readInt();
					if (rccount <= 0) {
						continue;
					}
					state.byteContentRemaining = rccount - 1;
					return state.in.read();
				}
				default: {
					throw new AssertionError(cmd);
				}
			}
		}
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		state.expectRawBytesReading();
		int startoffset = off;
		reading_loop:
		while (len > 0) {
			if (state.byteContentRemaining > 0) {
				int toread = Math.min(len, state.byteContentRemaining);
				int result = state.in.read(b, off, toread);
				if (result > 0) {
					state.byteContentRemaining -= result;
				}
				return result;
			}
			int readc = off - startoffset;
			if (readc > 0) {
				return readc;
			}
			int cmd = state.expectCommands(EXPECTED_COMMANDS_BYTE);
			switch (cmd) {
				case ContentWriterObjectOutput.C_BYTE: {
					int readbyte = state.in.read();
					if (readbyte < 0) {
						//eof
						break reading_loop;
					}
					b[off++] = (byte) readbyte;
					--len;
					continue;
				}
				case ContentWriterObjectOutput.C_BYTEARRAY: {
					state.byteContentRemaining = readInt();
					if (state.byteContentRemaining > 0) {
						int c = Math.min(state.byteContentRemaining, len);
						int read = state.in.read(b, off, c);
						if (read > 0) {
							state.byteContentRemaining -= read;
						}
						return read;
					}
					continue;
				}
				default: {
					throw new AssertionError(cmd);
				}
			}
		}
		return off - startoffset;
	}

	@Override
	public Object readObject() throws ClassNotFoundException, IOException {
		int cmd = state.expectCommands(EXPECTED_COMMANDS_OBJECT);
		switch (cmd) {
			case ContentWriterObjectOutput.C_OBJECT_IDX: {
				return readObjectIdxImpl();
			}
			case ContentWriterObjectOutput.C_OBJECT_IDX_3: {
				return readObjectIdxImpl3();
			}
			case ContentWriterObjectOutput.C_OBJECT_IDX_2: {
				return readObjectIdxImpl2();
			}
			case ContentWriterObjectOutput.C_OBJECT_IDX_1: {
				return readObjectIdxImpl1();
			}
			case ContentWriterObjectOutput.C_OBJECT_ARRAY_ERROR:
			case ContentWriterObjectOutput.C_OBJECT_ARRAY: {
				return readArrayImpl(cmd);
			}
			case ContentWriterObjectOutput.C_OBJECT_EXTERNALIZABLE_ERROR:
			case ContentWriterObjectOutput.C_OBJECT_EXTERNALIZABLE: {
				return readExternalizableImpl(cmd);
			}
			case ContentWriterObjectOutput.C_OBJECT_TYPE: {
				return readTypeImpl();
			}
			case ContentWriterObjectOutput.C_OBJECT_SERIALIZABLE_ERROR:
			case ContentWriterObjectOutput.C_OBJECT_SERIALIZABLE: {
				return readSerializedObjectImpl(cmd);
			}
			case ContentWriterObjectOutput.C_OBJECT_CUSTOM_SERIALIZABLE_ERROR:
			case ContentWriterObjectOutput.C_OBJECT_CUSTOM_SERIALIZABLE: {
				return readCustomSerializableObjectImpl(cmd);
			}
			case ContentWriterObjectOutput.C_OBJECT_ENUM: {
				return readEnumImpl();
			}
			case ContentWriterObjectOutput.C_OBJECT_VALUE: {
				return readValueObjectImpl();
			}
			case ContentWriterObjectOutput.C_OBJECT_UTF: {
				return readUTF();
			}
			case ContentWriterObjectOutput.C_OBJECT_NULL: {
				return null;
			}
			case ContentWriterObjectOutput.C_OBJECT_CLASSLOADER: {
				return readExternalClassLoader();
			}
			case ContentWriterObjectOutput.C_OBJECT_PROXY: {
				return readExternalProxy();
			}
			default: {
				throw new AssertionError();
			}
		}
	}

	@Override
	public long skip(long n) throws IOException {
		if (n <= 0) {
			return 0;
		}
		if (state.byteContentRemaining > 0) {
			long c = Math.min(state.byteContentRemaining, n);
			long skipped = state.in.skip(c);
			if (skipped > 0) {
				state.byteContentRemaining -= skipped;
			}
			return skipped;
		}
		//byteContentRemaining == 0
		//don't try to skip chars, as we cannot ensure that actually an even number of bytes are skipped
		//cannot skip, return 0
		return 0;
	}

	@Override
	public int available() throws IOException {
		return state.in.available();
	}

	@Override
	public void close() throws IOException {
	}

	int addSerializedObject(PresentSerializedObject<?> obj) {
		int idx = serializedObjects.size();
		serializedObjects.add(obj);
		return idx;
	}

	int addSerializedObject(SerializedObject<?> obj) {
		int idx = serializedObjects.size();
		serializedObjects.add(obj);
		return idx;
	}

	void setSerializedObject(int idx, SerializedObject<?> obj) {
		serializedObjects.set(idx, obj);
	}

	Class<?> readTypeWithCommand() throws IOException, ClassNotFoundException {
		int cmd = state.expectCommands(EXPECTED_COMMANDS_TYPE);
		switch (cmd) {
			case ContentWriterObjectOutput.C_OBJECT_TYPE: {
				return readTypeImpl();
			}
			case ContentWriterObjectOutput.C_OBJECT_IDX: {
				return getTypeIdxImpl(readIntImpl());
			}
			case ContentWriterObjectOutput.C_OBJECT_IDX_3: {
				return getTypeIdxImpl(readIntImpl3());
			}
			case ContentWriterObjectOutput.C_OBJECT_IDX_2: {
				return getTypeIdxImpl(readIntImpl2());
			}
			case ContentWriterObjectOutput.C_OBJECT_IDX_1: {
				return getTypeIdxImpl(readIntImpl1());
			}
			default: {
				throw new AssertionError(cmd);
			}
		}
	}

	private Class<?> readExternalClass() throws IOException, ClassNotFoundException {
		String classLoaderResolverId = readUTF();
		if (classLoaderResolverId.isEmpty()) {
			classLoaderResolverId = null;
		}
		ClassLoader cl = registry.getClassLoaderForIdentifier(classLoaderResolverId);

		String className = readUTF();
		try {
			return Class.forName(className, false, cl);
		} catch (ClassNotFoundException e) {
			throw new ClassNotFoundException(
					"Class not found with resolver id: " + StringUtils.toStringQuoted(classLoaderResolverId), e);
		}
	}

	private Object readExternalProxy() throws IOException, ClassNotFoundException {
		int idx = addSerializedObject(UnavailableSerializedObject.instance());

		//catch and collect all exceptions in order to properly pre-read the proxy data
		Exception failexc = null;
		ClassLoader cl = null;
		try {
			cl = readExternalClassLoader();
		} catch (IOException | RuntimeException e) {
			IOUtils.addExc(failexc, e);
		}
		//exception from readInt is propagated without collecting it. it is a hard failure.
		int itfslen;
		try {
			itfslen = readInt();
		} catch (Exception e) {
			Exception cause = IOUtils.addExc(failexc, e);
			setSerializedObject(idx,
					new FailedSerializedObject<>(() -> new ObjectReadException("Failed to read proxy object.", cause)));
			throw e;
		}
		Class<?>[] interfaces = new Class<?>[itfslen];
		for (int i = 0; i < itfslen; i++) {
			try {
				interfaces[i] = readTypeWithCommand();
			} catch (IOException | ClassNotFoundException | RuntimeException e) {
				IOUtils.addExc(failexc, e);
			}
		}
		Object ih;
		try {
			ih = readObject();
		} catch (IOException | ClassNotFoundException | RuntimeException e) {
			IOUtils.addExc(failexc, e);
			setSerializedObject(idx, new FailedSerializedObject<>(
					() -> new ObjectReadException("Failed to read proxy object.", failexc)));
			throw e;
		}
		if (!(ih instanceof InvocationHandler)) {
			throw new ObjectTypeException("Proxy invocation handler doesn't implement " + InvocationHandler.class
					+ " in class: " + ih.getClass().getName());
		}

		try {
			Object result = Proxy.newProxyInstance(cl, interfaces, (InvocationHandler) ih);
			setSerializedObject(idx, new PresentSerializedObject<>(result));
			return result;
		} catch (IllegalArgumentException | SecurityException e) {
			throw new SerializationReflectionException(
					"Failed to instantiate proxy object. (" + Arrays.toString(interfaces) + ")", e);
		}
	}

	private ClassLoader readExternalClassLoader() throws IOException {
		String classLoaderResolverId = readUTF();
		if (classLoaderResolverId.isEmpty()) {
			classLoaderResolverId = null;
		}
		return registry.getClassLoaderForIdentifier(classLoaderResolverId);
	}

	private Class<?> readTypeImpl() throws ClassNotFoundException, IOException {
		try {
			Class<?> result = readExternalClass();
			addSerializedObject(new PresentSerializedObject<>(result));
			return result;
		} catch (ClassNotFoundException e) {
			FailedSerializedObject<?> serializedobj = new FailedSerializedObject<>(
					() -> new ClassNotFoundException("Class not found.", e));
			addSerializedObject(serializedobj);
			throw e;
		}
	}

	private Class<?> getTypeIdxImpl(int idx) throws IOException, ClassNotFoundException {
		return (Class<?>) serializedObjects.get(idx).get();
	}

	private Object readArrayImpl(int cmd) throws IOException, ClassNotFoundException {
		Class<?> arrayclass;
		try {
			arrayclass = readTypeWithCommand();
		} catch (Exception e) {
			int len = readInt();
			checkInvalidLength(len);
			FailedSerializedObject<Object> serializedobj = new FailedSerializedObject<>(
					() -> new ClassNotFoundException("Array component class not found.", e));
			addSerializedObject(serializedobj);
			while (len-- > 0) {
				preReadSingle();
			}
			return serializedobj.get();
		}
		int len = readInt();
		checkInvalidLength(len);
		if (cmd == ContentWriterObjectOutput.C_OBJECT_ARRAY_ERROR) {
			FailedSerializedObject<Object> serializedobj = new FailedSerializedObject<>(
					() -> new ObjectWriteException("Failed to write array object."));
			addSerializedObject(serializedobj);
			while (len-- > 0) {
				preReadSingle();
			}
			return serializedobj.get();
		}

		Class<?> componenttype = arrayclass.getComponentType();
		Object array = Array.newInstance(componenttype, len);
		int serobjidx = addSerializedObject(new PresentSerializedObject<>(array));
		IOFunction<DataInput, Object> reader = primitiveArrayElementReaders.get(componenttype);
		if (reader != null) {
			for (int i = 0; i < len; i++) {
				Object readelement = reader.apply(state.in);
				Array.set(array, i, readelement);
			}
		} else {
			Object[] objarr = (Object[]) array;
			for (int i = 0; i < len; i++) {
				try {
					Object obj = readObject();
					objarr[i] = obj;
				} catch (Exception e) {
					int idx = i;
					FailedSerializedObject<Object> serializedobj = new FailedSerializedObject<>(
							() -> new ObjectReadException("Failed to read array element at index: " + idx, e));
					setSerializedObject(serobjidx, serializedobj);
					while (++i < len) {
						preReadSingle();
					}
					return serializedobj.get();
				}
			}
		}
		return array;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Enum<?> readEnumImpl() throws IOException, ClassNotFoundException {
		Class type;
		try {
			type = readTypeWithCommand();
		} catch (ClassNotFoundException e) {
			//read the name nonetheless not to distrupt the protocol
			String name = DataInputUnsyncByteArrayInputStream.readStringLengthChars(state.in);
			FailedSerializedObject<Enum<?>> serializedobj = new FailedSerializedObject<>(
					() -> new SerializationReflectionException("Enum class not found for enum name: " + name, e));
			addSerializedObject(serializedobj);
			return serializedobj.get();
		}
		String name = DataInputUnsyncByteArrayInputStream.readStringLengthChars(state.in);
		try {
			Enum<?> result = Enum.valueOf(type, name);
			addSerializedObject(new PresentSerializedObject<>(result));
			return result;
		} catch (IllegalArgumentException e) {
			FailedSerializedObject<Enum<?>> serializedobj = new FailedSerializedObject<>(
					() -> new SerializationReflectionException("Enum value not found: " + type.getName() + "." + name,
							e));
			addSerializedObject(serializedobj);
			return serializedobj.get();
		}
	}

	private Object readCustomSerializableObjectImpl(int cmd) throws IOException, ClassNotFoundException {
		Class<?> type = readTypeWithCommand();
		int len = state.in.readInt();
		checkInvalidLength(len);

		LimitInputStream limitis = new LimitInputStream(this.state.actualInput, len);
		ReadState nstate = new ReadState(limitis);
		ReadState prevstate = this.state;
		this.state = nstate;
		try {
			if (cmd == ContentWriterObjectOutput.C_OBJECT_CUSTOM_SERIALIZABLE_ERROR) {
				FailedSerializedObject<Object> serializedobj = new FailedSerializedObject<>(
						() -> new ObjectWriteException("Failed to write object."));
				addSerializedObject(serializedobj);
				return serializedobj.get();
			}
			ObjectReaderFunction<ContentReaderObjectInput, Object> reader = ContentWriterObjectOutput.SERIALIZABLE_CLASS_READERS
					.get(type);
			if (reader == null) {
				FailedSerializedObject<Object> serializedobj = new FailedSerializedObject<>(
						() -> new SerializationProtocolException("No object reader found for class: " + type));
				addSerializedObject(serializedobj);
				return serializedobj.get();
			}

			Object result = reader.apply(this);
			if (limitis.getRemainingLimit() > 0) {
				//the contents were not fully read by the readExternal function
				if (warnedNotFullyReadClasses.add(type)) {
					if (TestFlag.ENABLED) {
						TestFlag.metric().serializationWarning(type.getName());
					}
					SakerLog.warning().out(System.err)
							.println("Failed to fully read all serialized data from stream: " + type.getName());
				}
			}
			return result;
		} finally {
			try {
				preReadRemaining();
			} finally {
				this.state = prevstate;
			}
		}
	}

	private Object readSerializedObjectImpl(int cmd) throws IOException, ClassNotFoundException {
		int len = state.in.readInt();
		checkInvalidLength(len);
		if (cmd == ContentWriterObjectOutput.C_OBJECT_SERIALIZABLE_ERROR) {
			StreamUtils.skipStreamExactly(state.in, len);
			FailedSerializedObject<Object> serializedobj = new FailedSerializedObject<>(
					() -> new ObjectWriteException("Failed to write Serializable object."));
			addSerializedObject(serializedobj);
			return serializedobj.get();
		}
		LimitInputStream limit = new LimitInputStream(state.in, len);
		Object result;
		try (ObjectInputStream ois = new ClassLoaderResolverObjectInputStream(registry,
				StreamUtils.closeProtectedInputStream(limit))) {
			result = ois.readObject();
		} catch (Exception e) {
			FailedSerializedObject<Object> serializedobj = new FailedSerializedObject<>(
					() -> new ObjectReadException("Failed to read Serializable object.", e));
			addSerializedObject(serializedobj);
			StreamUtils.consumeStream(limit);
			return serializedobj.get();
		}
		if (limit.getRemainingLimit() > 0) {
			SakerLog.warning().out(System.err)
					.println("Serializable object failed to read all data. (" + ObjectUtils.classOf(result) + ")");
		}
		addSerializedObject(new PresentSerializedObject<>(result));
		return result;
	}

	private Object readValueObjectImpl() throws IOException, ClassNotFoundException {
		Class<?> type = readTypeWithCommand();
		ObjectReaderFunction<ContentReaderObjectInput, ?> reader = ContentWriterObjectOutput.VALUE_CLASS_READERS
				.get(type);
		if (reader == null) {
			//fatal error. we cannot preread the remaining, as we don't know whats there
			throw new SerializationProtocolException("No value object reader found for class: " + type);
		}
		return reader.apply(this);
	}

	private Object readObjectIdxImpl() throws IOException, ClassNotFoundException {
		return getObjectIdxImpl(readIntImpl());
	}

	private Object readObjectIdxImpl3() throws IOException, ClassNotFoundException {
		return getObjectIdxImpl(readIntImpl3());
	}

	private Object readObjectIdxImpl2() throws IOException, ClassNotFoundException {
		return getObjectIdxImpl(readIntImpl2());
	}

	private Object readObjectIdxImpl1() throws IOException, ClassNotFoundException {
		return getObjectIdxImpl(readIntImpl1());
	}

	private Object getObjectIdxImpl(int idx)
			throws SerializationProtocolException, IOException, ClassNotFoundException {
		int size = serializedObjects.size();
		if (idx >= size) {
			throw new SerializationProtocolException(
					"Referenced object not found at index: " + idx + " (current size: " + size + ")");
		}
		return serializedObjects.get(idx).get();
	}

	private Externalizable readExternalizableImpl(int cmd) throws IOException, ClassNotFoundException {
		Class<?> type;
		try {
			type = readTypeWithCommand();
		} catch (ClassNotFoundException e) {
			FailedSerializedObject<Externalizable> serializedobj = new FailedSerializedObject<>(
					() -> new ClassNotFoundException("Externalizable class not found.", e));
			addSerializedObject(serializedobj);
			int len = state.in.readInt();
			checkInvalidLength(len);
			preReadExternalizableHeaderFailure(len);
			return serializedobj.get();
		}
		int len = state.in.readInt();
		checkInvalidLength(len);

		if (cmd == ContentWriterObjectOutput.C_OBJECT_EXTERNALIZABLE_ERROR) {
			FailedSerializedObject<Externalizable> serializedobj = new FailedSerializedObject<>(
					() -> new ObjectWriteException("Failed to write Externalizable object. (" + type.getName() + ")"));
			addSerializedObject(serializedobj);
			preReadExternalizableHeaderFailure(len);
			return serializedobj.get();
		}

		Externalizable instance;
		try {
			instance = (Externalizable) ReflectUtils.newInstance(type);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException | ClassCastException e) {
			addSerializedObject(new FailedSerializedObject<>(() -> new SerializationReflectionException(
					"Failed to instantiate Externalizable: " + type.getName(), e)));
			preReadExternalizableHeaderFailure(len);
			throw new SerializationReflectionException("Failed to instantiate Externalizable: " + type.getName(), e);
		}

		int serobjidx = addSerializedObject(new PresentSerializedObject<>(instance));

		LimitInputStream limitis = new LimitInputStream(this.state.actualInput, len);
		ReadState nstate = new ReadState(limitis);
		ReadState prevstate = this.state;
		this.state = nstate;
		try {
			instance.readExternal(this);
			if (limitis.getRemainingLimit() > 0) {
				//the contents were not fully read by the readExternal function
				if (warnedNotFullyReadClasses.add(type)) {
					if (TestFlag.ENABLED) {
						TestFlag.metric().serializationWarning(type.getName());
					}
					SakerLog.warning().out(System.err).println(
							"Externalizable failed to fully read all serialized data from stream: " + type.getName());
				}
			}
		} catch (Exception e) {
			FailedSerializedObject<Externalizable> serializedobj = new FailedSerializedObject<>(
					() -> new ObjectReadException("Failed to read Externalizable object. (" + type.getName() + ")", e));
			setSerializedObject(serobjidx, serializedobj);
			return serializedobj.get();
		} finally {
			try {
				preReadRemaining();
			} finally {
				this.state = prevstate;
			}
		}
		return instance;
	}

	private void preReadExternalizableHeaderFailure(int len) throws IOException {
		if (len <= 0) {
			return;
		}
		LimitInputStream limitis = new LimitInputStream(this.state.actualInput, len);
		ReadState nstate = new ReadState(limitis);
		ReadState prevstate = this.state;
		this.state = nstate;
		try {
			preReadRemaining();
		} finally {
			this.state = prevstate;
		}
	}

	private void preReadRemaining() {
		try {
			if (state.byteContentRemaining > 0) {
				StreamUtils.skipStreamExactly(state.in, state.byteContentRemaining);
				state.byteContentRemaining = 0;
			}
			if (state.charContentRemaining > 0) {
				StreamUtils.skipStreamExactly(state.in, state.charContentRemaining);
				state.charContentRemaining = 0;
			}
		} catch (IOException e) {
			//ignoreable protocol errors
			return;
		}
		while (true) {
			int cmd = state.takeCommandIfAny();
			if (cmd < 0) {
				//eof
				break;
			}
			preReadCommand(cmd);
		}
	}

	private void preReadSingle() {
		int cmd = state.takeCommandIfAny();
		if (cmd < 0) {
			return;
		}
		preReadCommand(cmd);
	}

	private void preReadCommand(int cmd) {
		try {
			switch (cmd) {
				case ContentWriterObjectOutput.C_OBJECT_ARRAY_ERROR:
				case ContentWriterObjectOutput.C_OBJECT_ARRAY: {
					readArrayImpl(cmd);
					break;
				}
				case ContentWriterObjectOutput.C_OBJECT_SERIALIZABLE_ERROR:
				case ContentWriterObjectOutput.C_OBJECT_SERIALIZABLE: {
					readSerializedObjectImpl(cmd);
					break;
				}
				case ContentWriterObjectOutput.C_OBJECT_CUSTOM_SERIALIZABLE_ERROR:
				case ContentWriterObjectOutput.C_OBJECT_CUSTOM_SERIALIZABLE: {
					readCustomSerializableObjectImpl(cmd);
					break;
				}
				case ContentWriterObjectOutput.C_OBJECT_TYPE: {
					readTypeImpl();
					break;
				}
				case ContentWriterObjectOutput.C_OBJECT_EXTERNALIZABLE_ERROR:
				case ContentWriterObjectOutput.C_OBJECT_EXTERNALIZABLE: {
					readExternalizableImpl(cmd);
					break;
				}
				case ContentWriterObjectOutput.C_OBJECT_ENUM: {
					readEnumImpl();
					break;
				}
				case ContentWriterObjectOutput.C_OBJECT_VALUE: {
					readValueObjectImpl();
					break;
				}
				case ContentWriterObjectOutput.C_OBJECT_UTF: {
					readUTF();
					break;
				}
				case ContentWriterObjectOutput.C_OBJECT_NULL: {
					break;
				}
				case ContentWriterObjectOutput.C_OBJECT_IDX: {
					readIntImpl();
					break;
				}
				case ContentWriterObjectOutput.C_OBJECT_IDX_3: {
					readIntImpl3();
					break;
				}
				case ContentWriterObjectOutput.C_OBJECT_IDX_2: {
					readIntImpl2();
					break;
				}
				case ContentWriterObjectOutput.C_OBJECT_IDX_1: {
					readIntImpl1();
					break;
				}
				case ContentWriterObjectOutput.C_OBJECT_PROXY: {
					readExternalProxy();
					break;
				}
				case ContentWriterObjectOutput.C_UTF: {
					readUTFImpl();
					break;
				}
				case ContentWriterObjectOutput.C_UTF_LOWBYTES: {
					readUTFLowBytesImpl();
					break;
				}
				case ContentWriterObjectOutput.C_UTF_IDX: {
					//idx
					state.in.readInt();
					break;
				}
				case ContentWriterObjectOutput.C_UTF_IDX_3: {
					state.in.readByte();
					state.in.readShort();
					break;
				}
				case ContentWriterObjectOutput.C_UTF_IDX_2: {
					state.in.readShort();
					break;
				}
				case ContentWriterObjectOutput.C_UTF_IDX_1: {
					state.in.readByte();
					break;
				}
				case ContentWriterObjectOutput.C_BOOLEAN: {
					state.in.readBoolean();
					break;
				}
				case ContentWriterObjectOutput.C_BYTE: {
					state.in.readByte();
					break;
				}
				case ContentWriterObjectOutput.C_BYTEARRAY: {
					int len = readInt();
					checkInvalidLength(len);
					StreamUtils.skipStreamExactly(state.in, len);
					break;
				}
				case ContentWriterObjectOutput.C_CHAR: {
					state.in.readChar();
					break;
				}
				case ContentWriterObjectOutput.C_CHARS: {
					int charcount = readInt();
					checkInvalidLength(charcount);
					StreamUtils.skipStreamExactly(state.in, charcount * 2);
					break;
				}
				case ContentWriterObjectOutput.C_DOUBLE: {
					state.in.readDouble();
					break;
				}
				case ContentWriterObjectOutput.C_FLOAT: {
					state.in.readFloat();
					break;
				}
				case ContentWriterObjectOutput.C_INT_1:
				case ContentWriterObjectOutput.C_INT_F_1: {
					readIntImpl1();
					break;
				}
				case ContentWriterObjectOutput.C_INT_2:
				case ContentWriterObjectOutput.C_INT_F_2: {
					readIntImpl2();
					break;
				}
				case ContentWriterObjectOutput.C_INT_3:
				case ContentWriterObjectOutput.C_INT_F_3: {
					readIntImpl3();
					break;
				}
				case ContentWriterObjectOutput.C_INT: {
					readIntImpl();
					break;
				}
				case ContentWriterObjectOutput.C_INT_ZERO:
				case ContentWriterObjectOutput.C_INT_NEGATIVE_ONE: {
					break;
				}
				case ContentWriterObjectOutput.C_LONG: {
					readLongImpl();
					break;
				}
				case ContentWriterObjectOutput.C_LONG_2:
				case ContentWriterObjectOutput.C_LONG_F_2: {
					readLongImpl2();
					break;
				}
				case ContentWriterObjectOutput.C_LONG_4:
				case ContentWriterObjectOutput.C_LONG_F_4: {
					readLongImpl4();
					break;
				}
				case ContentWriterObjectOutput.C_LONG_6:
				case ContentWriterObjectOutput.C_LONG_F_6: {
					readLongImpl6();
					break;
				}
				case ContentWriterObjectOutput.C_LONG_ZERO:
				case ContentWriterObjectOutput.C_LONG_NEGATIVE_ONE: {
					break;
				}
				case ContentWriterObjectOutput.C_SHORT: {
					state.in.readShort();
					break;
				}
				case ContentWriterObjectOutput.C_OBJECT_CLASSLOADER: {
					readUTF();
					break;
				}
				default: {
					throw new SerializationProtocolException("Invalid command: " + cmd);
				}
			}
		} catch (IOException | ClassNotFoundException e) {
			//ignore exceptions, if they are relevant, they will be visible through the serialized object lookup
		}
	}

	private static void checkInvalidLength(int len) throws SerializationProtocolException {
		if (len < 0) {
			throw new SerializationProtocolException("Invalid length read: " + len);
		}
	}
}