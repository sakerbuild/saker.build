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
package saker.build.thirdparty.saker.rmi.connection;

import java.io.Closeable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.lang.invoke.MethodHandles;
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
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;

import saker.build.thirdparty.saker.rmi.connection.RequestHandler.Request;
import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMICallForbiddenException;
import saker.build.thirdparty.saker.rmi.exception.RMIContextVariableNotFoundException;
import saker.build.thirdparty.saker.rmi.exception.RMIIOFailureException;
import saker.build.thirdparty.saker.rmi.exception.RMIInvalidConfigurationException;
import saker.build.thirdparty.saker.rmi.exception.RMIObjectTransferFailureException;
import saker.build.thirdparty.saker.rmi.exception.RMIProxyCreationFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMIResourceUnavailableException;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.rmi.exception.RMIStackTracedException;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.rmi.io.writer.ArrayComponentRMIObjectWriteHandler;
import saker.build.thirdparty.saker.rmi.io.writer.ObjectWriterKind;
import saker.build.thirdparty.saker.rmi.io.writer.RMIObjectWriteHandler;
import saker.build.thirdparty.saker.rmi.io.writer.SelectorRMIObjectWriteHandler;
import saker.build.thirdparty.saker.rmi.io.writer.WrapperRMIObjectWriteHandler;
import saker.build.thirdparty.saker.util.ArrayUtils;
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
import saker.build.thirdparty.saker.util.ref.StrongSoftReference;

// suppress the warnings of unused resource in try-with-resource body
// this is used when we write a command and automatically flush with outputFlusher
@SuppressWarnings("try")
final class RMIStream implements Closeable {
	private static final RMIVariables[] EMPTY_RMI_VARIABLES_ARRAY = new RMIVariables[0];

	public static final String EXCEPTION_MESSAGE_DIRECT_REQUESTS_FORBIDDEN = "Direct requests are forbidden.";

	private static final AtomicIntegerFieldUpdater<RMIStream> AIFU_streamCloseWritten = AtomicIntegerFieldUpdater
			.newUpdater(RMIStream.class, "streamCloseWritten");

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
	//since protocol version 2
	private static final short COMMAND_METHODCALL_CONTEXTVAR = 29;
	private static final short COMMAND_METHODCALL_CONTEXTVAR_REDISPATCH = 30;
	private static final short COMMAND_METHODCALL_CONTEXTVAR_NOT_FOUND = 31;
	private static final short COMMAND_ASYNC_RESPONSE = 32;
	private static final short COMMAND_METHODCALL_ASYNC_WITH_RESPONSE = 33;

	private static final short COMMAND_END_VALUE = 34;

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
	//since protocol version 2
	private static final short OBJECT_WRAPPER2 = 30;
	private static final short OBJECT_SERIALIZED2 = 31;

	private static final short OBJECT_READER_END_VALUE = 32;

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

	interface RMIObjectReaderFunction<T> {
		public T readObject(RMIStream stream, RMIVariables vars, DataInputUnsyncByteArrayInputStream input)
				throws IOException, ClassNotFoundException;
	}

	//package private so testcases can access and override it if necessary
	static final RMIObjectReaderFunction<?>[] OBJECT_READERS = new RMIObjectReaderFunction<?>[OBJECT_READER_END_VALUE];
	private static final RMIObjectReaderFunction<?> OBJECT_READER_UNKNOWN = (s, vars, in) -> {
		throw new RMICallFailedException("Unknown object type.");
	};
	static {
		OBJECT_READERS[OBJECT_NULL] = (s, vars, in) -> null;
		OBJECT_READERS[OBJECT_BOOLEAN] = (s, vars, in) -> in.readBoolean();
		OBJECT_READERS[OBJECT_BYTE] = (s, vars, in) -> in.readByte();
		OBJECT_READERS[OBJECT_SHORT] = (s, vars, in) -> in.readShort();
		OBJECT_READERS[OBJECT_INT] = (s, vars, in) -> in.readInt();
		OBJECT_READERS[OBJECT_LONG] = (s, vars, in) -> in.readLong();
		OBJECT_READERS[OBJECT_FLOAT] = (s, vars, in) -> in.readFloat();
		OBJECT_READERS[OBJECT_DOUBLE] = (s, vars, in) -> in.readDouble();
		OBJECT_READERS[OBJECT_CHAR] = (s, vars, in) -> in.readChar();
		OBJECT_READERS[OBJECT_STRING] = (s, vars, in) -> readString(in);
		OBJECT_READERS[OBJECT_ARRAY] = RMIStream::readObjectArray;
		OBJECT_READERS[OBJECT_ENUM] = (s, vars, in) -> s.readEnum(in);
		OBJECT_READERS[OBJECT_REMOTE] = (s, vars, in) -> readRemoteObject(vars, in);
		OBJECT_READERS[OBJECT_NEW_REMOTE] = RMIStream::readNewRemoteObject;
		OBJECT_READERS[OBJECT_EXTERNALIZABLE] = RMIStream::readExternalizableObject;
		OBJECT_READERS[OBJECT_CLASS] = (s, vars, in) -> s.readClass(in).get(vars.getConnection());
		OBJECT_READERS[OBJECT_METHOD] = (s, vars, in) -> s.readMethod(in, null);
		OBJECT_READERS[OBJECT_CONSTRUCTOR] = (s, vars, in) -> s.readConstructor(in);
		OBJECT_READERS[OBJECT_SERIALIZED] = (s, vars, in) -> s.readSerializedObject(in);
		OBJECT_READERS[OBJECT_WRAPPER] = RMIStream::readWrappedObject;
		OBJECT_READERS[OBJECT_WRAPPER2] = RMIStream::readWrapped2Object;
		OBJECT_READERS[OBJECT_SERIALIZED2] = (s, vars, in) -> s.readSerialized2Object(in);

		OBJECT_READERS[OBJECT_BYTE_ARRAY] = (s, vars, in) -> readObjectByteArray(in);
		OBJECT_READERS[OBJECT_SHORT_ARRAY] = (s, vars, in) -> readObjectShortArray(in);
		OBJECT_READERS[OBJECT_INT_ARRAY] = (s, vars, in) -> readObjectIntArray(in);
		OBJECT_READERS[OBJECT_LONG_ARRAY] = (s, vars, in) -> readObjectLongArray(in);
		OBJECT_READERS[OBJECT_FLOAT_ARRAY] = (s, vars, in) -> readObjectFloatArray(in);
		OBJECT_READERS[OBJECT_DOUBLE_ARRAY] = (s, vars, in) -> readObjectDoubleArray(in);
		OBJECT_READERS[OBJECT_BOOLEAN_ARRAY] = (s, vars, in) -> readObjectBooleanArray(in);
		OBJECT_READERS[OBJECT_CHAR_ARRAY] = (s, vars, in) -> readObjectCharArray(in);
		OBJECT_READERS[OBJECT_CLASSLOADER] = (s, vars, in) -> s.readClassLoader(in).get(vars.getConnection());
		OBJECT_READERS[OBJECT_FIELD] = (s, vars, in) -> s.readField(in, null);
	}

	@FunctionalInterface
	public interface CommandHandler {
		/**
		 * @return <code>true</code> if the next reading task has been offered
		 */
		public boolean handleCommand(RMIStream stream, RunInputRunnable inputrunnable,
				DataInputUnsyncByteArrayInputStream in, ReferencesReleasedAction gcaction) throws IOException;
	}

	@FunctionalInterface
	public interface SimpleCommandHandler extends CommandHandler {
		public void handleCommand(RMIStream stream, DataInputUnsyncByteArrayInputStream in) throws IOException;

		@Override
		public default boolean handleCommand(RMIStream stream, RunInputRunnable inputrunnable,
				DataInputUnsyncByteArrayInputStream in, ReferencesReleasedAction gcaction) throws IOException {
			inputrunnable.offerSelfStreamTask();
			this.handleCommand(stream, in);
			return true;
		}
	}

	@FunctionalInterface
	public interface GarbageCollectionPreventingCommandHandler extends CommandHandler {
		public void handleCommand(RMIStream stream, DataInputUnsyncByteArrayInputStream in,
				ReferencesReleasedAction gcaction) throws IOException;

		@Override
		public default boolean handleCommand(RMIStream stream, RunInputRunnable inputrunnable,
				DataInputUnsyncByteArrayInputStream in, ReferencesReleasedAction gcaction) throws IOException {
			gcaction.increasePendingRequestCount();
			inputrunnable.offerSelfStreamTask();
			this.handleCommand(stream, in, gcaction);
			return true;
		}
	}

	@FunctionalInterface
	public interface PendingResponseSimpleCommandHandler extends SimpleCommandHandler {
		@Override
		public default boolean handleCommand(RMIStream stream, RunInputRunnable inputrunnable,
				DataInputUnsyncByteArrayInputStream in, ReferencesReleasedAction gcaction) throws IOException {
			stream.requestHandlerAddResponseCount();
			try {
				return SimpleCommandHandler.super.handleCommand(stream, inputrunnable, in, gcaction);
			} finally {
				stream.requestHandlerRemoveResponseCount();
			}
		}

	}

	@FunctionalInterface
	public interface PendingResponseGarbageCollectionPreventingCommandHandler extends CommandHandler {
		public void handleCommand(RMIStream stream, DataInputUnsyncByteArrayInputStream in,
				ReferencesReleasedAction gcaction) throws IOException;

		@Override
		public default boolean handleCommand(RMIStream stream, RunInputRunnable inputrunnable,
				DataInputUnsyncByteArrayInputStream in, ReferencesReleasedAction gcaction) throws IOException {
			gcaction.increasePendingRequestCount();
			stream.requestHandlerAddResponseCount();
			try {
				inputrunnable.offerSelfStreamTask();
				this.handleCommand(stream, in, gcaction);
				return true;
			} finally {
				stream.requestHandlerRemoveResponseCount();
			}
		}
	}

	//package private so testcases can access and override it if necessary
	static final CommandHandler[] COMMAND_HANDLERS = new CommandHandler[COMMAND_END_VALUE];
	static {
		COMMAND_HANDLERS[COMMAND_NEWINSTANCE] = (CommandHandler) RMIStream::handleCommandNewInstance;
		COMMAND_HANDLERS[COMMAND_NEWINSTANCE_REDISPATCH] = (PendingResponseGarbageCollectionPreventingCommandHandler) RMIStream::handleCommandNewInstanceRedispatch;
		COMMAND_HANDLERS[COMMAND_NEWINSTANCE_UNKNOWNCLASS] = (CommandHandler) RMIStream::handleCommandNewInstanceUnknownClass;
		COMMAND_HANDLERS[COMMAND_NEWINSTANCE_UNKNOWNCLASS_REDISPATCH] = (PendingResponseGarbageCollectionPreventingCommandHandler) RMIStream::handleCommandNewInstanceUnknownClassRedispatch;
		COMMAND_HANDLERS[COMMAND_METHODCALL] = (CommandHandler) RMIStream::handleCommandMethodCall;
		COMMAND_HANDLERS[COMMAND_METHODCALL_REDISPATCH] = (PendingResponseGarbageCollectionPreventingCommandHandler) RMIStream::handleCommandMethodCallRedispatch;
		COMMAND_HANDLERS[COMMAND_METHODRESULT] = (PendingResponseGarbageCollectionPreventingCommandHandler) RMIStream::handleCommandMethodResult;
		//new instance result doesn't prevent garbage collection, as it only reads a remote index
		COMMAND_HANDLERS[COMMAND_UNKNOWN_NEWINSTANCE_RESULT] = (PendingResponseSimpleCommandHandler) RMIStream::handleCommandNewInstanceUnknownClassResult;
		COMMAND_HANDLERS[COMMAND_METHODRESULT_FAIL] = (PendingResponseSimpleCommandHandler) RMIStream::handleCommandMethodResultFailure;
		COMMAND_HANDLERS[COMMAND_NEWINSTANCERESULT_FAIL] = (PendingResponseSimpleCommandHandler) RMIStream::handleCommandNewInstanceResultFailure;
		COMMAND_HANDLERS[COMMAND_NEW_VARIABLES] = (SimpleCommandHandler) RMIStream::handleCommandNewVariables;
		COMMAND_HANDLERS[COMMAND_GET_CONTEXT_VAR] = (SimpleCommandHandler) RMIStream::handleCommandGetContextVariable;
		COMMAND_HANDLERS[COMMAND_GET_CONTEXT_VAR_RESPONSE] = (PendingResponseSimpleCommandHandler) RMIStream::handleCommandGetContextVariableResult;
		COMMAND_HANDLERS[COMMAND_NEWINSTANCE_RESULT] = (PendingResponseSimpleCommandHandler) RMIStream::handleCommandNewInstanceResult;
		COMMAND_HANDLERS[COMMAND_NEW_VARIABLES_RESULT] = (PendingResponseSimpleCommandHandler) RMIStream::handleCommandNewVariablesResult;
		COMMAND_HANDLERS[COMMAND_CLOSE_VARIABLES] = (SimpleCommandHandler) RMIStream::handleCommandCloseVariables;
		COMMAND_HANDLERS[COMMAND_PING] = (SimpleCommandHandler) RMIStream::handleCommandPing;
		COMMAND_HANDLERS[COMMAND_PONG] = (PendingResponseSimpleCommandHandler) RMIStream::handleCommandPong;
		COMMAND_HANDLERS[COMMAND_CACHED_CLASS] = (SimpleCommandHandler) RMIStream::handleCommandCachedClass;
		COMMAND_HANDLERS[COMMAND_CACHED_CONSTRUCTOR] = (SimpleCommandHandler) RMIStream::handleCommandCachedConstructor;
		COMMAND_HANDLERS[COMMAND_CACHED_CLASSLOADER] = (SimpleCommandHandler) RMIStream::handleCommandCachedClassLoader;
		COMMAND_HANDLERS[COMMAND_CACHED_METHOD] = (SimpleCommandHandler) RMIStream::handleCommandCachedMethod;
		COMMAND_HANDLERS[COMMAND_CACHED_FIELD] = (SimpleCommandHandler) RMIStream::handleCommandCachedField;
		COMMAND_HANDLERS[COMMAND_INTERRUPT_REQUEST] = (SimpleCommandHandler) RMIStream::handleCommandInterruptRequest;
		COMMAND_HANDLERS[COMMAND_DIRECT_REQUEST_FORBIDDEN] = (PendingResponseSimpleCommandHandler) RMIStream::handleCommandDirectRequestForbidden;
		COMMAND_HANDLERS[COMMAND_METHODCALL_ASYNC] = (CommandHandler) RMIStream::handleCommandMethodCallAsync;

		//protocol 2
		COMMAND_HANDLERS[COMMAND_METHODCALL_CONTEXTVAR] = (CommandHandler) RMIStream::handleCommandContextVariableMethodCall;
		COMMAND_HANDLERS[COMMAND_METHODCALL_CONTEXTVAR_REDISPATCH] = (PendingResponseGarbageCollectionPreventingCommandHandler) RMIStream::handleCommandContextVariableMethodCallRedispatch;
		COMMAND_HANDLERS[COMMAND_METHODCALL_CONTEXTVAR_NOT_FOUND] = (PendingResponseSimpleCommandHandler) RMIStream::handleCommandContextVariableMethodCallVariableNotFound;
		COMMAND_HANDLERS[COMMAND_ASYNC_RESPONSE] = (SimpleCommandHandler) RMIStream::handleCommandAsyncResponse;
		COMMAND_HANDLERS[COMMAND_METHODCALL_ASYNC_WITH_RESPONSE] = (CommandHandler) RMIStream::handleCommandMethodCallAsyncWithResponse;
	}

	interface RequestScopeHandler {
		public Integer getCurrentServingRequest();

		public <T> T run(int reqid, Callable<? extends T> runnable) throws Exception;
	}

	public static final class ThreadLocalRequestScopeHandler implements RequestScopeHandler {
		private final ThreadLocal<int[]> currentThreadPreviousMethodCallRequestIdThreadLocal = new ThreadLocal<>();

		@Override
		public <T> T run(int reqid, Callable<? extends T> runnable) throws Exception {
			int[] reqidint = currentThreadPreviousMethodCallRequestIdThreadLocal.get();
			if (reqidint == null) {
				reqidint = new int[] { reqid };
				currentThreadPreviousMethodCallRequestIdThreadLocal.set(reqidint);
				try {
					return runnable.call();
				} finally {
					//clear the thread local to avoid leaks
					currentThreadPreviousMethodCallRequestIdThreadLocal.remove();
				}
			} else {
				int currentid = reqidint[0];
				reqidint[0] = reqid;
				try {
					return runnable.call();
				} finally {
					reqidint[0] = currentid;
				}
			}
		}

		@Override
		public Integer getCurrentServingRequest() {
			int[] reqidint = currentThreadPreviousMethodCallRequestIdThreadLocal.get();
			if (reqidint == null) {
				return null;
			}
			return nullizeRequestId(reqidint[0]);
		}
	}

	//ScopeLocal (or called ExtentLocal, not yet finalized JEP) requets handler is commented out
	//as it is not yet delivered in the upcoming Java releases.
	/*private static final class ScopeLocalRequestScopeHandler implements RequestScopeHandler {
		private final Object scopeLocalInstance;
		private final MethodHandle orElseMethod;
		private final MethodHandle whereCallable;
	
		public ScopeLocalRequestScopeHandler(Object scopeLocalInstance, MethodHandle orElseMethod,
				MethodHandle whereCallable) {
			this.scopeLocalInstance = scopeLocalInstance;
			this.whereCallable = whereCallable;
			this.orElseMethod = orElseMethod.bindTo(scopeLocalInstance);
		}
	
		@Override
		public <T> T run(int reqid, Callable<? extends T> runnable) throws Exception {
			try {
				int[] reqidint = (int[]) (Object) orElseMethod.invokeExact((Object) null);
				if (reqidint == null) {
					//not bound yet
					reqidint = new int[] { reqid };
					//no need to unset the request id in the array, as the scope is over after the call
					//(can't invokeExact this, as ScopeLocal class is not visible for us during compile time)
					return (T) whereCallable.invoke(scopeLocalInstance, reqidint, runnable);
				}
				int currentid = reqidint[0];
				reqidint[0] = reqid;
				try {
					return runnable.call();
				} finally {
					reqidint[0] = currentid;
				}
			} catch (Throwable e) {
				//directly thrown by the MethodHandle.invokeExact calls, as no checked exceptions are expected, this is thrown sneakily
				throw ObjectUtils.sneakyThrow(e);
			}
		}
	
		@Override
		public Integer getCurrentServingRequest() {
			try {
				int[] reqidint = (int[]) (Object) orElseMethod.invokeExact((Object) null);
				if (reqidint == null) {
					return null;
				}
				return nullizeRequestId(reqidint[0]);
			} catch (Throwable e) {
				//directly thrown by the MethodHandle.invokeExact, as no checked exceptions are expected, this is thrown sneakily
				throw ObjectUtils.sneakyThrow(e);
			}
		}
	}*/

	//TODO this should be directly compiled for JDK 19+ and should be released as a multi-release JAR.
	//     this should be done when JDK 19 releases, or when ScopeLocal is no longer an incubator feature 
	//ScopeLocal/ExtentLocal based request handler is not yet delivered, commented out, see above
	//leftover code, to be removed, modified, adapted, when extent locals ship
	/*try {
		//This class is only accessible if the jdk.incubator.concurrent module has been explicitly added to the
		//java command. Otherwise the class won't be found.
		//So if the module is added, it's fine to use it, and we don't need other external parameters
		//to control whether or not we should use it.
		Class<?> scopelocalclass = Class.forName("jdk.incubator.concurrent.ScopeLocal");
		Lookup lookup = MethodHandles.lookup();
		MethodHandle orElse = lookup.findVirtual(scopelocalclass, "orElse", MethodType.genericMethodType(1));
		MethodHandle whereCallable = lookup.findStatic(scopelocalclass, "where", MethodType.methodType(Object.class,
				new Class<?>[] { scopelocalclass, Object.class, Callable.class }));
		MethodHandle newInstanceMethod = lookup.findStatic(scopelocalclass, "newInstance",
				MethodType.methodType(scopelocalclass));
		Object instance;
		try {
			instance = newInstanceMethod.invoke();
		} catch (Throwable e) {
			//new instance creation failed, this shouldn't happen
			//we throw it directly, as the newInstance method doesn't throw checked exceptions
			throw ObjectUtils.sneakyThrow(e);
		}
		RequestScopeHandler scopehandler = new ScopeLocalRequestScopeHandler(instance, orElse, whereCallable);
	} catch (NoSuchMethodException | ClassNotFoundException | IllegalAccessException | ClassCastException
			| WrongMethodTypeException e) {
		//handle exception
	}*/

	private static final AtomicReferenceFieldUpdater<RMIStream, RequestHandlerState> ARFU_requestHandlerState = AtomicReferenceFieldUpdater
			.newUpdater(RMIStream.class, RequestHandlerState.class, "requestHandlerState");
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static final AtomicReferenceFieldUpdater<RMIStream, Function<? super Request, ? extends RMIRuntimeException>> ARFU_requestHandlerCloseReason = (AtomicReferenceFieldUpdater) AtomicReferenceFieldUpdater
			.newUpdater(RMIStream.class, Function.class, "requestHandlerCloseReason");

	private static final AtomicReferenceFieldUpdater<RMIStream, RMIVariables[]> ARFU_associatedVariables = AtomicReferenceFieldUpdater
			.newUpdater(RMIStream.class, RMIVariables[].class, "associatedVariables");

	protected final RMIConnection connection;
	protected final RequestHandler requestHandler;
	protected volatile RequestHandlerState requestHandlerState = RequestHandlerState.INTIAL;
	protected final RequestScopeHandler requestScopeHandler;

	protected final BlockOutputStream blockOut;
	protected final BlockInputStream blockIn;

	/**
	 * Boolean as an integer to support atomic operations.
	 */
	protected volatile int streamCloseWritten;

	/**
	 * A non-reentrant lock for accessing the output stream.
	 */
	protected final Semaphore outSemaphore = new Semaphore(1);

	private final RMICommCache<ClassReflectionElementSupplier> commClasses;
	private final RMICommCache<ClassLoaderReflectionElementSupplier> commClassLoaders;

	private final RMICommCache<MethodReflectionElementSupplier> commMethods;
	private final RMICommCache<ConstructorReflectionElementSupplier> commConstructors;
	private final RMICommCache<FieldReflectionElementSupplier> commFields;

	private final ClassLoader nullClassLoader;
	private final ClassLoaderReflectionElementSupplier nullClassLoaderSupplier;

	/**
	 * The array of associated variables, or <code>null</code> if the stream read IO has shut down.
	 */
	private volatile RMIVariables[] associatedVariables = EMPTY_RMI_VARIABLES_ARRAY;

	private volatile Function<? super Request, ? extends RMIRuntimeException> requestHandlerCloseReason;

	private class CommandFlusher implements Closeable {
		final StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> buffer;

		public CommandFlusher() {
			this(connection.getCachedByteBuffer());
		}

		public CommandFlusher(StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> buffer) {
			this.buffer = buffer;
		}

		public DataOutputUnsyncByteArrayOutputStream getBuffer() {
			return buffer.get();
		}

		@Override
		public void close() {
			flushCommand(buffer);
		}
	}

	//XXX a test is recommended for this mechanism, to check that no gc action is pending when an RMIVariables is closed
	public static final class ReferencesReleasedAction {
		private static final class ReleaseData {
			protected final RMIVariables vars;
			protected final int localId;
			protected final int count;

			public ReleaseData(RMIVariables vars, int localId, int count) {
				this.vars = vars;
				this.localId = localId;
				this.count = count;
			}

			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("ReleaseData[");
				builder.append("vars=");
				builder.append(vars);
				builder.append(", localId=");
				builder.append(localId);
				builder.append(", count=");
				builder.append(count);
				builder.append("]");
				return builder.toString();
			}
		}

		protected static final AtomicIntegerFieldUpdater<RMIStream.ReferencesReleasedAction> AIFU_pendingRequests = AtomicIntegerFieldUpdater
				.newUpdater(RMIStream.ReferencesReleasedAction.class, "pendingRequests");
		private static final AtomicReferenceFieldUpdater<RMIStream.ReferencesReleasedAction, ReleaseData> ARFU_releaseData = AtomicReferenceFieldUpdater
				.newUpdater(RMIStream.ReferencesReleasedAction.class, ReleaseData.class, "releaseData");

		private static final AtomicReferenceFieldUpdater<RMIStream.ReferencesReleasedAction, ReferencesReleasedAction> ARFU_prev = AtomicReferenceFieldUpdater
				.newUpdater(RMIStream.ReferencesReleasedAction.class, ReferencesReleasedAction.class, "prev");
		private static final AtomicReferenceFieldUpdater<RMIStream.ReferencesReleasedAction, ReferencesReleasedAction> ARFU_next = AtomicReferenceFieldUpdater
				.newUpdater(RMIStream.ReferencesReleasedAction.class, ReferencesReleasedAction.class, "next");

		protected volatile ReferencesReleasedAction next;
		protected volatile ReferencesReleasedAction prev;

		protected volatile int pendingRequests;

		protected volatile ReleaseData releaseData;

		public ReferencesReleasedAction() {
		}

		public ReferencesReleasedAction(ReferencesReleasedAction prev) {
			this.prev = prev;
		}

		private boolean isAllPendingRequestsDone() {
			if (this.pendingRequests != 0) {
				return false;
			}
			ReferencesReleasedAction action = this.prev;
			if (action == null) {
				return true;
			}
			if (action.releaseData == null) {
				//the release data from the previous is cleared, so it must not have any pending requests
				this.prev = null;
				return true;
			}
			return false;
		}

		//the next action is a newly constructed clean instance, that has its prev pointer set to this
		//we don't have to do notifications for the next action in case we have no more pending requests,
		//as the next action is a clean one
		//increasePendingRequestCount() is no longer called on this instance after this referencesReleased call
		public void referencesReleased(RMIVariables vars, int localid, int count, ReferencesReleasedAction nextaction) {
			//always set the next pointer so the chain doesn't break
			if (!ARFU_next.compareAndSet(this, null, nextaction)) {
				//next can only be set once, so do a check for this via compareAndSet
				throw new IllegalStateException(
						"Duplicate use of reference release action, failed to set next action.");
			}

			if (isAllPendingRequestsDone()) {
				vars.referencesReleased(localid, count);
				//clear the prev action of the next one, as we have no more pending requests
				nextaction.prev = null;
				return;
			}
			ReleaseData ndata = new ReleaseData(vars, localid, count);
			//doesn't need to use the atomic setter for this, as we already check that this is only set a single time using the field next
			this.releaseData = ndata;
			//query the pending requests again in case it was concurrently modified
			if (!isAllPendingRequestsDone()) {
				//okay, somebody else will do the garbage collection
				return;
			}
			//clear the prev action of the next one, as we have no more pending requests
			nextaction.prev = null;

			//the pending requests are done, and it won't increase, as this function is called on a single thread that manages its increase
			ReleaseData prevdata = ARFU_releaseData.getAndSet(this, null);
			if (prevdata != null) {
				//can be called on the parameter variables, as they must equal to the values in the received ReleaseData
				vars.referencesReleased(localid, count);
			}
			//else okay, somebody else is doing it now
		}

		// this is only called if a given command prevents/delays garbage collection
		// this is only called on the socket reading thread
		// this is only called on the last action in the chain
		//   if a new action is added to the chain, this is no longer called
		public void increasePendingRequestCount() {
			AIFU_pendingRequests.incrementAndGet(this);
		}

		public void decreasePendingRequestCount() {
			int nval = AIFU_pendingRequests.decrementAndGet(this);
			if (nval == 0) {
				//no more pending requests, perform the garbage collection if any, 
				//and all previous action requests are done

				ReferencesReleasedAction action = this.prev;
				if (action != null) {
					if (action.releaseData != null) {
						//the previous action still hasn't done the GC, we can't do it either
						//we'll get a no more request notification when the previous one doesn't have any more pending requests
						return;
					}
					//clear the prev field, as all previous pending requests are done, and we don't need it anymore
					this.prev = null;
				}
				//no more pending requests in the previous actions (if there's any previous action)
				//we can proceed with the reference releasing

				releaseReferenceOnNoMorePendingRequests();

				//notify the next actions about the no more pending requests info
				for (ReferencesReleasedAction next = this.next; next != null;) {
					if (next.noMoreRequestNotificationFromPrevious()) {
						//the action has no more pending requests, proceed and notify up the chain
						next = next.next;
					} else {
						//the action still has some pending request, break the notification chain
						break;
					}
				}
			}
		}

		private boolean noMoreRequestNotificationFromPrevious() {
			//clear the prev field, as all previous pending requests are done, and we don't need it anymore
			this.prev = null;
			if (pendingRequests != 0) {
				//we still have some requests going on
				return false;
			}
			//no more requests, perform the reference release if needed
			releaseReferenceOnNoMorePendingRequests();
			return true;
		}

		private void releaseReferenceOnNoMorePendingRequests() {
			ReleaseData prevdata = ARFU_releaseData.getAndSet(this, null);
			if (prevdata != null) {
				prevdata.vars.referencesReleased(prevdata.localId, prevdata.count);
			}
		}
	}

	public final class RunInputRunnable implements Runnable {
		// this field could be kept on a per-RMIVariables basis, but this is fine for now
		protected ReferencesReleasedAction gcAction = new ReferencesReleasedAction();

		/**
		 * @return <code>false</code> if the stream handling should exit, <code>true</code> if a new read task has been
		 *             posted, and is still running.
		 * @throws IOException
		 */
		private boolean readHandleSingleBlock() throws IOException {
			StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> fullblock = connection.getCachedByteBuffer();
			try {
				DataOutputUnsyncByteArrayOutputStream fullblockbuf = fullblock.get();

				//enclose the reading in a loop, so if we handle a command in a way that doesn't post the next block reading
				//to a new task, but rather continue on this thread, then we can restart
				block_read_loop:
				for (;; fullblockbuf.reset()) {
					blockIn.nextBlock();
					long readcount;
					try {
						readcount = fullblockbuf.readFrom(blockIn);
					} catch (IOException e) {
						if (streamCloseWritten != 0) {
							//the stream is closing. IOException can be ignored
							//go and close the socket
							return false;
						}
						throw e;
					}
					if (readcount == 0) {
						//no more data from the socket
						//if we're exiting, this is fine, go ahead with closing
						//if not, then signal with an EOFException that it is unexpected
						if (streamCloseWritten == 0) {
							throw new EOFException("No more data from stream.");
						}
						return false;
					}

					try (DataInputUnsyncByteArrayInputStream in = new DataInputUnsyncByteArrayInputStream(
							fullblockbuf.toByteArrayRegion())) {
						short command = in.readShort();
						switch (command) {
							case COMMAND_STREAM_CLOSED: {
								connection.clientClose();
								//go and close the socket
								return false;
							}
							case COMMAND_REFERENCES_RELEASED: {
								ReferencesReleasedAction currentaction = this.gcAction;
								ReferencesReleasedAction nextaction = new ReferencesReleasedAction(currentaction);
								this.gcAction = nextaction;

								RMIVariables vars = readVariablesImpl(in);
								if (vars == null) {
									continue block_read_loop;
								}
								int localid = in.readInt();
								int count = in.readInt();

								currentaction.referencesReleased(vars, localid, count, nextaction);
								continue block_read_loop;
							}
							default: {
								CommandHandler commandhandler = getCommandHandler(command);
								if (commandhandler == null) {
									offerSelfStreamTask();
									handleUnknownCommand(command, in);
								} else {
									if (commandhandler.handleCommand(RMIStream.this, this, in, this.gcAction)) {
										break;
									}
									//continue the loop, as the next reading task hasn't been offered
									continue block_read_loop;
								}
								break;
							}
						}
					}
					// no exceptions should escape the command handling. If any escapes, that is an implementation error in the
					// RMI library and shutdown is expected

					// exit the loop by default
					break;
				}
			} finally {
				connection.releaseCachedByteBuffer(fullblock);
			}
			return true;
		}

		protected void offerSelfStreamTask() {
			connection.offerStreamTask(this);
		}

		@Override
		public void run() {
			Throwable streamerrorexc = null;
			try {
				if (readHandleSingleBlock()) {
					//handling the input block completed successfully,
					//return and dont close the sockets
					return;
				} // else the stream is exiting
				initRequestHandlerCloseReason(req -> new RMIIOFailureException("Reached end of RMI stream."));
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
					| ServiceConfigurationError e) {
				streamerrorexc = e;
				addRequestHandlerCloseReason(r -> new RMIIOFailureException("Failed to read data from RMI socket.", e));
			}
			try {
				RMIVariables[] assocvars = ARFU_associatedVariables.getAndSet(RMIStream.this, null);
				//close the request handler, as the stream reading is exiting
				closeRequestHandler();
				if (assocvars != null) {
					//shouldn't be null, but safety check
					for (RMIVariables vars : assocvars) {
						vars.clearAsyncRequestsOnReadIOFailure();
					}
				}
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
					| ServiceConfigurationError e) {
				// this code shouldn't throw, but just in case handle it
				streamerrorexc = IOUtils.addExc(streamerrorexc, e);
			}
			if (streamerrorexc != null) {
				streamError(streamerrorexc);
				//the stream error exception was handled
				streamerrorexc = null;
			}

			outSemaphore.acquireUninterruptibly();
			try {
				try {
					writeStreamCloseIfNotWrittenLocked();
				} catch (Exception e) {
					streamerrorexc = IOUtils.addExc(streamerrorexc, e);
				}
				try {
					IOUtils.close(blockIn);
				} catch (Exception e) {
					streamerrorexc = IOUtils.addExc(streamerrorexc, e);
				}
			} finally {
				outSemaphore.release();
			}
			try {
				connection.removeStream(RMIStream.this);
			} catch (IOException e) {
				streamerrorexc = IOUtils.addExc(streamerrorexc, e);
			}

			//this is the best we can do with any exception
			if (streamerrorexc != null) {
				connection.invokeIOErrorListeners(streamerrorexc);
			}
		}
	}

	public RMIStream(RMIConnection connection, InputStream is, OutputStream os) {
		this.requestHandler = new RequestHandler(connection);
		this.requestScopeHandler = connection.getRequestScopeHandler();

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
		this.nullClassLoaderSupplier = new NullClassLoaderReflectionElementSupplier(this.nullClassLoader);
	}

	public RMIStream(RMIConnection connection, StreamPair streams) {
		this(connection, streams.getInput(), streams.getOutput());
	}

	public void start() {
		connection.offerStreamTask(new RunInputRunnable());
	}

	private Function<? super Request, ? extends RMIRuntimeException> getRequestHandlerCloseReason() {
		Function<? super Request, ? extends RMIRuntimeException> result = this.requestHandlerCloseReason;
		if (result != null) {
			return result;
		}
		return req -> {
			return new RMIResourceUnavailableException("RMI stream closed.");
		};
	}

	private void initRequestHandlerCloseReason(
			Function<? super Request, ? extends RMIRuntimeException> requestHandlerCloseReason) {
		ARFU_requestHandlerCloseReason.compareAndSet(this, null, requestHandlerCloseReason);
	}

	private void addRequestHandlerCloseReason(
			Function<? super Request, ? extends RMIRuntimeException> requestHandlerCloseReason) {
		while (true) {
			Function<? super Request, ? extends RMIRuntimeException> f = this.requestHandlerCloseReason;
			Function<? super Request, ? extends RMIRuntimeException> nfunc;
			if (f == null) {
				nfunc = requestHandlerCloseReason;
			} else {
				nfunc = req -> {
					RMIRuntimeException res = f.apply(req);
					res.addSuppressed(requestHandlerCloseReason.apply(req));
					return res;
				};
			}
			if (ARFU_requestHandlerCloseReason.compareAndSet(this, f, nfunc)) {
				break;
			}
		}
	}

	private void closeRequestHandler() {
		while (true) {
			RequestHandlerState state = this.requestHandlerState;
			if (state.closed) {
				return;
			}
			RequestHandlerState nstate = state.close();
			if (ARFU_requestHandlerState.compareAndSet(this, state, nstate)) {
				if (nstate.unprocessedResponseCount == 0) {
					requestHandler.close(getRequestHandlerCloseReason());
				}
				break;
			}
		}
	}

	private void requestHandlerAddResponseCount() {
		while (true) {
			RequestHandlerState state = this.requestHandlerState;
			if (ARFU_requestHandlerState.compareAndSet(this, state, state.addResponseCount())) {
				break;
			}
		}
	}

	private void requestHandlerRemoveResponseCount() {
		while (true) {
			RequestHandlerState state = this.requestHandlerState;
			RequestHandlerState nstate = state.removeResponseCount();
			if (ARFU_requestHandlerState.compareAndSet(this, state, nstate)) {
				if (nstate.closed && nstate.unprocessedResponseCount == 0) {
					requestHandler.close(getRequestHandlerCloseReason());
				}
				break;
			}
		}
	}

	@Override
	public void close() throws IOException {
		try {
			writeStreamCloseIfNotWritten();
		} finally {
			closeRequestHandler();
		}
	}

	private void writeStreamCloseIfNotWritten() throws IOException {
		if (streamCloseWritten != 0) {
			return;
		}
		outSemaphore.acquireUninterruptibly();
		try {
			initRequestHandlerCloseReason(req -> new RMIIOFailureException("RMI stream closed."));
			writeStreamCloseIfNotWrittenLocked();
		} finally {
			outSemaphore.release();
		}
	}

	private void writeStreamCloseIfNotWrittenLocked() throws IOException {
		if (!AIFU_streamCloseWritten.compareAndSet(this, 0, 1)) {
			return;
		}
		IOException writeexc = null;
		try {
			byte[] buf = { (COMMAND_STREAM_CLOSED >>> 8), COMMAND_STREAM_CLOSED };
			blockOut.write(buf);
			blockOut.nextBlock();
			blockOut.flush();
		} catch (IOException e) {
			//dont care, it might be closed already
			//store the write exception to add later to the close exception if any, else it can be ignored
			writeexc = e;
		}
		try {
			IOUtils.close(blockOut);
		} catch (IOException e) {
			throw IOUtils.addExc(e, writeexc);
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

	protected void flushCommand(StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> bufferref)
			throws RMIIOFailureException {
		try {
			outSemaphore.acquireUninterruptibly();
			//check closed in the lock
			//the exception from the checkClosed() doesn't need to be passed to streamError()
			if (streamCloseWritten != 0) {
				outSemaphore.release();
				throw new RMIResourceUnavailableException("Stream already closed.");
			}
			try {
				//XXX we might remove checkClosed calls from the command writers
				try {
					bufferref.get().writeTo(blockOut);
					blockOut.nextBlock();
					//need to flush, as the underlying output stream might be buffered, or anything
					blockOut.flush();
				} finally {
					//we need to release the lock before handling the possible IOException
					//as that might recursively call other writer functions to the stream
					//(like close writing)
					outSemaphore.release();
				}
			} catch (IOException e) {
				try {
					streamError(e);
				} catch (Throwable e2) {
					e2.addSuppressed(e);
					throw e2;
				}
				throw new RMIIOFailureException("Failed to write RMI command to stream.", e);
			}
		} finally {
			connection.releaseCachedByteBuffer(bufferref);
		}
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
				throw new RMIObjectTransferFailureException("Unrecognized ObjectWriterKind: " + kind);
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
		if (obj instanceof Throwable) {
			writeSerializedObject(obj, out);
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
		RMITransferPropertiesHolder properties = variables.getPropertiesCheckClosed();
		ClassTransferProperties<?> objclassproperties = properties.getClassProperties(objclass);
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
			InvalidObjectException te = new InvalidObjectException(
					"Failed to cast object to array. (" + obj.getClass() + ":" + obj + ")");
			te.initCause(e);
			throw te;
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
		ClassLoaderReflectionElementSupplier clres = getClassLoaderReflectionElementSupplier(cl);
		writeClassLoader(clres, out);
	}

	private ClassLoaderReflectionElementSupplier getClassLoaderReflectionElementSupplier(ClassLoader cl) {
		if (cl == nullClassLoader) {
			return nullClassLoaderSupplier;
		}
		String clid = connection.getClassLoaderId(cl);
		DynamicClassLoaderReflectionElementSupplier clres = new DynamicClassLoaderReflectionElementSupplier(connection,
				clid);
		return clres;
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
			InvalidObjectException te = new InvalidObjectException(
					"Failed to cast object to Enum. (" + obj.getClass() + ":" + obj + ")");
			te.initCause(e);
			throw te;
		}
		writeObjectEnum(out, en);
	}

	private void writeSerializedObject(Object obj, DataOutputUnsyncByteArrayOutputStream out) throws IOException {
		Class<? extends Object> objclass = obj.getClass();
		if (ReflectUtils.isEnumOrEnumAnonymous(objclass)) {
			writeObjectEnumImpl(out, objclass, ((Enum<?>) obj).name());
			return;
		}

		if (connection.getProtocolVersion() >= RMIConnection.PROTOCOL_VERSION_2) {
			//protocol includes the number of bytes written
			out.writeShort(OBJECT_SERIALIZED2);

			int sizeoffset = out.size();
			out.writeInt(0);
			try {
				try (ObjectOutputStream oos = new RMISerializeObjectOutputStream(out, connection)) {
					oos.writeObject(obj);
				}
			} finally {
				out.replaceInt(out.size() - sizeoffset - 4, sizeoffset);
			}
		} else {
			out.writeShort(OBJECT_SERIALIZED);
			try (ObjectOutputStream oos = new RMISerializeObjectOutputStream(out, connection)) {
				oos.writeObject(obj);
			}
		}
	}

	private Object readSerializedObject(DataInputUnsyncByteArrayInputStream in)
			throws IOException, ClassNotFoundException {
		//TODO handle serialization security-wise
		try (ObjectInputStream ois = new RMISerializeObjectInputStream(StreamUtils.closeProtectedInputStream(in),
				connection)) {
			return ois.readObject();
		}
	}

	private Object readSerialized2Object(DataInputUnsyncByteArrayInputStream in)
			throws IOException, ClassNotFoundException {
		//TODO handle serialization security-wise
		int bytecount = in.readInt();
		ByteArrayRegion inregion = in.toByteArrayRegion();
		DataInputUnsyncByteArrayInputStream limitreader = new DataInputUnsyncByteArrayInputStream(inregion.getArray(),
				inregion.getOffset(), bytecount);
		//skip the bytes after constructing the reader for the externalizable
		in.skipBytes(bytecount);

		Object result;
		try (ObjectInputStream ois = new RMISerializeObjectInputStream(limitreader, connection)) {
			result = ois.readObject();
		}
		if (connection.isObjectTransferByteChecks()) {
			int avail = limitreader.available();
			if (avail > 0) {
				throw new RMIObjectTransferFailureException("Serializable stream wasn't read fully by "
						+ ObjectUtils.classNameOf(result) + ". (Remaining " + avail + " bytes)");
			}
		}
		return result;
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
			DataOutputUnsyncByteArrayOutputStream out) throws IOException {
		if (obj instanceof Externalizable) {
			out.writeShort(OBJECT_EXTERNALIZABLE);
			Class<?> clazz = obj.getClass();
			writeClass(clazz, out);
			int sizeoffset = out.size();
			out.writeInt(0);
			try {
				((Externalizable) obj).writeExternal(new RMIObjectOutputImpl(variables, this, out));
			} finally {
				out.replaceInt(out.size() - sizeoffset - 4, sizeoffset);
			}
			return true;
		}
		return false;
	}

	private Object readExternalizableObject(RMIVariables variables, DataInputUnsyncByteArrayInputStream in)
			throws IOException, ClassNotFoundException {
		Class<?> clazz = readClass(in).get(connection);
		Externalizable instance;
		try {
			//cast down to externalizable, to avoid malicious client instantiating other kinds of classes
			instance = ReflectUtils.newInstance(clazz.asSubclass(Externalizable.class));
		} catch (NoSuchMethodException e) {
			InvalidClassException te = new InvalidClassException(clazz.getName(), "no valid constructor");
			te.initCause(e);
			throw te;
		} catch (ClassCastException e) {
			InvalidClassException te = new InvalidClassException(clazz.getName(), "class is not Externalizable");
			te.initCause(e);
			throw te;
		} catch (InvocationTargetException e) {
			InvalidClassException te = new InvalidClassException(clazz.getName(), "failed to invoke constructor");
			te.initCause(e.getCause());
			throw te;
		} catch (Exception e) {
			InvalidClassException te = new InvalidClassException(clazz.getName(), "failed to invoke constructor");
			te.initCause(e);
			throw te;
		}
		int bytecount = in.readInt();
		ByteArrayRegion inregion = in.toByteArrayRegion();
		DataInputUnsyncByteArrayInputStream limitreader = new DataInputUnsyncByteArrayInputStream(inregion.getArray(),
				inregion.getOffset(), bytecount);
		//skip the bytes after constructing the reader for the externalizable
		in.skipBytes(bytecount);

		instance.readExternal(getObjectInputForVariables(variables, limitreader));
		if (connection.isObjectTransferByteChecks()) {
			int avail = limitreader.available();
			if (avail > 0) {
				throw new RMIObjectTransferFailureException("Externalizable " + clazz.getName()
						+ " didn't read input fully. (Remaining " + avail + " bytes)");
			}
		}
		return instance;
	}

	private Object readObjectArray(RMIVariables vars, DataInputUnsyncByteArrayInputStream in)
			throws IOException, ClassNotFoundException {
		Class<?> component = this.readClass(in).get(connection);
		int len = in.readInt();
		Object array = Array.newInstance(component, len);
		for (int i = 0; i < len; i++) {
			Object obj = this.readObject(vars, in);
			Array.set(array, i, obj);
		}
		return array;
	}

	private Object readEnum(DataInputUnsyncByteArrayInputStream in) throws IOException, ClassNotFoundException {
		@SuppressWarnings("rawtypes")
		Class enumtype = this.readClass(in).get(connection);
		@SuppressWarnings({ "rawtypes", "unchecked" })
		Enum result = Enum.valueOf(enumtype, readString(in));
		return result;
	}

	Object readObject(RMIVariables vars, DataInputUnsyncByteArrayInputStream in)
			throws IOException, ClassNotFoundException {
		short type = in.readShort();
		RMIObjectReaderFunction<?> reader;
		try {
			reader = OBJECT_READERS[type];
		} catch (ArrayIndexOutOfBoundsException e) {
			// should happen very rarely, only when an unknown type is sent.
			// so its probably more efficient (?) to handle in a try-catch, than by using an if-else
			reader = OBJECT_READER_UNKNOWN;
		}
		return reader.readObject(this, vars, in);
	}

	private void writeClass(Class<?> clazz, DataOutputUnsyncByteArrayOutputStream out) {
		ClassReflectionElementSupplier cres = getClassReflectionElementSupplier(clazz);
		writeClass(cres, out);
	}

	private void writeClass(ClassReflectionElementSupplier cres, DataOutputUnsyncByteArrayOutputStream out) {
		RMICommCache<ClassReflectionElementSupplier> cache = commClasses;
		Integer index;
		index = cache.getWriteIndex(cres);
		if (index != null) {
			out.writeShort(CLASS_INDEX);
			out.writeInt(index);
			return;
		}
		out.writeShort(CLASS_DETAILS);
		writeClassData(cres, out);
	}

	private ClassReflectionElementSupplier getClassReflectionElementSupplier(Class<?> clazz) {
		ClassReflectionElementSupplier cres;
		if (RMICommState.DEFAULT_CLASSES.contains(clazz)) {
			cres = new BootstrapClassReflectionElementSupplier(clazz);
		} else {
			cres = new DynamicClassReflectionElementSupplier(new DynamicClassLoaderReflectionElementSupplier(connection,
					connection.getClassLoaderId(clazz.getClassLoader())), clazz.getName());
		}
		return cres;
	}

	private ClassReflectionElementSupplier readClass(DataInputUnsyncByteArrayInputStream in) throws IOException {
		RMICommCache<ClassReflectionElementSupplier> cache = commClasses;
		short cmd = in.readShort();
		switch (cmd) {
			case CLASS_DETAILS: {
				String classname = readString(in);
				ClassLoaderReflectionElementSupplier clsupplier = readClassLoader(in);
				ClassReflectionElementSupplier classsupplier = new DynamicClassReflectionElementSupplier(clsupplier,
						classname);
				Integer putidx = cache.putReadIfAbsent(classsupplier);
				if (putidx != null) {
					writeCommandCachedClass(classsupplier, putidx);
				}
				return classsupplier;
			}
			case CLASS_INDEX: {
				return readClassWithIndex(in, cache);
			}
			default: {
				throw new RMICallFailedException("illegal command: " + cmd);
			}
		}
	}

	private static ClassReflectionElementSupplier readClassWithIndex(DataInputUnsyncByteArrayInputStream in,
			RMICommCache<ClassReflectionElementSupplier> cache) throws EOFException {
		int cindex = in.readInt();
		ClassReflectionElementSupplier result = cache.getRead(cindex);
		if (result == null) {
			throw new RMIObjectTransferFailureException("Class not found for index: " + cindex);
		}
		return result;
	}

	private void writeClassData(ClassReflectionElementSupplier clazz, DataOutputUnsyncByteArrayOutputStream out) {
		writeString(clazz.getClassName(), out);
		writeClassLoader(clazz.getClassLoader(), out);
	}

	private ClassReflectionElementSupplier readClassData(DataInputUnsyncByteArrayInputStream in) throws IOException {
		String classname = readString(in);
		ClassLoaderReflectionElementSupplier clsupplier = readClassLoader(in);
		return new DynamicClassReflectionElementSupplier(clsupplier, classname);
	}

	private void writeClassLoader(ClassLoaderReflectionElementSupplier cl, DataOutputUnsyncByteArrayOutputStream out) {
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

	private ClassLoaderReflectionElementSupplier readClassLoader(DataInputUnsyncByteArrayInputStream in)
			throws IOException {
		short cmd = in.readShort();
		switch (cmd) {
			case CLASSLOADER_DETAILS: {
				String clid = readClassLoaderId(in);
				ClassLoaderReflectionElementSupplier clsupplier = new DynamicClassLoaderReflectionElementSupplier(
						connection, clid);
				Integer putidx = commClassLoaders.putReadIfAbsent(clsupplier);
				if (putidx != null) {
					writeCommandCachedClassLoader(clsupplier, putidx);
				}
				return clsupplier;
			}
			case CLASSLOADER_INDEX: {
				int cindex = in.readInt();
				ClassLoaderReflectionElementSupplier readcl = commClassLoaders.getRead(cindex);
				if (readcl == null) {
					throw new RMIObjectTransferFailureException("Class not found for index: " + cindex);
				}
				return readcl;
			}
			case CLASSLOADER_NULL: {
				return nullClassLoaderSupplier;
			}
			default: {
				throw new RMICallFailedException("illegal command: " + cmd);
			}
		}
	}

	private static void writeClassLoaderData(ClassLoaderReflectionElementSupplier cl,
			DataOutputUnsyncByteArrayOutputStream out) {
		String clid = cl.getClassLoaderId();
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

	private static Optional<ClassLoader> getClassLoaderByIdOptional(RMIConnection connection, String clid) {
		return connection.getClassLoaderByIdOptional(clid);
	}

	private static class ClassSetPartiallyReadException extends Exception {
		private static final long serialVersionUID = 1L;

		//suppress warning:
		//   non-transient instance field of a serializable class declared with a non-serializable type
		@SuppressWarnings("serial")
		private Set<Class<?>> result;

		public ClassSetPartiallyReadException(Set<Class<?>> result) {
			super(null, null, true, false);
			this.result = result;
		}

		public Set<Class<?>> getReadClasses() {
			return result;
		}
	}

	private Set<Class<?>> readClassesSet(DataInputUnsyncByteArrayInputStream in, RMIVariables variables)
			throws IOException, ClassSetPartiallyReadException {
		short count = in.readShort();
		if (count == 0) {
			return Collections.emptySet();
		}
		Set<ClassReflectionElementSupplier> classsuppliers = new LinkedHashSet<>();
		while (count-- > 0) {
			classsuppliers.add(readClass(in));
		}
		ClassSetPartiallyReadException partialexc = null;
		LinkedHashSet<Class<?>> result = new LinkedHashSet<>();
		for (ClassReflectionElementSupplier cres : classsuppliers) {
			try {
				Class<?> c = cres.get(connection);
				result.add(c);
			} catch (ClassNotFoundException e) {
				if (partialexc == null) {
					partialexc = new ClassSetPartiallyReadException(result);
				} else {
					partialexc.addSuppressed(e);
				}
			}
		}
		reducePublicNonAssignableInterfaces(result, variables.getMarkerClassLookup(),
				connection.getCollectingStatistics());
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
		RMICommCache<ConstructorReflectionElementSupplier> cache = commConstructors;
		ConstructorReflectionElementSupplier constructorres = getConstructorReflectionElementSupplier(constructor);
		Integer index = cache.getWriteIndex(constructorres);
		if (index != null) {
			out.writeShort(CONSTRUCTOR_INDEX);
			out.writeInt(index);
			return;
		}
		out.writeShort(CONSTRUCTOR_DETAILS);
		writeConstructorData(constructorres, out);
	}

	private Constructor<?> readConstructor(DataInputUnsyncByteArrayInputStream in)
			throws IOException, ClassNotFoundException {
		RMICommCache<ConstructorReflectionElementSupplier> cache = commConstructors;
		short cmd = in.readShort();
		switch (cmd) {
			case CONSTRUCTOR_DETAILS: {
				ConstructorReflectionElementSupplier result = readConstructorData(in);
				Integer putidx = cache.putReadIfAbsent(result);
				if (putidx != null) {
					writeCommandCachedConstructor(result, putidx);
				}
				return result.get(connection);
			}
			case CONSTRUCTOR_INDEX: {
				int cindex = in.readInt();
				ConstructorReflectionElementSupplier result = cache.getRead(cindex);
				if (result == null) {
					throw new RMIObjectTransferFailureException("Constructor not found with index: " + cindex);
				}
				return result.get(connection);
			}
			default: {
				throw new RMICallFailedException("illegal command: " + cmd);
			}
		}
	}

	private void writeConstructorData(ConstructorReflectionElementSupplier constructor,
			DataOutputUnsyncByteArrayOutputStream out) {
		writeClass(constructor.getDeclaringClass(), out);
		writeClassNames(constructor.getParameterTypeNames(), out);
	}

	private ConstructorReflectionElementSupplier readConstructorData(DataInputUnsyncByteArrayInputStream in)
			throws IOException {
		ClassReflectionElementSupplier declaringclass = readClass(in);
		String[] parameterTypes = readClassNames(in);

		return new ImplConstructorReflectionElementSupplier(declaringclass, parameterTypes);
	}

	private void writeField(Field f, DataOutputUnsyncByteArrayOutputStream out) {
		RMICommCache<FieldReflectionElementSupplier> cache = commFields;
		FieldReflectionElementSupplier fres = getFieldReflectionElementSupplier(f);
		Integer index = cache.getWriteIndex(fres);
		if (index != null) {
			out.writeShort(FIELD_INDEX);
			out.writeInt(index);
			return;
		}
		out.writeShort(FIELD_DETAILS);
		writeFieldData(fres, out);
	}

	private Field readField(DataInputUnsyncByteArrayInputStream in, Object relativeobject)
			throws IOException, ClassNotFoundException {
		RMICommCache<FieldReflectionElementSupplier> cache = commFields;
		short cmd = in.readShort();
		switch (cmd) {
			case FIELD_DETAILS: {
				final FieldReflectionElementSupplier fielddata = readFieldData(in);
				Integer putidx = commFields.putReadIfAbsent(fielddata);
				if (putidx != null) {
					writeCommandCachedField(fielddata, putidx);
				}
				return fielddata.get(connection, relativeobject);
			}
			case FIELD_INDEX: {
				int cindex = in.readInt();
				FieldReflectionElementSupplier result = cache.getRead(cindex);
				if (result == null) {
					throw new RMIObjectTransferFailureException("Field not found with index: " + cindex);
				}
				return result.get(connection, relativeobject);
			}
			default: {
				throw new RMICallFailedException("illegal command: " + cmd);
			}
		}
	}

	private void writeFieldData(FieldReflectionElementSupplier f, DataOutputUnsyncByteArrayOutputStream out) {
		writeClass(f.getDeclaringClass(), out);
		writeString(f.getFieldName(), out);
	}

	private FieldReflectionElementSupplier readFieldData(DataInputUnsyncByteArrayInputStream in) throws IOException {
		ClassReflectionElementSupplier declaringclass = readClass(in);
		String name = readString(in);
		DeclaredFieldReflectionElementSupplier result = new DeclaredFieldReflectionElementSupplier(declaringclass,
				name);
		return result;
	}

	private FieldReflectionElementSupplier getFieldReflectionElementSupplier(Field f) {
		return new DeclaredFieldReflectionElementSupplier(getClassReflectionElementSupplier(f.getDeclaringClass()),
				f.getName());
	}

	private ConstructorReflectionElementSupplier getConstructorReflectionElementSupplier(Constructor<?> constructor) {
		Class<?> declclass = constructor.getDeclaringClass();
		ClassReflectionElementSupplier declaringclassres = getClassReflectionElementSupplier(declclass);
		Class<?>[] paramtypes = constructor.getParameterTypes();
		String[] stringtypenames = new String[paramtypes.length];
		for (int i = 0; i < stringtypenames.length; i++) {
			stringtypenames[i] = paramtypes[i].getName();
		}
		return new ImplConstructorReflectionElementSupplier(declaringclassres, stringtypenames);
	}

	private MethodReflectionElementSupplier getMethodReflectionElementSupplier(Method method) {
		Class<?> declclass = method.getDeclaringClass();
		ClassReflectionElementSupplier declaringclassres = getClassReflectionElementSupplier(declclass);
		String methodname = method.getName();
		Class<?>[] paramtypes = method.getParameterTypes();
		String[] stringtypenames = new String[paramtypes.length];
		for (int i = 0; i < stringtypenames.length; i++) {
			stringtypenames[i] = paramtypes[i].getName();
		}
		return new ImplMethodReflectionElementSupplier(declaringclassres, methodname, stringtypenames);

	}

	private void writeMethod(Method method, DataOutputUnsyncByteArrayOutputStream out) {
		RMICommCache<MethodReflectionElementSupplier> cache = commMethods;
		MethodReflectionElementSupplier methodres = getMethodReflectionElementSupplier(method);
		Integer index = cache.getWriteIndex(methodres);
		if (index != null) {
			out.writeShort(METHOD_INDEX);
			out.writeInt(index);
			return;
		}
		out.writeShort(METHOD_DETAILS);
		writeMethodData(methodres, out);
	}

	private Method readMethod(DataInputUnsyncByteArrayInputStream in, Object relativeobject)
			throws IOException, ClassNotFoundException {
		RMICommCache<MethodReflectionElementSupplier> cache = commMethods;
		short cmd = in.readShort();
		switch (cmd) {
			case METHOD_DETAILS: {
				final MethodReflectionElementSupplier methoddata = readMethodData(in);
				Integer putidx = commMethods.putReadIfAbsent(methoddata);
				if (putidx != null) {
					writeCommandCachedMethod(methoddata, putidx);
				}
				return methoddata.get(connection, relativeobject);
			}
			case METHOD_INDEX: {
				int cindex = in.readInt();
				MethodReflectionElementSupplier result = cache.getRead(cindex);
				if (result == null) {
					throw new RMIObjectTransferFailureException("Method not found with index: " + cindex);
				}
				return result.get(connection, relativeobject);
			}
			default: {
				throw new RMICallFailedException("illegal command: " + cmd);
			}
		}
	}

	private void writeMethodData(MethodReflectionElementSupplier method, DataOutputUnsyncByteArrayOutputStream out) {
		writeClass(method.getDeclaringClass(), out);
		writeString(method.getMethodName(), out);
		writeClassNames(method.getParameterTypeNames(), out);
	}

	private static void writeClassNames(String[] clazz, DataOutputUnsyncByteArrayOutputStream out) {
		out.writeInt(clazz.length);
		for (int i = 0; i < clazz.length; i++) {
			writeString(clazz[i], out);
		}
	}

	private static String[] readClassNames(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int len = in.readInt();
		String[] result = new String[len];
		for (int i = 0; i < len; i++) {
			String cname = readString(in);
			result[i] = cname;
		}
		return result;
	}

	private MethodReflectionElementSupplier readMethodData(DataInputUnsyncByteArrayInputStream in) throws IOException {
		ClassReflectionElementSupplier declaringclass = readClass(in);
		String name = readString(in);
		String[] parameterTypes = readClassNames(in);

		ImplMethodReflectionElementSupplier result = new ImplMethodReflectionElementSupplier(declaringclass, name,
				parameterTypes);
		return result;
	}

	private void writeMethodParameters(RMIVariables variables, ExecutableTransferProperties<?> execproperties,
			Object[] arguments, DataOutputUnsyncByteArrayOutputStream out) {
		if (ObjectUtils.isNullOrEmpty(arguments)) {
			out.writeShort(0);
			return;
		}
		out.writeShort(arguments.length);
		Executable exec = execproperties.getExecutable();
		Class<?>[] paramtypes = exec.getParameterTypes();
		for (int i = 0; i < arguments.length; i++) {
			try {
				Object argument = unwrapWrapperForTransfer(arguments[i], variables);
				writeObjectUsingWriteHandler(execproperties.getParameterWriter(i), variables, argument, out,
						paramtypes[i]);
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
					| ServiceConfigurationError e) {
				throw new RMIObjectTransferFailureException("Failed to write method call argument[" + i + "].", e);
			}
		}
	}

	private Object readWrappedObject(RMIVariables variables, DataInputUnsyncByteArrayInputStream in)
			throws IOException, ClassNotFoundException {
		Class<?> wrapperclass = readClass(in).get(connection);
		RMIWrapper wrapper;
		try {
			//cast down the class to ensure that the wrapper is only instantiated if it actually implements the RMIWrapper
			//this is to avoid malicious client to provide a non-rmi wrapper wrapper class during transmit
			wrapper = ReflectUtils.newInstance(wrapperclass.asSubclass(RMIWrapper.class));
		} catch (InvocationTargetException e) {
			InvalidClassException te = new InvalidClassException(wrapperclass.getName(),
					"Failed to instantiate RMIWrapper.");
			te.initCause(e.getCause());
			throw te;
		} catch (Exception e) {
			InvalidClassException te = new InvalidClassException(wrapperclass.getName(),
					"Failed to instantiate RMIWrapper.");
			te.initCause(e);
			throw te;
		}
		wrapper.readWrapped(getObjectInputForVariables(variables, in));
		return wrapper.resolveWrapped();
	}

	private Object readWrapped2Object(RMIVariables variables, DataInputUnsyncByteArrayInputStream in)
			throws IOException, ClassNotFoundException {
		Class<?> wrapperclass = readClass(in).get(connection);
		RMIWrapper wrapper;
		try {
			//cast down the class to ensure that the wrapper is only instantiated if it actually implements the RMIWrapper
			//this is to avoid malicious client to provide a non-rmi wrapper wrapper class during transmit
			wrapper = ReflectUtils.newInstance(wrapperclass.asSubclass(RMIWrapper.class));
		} catch (InvocationTargetException e) {
			InvalidClassException te = new InvalidClassException(wrapperclass.getName(),
					"Failed to instantiate RMIWrapper.");
			te.initCause(e.getCause());
			throw te;
		} catch (Exception e) {
			InvalidClassException te = new InvalidClassException(wrapperclass.getName(),
					"Failed to instantiate RMIWrapper.");
			te.initCause(e);
			throw te;
		}

		int bytecount = in.readInt();
		ByteArrayRegion inregion = in.toByteArrayRegion();
		DataInputUnsyncByteArrayInputStream limitreader = new DataInputUnsyncByteArrayInputStream(inregion.getArray(),
				inregion.getOffset(), bytecount);
		//skip the bytes after constructing the reader for the externalizable
		in.skipBytes(bytecount);

		wrapper.readWrapped(getObjectInputForVariables(variables, limitreader));
		if (connection.isObjectTransferByteChecks()) {
			int avail = limitreader.available();
			if (avail > 0) {
				throw new RMIObjectTransferFailureException("RMIWrapper " + wrapperclass.getName()
						+ " didn't read input fully. (Remaining " + avail + " bytes)");
			}
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
			InvalidClassException te = new InvalidClassException(wrapperclass.getName(),
					"Failed to instantiate RMIWrapper.");
			te.initCause(e.getCause());
			throw te;
		} catch (Exception e) {
			InvalidClassException te = new InvalidClassException(wrapperclass.getName(),
					"Failed to instantiate RMIWrapper.");
			te.initCause(e);
			throw te;
		}
		if (connection.getProtocolVersion() >= RMIConnection.PROTOCOL_VERSION_2) {
			//protocol includes the number of bytes written
			out.writeShort(OBJECT_WRAPPER2);
			writeClass(wrapperclass, out);

			int sizeoffset = out.size();
			out.writeInt(0);
			try {
				wrapper.writeWrapped(new RMIObjectOutputImpl(variables, this, out, obj));
			} finally {
				out.replaceInt(out.size() - sizeoffset - 4, sizeoffset);
			}
		} else {
			out.writeShort(OBJECT_WRAPPER);
			writeClass(wrapperclass, out);
			wrapper.writeWrapped(new RMIObjectOutputImpl(variables, this, out, obj));
		}
	}

	@SuppressWarnings("unchecked")
	private static Constructor<? extends RMIWrapper> getRMIWrapperConstructor(Class<?> paramtype,
			Class<? extends RMIWrapper> wrapperclass) throws InvalidClassException {
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
		throw new InvalidClassException("No appropriate RMIWrapper constructor found for parameter type: " + paramtype
				+ " in " + wrapperclass.getName());
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
		return variables.requireObjectWithLocalId(idx);
	}

	private Object[] readMethodParameters(RMIVariables variables, DataInputUnsyncByteArrayInputStream in)
			throws IOException, ClassNotFoundException {
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
		validateVariables(variables);
		return variables;
	}

	private static void validateVariables(RMIVariables variables) {
		if (variables == null) {
			throw new RMIResourceUnavailableException("Variables not found.");
		}
	}

	protected RMIVariables readVariablesImpl(DataInputUnsyncByteArrayInputStream in) throws EOFException {
		int variablesid = in.readInt();
		return connection.getVariablesByLocalId(variablesid);
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
		public void executeRedispatchAction() {
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
		public void executeRedispatchAction() {
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
		public void executeRedispatchAction() {
			instantiateAndWriteUnknownNewInstanceCall(requestId, variables, cl, className, argClassNames, args);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (className != null ? "classname=" + className : "") + "]";
		}
	}

	private static final class ContextVariableNotFoundResponse implements RedispatchResponse {
		private final String varname;

		private ContextVariableNotFoundResponse(String varname) {
			this.varname = varname;
		}

		@Override
		public void executeRedispatchAction() {
			throw new RMIContextVariableNotFoundException(varname);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder(getClass().getSimpleName());
			builder.append("[");
			builder.append(varname);
			builder.append("]");
			return builder.toString();
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
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
				| ServiceConfigurationError e) {
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
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
				| ServiceConfigurationError e) {
			requestHandler.addResponse(reqid, new MethodCallFailedResponse(interrupted, interruptreqcount,
					new RMICallFailedException("Failed to read new instance result.", e)));
		}
	}

	private void handleCommandGetContextVariableResult(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int variablesid = in.readInt();
		int reqid = in.readInt();
		try {
			RMIVariables vars = connection.getVariablesByLocalId(variablesid);
			validateVariables(vars);
			Object obj = readObject(vars, in);

			requestHandler.addResponse(reqid, new GetContextVariableResponse(obj));
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
				| ServiceConfigurationError e) {
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
		int variablesid = in.readInt();
		int reqid = in.readInt();
		RMIVariables vars = connection.getVariablesByLocalId(variablesid);
		validateVariables(vars);
		String varid = in.readUTF();

		writeGetContextVarResponse(reqid, vars, connection.getLocalContextVariable(varid));
	}

	private void handleCommandPing(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();

		writeCommandPong(reqid);
	}

	private void handleCommandCachedClass(DataInputUnsyncByteArrayInputStream in) throws IOException {
		ClassReflectionElementSupplier classdata = readClassData(in);
		int idx = in.readInt();
		commClasses.putWrite(classdata, idx);
	}

	private static Class<?>[] loadParameterTypeClasses(ClassLoader declcl, String[] parametertypenames, Object msgsrc) {
		Class<?>[] paramtypes = new Class<?>[parametertypenames.length];
		for (int i = 0; i < paramtypes.length; i++) {
			try {
				Class<?> foundc = ReflectUtils.primitiveNameToPrimitiveClass(parametertypenames[i]);
				if (foundc != null) {
					paramtypes[i] = foundc;
				} else {
					paramtypes[i] = Class.forName(parametertypenames[i], false, declcl);
				}
			} catch (ClassNotFoundException e) {
				throw new RMIObjectTransferFailureException(
						"Failed to load type: " + parametertypenames[i] + " for " + msgsrc, e);
			}
		}
		return paramtypes;
	}

	private void handleCommandCachedClassLoader(DataInputUnsyncByteArrayInputStream in) throws IOException {
		String clid = readClassLoaderId(in);
		DynamicClassLoaderReflectionElementSupplier clsupplier = new DynamicClassLoaderReflectionElementSupplier(
				connection, clid);
		int idx = in.readInt();
		commClassLoaders.putWrite(clsupplier, idx);
	}

	private void handleCommandCachedMethod(DataInputUnsyncByteArrayInputStream in) throws IOException {
		MethodReflectionElementSupplier method = readMethodData(in);
		int idx = in.readInt();
		commMethods.putWrite(method, idx);
	}

	private void handleCommandInterruptRequest(DataInputUnsyncByteArrayInputStream in) throws IOException {
		int reqid = in.readInt();
		connection.interruptRequestThread(reqid);
	}

	private void handleCommandCachedField(DataInputUnsyncByteArrayInputStream in) throws IOException {
		FieldReflectionElementSupplier field = readFieldData(in);
		int idx = in.readInt();
		commFields.putWrite(field, idx);
	}

	private void handleCommandCachedConstructor(DataInputUnsyncByteArrayInputStream in) throws IOException {
		ConstructorReflectionElementSupplier constructor = readConstructorData(in);
		int idx = in.readInt();
		commConstructors.putWrite(constructor, idx);
	}

	private void handleCommandNewVariables(DataInputUnsyncByteArrayInputStream in) throws IOException {
		//read failures are protocol errors
		int reqid = in.readInt();
		int remoteid;
		String name;
		try {
			remoteid = in.readInt();
			name = in.readUTF();
			if (name.isEmpty()) {
				name = null;
			}
		} catch (IOException e) {
			//protocol error, but still attempt to write a response
			try {
				writeCommandNewVariablesResult(reqid, RMIVariables.NO_OBJECT_ID);
			} catch (Throwable e2) {
				e.addSuppressed(e2);
			}
			throw e;
		}
		try {
			RMIVariables vars = connection.newRemoteVariables(name, remoteid, this);
			int localid = vars.getLocalIdentifier();

			writeCommandNewVariablesResult(reqid, localid);
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
				| ServiceConfigurationError e) {
			//XXX do something with these exceptions, they're currently ignored
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
				interfaces = readClassesSet(in, variables);
			} catch (ClassSetPartiallyReadException e) {
				interfaces = e.getReadClasses();
			}

			interrupted = isCompressedInterruptStatusInvokerThreadInterrupted(compressedinterruptstatus);
			interruptreqcount = getCompressedInterruptStatusDeliveredRequestCount(compressedinterruptstatus);

			requestHandler.addResponse(reqid,
					new UnknownNewInstanceResponse(interrupted, interruptreqcount, remoteindex, interfaces));
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
				| ServiceConfigurationError e) {
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
			requestHandler.addResponse(reqid, new NewInstanceFailedResponse(interrupted, interruptreqcount, e));
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
				| ServiceConfigurationError e) {
			requestHandler.addResponse(reqid, new NewInstanceFailedResponse(interrupted, interruptreqcount,
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
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
				| ServiceConfigurationError e) {
			requestHandler.addResponse(reqid, new MethodCallFailedResponse(interrupted, interruptreqcount,
					new RMICallFailedException("Failed to read remote exception.", e)));
		}
	}

	private void writeDirectRequestForbidden(short command, int reqid) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_DIRECT_REQUEST_FORBIDDEN);
			out.writeInt(reqid);
			out.writeShort(command);
		}
	}

	private boolean handleCommandNewInstance(RunInputRunnable inputrunnable, DataInputUnsyncByteArrayInputStream in,
			ReferencesReleasedAction gcaction) throws IOException {
		//throws IOException if fails, protocol error
		int reqid = in.readInt();
		if (!connection.isAllowDirectRequests()) {
			writeDirectRequestForbidden(COMMAND_NEWINSTANCERESULT_FAIL, reqid);
			return false;
		}
		RMIVariables variables = null;
		boolean ongoingrequestadded = false;
		try {
			Constructor<?> constructor;
			Object[] args;

			gcaction.increasePendingRequestCount();
			try {
				boolean streamtaskoffered = false;
				try {
					variables = readVariablesValidate(in);
					variables.addOngoingRequest();
					ongoingrequestadded = true;

					inputrunnable.offerSelfStreamTask();
					streamtaskoffered = true;

					constructor = readConstructor(in);
					args = readMethodParameters(variables, in);
				} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
						| ServiceConfigurationError e) {
					writeCommandExceptionResult(COMMAND_NEWINSTANCERESULT_FAIL, reqid, e, false, 0);
					return streamtaskoffered;
				}
			} finally {
				gcaction.decreasePendingRequestCount();
			}
			//release the action for garbage collection
			gcaction = null;
			instantiateAndWriteNewInstanceCall(reqid, variables, constructor, args);
			return true;
		} finally {
			if (ongoingrequestadded) {
				variables.removeOngoingRequest();
			}
		}
	}

	private void handleCommandNewInstanceRedispatch(DataInputUnsyncByteArrayInputStream in,
			ReferencesReleasedAction gcaction) throws IOException {
		try {
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

				boolean responseadded = requestHandler.addResponse(dispatchid,
						new NewInstanceRedispatchResponse(variables, reqid, constructor, args));
				checkRedispatchResponseAdded(responseadded);
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
					| ServiceConfigurationError e) {
				writeCommandExceptionResult(COMMAND_NEWINSTANCERESULT_FAIL, reqid, e, false, 0);
			}
		} finally {
			gcaction.decreasePendingRequestCount();
		}
	}

	private boolean handleCommandNewInstanceUnknownClass(RunInputRunnable inputrunnable,
			DataInputUnsyncByteArrayInputStream in, ReferencesReleasedAction gcaction) throws IOException {
		//throws IOException if fails, protocol error
		int reqid = in.readInt();
		if (!connection.isAllowDirectRequests()) {
			writeDirectRequestForbidden(COMMAND_NEWINSTANCERESULT_FAIL, reqid);
			return false;
		}
		RMIVariables variables = null;
		boolean ongoingrequestadded = false;
		try {
			ClassLoader cl;
			String classname;
			String[] argclassnames;
			Object[] args;

			gcaction.increasePendingRequestCount();
			try {
				boolean streamtaskoffered = false;
				try {
					variables = readVariablesValidate(in);
					variables.addOngoingRequest();
					ongoingrequestadded = true;

					inputrunnable.offerSelfStreamTask();
					streamtaskoffered = true;

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
				} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
						| ServiceConfigurationError e) {
					writeCommandExceptionResult(COMMAND_NEWINSTANCERESULT_FAIL, reqid, e, false, 0);
					return streamtaskoffered;
				}
			} finally {
				gcaction.decreasePendingRequestCount();
			}
			//release the action for garbage collection
			gcaction = null;
			instantiateAndWriteUnknownNewInstanceCall(reqid, variables, cl, classname, argclassnames, args);
			return true;
		} finally {
			if (ongoingrequestadded) {
				variables.removeOngoingRequest();
			}
		}
	}

	private void handleCommandNewInstanceUnknownClassRedispatch(DataInputUnsyncByteArrayInputStream in,
			ReferencesReleasedAction gcaction) throws IOException {
		try {
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

				boolean responseadded = requestHandler.addResponse(dispatchid,
						new UnknownClassNewInstanceRedispatchResponse(classname, args, reqid, variables, cl,
								argclassnames));
				checkRedispatchResponseAdded(responseadded);
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
					| ServiceConfigurationError e) {
				writeCommandExceptionResult(COMMAND_UNKNOWN_NEWINSTANCE_RESULT, reqid, e, false, 0);
			}
		} finally {
			gcaction.decreasePendingRequestCount();
		}
	}

	private boolean handleCommandMethodCallAsync(RunInputRunnable inputrunnable, DataInputUnsyncByteArrayInputStream in,
			ReferencesReleasedAction gcaction) throws EOFException {
		RMIVariables variables;

		//throws IOException if fails, protocol error
		variables = readVariablesImpl(in);
		if (variables == null) {
			//the variables was closed on this side, or doesnt exist
			//can't do much about it in case of async call
			return false;
		}
		if (!variables.tryOngoingRequest()) {
			//failed to add an ongoing request
			//variables probably got closed meanwhile
			return false;
		}
		//throws IOException if fails, protocol error
		int localid = in.readInt();
		try {
			Object invokeobject;
			Object[] args;
			Method method;
			gcaction.increasePendingRequestCount();
			try {
				//we can offer the task at this point
				inputrunnable.offerSelfStreamTask();
				invokeobject = readMethodInvokeObject(variables, localid);

				if (invokeobject == null && !connection.isAllowDirectRequests()) {
					//forbidden to call static methods
					//XXX should notify the user somehow
					return true;
				}

				try {
					method = readMethod(in, invokeobject);
					//TODO check disallowing direct requests if the called method has a non-interface declaring class (or java.lang.Object)
					if (((method.getModifiers() & Modifier.STATIC) == Modifier.STATIC)
							&& !connection.isAllowDirectRequests()) {
						//XXX should notify the user somehow
						return true;
					}
					args = readMethodParameters(variables, in);
				} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
						| ServiceConfigurationError e) {
					//XXX should notify the user somehow
					return true;
				}
			} finally {
				gcaction.decreasePendingRequestCount();
			}
			//release the action for garbage collection
			gcaction = null;
			try {
				ReflectUtils.invokeMethod(invokeobject, method, args);
				return true;
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
					| ServiceConfigurationError e) {
				//XXX should notify the user somehow
				return true;
			}
		} finally {
			variables.removeOngoingRequest();
		}
	}

	private boolean handleCommandMethodCallAsyncWithResponse(RunInputRunnable inputrunnable,
			DataInputUnsyncByteArrayInputStream in, ReferencesReleasedAction gcaction) throws EOFException {
		boolean writtenresponse = false;

		//throws IOException if fails, protocol error
		//the remote id is only used for sending back the response
		int variablesremoteid = in.readInt();
		try {
			//throws IOException if fails, protocol error
			RMIVariables variables = readVariablesImpl(in);
			if (variables == null) {
				//the variables was closed on this side, or doesnt exist
				//can't do much about it in case of async call
				return false;
			}
			if (!variables.tryOngoingRequest()) {
				//failed to add an ongoing request
				//variables probably got closed meanwhile
				return false;
			}
			try {
				Object invokeobject;
				Object[] args;
				Method method;

				gcaction.increasePendingRequestCount();
				try {
					//we can offer the task at this point, after starting the request, and increasing the GC pending count
					inputrunnable.offerSelfStreamTask();

					//throws IOException if fails, protocol error
					int localid = in.readInt();
					invokeobject = readMethodInvokeObject(variables, localid);

					if (invokeobject == null && !connection.isAllowDirectRequests()) {
						//forbidden to call static methods
						//XXX should notify the user somehow
						return true;
					}

					try {
						method = readMethod(in, invokeobject);
						//TODO check disallowing direct requests if the called method has a non-interface declaring class (or java.lang.Object)
						if (((method.getModifiers() & Modifier.STATIC) == Modifier.STATIC)
								&& !connection.isAllowDirectRequests()) {
							//XXX should notify the user somehow
							return true;
						}
						args = readMethodParameters(variables, in);
					} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
							| ServiceConfigurationError e) {
						//XXX should notify the user somehow
						return true;
					}
				} finally {
					gcaction.decreasePendingRequestCount();
				}
				//release the action for garbage collection
				gcaction = null;
				try {
					ReflectUtils.invokeMethod(invokeobject, method, args);
					return true;
				} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
						| ServiceConfigurationError e) {
					//XXX should notify the user somehow
					return true;
				}
			} finally {
				//the response should be written before the request is finished
				//set the written response flag, so we don't write it in the finally block
				writtenresponse = true;
				try {
					writeCommandAsyncResponse(variablesremoteid);
				} finally {
					//this should be always called even if the response writing fails for some reason
					variables.removeOngoingRequest();
				}
			}
		} finally {
			if (!writtenresponse) {
				//some exception or something happened, and we haven't written the response above
				//write it now, as this is necessary 
				writeCommandAsyncResponse(variablesremoteid);
			}
		}
	}

	private boolean handleCommandMethodCall(RunInputRunnable inputrunnable, DataInputUnsyncByteArrayInputStream in,
			ReferencesReleasedAction gcaction) throws IOException {
		//throws IOException if fails, protocol error
		int reqid = in.readInt();

		RMIVariables variables = null;
		boolean ongoingrequestadded = false;
		try {
			Object invokeobject;
			MethodTransferProperties transfermethod;
			Object[] args;

			gcaction.increasePendingRequestCount();
			try {
				boolean streamtaskoffered = false;
				try {
					variables = readVariablesValidate(in);
					variables.addOngoingRequest();
					ongoingrequestadded = true;

					inputrunnable.offerSelfStreamTask();
					streamtaskoffered = true;

					int localid = in.readInt();
					invokeobject = readMethodInvokeObject(variables, localid);
					if (invokeobject == null && !connection.isAllowDirectRequests()) {
						//forbidden to call static methods
						writeDirectRequestForbidden(COMMAND_METHODRESULT_FAIL, reqid);
						return true;
					}
					Method method = readMethod(in, invokeobject);
					//TODO check disallowing direct requests if the called method has a non-interface declaring class (or java.lang.Object)
					if (((method.getModifiers() & Modifier.STATIC) == Modifier.STATIC)
							&& !connection.isAllowDirectRequests()) {
						writeDirectRequestForbidden(COMMAND_METHODRESULT_FAIL, reqid);
						return true;
					}

					transfermethod = variables.getPropertiesCheckClosed().getExecutableProperties(method);
					args = readMethodParameters(variables, in);
				} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
						| ServiceConfigurationError e) {
					writeCommandExceptionResult(COMMAND_METHODRESULT_FAIL, reqid, e, false, 0);
					return streamtaskoffered;
				}
			} finally {
				gcaction.decreasePendingRequestCount();
			}
			//release the action for garbage collection
			gcaction = null;
			invokeAndWriteMethodCall(reqid, variables, invokeobject, transfermethod, args);
			return true;
		} finally {
			if (ongoingrequestadded) {
				variables.removeOngoingRequest();
			}
		}
	}

	private boolean handleCommandContextVariableMethodCall(RunInputRunnable inputrunnable,
			DataInputUnsyncByteArrayInputStream in, ReferencesReleasedAction gcaction) throws IOException {
		//throws IOException if fails, protocol error
		int reqid = in.readInt();

		RMIVariables variables = null;
		boolean ongoingrequestadded = false;
		try {
			Object invokeobject;
			MethodTransferProperties transfermethod;
			Object[] args;

			gcaction.increasePendingRequestCount();
			try {
				boolean streamtaskoffered = false;
				try {
					variables = readVariablesValidate(in);
					variables.addOngoingRequest();
					ongoingrequestadded = true;

					inputrunnable.offerSelfStreamTask();
					streamtaskoffered = true;

					String varid = in.readUTF();
					invokeobject = connection.getLocalContextVariable(varid);

					if (invokeobject == null) {
						writeCommandContextVariableMethodCallVariableNotFound(reqid, varid);
						return true;
					}
					Method method = readMethod(in, invokeobject);
					//TODO check disallowing direct requests if the called method has a non-interface declaring class (or java.lang.Object)
					if (((method.getModifiers() & Modifier.STATIC) == Modifier.STATIC)
							&& !connection.isAllowDirectRequests()) {
						writeDirectRequestForbidden(COMMAND_METHODRESULT_FAIL, reqid);
						return true;
					}

					transfermethod = variables.getPropertiesCheckClosed().getExecutableProperties(method);
					args = readMethodParameters(variables, in);
				} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
						| ServiceConfigurationError e) {
					writeCommandExceptionResult(COMMAND_METHODRESULT_FAIL, reqid, e, false, 0);
					return streamtaskoffered;
				}
			} finally {
				gcaction.decreasePendingRequestCount();
			}
			//release the action for garbage collection
			gcaction = null;
			invokeAndWriteMethodCall(reqid, variables, invokeobject, transfermethod, args);
			return true;
		} finally {
			if (ongoingrequestadded) {
				variables.removeOngoingRequest();
			}
		}
	}

	private void handleCommandMethodCallRedispatch(DataInputUnsyncByteArrayInputStream in,
			ReferencesReleasedAction gcaction) throws IOException {
		try {
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
				if (((method.getModifiers() & Modifier.STATIC) == Modifier.STATIC)
						&& !connection.isAllowDirectRequests()) {
					writeDirectRequestForbidden(COMMAND_METHODRESULT_FAIL, reqid);
					return;
				}

				MethodTransferProperties transfermethod = variables.getPropertiesCheckClosed()
						.getExecutableProperties(method);
				Object[] args = readMethodParameters(variables, in);

				boolean responseadded = requestHandler.addResponse(dispatchid,
						new MethodCallRedispatchResponse(reqid, invokeobject, transfermethod, variables, args));
				checkRedispatchResponseAdded(responseadded);
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
					| ServiceConfigurationError e) {
				writeCommandExceptionResult(COMMAND_METHODRESULT_FAIL, reqid, e, false, 0);
			}
		} finally {
			gcaction.decreasePendingRequestCount();
		}
	}

	private void handleCommandContextVariableMethodCallRedispatch(DataInputUnsyncByteArrayInputStream in,
			ReferencesReleasedAction gcaction) throws IOException {
		try {
			int reqid = in.readInt();
			try {
				RMIVariables variables = readVariablesValidate(in);
				int dispatchid = in.readInt();
				String varid = in.readUTF();
				Object invokeobject = connection.getLocalContextVariable(varid);

				if (invokeobject == null) {
					writeCommandContextVariableMethodCallVariableNotFound(reqid, varid);
					return;
				}
				Method method = readMethod(in, invokeobject);
				//TODO check disallowing direct requests if the called method has a non-interface declaring class (or java.lang.Object)
				if (((method.getModifiers() & Modifier.STATIC) == Modifier.STATIC)
						&& !connection.isAllowDirectRequests()) {
					writeDirectRequestForbidden(COMMAND_METHODRESULT_FAIL, reqid);
					return;
				}

				MethodTransferProperties transfermethod = variables.getPropertiesCheckClosed()
						.getExecutableProperties(method);
				Object[] args = readMethodParameters(variables, in);

				boolean responseadded = requestHandler.addResponse(dispatchid,
						new MethodCallRedispatchResponse(reqid, invokeobject, transfermethod, variables, args));
				checkRedispatchResponseAdded(responseadded);
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
					| ServiceConfigurationError e) {
				writeCommandExceptionResult(COMMAND_METHODRESULT_FAIL, reqid, e, false, 0);
			}
		} finally {
			gcaction.decreasePendingRequestCount();
		}
	}

	private void handleCommandContextVariableMethodCallVariableNotFound(DataInputUnsyncByteArrayInputStream in)
			throws IOException {
		int reqid = in.readInt();
		String varname = in.readUTF();
		requestHandler.addResponse(reqid, new ContextVariableNotFoundResponse(varname));
	}

	private void handleCommandAsyncResponse(DataInputUnsyncByteArrayInputStream in) throws IOException {
		RMIVariables vars = readVariablesImpl(in);
		if (vars != null) {
			vars.removeOngoingAsyncRequest();
		}
	}

	/**
	 * @param variablesremoteid
	 *            The {@link RMIVariables#getRemoteIdentifier()} of the variables.
	 */
	//parameter is an integer instead of an RMIVariables, so if it gets closed on the server, 
	//then a response can still be sent back based on the identifier
	private void writeCommandAsyncResponse(int variablesremoteid) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_ASYNC_RESPONSE);
			out.writeInt(variablesremoteid);
		}
	}

	private static void checkRedispatchResponseAdded(boolean responseadded) {
		if (!responseadded) {
			throw new RMICallFailedException("RMI redispatch call site is no longer available.");
		}
	}

	private static Object readMethodInvokeObject(RMIVariables variables, int localid) {
		if (localid == RMIVariables.NO_OBJECT_ID) {
			return null;
		}
		return variables.requireObjectWithLocalId(localid);
	}

	private void handleCommandMethodResult(DataInputUnsyncByteArrayInputStream in, ReferencesReleasedAction gcaction)
			throws IOException {
		try {
			boolean interrupted = false;
			int interruptreqcount = 0;
			RMIVariables variables;

			int reqid = in.readInt();
			try {
				variables = readVariablesValidate(in);
				int compressedinterruptstatus = in.readInt();
				interrupted = isCompressedInterruptStatusInvokerThreadInterrupted(compressedinterruptstatus);
				interruptreqcount = getCompressedInterruptStatusDeliveredRequestCount(compressedinterruptstatus);
			} catch (IOException e) {
				requestHandler.addResponse(reqid, new MethodCallIOFailureResponse(interrupted, interruptreqcount,
						"Failed to read method result.", e));
				return;
			} catch (RMIRuntimeException e) {
				requestHandler.addResponse(reqid, new MethodCallFailedResponse(interrupted, interruptreqcount, e));
				return;
			}

			Object value;
			try {
				value = readObject(variables, in);
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
					| ServiceConfigurationError e) {
				requestHandler.addResponse(reqid, new MethodCallObjectTransferFailedResponse(interrupted,
						interruptreqcount, "Failed to read method result.", e));
				return;
			}

			requestHandler.addResponse(reqid, new MethodCallResponse(interrupted, interruptreqcount, value));
		} finally {
			gcaction.decreasePendingRequestCount();
		}
	}

	private void handleCommandCloseVariables(DataInputUnsyncByteArrayInputStream in) throws EOFException {
		RMIVariables vars = readVariablesImpl(in);
		if (vars != null) {
			vars.remotelyClosed();
		}
	}

	protected static CommandHandler getCommandHandler(short command) {
		try {
			return COMMAND_HANDLERS[command];
		} catch (ArrayIndexOutOfBoundsException e) {
			// should happen very rarely, only when an unknown command is sent.
			// so its probably more efficient (?) to handle in a try-catch, than by using an if-else
			return null;
		}
	}

	private static ClassLoader getClassLoaderWithId(RMIVariables variables, int localid) {
		if (localid == RMIVariables.NO_OBJECT_ID) {
			return null;
		}
		Object obj = variables.requireObjectWithLocalId(localid);
		if (obj instanceof ClassLoader) {
			return (ClassLoader) obj;
		}
		throw new RMICallFailedException(
				"Object with id: " + localid + " is not an instance of " + ClassLoader.class.getName());
	}

	void writeCommandReferencesReleased(RMIVariables variables, int remoteid, int count) throws InterruptedException {
		if (count <= 0) {
			throw new IllegalArgumentException("Count must be greater than zero: " + count);
		}
		StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> buffer = connection.getCachedByteBuffer();
		DataOutputUnsyncByteArrayOutputStream out = buffer.get();
		out.writeShort(COMMAND_REFERENCES_RELEASED);
		writeVariables(variables, out);
		out.writeInt(remoteid);
		out.writeInt(count);

		Semaphore gcsemaphore = variables.gcCommandSemaphore;
		gcsemaphore.acquire();
		try {
			flushCommand(buffer);
		} finally {
			gcsemaphore.release();
		}
	}

	private void instantiateAndWriteUnknownNewInstanceCall(int reqid, RMIVariables variables, ClassLoader cl,
			String classname, String[] argclassnames, Object[] args) {

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
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
					| ServiceConfigurationError e) {
				writeCommandExceptionResult(COMMAND_UNKNOWN_NEWINSTANCE_RESULT, reqid, e, Thread.interrupted(),
						interruptreqcount);
				return;
			}
			writeCommandUnknownNewInstanceResult(variables, reqid, localindex, ReflectUtils.getAllInterfaces(clazz),
					Thread.interrupted(), interruptreqcount);
		} finally {
			variables.removeOngoingRequest();
		}
	}

	public static Set<Class<?>> getPublicNonAssignableInterfaces(Class<?> clazz, MethodHandles.Lookup lookup,
			RMIStatistics rmistatistics) {
		Set<Class<?>> itfs = ReflectUtils.getAllInterfaces(clazz);
		reducePublicNonAssignableInterfaces(itfs, lookup, rmistatistics);
		return itfs;
	}

	public static void reducePublicNonAssignableInterfaces(Set<Class<?>> itfs, MethodHandles.Lookup lookup,
			RMIStatistics rmistatistics) {
		if (itfs.isEmpty()) {
			return;
		}
		for (Iterator<Class<?>> it = itfs.iterator(); it.hasNext();) {
			Class<?> itf = it.next();
			if (!itf.isInterface()) {
				//the type might not be an interface if read from an other endpoint
				it.remove();
				continue;
			}
			if (!Modifier.isPublic(itf.getModifiers())) {
				//the type might not be an interface if read from an other endpoint
				it.remove();
				if (rmistatistics != null) {
					rmistatistics.inaccessibleInterface(itf);
				}
				continue;
			}
			try {
				ReflectUtils.lookupAccessClass(lookup, itf);
			} catch (IllegalAccessException | SecurityException e) {
				it.remove();
				if (rmistatistics != null) {
					rmistatistics.inaccessibleInterface(itf);
				}
				continue;
			}
		}
		ReflectUtils.reduceAssignableTypes(itfs);
	}

	private void instantiateAndWriteNewInstanceCall(int reqid, RMIVariables variables, Constructor<?> constructor,
			Object[] args) {
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
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
					| ServiceConfigurationError e) {
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
			MethodTransferProperties method, Object[] args) {
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
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
					| ServiceConfigurationError e) {
				writeCommandExceptionResult(COMMAND_METHODRESULT_FAIL, reqid, e, Thread.interrupted(),
						interruptreqcount);
				return;
			}
			writeCommandMethodResult(variables, reqid, dispatchresult, method, Thread.interrupted(), interruptreqcount);
		} finally {
			variables.removeOngoingRequest();
		}
	}

	private void writeCommandInterruptRequest(int reqid) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_INTERRUPT_REQUEST);
			out.writeInt(reqid);
		}
	}

	private void writeCommandPing(int reqid) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_PING);
			out.writeInt(reqid);
		}
	}

	private void writeCommandGetContextVar(int reqid, RMIVariables vars, String variableid) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_GET_CONTEXT_VAR);
			writeVariables(vars, out);
			out.writeInt(reqid);
			try {
				out.writeUTF(variableid);
			} catch (UTFDataFormatException e) {
				// if the variable id is too long
				throw new IllegalArgumentException("Invalid context variable name: " + variableid, e);
			}
		}
	}

	private void writeGetContextVarResponse(int reqid, RMIVariables vars, Object result) {
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

	private void writeCommandPong(int reqid) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_PONG);
			out.writeInt(reqid);
		}
	}

	private void writeCommandCachedClass(ClassReflectionElementSupplier clazz, int index) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_CACHED_CLASS);
			writeClassData(clazz, out);
			out.writeInt(index);
		}
	}

	private void writeCommandCachedClassLoader(ClassLoaderReflectionElementSupplier cl, int index) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_CACHED_CLASSLOADER);
			writeClassLoaderData(cl, out);
			out.writeInt(index);
		}
	}

	private void writeCommandCachedMethod(MethodReflectionElementSupplier method, int index) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_CACHED_METHOD);
			writeMethodData(method, out);
			out.writeInt(index);
		}
	}

	private void writeCommandCachedConstructor(ConstructorReflectionElementSupplier constructor, int index) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_CACHED_CONSTRUCTOR);
			writeConstructorData(constructor, out);
			out.writeInt(index);
		}
	}

	private void writeCommandCachedField(FieldReflectionElementSupplier field, int index) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_CACHED_FIELD);
			writeFieldData(field, out);
			out.writeInt(index);
		}
	}

	private void writeCommandNewVariablesResult(int reqid, int localid) {
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
		writeClasses(ReflectUtils.getAllInterfaces(obj.getClass()), out);
		out.writeInt(localid);
	}

	private Object readNewRemoteObject(RMIVariables variables, DataInputUnsyncByteArrayInputStream in)
			throws IOException {
		if (variables == null) {
			throw new RMIObjectTransferFailureException("Failed to read remote object with null variables.");
		}
		Set<Class<?>> classes;
		try {
			classes = readClassesSet(in, variables);
		} catch (ClassSetPartiallyReadException e) {
			classes = e.getReadClasses();
		}
		int remoteid = in.readInt();
		return variables.getProxyIncreaseReference(classes, remoteid);
	}

	private void writeCommandUnknownNewInstanceResult(RMIVariables variables, int reqid, int localindex,
			Set<Class<?>> interfaces, boolean currentthreadinterrupted, int interruptreqcount) {
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
			int interruptreqcount) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_NEWINSTANCE_RESULT);
			out.writeInt(reqid);
			out.writeInt(localindex);
			out.writeInt(compressInterruptStatus(currentthreadinterrupted, interruptreqcount));
		}
	}

	private void writeCommandMethodCallAsync(RMIVariables variables, int remoteid, MethodTransferProperties method,
			Object[] arguments) {
		checkClosed();
		StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> buffer = connection.getCachedByteBuffer();
		DataOutputUnsyncByteArrayOutputStream out = buffer.get();

		out.writeShort(COMMAND_METHODCALL_ASYNC);
		writeVariables(variables, out);
		out.writeInt(remoteid);

		writeMethod(method.getExecutable(), out);

		Semaphore gcsemaphore = variables.gcCommandSemaphore;
		gcsemaphore.acquireUninterruptibly();
		try {
			writeMethodParameters(variables, method, arguments, out);
			flushCommand(buffer);
		} finally {
			gcsemaphore.release();
		}
	}

	private void writeCommandMethodCallAsyncWithResponse(RMIVariables variables, int remoteid,
			MethodTransferProperties method, Object[] arguments) {
		checkClosed();
		StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> buffer = connection.getCachedByteBuffer();
		DataOutputUnsyncByteArrayOutputStream out = buffer.get();

		out.writeShort(COMMAND_METHODCALL_ASYNC_WITH_RESPONSE);
		out.writeInt(variables.getLocalIdentifier());
		writeVariables(variables, out);
		out.writeInt(remoteid);

		writeMethod(method.getExecutable(), out);

		Semaphore gcsemaphore = variables.gcCommandSemaphore;
		gcsemaphore.acquireUninterruptibly();
		try {
			writeMethodParameters(variables, method, arguments, out);
			flushCommand(buffer);
		} finally {
			gcsemaphore.release();
		}
	}

	private void writeCommandMethodCall(RMIVariables variables, int reqid, int remoteid,
			MethodTransferProperties method, Object[] arguments, Integer dispatch) {
		checkClosed();
		StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> buffer = connection.getCachedByteBuffer();
		DataOutputUnsyncByteArrayOutputStream out = buffer.get();

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

		Semaphore gcsemaphore = variables.gcCommandSemaphore;
		gcsemaphore.acquireUninterruptibly();
		try {
			writeMethodParameters(variables, method, arguments, out);
			flushCommand(buffer);
		} finally {
			gcsemaphore.release();
		}
	}

	private void writeCommandContextVariableMethodCall(RMIVariables variables, int reqid, String variablename,
			MethodTransferProperties method, Object[] arguments, Integer dispatch) {
		checkClosed();
		StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> buffer = connection.getCachedByteBuffer();
		DataOutputUnsyncByteArrayOutputStream out = buffer.get();

		if (dispatch == null) {
			out.writeShort(COMMAND_METHODCALL_CONTEXTVAR);
			out.writeInt(reqid);
			writeVariables(variables, out);
		} else {
			out.writeShort(COMMAND_METHODCALL_CONTEXTVAR_REDISPATCH);
			out.writeInt(reqid);
			writeVariables(variables, out);
			out.writeInt(dispatch);
		}
		try {
			out.writeUTF(variablename);
		} catch (UTFDataFormatException e) {
			throw new IllegalArgumentException("Invalid context variable name: " + variablename, e);
		}

		writeMethod(method.getExecutable(), out);

		Semaphore gcsemaphore = variables.gcCommandSemaphore;
		gcsemaphore.acquireUninterruptibly();
		try {
			writeMethodParameters(variables, method, arguments, out);
			flushCommand(buffer);
		} finally {
			gcsemaphore.release();
		}
	}

	private void writeCommandNewRemoteInstance(RMIVariables variables, int reqid,
			ConstructorTransferProperties<?> constructor, Object[] arguments, Integer dispatch) {
		checkClosed();
		StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> buffer = connection.getCachedByteBuffer();
		DataOutputUnsyncByteArrayOutputStream out = buffer.get();

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

		Semaphore gcsemaphore = variables.gcCommandSemaphore;
		gcsemaphore.acquireUninterruptibly();
		try {
			writeMethodParameters(variables, constructor, arguments, out);
			flushCommand(buffer);
		} finally {
			gcsemaphore.release();
		}
	}

	private void writeCommandNewRemoteInstanceUnknownClass(RMIVariables variables, int reqid, int remoteclassloaderid,
			String classname, String[] argumentclassnames, Object[] arguments, Integer dispatch) {
		if (argumentclassnames.length != arguments.length) {
			throw new RMICallFailedException("Length of argument types doesn't match provided argument object count: "
					+ argumentclassnames.length + " != " + arguments.length);
		}
		checkClosed();
		StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> buffer = connection.getCachedByteBuffer();
		DataOutputUnsyncByteArrayOutputStream out = buffer.get();

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

		try {
			out.writeUTF(classname);
		} catch (UTFDataFormatException e) {
			throw new RMIObjectTransferFailureException("Failed to transfer class name: " + classname, e);
		}
		out.writeInt(argumentclassnames.length);

		Semaphore gcsemaphore = variables.gcCommandSemaphore;
		gcsemaphore.acquireUninterruptibly();
		try {
			for (int i = 0; i < argumentclassnames.length; i++) {
				try {
					out.writeUTF(argumentclassnames[i]);
				} catch (UTFDataFormatException e) {
					throw new RMIObjectTransferFailureException(
							"Failed to transfer argument[" + i + "] class name: " + argumentclassnames[i], e);
				}
				try {
					Object obj = arguments[i];
					writeObjectUsingWriteHandler(RMIObjectWriteHandler.defaultWriter(), variables, obj, out,
							ObjectUtils.classOf(obj));
				} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
						| ServiceConfigurationError e) {
					throw new RMIObjectTransferFailureException("Failed to write constructor call argument[" + i + "].",
							e);
				}
			}
			flushCommand(buffer);
		} finally {
			gcsemaphore.release();
		}
	}

	private void writeCommandMethodResult(RMIVariables variables, int reqid, Object returnvalue,
			MethodTransferProperties executableproperties, boolean currentthreadinterrupted, int interruptreqcount) {
		returnvalue = unwrapWrapperForTransfer(returnvalue, variables);

		checkClosed();
		StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> buffer = connection.getCachedByteBuffer();
		DataOutputUnsyncByteArrayOutputStream out = buffer.get();

		out.writeShort(COMMAND_METHODRESULT);
		out.writeInt(reqid);
		writeVariables(variables, out);

		out.writeInt(compressInterruptStatus(currentthreadinterrupted, interruptreqcount));

		Semaphore gcsemaphore = variables.gcCommandSemaphore;
		gcsemaphore.acquireUninterruptibly();
		try {
			try {
				writeObjectUsingWriteHandler(executableproperties.getReturnValueWriter(), variables, returnvalue, out,
						executableproperties.getReturnType());
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
					| ServiceConfigurationError e) {
				//failed to write the return value for some reason

				//remove all previously written data from the buffer
				out.reset();

				//write a fail result to the output, and we will flush it below
				writeCommandExceptionResult(COMMAND_METHODRESULT_FAIL, reqid, e, currentthreadinterrupted,
						interruptreqcount, out);
			}
			flushCommand(buffer);
		} finally {
			gcsemaphore.release();
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

	private void writeCommandContextVariableMethodCallVariableNotFound(int reqid, String varname) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_METHODCALL_CONTEXTVAR_NOT_FOUND);
			out.writeInt(reqid);
			try {
				out.writeUTF(varname);
			} catch (UTFDataFormatException e) {
				// can't really happen, because the variable name was once encoded when we received it
				// so we sould be able to encode it again, it must be an internal error if we cant
				throw new IllegalStateException("Internal error, failed to re-encode context variable name: " + varname,
						e);
			}
		}
	}

	private void writeCommandExceptionResult(short commandname, int reqid, Throwable exc,
			boolean currentthreadinterrupted, int interruptreqcount) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			writeCommandExceptionResult(commandname, reqid, exc, currentthreadinterrupted, interruptreqcount, out);
		}
	}

	private void writeCommandExceptionResult(short commandname, int reqid, Throwable exc,
			boolean currentthreadinterrupted, int interruptreqcount, DataOutputUnsyncByteArrayOutputStream out) {
		out.writeShort(commandname);
		out.writeInt(reqid);
		out.writeInt(compressInterruptStatus(currentthreadinterrupted, interruptreqcount));
		writeException(exc, out);
	}

	private void writeException(Throwable exc, DataOutputUnsyncByteArrayOutputStream out) {
		if (!tryWriteException(exc, out)) {
			Throwable ioe = new RMIStackTracedException(exc);
			if (!tryWriteException(ioe, out)) {
				//writing RMIStackTracedException should always succeed, 
				//as they don't contain any special fields or anything
				//consider this a harder failure
				throw new RMIIOFailureException(ioe);
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
		} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
				| ServiceConfigurationError e) {
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

			Optional<ClassLoader> clopt = getClassLoaderByIdOptional(connection, clid);
			if (clopt == null) {
				throw new ClassNotFoundException(
						"Class not found: " + desc.getName() + " (ClassLoader not found for ID: " + clid + ")");
			}

			ClassLoader classloader = clopt.orElse(null);
			try {
				Class<?> result = Class.forName(desc.getName(), false, classloader);
				return result;
			} catch (ClassNotFoundException e) {
				throw new ClassNotFoundException(
						"Class not found in classloader with id: " + StringUtils.toStringQuoted(clid), e);
			}
		}
	}

	private void writeCommandNewVariables(String name, int identifier, int reqid) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_NEW_VARIABLES);
			out.writeInt(reqid);
			out.writeInt(identifier);
			try {
				out.writeUTF(name == null ? "" : name);
			} catch (UTFDataFormatException e) {
				throw new IllegalArgumentException("Failed to write variables context name: " + name, e);
			}
		}
	}

	private void writeCommandCloseVariables(RMIVariables variables) {
		try (CommandFlusher flusher = new CommandFlusher()) {
			DataOutputUnsyncByteArrayOutputStream out = flusher.getBuffer();
			out.writeShort(COMMAND_CLOSE_VARIABLES);
			writeVariables(variables, out);
		}
	}

	protected void checkClosed() throws RMIIOFailureException {
		if (streamCloseWritten != 0) {
			throw new RMIResourceUnavailableException("Stream already closed.");
		}
	}

	private void checkAborting(Integer currentrequestid) throws RMIIOFailureException {
		if (currentrequestid != null && connection.isAborting()) {
			throw new RMIResourceUnavailableException("RMI connection is aborting. No new requests are allowed.");
		}
	}

	static Integer nullizeRequestId(int reqid) {
		return reqid == 0 ? null : reqid;
	}

	private void checkAborting() throws RMIIOFailureException {
		checkAborting(requestScopeHandler.getCurrentServingRequest());
	}

	private Object invokeMethodWithRequestId(Method method, Object object, Object[] arguments, int reqid)
			throws InvocationTargetException {
		try {
			return requestScopeHandler.run(reqid, () -> {
				try {
					return ReflectUtils.invokeMethod(object, method, arguments);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new RMICallFailedException("Failed to call method " + method + " with object type: "
							+ ObjectUtils.classNameOf(object) + " and argument types: "
							+ Arrays.toString(ObjectUtils.classOfArrayElements(arguments)), e);
				}
			});
		} catch (InvocationTargetException e) {
			throw e;
		} catch (Exception e) {
			//other checked exceptions are not expected to be thrown by the invocation
			throw ObjectUtils.sneakyThrow(e);
		}
	}

	private <C> C invokeConstructorWithRequestId(Constructor<C> constructor, Object[] arguments, int reqid)
			throws InvocationTargetException, IllegalAccessException, InstantiationException {
		try {
			return requestScopeHandler.run(reqid, () -> {
				return ReflectUtils.invokeConstructor(constructor, arguments);
			});
		} catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
			throw e;
		} catch (Exception e) {
			//other checked exceptions are not expected to be thrown by the invocation
			throw ObjectUtils.sneakyThrow(e);
		}
	}

	//caller should call RMIVariables.addOngoingRequest(), which checks for RMIVariables state
	Object callMethod(RMIVariables variables, int remoteid, MethodTransferProperties method, Object[] arguments)
			throws RMIIOFailureException, InvocationTargetException {
		Integer currentservingrequest = requestScopeHandler.getCurrentServingRequest();
		return callMethod(variables, remoteid, method, currentservingrequest, arguments);
	}

	private Object callMethod(RMIVariables variables, int remoteid, MethodTransferProperties method, Integer dispatch,
			Object[] arguments) throws RMIIOFailureException, InvocationTargetException {
		try (Request request = requestHandler.newRequest()) {
			int reqid = request.getRequestId();
			writeCommandMethodCall(variables, reqid, remoteid, method, arguments, dispatch);
			MethodCallResponse mcr = waitInterruptTrackingResponse(request, MethodCallResponse.class);

			return mcr.getReturnValue();
		}
	}

	//caller should call RMIVariables.addOngoingRequest(), which checks for RMIVariables state
	Object callContextVariableMethod(RMIVariables variables, String variablename, MethodTransferProperties method,
			Object[] arguments) throws RMIIOFailureException, InvocationTargetException {
		Integer currentservingrequest = requestScopeHandler.getCurrentServingRequest();
		return callContextVariableMethod(variables, variablename, method, currentservingrequest, arguments);
	}

	private Object callContextVariableMethod(RMIVariables variables, String variablename,
			MethodTransferProperties method, Integer dispatch, Object[] arguments)
			throws RMIIOFailureException, InvocationTargetException {
		try (Request request = requestHandler.newRequest()) {
			int reqid = request.getRequestId();
			writeCommandContextVariableMethodCall(variables, reqid, variablename, method, arguments, dispatch);
			MethodCallResponse mcr = waitInterruptTrackingResponse(request, MethodCallResponse.class);

			return mcr.getReturnValue();
		}
	}

	void callMethodAsync(RMIVariables variables, int remoteid, MethodTransferProperties method, Object[] arguments)
			throws RMIIOFailureException {
		if (connection.getProtocolVersion() >= 2) {
			//supports response for async
			//the ongoing request will be removed when that response arrives
			variables.addOngoingAsyncRequest();
			try {
				writeCommandMethodCallAsyncWithResponse(variables, remoteid, method, arguments);
			} catch (Throwable e) {
				try {
					variables.removeOngoingAsyncRequest();
				} catch (Throwable e2) {
					e.addSuppressed(e2);
				}
				throw e;
			}
		} else {
			//add ongoing request, and remove it right away
			//which checks for the state of the RMIVariables
			//as well as prevents the streams and connection to be concurrently closed
			variables.addOngoingAsyncRequest();
			try {
				writeCommandMethodCallAsync(variables, remoteid, method, arguments);
			} finally {
				variables.removeOngoingAsyncRequest();
			}
		}
	}

	private <RetType extends InterruptStatusTrackingRequestResponse> RetType waitInterruptTrackingResponse(
			Request request, Class<RetType> type) {
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
	}

	private static void invokeDispatchingOrThrow(RequestResponse response) throws RMIIOFailureException {
		if (response instanceof RedispatchResponse) {
			RedispatchResponse mrr = (RedispatchResponse) response;
			mrr.executeRedispatchAction();
		} else {
			throw new RMICallFailedException("Unknown response received: " + response);
		}
	}

	protected final void streamError(Throwable exc) {
		//only set the close reason if this is the first exception that we encounter, otherwise some other error
		//was recorded, and this is probably also a cause of that
		initRequestHandlerCloseReason(req -> new RMIIOFailureException("RMI stream error.", exc));
		connection.streamError(this, exc);
	}

	//caller should call RMIVariables.addOngoingRequest(), which checks for RMIVariables state
	Object newRemoteInstance(RMIVariables variables, ConstructorTransferProperties<?> constructor, Object... arguments)
			throws RMIIOFailureException, InvocationTargetException, RMICallFailedException {
		Integer currentservingrequest = requestScopeHandler.getCurrentServingRequest();
		return newRemoteInstance(variables, constructor, currentservingrequest, arguments);
	}

	private Object newRemoteInstance(RMIVariables variables, ConstructorTransferProperties<?> constructor,
			Integer dispatch, Object... arguments)
			throws RMIIOFailureException, InvocationTargetException, RMICallFailedException {
		try (Request request = requestHandler.newRequest()) {
			int reqid = request.getRequestId();

			writeCommandNewRemoteInstance(variables, reqid, constructor, arguments, dispatch);

			NewInstanceResponse response = waitInterruptTrackingResponse(request, NewInstanceResponse.class);

			int remoteid = response.getRemoteId();
			Class<?> declaringclass = constructor.getExecutable().getDeclaringClass();

			return variables.getProxyIncreaseReference(declaringclass, remoteid);
		}
	}

	//caller should call RMIVariables.addOngoingRequest(), which checks for RMIVariables state
	Object newRemoteOnlyInstance(RMIVariables variables, int remoteclassloaderid, String classname,
			String[] constructorargumentclasses, Object[] constructorarguments)
			throws RMIIOFailureException, RMICallFailedException, InvocationTargetException {
		Integer currentservingrequest = requestScopeHandler.getCurrentServingRequest();
		return newRemoteOnlyInstance(variables, remoteclassloaderid, classname, constructorargumentclasses,
				constructorarguments, currentservingrequest);
	}

	private Object newRemoteOnlyInstance(RMIVariables variables, int remoteclassloaderid, String classname,
			String[] constructorargumentclasses, Object[] constructorarguments, Integer dispatch)
			throws RMIIOFailureException, RMICallFailedException, InvocationTargetException {
		try (Request request = requestHandler.newRequest()) {
			int reqid = request.getRequestId();

			writeCommandNewRemoteInstanceUnknownClass(variables, reqid, remoteclassloaderid, classname,
					constructorargumentclasses, constructorarguments, dispatch);

			NewInstanceResponse response = waitInterruptTrackingResponse(request, NewInstanceResponse.class);
			//get remote id before testing for instanceof, as getting the id will throw an appropriate exception
			int remoteid = response.getRemoteId();
			Set<Class<?>> interfaces = response.getInterfaces();

			return variables.getProxyIncreaseReference(interfaces, remoteid);
		}
	}

	/**
	 * Called while locked on {@link RMIConnection} state lock.
	 * 
	 * @param variables
	 */
	void writeVariablesClosed(RMIVariables variables) {
		while (true) {
			RMIVariables[] vars = this.associatedVariables;
			if (vars == null) {
				//stream IO shut down, writing below might fail, but probably okay for the caller
				break;
			}
			int associndex = variables.streamAssociationIndex;
			if (associndex < 0) {
				//the variables is not associated with this stream
				//might be because the initialization failed/aborted, so it wasn't fully assigned to this stream
				//skip removal from the array, and just go and write the close command
				break;
			}
			if (associndex >= vars.length || vars[associndex] != variables) {
				throw new IllegalArgumentException("RMIVariables is not associated with this stream.");
			}
			RMIVariables[] narray;
			if (vars.length == 0) {
				narray = EMPTY_RMI_VARIABLES_ARRAY;
				if (ARFU_associatedVariables.compareAndSet(this, vars, narray)) {
					variables.streamAssociationIndex = -1;
					break;
				}
			} else if (associndex == vars.length - 1) {
				narray = Arrays.copyOf(vars, vars.length - 1);
				if (ARFU_associatedVariables.compareAndSet(this, vars, narray)) {
					variables.streamAssociationIndex = -1;
					break;
				}
			} else {
				narray = new RMIVariables[vars.length - 1];
				System.arraycopy(vars, 0, narray, 0, associndex);
				RMIVariables movedvars = vars[vars.length - 1];
				narray[associndex] = movedvars;
				System.arraycopy(vars, associndex + 1, narray, associndex + 1, vars.length - associndex - 2);

				if (ARFU_associatedVariables.compareAndSet(this, vars, narray)) {
					variables.streamAssociationIndex = -1;
					movedvars.streamAssociationIndex = associndex;
					break;
				}
			}
			//try again
		}
		writeCommandCloseVariables(variables);
	}

	/**
	 * Called while locked on {@link RMIConnection} state lock.
	 * 
	 * @param variables
	 * @return <code>true</code> if the stream is alive, and the association was successful.
	 */
	boolean associateVariables(RMIVariables variables) {
		while (true) {
			RMIVariables[] vars = this.associatedVariables;
			if (vars == null) {
				return false;
			}
			RMIVariables[] narray = ArrayUtils.appended(vars, variables);
			variables.streamAssociationIndex = vars.length;
			if (ARFU_associatedVariables.compareAndSet(this, vars, narray)) {
				return true;
			}
			//try again
		}
	}

	/**
	 * @return negative if the stream IO has shut down.
	 */
	int getAssociatedRMIVariablesCount() {
		RMIVariables[] vars = associatedVariables;
		if (vars == null) {
			return -1;
		}
		return vars.length;
	}

	int createNewVariables(String name, int varlocalid) throws RMIRuntimeException {
		try (Request request = requestHandler.newRequest()) {
			int reqid = request.getRequestId();
			writeCommandNewVariables(name, varlocalid, reqid);
			NewVariablesResponse nvr = request.waitInstanceOfResponse(NewVariablesResponse.class);
			return nvr.getRemoteIdentifier();
		}
	}

	public void ping() {
		checkAborting();

		try (Request request = requestHandler.newRequest()) {
			int reqid = request.getRequestId();
			writeCommandPing(reqid);
			request.waitInstanceOfResponse(PingResponse.class);
		}
	}

	Object getRemoteContextVariable(RMIVariables vars, String variablename) {
		try (Request request = requestHandler.newRequest()) {
			int reqid = request.getRequestId();
			writeCommandGetContextVar(reqid, vars, variablename);
			return request.waitInstanceOfResponse(GetContextVariableResponse.class).getVariable();
		}
	}

	static class DynamicClassLoaderReflectionElementSupplier implements ClassLoaderReflectionElementSupplier {
		private RMIConnection connection;
		private String classLoaderId;

		public DynamicClassLoaderReflectionElementSupplier(RMIConnection connection, String classLoaderId) {
			this.connection = connection;
			this.classLoaderId = classLoaderId;
		}

		@Override
		public String getClassLoaderId() {
			return classLoaderId;
		}

		@Override
		public ClassLoader get(RMIConnection connection) throws ClassNotFoundException {
			Optional<ClassLoader> clopt = getClassLoaderByIdOptional(connection, classLoaderId);
			if (clopt == null) {
				throw new ClassNotFoundException("Class loader not found with id: " + classLoaderId);
			}
			return clopt.orElse(null);
		}

		@Override
		public int hashCode() {
			return (classLoaderId == null) ? 0 : classLoaderId.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			DynamicClassLoaderReflectionElementSupplier other = (DynamicClassLoaderReflectionElementSupplier) obj;
			if (connection != other.connection)
				return false;
			if (classLoaderId == null) {
				if (other.classLoaderId != null)
					return false;
			} else if (!classLoaderId.equals(other.classLoaderId))
				return false;
			return true;
		}

		@Override
		public String toString() {
			//the classloader id may contain invalid characters, replace them with escaped zero
			return "[Classloader with ID: " + classLoaderId + "]".replace("\0", "\\0");
		}
	}

	interface ClassReflectionElementSupplier extends ReflectionElementSupplier {
		public Class<?> get(RMIConnection connection) throws ClassNotFoundException;

		public String getClassName();

		public ClassLoaderReflectionElementSupplier getClassLoader();
	}

	interface ClassLoaderReflectionElementSupplier extends ReflectionElementSupplier {
		public ClassLoader get(RMIConnection connection) throws ClassNotFoundException;

		public String getClassLoaderId();
	}

	interface ConstructorReflectionElementSupplier extends ReflectionElementSupplier {
		public Constructor<?> get(RMIConnection connection) throws ClassNotFoundException;

		public ClassReflectionElementSupplier getDeclaringClass();

		public String[] getParameterTypeNames();
	}

	interface MethodReflectionElementSupplier extends ReflectionElementSupplier {
		public Method get(RMIConnection connection, Object relativeobject) throws ClassNotFoundException;

		public ClassReflectionElementSupplier getDeclaringClass();

		public String getMethodName();

		public String[] getParameterTypeNames();
	}

	interface FieldReflectionElementSupplier extends ReflectionElementSupplier {
		public Field get(RMIConnection connection, Object relativeobject) throws ClassNotFoundException;

		public ClassReflectionElementSupplier getDeclaringClass();

		public String getFieldName();
	}

	static class DeclaredFieldReflectionElementSupplier implements FieldReflectionElementSupplier {

		private ClassReflectionElementSupplier declaringClass;
		private String fieldName;

		public DeclaredFieldReflectionElementSupplier(ClassReflectionElementSupplier declaringClass, String fieldName) {
			super();
			this.declaringClass = declaringClass;
			this.fieldName = fieldName;
		}

		@Override
		public Field get(RMIConnection connection, Object relativeobject) throws ClassNotFoundException {
			if (relativeobject != null) {
				Class<?> hierarchyclass = ReflectUtils.findTypeWithNameInHierarchy(relativeobject.getClass(),
						declaringClass.getClassName());
				if (hierarchyclass != null) {
					try {
						return hierarchyclass.getDeclaredField(fieldName);
					} catch (NoSuchFieldException | SecurityException e) {
						throw new RMIObjectTransferFailureException("Field not found: " + this, e);
					}
				}
			}
			try {
				return declaringClass.get(connection).getDeclaredField(fieldName);
			} catch (NoSuchFieldException | SecurityException e) {
				throw new RMIObjectTransferFailureException("Field not found: " + this, e);
			}
		}

		@Override
		public ClassReflectionElementSupplier getDeclaringClass() {
			return declaringClass;
		}

		@Override
		public String getFieldName() {
			return fieldName;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((declaringClass == null) ? 0 : declaringClass.hashCode());
			result = prime * result + ((fieldName == null) ? 0 : fieldName.hashCode());
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
			DeclaredFieldReflectionElementSupplier other = (DeclaredFieldReflectionElementSupplier) obj;
			if (declaringClass == null) {
				if (other.declaringClass != null)
					return false;
			} else if (!declaringClass.equals(other.declaringClass))
				return false;
			if (fieldName == null) {
				if (other.fieldName != null)
					return false;
			} else if (!fieldName.equals(other.fieldName))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "[field " + fieldName + " in " + declaringClass + "]";
		}

	}

	static class ImplConstructorReflectionElementSupplier implements ConstructorReflectionElementSupplier {

		private ClassReflectionElementSupplier declaringClass;
		private String[] parameterTypes;

		public ImplConstructorReflectionElementSupplier(ClassReflectionElementSupplier declaringClass,
				String[] parameterTypes) {
			super();
			this.declaringClass = declaringClass;
			this.parameterTypes = parameterTypes;
		}

		@Override
		public Constructor<?> get(RMIConnection connection) throws ClassNotFoundException {
			Class<?> declclass = declaringClass.get(connection);
			ClassLoader declcl = declclass.getClassLoader();
			Class<?>[] paramtypes = loadParameterTypeClasses(declcl, parameterTypes, this);
			try {
				return declclass.getConstructor(paramtypes);
			} catch (NoSuchMethodException | SecurityException e) {
				throw new RMIObjectTransferFailureException("Failed to retrieve method: " + this, e);
			}
		}

		@Override
		public ClassReflectionElementSupplier getDeclaringClass() {
			return declaringClass;
		}

		@Override
		public String[] getParameterTypeNames() {
			return parameterTypes;
		}

	}

	static class ImplMethodReflectionElementSupplier implements MethodReflectionElementSupplier {

		private ClassReflectionElementSupplier declaringClass;
		private String methodName;
		private String[] parameterTypes;

		public ImplMethodReflectionElementSupplier(ClassReflectionElementSupplier declaringClass, String methodName,
				String[] parameterTypes) {
			super();
			this.declaringClass = declaringClass;
			this.methodName = methodName;
			this.parameterTypes = parameterTypes;
		}

		@Override
		public Method get(RMIConnection connection, Object relativeobject) throws ClassNotFoundException {
			if (relativeobject != null) {
				Class<?> hierarchyclass = ReflectUtils.findTypeWithNameInHierarchy(relativeobject.getClass(),
						declaringClass.getClassName());
				if (hierarchyclass != null) {
					Class<?>[] paramtypeclasses = loadParameterTypeClasses(hierarchyclass.getClassLoader(),
							parameterTypes, this);
					try {
						return hierarchyclass.getMethod(methodName, paramtypeclasses);
					} catch (NoSuchMethodException | SecurityException e) {
						throw new RMIObjectTransferFailureException("Method not found: " + hierarchyclass + "."
								+ methodName + "(" + StringUtils.toStringJoin(", ", parameterTypes) + ")", e);
					}
				}
			}

			Class<?> declclass = declaringClass.get(connection);
			ClassLoader declcl = declclass.getClassLoader();
			String[] parametertypenames = parameterTypes;
			Class<?>[] paramtypes = loadParameterTypeClasses(declcl, parametertypenames, this);
			try {
				return declclass.getMethod(methodName, paramtypes);
			} catch (NoSuchMethodException | SecurityException e) {
				throw new RMIObjectTransferFailureException("Failed to retrieve method: " + this, e);
			}
		}

		@Override
		public ClassReflectionElementSupplier getDeclaringClass() {
			return declaringClass;
		}

		@Override
		public String getMethodName() {
			return methodName;
		}

		@Override
		public String[] getParameterTypeNames() {
			return parameterTypes;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((declaringClass == null) ? 0 : declaringClass.hashCode());
			result = prime * result + ((methodName == null) ? 0 : methodName.hashCode());
			result = prime * result + Arrays.hashCode(parameterTypes);
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
			ImplMethodReflectionElementSupplier other = (ImplMethodReflectionElementSupplier) obj;
			if (declaringClass == null) {
				if (other.declaringClass != null)
					return false;
			} else if (!declaringClass.equals(other.declaringClass))
				return false;
			if (methodName == null) {
				if (other.methodName != null)
					return false;
			} else if (!methodName.equals(other.methodName))
				return false;
			if (!Arrays.equals(parameterTypes, other.parameterTypes))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "[method" + declaringClass + "." + methodName + "(" + StringUtils.toStringJoin(", ", parameterTypes)
					+ ")]";
		}

	}

	static class NullClassLoaderReflectionElementSupplier implements ClassLoaderReflectionElementSupplier {
		private ClassLoader classLoader;

		public NullClassLoaderReflectionElementSupplier(ClassLoader classLoader) {
			this.classLoader = classLoader;
		}

		@Override
		public ClassLoader get(RMIConnection connection) throws RMIObjectTransferFailureException {
			return classLoader;
		}

		@Override
		public String getClassLoaderId() {
			return null;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((classLoader == null) ? 0 : classLoader.hashCode());
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
			NullClassLoaderReflectionElementSupplier other = (NullClassLoaderReflectionElementSupplier) obj;
			if (classLoader == null) {
				if (other.classLoader != null)
					return false;
			} else if (!classLoader.equals(other.classLoader))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "[" + classLoader + "]";
		}

	}

	static class BootstrapClassReflectionElementSupplier implements ClassReflectionElementSupplier {
		private static final NullClassLoaderReflectionElementSupplier BOOTSTRAP_CL_SUPPLIER = new NullClassLoaderReflectionElementSupplier(
				null);
		private final Class<?> c;

		public BootstrapClassReflectionElementSupplier(Class<?> c) {
			this.c = c;
		}

		@Override
		public Class<?> get(RMIConnection connection) throws RMIObjectTransferFailureException {
			return c;
		}

		@Override
		public String getClassName() {
			return c.getName();
		}

		@Override
		public ClassLoaderReflectionElementSupplier getClassLoader() {
			return BOOTSTRAP_CL_SUPPLIER;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((c == null) ? 0 : c.hashCode());
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
			BootstrapClassReflectionElementSupplier other = (BootstrapClassReflectionElementSupplier) obj;
			if (c == null) {
				if (other.c != null)
					return false;
			} else if (!c.equals(other.c))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "[" + c + "]";
		}

	}

	static class DynamicClassReflectionElementSupplier implements ClassReflectionElementSupplier {
		private ClassLoaderReflectionElementSupplier classLoaderSupplier;
		private String className;

		public DynamicClassReflectionElementSupplier(ClassLoaderReflectionElementSupplier classLoaderSupplier,
				String className) {
			this.classLoaderSupplier = classLoaderSupplier;
			this.className = className;
		}

		@Override
		public Class<?> get(RMIConnection connection) throws ClassNotFoundException {
			return Class.forName(className, false, classLoaderSupplier.get(connection));
		}

		@Override
		public ClassLoaderReflectionElementSupplier getClassLoader() {
			return classLoaderSupplier;
		}

		@Override
		public String getClassName() {
			return className;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((classLoaderSupplier == null) ? 0 : classLoaderSupplier.hashCode());
			result = prime * result + ((className == null) ? 0 : className.hashCode());
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
			DynamicClassReflectionElementSupplier other = (DynamicClassReflectionElementSupplier) obj;
			if (classLoaderSupplier == null) {
				if (other.classLoaderSupplier != null)
					return false;
			} else if (!classLoaderSupplier.equals(other.classLoaderSupplier))
				return false;
			if (className == null) {
				if (other.className != null)
					return false;
			} else if (!className.equals(other.className))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "[Class " + className + " from class loader " + classLoaderSupplier + "]";
		}

	}

	private static class RequestHandlerState {
		public static final RequestHandlerState INTIAL = new RequestHandlerState(false, 0);

		public final boolean closed;
		public final int unprocessedResponseCount;

		private RequestHandlerState(boolean closed, int unprocessedResponseCount) {
			this.closed = closed;
			this.unprocessedResponseCount = unprocessedResponseCount;
		}

		public RequestHandlerState close() {
			return new RequestHandlerState(true, unprocessedResponseCount);
		}

		public RequestHandlerState addResponseCount() {
			return new RequestHandlerState(closed, unprocessedResponseCount + 1);
		}

		public RequestHandlerState removeResponseCount() {
			int ncount = unprocessedResponseCount - 1;
			if (ncount < 0) {
				throw new IllegalStateException("No pending responses.");
			}
			return new RequestHandlerState(closed, ncount);
		}

	}
}