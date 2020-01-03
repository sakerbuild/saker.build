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
package saker.build.util.data;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import saker.apiextract.api.PublicApi;
import saker.build.task.BuildTargetTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.dependencies.CommonTaskOutputChangeDetector;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.ComposedStructuredTaskResult;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.StructuredListTaskResult;
import saker.build.task.utils.StructuredMapTaskResult;
import saker.build.task.utils.StructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectTypes;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.util.data.annotation.ConverterConfiguration;
import saker.build.util.data.collection.AdaptingCollection;
import saker.build.util.data.collection.AdaptingEntry;
import saker.build.util.data.collection.AdaptingIterable;
import saker.build.util.data.collection.AdaptingList;
import saker.build.util.data.collection.AdaptingMap;
import saker.build.util.data.collection.AdaptingRandomAccessList;
import saker.build.util.data.collection.AdaptingSet;
import saker.build.util.data.collection.ProxyCollection;
import saker.build.util.data.collection.ProxyIterable;
import saker.build.util.data.collection.ProxyList;
import saker.build.util.data.collection.ProxyListArray;
import saker.build.util.data.collection.ProxyMap;

/**
 * Utility class providing functionality for converting between different types of objects.
 * <p>
 * The main purpose of this class to provide methods for converting between different types. This functionality is
 * required, as structures defined in build scripts might not be directly representable as Java objects. Furthermore, it
 * may be necessary to convert between different versions of a class to avoid linkage errors.
 * <p>
 * The class can provide the following conversional functionalities:
 * <ul>
 * <li>Converting between {@link String} and primitive types.</li>
 * <li>Converting to arrays, or collections.</li>
 * <li>Converting to generic types in a limited manner.</li>
 * <li>Converting build system related {@linkplain StructuredTaskResult structured task results} and
 * {@linkplain BuildTargetTaskResult build target task results}.</li>
 * <li>Converting to {@link Enum enums}.</li>
 * <li>Enclosing {@link Map} instances into interfaces.</li>
 * <li>Adapting different interface class versions for compatibility conflicts.</li>
 * <li>Extending the conversion mechanism.</li>
 * <li>Converting objects based on their own declared methods.</li>
 * </ul>
 * Unless otherwise noted, all converted objects that conversion functions return in this class should be treated as
 * immutable/unmodifiable, meaning, modifications to the returned objects do not propagate back to the converted value.
 * <p>
 * Calling functions on the returned objects from the conversion methods may throw {@link ConversionFailedException} at
 * any time. This usually means that conversion of some nested elements failed.
 * <p>
 * Calling {@link #equals(Object)} and {@link #hashCode()} may not work properly on the returned converted objects, and
 * clients should not rely on them. If they want to use these functions, they should clone the data contents of the
 * converted objects into a representation of their own and use those for comparions.
 * <p>
 * Converting to primitive types works in the following way:
 * <ol>
 * <li>If the value is a task result, it will be resolved.</li>
 * <li>If the target type is {@link Boolean} then the value will be converted to string, and
 * {@link Boolean#valueOf(String)} will be used.</li>
 * <li>If the target type is {@link Character}, then the value will be converted to string, and the first character is
 * returned. If the string is longer than 1 character, an exception is thrown.</li>
 * <li>If the value is a {@link Number}, then the appropriate {@link Number} function will be called to convert to the
 * target type. E.g. {@link Number#intValue()}.</li>
 * <li>Else the value is converted to string, and {@link Long#valueOf(String)} or {@link Double#valueOf(String)} will be
 * used to parse it (based on the target type precision). Then the above conversion function is called as in
 * {@link Number} conversion.</li>
 * <li>In case of any format exceptions, a {@link ConversionFailedException} is thrown.</li>
 * </ol>
 * Converting to arrays or collections work in a way that every element of the value should be converted to the target
 * element type. This usually requires generic information about the collections to be present. The class allows
 * wrapping a single value into singleton collections/arrays. The conversion between collection types are usually
 * delayed, meaning that the element conversions are only executed when the actual elements are accessed. The returned
 * collections are generally unmodifiable.
 * <p>
 * Users should avoiding conversions to the type {@link Set} as the returned object might violate the requirements of
 * that class. As element conversions are delayed, it is possible that there are elements in the returned set that
 * equal. It is recommended that users convert to {@link Collection} or {@link List} classes when they want to use
 * collections. They should handle the case of duplicates gracefully.
 * <p>
 * Arrays and collections are convertible vice-versa. The class is able to handle arrays of generic type, by converting
 * each array element to the generic component type.
 * <p>
 * The class is able to convert {@link StructuredListTaskResult} to collection instances.
 * <p>
 * For collections, {@linkplain Map maps} can only be converted to other maps with different generic arguments. The
 * converted maps are not recommended do be accessed via {@link Map#get(Object)}, but they only should be enumerated.
 * The class is able to convert {@link StructuredMapTaskResult} to map instances.
 * <p>
 * When converting to collection or map types, it is recommended to only use the interfaces as the targe type, not the
 * implementation classes. I.e. use {@link List} as a target type to convert to a list, but not {@link ArrayList}.
 * <p>
 * <p>
 * The generic type handling during conversion is limited to collections and generic arrays. Any type variables and
 * wildcards are not kept as side information after the conversion is done. The class forbids conversion to a target
 * type of instance {@link TypeVariable}, but may allow to {@link WildcardType}, when the wildcard contains only a
 * single upper bound.
 * <p>
 * When the class encounters {@link StructuredTaskResult} instances to convert, it may resolve the value immediately, or
 * delay the resolution of the value until it is accessed. This can result in less dependency reporting for tasks. E.g.
 * if a {@link StructuredMapTaskResult} is converted to a {@link Map}, the dependencies to the value tasks are only
 * installed when their values are actually accessed, which may result in less dependencies reported towards the build
 * system. If no {@link TaskResultResolver} is specified during conversion, a {@link ConversionFailedException} is most
 * likely to be thrown.
 * <p>
 * Converting to enumeration values works by converting the value to string, and calling
 * {@link Enum#valueOf(Class, String)} for it.
 * <p>
 * The class supports enclosing {@link Map} instances into a specified interface target type. This works by creating a
 * {@link Proxy} object as a result of conversion, and resolving the elements of the map when a method is called on it.
 * The proxy object is defined in the {@linkplain ConversionContext#getBaseClassLoader() base classloader} for the
 * conversion.
 * <p>
 * Interface wrapped maps will look up the keys on the value map based on the method called on the proxy object. If the
 * method has no arguments, and has the format <code>get&lt;FieldName&gt;</code> or <code>get_&lt;FieldName&gt;</code>
 * then the value for the string <code>FieldName</code> will be returned from the call. The value will be converted by
 * the rules of this class to the generic return type of the method. If the wrap interface is an annotation type, then
 * the key name will be exactly the same as the method name. If no key was found for the field name, then the default
 * implementation for the interface method will be returned (if any).
 * <p>
 * If a method with a single String argument is called, then a two phase lookup will happen, first for the key specified
 * by the method name, and second on the resolved value for the argument key. E.g. if <code>getField(String)</code> is
 * called with <code>"key"</code> argument, then the value for the key <code>"Field"</code> will be resolved, and if it
 * is present, then the value for <code>"key"</code> will be resolved on the previous result. After that, the result
 * will be converted to the return type of the method.
 * <p>
 * If all the previous lookups fail, the method call will throw an {@link UnsupportedOperationException}.
 * <p>
 * The interface wrapped maps are immutable, and their backing map instances will be copied during instantiation.
 * <p>
 * If the target type is an interface, and the value implements an interface with the same qualified name, but is not
 * assignable to the target type, then the class executes interface adapting conversion. This can happen when different
 * class versions of the same interface is loaded in the JVM, and conversion between them is requested. As they are
 * loaded from different classloaders, they are not assignable to each other, however they should be convertible to each
 * other, as they represent the same class. This scenario can happen if the interface evolves with different releases of
 * a library, and due to different dependencies for the runtime, they both need to be loaded. In this case interface
 * adapting conversion is done.
 * <p>
 * During the above conversion the value object will be wrapped into a {@link Proxy} that implements the interface in
 * the base classloader, and forwards calls to the actual object which implements the interface from a different
 * classloader. The method calls are forwarded, and the passed arguments and return type will be adapted as well.
 * <b>Important:</b> For this to properly work, make sure to only use interfaces as argument and return types for an
 * interface that might be subject to adaptation. Collection interfaces will be automatically adapted with their generic
 * types, but other generic interfaces may not be supported.
 * <p>
 * The class allows extending the conversion mechanism via the {@link DataConverter}, {@link ConversionContext}, and
 * related classes. Conversion can be initiated based on a field or method. In this case the
 * {@link ConverterConfiguration} annotations on the given element will be taken into account to specialize the
 * conversion mechanism for that passed value.
 * <p>
 * The target class can also be used to specialize the conversion by declaring a static method with the name
 * <code>valueOf</code>. The <code>valueOf</code> methods should have the same return type as the class they are
 * declared in. Only the methods which have an assignable return type to the declaring class will be considered.
 * <p>
 * If the value to convert is an instance of {@link StructuredTaskResult}, then the class will search for a method
 * <code>valueOf({@link StructuredTaskResult}, {@link ConversionContext})</code> and use it to convert if available. In
 * other cases the <code>valueOf</code> methods will be listed in the target type, and the one with the closes matching
 * parameter type will be chosen based on the value type. I.e. If an Integer is being converted to a type,
 * <code>valueOf(Integer)</code> will have priority over <code>valueOf(Number)</code>.
 * <p>
 * If any <code>valueOf</code> method conversions fail, the other applicable methods will be also tried to execute the
 * conversion.
 * <p>
 * The converted values can also specify the conversions by declaring a no-arg public method in the
 * <code>to&lt;target-type-simple-name&gt;()</code> format. The simple name of the target type will be retrieved, and
 * the method prefixed by the phrase <code>"to"</code> will be looked up. If the return type of that method is
 * assignable to the target type, then it will be tried to execute the conversion.
 */
@PublicApi
public class DataConverterUtils {
	//TODO test converter configurations on methods

	private static class MappedInterfaceInvocationHandler implements InvocationHandler {
		private static final ParameterizedType MAP_STRING_OBJECT_PARAMETERIZED_TYPE = ReflectTypes
				.makeParameterizedType(Map.class, String.class, Object.class);

		private static final Object NON_EXIST_DEFAULT_VALUE = new Object();

		protected TaskResultResolver taskResultResolver;
		protected Map<String, ?> map;
		protected Class<?> targetClass;
		/**
		 * Indicating, that a toString call is in progress<br>
		 * StackOverFlowError can happen, when the map contains the proxy itself
		 */
		private transient boolean callingToString = false;

		public MappedInterfaceInvocationHandler(TaskResultResolver taskresultresolver, Map<String, ?> map,
				Class<?> targetClass) {
			this.taskResultResolver = taskresultresolver;
			this.map = map;
			this.targetClass = targetClass;
		}

		protected String getGetterMethodFieldName(String methodname) {
			if (methodname.startsWith("get")) {
				// if get_ then exclude starting _
				return methodname.substring(methodname.charAt(3) == '_' ? 4 : 3);
			}
			return null;
		}

		@Override
		public synchronized Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			int arglen = args == null ? 0 : args.length;
			String methodname = method.getName();
			switch (arglen) {
				case 0: {
					Object pre = zeroArgumentPreGetter(proxy, methodname, method, args);
					if (pre != null) {
						return pre;
					}
					String getterfieldname = getGetterMethodFieldName(methodname);
					if (getterfieldname != null) {
						@SuppressWarnings({ "unchecked", "rawtypes" })
						Object mapval = ((Map) map).getOrDefault(getterfieldname, NON_EXIST_DEFAULT_VALUE);
						if (mapval == NON_EXIST_DEFAULT_VALUE) {
							return invokeDefaultMethod(proxy, method);
						}
						return convert(taskResultResolver, mapval, method);
					}
					break;
				}
				case 1: {
					if (methodname.equals("equals")) {
						Object param = args[0];
						return callEquals(proxy, param);
					}
					if (method.getParameterTypes()[0] == String.class) {
						String getterfieldname = getGetterMethodFieldName(methodname);
						if (getterfieldname != null) {
							//the methodname is in a format of getXYZ(String)
							//    try to look up the value with the parameter key and in the field XYZ
							@SuppressWarnings({ "unchecked", "rawtypes" })
							Object mapval = ((Map) map).getOrDefault(getterfieldname, NON_EXIST_DEFAULT_VALUE);
							if (mapval == NON_EXIST_DEFAULT_VALUE) {
								return invokeDefaultMethod(proxy, method);
							}
							//XXX we could optimize by avoiding conversion to map, and examine the result value directly
							@SuppressWarnings("unchecked")
							Map<String, ?> keymap = (Map<String, ?>) convert(mapval,
									MAP_STRING_OBJECT_PARAMETERIZED_TYPE);
							return convert(taskResultResolver, keymap.get(args[0]), method);
						}
					}
					break;
				}
				default: {
					break;
				}
			}

			if (method.isDefault()) {
				return ReflectUtils.invokeDefaultMethodOn(method, proxy, args);
			}

			// probably Object method
			throw new UnsupportedOperationException("Failed to call method: " + method);
		}

		/**
		 * Presents an opportunity to hijack the method invocation before the getter functions are invoked.
		 * 
		 * @param proxy
		 *            The proxy itself.
		 * @param methodname
		 *            The method name.
		 * @param method
		 *            The invoked method.
		 * @param args
		 *            The arguments provided for method invocation.
		 * @return Non-null if the method invocation was handled.
		 */
		protected Object zeroArgumentPreGetter(Object proxy, String methodname, Method method, Object[] args) {
			if (methodname.equals("toString")) {
				return callToString();
			}
			if (methodname.equals("hashCode")) {
				return callHashCode(proxy);
			}
			return null;
		}

		protected Object callToString() {
			if (callingToString) {
				return "(this Map)";
			}
			try {
				callingToString = true;
				return map.toString();
			} finally {
				callingToString = false;
			}
		}

		protected Object callEquals(Object proxy, Object param) {
			return proxy == param;
		}

		protected Object invokeDefaultMethod(Object proxy, Method method) throws Throwable {
			if (method.isDefault()) {
				return ReflectUtils.invokeDefaultMethodOn(method, proxy);
			}
			return null;
		}

		/**
		 * Calculate the hashcode for the proxy object.
		 * 
		 * @param proxy
		 *            The proxy itself.
		 * @return The resulting hashcode.
		 */
		protected Object callHashCode(Object proxy) {
			return System.identityHashCode(proxy);
		}
	}

	private static class AnnotationMappedInterfaceInvocationHandler extends MappedInterfaceInvocationHandler {

		public AnnotationMappedInterfaceInvocationHandler(TaskResultResolver taskresultresolver, Map<String, ?> map,
				Class<? extends Annotation> targetClass) {
			super(taskresultresolver, map, targetClass);
		}

		@Override
		protected Object zeroArgumentPreGetter(Object proxy, String methodname, Method method, Object[] args) {
			if ("annotationType".equals(methodname)) {
				return targetClass;
			}
			return super.zeroArgumentPreGetter(proxy, methodname, method, args);
		}

		@Override
		protected String getGetterMethodFieldName(String methodname) {
			return methodname;
		}

		@Override
		protected Object callToString() {
			StringBuilder sb = new StringBuilder();
			sb.append("@");
			sb.append(targetClass.getName());
			sb.append("(");
			Method[] methods = targetClass.getDeclaredMethods();
			outer:
			for (int i = 0; i < methods.length; i++) {
				if (i > 0) {
					sb.append(", ");
				}
				Method m = methods[i];
				String methodname = m.getName();
				sb.append(methodname);
				sb.append("=");
				for (Entry<?, ?> entry : map.entrySet()) {
					if (methodname.equals(Objects.toString(entry.getKey(), null))) {
						Object val = convert(taskResultResolver, entry.getValue(), m);
						sb.append(ReflectUtils.annotationValueToString(val));
						continue outer;
					}
				}
				sb.append(ReflectUtils.annotationValueToString(m.getDefaultValue()));
			}
			sb.append(")");
			return sb.toString();
		}

		@Override
		protected Object callHashCode(Object proxy) {
			int result = 0;
			for (Method m : targetClass.getDeclaredMethods()) {
				String methodname = m.getName();
				Object mapval = map.get(methodname);
				if (mapval != null) {
					Object val = convert(taskResultResolver, mapval, m);
					result += (127 * methodname.hashCode()) ^ ReflectUtils.annotationValueHashCode(val);
					continue;
				}
				result += (127 * methodname.hashCode()) ^ ReflectUtils.annotationValueHashCode(m.getDefaultValue());
			}
			return result;
		}

		@Override
		protected Object callEquals(Object proxy, Object param) {
			if (proxy == param) {
				return true;
			}
			if (!targetClass.isInstance(param)) {
				return false;
			}
			for (Method m : targetClass.getDeclaredMethods()) {
				String methodname = m.getName();
				Object mapval = map.get(methodname);
				if (mapval != null) {
					Object val = convert(taskResultResolver, mapval, m);
					if (!isAnnotationValueEqualsWithOther(param, m, val)) {
						return false;
					}
					continue;
				}
				//our value not found, use default
				if (!isAnnotationValueEqualsWithOther(param, m, m.getDefaultValue())) {
					return false;
				}
			}
			return true;
		}

		private static boolean isAnnotationValueEqualsWithOther(Object param, Method m, Object val)
				throws AssertionError {
			Object hisValue;
			try {
				hisValue = ReflectUtils.invokeMethod(param, m);
			} catch (InvocationTargetException e) {
				e.printStackTrace();
				return false;
			} catch (IllegalAccessException e) {
				throw new AssertionError(e);
			}
			if (!ReflectUtils.annotationValuesEqual(val, hisValue)) {
				return false;
			}
			return true;
		}

		@Override
		protected Object invokeDefaultMethod(Object proxy, Method method) throws Throwable {
			return method.getDefaultValue();
		}
	}

	private static final class InterfaceAdapterInvocationHandler implements InvocationHandler, Externalizable {
		private static final long serialVersionUID = 1L;

		protected Object value;

		/**
		 * For {@link Externalizable}.
		 */
		public InterfaceAdapterInvocationHandler() {
		}

		public InterfaceAdapterInvocationHandler(Object value) {
			this.value = value;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			Class<?> methoddeclaringclass = method.getDeclaringClass();
			if (!methoddeclaringclass.isInterface() && methoddeclaringclass != Object.class) {
				throw new UnsupportedOperationException(
						"Failed to call non-interface and non-Object method: " + method);
			}
			int arglen = args == null ? 0 : args.length;
			String methodname = method.getName();
			Class<? extends Object> valueclass = value.getClass();
			Class<?> correspondinginterface = ReflectUtils.findTypeWithNameInHierarchy(valueclass,
					methoddeclaringclass.getName());
			Method targetmethod = null;
			Class<?>[] methodparamtypes = method.getParameterTypes();
			if (arglen == 0) {
				try {
					targetmethod = correspondinginterface.getMethod(methodname);
				} catch (NoSuchMethodException e) {
				}
			} else {
				methodfinderloop:
				for (Method targetm : correspondinginterface.getMethods()) {
					if (targetm.getParameterCount() != arglen || !targetm.getName().equals(methodname)) {
						continue;
					}
					Class<?>[] targetparamtypes = targetm.getParameterTypes();

					for (int i = 0; i < arglen; i++) {
						if (!targetparamtypes[i].getName().equals(methodparamtypes[i].getName())) {
							//the target method have a different class name as a parameter, not the same methods
							continue methodfinderloop;
						}
					}
					//all the parameter type names equals
					targetmethod = targetm;
					break;
				}
			}
			if (targetmethod != null) {
				//a target method with same parameter type names was found
				Class<?>[] targetparamtypes = targetmethod.getParameterTypes();
				Object[] nargs = args == null ? null : new Object[args.length];
				for (int i = 0; i < targetparamtypes.length; i++) {
					if (args[i] == null) {
						//do not care about adapting this parameter, as it is null
						continue;
					}
					if (args[i] == proxy) {
						nargs[i] = value;
						continue;
					}
					Class<?> targetparamtype = targetparamtypes[i];
					if (targetparamtype.isPrimitive()) {
						nargs[i] = args[i];
					} else {
						Object adaptedarg = adaptInterface(valueclass.getClassLoader(), args[i]);
						if (!targetparamtype.isInstance(adaptedarg)) {
							throw new UnsupportedOperationException("Failed to adapt method parameter: " + args[i]
									+ " to " + targetparamtype + " for method: " + method);
						}
						nargs[i] = adaptedarg;
					}
				}
				Object returnvalue = targetmethod.invoke(value, nargs);
				if (returnvalue == null) {
					return null;
				}
				if (returnvalue == value) {
					return proxy;
				}
				Class<?> returntype = method.getReturnType();
				//adapt back
				Object adaptedreturnval = adaptInterface(proxy.getClass().getClassLoader(), returnvalue);
				if (!returntype.isPrimitive() && !returntype.isInstance(adaptedreturnval)) {
					throw new UnsupportedOperationException("Failed to adapt method return value: " + returnvalue
							+ " to " + returntype + " for method: " + method);
				}
				return adaptedreturnval;
			}
			//target method is null
			if (method.isDefault()) {
				return ReflectUtils.invokeDefaultMethodOn(method, proxy, args);
			}
			throw new UnsupportedOperationException("Failed to adapt method, not found on converted object: " + method);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(value);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = in.readObject();
		}
	}

	/**
	 * Adapts the interfaces of the specified object using the rules of this class.
	 * <p>
	 * This method examines if all of the interfaces of the object are present in the specified classloader. If not,
	 * then a proxy object will be created in the specified classloader with the adapted interfaces.
	 * <p>
	 * <i>Note: Adapting parameterized interfaces is not yet supported, unless they are {@link Collection},
	 * {@link List}, {@link Set}, {@link Map} or {@link Iterable}.</i>
	 * <p>
	 * If the argument object is already adapted, its adaptation will be unwrapped.
	 * <p>
	 * If the method fails to properly adapt the argument, or it doesn't need adaptation, the argument object will be
	 * returned without modification.
	 * 
	 * @param classloader
	 *            The classloader to adapt the interfaces for.
	 * @param obj
	 *            The object to adapt the interfaces of.
	 * @return The adapted object. This might be identity same to the argument object if it needs no adaptation.
	 */
	public static Object adaptInterface(final ClassLoader classloader, Object obj) {
		//TODO rename this method, as it adapts enumerations too?
		if (obj == null) {
			return null;
		}
		Class<? extends Object> objclass = obj.getClass();
		if (objclass == Class.class) {
			try {
				return Class.forName(((Class<?>) obj).getName(), false, classloader);
			} catch (ClassNotFoundException e) {
				return obj;
			}
		}
		if (Proxy.isProxyClass(objclass)) {
			InvocationHandler ihandler = Proxy.getInvocationHandler(obj);
			if (ihandler instanceof InterfaceAdapterInvocationHandler) {
				InterfaceAdapterInvocationHandler clproxy = (InterfaceAdapterInvocationHandler) ihandler;
				obj = clproxy.value;
				objclass = obj.getClass();
			}
		}
		if (ReflectUtils.isEnumOrEnumAnonymous(objclass)) {
			Class<?> enclass = objclass.isAnonymousClass() ? objclass.getSuperclass() : objclass;
			try {
				Class<?> clenum = Class.forName(enclass.getName(), false, classloader);
				if (ReflectUtils.isEnumOrEnumAnonymous(clenum)) {
					@SuppressWarnings({ "unchecked", "rawtypes" })
					Enum result = Enum.valueOf((Class) clenum, ((Enum) obj).name());
					return result;
				}
			} catch (ClassNotFoundException e) {
				//the enum class was not found in the target class loader, so check other options
			}
		}
		if (objclass.isArray()) {
			Class<?> component = objclass.getComponentType();
			if (component.isPrimitive()) {
				return obj;
			}
			try {
				Class<?> clcomponent = Class.forName(component.getName(), false, classloader);
				if (!clcomponent.isInterface() && !clcomponent.isArray()) {
					throw new IllegalArgumentException("Failed to adapt to non interface component: " + clcomponent);
				}
				Object[] objarray = (Object[]) obj;
				int arraylen = objarray.length;
				Object[] resultarray = (Object[]) Array.newInstance(clcomponent, arraylen);
				for (int i = 0; i < arraylen; i++) {
					resultarray[i] = adaptInterface(classloader, objarray[i]);
				}
				return resultarray;
			} catch (ClassNotFoundException e) {
				return obj;
			}
		}
		if (Iterable.class.isAssignableFrom(objclass)) {
			if (!Collection.class.isAssignableFrom(objclass)) {
				//just a simple iterable
				return new AdaptingIterable(classloader, (Iterable<?>) obj);
			}
			if (List.class.isAssignableFrom(objclass)) {
				if (RandomAccess.class.isAssignableFrom(objclass)) {
					return new AdaptingRandomAccessList(classloader, (List<?>) obj);
				}
				return new AdaptingList(classloader, (List<?>) obj);
			}
			if (Set.class.isAssignableFrom(objclass)) {
				return new AdaptingSet(classloader, (Set<?>) obj);
			}
			return new AdaptingCollection<Collection<?>>(classloader, (Collection<?>) obj);
		}
		if (Map.class.isAssignableFrom(objclass)) {
			return new AdaptingMap(classloader, (Map<?, ?>) obj);
		}
		if (Entry.class.isAssignableFrom(objclass)) {
			return new AdaptingEntry(classloader, (Entry<?, ?>) obj);
		}
		Collection<Class<?>> objinterfaces = ReflectUtils.getInterfaces(objclass);
		Set<Class<?>> actualinterfaces = new LinkedHashSet<>(objinterfaces.size() * 2);
		boolean allsameitf = true;
		for (Class<?> itf : objinterfaces) {
			try {
				Class<?> founditf = Class.forName(itf.getName(), false, classloader);
				if (founditf != itf) {
					allsameitf = false;
				}
				actualinterfaces.add(founditf);
			} catch (ClassNotFoundException e) {
				//XXX handle exception somehow?
				allsameitf = false;
			}
		}
		if (allsameitf) {
			//all the interfaces are the same, no need for adaptation
			return obj;
		}
		if (actualinterfaces.isEmpty()) {
			//failed to adapt. return object anyway, but does not need to create proxy as it won't be castable to anything anyway
			return obj;
		}

		return Proxy.newProxyInstance(classloader, actualinterfaces.toArray(ObjectUtils.EMPTY_CLASS_ARRAY),
				new InterfaceAdapterInvocationHandler(obj));
	}

	private DataConverterUtils() {
	}

	/**
	 * Converts the value object to the target type using the rules of this class.
	 * <p>
	 * No {@linkplain ConverterConfiguration converter configurations} and no {@link TaskResultResolver} will be used.
	 * The default conversion mechanism will be used.
	 * <p>
	 * This method is same as {@link #convert(Object, Type)}, but returns the result casted to the generic type argument
	 * T.
	 * 
	 * @param <T>
	 *            The target type.
	 * @param value
	 *            The object to convert.
	 * @param targettype
	 *            The target type of the conversion.
	 * @return The converted value.
	 * @throws ConversionFailedException
	 *             If the conversion fails.
	 */
	@SuppressWarnings({ "unchecked", "cast" })
	public static <T> T convert(Object value, Class<T> targettype) throws ConversionFailedException {
		//suprress cast warning because Eclipse warns about casting the target type to Type, although its necessary
		return (T) convert(value, (Type) targettype);
	}

	/**
	 * Converts the value object to the target type using the rules of this class.
	 * <p>
	 * No {@linkplain ConverterConfiguration converter configurations} and no {@link TaskResultResolver} will be used.
	 * The default conversion mechanism will be used.
	 * 
	 * @param value
	 *            The object to convert.
	 * @param type
	 *            The target type of the conversion.
	 * @return The converted value.
	 * @throws ConversionFailedException
	 *             If the conversion fails.
	 */
	public static Object convert(Object value, Type type) throws ConversionFailedException {
		Class<?> ctype = ReflectTypes.getClassFromType(null, type);
		ClassLoader basecl;
		if (ctype != null) {
			basecl = ctype.getClassLoader();
		} else {
			//XXX what do we set the base classloader to here?
			basecl = null;
		}
		ConversionContext convcontext = new ConversionContext(basecl, null,
				(Iterable<? extends ConverterConfiguration>) null);
		return convert(convcontext, value, type);
	}

	/**
	 * Converts the value object to the target field type using the rules of this class.
	 * <p>
	 * The specified {@link TaskResultResolver} will be used for resolving task identifier results, and the
	 * {@link ConverterConfiguration} annotations on the specified field will be used for converter specialization.
	 * 
	 * @param taskresultresolver
	 *            The task result resolver. May be <code>null</code>. See
	 *            {@link ConversionContext#getTaskResultResolver()}.
	 * @param value
	 *            The object to convert.
	 * @param fieldtype
	 *            The target field to convert the object for, this determines the generic target type.
	 * @return The converted object.
	 * @throws ConversionFailedException
	 *             If the conversion fails.
	 * @see Field#getGenericType()
	 * @see TaskContext
	 */
	public static Object convert(TaskResultResolver taskresultresolver, Object value, Field fieldtype)
			throws ConversionFailedException {
		Iterable<ConverterConfiguration> converterconfigs = ImmutableUtils
				.asUnmodifiableArrayList(fieldtype.getAnnotationsByType(ConverterConfiguration.class));
		return convert(taskresultresolver, fieldtype.getDeclaringClass().getClassLoader(), value,
				fieldtype.getGenericType(), converterconfigs);
	}

	/**
	 * Converts the value object to the target method type using the rules of this class.
	 * <p>
	 * The specified {@link TaskResultResolver} will be used for resolving task identifier results, and the
	 * {@link ConverterConfiguration} annotations on the specified method will be used for converter specialization.
	 * 
	 * @param taskresultresolver
	 *            The task result resolver. May be <code>null</code>. See
	 *            {@link ConversionContext#getTaskResultResolver()}.
	 * @param value
	 *            The object to convert.
	 * @param targetmethodtype
	 *            The target method to convert the object for, this determines the generic target type.
	 * @return The converted object.
	 * @throws ConversionFailedException
	 *             If the conversion fails.
	 * @see Method#getGenericReturnType()
	 * @see TaskContext
	 */
	public static Object convert(TaskResultResolver taskresultresolver, Object value, Method targetmethodtype)
			throws ConversionFailedException {
		Iterable<ConverterConfiguration> converterconfigs = ImmutableUtils
				.asUnmodifiableArrayList(targetmethodtype.getAnnotationsByType(ConverterConfiguration.class));
		return convert(taskresultresolver, targetmethodtype.getDeclaringClass().getClassLoader(), value,
				targetmethodtype.getGenericReturnType(), converterconfigs);
	}

	/**
	 * Converts the value object to the target type using the rules of this class.
	 * <p>
	 * The arguments will be used to initialize the {@link ConversionContext}.
	 * 
	 * @param taskresultresolver
	 *            The task result resolver. May be <code>null</code>. See
	 *            {@link ConversionContext#getTaskResultResolver()}.
	 * @param baseclassloader
	 *            The base classloader. See {@link ConversionContext}.
	 * @param value
	 *            The object to convert.
	 * @param targettype
	 *            The target type of the conversion.
	 * @param converterconfigurations
	 *            The converter configurations to use.
	 * @return The converted value.
	 * @throws ConversionFailedException
	 *             If the conversion fails.
	 * @see ConversionContext
	 */
	public static Object convert(TaskResultResolver taskresultresolver, ClassLoader baseclassloader, Object value,
			Type targettype, Iterable<? extends ConverterConfiguration> converterconfigurations)
			throws ConversionFailedException {
		return convert(new ConversionContext(baseclassloader, taskresultresolver, converterconfigurations), value,
				targettype);
	}

	/**
	 * Instantiates the argument {@link DataConverter} class.
	 * <p>
	 * Data converter implementations should have a public no-arg constructor.
	 * 
	 * @param <T>
	 *            The {@link DataConverter} type to instantiate.
	 * @param convertertype
	 *            The data converter type.
	 * @return The instantiated data converter.
	 * @throws ConversionFailedException
	 *             If the instantiation fails.
	 */
	public static <T extends DataConverter> T getDataConverterInstance(Class<T> convertertype)
			throws ConversionFailedException {
		//XXX cache the instantiated converters
		try {
			return ReflectUtils.newInstance(convertertype);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | NoSuchMethodException
				| SecurityException e) {
			throw new ConversionFailedException("Failed to instantiate data converter class.", e);
		} catch (InvocationTargetException e) {
			throw new ConversionFailedException("Failed to instantiate data converter class.", e.getCause());
		}
	}

	/**
	 * Converts the value to the target type using the specified conversion context.
	 * <p>
	 * This method examines the converter configuration for the specified conversion context, and uses the specified
	 * {@linkplain DataConverter data converters} to convert the value. If no data converters were defined, the
	 * {@linkplain #convertDefault(ConversionContext, Object, Type) default conversion mechanism} will be applied.
	 * <p>
	 * <b>Warning:</b> Do not call this method directly from {@link DataConverter} implementation using the same
	 * conversion context argument for the {@link DataConverter#convert(ConversionContext, Object, Type)} call. It will
	 * cause a stack overflow as it will recursively call the same data converter. Call
	 * {@link #convertDefault(ConversionContext, Object, Type)} to forward conversion for the currently converted value.
	 * <p>
	 * It is recommended that this method is called with a conversion context retrieved from
	 * {@link ConversionContext#genericChildContext(int)}.
	 * 
	 * @param conversioncontext
	 *            The conversion context to use.
	 * @param value
	 *            The object to convert.
	 * @param type
	 *            The target type of the conversion.
	 * @return The converted object.
	 * @throws ConversionFailedException
	 *             If the conversion fails.
	 */
	public static Object convert(ConversionContext conversioncontext, Object value, Type type)
			throws ConversionFailedException {
		if (value == null) {
			return null;
		}
		List<ConversionFailedException> excs = null;
		ConverterConfiguration currentconverterconfigurations = conversioncontext.getCurrentConverterConfiguration();

		if (currentconverterconfigurations != null) {
			Class<? extends DataConverter>[] dataconverters = currentconverterconfigurations.value();
			for (Class<? extends DataConverter> converter : dataconverters) {
				ConversionFailedException conve;
				try {
					DataConverter converterinstance = getDataConverterInstance(converter);
					return converterinstance.convert(conversioncontext, value, type);
				} catch (ConversionFailedException e) {
					conve = e;
				}
				if (excs == null) {
					excs = new ArrayList<>();
				}
				excs.add(conve);
			}
			ConversionFailedException resexc = new ConversionFailedException(
					"Failed to convert " + value.getClass().getName() + " to " + type + ".");
			if (excs != null) {
				for (ConversionFailedException e : excs) {
					resexc.addSuppressed(e);
				}
			}
			throw resexc;
		}
		return convertDefault(conversioncontext, value, type);
	}

	/**
	 * Converts the value to the target type using the specified conversion context without examining the converter
	 * configuration for the context.
	 * <p>
	 * Unlike {@link #convert(ConversionContext, Object, Type)}, this method doesn't use the converter configuration,
	 * and the defined {@link DataConverter DataConverters} to delegate conversion.
	 * 
	 * @param conversioncontext
	 *            The conversion context to use.
	 * @param value
	 *            The object to convert.
	 * @param type
	 *            The target type of the conversion.
	 * @return The converted object.
	 * @throws ConversionFailedException
	 *             If the conversion fails.
	 */
	public static Object convertDefault(ConversionContext conversioncontext, Object value, Type type)
			throws ConversionFailedException {
		Object result = convertDefaultImpl(conversioncontext, value, type);
		return result;
	}

	/**
	 * Wraps the given map into the specified interface using the rules of this class.
	 * <p>
	 * The classloader of the interface type will be used to define the proxy.
	 * 
	 * @param <T>
	 *            The type of the interface.
	 * @param map
	 *            The map object to wrap.
	 * @param itf
	 *            The interface type to wrap the map for.
	 * @return The wrapped interface.
	 * @throws IllegalArgumentException
	 *             If the type is not an interface.
	 * @see DataConverterUtils
	 */
	public static <T> T wrapMappedInterface(Map<String, ?> map, Class<T> itf) throws IllegalArgumentException {
		return wrapMappedInterface(map, itf, itf.getClassLoader());
	}

	/**
	 * Wraps the given map into the specified interface using the rules of this class.
	 * 
	 * @param <T>
	 *            The type of the interface.
	 * @param map
	 *            The map object to wrap.
	 * @param itf
	 *            The interface type to wrap the map for.
	 * @param cl
	 *            The classloader to define the proxy in.
	 * @return The wrapped interface.
	 * @throws IllegalArgumentException
	 *             If the type is not an interface.
	 * @see DataConverterUtils
	 */
	public static <T> T wrapMappedInterface(Map<String, ?> map, Class<T> itf, ClassLoader cl)
			throws IllegalArgumentException {
		return wrapMappedInterface(map, itf, cl, null);
	}

	/**
	 * Wraps the given map into the specified interface using the rules of this class.
	 * 
	 * @param <T>
	 *            The type of the interface.
	 * @param map
	 *            The map object to wrap.
	 * @param itf
	 *            The interface type to wrap the map for.
	 * @param cl
	 *            The classloader to define the proxy in.
	 * @param taskresultresolver
	 *            The task result resolver or <code>null</code> if not available.
	 * @return The wrapped interface.
	 * @throws IllegalArgumentException
	 *             If the type is not an interface.
	 * @see DataConverterUtils
	 */
	@SuppressWarnings("unchecked")
	public static <T> T wrapMappedInterface(Map<String, ?> map, Class<T> itf, ClassLoader cl,
			TaskResultResolver taskresultresolver) throws IllegalArgumentException {
		ReflectUtils.requireInterface(itf);
		//clone the map to protect against external modifications
		map = new TreeMap<>(map);
		InvocationHandler h;
		if (itf.isAnnotation()) {
			h = new AnnotationMappedInterfaceInvocationHandler(taskresultresolver, map,
					(Class<? extends Annotation>) itf);
		} else {
			h = new MappedInterfaceInvocationHandler(taskresultresolver, map, itf);
		}
		Class<?>[] classarray = { itf, };
		return (T) Proxy.newProxyInstance(cl, classarray, h);
	}

	private static final Map<Class<?>, Function<Number, Object>> NUMBER_TO_PRIMITIVE_CONVERTERS = new HashMap<>(12);
	static {
		NUMBER_TO_PRIMITIVE_CONVERTERS.put(byte.class, Number::byteValue);
		NUMBER_TO_PRIMITIVE_CONVERTERS.put(short.class, Number::shortValue);
		NUMBER_TO_PRIMITIVE_CONVERTERS.put(int.class, Number::intValue);
		NUMBER_TO_PRIMITIVE_CONVERTERS.put(long.class, Number::longValue);
		NUMBER_TO_PRIMITIVE_CONVERTERS.put(float.class, Number::floatValue);
		NUMBER_TO_PRIMITIVE_CONVERTERS.put(double.class, Number::doubleValue);
	}

	private static Object deTaskResultize(ConversionContext conversioncontext, Object value) {
		TaskResultResolver resresolver = conversioncontext.getTaskResultResolver();
		if (resresolver != null) {
			if (value instanceof StructuredTaskResult) {
				return ((StructuredTaskResult) value).toResult(resresolver);
			}
		}
		return value;
	}

	private static Object convertDefaultToGenericArrayImpl(ConversionContext conversioncontext, Object value,
			Type targettype) {
		//converting to a generic array, like List<...>[]
		GenericArrayType genericarraytargettype = (GenericArrayType) targettype;
		Type componenttype = genericarraytargettype.getGenericComponentType();
		Class<?> componentclass = ReflectTypes.getGenericArrayComponentClass(genericarraytargettype);
		value = deTaskResultize(conversioncontext, value);
		if (value == null) {
			return null;
		}
		Class<? extends Object> valueclass = value.getClass();
		if (valueclass.isArray()) {
			//converting array to array
			int size = Array.getLength(value);
			Object resarray = Array.newInstance(componentclass, size);
			for (int i = 0; i < size; i++) {
				Object e = Array.get(value, i);
				Array.set(resarray, i, convert(conversioncontext, e, componenttype));
			}
			return resarray;
		}
		if (Iterable.class.isAssignableFrom(valueclass)) {
			ArrayList<Object> items;
			if (Collection.class.isAssignableFrom(valueclass)) {
				items = new ArrayList<>((Collection<?>) value);
			} else {
				items = new ArrayList<>();
				for (Object e : (Iterable<?>) value) {
					items.add(e);
				}
			}
			int size = items.size();
			Object resarray = Array.newInstance(componentclass, size);
			for (int i = 0; i < size; i++) {
				Object e = items.get(i);
				Array.set(resarray, i, convert(conversioncontext, e, componenttype));
			}
			return resarray;
		}
		//wrap in a singleton array
		Object single = convert(conversioncontext, value, componenttype);
		Object resarray = Array.newInstance(componentclass, 1);
		Array.set(resarray, 0, single);
		return resarray;
	}

	private static Object convertDefaultToParameterizedImpl(ConversionContext conversioncontext, Object value,
			ParameterizedType targettype) {
		// converting to a template class
		Class<?> targetclass = (Class<?>) targettype.getRawType();
		if (targetclass.isAssignableFrom(List.class)) {
			//converting to a list, collection, or iterable
			Type collelementtype = ReflectTypes.getTypeArguments(targettype, 0);
			ConversionContext elemcontext = conversioncontext.genericChildContext(0);
			if (value instanceof StructuredListTaskResult) {
				List<StructuredTaskResult> proxysubjectlist = new ArrayList<>();
				((StructuredListTaskResult) value).forEach(etid -> {
					proxysubjectlist.add(etid);
				});
				return new ProxyList(elemcontext, proxysubjectlist, collelementtype);
			}
			if (value instanceof StructuredTaskResult) {
				TaskResultResolver resultresolver = elemcontext.getTaskResultResolver();
				if (resultresolver == null) {
					throw new ConversionFailedException(
							"Failed to convert structured task result without task result resolver.");
				}
				TaskResultDependencyHandle dephandle = ((StructuredTaskResult) value)
						.toResultDependencyHandle(resultresolver);
				Object depval = dephandle.get();
				if (depval == null) {
					return null;
				}
				Class<? extends Object> depvalclass = depval.getClass();
				if (!depvalclass.isArray() && !Iterable.class.isAssignableFrom(depvalclass)) {
					//wrapping an object into a singleton
					//pass the structured task result as the element, converted when accessed
					dephandle.setTaskOutputChangeDetector(CommonTaskOutputChangeDetector.notInstanceOf(Iterable.class));
					dephandle.setTaskOutputChangeDetector(CommonTaskOutputChangeDetector.IS_NOT_ARRAY);
					return new ProxyList(elemcontext, Collections.singletonList(value), collelementtype);
				}
				value = depval;
			}
			Class<?> valueclass = value.getClass();
			if (valueclass.isArray()) {
				//converting an array to list
				return new ProxyListArray(elemcontext, value, collelementtype);
			}
			if (Iterable.class.isAssignableFrom(valueclass)) {
				//converting an iterable to list
				if (List.class.isAssignableFrom(valueclass)) {
					//converting a list to a list
					return new ProxyList(elemcontext, (List<?>) value, collelementtype);
				}
				if (Collection.class.isAssignableFrom(valueclass)) {
					return new ProxyCollection<>(elemcontext, (Collection<?>) value, collelementtype);
				}
				return new ProxyIterable(elemcontext, (Iterable<?>) value, collelementtype);
			}
			//converting a non collection object
			Object single = convert(elemcontext, value, collelementtype);
			return ImmutableUtils.singletonList(single);
		}
		if (targetclass == Map.class) {
			//converting to a map
			if (value instanceof StructuredMapTaskResult) {
				Map<String, StructuredTaskResult> proxysubjectmap = new TreeMap<>(
						StringUtils.nullsFirstStringComparator());
				((StructuredMapTaskResult) value).forEach((key, vtid) -> {
					proxysubjectmap.put(key, vtid);
				});
				Type keytype = ReflectTypes.getTypeArguments(targettype, 0);
				Type valuetype = ReflectTypes.getTypeArguments(targettype, 1);
				return new ProxyMap(conversioncontext.genericChildContext(0), conversioncontext.genericChildContext(1),
						proxysubjectmap, keytype, valuetype);
			}
			if (value instanceof BuildTargetTaskResult) {
				Map<String, StructuredObjectTaskResult> proxysubjectmap = new TreeMap<>(
						StringUtils.nullsFirstStringComparator());
				((BuildTargetTaskResult) value).getTaskResultIdentifiers().forEach((key, vtid) -> {
					proxysubjectmap.put(key, new SimpleStructuredObjectTaskResult(vtid));
				});
				Type keytype = ReflectTypes.getTypeArguments(targettype, 0);
				Type valuetype = ReflectTypes.getTypeArguments(targettype, 1);
				return new ProxyMap(conversioncontext.genericChildContext(0), conversioncontext.genericChildContext(1),
						proxysubjectmap, keytype, valuetype);
			}
			value = deTaskResultize(conversioncontext, value);
			if (value == null) {
				return null;
			}
			Class<?> valueclass = value.getClass();
			if (!Map.class.isAssignableFrom(valueclass)) {
				throw new ConversionFailedException("Failed to convert " + valueclass.getName() + " (" + value + ") to "
						+ targetclass.getName() + ".");
			}
			Type keytype = ReflectTypes.getTypeArguments(targettype, 0);
			Type valuetype = ReflectTypes.getTypeArguments(targettype, 1);
			return new ProxyMap(conversioncontext.genericChildContext(0), conversioncontext.genericChildContext(1),
					(Map<?, ?>) value, keytype, valuetype);
		}
		value = deTaskResultize(conversioncontext, value);
		if (value == null) {
			return null;
		}
		Class<?> valueclass = value.getClass();
		if (targetclass == Enum.class) {
			Type enumelemtype = ReflectTypes.getTypeArguments(targettype, 0);
			if (!(enumelemtype instanceof Class) || !((Class<?>) enumelemtype).isEnum()) {
				throw new ConversionFailedException("Invalid Enum class type parameter: " + enumelemtype);
			}
			try {
				@SuppressWarnings({ "unchecked", "rawtypes" })
				Enum<?> enumres = Enum.valueOf((Class<? extends Enum>) enumelemtype, value.toString());
				return enumres;
			} catch (IllegalArgumentException e) {
				throw new ConversionFailedException("Failed to convert to enum class: " + enumelemtype, e);
			}
		}
		class_constructor_converter:
		if (targetclass == valueclass) {
			if (targetclass == Class.class) {
				Class<?> valueasclass = (Class<?>) value;
				Type[] typeargs = targettype.getActualTypeArguments();
				if (typeargs.length == 1) {
					Type arg = typeargs[0];
					if (arg instanceof GenericArrayType) {
						arg = ReflectTypes.getGenericArrayClass((GenericArrayType) arg);
					} else if (arg instanceof ParameterizedType) {
						//we're converting to Class<X<... >>
						//   its the same as converting to Class<X>
						//   as generic arguments on a Class<> subject doesn't make sense
						arg = ((ParameterizedType) arg).getRawType();
					}
					if (arg instanceof WildcardType) {
						//converting to Class<? ...>
						WildcardType wcarg = (WildcardType) arg;
						if (isSatisfiedWildcardBounds(wcarg, valueasclass)) {
							return value;
						}
					}
					if (arg instanceof Class) {
						//converting to Class<arg>
						Class<?> argclass = (Class<?>) arg;
						if (argclass == valueasclass) {
							return value;
						}
						if (argclass.getName().equals(valueasclass.getName())) {
							//the class names are the same, however they aren't equal
							//we return the type argument class, as a Class<X> can only represent X.class
							//we could do this in other cases as well, however if the class names are different, 
							//    we better fail the conversion
							return argclass;
						}
						break class_constructor_converter;
					}
					//else cannot satisfy the type argument requirement, unrecognized type
				} else if (typeargs.length == 0) {
					//there are no type arguments
					//raw conversion
					//pass through the object
					return value;
				} //else there are more than 1 type argument, shouldn't happen, can't convert
			} else if (targetclass == Constructor.class) {
				Constructor<?> valueasconstructor = (Constructor<?>) value;
				Class<?> valueconstructordeclaringclass = valueasconstructor.getDeclaringClass();
				Type[] typeargs = targettype.getActualTypeArguments();
				if (typeargs.length == 1) {
					Type arg = typeargs[0];
					if (arg instanceof GenericArrayType) {
						//there are no constructors for arrays
						break class_constructor_converter;
					}
					if (arg instanceof ParameterizedType) {
						//converting to Constructor<arg<...>>
						//it is the same as Constructor<arg> 
						arg = ((ParameterizedType) arg).getRawType();
					}

					if (arg instanceof WildcardType) {
						WildcardType wcarg = (WildcardType) arg;
						if (isSatisfiedWildcardBounds(wcarg, valueconstructordeclaringclass)) {
							return value;
						}
					} else if (arg instanceof Class) {
						//converting to Constructor<arg>
						if (arg == valueconstructordeclaringclass) {
							return value;
						}
						break class_constructor_converter;
					} //else cannot satisfy the type argument requirement, unrecognized type
				} else if (typeargs.length == 0) {
					//there are no type arguments
					//raw conversion
					//pass through the object
					return value;
				} //else there are more than 1 type argument, shouldn't happen, can't convert
			}
		}
		//cannot convert to known generic interfaces
		//we cannot convert to user defined generic interfaces, as it would require storing the generic parameter of the target type
		//and we have no information about the generic user class as well
		//throw an exception
		throw new ConversionFailedException(
				"Failed to convert " + valueclass.getName() + " (" + value + ") to unrecognized parameterized type "
						+ targettype + " with class type " + targetclass.getName() + ".");
	}

	private static boolean isSatisfiedWildcardBounds(WildcardType wcarg, Class<?> c) {
		Type[] lb = wcarg.getLowerBounds();
		Type[] ub = wcarg.getUpperBounds();

		if (lb.length != 0) {
			//there are lower bounds Class<? super ...>
			for (Type lbtype : lb) {
				Class<?> lbclass = ReflectTypes.getClassFromType(null, lbtype);
				if (lbclass == null) {
					//can't satisfy lower bound
					return false;
				}
				if (!c.isAssignableFrom(lbclass)) {
					//valclass is not a superclass or interface of lbclass
					return false;
				}
			}
		}
		//lower bounds satisfied
		if (ub.length != 0) {
			//there are upper bounds Class<? extends ...>
			for (Type ubtype : ub) {
				Class<?> ubclass = ReflectTypes.getClassFromType(null, ubtype);
				if (ubclass == null) {
					//can't satisfy upper bound
					return false;
				}
				if (!ubclass.isAssignableFrom(c)) {
					//ubclass is not a superclass or interface of valclass
					return false;
				}
			}
		}
		return true;
	}

	private static Object convertDefaultToPrimitiveImpl(ConversionContext conversioncontext, Object value,
			Class<?> targetclass) {
		value = deTaskResultize(conversioncontext, value);
		if (value == null) {
			return null;
		}
		Class<?> valueclass = value.getClass();
		if (targetclass == boolean.class) {
			if (value instanceof Boolean) {
				return value;
			}
			return Boolean.parseBoolean(value.toString());
		}
		if (targetclass == char.class) {
			if (value instanceof Character) {
				return value;
			}
			if (value instanceof String) {
				String vs = (String) value;
				if (vs.length() == 1) {
					return vs.charAt(0);
				}
			}
			throw new ConversionFailedException(
					"Failed to convert " + valueclass.getName() + " (" + value + ") to char.");
		}
		Number numv;
		try {
			if (value instanceof Number) {
				numv = (Number) value;
			} else if (targetclass == float.class || targetclass == double.class) {
				numv = Double.valueOf(value.toString());
			} else {
				numv = Long.valueOf(value.toString());
			}
		} catch (NumberFormatException e) {
			throw new ConversionFailedException("Failed to convert " + valueclass.getName() + " (" + value + ") to "
					+ targetclass.getSimpleName() + ".", e);
		}
		return NUMBER_TO_PRIMITIVE_CONVERTERS.get(targetclass).apply(numv);
	}

	private static Object convertDefaultImpl(ConversionContext conversioncontext, Object value, Type type) {
		if (value instanceof StructuredTaskResult && conversioncontext.getTaskResultResolver() == null) {
			throw new ConversionFailedException(
					"Failed to convert structured task result without task result resolver.");
		}
		Type targettype = ReflectTypes.deannotateType(type);
		if (targettype instanceof TypeVariable) {
			throw new ConversionFailedException("Cannot convert to type variable. (" + targettype + ")", value,
					targettype);
		}
		if (targettype instanceof WildcardType) {
			WildcardType wct = (WildcardType) targettype;
			Type[] ub = wct.getUpperBounds();
			if (ub.length == 0 || ub[0] == Object.class) {
				//just a single wildcard ?
				return value;
			}
			if (ub.length > 1) {
				//XXX maybe support in the future if all bounds define a single class
				throw new ConversionFailedException(
						"Cannot to convert to wildcard type with multiple upper boudns. (" + targettype + ")", value,
						targettype);
			}
			Type[] lb = wct.getLowerBounds();
			if (lb.length > 0) {
				//there are lower bounds. ? super ...
				if (ub.length == 0) {
					//no conversion necessary, as converting to the Object type satisifies all lower bounds
					return value;
				}
				throw new ConversionFailedException(
						"Cannot to convert to wildcard type with lower and upper bounds. (" + targettype + ")", value,
						targettype);
			}
			return convertDefaultImpl(conversioncontext, value, ub[0]);
		}
		if (targettype instanceof GenericArrayType) {
			return convertDefaultToGenericArrayImpl(conversioncontext, value, targettype);
		}
		Class<? extends Object> valueclass = value.getClass();
		if (targettype instanceof ParameterizedType) {
			return convertDefaultToParameterizedImpl(conversioncontext, value, (ParameterizedType) targettype);
		}
		if (!(targettype instanceof Class<?>)) {
			//unknown target type kind to convert to
			throw new ConversionFailedException("Failed to convert " + valueclass.getName() + " (" + value
					+ ") to unrecognized target type " + targettype + ".");
		}
		Class<?> targetclass = ReflectUtils.primitivize((Class<?>) targettype);
		return convertDefaultToClassTargetType(conversioncontext, value, valueclass, targetclass);
	}

	private static Object convertDefaultToClassTargetType(ConversionContext conversioncontext, Object value,
			Class<? extends Object> valueclass, final Class<?> targetclass) throws AssertionError {
		List<ConversionFailedException> excs = null;
		if (targetclass.isPrimitive()) {
			return convertDefaultToPrimitiveImpl(conversioncontext, value, targetclass);
		}
		if (targetclass.isAssignableFrom(valueclass)) {
			return value;
		}
		if (Iterable.class.isAssignableFrom(targetclass)) {
			return convertDefaultToIterableTargetClass(conversioncontext, value);
		}
		if (targetclass == Map.class) {
			//converting to a map
			//can throw an exception, as we can only convert maps to maps
			//assignability check was already done
			return convertDefaultToMapTargetClass(conversioncontext, value);
		}
		if (targetclass.isArray()) {
			//converting to an array
			return convertDefaultToArrayTargetClass(conversioncontext, value, targetclass);
		}
		if (targetclass == String.class) {
			return Objects.toString(deTaskResultize(conversioncontext, value), null);
		}
		if (ReflectUtils.isEnumOrEnumAnonymous(targetclass)) {
			return convertDefaultToEnumTargetClass(conversioncontext, value, targetclass);
		}
		Method strvalueof = null;
		//loop until the object is no longer a structured task result
		while (true) {
			Map<Class<?>, Integer> allvalueclassinheritedtypedistances = ReflectUtils
					.getAllInheritedTypesWithDistance(valueclass);

			Map<String, Class<?>> allvalueclassinheritedtypenameclasses = new TreeMap<>();
			for (Class<?> c : allvalueclassinheritedtypedistances.keySet()) {
				allvalueclassinheritedtypenameclasses.put(c.getName(), c);
			}
			if (targetclass.isInterface()) {
				Class<?> valuesuperitf = allvalueclassinheritedtypenameclasses.get(targetclass.getName());
				if (valuesuperitf != null) {
					if (valuesuperitf != targetclass) {
						//the value object implements the interface that is the target class
						//  however, that interface is defined by a different classloader
						//  this can happen when there are version changes between the interfaces
						//  adapt the value object to the given interface
						return adaptInterface(conversioncontext.getBaseClassLoader(), value);
					}
					throw new AssertionError(
							"Internal error: assignable interface conversion should've passed already. ("
									+ valuesuperitf + ")");
				}
			}

			valueof_converter_block:
			{
				if (value instanceof StructuredTaskResult) {
					try {
						Method contextedvalueof = targetclass.getDeclaredMethod("valueOf", StructuredTaskResult.class,
								ConversionContext.class);
						if (targetclass.isAssignableFrom(contextedvalueof.getReturnType())) {
							try {
								return ReflectUtils.invokeMethod((Object) null, contextedvalueof, value,
										conversioncontext);
							} catch (IllegalAccessException | IllegalArgumentException e) {
								excs = IOUtils.collectExc(excs, new ConversionFailedException(
										"Failed to convert using: " + contextedvalueof, e));
							} catch (InvocationTargetException e) {
								excs = IOUtils.collectExc(excs, new ConversionFailedException(
										"Failed to convert using: " + contextedvalueof, e.getCause()));
							}
						}
					} catch (NoSuchMethodException | SecurityException e) {
						excs = IOUtils.collectExc(excs, new ConversionFailedException(
								"valueOf(StructuredTaskResult, ConversionContext) method not found.", e));
					}
				}
				Map<Class<?>, Method> valueofs = new HashMap<>();
				for (Method m : targetclass.getDeclaredMethods()) {
					if (!"valueOf".equals(m.getName())) {
						continue;
					}
					if (m.getParameterCount() != 1) {
						continue;
					}
					if (!Modifier.isStatic(m.getModifiers())) {
						continue;
					}
					Type paramgentype = m.getGenericParameterTypes()[0];
					if (!isValidValueOfGenericParameterType(paramgentype)) {
						//the valueOf method parameter must not be generic, as we don't do conversion for the parameter
						continue;
					}
					if (!targetclass.isAssignableFrom(m.getReturnType())) {
						continue;
					}
					valueofs.put(m.getParameterTypes()[0], m);
				}
				Method perfectvalueofmatch = valueofs.remove(valueclass);
				if (perfectvalueofmatch != null) {
					try {
						return ReflectUtils.invokeMethod((Object) null, perfectvalueofmatch, value);
					} catch (IllegalAccessException | IllegalArgumentException e) {
						excs = IOUtils.collectExc(excs, new ConversionFailedException(
								"Failed to convert using perfect valueOf match: " + perfectvalueofmatch, e));
					} catch (InvocationTargetException e) {
						excs = IOUtils.collectExc(excs, new ConversionFailedException(
								"Failed to convert using perfect valueOf match: " + perfectvalueofmatch, e.getCause()));
					}

					//continue, check the primitivized version
				}
				Class<?> primitivizedvalueclass = ReflectUtils.primitivize(valueclass);
				Method primitivizedperfectvalueofmatch = valueofs.remove(primitivizedvalueclass);
				if (primitivizedperfectvalueofmatch != null) {
					try {
						return ReflectUtils.invokeMethod((Object) null, primitivizedperfectvalueofmatch, value);
					} catch (IllegalAccessException | IllegalArgumentException e) {
						excs = IOUtils.collectExc(excs, new ConversionFailedException(
								"Failed to convert using perfect valueOf match: " + primitivizedperfectvalueofmatch,
								e));
					} catch (InvocationTargetException e) {
						excs = IOUtils.collectExc(excs, new ConversionFailedException(
								"Failed to convert using perfect valueOf match: " + primitivizedperfectvalueofmatch,
								e.getCause()));
					}
				}
				if (perfectvalueofmatch != null || primitivizedperfectvalueofmatch != null) {
					//none of the perfect valueof matches matched, do not try other valueOf methods
					//    as there is at least one perfect match was found
					break valueof_converter_block;
				}
				strvalueof = valueofs.remove(String.class);
				if (valueofs.isEmpty()) {
					break valueof_converter_block;
				}

				List<Method> adaptvalueofmethods = new ArrayList<>();
				List<Method> matches = new ArrayList<>();
				Comparator<? super Method> valueofmethodsorter = (l, r) -> {
					return Integer.compare(allvalueclassinheritedtypedistances.get(l.getParameterTypes()[0]),
							allvalueclassinheritedtypedistances.get(r.getParameterTypes()[0]));
				};
				matches.sort(valueofmethodsorter);
				adaptvalueofmethods.sort(valueofmethodsorter);

				for (Iterator<Method> it = valueofs.values().iterator(); it.hasNext();) {
					Method m = it.next();
					Class<?> paramtype = m.getParameterTypes()[0];
					if (paramtype.isAssignableFrom(valueclass)) {
						matches.add(m);
						it.remove();
						continue;
					}
					if (paramtype.isInterface()) {
						Class<?> inheritednameclass = allvalueclassinheritedtypenameclasses.get(paramtype.getName());
						if (inheritednameclass != paramtype) {
							//a valueOf method is present that has an interface parameter
							//    that the value object implements
							//    but the implemented interface in the value object
							//    is loaded by a different classloader
							//  we can call this valueOf method if we adapt the value to the given interface
							adaptvalueofmethods.add(m);
							it.remove();
							continue;
						}
					}
				}
				for (Method m : matches) {
					try {
						return ReflectUtils.invokeMethod((Object) null, m, value);
					} catch (Exception e) {
						excs = IOUtils.collectExc(excs,
								new ConversionFailedException("Failed to convert using: " + m, e));
					}
				}
				for (Method m : adaptvalueofmethods) {
					try {
						Object adapted = adaptInterface(conversioncontext.getBaseClassLoader(), value);
						return ReflectUtils.invokeMethod((Object) null, m, adapted);
					} catch (Exception e) {
						excs = IOUtils.collectExc(excs,
								new ConversionFailedException("Failed to convert using: " + m, e));
					}
				}
			}
			if (targetclass.isInterface()) {
				if (Map.class.isAssignableFrom(valueclass)) {
					NavigableMap<String, Object> proxymap = new TreeMap<>(StringUtils.nullsFirstStringComparator());
					for (Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
						proxymap.put(Objects.toString(entry.getKey(), null), entry.getValue());
					}
					return wrapMappedInterface(proxymap, targetclass, conversioncontext.getBaseClassLoader(),
							conversioncontext.getTaskResultResolver());
				}
				if (value instanceof StructuredMapTaskResult) {
					NavigableMap<String, StructuredTaskResult> proxymap = new TreeMap<>(
							StringUtils.nullsFirstStringComparator());
					((StructuredMapTaskResult) value).forEach((key, vtid) -> {
						proxymap.put(key, vtid);
						//XXX warn if overwrite?
					});
					return wrapMappedInterface(proxymap, targetclass, conversioncontext.getBaseClassLoader(),
							conversioncontext.getTaskResultResolver());
				}
				if (BuildTargetTaskResult.class.isAssignableFrom(valueclass)) {
					NavigableMap<String, Object> proxymap = new TreeMap<>();
					for (Entry<String, TaskIdentifier> entry : ((BuildTargetTaskResult) value)
							.getTaskResultIdentifiers().entrySet()) {
						proxymap.put(entry.getKey(), new SimpleStructuredObjectTaskResult(entry.getValue()));
					}
					return wrapMappedInterface(proxymap, targetclass, conversioncontext.getBaseClassLoader(),
							conversioncontext.getTaskResultResolver());
				}
			}

			try {
				Method totargetmethod = null;
				try {
					totargetmethod = valueclass.getMethod("to" + targetclass.getCanonicalName().replace('.', '_'));
				} catch (NoSuchMethodException | SecurityException e) {
					try {
						totargetmethod = valueclass.getMethod("to" + targetclass.getSimpleName());
					} catch (NoSuchMethodException | SecurityException e2) {
						e.addSuppressed(e2);
						throw e;
					}
				}
				if (targetclass.isAssignableFrom(totargetmethod.getReturnType())) {
					try {
						return ReflectUtils.invokeMethod(value, totargetmethod);
					} catch (IllegalAccessException | IllegalArgumentException e) {
						excs = IOUtils.collectExc(excs,
								new ConversionFailedException("Failed to convert using: " + totargetmethod, e));
					} catch (InvocationTargetException e) {
						excs = IOUtils.collectExc(excs, new ConversionFailedException(
								"Failed to convert using: " + totargetmethod, e.getCause()));
					}
				} else {
					//types are not assignable. is the target class is an interface
					//    we can adapt the result of the to...() method call to the target class
					if (shouldAdaptToMethodResultToTargetClass(totargetmethod, targetclass)) {
						try {
							return adaptInterface(conversioncontext.getBaseClassLoader(),
									ReflectUtils.invokeMethod(value, totargetmethod));
						} catch (IllegalAccessException | IllegalArgumentException e) {
							excs = IOUtils.collectExc(excs,
									new ConversionFailedException("Failed to convert using: " + totargetmethod, e));
						} catch (InvocationTargetException e) {
							excs = IOUtils.collectExc(excs, new ConversionFailedException(
									"Failed to convert using: " + totargetmethod, e.getCause()));
						}
					} else {
						excs = IOUtils.collectExc(excs,
								new ConversionFailedException("Subject conversion method return type mismatch: "
										+ totargetmethod + " for " + targetclass));
					}
				}
			} catch (NoSuchMethodException | SecurityException e) {
				excs = IOUtils.collectExc(excs,
						new ConversionFailedException("No subject conversion method found.", e));
			}

			if (!(value instanceof StructuredTaskResult)) {
				//break out of the task result retry loop
				break;
			}

			TaskResultResolver resresolver = conversioncontext.getTaskResultResolver();
			if (resresolver == null) {
				excs.add(new ConversionFailedException(
						"Failed to convert structured task result without task result resolver."));
				break;
			}
			if (value instanceof StructuredObjectTaskResult) {
				//if the value consists of multiple consecutive object task results
				//advance one by one
				TaskIdentifier valuetaskid = ((StructuredObjectTaskResult) value).getTaskIdentifier();
				value = resresolver.getTaskResult(valuetaskid);
			} else if (value instanceof ComposedStructuredTaskResult) {
				value = ((ComposedStructuredTaskResult) value).getIntermediateTaskResult(resresolver);
			} else {
				value = ((StructuredTaskResult) value).toResult(resresolver);
			}

			if (value == null) {
				return null;
			}
			valueclass = value.getClass();
			if (targetclass.isAssignableFrom(valueclass)) {
				//check assignability again as the value class has been modified
				return value;
			}
		}

		if (strvalueof != null) {
			try {
				return ReflectUtils.invokeMethod((Object) null, strvalueof, value.toString());
			} catch (Exception e) {
				excs = IOUtils.collectExc(excs,
						new ConversionFailedException("Failed to convert using: " + strvalueof, e));
			}
		}

		ConversionFailedException exc = new ConversionFailedException(
				"Failed to convert " + valueclass.getName() + " (" + value + ") to " + targetclass.getName() + ".");
		if (excs != null) {
			for (ConversionFailedException e : excs) {
				exc.addSuppressed(e);
			}
		}
		throw exc;
	}

	private static boolean isValidValueOfGenericParameterType(Type paramgentype) {
		if (paramgentype == null) {
			return false;
		}
		if (paramgentype.getClass() == Class.class) {
			return true;
		}
		if (paramgentype instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) paramgentype;
			Type ownertype = pt.getOwnerType();
			if (ownertype != null && !isValidValueOfGenericParameterType(ownertype)) {
				return false;
			}
			Type[] typeargs = pt.getActualTypeArguments();
			if (ObjectUtils.isNullOrEmpty(typeargs)) {
				//not actually parameterized
				return true;
			}
			for (Type targ : typeargs) {
				//check if all of them are ? wildcards without bounds
				if (!(targ instanceof WildcardType)) {
					return false;
				}
				WildcardType wctype = (WildcardType) targ;
				Type[] lb = wctype.getLowerBounds();
				if (!ObjectUtils.isNullOrEmpty(lb)) {
					return false;
				}
				Type[] ub = wctype.getUpperBounds();
				if (!ObjectUtils.isNullOrEmpty(ub)) {
					//only ? extends Object is allowed
					if (ub.length != 1 || !Object.class.equals(ub[0])) {
						return false;
					}
				}
				//the wildcard type is acceptable
			}
			return true;
		}
		return false;
	}

	private static boolean shouldAdaptToMethodResultToTargetClass(Method m, Class<?> targetclass) {
		if (!targetclass.isInterface()) {
			return false;
		}
		Class<?> returntype = m.getReturnType();
		Class<?> itfinreturntype = ReflectUtils.findInterfaceWithNameInHierarchy(returntype, targetclass.getName());
		if (itfinreturntype == null) {
			return false;
		}
		return itfinreturntype != targetclass;
	}

	private static Object convertDefaultToEnumTargetClass(ConversionContext conversioncontext, Object value,
			final Class<?> targetclass) {
		Class<?> enumtargetclass;
		if (targetclass.isAnonymousClass()) {
			enumtargetclass = targetclass.getSuperclass();
		} else {
			enumtargetclass = targetclass;
		}
		if (enumtargetclass.isInstance(value)) {
			return value;
		}
		Object untaskedvalue = deTaskResultize(conversioncontext, value);
		if (untaskedvalue == null) {
			return null;
		}
		if (enumtargetclass.isInstance(untaskedvalue)) {
			return untaskedvalue;
		}
		try {
			String enumname = untaskedvalue instanceof Enum<?> ? ((Enum<?>) untaskedvalue).name()
					: untaskedvalue.toString();
			@SuppressWarnings({ "unchecked", "rawtypes" })
			Enum<?> enumres = Enum.valueOf((Class<? extends Enum>) enumtargetclass, enumname);
			return enumres;
		} catch (IllegalArgumentException e) {
			throw new ConversionFailedException("Failed to convert to enum class: " + enumtargetclass, e);
		}
	}

	private static Object convertDefaultToIterableTargetClass(ConversionContext conversioncontext, Object value) {
		//converting to a list, collection, or iterable
		ConversionContext elemcontext = conversioncontext.genericChildContext(0);
		if (value instanceof StructuredListTaskResult) {
			List<StructuredTaskResult> proxysubjectlist = new ArrayList<>();
			((StructuredListTaskResult) value).forEach(etid -> {
				proxysubjectlist.add(etid);
			});
			return new ProxyList(elemcontext, proxysubjectlist, Object.class);
		}
		if (value instanceof StructuredTaskResult) {
			TaskResultResolver resultresolver = elemcontext.getTaskResultResolver();
			if (resultresolver == null) {
				throw new ConversionFailedException(
						"Failed to convert structured task result without task result resolver.");
			}
			TaskResultDependencyHandle dephandle = ((StructuredTaskResult) value)
					.toResultDependencyHandle(resultresolver);
			Object depval = dephandle.get();
			if (depval == null) {
				return null;
			}
			Class<? extends Object> depvalclass = depval.getClass();
			if (!depvalclass.isArray() && !Iterable.class.isAssignableFrom(depvalclass)) {
				//wrapping an object into a singleton
				//pass the structured task result as the element, converted when accessed
				dephandle.setTaskOutputChangeDetector(CommonTaskOutputChangeDetector.notInstanceOf(Iterable.class));
				dephandle.setTaskOutputChangeDetector(CommonTaskOutputChangeDetector.IS_NOT_ARRAY);
				return ImmutableUtils.singletonList(value);
			}
			value = depval;
		}
		Class<?> valueclass = value.getClass();
		if (valueclass.isArray()) {
			//XXX should we clone the array?
			return ImmutableUtils.unmodifiableReflectionArrayList(value);
		}
		if (Iterable.class.isAssignableFrom(valueclass)) {
			//value is iterable
			return ImmutableUtils.makeImmutableList((Iterable<?>) value);
		}
		return ImmutableUtils.singletonList(value);
	}

	private static Object convertDefaultToMapTargetClass(ConversionContext conversioncontext, Object value) {
		if (value instanceof StructuredMapTaskResult) {
			Map<String, StructuredTaskResult> proxysubjectmap = new TreeMap<>(StringUtils.nullsFirstStringComparator());
			((StructuredMapTaskResult) value).forEach((key, vtid) -> {
				proxysubjectmap.put(key, vtid);
			});
			return new ProxyMap(conversioncontext.genericChildContext(0), conversioncontext.genericChildContext(1),
					proxysubjectmap, Object.class, Object.class);
		}
		if (value instanceof BuildTargetTaskResult) {
			Map<String, StructuredObjectTaskResult> proxysubjectmap = new TreeMap<>(
					StringUtils.nullsFirstStringComparator());
			((BuildTargetTaskResult) value).getTaskResultIdentifiers().forEach((key, vtid) -> {
				proxysubjectmap.put(key, new SimpleStructuredObjectTaskResult(vtid));
			});
			return new ProxyMap(conversioncontext.genericChildContext(0), conversioncontext.genericChildContext(1),
					proxysubjectmap, Object.class, Object.class);
		}
		Object untaskedvalue = deTaskResultize(conversioncontext, value);
		if (untaskedvalue == null) {
			return null;
		}
		Class<?> untaskedvalueclass = untaskedvalue.getClass();
		if (Map.class.isAssignableFrom(untaskedvalueclass)) {
			return untaskedvalue;
		}
		throw new ConversionFailedException("Failed to convert " + untaskedvalueclass.getName() + " (" + untaskedvalue
				+ ") to " + Map.class.getName() + ".");
	}

	private static Object convertDefaultToArrayTargetClass(ConversionContext conversioncontext, Object value,
			final Class<?> targetclass) {
		Object untaskedvalue = deTaskResultize(conversioncontext, value);
		if (untaskedvalue == null) {
			return null;
		}
		Class<?> untaskedvalueclass = untaskedvalue.getClass();
		Class<?> componentclass = targetclass.getComponentType();
		if (untaskedvalueclass.isArray()) {
			int size = Array.getLength(untaskedvalue);
			Object resarray = Array.newInstance(componentclass, size);
			for (int i = 0; i < size; i++) {
				Object e = Array.get(untaskedvalue, i);
				Array.set(resarray, i, convert(conversioncontext, e, componentclass));
			}
			return resarray;
		}
		if (Iterable.class.isAssignableFrom(untaskedvalueclass)) {
			ArrayList<Object> items;
			if (Collection.class.isAssignableFrom(untaskedvalueclass)) {
				items = new ArrayList<>((Collection<?>) untaskedvalue);
			} else {
				items = new ArrayList<>();
				for (Object e : (Iterable<?>) untaskedvalue) {
					items.add(e);
				}
			}
			int size = items.size();
			Object resarray = Array.newInstance(componentclass, size);
			for (int i = 0; i < size; i++) {
				Object e = items.get(i);
				Array.set(resarray, i, convert(conversioncontext, e, componentclass));
			}
			return resarray;
		}
		//wrap in a singleton array
		Object single = convert(conversioncontext, untaskedvalue, componentclass);
		Object resarray = Array.newInstance(componentclass, 1);
		Array.set(resarray, 0, single);
		return resarray;
	}

	private static Class<?> getParentInterface(Class<?> c, String itfname) {
		if (c.getName().equals(itfname)) {
			return c;
		}
		for (Class<?> itf : c.getInterfaces()) {
			if (hasParentInterface(itf, itfname)) {
				return itf;
			}
		}
		return null;
	}

	private static boolean hasParentInterface(Class<?> c, String itfname) {
		return getParentInterface(c, itfname) != null;
	}

}
