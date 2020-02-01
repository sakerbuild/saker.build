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
package saker.build.thirdparty.saker.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import saker.build.thirdparty.saker.util.Reflector;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.StreamUtils;

/**
 * Utility class containing functions related to reflection, types, and their manipulations.
 */
public class ReflectUtils {
	/**
	 * Looks up the primitive class instance for the specified name.
	 * 
	 * @param name
	 *            The name of the primitive class.
	 * @return The primitive class, or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see Class#getName()
	 */
	public static Class<?> primitiveNameToPrimitiveClass(String name) throws NullPointerException {
		Objects.requireNonNull(name, "name");
		return NAME_TO_PRIMITIVE_MAP.get(name);
	}

	/**
	 * Primitivises the argument class.
	 * <p>
	 * If the argument class is a boxed type of a primitive class, then the primitive class will be returned for it. In
	 * any other cases, the argument class will be returned.
	 * <p>
	 * Note that {@link Void} will be converted to <code>void.class</code>.
	 * 
	 * @param clazz
	 *            The class.
	 * @return The primitivized class.
	 */
	public static Class<?> primitivize(Class<?> clazz) {
		return CLASS_TO_PRIMITIVE_MAP.getOrDefault(clazz, clazz);
	}

	/**
	 * Unprimitivises the argument class.
	 * <p>
	 * If the argument class is a primitive class, then the boxed type will be returned for it. In any other cases, the
	 * argument class will be returned.
	 * <p>
	 * Note that <code>void.class</code> will be converted to {@link Void}.
	 * 
	 * @param clazz
	 *            The class.
	 * @return The unprimitivized class.
	 */
	public static Class<?> unprimitivize(Class<?> clazz) {
		return PRIMITIVE_TO_CLASS_MAP.getOrDefault(clazz, clazz);
	}

	/**
	 * Checks if the argument class is a boxed primitive.
	 * <p>
	 * The class {@link Void} is considered to be a boxed primitive.
	 * 
	 * @param clazz
	 *            The class.
	 * @return <code>true</code> if the argument is a boxed primitive.
	 */
	public static boolean isBoxedPrimitive(Class<?> clazz) {
		return CLASS_TO_PRIMITIVE_MAP.containsKey(clazz);
	}

	/**
	 * Gets the default value for a primitive class.
	 * <p>
	 * The argument may be a direct primitive class (such as <code>int.class</code>), or a boxed type (such as
	 * {@link Integer}).
	 * 
	 * @param primitiveclazz
	 *            The primitive class to get the default value for.
	 * @return The default value, or <code>null</code> if not found. (In which case <code>null</code> would be the
	 *             default value, as the argument is a reference type.)
	 */
	public static Object getPrimitiveClassDefaultValue(Class<?> primitiveclazz) {
		return PRIMITIVE_DEFAULTS.get(primitiveclazz);
	}

	/**
	 * Checks if the argument classes represent the same classes in regards with primitive types.
	 * <p>
	 * The function converts the argument classes to their primitive counterparts if any, and checks if they equal. The
	 * primitive counterpart is determined using {@link #primitivize(Class)}.
	 * <p>
	 * If both arguments are <code>null</code>, <code>true</code> is returned.
	 * 
	 * @param c1
	 *            The first class.
	 * @param c2
	 *            The second class.
	 * @return <code>true</code> if the classes are the same in regards with the primitive types.
	 */
	public static boolean isSamePrimitive(Class<?> c1, Class<?> c2) {
		return Objects.equals(primitivize(c1), primitivize(c2));
	}

	/**
	 * Gets the constructor with the specified parameter types from a class, throws an {@link AssertionError} if fails.
	 * <p>
	 * This function will use {@link Class#getConstructor(Class...)} to look up the appropriate constructor.
	 * 
	 * @param <T>
	 *            The type of the class.
	 * @param clazz
	 *            The class to look up the constructor of.
	 * @param argtypes
	 *            The argument types of the constructor.
	 * @return The found constructor.
	 * @throws NullPointerException
	 *             If the class argument is <code>null</code>.
	 * @throws AssertionError
	 *             If the constructor was not found, or not accessible due to security restrictions.
	 */
	public static <T> Constructor<T> getConstructorAssert(Class<T> clazz, Class<?>... argtypes)
			throws NullPointerException, AssertionError {
		Objects.requireNonNull(clazz, "class");
		try {
			return clazz.getConstructor(argtypes);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new AssertionError(e);
		}
	}

	/**
	 * Gets the declared constructor with the specified parameter types from a class, throws an {@link AssertionError}
	 * if fails.
	 * <p>
	 * This function will use {@link Class#getDeclaredConstructor(Class...)} to look up the appropriate constructor.
	 * 
	 * @param <T>
	 *            The type of the class.
	 * @param clazz
	 *            The class to look up the constructor of.
	 * @param argtypes
	 *            The argument types of the constructor.
	 * @return The found constructor.
	 * @throws NullPointerException
	 *             If the class argument is <code>null</code>.
	 * @throws AssertionError
	 *             If the constructor was not found, or not accessible due to security restrictions.
	 */
	public static <T> Constructor<T> getDeclaredConstructorAssert(Class<T> clazz, Class<?>... argtypes)
			throws NullPointerException, AssertionError {
		Objects.requireNonNull(clazz, "class");
		try {
			return clazz.getDeclaredConstructor(argtypes);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new AssertionError(e);
		}
	}

	/**
	 * Gets the method with the specified parameter types and name from a class, throws an {@link AssertionError} if
	 * fails.
	 * <p>
	 * This function will use {@link Class#getMethod(String, Class...)} to look up the appropriate method.
	 * 
	 * @param clazz
	 *            The class to look up the method of.
	 * @param methodname
	 *            The name of the method.
	 * @param argtypes
	 *            The argument types of the method.
	 * @return The found method.
	 * @throws NullPointerException
	 *             If The class or method name arguments are <code>null</code>.
	 * @throws AssertionError
	 *             If the method was not found, or not accessible due to security restrictions.
	 */
	public static Method getMethodAssert(Class<?> clazz, String methodname, Class<?>... argtypes)
			throws NullPointerException, AssertionError {
		Objects.requireNonNull(clazz, "class");
		Objects.requireNonNull(methodname, "method name");
		try {
			return clazz.getMethod(methodname, argtypes);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new AssertionError(e);
		}
	}

	/**
	 * Gets the declared method with the specified parameter types and name from a class, throws an
	 * {@link AssertionError} if fails.
	 * <p>
	 * This function will use {@link Class#getDeclaredMethod(String, Class...)} to look up the appropriate method.
	 * 
	 * @param clazz
	 *            The class to look up the method of.
	 * @param methodname
	 *            The name of the method.
	 * @param argtypes
	 *            The argument types of the method.
	 * @return The found method.
	 * @throws NullPointerException
	 *             If The class or method name arguments are <code>null</code>.
	 * @throws AssertionError
	 *             If the method was not found, or not accessible due to security restrictions.
	 */
	public static Method getDeclaredMethodAssert(Class<?> clazz, String methodname, Class<?>... argtypes)
			throws NullPointerException, AssertionError {
		Objects.requireNonNull(clazz, "class");
		Objects.requireNonNull(methodname, "method name");
		try {
			return clazz.getDeclaredMethod(methodname, argtypes);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new AssertionError(e);
		}
	}

	/**
	 * Gets the field with the specified name from a class, throws an {@link AssertionError} if fails.
	 * <p>
	 * This function will use {@link Class#getField(String)} to look up the appropriate field.
	 * 
	 * @param clazz
	 *            The class to look up the field of.
	 * @param fieldname
	 *            The name of the field.
	 * @return The found field.
	 * @throws NullPointerException
	 *             If The class or field name arguments are <code>null</code>.
	 * @throws AssertionError
	 *             If the field was not found, or not accessible due to security restrictions.
	 */
	public static Field getFieldAssert(Class<?> clazz, String fieldname) throws NullPointerException, AssertionError {
		Objects.requireNonNull(clazz, "class");
		Objects.requireNonNull(fieldname, "field name");
		try {
			return clazz.getField(fieldname);
		} catch (NoSuchFieldException | SecurityException e) {
			throw new AssertionError(e);
		}
	}

	/**
	 * Gets the declared field with the specified name from a class, throws an {@link AssertionError} if fails.
	 * <p>
	 * This function will use {@link Class#getDeclaredField(String)} to look up the appropriate field.
	 * 
	 * @param clazz
	 *            The class to look up the field of.
	 * @param fieldname
	 *            The name of the field.
	 * @return The found field.
	 * @throws NullPointerException
	 *             If The class or field name arguments are <code>null</code>.
	 * @throws AssertionError
	 *             If the field was not found, or not accessible due to security restrictions.
	 */
	public static Field getDeclaredFieldAssert(Class<?> clazz, String fieldname)
			throws NullPointerException, AssertionError {
		Objects.requireNonNull(clazz, "class");
		Objects.requireNonNull(fieldname, "field name");
		try {
			return clazz.getDeclaredField(fieldname);
		} catch (NoSuchFieldException | SecurityException e) {
			throw new AssertionError(e);
		}
	}

	/**
	 * Sets the value of the field, handling <code>null</code> values for primitive types.
	 * <p>
	 * The field will be set to accessible by calling {@link Field#setAccessible(boolean)}.
	 * <p>
	 * If the field has a primitive type, and the value is <code>null</code>, the default value will be set for the
	 * field.
	 * <p>
	 * In any other cases, the {@link Field#set(Object, Object)} is called with the given arguments.
	 * 
	 * @param field
	 *            The field to set.
	 * @param obj
	 *            The instance object. May be <code>null</code> for static fields.
	 * @param value
	 *            The value to set.
	 * @throws IllegalAccessException
	 *             See {@link Field#set(Object, Object)}.
	 * @throws NullPointerException
	 *             If the field is <code>null</code>.
	 * @see Field#set(Object, Object)
	 */
	public static void setFieldValue(Field field, Object obj, Object value)
			throws IllegalAccessException, NullPointerException {
		Objects.requireNonNull(field, "field");
		Class<?> ftype = field.getType();
		field.setAccessible(true);
		if (value == null && ftype.isPrimitive()) {
			if (ftype.equals(boolean.class)) {
				field.setBoolean(obj, false);
			} else if (ftype.equals(byte.class)) {
				field.setByte(obj, (byte) 0);
			} else if (ftype.equals(short.class)) {
				field.setShort(obj, (short) 0);
			} else if (ftype.equals(int.class)) {
				field.setInt(obj, 0);
			} else if (ftype.equals(long.class)) {
				field.setLong(obj, 0);
			} else if (ftype.equals(float.class)) {
				field.setFloat(obj, 0);
			} else if (ftype.equals(double.class)) {
				field.setDouble(obj, 0);
			} else if (ftype.equals(char.class)) {
				field.setChar(obj, (char) 0);
			} else {
				throw new AssertionError("Unknown primitive type: " + ftype);
			}
		} else {
			field.set(obj, value);
		}
	}

	/**
	 * Gets the value of a field.
	 * <p>
	 * The field will be set to accessible by calling {@link Field#setAccessible(boolean)}.
	 * 
	 * @param field
	 *            The field to get the value of.
	 * @param obj
	 *            The instance object for the field. May be <code>null</code> for static fields.
	 * @return The value of the field.
	 * @throws IllegalAccessException
	 *             See {@link Field#get(Object)}.
	 * @see Field#get(Object)
	 */
	public static Object getFieldValue(Field field, Object obj) throws IllegalAccessException {
		Objects.requireNonNull(field, "field");
		field.setAccessible(true);
		return field.get(obj);
	}

	/**
	 * Invokes the specified constructor with the given array of arguments.
	 * <p>
	 * The constructor will be set to accessible by calling {@link Constructor#setAccessible(boolean)}.
	 * 
	 * @param constructor
	 *            The constructor to invoke.
	 * @param args
	 *            The arguments to pass to the constructor.
	 * @return The created new instance.
	 * @throws IllegalAccessException
	 *             See {@link Constructor#newInstance(Object...)}.
	 * @throws IllegalArgumentException
	 *             See {@link Constructor#newInstance(Object...)}.
	 * @throws InvocationTargetException
	 *             See {@link Constructor#newInstance(Object...)}.
	 * @throws InstantiationException
	 *             See {@link Constructor#newInstance(Object...)}.
	 * @throws NullPointerException
	 *             If the constructor is <code>null</code>.
	 */
	public static <T> T invokeConstructor(Constructor<T> constructor, Object... args) throws IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, InstantiationException, NullPointerException {
		Objects.requireNonNull(constructor, "constructor");
		constructor.setAccessible(true);
		return constructor.newInstance(args);
	}

	/**
	 * Invokes the specified method with the given array of arguments.
	 * <p>
	 * The method will be set to accessible by calling {@link Method#setAccessible(boolean)}.
	 * 
	 * @param obj
	 *            The instance object to call the method on. May be <code>null</code> for static methods.
	 * @param method
	 *            The method to invoke.
	 * @param args
	 *            The arguments to pass to the method.
	 * @return The result of the method invocation.
	 * @throws IllegalAccessException
	 *             See {@link Method#invoke(Object, Object...)}.
	 * @throws IllegalArgumentException
	 *             See {@link Method#invoke(Object, Object...)}.
	 * @throws InvocationTargetException
	 *             See {@link Method#invoke(Object, Object...)}.
	 * @throws NullPointerException
	 *             If the method is <code>null</code>.
	 */
	public static Object invokeMethod(Object obj, Method method, Object... args)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NullPointerException {
		Objects.requireNonNull(method, "method");
		method.setAccessible(true);
		return method.invoke(obj, args);
	}

	/**
	 * Creates a new instance of the argument class by invoking the no-arg constructor.
	 * <p>
	 * The no-arg constructor will be looked up, set to accessible, and invoked.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * invokeConstructor(clazz.getDeclaredConstructor());
	 * </pre>
	 * 
	 * @param clazz
	 *            The class to create a new instance of.
	 * @return The newly created instance.
	 * @throws InstantiationException
	 *             See {@link Constructor#newInstance(Object...)}.
	 * @throws IllegalAccessException
	 *             See {@link Constructor#newInstance(Object...)}.
	 * @throws IllegalArgumentException
	 *             See {@link Constructor#newInstance(Object...)}.
	 * @throws InvocationTargetException
	 *             See {@link Constructor#newInstance(Object...)}.
	 * @throws NoSuchMethodException
	 *             See {@link Class#getDeclaredConstructor(Class...)}.
	 * @throws SecurityException
	 *             See {@link Constructor#newInstance(Object...)} and {@link Class#getDeclaredConstructor(Class...)}.
	 */
	public static <T> T newInstance(Class<T> clazz) throws InstantiationException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		return invokeConstructor(clazz.getDeclaredConstructor());
	}

	/**
	 * Checks if the argument object is an array.
	 * <p>
	 * It is determined by checking if its class is an {@linkplain Class#isArray() array}.
	 * 
	 * @param obj
	 *            The object to check.
	 * @return <code>true</code> if the argument is non-<code>null</code> and an array.
	 */
	public static boolean isArray(Object obj) {
		return obj != null && obj.getClass().isArray();
	}

	/**
	 * Creates an array with the given component type and length.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * Array.newInstance(component, length);
	 * </pre>
	 * 
	 * @param component
	 *            The component type.
	 * @param length
	 *            The length of the created array.
	 * @return An empty array with the given component type.
	 * @throws NullPointerException
	 *             If the component type is <code>null</code>.
	 * @throws NegativeArraySizeException
	 *             If the length is negative.
	 */
	public static Object createArray(Class<?> component, int length)
			throws NullPointerException, NegativeArraySizeException {
		Objects.requireNonNull(component, "component");
		return Array.newInstance(component, length);
	}

	/**
	 * Creates an empty array with the given component type.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * Array.newInstance(component, 0);
	 * </pre>
	 * 
	 * @param component
	 *            The component type.
	 * @return An empty array with the given component type.
	 * @throws NullPointerException
	 *             If the component type is <code>null</code>.
	 */
	public static Object createEmptyArray(Class<?> component) throws NullPointerException {
		Objects.requireNonNull(component, "component");
		return Array.newInstance(component, 0);
	}

	/**
	 * Wraps a single value into an array with a single element.
	 * 
	 * @param value
	 *            The value to wrap.
	 * @param componenttype
	 *            The component type of the created array.
	 * @return The array containing the single value.
	 * @throws NullPointerException
	 *             If the component type is <code>null</code>.
	 */
	public static Object wrapIntoSingletonArray(Object value, Class<?> componenttype) throws NullPointerException {
		Object result = createArray(componenttype, 1);
		Array.set(result, 0, value);
		return result;
	}

	/**
	 * Invokes clone on the argument array.
	 * <p>
	 * This method will always invoke <code>.clone()</code> on the array. If the array is a primitive array, it will be
	 * casted down and invoekd accordingly.
	 * <p>
	 * If the argument is <code>null</code>, <code>null</code> is returned.
	 * <p>
	 * This method creates a shallow clone, elements are not cloned.
	 * 
	 * @param array
	 *            The array to clone.
	 * @return The cloned array.
	 * @throws IllegalArgumentException
	 *             If the argument is not an array.
	 */
	public static Object cloneArray(Object array) throws IllegalArgumentException {
		if (array == null) {
			return null;
		}
		Class<?> objclass = array.getClass();
		Class<?> component = objclass.getComponentType();
		if (component == null) {
			throw new IllegalArgumentException("Not an array: " + objclass);
		}
		if (component.isPrimitive()) {
			if (component.equals(boolean.class)) {
				return ((boolean[]) array).clone();
			}
			if (component.equals(byte.class)) {
				return ((byte[]) array).clone();
			}
			if (component.equals(short.class)) {
				return ((short[]) array).clone();
			}
			if (component.equals(int.class)) {
				return ((int[]) array).clone();
			}
			if (component.equals(long.class)) {
				return ((long[]) array).clone();
			}
			if (component.equals(float.class)) {
				return ((float[]) array).clone();
			}
			if (component.equals(double.class)) {
				return ((double[]) array).clone();
			}
			if (component.equals(char.class)) {
				return ((char[]) array).clone();
			}
			throw new AssertionError("Unknown primitive type: " + component);
		}
		return ((Object[]) array).clone();
	}

	/**
	 * Gets a class instance that has the component type as the argument.
	 * <p>
	 * This method is the same as:
	 * 
	 * <pre>
	 * createEmptyArray(component).getClass();
	 * </pre>
	 * 
	 * The implementation of this method may be optimized on JDK12+, where the <code>Class.arrayType()</code> function
	 * is available.
	 * 
	 * @param component
	 *            The component type.
	 * @return The class with the given component type.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static Class<?> getArrayClassWithComponent(Class<?> component) throws NullPointerException {
		Objects.requireNonNull(component, "component");
		//XXX use arrayType() on JDK 12+ (turns out it does the same thing)
		//https://download.java.net/java/early_access/jdk13/docs/api/java.base/java/lang/Class.html#arrayType()
		return createEmptyArray(component).getClass();
	}

	/**
	 * Gets the hash code of the argument, assuming that it is a valid value for an annotation function.
	 * <p>
	 * This function calls {@link ArrayUtils#arrayHashCode(Object)} for the argument if it is an array, else calls
	 * {@link Object#hashCode()} on it. This behaviour is in line with the requirements of
	 * {@link Annotation#hashCode()}.
	 * <p>
	 * Although the <code>null</code> is not a valid annotation value, this function will return 0 for it.
	 * 
	 * @param value
	 *            The value to get the hash code of.
	 * @return The computed hash code.
	 */
	public static int annotationValueHashCode(Object value) {
		if (value == null) {
			return 0;
		}
		Class<? extends Object> type = value.getClass();
		if (type.isArray()) {
			return ArrayUtils.arrayHashCode(value);
		}
		return value.hashCode();
	}

	/**
	 * Converts the argument objects to a string representation, assuming that it is a value value for an annotation
	 * function.
	 * <p>
	 * This function calls {@link ArrayUtils#arrayToString(Object)} if the argument is an array, else calls
	 * {@link Object#toString()} on it. This behaviour is in line with the typical implementation of
	 * {@link Annotation#toString()}.
	 * <p>
	 * Although the <code>null</code> is not a valid annotation value, this function will return <code>"null"</code> for
	 * it.
	 * 
	 * @param value
	 *            The value to get the string representation of.
	 * @return The string representation.
	 */
	public static String annotationValueToString(Object value) {
		if (value == null) {
			//should not happen, but better safe than sorry
			return "null";
		}
		Class<? extends Object> type = value.getClass();
		if (type.isArray()) {
			return ArrayUtils.arrayToString(value);
		}
		return value.toString();
	}

	/**
	 * Checks if the arguments equal, assuming that they are valid values for an annotation function.
	 * <p>
	 * This function calls {@link ArrayUtils#arraysEqual(Object, Object)} if the first argument is an array, else calls
	 * {@link Object#equals(Object)} on the first argument. This behaviour is in line with the requirements of
	 * {@link Annotation#equals(Object)}.
	 * <p>
	 * Although the <code>null</code> is not a valid annotation value, this function will return <code>false</code> if
	 * only one of the arguments are <code>null</code>, and <code>true</code> if both of them are.
	 * 
	 * @param v1
	 *            The first value.
	 * @param v2
	 *            The second value.
	 * @return <code>true</code> if the arguments are considered to be equal.
	 */
	public static boolean annotationValuesEqual(Object v1, Object v2) {
		if (v1 == v2) {
			return true;
		}
		if (v1 == null || v2 == null) {
			return false;
		}
		Class<?> type = v1.getClass();

		if (type.isArray()) {
			return ArrayUtils.arraysEqual(v1, v2);
		}
		return v1.equals(v2);
	}

	/**
	 * Gets the annotation from the given array that has the same type as the specified type.
	 * <p>
	 * If any of the arguments are <code>null</code>, this method returns <code>null</code>.
	 * 
	 * @param annotations
	 *            The annotations array to search in.
	 * @param annottype
	 *            The annotation type to look for.
	 * @return The found annotation with the given type of <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Annotation> T getAnnotation(Annotation[] annotations, Class<T> annottype) {
		if (annotations == null || annottype == null) {
			return null;
		}
		for (Annotation a : annotations) {
			if (a.annotationType() == annottype) {
				return (T) a;
			}
		}
		return null;
	}

	/**
	 * Gets a {@link MethodHandle} to the default method implementation of an interface method.
	 * <p>
	 * This method was tested to handle different Java runtime versions appropriately.
	 * 
	 * @param method
	 *            The interface method to get the default method handle for.
	 * @return The method handle.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws ReflectiveOperationException
	 *             If the operation fails.
	 */
	public static MethodHandle getDefaultMethodHandle(Method method)
			throws NullPointerException, ReflectiveOperationException {
		Objects.requireNonNull(method, "method");
		Class<?> declaringClass = method.getDeclaringClass();
		return Reflector.getDefaultMethodHandle(method, declaringClass);
	}

	/**
	 * Invokes the default implementation of an interface method.
	 * <p>
	 * This method was tested to handle different Java runtime versions appropriately.
	 * 
	 * @param method
	 *            The default interface method to invoke.
	 * @param object
	 *            The interface object to invoke the method on.
	 * @param args
	 *            The arguments to the invocation.
	 * @return The return value of the method invocation.
	 * @throws NullPointerException
	 *             If the method is <code>null</code>.
	 * @throws Throwable
	 *             Any exception from the method call.
	 * @see #getDefaultMethodHandle(Method)
	 */
	public static Object invokeDefaultMethodOn(Method method, Object object, Object... args)
			throws NullPointerException, Throwable {
		Objects.requireNonNull(method, "method");
		return getDefaultMethodHandle(method).bindTo(object).invokeWithArguments(args);
	}

	/**
	 * Find the type instance with the given name in a hierarchy of classes.
	 * <p>
	 * This method will try to find the type instance that has the same name as the argument. The method will look at
	 * the specified class argument, and check the super classes and super interfaces if they have the same name.
	 * <p>
	 * <b>Note:</b> Unlike {@link #findInterfaceWithNameInHierarchy(Class, String)} or
	 * {@link #findClassWithNameInHierarchy(Class, String)}, this method will find the interfaces and classes too.
	 * 
	 * @param basetype
	 *            The base type to start the search at.
	 * @param typename
	 *            The type name to search for.
	 * @return The found type instance or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static Class<?> findTypeWithNameInHierarchy(Class<?> basetype, String typename) throws NullPointerException {
		Objects.requireNonNull(basetype, "base type");
		Objects.requireNonNull(typename, "type name");
		return findTypeWithNameInHierarchyImpl(basetype, typename);
	}

	/**
	 * Find the interface instance with the given name in a hierarchy of classes.
	 * <p>
	 * This method will try to find the interface instance that has the same name as the argument. The method will look
	 * at the specified class argument, and check the super classes and super interfaces if they have the same name.
	 * <p>
	 * <b>Note:</b> Unlike {@link #findTypeWithNameInHierarchy(Class, String)} or
	 * {@link #findClassWithNameInHierarchy(Class, String)}, this method only searches for
	 * {@linkplain Class#isInterface() interfaces}.
	 * 
	 * @param basetype
	 *            The base type to start the search at.
	 * @param itfname
	 *            The interface name to search for.
	 * @return The found interface instance or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static Class<?> findInterfaceWithNameInHierarchy(Class<?> basetype, String itfname)
			throws NullPointerException {
		Objects.requireNonNull(basetype, "base type");
		Objects.requireNonNull(itfname, "interface name");
		if (basetype.isInterface() && basetype.getName().equals(itfname)) {
			return basetype;
		}
		return findInterfaceWithNameInHierarchyImpl(basetype, itfname);
	}

	/**
	 * Find the class instance with the given name in a hierarchy of classes.
	 * <p>
	 * This method will try to find the class instance that has the same name as the argument. The method will look at
	 * the specified class argument, and check the super classes if they have the same name.
	 * <p>
	 * <b>Note:</b> Unlike {@link #findTypeWithNameInHierarchy(Class, String)} or
	 * {@link #findInterfaceWithNameInHierarchy(Class, String)}, this method only searches for
	 * {@linkplain Class#isInterface() non-interfaces}.
	 * 
	 * @param basetype
	 *            The base type to start the search at.
	 * @param classname
	 *            The class name to search for.
	 * @return The found class instance or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static Class<?> findClassWithNameInHierarchy(Class<?> basetype, String classname)
			throws NullPointerException {
		Objects.requireNonNull(basetype, "base type");
		Objects.requireNonNull(classname, "class name");
		if (!basetype.isInterface() && basetype.getName().equals(classname)) {
			return basetype;
		}
		return findClassWithNameInHierarchyImpl(basetype, classname);
	}

	/**
	 * Gets all inherited types of the argument type.
	 * <p>
	 * This method will put all superclasses and all interfaces into the result set, and return that. The result set
	 * will also contain the argument type as well.
	 * <p>
	 * If the argument is <code>null</code>, <code>null</code> is returned.
	 * <p>
	 * The returned set has a deterministic iteration order, meaning that invoking this method between different
	 * invocations of the Java Virtual Machine will produce the same set with the same iteration order.
	 * 
	 * @param type
	 *            The type to get all inherited types of.
	 * @return A set of types which are extended or implemented by the argument.
	 */
	public static Set<Class<?>> getAllInheritedTypes(Class<?> type) {
		if (type == null) {
			return null;
		}
		Set<Class<?>> result = new LinkedHashSet<>();
		result.add(type);
		collectAllInheritedTypesImpl(type, result);
		return result;
	}

	/**
	 * Gets all inherited types of the argument type and maps them to their inheritance distance from the argument.
	 * <p>
	 * This method collects all classes and interfaces that the argument extends and implements. Each type is mapped to
	 * the inheritance distance of the type in relation to the argument.
	 * <p>
	 * The inheritance distance is defined as the number of 'hops' it takes to reach the inheritance declaration from
	 * the starting type.
	 * <p>
	 * E.g. for the type Integer:
	 * 
	 * <pre>
	 * {@link Integer} : 0
	 * {@link Number} : 1 (via <code>Integer</code>)
	 * {@link Comparable Comparable&lt;Integer&gt;} : 1 (via <code>Integer</code>) 
	 * {@link Serializable} : 2 (via <code>Number</code>)
	 * {@link Object} : 2 (via <code>Number</code>)
	 * </pre>
	 * 
	 * The inheritance distances are minimized, meaning that if a type is present via multiple types (i.e. an interface
	 * is implemented by multiple supertypes), then the lesser of them will be taken.
	 * 
	 * @param type
	 *            The type to get the inherited types of.
	 * @return The inherited types mapped to their distance, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static Map<Class<?>, Integer> getAllInheritedTypesWithDistance(Class<?> type) {
		if (type == null) {
			return null;
		}
		Map<Class<?>, Integer> result = new LinkedHashMap<>();
		result.put(type, 0);
		collectAllInheritedTypesWithDistanceImpl(type, 1, result);
		return result;
	}

	/**
	 * Gets the names of all inherited types of the argument type.
	 * <p>
	 * This method will put the names of all superclasses and all interfaces into the result set, and return that. The
	 * result set will also contain the argument type name as well.
	 * <p>
	 * If the argument is <code>null</code>, <code>null</code> is returned.
	 * <p>
	 * The returned set has a deterministic iteration order, meaning that invoking this method between different
	 * invocations of the Java Virtual Machine will produce the same set with the same iteration order.
	 * 
	 * @param type
	 *            The to get all inherited type names for.
	 * @return A set of type names which are extended or implemented by the argument.
	 * @see Class#getName()
	 */
	public static Set<String> getAllInheritedTypeNames(Class<?> type) {
		if (type == null) {
			return null;
		}
		Set<String> result = new LinkedHashSet<>();
		result.add(type.getName());
		collectAllInheritedClassNamesImpl(type, result);
		return result;
	}

	/**
	 * Gets all interfaces of the argument type.
	 * <p>
	 * This method will collect all interfaces that the argument type extends. If the argument is already an interface,
	 * it will be part of the result set.
	 * <p>
	 * If the argument is <code>null</code>, <code>null</code> is returned.
	 * <p>
	 * The returned set has a deterministic iteration order, meaning that invoking this method between different
	 * invocations of the Java Virtual Machine will produce the same set with the same iteration order.
	 * 
	 * @param type
	 *            The type to get the interfaces of.
	 * @return A set of interfaces that the argument extends or <code>null</code> if the argument is <code>null</code>.
	 * @see Class#isInterface()
	 */
	public static Set<Class<?>> getAllInterfaces(Class<?> type) {
		if (type == null) {
			return null;
		}
		Set<Class<?>> result = new LinkedHashSet<>();
		if (type.isInterface()) {
			result.add(type);
		}
		collectAllInterfacesImpl(type, result);
		return result;
	}

	/**
	 * Gets the set of interfaces the argument class implements.
	 * <p>
	 * This method will collect all the interfaces that a type implements. The returned set will be reduced, meaning
	 * that if an interface I is present, none of the interfaces that I extends will be in the resulting set. E.g. if a
	 * class implements {@link List}, it will not contain the interfaces for {@link Collection}, or {@link Iterable}.
	 * <p>
	 * If the argument is already an interface, the returned set will contain only the argument.
	 * <p>
	 * The returned set has a deterministic iteration order, meaning that invoking this method between different
	 * invocations of the Java Virtual Machine will produce the same set with the same iteration order.
	 * 
	 * @param type
	 *            The class to get the implemented interfaces of.
	 * @return A set of interfaces that the class implements.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static Set<Class<?>> getInterfaces(Class<?> type) throws NullPointerException {
		Objects.requireNonNull(type, "type");
		//use linked hash set to have a deterministic order
		Set<Class<?>> result = new LinkedHashSet<>();
		if (type.isInterface()) {
			result.add(type);
			return result;
		}
		collectInterfaces(type, result);
		return result;
	}

	/**
	 * Reduces the argument type set to only contain types which do not have assignable duplicates.
	 * <p>
	 * This method will remove any types in the argument collection for which there is a subclass or subinterface
	 * present.
	 * <p>
	 * E.g. If the type {@link Collection} and {@link List} is in it, then {@link Collection} will be removed, as it is
	 * already present in {@link List}, as that extends it.
	 * <p>
	 * Only the types will be removed which have at least one subclass of it present. E.g. If the argument contains
	 * {@link Number}, {@link Float}, and {@link Double}, only {@link Number} will be removed.
	 * 
	 * @param classes
	 *            The classes to reduce.
	 * @see Class#isAssignableFrom(Class)
	 */
	public static void reduceAssignableTypes(Set<Class<?>> classes) {
		if (classes == null) {
			return;
		}
		for (Iterator<Class<?>> it = classes.iterator(); it.hasNext();) {
			Class<?> c = it.next();
			for (Class<?> c2 : classes) {
				if (c == c2) {
					continue;
				}
				if (c.isAssignableFrom(c2)) {
					it.remove();
					break;
				}
			}
		}
	}

	/**
	 * Removes the elements from the argument iterable which are not interface types.
	 * <p>
	 * If the argument is <code>null</code>, this method does nothing.
	 * 
	 * @param classes
	 *            The iterable of types.
	 * @see Class#isInterface()
	 */
	public static void removeNonInterfaces(Iterable<Class<?>> classes) {
		if (classes == null) {
			return;
		}
		for (Iterator<Class<?>> it = classes.iterator(); it.hasNext();) {
			Class<?> c = it.next();
			if (!c.isInterface()) {
				it.remove();
			}
		}
	}

	/**
	 * Checks if the argument type represents either an enum class, or an anonymous enum inner class.
	 * <p>
	 * For enum objects which are inner classes as well, {@link Class#isEnum()} will return <code>false</code>. E.g.:
	 * 
	 * <pre>
	 * public enum MyEnum {
	 * 	FIRST_ENUM,
	 * 	SECOND_ENUM {
	 * 	};
	 * }
	 * // MyEnum.FIRST_ENUM.getClass() will return MyEnum.class
	 * // MyEnum.SECOND_ENUM.getClass() will return a type that is a subclass of MyEnum, but 
	 * </pre>
	 * 
	 * This method will check if the given type is either an enum, or an anonymous inner enum.
	 * <p>
	 * This method returns <code>false</code> if the argument is <code>null</code>.
	 * 
	 * @param type
	 *            The type to check.
	 * @return <code>true</code> if the argument type is an enum or anonymous inner enum class.
	 */
	public static boolean isEnumOrEnumAnonymous(Class<?> type) {
		if (type == null) {
			return false;
		}
		return type != Enum.class && Enum.class.isAssignableFrom(type);
	}

	/**
	 * Gets the package name of the given type based on its {@linkplain Class#getName() name}.
	 * <p>
	 * The name of the type will be retrieved, and the part before the last <code>'.'</code> dot character will be
	 * returned. An empty string is returned if no dot character was found, representing the default package.
	 * <p>
	 * If the argument type is an inner class, the package name is returned nonetheless.
	 * <p>
	 * <code>null</code> is returned if the argument is <code>null</code>.
	 * 
	 * @param type
	 *            The type.
	 * @return The package name of the type derived from its name.
	 */
	public static String getPackageNameOf(Class<?> type) {
		if (type == null) {
			return null;
		}
		String name = type.getName();
		int idx = name.lastIndexOf('.');
		if (idx < 0) {
			return "";
		}
		return name.substring(0, idx);
	}

	/**
	 * Gets the enclosing element {@linkplain Class#getCanonicalName() canonical name} of a given type.
	 * <p>
	 * This method returns the canonical name of the enclosing Java element that declares the given type. For inner
	 * types, this returns the canonical name of the enclosing type. For top level type, this returns the name of the
	 * package it is declared in.
	 * 
	 * @param type
	 *            The type.
	 * @return The canonical name of the enclosing element.
	 */
	public static String getEnclosingCanonicalNameOf(Class<?> type) {
		if (type == null) {
			return null;
		}
		Class<?> enclosing = type.getEnclosingClass();
		if (enclosing != null) {
			return enclosing.getCanonicalName();
		}
		String name = type.getName();
		int idx = name.lastIndexOf('.');
		if (idx < 0) {
			return "";
		}
		return name.substring(0, idx);
	}

	/**
	 * Checks if the subject classloader has a given parent classloader in its parent hierarchy.
	 * <p>
	 * The subject classloader will be checked if it equals to the expected parent classloader. The same will be
	 * recursively checked for the parent classloader of the <code>subject</code>.
	 * <p>
	 * If the <code>parent</code> is <code>null</code>, this method will return <code>true</code>.
	 * 
	 * @param subject
	 *            The subject classloader to check if it has the given parent.
	 * @param parent
	 *            The parent to search for.
	 * @return <code>true</code> if the parent classloader was found in the parent hierarchy of the subject.
	 */
	public static boolean hasParentClassLoader(ClassLoader subject, ClassLoader parent) {
		if (parent == null) {
			return true;
		}
		while (subject != null) {
			if (subject.equals(parent)) {
				return true;
			}
			subject = subject.getParent();
		}
		return false;
	}

	/**
	 * Checks if the given type has directly extends or implements the given type.
	 * <p>
	 * This method will compare the given super type with the {@linkplain Class#getSuperclass() super class} and
	 * {@linkplain Class#getInterfaces() interfaces} of the examined type.
	 * 
	 * @param type
	 *            The type to examine.
	 * @param supertype
	 *            The super type to search for.
	 * @return <code>true</code> if either the super class or one of the implemented interfaces are the specified super
	 *             type.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static boolean hasDirectSuperClassOrInterface(Class<?> type, Class<?> supertype)
			throws NullPointerException {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(supertype, "super type");
		if (type.getSuperclass() == supertype) {
			return true;
		}
		for (Class<?> itf : type.getInterfaces()) {
			if (itf == supertype) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Gets the bytes of a resource from a {@link ClassLoader} given the name of the resource.
	 * <p>
	 * This function will open the resource stream from the classloader usin
	 * {@link ClassLoader#getResourceAsStream(String)} and read the stream fully.
	 * 
	 * @param classloader
	 *            The classloader to get the resource from.
	 * @param resourcename
	 *            The name of the resource.
	 * @return The bytes of the resource or <code>null</code> if the resource was not found, or an I/O error occurred.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static ByteArrayRegion getResourceBytes(ClassLoader classloader, String resourcename)
			throws NullPointerException {
		Objects.requireNonNull(classloader, "classloader");
		Objects.requireNonNull(resourcename, "resource name");
		try (InputStream is = classloader.getResourceAsStream(resourcename)) {
			if (is == null) {
				return null;
			}
			return StreamUtils.readStreamFully(is);
		} catch (IOException e) {
		}
		return null;
	}

	/**
	 * Tries to get the bytes for the given parameter class using its classloader.
	 * <p>
	 * This method gets the bytes for the corresponding class file from the defining classloader.
	 * <p>
	 * It is not ensured that the returned bytes are actually represent the given class parameter. <b>This method should
	 * be used with care and for testing purposes only.</b>
	 * 
	 * @param clazz
	 *            The class to get the bytes for.
	 * @return The bytes of the corresponding class file resource or <code>null</code> if not found.
	 */
	public static ByteArrayRegion getClassBytesUsingClassLoader(Class<?> clazz) {
		return getResourceBytes(clazz.getClassLoader(), clazz.getName().replace('.', '/') + ".class");
	}

	/**
	 * Validation method for ensuring that the argument is an interface type.
	 * 
	 * @param type
	 *            The type to validate.
	 * @return The argument.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see Class#isInterface()
	 */
	public static <T> Class<T> requireInterface(Class<T> type) throws NullPointerException {
		Objects.requireNonNull(type, "type");
		if (!type.isInterface()) {
			throw new IllegalArgumentException("Type is not an interface: " + type);
		}
		return type;
	}

	/**
	 * Gets the name of the argument {@link Class}, if non-<code>null</code>.
	 * 
	 * @param type
	 *            The type to get the name of.
	 * @return The name, or <code>null</code> if the argument is <code>null</code>.
	 * @see Class#getName()
	 */
	public static String getClassName(Class<?> type) {
		return type == null ? null : type.getName();
	}

	private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = new HashMap<>();
	private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_CLASS_MAP;
	private static final Map<Class<?>, Class<?>> CLASS_TO_PRIMITIVE_MAP;
	private static final Map<String, Class<?>> NAME_TO_PRIMITIVE_MAP = new TreeMap<>();
	static {
		PRIMITIVE_DEFAULTS.put(Byte.class, (byte) 0);
		PRIMITIVE_DEFAULTS.put(Short.class, (short) 0);
		PRIMITIVE_DEFAULTS.put(Integer.class, 0);
		PRIMITIVE_DEFAULTS.put(Long.class, (long) 0);
		PRIMITIVE_DEFAULTS.put(Float.class, (float) 0);
		PRIMITIVE_DEFAULTS.put(Double.class, (double) 0);
		PRIMITIVE_DEFAULTS.put(Character.class, (char) 0);
		PRIMITIVE_DEFAULTS.put(Boolean.class, false);

		PRIMITIVE_DEFAULTS.put(byte.class, (byte) 0);
		PRIMITIVE_DEFAULTS.put(short.class, (short) 0);
		PRIMITIVE_DEFAULTS.put(int.class, 0);
		PRIMITIVE_DEFAULTS.put(long.class, (long) 0);
		PRIMITIVE_DEFAULTS.put(float.class, (float) 0);
		PRIMITIVE_DEFAULTS.put(double.class, (double) 0);
		PRIMITIVE_DEFAULTS.put(char.class, (char) 0);
		PRIMITIVE_DEFAULTS.put(boolean.class, false);
	}
	static {
		PRIMITIVE_TO_CLASS_MAP = new HashMap<>();
		PRIMITIVE_TO_CLASS_MAP.put(boolean.class, Boolean.class);
		PRIMITIVE_TO_CLASS_MAP.put(byte.class, Byte.class);
		PRIMITIVE_TO_CLASS_MAP.put(short.class, Short.class);
		PRIMITIVE_TO_CLASS_MAP.put(int.class, Integer.class);
		PRIMITIVE_TO_CLASS_MAP.put(long.class, Long.class);
		PRIMITIVE_TO_CLASS_MAP.put(float.class, Float.class);
		PRIMITIVE_TO_CLASS_MAP.put(double.class, Double.class);
		PRIMITIVE_TO_CLASS_MAP.put(char.class, Character.class);
		PRIMITIVE_TO_CLASS_MAP.put(void.class, Void.class);
	}
	static {
		CLASS_TO_PRIMITIVE_MAP = new HashMap<>();
		CLASS_TO_PRIMITIVE_MAP.put(Boolean.class, boolean.class);
		CLASS_TO_PRIMITIVE_MAP.put(Byte.class, byte.class);
		CLASS_TO_PRIMITIVE_MAP.put(Short.class, short.class);
		CLASS_TO_PRIMITIVE_MAP.put(Integer.class, int.class);
		CLASS_TO_PRIMITIVE_MAP.put(Long.class, long.class);
		CLASS_TO_PRIMITIVE_MAP.put(Float.class, float.class);
		CLASS_TO_PRIMITIVE_MAP.put(Double.class, double.class);
		CLASS_TO_PRIMITIVE_MAP.put(Character.class, char.class);
		CLASS_TO_PRIMITIVE_MAP.put(Void.class, void.class);
	}
	static {
		NAME_TO_PRIMITIVE_MAP.put(boolean.class.getName(), boolean.class);
		NAME_TO_PRIMITIVE_MAP.put(byte.class.getName(), byte.class);
		NAME_TO_PRIMITIVE_MAP.put(short.class.getName(), short.class);
		NAME_TO_PRIMITIVE_MAP.put(int.class.getName(), int.class);
		NAME_TO_PRIMITIVE_MAP.put(long.class.getName(), long.class);
		NAME_TO_PRIMITIVE_MAP.put(float.class.getName(), float.class);
		NAME_TO_PRIMITIVE_MAP.put(double.class.getName(), double.class);
		NAME_TO_PRIMITIVE_MAP.put(char.class.getName(), char.class);
		NAME_TO_PRIMITIVE_MAP.put(void.class.getName(), void.class);
	}

	private static void collectAllInheritedTypesWithDistanceImpl(Class<?> type, Integer currentdistance,
			Map<Class<?>, Integer> result) {
		Integer nextdistance = currentdistance + 1;
		for (Class<?> itf : type.getInterfaces()) {
			Integer prev = result.putIfAbsent(itf, currentdistance);
			if (prev == null) {
				collectAllInheritedTypesWithDistanceImpl(itf, nextdistance, result);
			} else {
				if (currentdistance < prev) {
					result.put(itf, currentdistance);
					//collect them again, as the distance is lowered
					collectAllInheritedTypesWithDistanceImpl(itf, nextdistance, result);
				}
			}
		}
		Class<?> sc = type.getSuperclass();
		if (sc != null) {
			Integer prev = result.putIfAbsent(sc, currentdistance);
			if (prev == null) {
				collectAllInheritedTypesWithDistanceImpl(sc, nextdistance, result);
			} else {
				if (currentdistance < prev) {
					result.put(sc, currentdistance);
					//collect them again, as the distance is lowered
					collectAllInheritedTypesWithDistanceImpl(sc, nextdistance, result);
				}
			}
		}
	}

	private static void collectAllInheritedTypesImpl(Class<?> c, Set<Class<?>> result) {
		for (Class<?> itf : c.getInterfaces()) {
			if (result.add(itf)) {
				collectAllInheritedTypesImpl(itf, result);
			}
		}
		Class<?> sc = c.getSuperclass();
		if (sc != null && result.add(sc)) {
			collectAllInheritedTypesImpl(sc, result);
		}
	}

	private static void collectAllInterfacesImpl(Class<?> c, Set<Class<?>> result) {
		for (Class<?> itf : c.getInterfaces()) {
			if (result.add(itf)) {
				collectAllInterfacesImpl(itf, result);
			}
		}
		Class<?> sc = c.getSuperclass();
		if (sc != null) {
			collectAllInterfacesImpl(sc, result);
		}
	}

	private static void collectAllInheritedClassNamesImpl(Class<?> c, Set<String> result) {
		for (Class<?> itf : c.getInterfaces()) {
			if (result.add(itf.getName())) {
				collectAllInheritedClassNamesImpl(itf, result);
			}
		}
		Class<?> sc = c.getSuperclass();
		if (sc != null && result.add(sc.getName())) {
			collectAllInheritedClassNamesImpl(sc, result);
		}
	}

	private static void collectInterfaces(Class<?> clazz, Collection<Class<?>> result) {
		outer:
		for (Class<?> itf : clazz.getInterfaces()) {
			for (Class<?> c : result) {
				if (itf.isAssignableFrom(c)) {
					// a subclass of itf is already in result
					continue outer;
				}
			}
			for (Iterator<Class<?>> it = result.iterator(); it.hasNext();) {
				Class<?> c = it.next();
				if (c.isAssignableFrom(itf)) {
					it.remove();
				}
			}
			result.add(itf);
		}
		Class<?> sclass = clazz.getSuperclass();
		if (sclass != null) {
			collectInterfaces(sclass, result);
		}
	}

	private static Class<?> findTypeWithNameInHierarchyImpl(Class<?> baseclass, String searchclassname) {
		if (baseclass.getName().equals(searchclassname)) {
			return baseclass;
		}
		for (Class<?> itf : baseclass.getInterfaces()) {
			Class<?> itffound = findTypeWithNameInHierarchy(itf, searchclassname);
			if (itffound != null) {
				return itffound;
			}
		}
		Class<?> superc = baseclass.getSuperclass();
		if (superc != null) {
			return findTypeWithNameInHierarchy(superc, searchclassname);
		}
		return null;
	}

	private static Class<?> findInterfaceWithNameInHierarchyImpl(Class<?> type, String itfname) {
		for (Class<?> itf : type.getInterfaces()) {
			if (itfname.equals(itf.getName())) {
				return itf;
			}
			Class<?> itffound = findInterfaceWithNameInHierarchy(itf, itfname);
			if (itffound != null) {
				return itffound;
			}
		}
		Class<?> superc = type.getSuperclass();
		if (superc != null) {
			return findInterfaceWithNameInHierarchy(superc, itfname);
		}
		return null;
	}

	private static Class<?> findClassWithNameInHierarchyImpl(Class<?> type, String classname) {
		Class<?> superc = type.getSuperclass();
		if (superc != null) {
			if (superc.getName().equals(classname)) {
				return superc;
			}
			return findClassWithNameInHierarchyImpl(superc, classname);
		}
		return null;
	}

	private ReflectUtils() {
		throw new UnsupportedOperationException();
	}
}
