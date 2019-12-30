package saker.build.thirdparty.saker.rmi.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;

/**
 * API utilities for the RMI runtime.
 */
@PublicApi
public class RMIUtils {
	private RMIUtils() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Calls a remote method on an RMI proxy object with the specified arguments and without any redirection.
	 * <p>
	 * This method only succeeds if the remote call succeeds. If the RMI configuration forbids the call of this method
	 * then the request will fail. <br>
	 * Default implementations are not called, call results are not cached, redirections are not applied, and exceptions
	 * are not rethrown as a different type.
	 * <p>
	 * If the specified method is not subject of redirection, then this method will throw an
	 * {@link RMICallFailedException}.
	 * 
	 * @param m
	 *            The method to call.
	 * @param remoteobject
	 *            The remote object to execute the call on.
	 * @param args
	 *            The arguments for the method call.
	 * @return The result of the invocation.
	 * @throws InvocationTargetException
	 *             If the method implementation throws an exception.
	 * @throws RMIRuntimeException
	 *             In case of RMI error.
	 */
	public static Object invokeRedirectRemoteMethod(Method m, Object remoteobject, Object... args)
			throws InvocationTargetException, RMIRuntimeException {
		try {
			Class<?> c = remoteobject.getClass();
			Method callermethod = c.getDeclaredMethod("0rmi_nonredirect$" + m.getName(), m.getParameterTypes());
			return callermethod.invoke(remoteobject, args);
		} catch (InvocationTargetException e) {
			throw e;
		} catch (NoSuchMethodException e) {
			throw new RMICallFailedException("Method is not redirected, or forbidden. (" + m + ")", e);
		} catch (Exception e) {
			throw new RMICallFailedException(e);
		}
	}
}
