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
package saker.build.thirdparty.saker.rmi.annot.invoke;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import saker.build.thirdparty.saker.rmi.util.RMIUtils;

/**
 * Annotated methods will be redirected to the specified method when called on a remote proxy.
 * <p>
 * This annotation can be used to handle direct calls to a method on a remote proxy, and modify the behavior of it.
 * <p>
 * The specified method needs to have the declaring interface as a first parameter, and exactly the same parameter types
 * as the subject method. The method return types must be exactly the same as well. The specified method must be static.
 * <p>
 * If a call is issued to the remote method then it will be redirected to the specified method with the proxy object as
 * the first parameter and the method parameters forwarded to the specified method.
 * <p>
 * The first parameter is the proxy object which is an instance of <code>RemoteProxyObject</code>. The method
 * {@link RMIUtils#invokeRedirectRemoteMethod} can be used to bypass this annotation and directly call the redirected
 * method on the remote object.
 * <p>
 * The default values of the annotation specifies a static method with the same name as the subject method and declared
 * in the same interface as the subject method. Specify {@link #type()} to denote a method in a different class, and use
 * {@link #method()} to specify a different redirect method name.
 * <p>
 * Simple usage example:
 * 
 * <pre>
 * // The function call is redirected to a static method which parses the parameter String.
 * interface Stub {
 * 	&#64;RMIRedirect(type = Redirects.class, method = "functionRedirect")
 * 	public int function(String param);
 * }
 * 
 * class Redirects {
 * 	public static int functionRedirect(Stub s, String param) {
 * 		return Integer.parseInt(param);
 * 	}
 * }
 * </pre>
 * <p>
 * Invoking redirected method:
 * 
 * <pre>
 * interface Stub {
 * 	&#64;RMIRedirect
 * 	public int function(String param);
 * 
 * 	public static int function(Stub s, String param) {
 * 		//RMIUtils is used to invoke the redirected method, which bypasses it.
 * 		//getFunctionMethod() gets the java.lang.reflect.Method instance for Stub.function(String)
 * 		//    (This method should be cached for performance in a real implementation.)
 * 		//The stub and a modified parameter is passed to the invoker function.
 * 		try {
 * 			return RMIUtils.invokeRedirectRemoteMethod(getFunctionMethod(), s, param + "-modified");
 * 		} catch (InvocationTargetException e) {
 * 			//handle e.getTargetException() exception thrown by the remote implementation
 * 		}
 * 	}
 * }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RMIRedirect {
	/**
	 * The declaring type of the redirect target method.
	 * 
	 * @return The enclosing type of the specified method. Same as the annotated method declaring type by default.
	 */
	public Class<?> type() default RMIRedirect.class;

	/**
	 * The name of the redirect target method.
	 * 
	 * @return The name of the method to call. Same as the annotated method by default.
	 */
	public String method() default "";
}
