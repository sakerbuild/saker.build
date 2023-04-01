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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIDefaultOnFailure;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIExceptionRethrow;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIRedirect;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIRemote;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.exception.RMIInvalidConfigurationException;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.rmi.io.writer.RMIObjectWriteHandler;
import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;

/**
 * Describes the properties for a method for use with RMI.
 * <p>
 * This class describes the serialization of the parameters, result and also specifies how the method will be called.
 * <p>
 * The {@link Builder} should be used for construction, or the constructor
 * {@link MethodTransferProperties#MethodTransferProperties(Method)} for annotation based properties parsing.
 */
public final class MethodTransferProperties extends ExecutableTransferProperties<Method> {
	protected boolean defaultOnFailure = false;
	protected boolean forbidden = false;
	protected boolean cacheResult = false;
	//TODO implement the redirect method with a MethodHandle?
	protected Method redirectMethod;
	protected Constructor<? extends Throwable> rmiExceptionRethrowConstructor;
	protected RMIObjectWriteHandler returnValueWriter;

	private MethodTransferProperties() {
	}

	/**
	 * Creates an instance by examining the provided annotations on the given method.
	 * <p>
	 * The annotations of the given method is examined and a configuration is parsed for use.
	 * <p>
	 * See the possible method and parameter annotations for more information. (package
	 * <code>saker.rmi.annot.invoke</code> and package <code>saker.rmi.annot.transfer</code>)
	 * 
	 * @param method
	 *            The method for this properties.
	 * @throws RMIInvalidConfigurationException
	 *             If the configuration is invalid.
	 */
	public MethodTransferProperties(Method method) throws RMIInvalidConfigurationException {
		super(method, createParameterWriteHandlers(method));

		this.returnValueWriter = createWriteHandlerForMethod(method);

		defaultOnFailure = method.isAnnotationPresent(RMIDefaultOnFailure.class);
		if (!isValidDefaultOnFailure(method, defaultOnFailure)) {
			throw new RMIInvalidConfigurationException("Cannot call default method on failure with method: " + method);
		}
		cacheResult = method.isAnnotationPresent(RMICacheResult.class);
		if (!isValidCacheResult(method, cacheResult)) {
			throw new RMIInvalidConfigurationException("Cannot cache result of method: " + method);
		}
		forbidden = method.isAnnotationPresent(RMIForbidden.class);
		if (!isValidForbiddenMethod(method, forbidden)) {
			throw new RMIInvalidConfigurationException("Cannot forbid RMI for method: " + method);
		}
		if (cacheResult && forbidden) {
			throw new RMIInvalidConfigurationException("Cannot cache forbidden method: " + method);
		}

		RMIExceptionRethrow excrethrow = method.getAnnotation(RMIExceptionRethrow.class);
		if (excrethrow != null) {
			if (defaultOnFailure) {
				throw new RMIInvalidConfigurationException(
						"Cannot have " + RMIDefaultOnFailure.class.getSimpleName() + " and "
								+ RMIExceptionRethrow.class.getSimpleName() + " on the same method. (" + method + ")");
			}
			this.rmiExceptionRethrowConstructor = getExceptionRethrowConstructor(method, excrethrow.value());
		}

		RMIRedirect redirect = method.getAnnotation(RMIRedirect.class);
		if (redirect != null) {
			//TODO allow exception rethrowing on redirect methods if they're called directly using rmi utils
			if (excrethrow != null) {
				throw new RMIInvalidConfigurationException("Cannot have " + RMIRedirect.class.getSimpleName() + " and "
						+ RMIExceptionRethrow.class.getSimpleName() + " on the same method. (" + method + ")");
			}
			if (defaultOnFailure) {
				throw new RMIInvalidConfigurationException("Cannot have " + RMIDefaultOnFailure.class.getSimpleName()
						+ " and " + RMIRedirect.class.getSimpleName() + " on the same method. (" + method + ")");
			}
			if (cacheResult) {
				throw new RMIInvalidConfigurationException("Cannot cache result of redirected method: " + method);
			}
			String mname = redirect.method();
			if (mname.isEmpty()) {
				mname = method.getName();
			}
			Class<?> enclosing = redirect.type();
			Class<?> declaringclass = method.getDeclaringClass();
			if (enclosing == RMIRedirect.class) {
				enclosing = declaringclass;
			}

			int paramcount = method.getParameterCount();
			Class<?>[] redirectargtypes = new Class<?>[paramcount + 1];
			redirectargtypes[0] = declaringclass;
			System.arraycopy(method.getParameterTypes(), 0, redirectargtypes, 1, paramcount);

			try {
				redirectMethod = enclosing.getDeclaredMethod(mname, redirectargtypes);
				if (!isValidRedirectMethod(method, redirectMethod)) {
					throw new RMIInvalidConfigurationException("Redirect method is not applicable: " + redirectMethod);
				}
			} catch (NoSuchMethodException e) {
//				throw new RMIInvalidConfigurationException("Redirect method specified by annotation not found: " + mname + "("
//						+ StringUtils.join(", ", redirectargtypes, c -> c.getName()) + ") on " + method, e);
				throw new RMIInvalidConfigurationException("Redirect method specified by annotation not found: " + mname
						+ "("
						+ StringUtils.toStringJoin(", ",
								ObjectUtils.transformIterator(ObjectUtils.iterator(redirectargtypes), Class::getName))
						+ ") on " + method, e);
			}
		}
	}

	/**
	 * Gets the write handler for the return value of the method.
	 * <p>
	 * The returned writer will be used when the method is invoked by a remote client.
	 * 
	 * @return The write handler.
	 */
	public RMIObjectWriteHandler getReturnValueWriter() {
		return returnValueWriter;
	}

	/**
	 * Gets the return type of the method.
	 * 
	 * @return The return type.
	 */
	public Class<?> getReturnType() {
		return getExecutable().getReturnType();
	}

	/**
	 * Returns if the default method should be called in case of RMI failure.
	 * 
	 * @return <code>true</code> if the default method should be called.
	 * @see RMIDefaultOnFailure
	 */
	public boolean isDefaultOnFailure() {
		return defaultOnFailure;
	}

	/**
	 * Returns if the method is not allowed to be called through RMI.
	 * <p>
	 * When designing security sensitive applications, if this method returns <code>true</code> on the server side, it
	 * doesn't protect against remote calls issued by the client. I.e. The forbidden method may still be called by a
	 * malicious client.
	 * 
	 * @return <code>true</code> if the method may not be called.
	 * @see RMIForbidden
	 */
	public boolean isForbidden() {
		return forbidden;
	}

	/**
	 * Returns if the method result should be cached for future calls.
	 * 
	 * @return <code>true</code> if the result should be cached.
	 * @see RMICacheResult
	 */
	public boolean isCacheResult() {
		return cacheResult;
	}

	/**
	 * Returns non-<code>null</code> if the RMI method calls should be redirected to a specified method.
	 * 
	 * @return The method to redirect the calls to or <code>null</code>.
	 * @see RMIRedirect
	 */
	public Method getRedirectMethod() {
		return redirectMethod;
	}

	/**
	 * Returns the constructor of the exception which should be used to rethrown RMI errors.
	 * 
	 * @return Non-<code>null</code> if an exception rethrow type was specified.
	 * @see RMIExceptionRethrow
	 */
	public Constructor<? extends Throwable> getRMIExceptionRethrowConstructor() {
		return rmiExceptionRethrowConstructor;
	}

	@Override
	public boolean propertiesEquals(ExecutableTransferProperties<?> other) {
		if (!(other instanceof MethodTransferProperties)) {
			return false;
		}
		if (!super.propertiesEquals(other)) {
			return false;
		}
		MethodTransferProperties methodother = (MethodTransferProperties) other;
		if (cacheResult != methodother.cacheResult)
			return false;
		if (defaultOnFailure != methodother.defaultOnFailure)
			return false;
		if (forbidden != methodother.forbidden)
			return false;
		if (returnValueWriter == null) {
			if (methodother.returnValueWriter != null)
				return false;
		} else if (!returnValueWriter.equals(methodother.returnValueWriter))
			return false;
		if (redirectMethod == null) {
			if (methodother.redirectMethod != null)
				return false;
		} else if (!redirectMethod.equals(methodother.redirectMethod))
			return false;
		if (rmiExceptionRethrowConstructor == null) {
			if (methodother.rmiExceptionRethrowConstructor != null)
				return false;
		} else if (!rmiExceptionRethrowConstructor.equals(methodother.rmiExceptionRethrowConstructor))
			return false;
		return true;
	}

	/**
	 * Creates a new builder instance for this class.
	 * 
	 * @param method
	 *            The method to create the builder for.
	 * @return The builder instance.
	 */
	public static Builder builder(Method method) {
		return new Builder(method);
	}

	/**
	 * Builder class for custom construction of method properties.
	 */
	public static final class Builder {
		protected Method executable;
		protected RMIObjectWriteHandler returnTypeWriter;
		protected RMIObjectWriteHandler[] parametertWriters;
		protected boolean defaultOnFailure;
		protected boolean forbidden;
		protected boolean cacheResult;
		protected Method redirectMethod;
		protected Constructor<? extends Throwable> rmiExceptionRethrowConstructor;

		/**
		 * Creates a properties builder for the given method.
		 * <p>
		 * The annotations on the method are not taken into account.
		 * 
		 * @param method
		 *            The method to create the builder for.
		 */
		public Builder(Method method) {
			Objects.requireNonNull(method, "method");

			this.executable = method;
			this.parametertWriters = new RMIObjectWriteHandler[method.getParameterCount()];
		}

		/**
		 * Sets the writer for the return value.
		 * <p>
		 * The argument writer will be used when the method is invoked by a remote client.
		 * 
		 * @param writer
		 *            The write handler.
		 * @return <code>this</code>
		 */
		public Builder returnWriter(RMIObjectWriteHandler writer) {
			this.returnTypeWriter = writer;
			return this;
		}

		/**
		 * Sets all of write handlers for every parameter.
		 * <p>
		 * The parameter array can contain <code>null</code>, in which case the default write handler will be used.
		 * 
		 * @param writers
		 *            The write handlers for the parameters.
		 * @return <code>this</code>
		 * @throws IndexOutOfBoundsException
		 *             If the parameter array is not the same length as the parameter count.
		 * @throws NullPointerException
		 *             If the parameter array is <code>null</code>.
		 */
		public Builder parameterWriters(RMIObjectWriteHandler[] writers)
				throws IndexOutOfBoundsException, NullPointerException {
			System.arraycopy(writers, 0, parametertWriters, 0, parametertWriters.length);
			return this;
		}

		/**
		 * Sets the write handler for the parameter at the specified index.
		 * 
		 * @param index
		 *            The index of the parameter.
		 * @param writer
		 *            The write handler.
		 * @return <code>this</code>
		 */
		public Builder parameterWriter(int index, RMIObjectWriteHandler writer) {
			parametertWriters[index] = writer;
			return this;
		}

		/**
		 * Sets if the default implementation should be called for the method in case of RMI error.
		 * 
		 * @param defaultonfailure
		 *            <code>true</code> if the default implementation should be called.
		 * @return <code>this</code>
		 * @throws RMIInvalidConfigurationException
		 *             In case of invalid configuration.
		 * @see RMIDefaultOnFailure
		 */
		public Builder defaultOnFailure(boolean defaultonfailure) throws RMIInvalidConfigurationException {
			if (defaultonfailure) {
				if (rmiExceptionRethrowConstructor != null) {
					throw new RMIInvalidConfigurationException(
							"A method cannot call default on failure, and rethrow rmi exceptions at the same time.");
				}
				if (redirectMethod != null) {
					throw new RMIInvalidConfigurationException(
							"Cannot redirect and default on failure with method: " + executable);
				}
			}
			if (!isValidDefaultOnFailure(executable, defaultonfailure)) {
				throw new RMIInvalidConfigurationException(
						"Cannot call default method on failure with method: " + executable);
			}
			this.defaultOnFailure = defaultonfailure;
			return this;
		}

		/**
		 * Sets if this method is callable through RMI.
		 * <p>
		 * When designing security sensitive applications, setting this <code>true</code> on the server side transfer
		 * properties doesn't protect against remote calls issued by the client. I.e. The forbidden method may still be
		 * called by a malicious client.
		 * 
		 * @param forbidden
		 *            <code>true</code> if it may not be called.
		 * @return <code>this</code>
		 * @throws RMIInvalidConfigurationException
		 *             In case of invalid configuration.
		 * @see RMIForbidden
		 */
		public Builder forbidden(boolean forbidden) throws RMIInvalidConfigurationException {
			if (!isValidForbiddenMethod(executable, forbidden)) {
				throw new RMIInvalidConfigurationException("Cannot forbid RMI for method: " + executable);
			}
			if (cacheResult) {
				throw new RMIInvalidConfigurationException("Cannot cache forbidden method: " + executable);
			}
			this.forbidden = forbidden;
			return this;
		}

		/**
		 * Sets the method to redirect RMI calls to.
		 * 
		 * @param method
		 *            The redirected method or <code>null</code> to disable redirection.
		 * @return <code>this</code>
		 * @throws RMIInvalidConfigurationException
		 *             In case of invalid configuration.
		 * @see RMIRedirect
		 */
		public Builder redirect(Method method) throws RMIInvalidConfigurationException {
			if (method != null) {
				if (rmiExceptionRethrowConstructor != null) {
					throw new RMIInvalidConfigurationException(
							"Cannot redirect and rethrow rmi exceptions on the same method. (" + executable + ")");
				}
				if (!isValidRedirectMethod(this.executable, method)) {
					throw new RMIInvalidConfigurationException(
							"Redirect method is not applicable: " + method + " for " + executable);
				}
				if (cacheResult) {
					throw new RMIInvalidConfigurationException(
							"Cannot cache result of redirected method: " + executable);
				}
			}
			this.redirectMethod = method;
			return this;
		}

		/**
		 * Sets if the result of an RMI method call should be cached for future reuse.
		 * 
		 * @param cache
		 *            <code>true</code> if the result should be cached.
		 * @return <code>this</code>
		 * @throws RMIInvalidConfigurationException
		 *             In case of invalid configuration.
		 * @see RMICacheResult
		 */
		public Builder cacheResult(boolean cache) throws RMIInvalidConfigurationException {
			if (!isValidCacheResult(executable, cache)) {
				throw new RMIInvalidConfigurationException("Cannot cache result of method: " + executable);
			}
			if (cache) {
				if (forbidden) {
					throw new RMIInvalidConfigurationException("Cannot cache forbidden method: " + executable);
				}
				if (redirectMethod != null) {
					throw new RMIInvalidConfigurationException(
							"Cannot cache result of redirected method: " + executable);
				}
			}
			this.cacheResult = cache;
			return this;
		}

		/**
		 * Sets the class which to use to rethrow RMI errors.
		 * 
		 * @param rethrowclass
		 *            The exception class to use or <code>null</code> to disable rethrowing.
		 * @return <code>this</code>
		 * @throws RMIInvalidConfigurationException
		 *             In case of invalid configuration.
		 * @see RMIExceptionRethrow
		 */
		public Builder rmiExceptionRethrow(Class<? extends Throwable> rethrowclass)
				throws RMIInvalidConfigurationException {
			if (rethrowclass != null) {
				if (redirectMethod != null) {
					throw new RMIInvalidConfigurationException(
							"Cannot redirect and rethrow rmi exceptions on the same method. (" + executable + ")");
				}
				if (defaultOnFailure) {
					throw new RMIInvalidConfigurationException(
							"A method cannot call default on failure, and rethrow rmi exceptions at the same time.");
				}
				this.rmiExceptionRethrowConstructor = getExceptionRethrowConstructor(executable, rethrowclass);
			} else {
				this.rmiExceptionRethrowConstructor = null;
			}
			return this;
		}

		/**
		 * Sets the exception constructor which to use to rethrow RMI errors.
		 * 
		 * @param rethrowconstructor
		 *            The constructor to create rethrown exceptions or <code>null</code> to disable rethrowing.
		 * @return <code>this</code>
		 * @throws RMIInvalidConfigurationException
		 *             In case of invalid configuration.
		 * @see RMIExceptionRethrow
		 */
		public Builder rmiExceptionRethrow(Constructor<? extends Throwable> rethrowconstructor)
				throws RMIInvalidConfigurationException {
			if (rethrowconstructor != null) {
				if (redirectMethod != null) {
					throw new RMIInvalidConfigurationException(
							"Cannot redirect and rethrow rmi exceptions on the same method. (" + executable + ")");
				}
				if (defaultOnFailure) {
					throw new RMIInvalidConfigurationException(
							"A method cannot call default on failure, and rethrow rmi exceptions at the same time.");
				}
				if (rethrowconstructor.getParameterCount() != 1
						|| rethrowconstructor.getParameterTypes()[0] != Throwable.class
						|| rethrowconstructor.getParameterTypes()[0] != RMIRuntimeException.class) {
					throw new RMIInvalidConfigurationException(
							"RMI exception rethrow class constructor should have 1 parameter with Throwable. " + "("
									+ rethrowconstructor + ")");
				}
				checkCanThrowException(executable, rethrowconstructor.getDeclaringClass());
				this.rmiExceptionRethrowConstructor = rethrowconstructor;
			} else {
				this.rmiExceptionRethrowConstructor = null;
			}
			return this;
		}

		/**
		 * Creates the transfer properties specified by this builder.
		 * 
		 * @return The constructed transfer properties.
		 */
		public MethodTransferProperties build() {
			MethodTransferProperties result = new MethodTransferProperties();
			result.executable = executable;
			result.returnValueWriter = returnTypeWriter == null ? RMIObjectWriteHandler.defaultWriter()
					: returnTypeWriter;
			for (int i = 0; i < parametertWriters.length; i++) {
				if (parametertWriters[i] == null) {
					parametertWriters[i] = RMIObjectWriteHandler.defaultWriter();
				}
			}
			result.parameterWriters = parametertWriters;

			result.defaultOnFailure = this.defaultOnFailure;
			result.forbidden = this.forbidden;
			result.redirectMethod = this.redirectMethod;
			result.cacheResult = this.cacheResult;
			result.rmiExceptionRethrowConstructor = this.rmiExceptionRethrowConstructor;
			return result;
		}
	}

	private static boolean isValidDefaultOnFailure(Method method, boolean defaultonfailure) {
		if (!defaultonfailure) {
			return true;
		}
		if (!method.isDefault()) {
			return false;
		}
		return true;
	}

	private static boolean isValidCacheResult(Method method, boolean cacheresult) {
		if (!cacheresult) {
			return true;
		}
		if (Modifier.isStatic(method.getModifiers())) {
			return false;
		}
		if (method.getReturnType() == void.class) {
			//XXX maybe enable this in the future to have a method called only once?
			return false;
		}
		return true;
	}

	private static <T extends Throwable> Constructor<T> getExceptionRethrowConstructor(Method method,
			Class<T> excrethrow) {
		if (Modifier.isStatic(method.getModifiers())) {
			throw new RMIInvalidConfigurationException("Cannot specify rethrow exception for static method: " + method);
		}
		checkCanThrowException(method, excrethrow);
		//check for available constructor
		try {
			return excrethrow.getConstructor(Throwable.class);
		} catch (NoSuchMethodException | SecurityException e) {
			try {
				return excrethrow.getConstructor(RMIRuntimeException.class);
			} catch (NoSuchMethodException | SecurityException e1) {
				e1.addSuppressed(e);
				throw new RMIInvalidConfigurationException(
						"No appropriate constructor found for exception: " + excrethrow, e1);
			}
		}
	}

	private static <T extends Throwable> void checkCanThrowException(Method method, Class<T> excrethrow) {
		if (!RuntimeException.class.isAssignableFrom(excrethrow) && !Error.class.isAssignableFrom(excrethrow)) {
			//if the exception to rethrow is checked exception, then check if it can be thrown
			if (!canThrowCheckedException(method, excrethrow)) {
				throw new RMIInvalidConfigurationException(
						"Exception cannot be thrown: " + excrethrow + " by method: " + method);
			}
		}
	}

	private static boolean canThrowCheckedException(Method method, Class<? extends Throwable> excrethrow) {
		Class<?>[] exceptions = method.getExceptionTypes();
		for (Class<?> thrownexc : exceptions) {
			if (thrownexc.isAssignableFrom(excrethrow)) {
				//a thrown exception is supertype of the rethrown exception
				return true;
			}
		}
		return false;
	}

	private static boolean isValidRedirectMethod(Method basemethod, Method redirectmethod) {
		boolean isstatic = Modifier.isStatic(redirectmethod.getModifiers());
		if (!isstatic) {
			return false;
		}
		if (Modifier.isStatic(basemethod.getModifiers())) {
			return false;
		}

		Class<?>[] baseparams = basemethod.getParameterTypes();
		Class<?>[] redirectparams = redirectmethod.getParameterTypes();
		if (redirectparams.length != baseparams.length + 1) {
			return false;
		}
		if (!redirectparams[0].isAssignableFrom(basemethod.getDeclaringClass())) {
			return false;
		}
		if (!ArrayUtils.arrayRangeEquals(baseparams, 0, redirectparams, 1, baseparams.length)) {
			return false;
		}
		if (basemethod.getReturnType() != redirectmethod.getReturnType()) {
			return false;
		}

		Class<?>[] redirectexceptions = redirectmethod.getExceptionTypes();
		if (!ObjectUtils.isNullOrEmpty(redirectexceptions)) {
			Class<?>[] baseexceptions = basemethod.getExceptionTypes();
			outer:
			for (Class<?> redexc : redirectexceptions) {
				if (RuntimeException.class.isAssignableFrom(redexc) || Error.class.isAssignableFrom(redexc)) {
					continue;
				}
				//redex is a checked exception (Throwable or Exception subclass)
				for (Class<?> baseexc : baseexceptions) {
					if (baseexc.isAssignableFrom(redexc)) {
						//the exception thrown by the redirected method is a subclass (or same) as a declared exception on the base method
						continue outer;
					}
				}
				//the exception on the redirect method was not declared on the base method
				return false;
			}
		}

		return true;
	}

	private static boolean isValidForbiddenMethod(Method method, boolean forbidden) {
		return !forbidden || !Modifier.isStatic(method.getModifiers());
	}

	/**
	 * Creates a return value write handler for the given method.
	 * <p>
	 * The annotations on the method is parsed and the write handler is determined.
	 * 
	 * @param method
	 *            The method to create the return value write handler for.
	 * @return The write handler.
	 * @throws RMIInvalidConfigurationException
	 *             In case of invalid configuration.
	 */
	public static RMIObjectWriteHandler createWriteHandlerForMethod(Method method)
			throws RMIInvalidConfigurationException {
		RMIWriter writerannot = method.getAnnotation(RMIWriter.class);
		boolean serializepresent = method.isAnnotationPresent(RMISerialize.class);
		boolean remotepresent = method.isAnnotationPresent(RMIRemote.class);
		RMIWrap wrap = method.getAnnotation(RMIWrap.class);

		Class<?> returntype = method.getReturnType();
		return createWriteHandlerWithAnnotations(writerannot, serializepresent, remotepresent, wrap, returntype);
	}
}
