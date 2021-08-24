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

import java.lang.ref.Reference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;

import saker.apiextract.api.ExcludeApi;
import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMICallForbiddenException;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;

/**
 * Common superclass for all proxy objects which are present in the RMI runtime.
 * <p>
 * This class is only public for generated proxy object to be able to access it by visibility. <br>
 * <b>Warning: Subclassing and using this class directly might break RMI functionality.</b>
 */
@ExcludeApi
public abstract class RemoteProxyObject {
	//Bytecode for the proxy objects are generated in the class ProxyGenerator.
	protected final Reference<? extends RMIVariables> variables;
	protected final int remoteId;

	protected RemoteProxyObject(Reference<? extends RMIVariables> variables, int remoteId) {
		this.variables = variables;
		this.remoteId = remoteId;
	}

	protected static final Object invokeRedirectInternal(RemoteProxyObject remoteobject, MethodTransferProperties m,
			Object[] args) throws Throwable {
		try {
			return RMIVariables.invokeRedirectMethod(remoteobject, m.getRedirectMethod(), args);
		} catch (InvocationTargetException e) {
			throw e.getTargetException();
		} finally {
			reachabilityFence(remoteobject);
		}
	}

	protected static final Throwable forbiddenThrowableInternal(MethodTransferProperties m) {
		RMIRuntimeException e = new RMICallForbiddenException(m.getExecutable().toString());
		return getExceptionRethrowException(m, e);
	}

	protected static final Object callMethodInternal(RemoteProxyObject remoteobject, MethodTransferProperties m,
			Object[] args) throws Throwable {
		try {
			return getCheckVariables(remoteobject).invokeAllowedNonRedirectMethod(remoteobject.remoteId, m, args);
		} catch (RMIRuntimeException e) {
			throw getExceptionRethrowException(m, e);
		} catch (InvocationTargetException e) {
			throw e.getTargetException();
		} finally {
			reachabilityFence(remoteobject);
		}
	}

	protected static final Object callMethodInternal(RemoteProxyObject remoteobject, MethodTransferProperties m,
			Object[] args, RMIStatistics statistics) throws Throwable {
		try {
			RMIVariables vars = getCheckVariables(remoteobject);
			long nanos = System.nanoTime();
			try {
				return vars.invokeAllowedNonRedirectMethod(remoteobject.remoteId, m, args);
			} finally {
				statistics.recordMethodCall(m.getExecutable(), nanos, System.nanoTime());
				reachabilityFence(remoteobject);
			}
		} catch (RMIRuntimeException e) {
			throw getExceptionRethrowException(m, e);
		} catch (InvocationTargetException e) {
			throw e.getTargetException();
		}
	}

	protected static final RMIVariables getVariables(RemoteProxyObject remoteobject) {
		return remoteobject.variables.get();
	}

	protected static final Object callNonRedirectMethodFromStaticDelegate(MethodTransferProperties method,
			RemoteProxyObject proxy, Object[] args) throws Throwable {
		try {
			RMIVariables vars = getCheckVariables(proxy);
			return vars.invokeRemoteMethodInternal(proxy.remoteId, method, args);
		} finally {
			reachabilityFence(proxy);
		}
	}

	protected static final Object callNonRedirectMethodFromStaticDelegate(MethodTransferProperties method,
			RemoteProxyObject proxy, Object[] args, RMIStatistics statistics) throws Throwable {
		RMIVariables vars = getCheckVariables(proxy);
		long nanos = System.nanoTime();
		try {
			return vars.invokeRemoteMethodInternal(proxy.remoteId, method, args);
		} finally {
			statistics.recordMethodCall(method.getExecutable(), nanos, System.nanoTime());
			reachabilityFence(proxy);
		}
	}

	static final RMIVariables getCheckVariables(RemoteProxyObject remoteobject) {
		RMIVariables variables = remoteobject.variables.get();
		if (variables == null) {
			throw new RMICallFailedException("RMIVariables not found. (RMI connection closed?)");
		}
		return variables;
	}

	private static final Throwable getExceptionRethrowException(MethodTransferProperties m, RMIRuntimeException e) {
		Constructor<? extends Throwable> excrethrowconstructor = m.getRMIExceptionRethrowConstructor();
		if (excrethrowconstructor != null) {
			try {
				return ReflectUtils.invokeConstructor(excrethrowconstructor, e);
			} catch (Exception e1) {
				e.addSuppressed(e1);
			}
		}
		return new RemoteInvocationRMIFailureException(e);
	}

	static final void reachabilityFence(@SuppressWarnings("unused") Object o) {
		//this method is called in functions that work with remote proxy objects. As their garbage collection
		//can cause remote references to be released, it needs to be called after the network requests.
		ObjectUtils.reachabilityFence(o);
	}

	protected static final class RMICacheHelper {
		private static final Object RESULT_NOT_READY = new Object();

		private static final AtomicReferenceFieldUpdater<RMICacheHelper, Object> ARFU_result = AtomicReferenceFieldUpdater
				.newUpdater(RMICacheHelper.class, Object.class, "result");

		private volatile Object result = RESULT_NOT_READY;
		//the lock must be reentrant as the method may call itself recursively
		private final ReentrantLock lock = new ReentrantLock();

		public RMICacheHelper() {
		}

		public Object call(RemoteProxyObject proxy, MethodTransferProperties method, Object[] args) throws Throwable {
			Object r = this.result;
			if (r != RESULT_NOT_READY) {
				return r;
			}
			lock.lock();
			try {
				r = this.result;
				if (r != RESULT_NOT_READY) {
					return r;
				}
				Object res = RemoteProxyObject.callMethodInternal(proxy, method, args);
				//the following CAS will always succeed as we're in a locked section
				ARFU_result.compareAndSet(this, RESULT_NOT_READY, res);
				return res;
			} finally {
				lock.unlock();
			}
		}

		public Object call(RemoteProxyObject proxy, MethodTransferProperties method, Object[] args,
				RMIStatistics statistics) throws Throwable {
			Object r = this.result;
			if (r != RESULT_NOT_READY) {
				return r;
			}
			lock.lock();
			try {
				r = this.result;
				if (r != RESULT_NOT_READY) {
					return r;
				}
				Object res = RemoteProxyObject.callMethodInternal(proxy, method, args, statistics);
				//the following CAS will always succeed as we're in a locked section
				ARFU_result.compareAndSet(this, RESULT_NOT_READY, res);
				return res;
			} finally {
				lock.unlock();
			}
		}
	}

	protected static final class RemoteInvocationRMIFailureException extends Throwable {
		private static final long serialVersionUID = 1L;

		public RemoteInvocationRMIFailureException(RMIRuntimeException cause) {
			super(null, cause, false, false);
		}
	}

}
