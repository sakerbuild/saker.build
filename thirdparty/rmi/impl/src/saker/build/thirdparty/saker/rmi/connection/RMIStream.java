package saker.build.thirdparty.saker.rmi.connection;

import java.io.Closeable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.rmi.connection.RequestHandler.Request;
import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMICallForbiddenException;
import saker.build.thirdparty.saker.rmi.exception.RMIIOFailureException;
import saker.build.thirdparty.saker.rmi.exception.RMIInvalidConfigurationException;
import saker.build.thirdparty.saker.rmi.exception.RMIObjectTransferFailureException;
import saker.build.thirdparty.saker.rmi.exception.RMIProxyCreationFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.rmi.exception.RMIStackTracedException;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.rmi.io.writer.ArrayComponentRMIObjectWriteHandler;
import saker.build.thirdparty.saker.rmi.io.writer.ObjectWriterKind;
import saker.build.thirdparty.saker.rmi.io.writer.RMIObjectWriteHandler;
import saker.build.thirdparty.saker.rmi.io.writer.SelectorRMIObjectWriteHandler;
import saker.build.thirdparty.saker.rmi.io.writer.WrapperRMIObjectWriteHandler;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.DataInputUnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.DataOutputUnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.StreamPair;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncBufferedInputStream;
import saker.build.thirdparty.saker.util.io.function.IOBiConsumer;
import saker.build.thirdparty.saker.util.io.function.IOTriFunction;
import saker.build.thirdparty.saker.util.ref.StrongSoftReference;

// suppress the warnings of unused resource in try-with-resource body
// this is used when we write a command and automatically flush with outputFlusher
@SuppressWarnings("try")
final class RMIStream implements Closeable {
	public static final String EXCEPTION_MESSAGE_DIRECT_REQUESTS_FORBIDDEN = "Direct requests are forbidden.";

	private static final short COMMAND_NEWINSTANCE = 1;
	private static final short COMMAND_METHODCALL = 2;
	private static final short COMMAND_METHODRESULT = 3;
	private static final short COMMAND_NEWINSTANCE_RESULT = 4;
	private static final short COMMAND_METHODRESULT_FAIL = 5;
	private static final short COMMAND_NEWINSTANCERESULT_FAIL = 6;
	private static final short COMMAND_METHODCALL_REDISPATCH = 7;
	private static final short COMMAND_GET_CONTEXT_VAR = 8;
	private static final short COMMAND_GET_CONTEXT_VAR_RESPONSE = 9;
	private static final short COMMAND_NEW_VARIABLES = 10;
	private static final short COMMAND_NEW_VARIABLES_RESULT = 11;
	private static final short COMMAND_CLOSE_VARIABLES = 12;
	private static final short COMMAND_NEWINSTANCE_UNKNOWNCLASS = 13;
	private static final short COMMAND_UNKNOWN_NEWINSTANCE_RESULT = 14;
	private static final short COMMAND_NEWINSTANCE_UNKNOWNCLASS_REDISPATCH = 15;
	private static final short COMMAND_NEWINSTANCE_REDISPATCH = 16;
	private static final short COMMAND_REFERENCES_RELEASED = 17;
	private static final short COMMAND_STREAM_CLOSED = 18;
	private static final short COMMAND_PING = 19;
	private static final short COMMAND_PONG = 20;
	private static final short COMMAND_CACHED_CLASS = 21;
	private static final short COMMAND_CACHED_METHOD = 22;
	private static final short COMMAND_CACHED_CONSTRUCTOR = 23;
	private static final short COMMAND_CACHED_CLASSLOADER = 24;
	private static final short COMMAND_CACHED_FIELD = 25;
	private static final short COMMAND_INTERRUPT_REQUEST = 26;
	private static final short COMMAND_DIRECT_REQUEST_FORBIDDEN = 27;
	private static final short COMMAND_METHODCALL_ASYNC = 28;

	private static final short COMMAND_END_VALUE = 29;

	private static final short OBJECT_NULL = 0;
	private static final short OBJECT_BOOLEAN = 1;
	private static final short OBJECT_BYTE = 2;
	private static final short OBJECT_SHORT = 3;
	private static final short OBJECT_INT = 4;
	private static final short OBJECT_LONG = 5;
	private static final short OBJECT_CHAR = 6;
	private static final short OBJECT_FLOAT = 7;
	private static final short OBJECT_DOUBLE = 8;
	private static final short OBJECT_STRING = 9;
	private static final short OBJECT_ARRAY = 10;
	private static final short OBJECT_ENUM = 11;
	private static final short OBJECT_REMOTE = 12;
	private static final short OBJECT_NEW_REMOTE = 13;
	private static final short OBJECT_EXTERNALIZABLE = 14;
	private static final short OBJECT_CLASS = 15;
	private static final short OBJECT_METHOD = 16;
	private static final short OBJECT_CONSTRUCTOR = 17;
	private static final short OBJECT_SERIALIZED = 18;
	private static final short OBJECT_WRAPPER = 19;
	private static final short OBJECT_BYTE_ARRAY = 20;
	private static final short OBJECT_SHORT_ARRAY = 21;
	private static final short OBJECT_INT_ARRAY = 22;
	private static final short OBJECT_LONG_ARRAY = 23;
	private static final short OBJECT_FLOAT_ARRAY = 24;
	private static final short OBJECT_DOUBLE_ARRAY = 25;
	private static final short OBJECT_BOOLEAN_ARRAY = 26;
	private static final short OBJECT_CHAR_ARRAY = 27;
	private static final short OBJECT_CLASSLOADER = 28;
	private static final short OBJECT_FIELD = 29;
	private static final short OBJECT_READER_END_VALUE = 30;

	private static final short CLASS_DETAILS = 0;
	private static final short CLASS_INDEX = 1;

	private static final short CLASSLOADER_DETAILS = 0;
	private static final short CLASSLOADER_INDEX = 1;
	private static final short CLASSLOADER_NULL = 2;

	private static final short METHOD_DETAILS = 0;
	private static final short METHOD_INDEX = 1;

	private static final short CONSTRUCTOR_DETAILS = 0;
	private static final short CONSTRUCTOR_INDEX = 1;

	private static final short FIELD_DETAILS = 0;
	private static final short FIELD_INDEX = 1;

	private static final Map<Short, IOTriFunction<RMIStream, RMIVariables, DataInputUnsyncByteArrayInputStream, Object>> OBJECT_READERS = new HashMap<>(
			OBJECT_READER_END_VALUE * 2);
	private static final IOTriFunction<RMIStream, RMIVariables, DataInputUnsyncByteArrayInputStream, Object> OBJECT_READER_UNKNOWN = (
			s, vars, in) -> {
		throw new RMICallFailedException("Unknown object type.");
	};
	static {
		OBJECT_READERS.put(OBJECT_NULL, (s, vars, in) -> null);
		OBJECT_READERS.put(OBJECT_BOOLEAN, (s, vars, in) -> in.readBoolean());
		OBJECT_READERS.put(OBJECT_BYTE, (s, vars, in) -> in.readByte());
		OBJECT_READERS.put(OBJECT_SHORT, (s, vars, in) -> in.readShort());
		OBJECT_READERS.put(OBJECT_INT, (s, vars, in) -> in.readInt());
		OBJECT_READERS.put(OBJECT_LONG, (s, vars, in) -> in.readLong());
		OBJECT_READERS.put(OBJECT_FLOAT, (s, vars, in) -> in.readFloat());
		OBJECT_READERS.put(OBJECT_DOUBLE, (s, vars, in) -> in.readDouble());
		OBJECT_READERS.put(OBJECT_CHAR, (s, vars, in) -> in.readChar());
		OBJECT_READERS.put(OBJECT_STRING, (s, vars, in) -> readString(in));
		OBJECT_READERS.put(OBJECT_ARRAY, RMIStream::readObjectArray);
		OBJECT_READERS.put(OBJECT_ENUM, (s, vars, in) -> s.readEnum(in));
		OBJECT_READERS.put(OBJECT_REMOTE, (s, vars, in) -> readRemoteObject(vars, in));
		OBJECT_READERS.put(OBJECT_NEW_REMOTE, RMIStream::readNewRemoteObject);
		OBJECT_READERS.put(OBJECT_EXTERNALIZABLE, RMIStream::readExternalizableObject);
		OBJECT_READERS.put(OBJECT_CLASS, (s, vars, in) -> s.readClass(in));
		OBJECT_READERS.put(OBJECT_METHOD, (s, vars, in) -> s.readMethod(in, null));
		OBJECT_READERS.put(OBJECT_CONSTRUCTOR, (s, vars, in) -> s.readConstructor(in));
		OBJECT_READERS.put(OBJECT_SERIALIZED, (s, vars, in) -> s.readSerializedObject(in));
		OBJECT_READERS.put(OBJECT_WRAPPER, RMIStream::readWrappedObject);

		OBJECT_READERS.put(OBJECT_BYTE_ARRAY, (s, vars, in) -> readObjectByteArray(in));
		OBJECT_READERS.put(OBJECT_SHORT_ARRAY, (s, vars, in) -> readObjectShortArray(in));
		OBJECT_READERS.put(OBJECT_INT_ARRAY, (s, vars, in) -> readObjectIntArray(in));
		OBJECT_READERS.put(OBJECT_LONG_ARRAY, (s, vars, in) -> readObjectLongArray(in));
		OBJECT_READERS.put(OBJECT_FLOAT_ARRAY, (s, vars, in) -> readObjectFloatArray(in));
		OBJECT_READERS.put(OBJECT_DOUBLE_ARRAY, (s, vars, in) -> readObjectDoubleArray(in));
		OBJECT_READERS.put(OBJECT_BOOLEAN_ARRAY, (s, vars, in) -> readObjectBooleanArray(in));
		OBJECT_READERS.put(OBJECT_CHAR_ARRAY, (s, vars, in) -> readObjectCharArray(in));
		OBJECT_READERS.put(OBJECT_CLASSLOADER, (s, vars, in) -> s.readClassLoader(in));
		OBJECT_READERS.put(OBJECT_FIELD, (s, vars, in) -> s.readField(in, null));
	}
	private static final Map<Short, IOBiConsumer<RMIStream, DataInputUnsyncByteArrayInputStream>> COMMAND_HANDLERS = new HashMap<>(
			COMMAND_END_VALUE * 2);
	static {
		COMMAND_HANDLERS.put(COMMAND_NEWINSTANCE, RMIStream::handleCommandNewInstance);
		COMMAND_HANDLERS.put(COMMAND_NEWINSTANCE_REDISPATCH, RMIStream::handleCommandNewInstanceRedispatch);
		COMMAND_HANDLERS.put(COMMAND_NEWINSTANCE_UNKNOWNCLASS, RMIStream::handleCommandNewInstanceUnknownClass);
		COMMAND_HANDLERS.put(COMMAND_NEWINSTANCE_UNKNOWNCLASS_REDISPATCH,
				RMIStream::handleCommandNewInstanceUnknownClassRedispatch);
		COMMAND_HANDLERS.put(COMMAND_METHODCALL, RMIStream::handleCommandMethodCall);
		COMMAND_HANDLERS.put(COMMAND_METHODCALL_REDISPATCH, RMIStream::handleCommandMethodCallRedispatch);
		COMMAND_HANDLERS.put(COMMAND_METHODRESULT, RMIStream::handleCommandMethodResult);
		COMMAND_HANDLERS.put(COMMAND_UNKNOWN_NEWINSTANCE_RESULT, RMIStream::handleCommandNewInstanceUnknownClassResult);
		COMMAND_HANDLERS.put(COMMAND_METHODRESULT_FAIL, RMIStream::handleCommandMethodResultFailure);
		COMMAND_HANDLERS.put(COMMAND_NEWINSTANCERESULT_FAIL, RMIStream::handleCommandNewInstanceResultFailure);
		COMMAND_HANDLERS.put(COMMAND_NEW_VARIABLES, RMIStream::handleCommandNewVariables);
		COMMAND_HANDLERS.put(COMMAND_GET_CONTEXT_VAR, RMIStream::handleCommandGetContextVariable);
		COMMAND_HANDLERS.put(COMMAND_GET_CONTEXT_VAR_RESPONSE, RMIStream::handleCommandGetContextVariableResult);
		COMMAND_HANDLERS.put(COMMAND_NEWINSTANCE_RESULT, RMIStream::handleCommandNewInstanceResult);
		COMMAND_HANDLERS.put(COMMAND_NEW_VARIABLES_RESULT, RMIStream::handleCommandNewVariablesResult);
		COMMAND_HANDLERS.put(COMMAND_REFERENCES_RELEASED, RMIStream::handleCommandReferencesReleased);
		COMMAND_HANDLERS.put(COMMAND_CLOSE_VARIABLES, RMIStream::handleCommandCloseVariables);
		COMMAND_HANDLERS.put(COMMAND_PING, RMIStream::handleCommandPing);
		COMMAND_HANDLERS.put(COMMAND_PONG, RMIStream::handleCommandPong);
		COMMAND_HANDLERS.put(COMMAND_CACHED_CLASS, RMIStream::handleCommandCachedClass);
		COMMAND_HANDLERS.put(COMMAND_CACHED_CONSTRUCTOR, RMIStream::handleCommandCachedConstructor);
		COMMAND_HANDLERS.put(COMMAND_CACHED_CLASSLOADER, RMIStream::handleCommandCachedClassLoader);
		COMMAND_HANDLERS.put(COMMAND_CACHED_METHOD, RMIStream::handleCommandCachedMethod);
		COMMAND_HANDLERS.put(COMMAND_CACHED_FIELD, RMIStream::handleCommandCachedField);
		COMMAND_HANDLERS.put(COMMAND_INTERRUPT_REQUEST, RMIStream::handleCommandInterruptRequest);
		COMMAND_HANDLERS.put(COMMAND_DIRECT_REQUEST_FORBIDDEN, RMIStream::handleCommandDirectRequestForbidden);
		COMMAND_HANDLERS.put(COMMAND_METHODCALL_ASYNC, RMIStream::handleCommandMethodCallAsync);
	}

	protected final RMIConnection connection;
	protected final ThreadLocal<AtomicInteger> currentThreadPreviousMethodCallRequestId;
	protected final RequestHandler requestHandler;

	protected final BlockOutputStream blockOut;
	protected final BlockInputStream blockIn;

	protected volatile boolean streamCloseWritten = false;

	protected final Object outLock = new Object();

	private final RMIWeakCommCache<Class<?>> commClasses;
	private final RMIWeakCommCache<ClassLoader> commClassLoaders;

	private final RMIGeneratingCommCache<Method> commMethods;
	private final RMIGeneratingCommCache<Constructor<?>> commConstructors;
	private final RMIGeneratingCommCache<Field> commFields;

	private final ClassLoader nullClassLoader;

	private class CommandFlusher implements Closeable {
		final StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> buffer;

		public CommandFlusher() {
			this.buffer = connection.getCachedByteBuffer();
		}

		public DataOutputUnsyncByteArrayOutputStream getBuffer() {
			return buffer.get();
		}

		@Override
		public void close() throws IOException {
			try {
				checkClosed();

				synchronized (outLock) {
					//XXX we might remove checkClosed calls from the command writers
					getBuffer().writeTo(blockOut);
					blockOut.nextBlock();
					//need to flush, as the underlying output stream might be buffered, or anything
					blockOut.flush();
				}
			} catch (IOException e) {
				streamError(e);
				throw e;
			} finally {
				connection.releaseCachedByteBuffer(buffer);
			}
		}
	}

	public RMIStream(RMIConnection connection, InputStream is, OutputStream os) {
		this.currentThreadPreviousMethodCallRequestId = connection
				.getCurrentThreadPreviousMethodCallRequestIdThreadLocal();
		this.requestHandler = connection.getRequestHandler();

		this.blockOut = new BlockOutputStream(os);
		this.blockIn = new BlockInputStream(new UnsyncBufferedInputStream(is));

		this.connection = connection;

		RMICommState commState = new RMICommState();
		commClasses = commState.getClasses();
		commClassLoaders = commState.getClassLoaders();
		commMethods = commState.getMethods();
		commConstructors = commState.getConstructors();
		commFields = commState.getFields();

		this.nullClassLoader = connection.getNullClassLoader();
	}

	public RMIStream(RMIConnection connection, StreamPair streams) {
		this(connection, streams.getInput(), streams.getOutput());
	}

	public void start() {
		connection.offerStreamTask(this::runInput);
	}

	@Override
	public void close() {
		if (!streamCloseWritten) {
			synchronized (outLock) {
				if (!streamCloseWritten) {
					streamCloseWritten = true;
					try {
						byte[] buf = { (COMMAND_STREAM_CLOSED >>> 8), COMMAND_STREAM_CLOSED };
						blockOut.write(buf);
						blockOut.nextBlock();
						blockOut.flush();
					} catch (IOException e) {
						//dont care, it might be closed already
					}
					IOUtils.closePrint(blockOut);
				}
			}
		}
	}

	private static void writeNullObject(DataOutputUnsyncByteArrayOutputStream out) {
		out.writeShort(OBJECT_NULL);
	}

	private static final Set<Class<?>> NON_CUSTOMIZABLE_SERIALIZE_TYPES = ImmutableUtils.makeImmutableHashSet(
			new Class<?>[] { Void.class, void.class, boolean.class, Boolean.class, byte.class, Byte.class, short.class,
					Short.class, int.class, Integer.class, long.class, Long.class, float.class, Float.class,
					double.class, Double.class, char.class, Character.class, String.class, byte[].class, short[].class,
					int[].class, long[].class, float[].class, double[].class, boolean[].class, char[].class });

	static boolean isNotCustomizableSerializeType(Class<?> type) {
		return NON_CUSTOMIZABLE_SERIALIZE_TYPES.contains(type);
	}

	private final Map<Class<?>, IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, ?>> reflectionTypeWriters = new HashMap<>(
			50);
	{
		reflectionTypeWriters.put(Class.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Class<?>>) this::writeObjectClass);
		reflectionTypeWriters.put(Method.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Method>) this::writeObjectMethod);
		reflectionTypeWriters.put(Constructor.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Constructor<?>>) this::writeObjectConstructor);
		reflectionTypeWriters.put(Field.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Field>) this::writeObjectField);
	}
	private final Map<Class<?>, IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, ?>> typeWriters = new HashMap<>(50);
	{
		typeWriters.put(Void.class, (out, v) -> writeNullObject(out));
		typeWriters.put(void.class, (out, v) -> writeNullObject(out));

		typeWriters.put(boolean.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Boolean>) RMIStream::writeObjectBoolean);
		typeWriters.put(Boolean.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Boolean>) RMIStream::writeObjectBoolean);

		typeWriters.put(byte.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Byte>) RMIStream::writeObjectByte);
		typeWriters.put(Byte.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Byte>) RMIStream::writeObjectByte);

		typeWriters.put(short.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Short>) RMIStream::writeObjectShort);
		typeWriters.put(Short.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Short>) RMIStream::writeObjectShort);

		typeWriters.put(int.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Integer>) RMIStream::writeObjectInt);
		typeWriters.put(Integer.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Integer>) RMIStream::writeObjectInt);

		typeWriters.put(long.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Long>) RMIStream::writeObjectLong);
		typeWriters.put(Long.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Long>) RMIStream::writeObjectLong);

		typeWriters.put(float.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Float>) RMIStream::writeObjectFloat);
		typeWriters.put(Float.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Float>) RMIStream::writeObjectFloat);

		typeWriters.put(double.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Double>) RMIStream::writeObjectDouble);
		typeWriters.put(Double.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Double>) RMIStream::writeObjectDouble);

		typeWriters.put(char.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Character>) RMIStream::writeObjectChar);
		typeWriters.put(Character.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Character>) RMIStream::writeObjectChar);

		typeWriters.put(String.class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, String>) RMIStream::writeObjectString);

		typeWriters.put(byte[].class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, byte[]>) RMIStream::writeObjectByteArray);
		typeWriters.put(short[].class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, short[]>) RMIStream::writeObjectShortArray);
		typeWriters.put(int[].class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, int[]>) RMIStream::writeObjectIntArray);
		typeWriters.put(long[].class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, long[]>) RMIStream::writeObjectLongArray);
		typeWriters.put(float[].class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, float[]>) RMIStream::writeObjectFloatArray);
		typeWriters.put(double[].class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, double[]>) RMIStream::writeObjectDoubleArray);
		typeWriters.put(boolean[].class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, boolean[]>) RMIStream::writeObjectBooleanArray);
		typeWriters.put(char[].class,
				(IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, char[]>) RMIStream::writeObjectCharArray);

	}

	private void writeCustomizableWithWriteHandler(RMIVariables variables, Object obj, Class<?> targettype,
			DataOutputUnsyncByteArrayOutputStream out, RMIObjectWriteHandler writehandler) throws IOException {
		ObjectWriterKind kind = writehandler.getKind();
		switch (kind) {
			case DEFAULT: {
				writeObjectDefault(variables, obj, targettype, out);
				break;
			}
			case ENUM: {
				Enum<?> en;
				try {
					en = (Enum<?>) obj;
				} catch (ClassCastException e) {
					throw new RMIObjectTransferFailureException(
							"Failed to cast object to Enum. (" + obj.getClass() + ":" + obj + ")", e);
				}
				writeObjectEnum(out, en);
				break;
			}
			case REMOTE: {
				writeRemoteObject(variables, obj, out);
				break;
			}
			case REMOTE_ONLY: {
				writeOnlyIfRemote(variables, obj, out);
				break;
			}
			case SERIALIZE: {
				writeSerializedObject(obj, out);
				break;
			}
			case ARRAY_COMPONENT: {
				ArrayComponentRMIObjectWriteHandler arraywriter = (ArrayComponentRMIObjectWriteHandler) writehandler;
				Class<?> targetcomponenttype = targettype.getComponentType();
				writeObjectObjectArray(variables, out,
						targetcomponenttype == null ? obj.getClass().getComponentType() : targetcomponenttype, obj,
						arraywriter.getComponentWriter());
				break;
			}
			case SELECTOR: {
				SelectorRMIObjectWriteHandler selectorwriter = (SelectorRMIObjectWriteHandler) writehandler;
				RMIObjectWriteHandler selected = selectorwriter.selectWriteHandler(obj, targettype);
				writeCustomizableWithWriteHandler(variables, obj, targettype, out, selected);
				break;
			}
			case WRAPPER: {
				WrapperRMIObjectWriteHandler wrapperwriter = (WrapperRMIObjectWriteHandler) writehandler;
				writeWrappedObject(variables, obj, targettype, wrapperwriter.getWrapperClass(), out);
				break;
			}
			default: {
				//forward compatible, for unknown kind
				System.err.println("Unrecognized RMI write handler kind: " + kind);
				writeObjectDefault(variables, obj, targettype, out);
				break;
			}
		}
	}

	void writeObjectFromStream(RMIVariables variables, Object obj, Class<?> targettype,
			DataOutputUnsyncByteArrayOutputStream out, Object currentlyWrapWrittenObject) throws IOException {
		if (obj != currentlyWrapWrittenObject) {
			if (writeNonCustomizableWritingObject(variables, obj, out)) {
				return;
			}
		}
		writeObjectDefaultImplWithoutWriteHandler(variables, obj, targettype, out, obj.getClass());
	}

	private void writeObjectDefault(RMIVariables variables, Object obj, Class<?> targettype,
			DataOutputUnsyncByteArrayOutputStream out) throws IOException {
		writeObjectDefaultImplWithoutWriteHandler(variables, obj, targettype, out, obj.getClass());
	}

	private void writeObjectDefaultImplWithoutWriteHandler(RMIVariables variables, Object obj, Class<?> targettype,
			DataOutputUnsyncByteArrayOutputStream out, Class<?> objclass) throws IOException {
		if (targettype != null && targettype.isArray()) {
			writeObjectObjectArrayImpl(variables, out, targettype.getComponentType(), (Object[]) obj,
					RMIObjectWriteHandler.defaultWriter());
			return;
		}
		if (objclass.isArray()) {
			writeObjectObjectArrayImpl(variables, out, objclass.getComponentType(), (Object[]) obj,
					RMIObjectWriteHandler.defaultWriter());
			return;
		}
		if (ReflectUtils.isEnumOrEnumAnonymous(objclass)) {
			writeObjectEnumImpl(out, objclass, ((Enum<?>) obj).name());
			return;
		}
		if (obj instanceof ClassLoader) {
			//classloader needs to have instanceof instead of putting the exact class in the type writers
			writeObjectClassLoader(out, (ClassLoader) obj);
			return;
		}
		@SuppressWarnings("unchecked")
		IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Object> simplewriter = (IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Object>) reflectionTypeWriters
				.get(objclass);
		if (simplewriter != null) {
			simplewriter.accept(out, obj);
			return;
		}
		if ((targettype != null && targettype.isInterface()) || targettype == RemoteProxyObject.class) {
			writeNewRemoteObject(variables, obj, out);
			return;
		}
		if (writeExternalizableObject(variables, obj, out)) {
			return;
		}
		writeNewRemoteObject(variables, obj, out);
	}

	private boolean writeNonCustomizableWritingObject(RMIVariables variables, Object obj,
			DataOutputUnsyncByteArrayOutputStream out) throws IOException {
		if (obj == null) {
			writeNullObject(out);
			return true;
		}
		if (variables != null) {
			Integer remoteid = variables.getRemoteIdentifierForObject(obj);
			if (remoteid != null) {
				writeRemoteObject(remoteid, out);
				return true;
			}
		}
		Class<?> objclass = obj.getClass();
		@SuppressWarnings("unchecked")
		IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Object> simplewriter = (IOBiConsumer<DataOutputUnsyncByteArrayOutputStream, Object>) typeWriters
				.get(objclass);
		if (simplewriter != null) {
			simplewriter.accept(out, obj);
			return true;
		}
		ClassTransferProperties<?> objclassproperties = variables.getProperties().getClassProperties(objclass);
		if (objclassproperties != null) {
			RMIObjectWriteHandler writehandler = objclassproperties.getWriteHandler();
			writeCustomizableWithWriteHandler(variables, obj, objclass, out, writehandler);
			return true;
		}
		return false;
	}

	private void writeObjectEnumImpl(DataOutputUnsyncByteArrayOutputStream out, Class<?> enumclass, String enumname) {
		if (enumclass.isAnonymousClass()) {
			enumclass = enumclass.getSuperclass();
		}
		out.writeShort(OBJECT_ENUM);
		writeClass(enumclass, out);
		writeString(enumname, out);
	}

	private void writeObjectEnum(DataOutputUnsyncByteArrayOutputStream out, Enum<?> en) {
		writeObjectEnumImpl(out, en.getClass(), en.name());
	}

	private void writeObjectObjectArray(RMIVariables variables, DataOutputUnsyncByteArrayOutputStream out,
			Class<?> componenttype, Object obj, RMIObjectWriteHandler componentwriter) throws IOException {
		Object[] array;
		try {
			array = (Object[]) obj;
		} catch (ClassCastException e) {
			throw new RMIObjectTransferFailureException(
					"Failed to cast object to array. (" + obj.getClass() + ":" + obj + ")", e);
		}
		writeObjectObjectArrayImpl(variables, out, componenttype, array, componentwriter);
	}

	private void writeObjectUsingWriteHandler(RMIObjectWriteHandler writehandler, RMIVariables variables, Object obj,
			DataOutputUnsyncByteArrayOutputStream out, Class<?> targettype) throws IOException {
		if (writeNonCustomizableWritingObject(variables, obj, out)) {
			return;
		}
		writeCustomizableWithWriteHandler(variables, obj, targettype, out, writehandler);
	}

	private void writeObjectObjectArrayImpl(RMIVariables variables, DataOutputUnsyncByteArrayOutputStream out,
			Class<?> componenttype, Object[] array, RMIObjectWriteHandler componentwriter) throws IOException {
		out.writeShort(OBJECT_ARRAY);
		writeClass(componenttype, out);
		int len = array.length;
		out.writeInt(len);

		for (int i = 0; i < len; i++) {
			writeObjectUsingWriteHandler(componentwriter, variables, array[i], out, componenttype);
		}
	}

	private void writeObjectConstructor(DataOutputUnsyncByteArrayOutputStream out, Constructor<?> c) {
		out.writeShort(OBJECT_CONSTRUCTOR);
		writeConstructor(c, out);
	}

	private void writeObjectField(DataOutputUnsyncByteArrayOutputStream out, Field f) {
		out.writeShort(OBJECT_FIELD);
		writeField(f, out);
	}

	private void writeObjectMethod(DataOutputUnsyncByteArrayOutputStream out, Method m) {
		out.writeShort(OBJECT_METHOD);
		writeMethod(m, out);
	}

	private void writeObjectClass(DataOutputUnsyncByteArrayOutputStream out, Class<?> c) {
		out.writeShort(OBJECT_CLASS);
		writeClass(c, out);
	}

	private void writeObjectClassLoader(DataOutputUnsyncByteArrayOutputStream out, ClassLoader cl) {
		out.writeShort(OBJECT_CLASSLOADER);
		writeClassLoader(cl, out);
	}

	private static void writeObjectString(DataOutputUnsyncByteArrayOutputStream out, String s) {
		out.writeShort(OBJECT_STRING);
		writeString(s, out);
	}

	private static void writeObjectChar(DataOutputUnsyncByteArrayOutputStream out, char v) {
		out.writeShort(OBJECT_CHAR);
		out.writeChar(v);
	}

	private static void writeObjectDouble(DataOutputUnsyncByteArrayOutputStream out, double v) {
		out.writeShort(OBJECT_DOUBLE);
		out.writeDouble(v);
	}

	private static void writeObjectFloat(DataOutputUnsyncByteArrayOutputStream out, float v) {
		out.writeShort(OBJECT_FLOAT);
		out.writeFloat(v);
	}

	private static void writeObjectLong(DataOutputUnsyncByteArrayOutputStream out, long v) {
		out.writeShort(OBJECT_LONG);
		out.writeLong(v);
	}

	private static void writeObjectInt(DataOutputUnsyncByteArrayOutputStream out, int v) {
		out.writeShort(OBJECT_INT);
		out.writeInt(v);
	}

	private static void writeObjectShort(DataOutputUnsyncByteArrayOutputStream out, short v) {
		out.writeShort(OBJECT_SHORT);
		out.writeShort(v);
	}

	private static void writeObjectByte(DataOutputUnsyncByteArrayOutputStream out, byte v) {
		out.writeShort(OBJECT_BYTE);
		out.writeByte(v);
	}

	private static void writeObjectBoolean(DataOutputUnsyncByteArrayOutputStream out, boolean v) {
		out.writeShort(OBJECT_BOOLEAN);
		out.writeBoolean(v);
	}

	private static void writeObjectByteArray(DataOutputUnsyncByteArrayOutputStream out, byte[] v) {
		out.writeShort(OBJECT_BYTE_ARRAY);
		out.writeInt(v.length);
		out.write(v);
	}

	private static void writeObjectShortArray(DataOutputUnsyncByteArrayOutputStream out, short[] v) {
		out.writeShort(OBJECT_SHORT_ARRAY);
		out.writeInt(v.length);
		out.write(v);
	}

	private static void writeObjectIntArray(DataOutputUnsyncByteArrayOutputStream out, int[] v) {
		out.writeShort(OBJECT_INT_ARRAY);
		out.writeInt(v.length);
		out.write(v);
	}

	private static void writeObjectLongArray(DataOutputUnsyncByteArrayOutputStream out, long[] v) {
		out.writeShort(OBJECT_LONG_ARRAY);
		out.writeInt(v.length);
		out.write(v);
	}

	private static void writeObjectFloatArray(DataOutputUnsyncByteArrayOutputStream out, float[] v) {
		out.writeShort(OBJECT_FLOAT_ARRAY);
		out.writeInt(v.length);
		out.write(v);
	}

	private static void writeObjectDoubleArray(DataOutputUnsyncByteArrayOutputStream out, double[] v) {
		out.writeShort(OBJECT_DOUBLE_ARRAY);
		out.writeInt(v.length);
		out.write(v);
	}

	private static void writeObjectBooleanArray(DataOutputUnsyncByteArrayOutputStream out, boolean[] v) {
		out.writeShort(OBJECT_BOOLEAN_ARRAY);
		out.writeInt(v.length);
		out.write(v);
	}

	private static void writeObjectCharArray(DataOutputUnsyncByteArrayOutputStream out, char[] v) {
		out.writeShort(OBJECT_CHAR_ARRAY);
		out.writeInt(v.length);
		out.write(v);
	}

	private static byte[] readObjectByteArray(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int len = in.readInt();
		byte[] result = new byte[len];
		in.readFully(result);
		return result;
	}

	private static short[] readObjectShortArray(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int len = in.readInt();
		short[] result = new short[len];
		in.readFully(result);
		return result;
	}

	private static int[] readObjectIntArray(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int len = in.readInt();
		int[] result = new int[len];
		in.readFully(result);
		return result;
	}

	private static long[] readObjectLongArray(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int len = in.readInt();
		long[] result = new long[len];
		in.readFully(result);
		return result;
	}

	private static float[] readObjectFloatArray(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int len = in.readInt();
		float[] result = new float[len];
		in.readFully(result);
		return result;
	}

	private static double[] readObjectDoubleArray(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int len = in.readInt();
		double[] result = new double[len];
		in.readFully(result);
		return result;
	}

	private static boolean[] readObjectBooleanArray(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int len = in.readInt();
		boolean[] result = new boolean[len];
		in.readFully(result);
		return result;
	}

	private static char[] readObjectCharArray(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int len = in.readInt();
		char[] result = new char[len];
		in.readFully(result);
		return result;
	}

	void writeSerializedObjectFromStream(RMIVariables variables, Object obj, DataOutputUnsyncByteArrayOutputStream out,
			Object currentlyWrapWrittenObject) throws IOException {
		if (obj != currentlyWrapWrittenObject) {
			if (writeNonCustomizableWritingObject(variables, obj, out)) {
				return;
			}
		}
		writeSerializedObject(obj, out);
	}

	void writeWrappedObjectFromStream(RMIVariables variables, Object obj, Class<? extends RMIWrapper> wrapperclass,
			DataOutputUnsyncByteArrayOutputStream out, Object currentlyWrapWrittenObject) throws IOException {
		if (obj != currentlyWrapWrittenObject) {
			if (writeNonCustomizableWritingObject(variables, obj, out)) {
				return;
			}
		}
		writeWrappedObject(variables, obj, obj.getClass(), wrapperclass, out);
	}

	void writeEnumObjectFromStream(RMIVariables variables, Object obj, DataOutputUnsyncByteArrayOutputStream out,
			Object currentlyWrapWrittenObject) throws IOException, RMIObjectTransferFailureException {
		if (obj != currentlyWrapWrittenObject) {
			if (writeNonCustomizableWritingObject(variables, obj, out)) {
				return;
			}
		}
		Enum<?> en;
		try {
			en = (Enum<?>) obj;
		} catch (ClassCastException e) {
			throw new RMIObjectTransferFailureException(
					"Failed to cast object to Enum. (" + obj.getClass() + ":" + obj + ")", e);
		}
		writeObjectEnum(out, en);
	}

	private void writeSerializedObject(Object obj, DataOutputUnsyncByteArrayOutputStream out) {
		Class<? extends Object> objclass = obj.getClass();
		if (ReflectUtils.isEnumOrEnumAnonymous(objclass)) {
			writeObjectEnumImpl(out, objclass, ((Enum<?>) obj).name());
			return;
		}
		out.writeShort(OBJECT_SERIALIZED);
		try (ObjectOutputStream oos = new RMISerializeObjectOutputStream(out, connection)) {
			oos.writeObject(obj);
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			throw new RMIObjectTransferFailureException(
					"Failed to transfer object as serializable. (" + obj.getClass() + ":" + obj + ")", e);
		}
	}

	private Object readSerializedObject(DataInputUnsyncByteArrayInputStream in) {
		//TODO handle serialization security-wise
		try (ObjectInputStream ois = new RMISerializeObjectInputStream(StreamUtils.closeProtectedInputStream(in),
				connection)) {
			return ois.readObject();
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			throw new RMIObjectTransferFailureException("Failed to read serializable object.", e);
		}
	}

	private RMIObjectInput getObjectInputForVariables(RMIVariables variables, DataInputUnsyncByteArrayInputStream in) {
		return new RMIObjectInputImpl(variables, this, in);
	}

	private static Object unwrapWrapperForTransfer(Object obj, RMIVariables variables) {
		if (variables.getRemoteIdentifierForObject(obj) != null) {
			return obj;
		}
		Object original = obj;
		while (true) {
			//obj is currently not a remote proxy in the argument variables
			if (obj instanceof RMIWrapper) {
				Object thewrapped = ((RMIWrapper) obj).getWrappedObject();
				if (thewrapped == obj) {
					//failed to completely unwrap, we're better off with the original
					return original;
				}
				//continue trying to unwrap more
				obj = thewrapped;
				continue;
			}
			if (variables.getRemoteIdentifierForObject(obj) != null) {
				//we found a remote object that is present on the other side
				return obj;
			}
			//the obj is not a remote proxy for the variables and not an RMIWrapper
			//so we're better off returning the original object than the completely unwrapped
			return original;
		}
	}

	private boolean writeExternalizableObject(RMIVariables variables, Object obj,
			DataOutputUnsyncByteArrayOutputStream out) {
		if (obj instanceof Externalizable) {
			out.writeShort(OBJECT_EXTERNALIZABLE);
			Class<?> clazz = obj.getClass();
			writeClass(clazz, out);
			int sizeoffset = out.size();
			out.writeInt(0);
			try {
				((Externalizable) obj).writeExternal(new RMIObjectOutputImpl(variables, this, out));
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
				throw new RMIObjectTransferFailureException(
						"Failed to transfer object as externalizable. (" + obj.getClass() + ":" + obj + ")", e);
			}
			out.replaceInt(out.size() - sizeoffset - 4, sizeoffset);
			return true;
		}
		return false;
	}

	private Object readExternalizableObject(RMIVariables variables, DataInputUnsyncByteArrayInputStream in)
			throws IOException {
		Class<?> clazz = readClass(in);
		try {
			Externalizable instance;
			try {
				//cast down to externalizable, to avoid malicious client instantiating other kinds of classes
				instance = ReflectUtils.newInstance(clazz.asSubclass(Externalizable.class));
			} catch (NoSuchMethodException e) {
				InvalidClassException te = new InvalidClassException(clazz.getName(), "no valid constructor");
				te.initCause(e);
				throw te;
			} catch (ClassCastException e) {
				InvalidClassException te = new InvalidClassException(clazz.getName(), "not externalizable");
				te.initCause(e);
				throw te;
			}
			int bytecount = in.readInt();
			try {
				ByteArrayRegion inregion = in.toByteArrayRegion();
				DataInputUnsyncByteArrayInputStream limitreader = new DataInputUnsyncByteArrayInputStream(
						inregion.getArray(), inregion.getOffset(), bytecount);
				instance.readExternal(getObjectInputForVariables(variables, limitreader));
				int avail = limitreader.available();
				if (avail > 0) {
					throw new RMIObjectTransferFailureException("Externalizable " + clazz.getName()
							+ " didn't read input fully. (Remaining " + avail + " bytes)");
				}
				in.skipBytes(bytecount);
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
				throw new RMIObjectTransferFailureException("Failed to read externalizable. (" + clazz + ")", e);
			}
			return instance;
		} catch (InvocationTargetException e) {
			throw new RMIObjectTransferFailureException(e.getTargetException());
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | SecurityException e) {
			throw new RMIObjectTransferFailureException(e);
		}
	}

	private Object readObjectArray(RMIVariables vars, DataInputUnsyncByteArrayInputStream in) throws IOException {
		Class<?> component = this.readClass(in);
		int len = in.readInt();
		Object array = Array.newInstance(component, len);
		for (int i = 0; i < len; i++) {
			Object obj = this.readObject(vars, in);
			Array.set(array, i, obj);
		}
		return array;
	}

	private Object readEnum(DataInputUnsyncByteArrayInputStream in) throws IOException {
		@SuppressWarnings("rawtypes")
		Class enumtype = this.readClass(in);
		@SuppressWarnings({ "rawtypes", "unchecked" })
		Enum result = Enum.valueOf(enumtype, readString(in));
		return result;
	}

	Object readObject(RMIVariables vars, DataInputUnsyncByteArrayInputStream in) throws IOException {
		short type = in.readShort();
		return OBJECT_READERS.getOrDefault(type, OBJECT_READER_UNKNOWN).accept(this, vars, in);
	}

	private void writeClass(Class<?> clazz, DataOutputUnsyncByteArrayOutputStream out) {
		RMIWeakCommCache<Class<?>> cache = commClasses;
		Integer index = cache.getWriteIndex(clazz);
		if (index != null) {
			out.writeShort(CLASS_INDEX);
			out.writeInt(index);
			return;
		}
		out.writeShort(CLASS_DETAILS);
		writeClassData(clazz, out);
	}

	private Class<?> readClass(DataInputUnsyncByteArrayInputStream in) throws IOException {
		RMIWeakCommCache<Class<?>> cache = commClasses;
		short cmd = in.readShort();
		switch (cmd) {
			case CLASS_DETAILS: {
				String classname = readString(in);
				ClassLoader cl;
				try {
					cl = readClassLoader(in);
				} catch (ClassLoaderNotFoundIOException e) {
					throw new RMIObjectTransferFailureException("Class not found: " + classname, e);
				}
				try {
					Class<?> result = Class.forName(classname, false, cl);

					Integer putidx = cache.putReadIfAbsent(result);
					if (putidx != null) {
						writeCommandCachedClass(result, putidx);
					}
					return result;
				} catch (ClassNotFoundException e) {
					throw new RMIObjectTransferFailureException(
							"Class not found: " + classname + " in classloader: " + cl, e);
				}
			}
			case CLASS_INDEX: {
				return readClassWithIndex(in, cache);
			}
			default: {
				throw new RMICallFailedException("illegal command: " + cmd);
			}
		}
	}

	private Class<?> readClassWithHierarchyOptional(DataInputUnsyncByteArrayInputStream in, Class<?> relativehierarchy,
			boolean[] outcachetransferable) throws IOException {
		RMIWeakCommCache<Class<?>> cache = commClasses;
		short cmd = in.readShort();
		switch (cmd) {
			case CLASS_DETAILS: {
				String classname = readString(in);
				ClassLoader cl;
				try {
					cl = readClassLoader(in);
				} catch (ClassLoaderNotFoundIOException e) {
					throw new RMIObjectTransferFailureException("Class not found: " + classname, e);
				}
				ClassLoader findercl;
				Class<?> result = ReflectUtils.findTypeWithNameInHierarchy(relativehierarchy, classname);
				if (result == null) {
					try {
						result = Class.forName(classname, false, cl);
						findercl = cl;

					} catch (ClassNotFoundException e) {
						throw new RMIObjectTransferFailureException(
								"Class not found: " + classname + " in classloader: " + cl, e);
					}
				} else {
					findercl = result.getClassLoader();
				}
				//only update the cache if the class was found through the denoted classloader
				if (findercl == cl) {
					outcachetransferable[0] = true;
					Integer putidx = cache.putReadIfAbsent(result);
					if (putidx != null) {
						writeCommandCachedClass(result, putidx);
					}
				}
				return result;
			}
			case CLASS_INDEX: {
				return readClassWithIndex(in, cache);
			}
			default: {
				throw new RMICallFailedException("illegal command: " + cmd);
			}
		}
	}

	private static Class<?> readClassWithIndex(DataInputUnsyncByteArrayInputStream in, RMIWeakCommCache<Class<?>> cache)
			throws EOFException {
		int cindex = in.readInt();
		Class<?> result = cache.getRead(cindex);
		if (result == null) {
			throw new RMIObjectTransferFailureException("Class not found for index: " + cindex);
		}
		return result;
	}

	private void writeClassData(Class<?> clazz, DataOutputUnsyncByteArrayOutputStream out) {
		writeString(clazz.getName(), out);
		writeClassLoader(clazz.getClassLoader(), out);
	}

	private Class<?> readClassData(DataInputUnsyncByteArrayInputStream in) throws IOException {
		String classname = readString(in);
		ClassLoader cl;
		try {
			cl = readClassLoader(in);
		} catch (ClassLoaderNotFoundIOException e) {
			throw new RMIObjectTransferFailureException("Class not found: " + classname, e);
		}
		try {
			return Class.forName(classname, false, cl);
		} catch (ClassNotFoundException e) {
			throw new RMIObjectTransferFailureException("Class not found: " + classname + " in classloader: " + cl, e);
		}
	}

	private void writeClassLoader(ClassLoader cl, DataOutputUnsyncByteArrayOutputStream out) {
		if (cl == null) {
			out.writeShort(CLASSLOADER_NULL);
			return;
		}
		Integer index = commClassLoaders.getWriteIndex(cl);
		if (index != null) {
			out.writeShort(CLASSLOADER_INDEX);
			out.writeInt(index);
			return;
		}
		out.writeShort(CLASSLOADER_DETAILS);
		writeClassLoaderData(cl, out);
	}

	private ClassLoader readClassLoader(DataInputUnsyncByteArrayInputStream in) throws IOException {
		short cmd = in.readShort();
		switch (cmd) {
			case CLASSLOADER_DETAILS: {
				ClassLoader result = readClassLoaderData(in);
				if (result != null) {
					Integer putidx = commClassLoaders.putReadIfAbsent(result);
					if (putidx != null) {
						writeCommandCachedClassLoader(result, putidx);
					}
				}
				return result;
			}
			case CLASSLOADER_INDEX: {
				int cindex = in.readInt();
				ClassLoader readcl = commClassLoaders.getRead(cindex);
				if (readcl == null) {
					throw new RMIObjectTransferFailureException("Class not found for index: " + cindex);
				}
				return readcl;
			}
			case CLASSLOADER_NULL: {
				return nullClassLoader;
			}
			default: {
				throw new RMICallFailedException("illegal command: " + cmd);
			}
		}
	}

	private void writeClassLoaderData(ClassLoader cl, DataOutputUnsyncByteArrayOutputStream out) {
		String clid = connection.getClassLoaderId(cl);
		writeClassLoaderId(out, clid);
	}

	private static void writeClassLoaderId(DataOutputUnsyncByteArrayOutputStream out, String clid) {
		writeString(clid == null ? "" : clid, out);
	}

	private static String readClassLoaderId(DataInputUnsyncByteArrayInputStream in) throws IOException {
		String result = readString(in);
		if ("".equals(result)) {
			return null;
		}
		return result;
	}

	private static void writeClassLoaderId(DataOutput out, String clid) throws IOException {
		out.writeUTF(clid == null ? "" : clid);
	}

	private static String readClassLoaderId(DataInput in) throws IOException {
		String result = in.readUTF();
		if ("".equals(result)) {
			return null;
		}
		return result;
	}

	private ClassLoader readClassLoaderData(DataInputUnsyncByteArrayInputStream in) throws IOException {
		String clid = readClassLoaderId(in);
		return getClassLoaderById(connection, clid);
	}

	private static ClassLoader getClassLoaderById(RMIConnection connection, String clid) throws IOException {
		return connection.getClassLoaderByIdOrThrow(clid);
	}

	static class ClassLoaderNotFoundIOException extends IOException {
		private static final long serialVersionUID = 1L;

		public ClassLoaderNotFoundIOException(String classloaderid) {
			super("ClassLoader not found for id: " + classloaderid);
		}
	}

	private static class ClassSetPartiallyReadException extends Exception {
		private static final long serialVersionUID = 1L;

		private Set<Class<?>> result;

		public ClassSetPartiallyReadException(Set<Class<?>> result) {
			super(null, null, true, false);
			this.result = result;
		}

		public Set<Class<?>> getReadClasses() {
			return result;
		}
	}

	private Set<Class<?>> readClassesSet(DataInputUnsyncByteArrayInputStream in)
			throws IOException, ClassSetPartiallyReadException {
		Set<Class<?>> result = new LinkedHashSet<>();
		short count = in.readShort();
		if (count == 0) {
			return Collections.emptySet();
		}
		ClassSetPartiallyReadException partialexc = null;
		while (count-- > 0) {
			try {
				result.add(readClass(in));
			} catch (RMIObjectTransferFailureException e) {
				if (partialexc == null) {
					partialexc = new ClassSetPartiallyReadException(result);
				} else {
					partialexc.addSuppressed(e);
				}
			}
		}
		reducePublicNonAssignableInterfaces(result);
		IOUtils.throwExc(partialexc);
		return result;
	}

	private void writeClasses(Collection<Class<?>> clazz, DataOutputUnsyncByteArrayOutputStream out) {
		out.writeShort(clazz.size());
		for (Class<?> c : clazz) {
			writeClass(c, out);
		}
	}

	private void writeConstructor(Constructor<?> constructor, DataOutputUnsyncByteArrayOutputStream out) {
		RMIGeneratingCommCache<Constructor<?>> cache = commConstructors;
		Integer index = cache.getWriteIndex(constructor);
		if (index != null) {
			out.writeShort(CONSTRUCTOR_INDEX);
			out.writeInt(index);
			return;
		}
		out.writeShort(CONSTRUCTOR_DETAILS);
		writeConstructorData(constructor, out);
	}

	private Constructor<?> readConstructor(DataInputUnsyncByteArrayInputStream in) throws IOException {
		RMIGeneratingCommCache<Constructor<?>> cache = commConstructors;
		short cmd = in.readShort();
		Constructor<?> result;
		switch (cmd) {
			case CONSTRUCTOR_DETAILS: {
				result = readConstructorData(in);
				Integer putidx = cache.putReadIfAbsent(result, new ConstructorWeakSupplier(result));
				if (putidx != null) {
					writeCommandCachedConstructor(result, putidx);
				}
				break;
			}
			case CONSTRUCTOR_INDEX: {
				int cindex = in.readInt();
				Constructor<?> readconstructor = cache.getRead(cindex);
				if (readconstructor == null) {
					throw new RMIObjectTransferFailureException("Constructor not found with index: " + cindex);
				}
				result = readconstructor;
				break;
			}
			default: {
				throw new RMICallFailedException("illegal command: " + cmd);
			}
		}
		return result;
	}

	private void writeConstructorData(Constructor<?> constructor, DataOutputUnsyncByteArrayOutputStream out) {
		writeClass(constructor.getDeclaringClass(), out);
		writeClassNames(constructor.getParameterTypes(), out);
	}

	private Constructor<?> readConstructorData(DataInputUnsyncByteArrayInputStream in) throws IOException {
		try {
			Class<?> declaringclass = readClass(in);
			Class<?>[] parameterTypes = readClassNames(in, declaringclass.getClassLoader());

			return declaringclass.getDeclaredConstructor(parameterTypes);
		} catch (NoSuchMethodException e) {
			throw new RMIObjectTransferFailureException(e);
		}
	}

	private void writeField(Field f, DataOutputUnsyncByteArrayOutputStream out) {
		RMIGeneratingCommCache<Field> cache = commFields;
		Integer index = cache.getWriteIndex(f);
		if (index != null) {
			out.writeShort(FIELD_INDEX);
			out.writeInt(index);
			return;
		}
		out.writeShort(FIELD_DETAILS);
		writeFieldData(f, out);
	}

	private Field readField(DataInputUnsyncByteArrayInputStream in, Object relativeobject) throws IOException {
		RMIGeneratingCommCache<Field> cache = commFields;
		short cmd = in.readShort();
		switch (cmd) {
			case FIELD_DETAILS: {
				Field result;
				if (relativeobject != null) {
					try {
						boolean[] outcachetransferable = { false };
						Class<?> declaringclass = readClassWithHierarchyOptional(in, relativeobject.getClass(),
								outcachetransferable);
						String name = readString(in);

						result = declaringclass.getDeclaredField(name);
						if (!outcachetransferable[0]) {
							return result;
						}
					} catch (NoSuchFieldException e) {
						throw new RMIObjectTransferFailureException(e);
					}
				} else {
					result = readFieldData(in);
				}
				Integer putidx = cache.putReadIfAbsent(result, new FieldWeakSupplier(result));
				if (putidx != null) {
					writeCommandCachedField(result, putidx);
				}
				return result;
			}
			case FIELD_INDEX: {
				int cindex = in.readInt();
				Field result = cache.getRead(cindex);
				if (result == null) {
					throw new RMIObjectTransferFailureException("Field not found with index: " + cindex);
				}
				return result;
			}
			default: {
				throw new RMICallFailedException("illegal command: " + cmd);
			}
		}
	}

	private void writeFieldData(Field f, DataOutputUnsyncByteArrayOutputStream out) {
		writeClass(f.getDeclaringClass(), out);
		writeString(f.getName(), out);
	}

	private Field readFieldData(DataInputUnsyncByteArrayInputStream in) throws IOException {
		try {
			Class<?> declaringclass = readClass(in);
			String name = readString(in);
			return declaringclass.getDeclaredField(name);
		} catch (NoSuchFieldException e) {
			throw new RMIObjectTransferFailureException(e);
		}
	}

	private void writeMethod(Method method, DataOutputUnsyncByteArrayOutputStream out) {
		RMIGeneratingCommCache<Method> cache = commMethods;
		Integer index = cache.getWriteIndex(method);
		if (index != null) {
			out.writeShort(METHOD_INDEX);
			out.writeInt(index);
			return;
		}
		out.writeShort(METHOD_DETAILS);
		writeMethodData(method, out);
	}

	private Method readMethod(DataInputUnsyncByteArrayInputStream in, Object relativeobject) throws IOException {
		RMIGeneratingCommCache<Method> cache = commMethods;
		short cmd = in.readShort();
		switch (cmd) {
			case METHOD_DETAILS: {
				final Method result;
				if (relativeobject != null) {
					try {
						boolean[] outcachetransferable = { false };
						Class<?> declaringclass = readClassWithHierarchyOptional(in, relativeobject.getClass(),
								outcachetransferable);
						String name = readString(in);
						Class<?>[] parameterTypes = readClassNames(in, declaringclass.getClassLoader());

						result = declaringclass.getMethod(name, parameterTypes);
						if (!outcachetransferable[0]) {
							return result;
						}
					} catch (NoSuchMethodException e) {
						throw new RMIObjectTransferFailureException(e);
					}
				} else {
					result = readMethodData(in);
				}
				Integer putidx = cache.putReadIfAbsent(result, new MethodWeakSupplier(result));
				if (putidx != null) {
					writeCommandCachedMethod(result, putidx);
				}
				return result;
			}
			case METHOD_INDEX: {
				int cindex = in.readInt();
				Method result = cache.getRead(cindex);
				if (result == null) {
					throw new RMIObjectTransferFailureException("Method not found with index: " + cindex);
				}
				return result;
			}
			default: {
				throw new RMICallFailedException("illegal command: " + cmd);
			}
		}
	}

	private void writeMethodData(Method method, DataOutputUnsyncByteArrayOutputStream out) {
		writeClass(method.getDeclaringClass(), out);
		writeString(method.getName(), out);
		writeClassNames(method.getParameterTypes(), out);
	}

	private static void writeClassNames(Class<?>[] clazz, DataOutputUnsyncByteArrayOutputStream out) {
		out.writeInt(clazz.length);
		for (int i = 0; i < clazz.length; i++) {
			String cname = clazz[i].getName();
			writeString(cname, out);
		}
	}

	private static Class<?>[] readClassNames(DataInputUnsyncByteArrayInputStream in, ClassLoader cl)
			throws IOException {
		int len = in.readInt();
		Class<?>[] result = new Class<?>[len];
		for (int i = 0; i < len; i++) {
			String cname = readString(in);
			try {
				Class<?> foundc = ReflectUtils.primitiveNameToPrimitiveClass(cname);
				if (foundc == null) {
					foundc = Class.forName(cname, false, cl);
				}
				result[i] = foundc;
			} catch (ClassNotFoundException e) {
				throw new RMIObjectTransferFailureException(
						"Class not found for name: " + cname + " in clasloader: " + cl, e);
			}
		}
		return result;
	}

	private Method readMethodData(DataInputUnsyncByteArrayInputStream in) throws IOException {
		try {
			Class<?> declaringclass = readClass(in);
			String name = readString(in);
			Class<?>[] parameterTypes = readClassNames(in, declaringclass.getClassLoader());

			return declaringclass.getMethod(name, parameterTypes);
		} catch (NoSuchMethodException e) {
			throw new RMIObjectTransferFailureException(e);
		}
	}

	private void writeMethodParameters(RMIVariables variables, ExecutableTransferProperties<?> execproperties,
			Object[] arguments, DataOutputUnsyncByteArrayOutputStream out) throws IOException {
		if (ObjectUtils.isNullOrEmpty(arguments)) {
			out.writeShort(0);
			return;
		}
		out.writeShort(arguments.length);
		Executable exec = execproperties.getExecutable();
		Class<?>[] paramtypes = exec.getParameterTypes();
		for (int i = 0; i < arguments.length; i++) {
			Object argument = unwrapWrapperForTransfer(arguments[i], variables);
			writeObjectUsingWriteHandler(execproperties.getParameterWriter(i), variables, argument, out, paramtypes[i]);
		}
	}

	private Object readWrappedObject(RMIVariables variables, DataInputUnsyncByteArrayInputStream in)
			throws IOException {
		Class<?> clazz = readClass(in);
		RMIWrapper wrapper;
		try {
			//cast down the class to ensure that the wrapper is only instantiated if it actually implements the RMIWrapper
			//this is to avoid malicious client to provide a non-rmi wrapper wrapper class during transmit
			wrapper = ReflectUtils.newInstance(clazz.asSubclass(RMIWrapper.class));
		} catch (InvocationTargetException e) {
			throw new RMIObjectTransferFailureException("Failed to instantiate RMIWrapper: " + clazz, e.getCause());
		} catch (Exception e) {
			throw new RMIObjectTransferFailureException(
					new InvalidClassException(clazz.getName(), "Failed to instantiate RMIWrapper: " + clazz));
		}
		try {
			wrapper.readWrapped(getObjectInputForVariables(variables, in));
		} catch (Exception e) {
			throw new RMIObjectTransferFailureException("Failed to read RMI wrapped object. (" + clazz + ")", e);
		}
		return wrapper.resolveWrapped();
	}

	private void writeWrappedObject(RMIVariables variables, Object obj, Class<?> paramtype,
			Class<? extends RMIWrapper> wrapperclass, DataOutputUnsyncByteArrayOutputStream out) throws IOException {
		Constructor<? extends RMIWrapper> constructor = getRMIWrapperConstructor(paramtype, wrapperclass);
		RMIWrapper wrapper;
		try {
			wrapper = ReflectUtils.invokeConstructor(constructor, obj);
		} catch (InvocationTargetException e) {
			throw new RMIObjectTransferFailureException("Failed to instantiate RMIWrapper: " + wrapperclass,
					e.getCause());
		} catch (Exception e) {
			throw new RMIObjectTransferFailureException("Failed to instantiate RMIWrapper: " + wrapperclass, e);
		}
		out.writeShort(OBJECT_WRAPPER);
		writeClass(wrapperclass, out);
		wrapper.writeWrapped(new RMIObjectOutputImpl(variables, this, out, obj));
	}

	@SuppressWarnings("unchecked")
	private static Constructor<? extends RMIWrapper> getRMIWrapperConstructor(Class<?> paramtype,
			Class<? extends RMIWrapper> wrapperclass) {
		try {
			//get directly if possible
			return wrapperclass.getDeclaredConstructor(paramtype);
		} catch (NoSuchMethodException e) {
		}
		for (Constructor<?> c : wrapperclass.getDeclaredConstructors()) {
			Class<?>[] params = c.getParameterTypes();
			if (params.length != 1) {
				continue;
			}
			if (params[0].isAssignableFrom(paramtype)) {
				return (Constructor<? extends RMIWrapper>) c;
			}
		}
		throw new RMIObjectTransferFailureException("No appropriate RMIWrapper constructor found for parameter type: "
				+ paramtype + " in " + wrapperclass.getName());
	}

	private static void writeRemoteObject(int remoteid, DataOutputUnsyncByteArrayOutputStream out) {
		out.writeShort(OBJECT_REMOTE);
		out.writeInt(remoteid);
	}

	void writeRemoteObjectFromStream(RMIVariables variables, Object obj, DataOutputUnsyncByteArrayOutputStream out,
			Object currentlyWrapWrittenObject) throws IOException {
		if (obj != currentlyWrapWrittenObject) {
			if (writeNonCustomizableWritingObject(variables, obj, out)) {
				return;
			}
		}
		writeRemoteObject(variables, obj, out);
	}

	private void writeRemoteObject(RMIVariables variables, Object obj, DataOutputUnsyncByteArrayOutputStream out) {
		if (variables == null) {
			throw new RMIObjectTransferFailureException("No variables available for remote object transfer.");
		}
		Integer remoteid = variables.getRemoteIdentifierForObject(obj);
		if (remoteid != null) {
			writeRemoteObject(remoteid, out);
		} else {
			writeNewRemoteObject(variables, obj, out);
		}
	}

	private static void writeOnlyIfRemote(RMIVariables variables, Object obj,
			DataOutputUnsyncByteArrayOutputStream out) {
		if (variables == null) {
			throw new RMIObjectTransferFailureException("No variables available for remote object transfer.");
		}
		Integer remoteid = variables.getRemoteIdentifierForObject(obj);
		if (remoteid == null) {
			throw new RMIObjectTransferFailureException("Not remote object for the given variables.");
		}
		writeRemoteObject(remoteid, out);
	}

	private static Object readRemoteObject(RMIVariables variables, DataInputUnsyncByteArrayInputStream in)
			throws IOException {
		if (variables == null) {
			throw new RMICallFailedException("Failed to read remote object with null variables.");
		}
		int idx = in.readInt();
		Object remobj = variables.getObjectWithLocalId(idx);
		if (remobj == null) {
			throw new RMICallFailedException("Remote object not found with id: " + idx);
		}
		return remobj;
	}

	private Object[] readMethodParameters(RMIVariables variables, DataInputUnsyncByteArrayInputStream in)
			throws IOException {
		short len = in.readShort();
		if (len == 0) {
			return ObjectUtils.EMPTY_OBJECT_ARRAY;
		}
		Object[] result = new Object[len];
		for (int i = 0; i < len; i++) {
			result[i] = readObject(variables, in);
		}
		return result;
	}

	private RMIVariables readVariablesValidate(DataInputUnsyncByteArrayInputStream in)
			throws IOException, RMICallFailedException {
		RMIVariables variables = readVariablesImpl(in);
		if (variables == null) {
			throw new RMICallFailedException("Variables not found.");
		}
		return variables;
	}

	private RMIVariables readVariablesImpl(DataInputUnsyncByteArrayInputStream in) throws EOFException {
		int variablesid = in.readInt();
		RMIVariables variables = connection.getVariablesByLocalId(variablesid);
		return variables;
	}

	private static void writeVariables(RMIVariables variables, DataOutputUnsyncByteArrayOutputStream out) {
		int remoteid = variables.getRemoteIdentifier();
		out.writeInt(remoteid);
	}

	private final class MethodCallRedispatchResponse implements RedispatchResponse {
		private final int requestId;
		private final Object invokeObject;
		private final MethodTransferProperties method;
		private final RMIVariables variables;
		private final Object[] args;

		private MethodCallRedispatchResponse(int reqid, Object invokeobject, MethodTransferProperties method,
				RMIVariables variables, Object[] args) {
			this.requestId = reqid;
			this.invokeObject = invokeobject;
			this.method = method;
			this.variables = variables;
			this.args = args;
		}

		@Override
		public void executeRedispatchAction() throws IOException {
			invokeAndWriteMethodCall(requestId, variables, invokeObject, method, args);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (method != null ? "method=" + method : "") + "]";
		}
	}

	private final class NewInstanceRedispatchResponse implements RedispatchResponse {
		private final RMIVariables variables;
		private final int requestId;
		private final Constructor<?> constructor;
		private final Object[] args;

		private NewInstanceRedispatchResponse(RMIVariables variables, int reqid, Constructor<?> constructor,
				Object[] args) {
			this.variables = variables;
			this.requestId = reqid;
			this.constructor = constructor;
			this.args = args;
		}

		@Override
		public void executeRedispatchAction() throws IOException {
			instantiateAndWriteNewInstanceCall(requestId, variables, constructor, args);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (constructor != null ? "constructor=" + constructor : "") + "]";
		}
	}

	private final class UnknownClassNewInstanceRedispatchResponse implements RedispatchResponse {
		private final String className;
		private final Object[] args;
		private final int requestId;
		private final RMIVariables variables;
		private final ClassLoader cl;
		private final String[] argClassNames;

		private UnknownClassNewInstanceRedispatchResponse(String classname, Object[] args, int reqid,
				RMIVariables variables, ClassLoader cl, String[] argclassnames) {
			this.className = classname;
			this.args = args;
			this.requestId = reqid;
			this.variables = variables;
			this.cl = cl;
			this.argClassNames = argclassnames;
		}

		@Override
		public void executeRedispatchAction() throws IOException {
			instantiateAndWriteUnknownNewInstanceCall(requestId, variables, cl, className, argClassNames, args);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (className != null ? "classname=" + className : "") + "]";
		}
	}

	private void handleCommandPong(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		requestHandler.addResponse(reqid, new PingResponse());
	}

	private void handleCommandNewVariablesResult(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		try {
			int remoteid = in.readInt();

			if (remoteid == RMIVariables.NO_OBJECT_ID) {
				requestHandler.addResponse(reqid,
						new NewVariablesFailedResponse(new RMICallFailedException("Failed to create variables.")));
			} else {
				requestHandler.addResponse(reqid, new NewVariablesResponse(remoteid));
			}
		} catch (IOException e) {
			requestHandler.addResponse(reqid,
					new NewVariablesFailedResponse(new RMIIOFailureException("Failed to read response.", e)));
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			requestHandler.addResponse(reqid,
					new NewVariablesFailedResponse(new RMICallFailedException("Failed to read response.", e)));
		}
	}

	private void handleCommandNewInstanceResult(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		boolean interrupted = false;
		int interruptreqcount = 0;
		try {
			int remoteindex = in.readInt();
			int compressedinterruptstatus = in.readInt();

			interrupted = isCompressedInterruptStatusInvokerThreadInterrupted(compressedinterruptstatus);
			interruptreqcount = getCompressedInterruptStatusDeliveredRequestCount(compressedinterruptstatus);

			requestHandler.addResponse(reqid, new NewInstanceResponse(interrupted, interruptreqcount, remoteindex));
		} catch (IOException e) {
			requestHandler.addResponse(reqid, new MethodCallFailedResponse(interrupted, interruptreqcount,
					new RMIIOFailureException("Failed to read new instance result.", e)));
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			requestHandler.addResponse(reqid, new MethodCallFailedResponse(interrupted, interruptreqcount,
					new RMICallFailedException("Failed to read new instance result.", e)));
		}
	}

	private void handleCommandGetContextVariableResult(DataInputUnsyncByteArrayInputStream in) throws IOException {
		RMIVariables vars = readVariablesValidate(in);
		int reqid = in.readInt();
		try {
			Object obj = readObject(vars, in);

			requestHandler.addResponse(reqid, new GetContextVariableResponse(obj));
		} catch (IOException e) {
			requestHandler.addResponse(reqid,
					new GetContextVariableFailedResponse(new RMIIOFailureException("Failed to read response.", e)));
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			requestHandler.addResponse(reqid,
					new GetContextVariableFailedResponse(new RMICallFailedException("Failed to read response.", e)));
		}
	}

	private void handleCommandDirectRequestForbidden(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		short cmd = in.readShort();
		switch (cmd) {
			case COMMAND_METHODRESULT_FAIL: {
				requestHandler.addResponse(reqid, DirectForbiddenMethodCallResponse.INSTANCE);
				break;
			}
			case COMMAND_NEWINSTANCERESULT_FAIL: {
				requestHandler.addResponse(reqid, DirectForbiddenNewInstanceResponse.INSTANCE);
				break;
			}
			case COMMAND_UNKNOWN_NEWINSTANCE_RESULT: {
				requestHandler.addResponse(reqid, DirectForbiddenUnknownNewInstanceResponse.INSTANCE);
				break;
			}
			default: {
				requestHandler.addResponse(reqid, new CommandFailedResponse(new RMICallForbiddenException(
						EXCEPTION_MESSAGE_DIRECT_REQUESTS_FORBIDDEN + " (Unknown command: " + cmd + ")")));
				break;
			}
		}
	}

	private void handleCommandGetContextVariable(DataInputUnsyncByteArrayInputStream in) throws IOException {
		RMIVariables vars = readVariablesValidate(in);
		int reqid = in.readInt();
		String varid = in.readUTF();

		writeGetContextVarResponse(reqid, vars, connection.getLocalContextVariable(varid));
	}

	private void handleCommandPing(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();

		writeCommandPong(reqid);
	}

	private void handleCommandCachedClass(DataInputUnsyncByteArrayInputStream in) throws IOException {
		Class<?> classdata = readClassData(in);
		int idx = in.readInt();
		commClasses.putWrite(classdata, idx);
	}

	private void handleCommandCachedClassLoader(DataInputUnsyncByteArrayInputStream in) throws IOException {
		ClassLoader cl = readClassLoaderData(in);
		int idx = in.readInt();
		commClassLoaders.putWrite(cl, idx);
	}

	private void handleCommandCachedMethod(DataInputUnsyncByteArrayInputStream in) throws IOException {
		Method method = readMethodData(in);
		int idx = in.readInt();
		commMethods.putWrite(method, new MethodWeakSupplier(method), idx);
	}

	private void handleCommandInterruptRequest(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		connection.interruptRequestThread(reqid);
	}

	private void handleCommandCachedField(DataInputUnsyncByteArrayInputStream in) throws IOException {
		Field field = readFieldData(in);
		int idx = in.readInt();
		commFields.putWrite(field, new FieldWeakSupplier(field), idx);
	}

	private void handleCommandCachedConstructor(DataInputUnsyncByteArrayInputStream in) throws IOException {
		Constructor<?> constructor = readConstructorData(in);
		int idx = in.readInt();
		commConstructors.putWrite(constructor, new ConstructorWeakSupplier(constructor), idx);
	}

	private void handleCommandNewVariables(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		try {
			int remoteid = in.readInt();
			String name = in.readUTF();
			if (name.isEmpty()) {
				name = null;
			}

			RMIVariables vars = connection.newRemoteVariables(name, remoteid);
			int localid = vars.getLocalIdentifier();

			writeCommandNewVariablesResult(reqid, localid);
		} catch (IOException e) {
			writeCommandNewVariablesResult(reqid, RMIVariables.NO_OBJECT_ID);
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			writeCommandNewVariablesResult(reqid, RMIVariables.NO_OBJECT_ID);
		}
	}

	private void handleCommandNewInstanceUnknownClassResult(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		boolean interrupted = false;
		int interruptreqcount = 0;
		try {
			RMIVariables variables = readVariablesValidate(in);
			int compressedinterruptstatus = in.readInt();
			int remoteindex = in.readInt();
			Set<Class<?>> interfaces;
			try {
				interfaces = readClassesSet(in);
			} catch (ClassSetPartiallyReadException e) {
				interfaces = e.getReadClasses();
			}

			interrupted = isCompressedInterruptStatusInvokerThreadInterrupted(compressedinterruptstatus);
			interruptreqcount = getCompressedInterruptStatusDeliveredRequestCount(compressedinterruptstatus);

			requestHandler.addResponse(reqid,
					new UnknownNewInstanceResponse(interrupted, interruptreqcount, remoteindex, interfaces));
		} catch (IOException e) {
			requestHandler.addResponse(reqid, new UnknownNewInstanceFailedResponse(interrupted, interruptreqcount,
					new RMIIOFailureException("Failed to read new instance result.", e)));
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			requestHandler.addResponse(reqid, new UnknownNewInstanceFailedResponse(interrupted, interruptreqcount,
					new RMICallFailedException("Failed to read new instance result.", e)));
		}
	}

	private void handleCommandNewInstanceResultFailure(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		boolean interrupted = false;
		int interruptreqcount = 0;
		try {
			int compressedinterruptstatus = in.readInt();
			Throwable exc = readException(in);

			interrupted = isCompressedInterruptStatusInvokerThreadInterrupted(compressedinterruptstatus);
			interruptreqcount = getCompressedInterruptStatusDeliveredRequestCount(compressedinterruptstatus);

			requestHandler.addResponse(reqid, new NewInstanceFailedResponse(interrupted, interruptreqcount, exc));
		} catch (RMIRuntimeException e) {
			requestHandler.addResponse(reqid, new UnknownNewInstanceFailedResponse(interrupted, interruptreqcount, e));
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			requestHandler.addResponse(reqid, new UnknownNewInstanceFailedResponse(interrupted, interruptreqcount,
					new RMICallFailedException("Failed to read remote exception.", e)));
		}
	}

	private void handleCommandMethodResultFailure(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		boolean interrupted = false;
		int interruptreqcount = 0;
		try {
			int compressedinterruptstatus = in.readInt();
			Throwable exc = readException(in);

			interrupted = isCompressedInterruptStatusInvokerThreadInterrupted(compressedinterruptstatus);
			interruptreqcount = getCompressedInterruptStatusDeliveredRequestCount(compressedinterruptstatus);

			requestHandler.addResponse(reqid, new MethodCallFailedResponse(interrupted, interruptreqcount, exc));
		} catch (RMIRuntimeException e) {
			requestHandler.addResponse(reqid, new MethodCallFailedResponse(interrupted, interruptreqcount, e));
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			requestHandler.addResponse(reqid, new MethodCallFailedResponse(interrupted, interruptreqcount,
					new RMICallFailedException("Failed to read remote exception.", e)));
		}
	}

	private void writeDirectRequestForbidden(short command, int reqid) throws IOException {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_DIRECT_REQUEST_FORBIDDEN);
			out.writeInt(reqid);
			out.writeShort(command);
		}
	}

	private void handleCommandNewInstance(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		if (!connection.isAllowDirectRequests()) {
			writeDirectRequestForbidden(COMMAND_NEWINSTANCERESULT_FAIL, reqid);
			return;
		}
		RMIVariables variables;
		Constructor<?> constructor;
		Object[] args;
		try {
			variables = readVariablesValidate(in);
			constructor = readConstructor(in);
			args = readMethodParameters(variables, in);
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			writeCommandExceptionResult(COMMAND_NEWINSTANCERESULT_FAIL, reqid, e, false, 0);
			return;
		}
		instantiateAndWriteNewInstanceCall(reqid, variables, constructor, args);
	}

	private void handleCommandNewInstanceRedispatch(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		if (!connection.isAllowDirectRequests()) {
			writeDirectRequestForbidden(COMMAND_NEWINSTANCERESULT_FAIL, reqid);
			return;
		}
		try {
			int dispatchid = in.readInt();
			RMIVariables variables = readVariablesValidate(in);
			Constructor<?> constructor = readConstructor(in);
			Object[] args = readMethodParameters(variables, in);

			requestHandler.addResponse(dispatchid,
					new NewInstanceRedispatchResponse(variables, reqid, constructor, args));
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			writeCommandExceptionResult(COMMAND_NEWINSTANCERESULT_FAIL, reqid, e, false, 0);
		}
	}

	private void handleCommandNewInstanceUnknownClass(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		if (!connection.isAllowDirectRequests()) {
			writeDirectRequestForbidden(COMMAND_UNKNOWN_NEWINSTANCE_RESULT, reqid);
			return;
		}
		RMIVariables variables;
		ClassLoader cl;
		String classname;
		String[] argclassnames;
		Object[] args;
		try {
			variables = readVariablesValidate(in);
			int localclassloaderid = in.readInt();
			cl = getClassLoaderWithId(variables, localclassloaderid);

			classname = in.readUTF();
			int arglen = in.readInt();

			argclassnames = new String[arglen];
			args = new Object[arglen];
			for (int i = 0; i < arglen; i++) {
				argclassnames[i] = in.readUTF();
				args[i] = readObject(variables, in);
			}
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			writeCommandExceptionResult(COMMAND_UNKNOWN_NEWINSTANCE_RESULT, reqid, e, false, 0);
			return;
		}
		instantiateAndWriteUnknownNewInstanceCall(reqid, variables, cl, classname, argclassnames, args);
	}

	private void handleCommandNewInstanceUnknownClassRedispatch(DataInputUnsyncByteArrayInputStream in)
			throws IOException {
		int reqid = in.readInt();
		if (!connection.isAllowDirectRequests()) {
			writeDirectRequestForbidden(COMMAND_UNKNOWN_NEWINSTANCE_RESULT, reqid);
			return;
		}
		try {
			int dispatchid = in.readInt();
			RMIVariables variables = readVariablesValidate(in);
			int localclassloaderid = in.readInt();
			ClassLoader cl = getClassLoaderWithId(variables, localclassloaderid);

			String classname = in.readUTF();
			int arglen = in.readInt();

			String[] argclassnames = new String[arglen];
			Object[] args = new Object[arglen];
			for (int i = 0; i < arglen; i++) {
				argclassnames[i] = in.readUTF();
				args[i] = readObject(variables, in);
			}

			requestHandler.addResponse(dispatchid, new UnknownClassNewInstanceRedispatchResponse(classname, args, reqid,
					variables, cl, argclassnames));
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			writeCommandExceptionResult(COMMAND_UNKNOWN_NEWINSTANCE_RESULT, reqid, e, false, 0);
		}
	}

	private void handleCommandMethodCallAsync(DataInputUnsyncByteArrayInputStream in) {
		RMIVariables variables;
		Object invokeobject;
		Object[] args;
		try {
			variables = readVariablesValidate(in);
			int localid = in.readInt();
			invokeobject = readMethodInvokeObject(variables, localid);
			if (invokeobject == null && !connection.isAllowDirectRequests()) {
				//forbidden to call static methods
				//XXX should notify the user somehow
				return;
			}
			Method method = readMethod(in, invokeobject);
			//TODO check disallowing direct requests if the called method has a non-interface declaring class (or java.lang.Object)
			if (((method.getModifiers() & Modifier.STATIC) == Modifier.STATIC) && !connection.isAllowDirectRequests()) {
				//XXX should notify the user somehow
				return;
			}

			args = readMethodParameters(variables, in);
			variables.addOngoingRequest();
			try {
				ReflectUtils.invokeMethod(invokeobject, method, args);
			} finally {
				variables.removeOngoingRequest();
			}
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			//XXX should notify the user somehow
			return;
		}
	}

	private void handleCommandMethodCall(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		RMIVariables variables;
		Object invokeobject;
		MethodTransferProperties transfermethod;
		Object[] args;
		try {
			variables = readVariablesValidate(in);
			int localid = in.readInt();
			invokeobject = readMethodInvokeObject(variables, localid);
			if (invokeobject == null && !connection.isAllowDirectRequests()) {
				//forbidden to call static methods
				writeDirectRequestForbidden(COMMAND_METHODRESULT_FAIL, reqid);
				return;
			}
			Method method = readMethod(in, invokeobject);
			//TODO check disallowing direct requests if the called method has a non-interface declaring class (or java.lang.Object)
			if (((method.getModifiers() & Modifier.STATIC) == Modifier.STATIC) && !connection.isAllowDirectRequests()) {
				writeDirectRequestForbidden(COMMAND_METHODRESULT_FAIL, reqid);
				return;
			}

			transfermethod = variables.getProperties().getExecutableProperties(method);
			args = readMethodParameters(variables, in);
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			writeCommandExceptionResult(COMMAND_METHODRESULT_FAIL, reqid, e, false, 0);
			return;
		}
		invokeAndWriteMethodCall(reqid, variables, invokeobject, transfermethod, args);
	}

	private void handleCommandMethodCallRedispatch(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		try {
			int dispatchid = in.readInt();
			RMIVariables variables = readVariablesValidate(in);
			int localid = in.readInt();
			Object invokeobject = readMethodInvokeObject(variables, localid);

			if (invokeobject == null && !connection.isAllowDirectRequests()) {
				writeDirectRequestForbidden(COMMAND_METHODRESULT_FAIL, reqid);
				return;
			}
			Method method = readMethod(in, invokeobject);
			//TODO check disallowing direct requests if the called method has a non-interface declaring class (or java.lang.Object)
			if (((method.getModifiers() & Modifier.STATIC) == Modifier.STATIC) && !connection.isAllowDirectRequests()) {
				writeDirectRequestForbidden(COMMAND_METHODRESULT_FAIL, reqid);
				return;
			}

			MethodTransferProperties transfermethod = variables.getProperties().getExecutableProperties(method);
			Object[] args = readMethodParameters(variables, in);

			requestHandler.addResponse(dispatchid,
					new MethodCallRedispatchResponse(reqid, invokeobject, transfermethod, variables, args));
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			writeCommandExceptionResult(COMMAND_METHODRESULT_FAIL, reqid, e, false, 0);
		}
	}

	private static Object readMethodInvokeObject(RMIVariables variables, int localid) {
		if (localid == RMIVariables.NO_OBJECT_ID) {
			return null;
		}
		Object result = variables.getObjectWithLocalId(localid);
		if (result == null) {
			throw new RMICallFailedException("Object not found with id: " + localid);
		}
		return result;
	}

	private void handleCommandMethodResult(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		boolean interrupted = false;
		int interruptreqcount = 0;
		try {
			RMIVariables variables = readVariablesValidate(in);
			int compressedinterruptstatus = in.readInt();
			Object value = readObject(variables, in);

			interrupted = isCompressedInterruptStatusInvokerThreadInterrupted(compressedinterruptstatus);
			interruptreqcount = getCompressedInterruptStatusDeliveredRequestCount(compressedinterruptstatus);

			requestHandler.addResponse(reqid, new MethodCallResponse(interrupted, interruptreqcount, value));
		} catch (IOException e) {
			requestHandler.addResponse(reqid, new MethodCallFailedResponse(interrupted, interruptreqcount,
					new RMIIOFailureException("Failed to read method result.", e)));
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			requestHandler.addResponse(reqid, new MethodCallFailedResponse(interrupted, interruptreqcount,
					new RMICallFailedException("Failed to read method result.", e)));
		}
	}

	private void handleCommandCloseVariables(DataInputUnsyncByteArrayInputStream in) throws EOFException {
		RMIVariables vars = readVariablesImpl(in);
		if (vars != null) {
			connection.remotelyClosedVariables(vars);
		}
	}

	private void handleCommandReferencesReleased(DataInputUnsyncByteArrayInputStream in) throws EOFException {
		RMIVariables vars = readVariablesImpl(in);
		if (vars != null) {
			int localid = in.readInt();
			int count = in.readInt();

			vars.referencesReleased(localid, count);
		}
	}

	private void handleCommand(short command, DataInputUnsyncByteArrayInputStream in) throws IOException {
		IOBiConsumer<RMIStream, DataInputUnsyncByteArrayInputStream> handler = COMMAND_HANDLERS.get(command);
		if (handler == null) {
			handleUnknownCommand(command, in);
		} else {
			handler.accept(this, in);
		}
	}

	private void runInput() {
		reader_try:
		try {
			blockIn.nextBlock();
			StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> fullblock = connection.getCachedByteBuffer();
			try {
				DataOutputUnsyncByteArrayOutputStream fullblockbuf = fullblock.get();
				fullblockbuf.readFrom(blockIn);

				try (DataInputUnsyncByteArrayInputStream in = new DataInputUnsyncByteArrayInputStream(
						fullblockbuf.toByteArrayRegion())) {
					short command = in.readShort();
					if (command == COMMAND_STREAM_CLOSED) {
						connection.clientClose();
						//go and close the socket
						break reader_try;
					}
					connection.offerStreamTask(this::runInput);
					handleCommand(command, in);
					if (in.available() > 0) {
						System.err.println("Warning: RMI Stream: Failed to read block fully. (Command: " + command
								+ ", remaining: " + in.available() + ")");
					}
				} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError e) {
					System.err.println("Error: Command block handling failure: " + e);
				}
			} finally {
				connection.releaseCachedByteBuffer(fullblock);
			}
			//handling the input block completed successfully,
			//return and dont close the sockets
			return;
		} catch (IOException e) {
			if (!streamCloseWritten) {
				System.err.println("RMI stream error: " + e);
				//XXX should make an option to print these verbose errors with full stacktrace
			}
			streamError(e);
		} catch (Exception e) {
			//dont care previous exit code, just set it
			streamError(e);
		}
		synchronized (outLock) {
			IOUtils.closeExc(blockIn);
		}
		connection.removeStream(this);
	}

	private static ClassLoader getClassLoaderWithId(RMIVariables variables, int localid) {
		if (localid == RMIVariables.NO_OBJECT_ID) {
			return null;
		}
		Object obj = variables.getObjectWithLocalId(localid);
		if (obj instanceof ClassLoader) {
			return (ClassLoader) obj;
		}
		throw new RMICallFailedException(
				"Object with id: " + localid + " is not an instance of " + ClassLoader.class.getName());
	}

	void writeCommandReferencesReleased(RMIVariables variables, int remoteid, int count) throws IOException {
		if (count <= 0) {
			throw new RMICallFailedException("Count must be greater than zero: " + count);
		}
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_REFERENCES_RELEASED);
			writeVariables(variables, out);
			out.writeInt(remoteid);
			out.writeInt(count);
		}
	}

	private void instantiateAndWriteUnknownNewInstanceCall(int reqid, RMIVariables variables, ClassLoader cl,
			String classname, String[] argclassnames, Object[] args) throws IOException {

		Thread thread = Thread.currentThread();
		variables.addOngoingRequest();
		try {
			int interruptreqcount = 0;
			Class<?> clazz;
			int localindex;
			try {
				connection.addRequestThread(reqid, thread);
				try {
					clazz = Class.forName(classname, false, cl);
					ClassLoader argcl = clazz.getClassLoader();
					int arglen = argclassnames.length;
					Class<?>[] argclasses = new Class<?>[arglen];
					for (int i = 0; i < arglen; i++) {
						String argcname = argclassnames[i];
						Class<?> primc = ReflectUtils.primitiveNameToPrimitiveClass(argcname);
						if (primc != null) {
							argclasses[i] = primc;
						} else {
							argclasses[i] = Class.forName(argcname, false, argcl);
						}
					}
					Constructor<?> constructor = clazz.getDeclaredConstructor(argclasses);
					Object instance = invokeConstructorWithRequestId(constructor, args, reqid);
					localindex = variables.getLocalInstanceIdIncreaseReference(instance);
				} finally {
					interruptreqcount = connection.removeRequestThread(reqid);
				}
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
				writeCommandExceptionResult(COMMAND_UNKNOWN_NEWINSTANCE_RESULT, reqid, e, Thread.interrupted(),
						interruptreqcount);
				return;
			}
			writeCommandUnknownNewInstanceResult(variables, reqid, localindex, getPublicNonAssignableInterfaces(clazz),
					Thread.interrupted(), interruptreqcount);
		} finally {
			variables.removeOngoingRequest();
		}
	}

	public static Set<Class<?>> getPublicNonAssignableInterfaces(Class<?> clazz) {
		Set<Class<?>> itfs = ReflectUtils.getAllInterfaces(clazz);
		reducePublicNonAssignableInterfaces(itfs);
		return itfs;
	}

	public static void reducePublicNonAssignableInterfaces(Set<Class<?>> itfs) {
		if (itfs.isEmpty()) {
			return;
		}
		for (Iterator<Class<?>> it = itfs.iterator(); it.hasNext();) {
			Class<?> itf = it.next();
			if (!itf.isInterface() || !Modifier.isPublic(itf.getModifiers())) {
				//the type might not be an interface if read from an other endpoint
				it.remove();
			}
		}
		ReflectUtils.reduceAssignableTypes(itfs);
	}

	private void instantiateAndWriteNewInstanceCall(int reqid, RMIVariables variables, Constructor<?> constructor,
			Object[] args) throws IOException {
		Thread thread = Thread.currentThread();
		variables.addOngoingRequest();
		try {
			int interruptreqcount = 0;
			int localindex;
			try {
				connection.addRequestThread(reqid, thread);
				try {
					Object instance = invokeConstructorWithRequestId(constructor, args, reqid);
					localindex = variables.getLocalInstanceIdIncreaseReference(instance);
				} finally {
					interruptreqcount = connection.removeRequestThread(reqid);
				}
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
				writeCommandExceptionResult(COMMAND_NEWINSTANCERESULT_FAIL, reqid, e, Thread.interrupted(),
						interruptreqcount);
				return;
			}
			writeCommandNewInstanceResult(reqid, localindex, Thread.interrupted(), interruptreqcount);
		} finally {
			variables.removeOngoingRequest();
		}
	}

	private void invokeAndWriteMethodCall(int reqid, RMIVariables variables, Object invokeobject,
			MethodTransferProperties method, Object[] args) throws IOException {
		Thread thread = Thread.currentThread();
		variables.addOngoingRequest();
		try {
			int interruptreqcount = 0;
			Object dispatchresult;
			try {
				connection.addRequestThread(reqid, thread);
				try {
					dispatchresult = invokeMethodWithRequestId(method.getExecutable(), invokeobject, args, reqid);
				} finally {
					interruptreqcount = connection.removeRequestThread(reqid);
				}
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
				writeCommandExceptionResult(COMMAND_METHODRESULT_FAIL, reqid, e, Thread.interrupted(),
						interruptreqcount);
				return;
			}
			writeCommandMethodResult(variables, reqid, dispatchresult, method, Thread.interrupted(), interruptreqcount);
		} finally {
			variables.removeOngoingRequest();
		}
	}

	private void writeCommandInterruptRequest(int reqid) throws IOException {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_INTERRUPT_REQUEST);
			out.writeInt(reqid);
		}
	}

	private void writeCommandPing(int reqid) throws IOException {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_PING);
			out.writeInt(reqid);
		}
	}

	private void writeCommandGetContextVar(int reqid, RMIVariables vars, String variableid) throws IOException {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_GET_CONTEXT_VAR);
			writeVariables(vars, out);
			out.writeInt(reqid);
			out.writeUTF(variableid);
		}
	}

	private void writeGetContextVarResponse(int reqid, RMIVariables vars, Object result) throws IOException {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_GET_CONTEXT_VAR_RESPONSE);
			writeVariables(vars, out);
			out.writeInt(reqid);
			if (result == null) {
				writeNullObject(out);
			} else {
				writeNewRemoteObject(vars, result, out);
			}
		}
	}

	private void writeCommandPong(int reqid) throws IOException {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_PONG);
			out.writeInt(reqid);
		}
	}

	private void writeCommandCachedClass(Class<?> clazz, int index) {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_CACHED_CLASS);
			writeClassData(clazz, out);
			out.writeInt(index);
		} catch (IOException e) {
			streamError(e);
		}
	}

	private void writeCommandCachedClassLoader(ClassLoader cl, int index) {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_CACHED_CLASSLOADER);
			writeClassLoaderData(cl, out);
			out.writeInt(index);
		} catch (IOException e) {
			streamError(e);
		}
	}

	private void writeCommandCachedMethod(Method method, int index) {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_CACHED_METHOD);
			writeMethodData(method, out);
			out.writeInt(index);
		} catch (IOException e) {
			streamError(e);
		}
	}

	private void writeCommandCachedConstructor(Constructor<?> constructor, int index) {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_CACHED_CONSTRUCTOR);
			writeConstructorData(constructor, out);
			out.writeInt(index);
		} catch (IOException e) {
			streamError(e);
		}
	}

	private void writeCommandCachedField(Field field, int index) {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_CACHED_FIELD);
			writeFieldData(field, out);
			out.writeInt(index);
		} catch (IOException e) {
			streamError(e);
		}
	}

	private void writeCommandNewVariablesResult(int reqid, int localid) throws IOException {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_NEW_VARIABLES_RESULT);
			out.writeInt(reqid);
			out.writeInt(localid);
		}
	}

	/**
	 * Presents an opportunity to the subclass to handle an unknown protocol command.
	 * 
	 * @param command
	 *            The command received.
	 * @param in
	 *            The input block for the command.
	 * @throws IOException
	 *             Thrown in case of an error.
	 */
	protected void handleUnknownCommand(short command, DataInputUnsyncByteArrayInputStream in) {
		throw new RMICallFailedException("Unknown command: " + command);
	}

	static void writeString(String s, DataOutputUnsyncByteArrayOutputStream out) {
		out.writeStringLengthChars(s);
	}

	static String readString(DataInputUnsyncByteArrayInputStream in) throws IOException {
		return in.readStringLengthChars();
	}

	private void writeNewRemoteObject(RMIVariables variables, Object obj, DataOutputUnsyncByteArrayOutputStream out) {
		int localid = variables.getLocalInstanceIdIncreaseReference(obj);
		out.writeShort(OBJECT_NEW_REMOTE);
		//XXX write only a class set identifier and cache these
		writeClasses(getPublicNonAssignableInterfaces(obj.getClass()), out);
		out.writeInt(localid);
	}

	private Object readNewRemoteObject(RMIVariables variables, DataInputUnsyncByteArrayInputStream in)
			throws IOException {
		if (variables == null) {
			throw new IOException("Failed to read remote object with null variables.");
		}
		Set<Class<?>> classes;
		try {
			classes = readClassesSet(in);
		} catch (ClassSetPartiallyReadException e) {
			classes = e.getReadClasses();
		}
		int remoteid = in.readInt();
		return variables.getProxyIncreaseReference(classes, remoteid);
	}

	private void writeCommandUnknownNewInstanceResult(RMIVariables variables, int reqid, int localindex,
			Set<Class<?>> interfaces, boolean currentthreadinterrupted, int interruptreqcount) throws IOException {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_UNKNOWN_NEWINSTANCE_RESULT);
			out.writeInt(reqid);
			writeVariables(variables, out);
			out.writeInt(compressInterruptStatus(currentthreadinterrupted, interruptreqcount));
			out.writeInt(localindex);
			writeClasses(interfaces, out);
		}
	}

	private void writeCommandNewInstanceResult(int reqid, int localindex, boolean currentthreadinterrupted,
			int interruptreqcount) throws IOException {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_NEWINSTANCE_RESULT);
			out.writeInt(reqid);
			out.writeInt(localindex);
			out.writeInt(compressInterruptStatus(currentthreadinterrupted, interruptreqcount));
		}
	}

	private void writeCommandMethodCallAsync(RMIVariables variables, int remoteid, MethodTransferProperties method,
			Object[] arguments) throws IOException {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_METHODCALL_ASYNC);
			writeVariables(variables, out);
			out.writeInt(remoteid);

			writeMethod(method.getExecutable(), out);
			writeMethodParameters(variables, method, arguments, out);
		}
	}

	private void writeCommandMethodCall(RMIVariables variables, int reqid, int remoteid,
			MethodTransferProperties method, Object[] arguments, Integer dispatch) throws IOException {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			if (dispatch == null) {
				out.writeShort(COMMAND_METHODCALL);
				out.writeInt(reqid);
			} else {
				out.writeShort(COMMAND_METHODCALL_REDISPATCH);
				out.writeInt(reqid);
				out.writeInt(dispatch);
			}
			writeVariables(variables, out);
			out.writeInt(remoteid);

			writeMethod(method.getExecutable(), out);
			writeMethodParameters(variables, method, arguments, out);
		}
	}

	private void writeCommandNewRemoteInstance(RMIVariables variables, int reqid,
			ConstructorTransferProperties<?> constructor, Object[] arguments, Integer dispatch) throws IOException {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			if (dispatch == null) {
				out.writeShort(COMMAND_NEWINSTANCE);
				out.writeInt(reqid);
			} else {
				out.writeShort(COMMAND_NEWINSTANCE_REDISPATCH);
				out.writeInt(reqid);
				out.writeInt(dispatch);
			}
			writeVariables(variables, out);

			writeConstructor(constructor.getExecutable(), out);
			writeMethodParameters(variables, constructor, arguments, out);
		}
	}

	private void writeCommandNewRemoteInstanceUnknownClass(RMIVariables variables, int reqid, int remoteclassloaderid,
			String classname, String[] argumentclassnames, Object[] arguments, Integer dispatch) throws IOException {
		if (argumentclassnames.length != arguments.length) {
			throw new RMICallFailedException("Length of argument types doesn't match provided argument object count: "
					+ argumentclassnames + " != " + arguments.length);
		}
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			if (dispatch == null) {
				out.writeShort(COMMAND_NEWINSTANCE_UNKNOWNCLASS);
				out.writeInt(reqid);
			} else {
				out.writeShort(COMMAND_NEWINSTANCE_UNKNOWNCLASS_REDISPATCH);
				out.writeInt(reqid);
				out.writeInt(dispatch);
			}
			writeVariables(variables, out);
			out.writeInt(remoteclassloaderid);

			out.writeUTF(classname);
			out.writeInt(argumentclassnames.length);
			for (int i = 0; i < argumentclassnames.length; i++) {
				out.writeUTF(argumentclassnames[0]);
				Object obj = arguments[i];
				writeObjectUsingWriteHandler(RMIObjectWriteHandler.defaultWriter(), variables, obj, out,
						ObjectUtils.classOf(obj));
			}
		}
	}

	private void writeCommandMethodResult(RMIVariables variables, int reqid, Object returnvalue,
			MethodTransferProperties executableproperties, boolean currentthreadinterrupted, int interruptreqcount)
			throws IOException {
		returnvalue = unwrapWrapperForTransfer(returnvalue, variables);
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();

			out.writeShort(COMMAND_METHODRESULT);
			out.writeInt(reqid);
			writeVariables(variables, out);

			out.writeInt(compressInterruptStatus(currentthreadinterrupted, interruptreqcount));

			writeObjectUsingWriteHandler(executableproperties.getReturnValueWriter(), variables, returnvalue, out,
					executableproperties.getReturnType());
		}
	}

	private static int compressInterruptStatus(boolean currentthreadinterrupted, int interruptreqcount) {
		return currentthreadinterrupted ? -interruptreqcount - 1 : interruptreqcount;
	}

	private static boolean isCompressedInterruptStatusInvokerThreadInterrupted(int comressedstatus) {
		return comressedstatus < 0;
	}

	private static int getCompressedInterruptStatusDeliveredRequestCount(int comressedstatus) {
		if (comressedstatus >= 0) {
			return comressedstatus;
		}
		return -(comressedstatus + 1);
	}

	private void writeCommandExceptionResult(short commandname, int reqid, Throwable exc,
			boolean currentthreadinterrupted, int interruptreqcount) throws IOException {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(commandname);
			out.writeInt(reqid);
			out.writeInt(compressInterruptStatus(currentthreadinterrupted, interruptreqcount));
			writeException(exc, out);
		} catch (IOException ioe) {
			ioe.addSuppressed(exc);
			throw ioe;
		}
	}

	private void writeException(Throwable exc, DataOutputUnsyncByteArrayOutputStream out) throws IOException {
		if (!tryWriteException(exc, out)) {
			Throwable ioe = new RMIStackTracedException(exc);
			if (!tryWriteException(ioe, out)) {
				throw new IOException(ioe);
			}
		}
	}

	private boolean tryWriteException(Throwable exc, DataOutputUnsyncByteArrayOutputStream out) {
		int startsize = out.size();
		try (ObjectOutputStream oos = new RMISerializeObjectOutputStream(out, connection)) {
			oos.writeObject(exc);
			oos.flush();
		} catch (IOException e) {
			out.reduceSize(startsize);
			return false;
		}
		return true;
	}

	private Throwable readException(DataInputUnsyncByteArrayInputStream in) {
		Throwable exc;
		try (ObjectInputStream ois = new ExceptionResolvingRMISerializeObjectInputStream(
				StreamUtils.closeProtectedInputStream(in), connection)) {
			exc = (Throwable) ois.readObject();
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError e) {
			throw new RMIObjectTransferFailureException("Failed to read remote exception.", e);
		}
		return exc;
	}

	private static final Map<String, Class<? extends RMIRuntimeException>> EXCEPTION_SERIALIZE_FALLBACK_CLASSES = new TreeMap<>();
	static {
		EXCEPTION_SERIALIZE_FALLBACK_CLASSES.put(RMIRuntimeException.class.getName(), RMIRuntimeException.class);
		EXCEPTION_SERIALIZE_FALLBACK_CLASSES.put(RMIObjectTransferFailureException.class.getName(),
				RMIObjectTransferFailureException.class);
		EXCEPTION_SERIALIZE_FALLBACK_CLASSES.put(RMICallFailedException.class.getName(), RMICallFailedException.class);
		EXCEPTION_SERIALIZE_FALLBACK_CLASSES.put(RMIStackTracedException.class.getName(),
				RMIStackTracedException.class);
		EXCEPTION_SERIALIZE_FALLBACK_CLASSES.put(RMICallForbiddenException.class.getName(),
				RMICallForbiddenException.class);
		EXCEPTION_SERIALIZE_FALLBACK_CLASSES.put(RMIInvalidConfigurationException.class.getName(),
				RMIInvalidConfigurationException.class);
		EXCEPTION_SERIALIZE_FALLBACK_CLASSES.put(RMIIOFailureException.class.getName(), RMIIOFailureException.class);
		EXCEPTION_SERIALIZE_FALLBACK_CLASSES.put(RMIProxyCreationFailedException.class.getName(),
				RMIProxyCreationFailedException.class);
	}

	private static final class ExceptionResolvingRMISerializeObjectInputStream extends RMISerializeObjectInputStream {
		private ExceptionResolvingRMISerializeObjectInputStream(InputStream in, RMIConnection connection)
				throws IOException {
			super(in, connection);
		}

		@Override
		protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
			try {
				return super.resolveClass(desc);
			} catch (ClassNotFoundException e) {
				String cname = desc.getName();
				Class<? extends RMIRuntimeException> found = EXCEPTION_SERIALIZE_FALLBACK_CLASSES.get(cname);
				if (found != null) {
					return found;
				}
				throw e;
			}
		}
	}

	//XXX handle Proxy objects in object streams?
	private static class RMISerializeObjectOutputStream extends ObjectOutputStream {
		private RMIConnection connection;

		public RMISerializeObjectOutputStream(OutputStream out, RMIConnection connection) throws IOException {
			super(out);
			this.connection = connection;
		}

		@Override
		protected void annotateClass(Class<?> cl) throws IOException {
			String clid = connection.getClassLoaderId(cl.getClassLoader());
			writeClassLoaderId(this, clid);
		}
	}

	private static class RMISerializeObjectInputStream extends ObjectInputStream {
		private RMIConnection connection;

		public RMISerializeObjectInputStream(InputStream in, RMIConnection connection) throws IOException {
			super(in);
			this.connection = connection;
		}

		@Override
		protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
			String clid = readClassLoaderId(this);

			ClassLoader classloader;
			try {
				classloader = getClassLoaderById(connection, clid);
			} catch (ClassLoaderNotFoundIOException e) {
				throw new ClassNotFoundException("Class not found: " + desc.getName(), e);
			}
			try {
				Class<?> result = Class.forName(desc.getName(), false, classloader);
				return result;
			} catch (ClassNotFoundException e) {
				throw new ClassNotFoundException(
						"Class not found in classloader with id: " + StringUtils.toStringQuoted(clid), e);
			}
		}
	}

	private void writeCommandNewVariables(String name, int identifier, int reqid) throws IOException {
		checkClosed();
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_NEW_VARIABLES);
			out.writeInt(reqid);
			out.writeInt(identifier);
			out.writeUTF(name == null ? "" : name);
		}
	}

	private void writeCommandCloseVariables(RMIVariables variables) throws IOException {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_CLOSE_VARIABLES);
			writeVariables(variables, out);
		}
	}

	protected void checkClosed() throws RMIIOFailureException {
		if (streamCloseWritten) {
			throw new RMIIOFailureException("Stream closed.");
		}
	}

	private static void checkAborting(Integer currentrequestid, RMIVariables vars) throws RMIIOFailureException {
		if (currentrequestid != null && vars.isAborting()) {
			throw new RMIIOFailureException("RMI variables is aborting. No new requests are allowed.");
		}
	}

	private void checkAborting(Integer currentrequestid) throws RMIIOFailureException {
		if (currentrequestid != null && connection.isAborting()) {
			throw new RMIIOFailureException("RMI connection is aborting. No new requests are allowed.");
		}
	}

	private static Integer nullizeRequestId(int reqid) {
		return reqid == 0 ? null : reqid;
	}

	private void checkAborting() throws RMIIOFailureException {
		checkAborting(nullizeRequestId(currentThreadPreviousMethodCallRequestId.get().get()));
	}

	private Object invokeMethodWithRequestId(Method method, Object object, Object[] arguments, int reqid)
			throws InvocationTargetException {
		AtomicInteger reqidint = currentThreadPreviousMethodCallRequestId.get();
		int currentid = reqidint.getAndSet(reqid);
		try {
			return ReflectUtils.invokeMethod(object, method, arguments);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new RMICallFailedException(e + " on " + method + " with argument types: "
					+ Arrays.toString(ObjectUtils.classOfArrayElements(arguments)));
		} finally {
			reqidint.set(currentid);
		}
	}

	private <C> C invokeConstructorWithRequestId(Constructor<C> constructor, Object[] arguments, int reqid)
			throws InvocationTargetException, IllegalAccessException, InstantiationException {
		AtomicInteger reqidint = currentThreadPreviousMethodCallRequestId.get();
		int currentid = reqidint.getAndSet(reqid);
		try {
			return ReflectUtils.invokeConstructor(constructor, arguments);
		} finally {
			reqidint.set(currentid);
		}
	}

	Object callMethod(RMIVariables variables, int remoteid, MethodTransferProperties method, Object[] arguments)
			throws RMIIOFailureException, InvocationTargetException {
		Integer currentservingrequest = nullizeRequestId(currentThreadPreviousMethodCallRequestId.get().get());
		checkAborting(currentservingrequest, variables);
		return callMethod(variables, remoteid, method, currentservingrequest, arguments);
	}

	private Object callMethod(RMIVariables variables, int remoteid, MethodTransferProperties method, Integer dispatch,
			Object[] arguments) throws RMIIOFailureException, InvocationTargetException {
		try (Request request = requestHandler.newRequest()) {
			int reqid = request.getRequestId();
			try {
				writeCommandMethodCall(variables, reqid, remoteid, method, arguments, dispatch);
			} catch (IOException e) {
				streamError(e);
				throw new RMIIOFailureException(e);
			}
			MethodCallResponse mcr = waitInterruptTrackingResponse(request, MethodCallResponse.class);

			return mcr.getReturnValue();
		}
	}

	void callMethodAsync(RMIVariables variables, int remoteid, MethodTransferProperties method, Object[] arguments)
			throws RMIIOFailureException {
		checkAborting(null, variables);
		try {
			writeCommandMethodCallAsync(variables, remoteid, method, arguments);
		} catch (IOException e) {
			streamError(e);
			throw new RMIIOFailureException(e);
		}
	}

	private <RetType extends InterruptStatusTrackingRequestResponse> RetType waitInterruptTrackingResponse(
			Request request, Class<RetType> type) {
		try {
			int interruptreqcount = 0;
			while (true) {
				try {
					RequestResponse response = (RequestResponse) request.waitResponseInterruptible();
					if (type.isInstance(response)) {
						InterruptStatusTrackingRequestResponse intres = (InterruptStatusTrackingRequestResponse) response;
						if (intres.isInvokerThreadInterrupted()
								|| intres.getDeliveredInterruptRequestCount() < interruptreqcount) {
							Thread.currentThread().interrupt();
						}
						@SuppressWarnings("unchecked")
						RetType res = (RetType) intres;
						return res;
					}
					invokeDispatchingOrThrow(response);
				} catch (InterruptedException e) {
					++interruptreqcount;
					writeCommandInterruptRequest(request.getRequestId());
				}
			}
		} catch (IOException e) {
			streamError(e);
			throw new RMIIOFailureException(e);
		}
	}

	private void invokeDispatchingOrThrow(RequestResponse response) throws RMIIOFailureException {
		if (response instanceof RedispatchResponse) {
			RedispatchResponse mrr = (RedispatchResponse) response;
			try {
				mrr.executeRedispatchAction();
			} catch (IOException e) {
				streamError(e);
			}
		} else {
			throw new RMIIOFailureException("Unknown response received: " + response);
		}
	}

	protected final void streamError(Throwable e) {
		connection.streamError(this, e);
	}

	Object newRemoteInstance(RMIVariables variables, ConstructorTransferProperties<?> constructor, Object... arguments)
			throws RMIIOFailureException, InvocationTargetException, RMICallFailedException {
		Integer currentservingrequest = nullizeRequestId(currentThreadPreviousMethodCallRequestId.get().get());
		checkAborting(currentservingrequest, variables);
		return newRemoteInstance(variables, constructor, currentservingrequest, arguments);
	}

	private Object newRemoteInstance(RMIVariables variables, ConstructorTransferProperties<?> constructor,
			Integer dispatch, Object... arguments)
			throws RMIIOFailureException, InvocationTargetException, RMICallFailedException {
		try (Request request = requestHandler.newRequest()) {
			int reqid = request.getRequestId();

			try {
				writeCommandNewRemoteInstance(variables, reqid, constructor, arguments, dispatch);
			} catch (IOException e) {
				streamError(e);
				throw new RMIIOFailureException(e);
			}

			NewInstanceResponse response = waitInterruptTrackingResponse(request, NewInstanceResponse.class);

			int remoteid = response.getRemoteId();
			Class<?> declaringclass = constructor.getExecutable().getDeclaringClass();

			return variables.getProxyIncreaseReference(declaringclass, remoteid);
		}
	}

	Object newRemoteOnlyInstance(RMIVariables variables, int remoteclassloaderid, String classname,
			String[] constructorargumentclasses, Object[] constructorarguments)
			throws RMIIOFailureException, RMICallFailedException, InvocationTargetException {
		Integer currentservingrequest = nullizeRequestId(currentThreadPreviousMethodCallRequestId.get().get());
		checkAborting(currentservingrequest, variables);
		return newRemoteOnlyInstance(variables, remoteclassloaderid, classname, constructorargumentclasses,
				constructorarguments, currentservingrequest);
	}

	private Object newRemoteOnlyInstance(RMIVariables variables, int remoteclassloaderid, String classname,
			String[] constructorargumentclasses, Object[] constructorarguments, Integer dispatch)
			throws RMIIOFailureException, RMICallFailedException, InvocationTargetException {
		try (Request request = requestHandler.newRequest()) {
			int reqid = request.getRequestId();

			try {
				writeCommandNewRemoteInstanceUnknownClass(variables, reqid, remoteclassloaderid, classname,
						constructorargumentclasses, constructorarguments, dispatch);
			} catch (IOException e) {
				streamError(e);
				throw new RMIIOFailureException(e);
			}

			UnknownNewInstanceResponse response = waitInterruptTrackingResponse(request,
					UnknownNewInstanceResponse.class);
			//get remote id before testing for instanceof, as getting the id will throw an appropriate exception
			int remoteid = response.getRemoteId();
			Set<Class<?>> interfaces = response.getInterfaces();

			return variables.getProxyIncreaseReference(interfaces, remoteid);
		}
	}

	public void writeVariablesClosed(RMIVariables variables) throws IOException {
		try {
			writeCommandCloseVariables(variables);
		} catch (IOException e) {
			streamError(e);
			throw e;
		}
	}

	int createNewVariables(String name, int varlocalid) throws RMIRuntimeException {
		checkAborting();

		try (Request request = requestHandler.newRequest()) {
			int reqid = request.getRequestId();
			writeCommandNewVariables(name, varlocalid, reqid);
			NewVariablesResponse nvr = request.waitInstanceOfResponse(NewVariablesResponse.class);
			return nvr.getRemoteIdentifier();
		} catch (IOException e) {
			streamError(e);
			throw new RMIIOFailureException(e);
		}
	}

	public void ping() throws IOException {
		checkAborting();

		try (Request request = requestHandler.newRequest()) {
			int reqid = request.getRequestId();
			writeCommandPing(reqid);
			request.waitInstanceOfResponse(PingResponse.class);
		} catch (IOException e) {
			streamError(e);
			throw e;
		}
	}

	Object getRemoteContextVariable(RMIVariables vars, String variablename) throws IOException {
		checkAborting();

		try (Request request = requestHandler.newRequest()) {
			int reqid = request.getRequestId();
			writeCommandGetContextVar(reqid, vars, variablename);
			return request.waitInstanceOfResponse(GetContextVariableResponse.class).getVariable();
		} catch (IOException e) {
			streamError(e);
			throw e;
		}
	}

	private static final class FieldWeakSupplier implements Supplier<Field> {
		private final WeakReference<Class<?>> declaringClass;
		private final String name;

		public FieldWeakSupplier(Field field) {
			this.declaringClass = new WeakReference<>(field.getDeclaringClass());
			this.name = field.getName();
		}

		@Override
		public Field get() {
			try {
				Class<?> decc = declaringClass.get();
				if (decc == null) {
					return null;
				}
				return decc.getDeclaredField(name);
			} catch (NoSuchFieldException | SecurityException e) {
				//print just in case
				return null;
			}
		}
	}

	private static abstract class ExecutableWeakSupplier<T extends Executable> implements Supplier<T> {
		protected final WeakReference<Class<?>> declaringClass;
		protected final WeakReference<Class<?>>[] parameterTypes;

		@SuppressWarnings("unchecked")
		protected ExecutableWeakSupplier(T executable) {
			this.declaringClass = new WeakReference<>(executable.getDeclaringClass());
			Class<?>[] paramtypes = executable.getParameterTypes();
			this.parameterTypes = (WeakReference<Class<?>>[]) new WeakReference<?>[paramtypes.length];
			for (int i = 0; i < paramtypes.length; i++) {
				this.parameterTypes[i] = new WeakReference<>(paramtypes[i]);
			}
		}

		@Override
		public T get() {
			Class<?> dc = declaringClass.get();
			if (dc == null) {
				return null;
			}
			Class<?>[] types = new Class<?>[parameterTypes.length];
			for (int i = 0; i < types.length; i++) {
				Class<?> t = parameterTypes[i].get();
				if (t == null) {
					//shouldnt ever occurr, but just in case
					return null;
				}
				types[i] = t;
			}
			try {
				return get(dc, types);
			} catch (NoSuchMethodException e) {
				//print just in case
				return null;
			}
		}

		protected abstract T get(Class<?> declaringclass, Class<?>[] parametertypes) throws NoSuchMethodException;
	}

	private static final class MethodWeakSupplier extends ExecutableWeakSupplier<Method> {
		private final String name;

		protected MethodWeakSupplier(Method executable) {
			super(executable);
			this.name = executable.getName();
		}

		@Override
		protected Method get(Class<?> declaringclass, Class<?>[] parametertypes) throws NoSuchMethodException {
			return declaringclass.getDeclaredMethod(name, parametertypes);
		}
	}

	private static final class ConstructorWeakSupplier extends ExecutableWeakSupplier<Constructor<?>> {
		protected ConstructorWeakSupplier(Constructor<?> executable) {
			super(executable);
		}

		@Override
		protected Constructor<?> get(Class<?> declaringclass, Class<?>[] parametertypes) throws NoSuchMethodException {
			return declaringclass.getDeclaredConstructor(parametertypes);
		}

	}
}