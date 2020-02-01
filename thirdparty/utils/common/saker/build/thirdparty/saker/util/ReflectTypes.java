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

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import saker.apiextract.api.ExcludeApi;

/**
 * Utility class containing functions for handling types in the <code>java.lang.reflect</code> package.
 * 
 * @see Type
 * @see ParameterizedType
 * @see GenericArrayType
 */
public class ReflectTypes {
	/**
	 * Deannotates the argument type object.
	 * <p>
	 * If the argument is an instance of {@link AnnotatedType}, the {@link AnnotatedType#getType()} method will be
	 * called on it. This is repeated until the last type is not an annotated type.
	 * 
	 * @param type
	 *            The type.
	 * @return The deannotated type.
	 */
	public static Type deannotateType(Type type) {
		while (type instanceof AnnotatedType) {
			type = ((AnnotatedType) type).getType();
		}
		return type;
	}

	/**
	 * Gets a {@link Type} that has the argument type as its component.
	 * <p>
	 * The returned type is either an instance of {@link GenericArrayType}, or a {@link Class}.
	 * 
	 * @param componenttype
	 *            The component type.
	 * @return The type that has the argument as component.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static Type makeArrayType(Type componenttype) throws NullPointerException {
		Objects.requireNonNull(componenttype, "component type");
		if (componenttype instanceof Class) {
			return ReflectUtils.getArrayClassWithComponent((Class<?>) componenttype);
		}
		return new GenericArrayTypeImpl(componenttype);
	}

	/**
	 * Creates a new {@link ParameterizedType} for the given owner, type, and template arguments.
	 * <p>
	 * The returned {@link ParameterizedType} will have the given arguments as its attributes.
	 * <p>
	 * The template argument array may or may not be cloned. Any modifications made to it may be reflected on the
	 * returned result. Callers shouldn't rely on the array being modifiable after this call returns.
	 * 
	 * @param owner
	 *            The {@linkplain ParameterizedType#getOwnerType() owner type}. May be <code>null</code>.
	 * @param type
	 *            The {@linkplain ParameterizedType#getRawType() raw type}.
	 * @param templateargs
	 *            The {@linkplain ParameterizedType#getActualTypeArguments() type arguments}.
	 * @return The created parameterized type.
	 * @throws NullPointerException
	 *             If the type or template arguments array is <code>null</code>.
	 */
	public static ParameterizedType makeParameterizedTypeWithOwner(Type owner, Type type, Type... templateargs)
			throws NullPointerException {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(templateargs, "templateargs");
		return new ParameterizedTypeImpl(owner, type, templateargs);
	}

	/**
	 * Creates a new {@link ParameterizedType} for the given type, and template arguments.
	 * <p>
	 * The returned {@link ParameterizedType} will have the given arguments as its attributes. The result will have
	 * <code>null</code> {@linkplain ParameterizedType#getOwnerType() owner type}.
	 * <p>
	 * The template argument array may or may not be cloned. Any modifications made to it may be reflected on the
	 * returned result. Callers shouldn't rely on the array being modifiable after this call returns.
	 * 
	 * @param type
	 *            The {@linkplain ParameterizedType#getRawType() raw type}.
	 * @param templateargs
	 *            The {@linkplain ParameterizedType#getActualTypeArguments() type arguments}.
	 * @return The created parameterized type.
	 * @throws NullPointerException
	 *             If the type or template arguments array is <code>null</code>.
	 */
	public static ParameterizedType makeParameterizedType(Type type, Type... templateargs) throws NullPointerException {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(templateargs, "templateargs");
		return new ParameterizedTypeImpl(null, type, templateargs);
	}

	/**
	 * Creates a new {@link WildcardType} that extends the argument types.
	 * <p>
	 * The extended types argument array may or may not be cloned. Any modifications made to it may be reflected on the
	 * returned result. Callers shouldn't rely on the array being modifiable after this call returns.
	 * 
	 * @param extendtypes
	 *            The types that the wildcard extends.
	 * @return The created wildcard type.
	 * @throws NullPointerException
	 *             If the argument array is <code>null</code>.
	 */
	public static WildcardType makeWildcardTypeExtends(Type... extendtypes) throws NullPointerException {
		Objects.requireNonNull(extendtypes, "extend types");
		if (extendtypes.length == 0) {
			extendtypes = TYPE_ARRAY_OBJECT_CLASS;
		}
		return new WildcardTypeImpl(EMPTY_TYPE_ARRAY, extendtypes);
	}

	/**
	 * Creates a new {@link WildcardType} that is a super type of the argument types.
	 * <p>
	 * The super types argument array may or may not be cloned. Any modifications made to it may be reflected on the
	 * returned result. Callers shouldn't rely on the array being modifiable after this call returns.
	 * 
	 * @param supertypes
	 *            The types that the wildcard is super of.
	 * @return The created wildcard type.
	 * @throws NullPointerException
	 *             If the argument array is <code>null</code>.
	 */
	public static WildcardType makeWildcardTypeSuper(Type... supertypes) throws NullPointerException {
		Objects.requireNonNull(supertypes, "super types");
		return new WildcardTypeImpl(supertypes, TYPE_ARRAY_OBJECT_CLASS);
	}

	/**
	 * Gets a list that contains the {@linkplain ParameterizedType#getActualTypeArguments() actual type parameters} of
	 * the argument, if it is a parameterized type.
	 * <p>
	 * If the argument is not a parameterized type, an empty list is returned.
	 * 
	 * @param generictype
	 *            The type to get the type arguments of.
	 * @return The type arguments.
	 */
	public static List<? extends Type> getTypeArguments(Type generictype) {
		if (generictype instanceof ParameterizedType) {
			return ImmutableUtils.asUnmodifiableArrayList(((ParameterizedType) generictype).getActualTypeArguments());
		}
		return Collections.emptyList();
	}

	/**
	 * Gets the type parameter of the possibly parameterized argument type at the given index.
	 * <p>
	 * This method will check if the argument is an instanceo of {@link ParameterizedType}, and return the
	 * {@linkplain ParameterizedType#getActualTypeArguments() actual type argument} at the given index.
	 * <p>
	 * If the argument is not an instance of {@link ParameterizedType}, or the index is out of bounds for the type
	 * arguments, {@link Object Object.class} is returned.
	 * 
	 * @param generictype
	 *            The generic type.
	 * @param index
	 *            The index at which to query the type argument.
	 * @return The found type or <code>Object.class</code>.
	 * @throws IllegalArgumentException
	 *             If the index is negative.
	 */
	public static Type getTypeArguments(Type generictype, int index) throws IllegalArgumentException {
		if (index < 0) {
			throw new IllegalArgumentException("Index is negative: " + index);
		}
		if (generictype instanceof ParameterizedType) {
			Type[] args = (((ParameterizedType) generictype).getActualTypeArguments());
			if (index < args.length) {
				return args[index];
			}
		}
		return Object.class;
	}

	//XXX exclude this function for now, as its use-case and mechanics have not been validated
	//examine this function
	@ExcludeApi
	private static Type getTypeParameterInHierarchy(Type concretetype, Class<?> parameterizedtype, int index) {
		return getTypeParameterInHierarchy(concretetype, parameterizedtype, index, new HashMap<>());
	}

	/**
	 * Gets the class of a type, and from the value that is treated as an instance of the given type.
	 * <p>
	 * If the <code>value</code> is not <code>null</code>, the class of the value object is returned.
	 * <p>
	 * Else the given type is examined.
	 * <p>
	 * If it is an instance of {@link Class}, it is returned.
	 * <p>
	 * If it is an instance of {@link ParameterizedType}, the raw type is returned.
	 * <p>
	 * If it is an instance of {@link GenericArrayType}, the array type corresponding to it is returned.
	 * <p>
	 * In any other cases, <code>null</code> is returned. {@linkplain WildcardType Wildcards} and
	 * {@linkplain TypeVariable type variables} are not recognized.
	 * 
	 * @param value
	 *            The value that is an instance of the given type.
	 * @param type
	 *            The type to get the class representation of.
	 * @return The corresponding class, or <code>null</code> if failed to determine.
	 */
	public static Class<?> getClassFromType(Object value, Type type) {
		if (value != null) {
			return value.getClass();
		}
		type = deannotateType(type);
		if (type instanceof Class) {
			return (Class<?>) type;
		}
		if (type instanceof ParameterizedType) {
			return (Class<?>) ((ParameterizedType) type).getRawType();
		}
		if (type instanceof GenericArrayType) {
			return createEmptyGenericArray((GenericArrayType) type).getClass();
		}
		return null;
	}

	/**
	 * Gets the classloaders which are associated with the given type.
	 * <p>
	 * This method examines the argument type, and gets the classloaders for them.
	 * <p>
	 * If the type is a {@link Class}, the classloader of that class is added.
	 * <p>
	 * If the type is a {@link ParameterizedType}, the classloaders for the raw type, and the type arguments are added.
	 * <p>
	 * If the type is a {@link GenericArrayType}, the classloaders for the component type is added.
	 * <p>
	 * If the type is a {@link WildcardType}, the classloaders for the upper and lower bounds is added.
	 * <p>
	 * If the type is a {@link TypeVariable}, the classloaders for the bounds and the generic declaration is added.
	 * <p>
	 * In any other case, the type is unrecognized, and no classloaders are added.
	 * 
	 * @param type
	 *            The type to get the classloaders for.
	 * @return A set of classloaders which are associated with the given type.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static Collection<ClassLoader> getClassLoadersFromType(Type type) throws NullPointerException {
		Objects.requireNonNull(type, "type");
		Collection<ClassLoader> result = new HashSet<>();
		getClassLoadersFromType(type, result);
		return result;

	}

	/**
	 * Gets the component class of the generic array type represented by the argument.
	 * 
	 * @param type
	 *            The generic array type to get the component class of.
	 * @return The component class of the argument.
	 * @throws IllegalArgumentException
	 *             If the method cannot determine the component type.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static Class<?> getGenericArrayComponentClass(GenericArrayType type)
			throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(type, "type");
		Type comp = type.getGenericComponentType();
		if (comp instanceof Class) {
			return (Class<?>) comp;
		}
		return createEmptyGenericArray(type).getClass().getComponentType();
	}

	/**
	 * Gets the class representation of the generic array type represented by the argument.
	 * 
	 * @param type
	 *            The generic array type to get the class of.
	 * @return The class of the generic array.
	 * @throws IllegalArgumentException
	 *             If the method cannot determine the component type.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static Class<?> getGenericArrayClass(GenericArrayType type)
			throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(type, "type");
		Type comp = type.getGenericComponentType();
		if (comp instanceof Class) {
			return (Class<?>) comp;
		}
		return createEmptyGenericArray(type).getClass();
	}

	/**
	 * Creates a new empty array instance that is compatible with the argument type.
	 * <p>
	 * The raw component type of the generic array type will be retrieved, and a new empty array is created for it. The
	 * component type will be resolved accordingly. This method will only be available to create a new array instance if
	 * the innermost component type is either a {@link Class}, or {@link ParameterizedType}. The method cannot create an
	 * array for type that have a {@linkplain WildcardType wildcard} or {@linkplain TypeVariable type variable}
	 * component type.
	 * 
	 * @param type
	 *            The generic array type to create the array for.
	 * @return The created array.
	 * @throws IllegalArgumentException
	 *             If the method cannot determine the component type.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static Object createEmptyGenericArray(GenericArrayType type)
			throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(type, "type");
		int count = 1;
		Type component = type.getGenericComponentType();
		while (component instanceof GenericArrayType) {
			component = ((GenericArrayType) component).getGenericComponentType();
			++count;
		}
		Class<?> componentclass;
		if (component instanceof ParameterizedType) {
			componentclass = (Class<?>) ((ParameterizedType) component).getRawType();
		} else if (component instanceof Class) {
			componentclass = (Class<?>) component;
		} else {
			throw new IllegalArgumentException("Failed to create empty generic array for: " + type);
		}
		return Array.newInstance(componentclass, new int[count]);
	}

	private static final Type[] EMPTY_TYPE_ARRAY = {};
	private static final Type[] TYPE_ARRAY_OBJECT_CLASS = { Object.class };

	private ReflectTypes() {
		throw new UnsupportedOperationException();
	}

	private static Type getTypeParameterInHierarchy(Type concretetype, Class<?> parameterizedtype, int index,
			Map<TypeVariable<?>, Type> argumentsmap) {
		concretetype = deannotateType(concretetype);
		addTypeParameterVariables(concretetype, argumentsmap);

		if (concretetype == parameterizedtype) {
			TypeVariable<?> tv = parameterizedtype.getTypeParameters()[index];
			Type got = tv;
			do {
				Type arggot = argumentsmap.get(got);
				if (arggot == null) {
					return null;
				}
				if (arggot.equals(got)) {
					break;
				}
				got = arggot;
			} while (got instanceof TypeVariable);
			return got;
		} else if (concretetype instanceof Class) {
			Class<?> c = (Class<?>) concretetype;
			Type genericsc = c.getGenericSuperclass();
			if (genericsc != null) {
				Type got = getTypeParameterInHierarchy(genericsc, parameterizedtype, index, argumentsmap);
				if (got != null) {
					return got;
				}
			}
			for (Type itf : c.getGenericInterfaces()) {
				Type got = getTypeParameterInHierarchy(itf, parameterizedtype, index, argumentsmap);
				if (got != null) {
					return got;
				}
			}
		} else if (concretetype instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) concretetype;
			return getTypeParameterInHierarchy(pt.getRawType(), parameterizedtype, index, argumentsmap);
		} else if (concretetype instanceof WildcardType) {
			WildcardType wc = (WildcardType) concretetype;
			for (Type bound : wc.getUpperBounds()) {
				Type got = getTypeParameterInHierarchy(bound, parameterizedtype, index, argumentsmap);
				if (got != null) {
					return got;
				}
			}
		} else if (concretetype instanceof TypeVariable) {
			TypeVariable<?> tv = (TypeVariable<?>) concretetype;
			argumentsmap.putIfAbsent(tv, tv);
			for (Type bound : tv.getBounds()) {
				Type got = getTypeParameterInHierarchy(bound, parameterizedtype, index, argumentsmap);
				if (got != null) {
					return got;
				}
			}
		}
		return null;
	}

	private static void addTypeParameterVariables(Type type, Map<TypeVariable<?>, Type> argumentsmap) {
		if (type instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) type;
			addTypeParameterVariables(pt.getOwnerType(), argumentsmap);

			Type[] targs = pt.getActualTypeArguments();
			Class<?> raw = (Class<?>) pt.getRawType();
			TypeVariable<?>[] rawtp = raw.getTypeParameters();
			for (int i = 0; i < targs.length; i++) {
				argumentsmap.putIfAbsent(rawtp[i], deannotateType(targs[i]));
			}
		}
//		else if (type instanceof WildcardType) {
//			WildcardType wc = (WildcardType) type;
//			for (Type bound : wc.getUpperBounds()) {
//				addTypeParameterVariables(bound, argumentsmap);
//			}
//		} 
//		else if (type instanceof TypeVariable) {
//			TypeVariable<?> tv = (TypeVariable<?>) type;
//			for (Type bound : tv.getBounds()) {
//				addTypeParameterVariables(bound, argumentsmap);
//			}
//		} 
//		else if (type instanceof Class) {
//			Class<?> c = (Class<?>) type;
//			for (TypeVariable<?> tv : c.getTypeParameters()) {
//				argumentsmap.putIfAbsent(tv, tv);
//			}
//		}
	}

	private static void getClassLoadersFromType(Type type, Collection<ClassLoader> result) {
		type = deannotateType(type);
		if (type instanceof Class) {
			ClassLoader cl = ((Class<?>) type).getClassLoader();
			if (cl == null) {
				return;
			}
			result.add(cl);
		} else if (type instanceof ParameterizedType) {
			ParameterizedType paramed = (ParameterizedType) type;
			getClassLoadersFromType(paramed.getRawType(), result);
			for (int i = 0; i < paramed.getActualTypeArguments().length; i++) {
				Type arg = paramed.getActualTypeArguments()[i];
				getClassLoadersFromType(arg, result);
			}
		} else if (type instanceof GenericArrayType) {
			getClassLoadersFromType(((GenericArrayType) type).getGenericComponentType(), result);
		} else if (type instanceof WildcardType) {
			WildcardType wc = (WildcardType) type;
			for (Type t : wc.getUpperBounds()) {
				getClassLoadersFromType(t, result);
			}
			for (Type t : wc.getLowerBounds()) {
				getClassLoadersFromType(t, result);
			}
		} else if (type instanceof TypeVariable<?>) {
			TypeVariable<?> tv = (TypeVariable<?>) type;
			for (Type b : tv.getBounds()) {
				getClassLoadersFromType(b, result);
			}
			GenericDeclaration gd = tv.getGenericDeclaration();
			if (gd instanceof Type) {
				getClassLoadersFromType((Type) gd, result);
			}
		}
	}

	private static final class GenericArrayTypeImpl implements GenericArrayType {
		private final Type component;

		public GenericArrayTypeImpl(Type component) {
			this.component = component;
		}

		@Override
		public Type getGenericComponentType() {
			return component;
		}

		@Override
		public String toString() {
			return component + "[]";
		}
	}

	private static final class ParameterizedTypeImpl implements ParameterizedType {
		private final Type raw;
		private final Type owner;
		private final Type[] args;

		public ParameterizedTypeImpl(Type owner, Type raw, Type[] args) {
			this.raw = raw;
			this.owner = owner;
			this.args = args;
		}

		@Override
		public Type getRawType() {
			return raw;
		}

		@Override
		public Type getOwnerType() {
			return owner;
		}

		@Override
		public Type[] getActualTypeArguments() {
			return args;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			if (owner != null) {
				sb.append(owner);
				sb.append(".");
			}
			sb.append(raw);
			if (!ObjectUtils.isNullOrEmpty(args)) {
				sb.append('<');
				for (int i = 0;;) {
					Type argtype = args[i];
					sb.append(argtype);
					if (++i < args.length) {
						sb.append(", ");
					} else {
						break;
					}
				}
				sb.append('>');
			}
			return sb.toString();
		}
	}

	private static final class WildcardTypeImpl implements WildcardType {
		private final Type[] lower;
		private final Type[] upper;

		public WildcardTypeImpl(Type[] lower, Type[] upper) {
			this.lower = lower;
			this.upper = upper;
		}

		@Override
		public Type[] getUpperBounds() {
			return upper;
		}

		@Override
		public Type[] getLowerBounds() {
			return lower;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append('?');
			if (upper.length > 0) {
				sb.append(" extends ");
				sb.append(upper[0]);
				for (int i = 1; i < upper.length; i++) {
					sb.append(" & ");
					sb.append(upper[i]);
				}
			}
			if (lower.length > 0) {
				sb.append(" super ");
				sb.append(lower[0]);
				for (int i = 1; i < lower.length; i++) {
					sb.append(" & ");
					sb.append(lower[i]);
				}
			}
			return sb.toString();
		}
	}

}
