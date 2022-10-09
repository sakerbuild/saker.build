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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import saker.build.thirdparty.org.objectweb.asm.ClassWriter;
import saker.build.thirdparty.org.objectweb.asm.FieldVisitor;
import saker.build.thirdparty.org.objectweb.asm.Label;
import saker.build.thirdparty.org.objectweb.asm.MethodVisitor;
import saker.build.thirdparty.org.objectweb.asm.Opcodes;
import saker.build.thirdparty.org.objectweb.asm.Type;

import saker.build.thirdparty.saker.rmi.connection.RemoteProxyObject.RMICacheHelper;
import saker.build.thirdparty.saker.rmi.connection.RemoteProxyObject.RemoteInvocationRMIFailureException;
import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMIProxyCreationFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;

class ProxyGenerator {

	static final String PROXY_MARKER_RMI_STATISTICS_FIELD_NAME = "statistics";

	public static byte[] generateProxyMarkerClass(String name, boolean withstatisticsfield) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name.replace('.', '/'), null,
				REMOTEPROXYOBJECT_INTERNAL_NAME, null);

		MethodVisitor constructorv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/ref/Reference;I)V", null,
				null);
		constructorv.visitCode();
		constructorv.visitVarInsn(Opcodes.ALOAD, 0);
		constructorv.visitVarInsn(Opcodes.ALOAD, 1);
		constructorv.visitVarInsn(Opcodes.ILOAD, 2);
		constructorv.visitMethodInsn(Opcodes.INVOKESPECIAL, REMOTEPROXYOBJECT_INTERNAL_NAME, "<init>",
				"(Ljava/lang/ref/Reference;I)V", false);
		constructorv.visitInsn(Opcodes.RETURN);
		constructorv.visitMaxs(0, 0);
		constructorv.visitEnd();

		if (withstatisticsfield) {
			FieldVisitor fw = cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
					PROXY_MARKER_RMI_STATISTICS_FIELD_NAME, RMISTATISTICS_DESCRIPTOR, null, null);
			fw.visitEnd();
		}

		cw.visitEnd();
		return cw.toByteArray();
	}

	public static byte[] generateProxy(String name, Set<Class<?>> itfs, String markerclassinternalname,
			RMITransferPropertiesHolder rmiproperties, boolean collectstatistics)
			throws RMIProxyCreationFailedException {
		String superclassinternalname = markerclassinternalname == null ? REMOTEPROXYOBJECT_INTERNAL_NAME
				: markerclassinternalname;
		Set<Class<?>> interfaces = new LinkedHashSet<>(itfs);
		//TODO test that the interfaces are all public, and non assignable with a test flag
		//XXX these reductions should happen earlier in the proxy class generation
		ReflectUtils.reduceAssignableTypes(interfaces);
		ReflectUtils.removeNonInterfaces(interfaces);

		Map<MethodKey, Collection<MethodRef>> methods = new HashMap<>();

		String[] itfnames = new String[interfaces.size()];
		int i = 0;
		for (Class<?> itf : interfaces) {
			itfnames[i++] = Type.getInternalName(itf);
			collectMethods(methods, itf, rmiproperties);
		}

		addInterfaceMethod(methods, METHOD_OBJECT_TOSTRING, rmiproperties);
		addInterfaceMethod(methods, METHOD_OBJECT_HASHCODE, rmiproperties);

		String thisclassinternalname = name.replace('.', '/');

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, thisclassinternalname, null,
				superclassinternalname, itfnames);

		Collection<Consumer<MethodVisitor>> initcachefieldswriters = new ArrayList<>();

		Set<String> cachehelperfieldnames = new TreeSet<>();

		i = 0;
		for (Collection<MethodRef> methodrefs : methods.values()) {
			MethodRef mr = reduceOverrideMethods(methodrefs);
			MethodKey key = mr.key;

			boolean defaultonfailure = mr.properties.isDefaultOnFailure();
			boolean forbidden = mr.properties.isForbidden();
			boolean cacheresult = mr.properties.isCacheResult();
			if (forbidden && defaultonfailure) {
				//call default super
				MethodVisitor mrefv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, key.name, key.descriptor,
						null, null);
				mrefv.visitCode();
				writeCallDefaultMethodAndReturn(mr, interfaces, mrefv);
				mrefv.visitMaxs(0, 0);
				mrefv.visitEnd();
				continue;
			}

			if (key.isReturnTypeOrAnyArgumentNonPublic()) {
				//there is a non-public return type or argument type.
				//the RMI method cannot be modelled, as it would throw IllegalAccessErrors and others
				//don't throw RMIProxyCreationFailedException, as it may be a valid use case
				//however, fail the method call

				MethodVisitor mrefv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, key.name, key.descriptor,
						null, null);
				mrefv.visitCode();

				mrefv.visitTypeInsn(Opcodes.NEW, RMICALLFAILEDEXCEPTION_INTERNAL_NAME);
				mrefv.visitInsn(Opcodes.DUP);
				StringBuilder sb = new StringBuilder();
				sb.append("Failed to call method with inaccessible return or argument types: ");
				sb.append(key.returnType.getName());
				sb.append(" ");
				sb.append(key.name);
				sb.append("(");
				for (int j = 0; j < key.argTypes.length; j++) {
					Class<?> c = key.argTypes[j];
					sb.append(c.getName());
					if (j + 1 < key.argTypes.length) {
						sb.append(", ");
					}
				}
				sb.append(")");
				mrefv.visitLdcInsn(sb.toString());
				mrefv.visitMethodInsn(Opcodes.INVOKESPECIAL, RMICALLFAILEDEXCEPTION_INTERNAL_NAME, "<init>",
						"(Ljava/lang/String;)V", false);
				mrefv.visitInsn(Opcodes.ATHROW);

				mrefv.visitMaxs(0, 0);
				mrefv.visitEnd();
				continue;
			}

			++i;
			String methodfieldname = createMethodHolderVariableName(i);
			String cachefieldname;
			String cachehelperfielddescriptor = JAVA_LANG_OBJECT_DESCRIPTOR;
			if (cacheresult) {
				cachefieldname = createCacheFieldHolderVariableName(i);
				cachehelperfieldnames.add(cachefieldname);

				FieldVisitor cachefieldfw = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_VOLATILE, cachefieldname,
						cachehelperfielddescriptor, null, null);
				cachefieldfw.visitEnd();
			} else {
				cachefieldname = null;
			}

			FieldVisitor methodfieldfw = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, methodfieldname,
					METHODTRANSFERROPERTIES_DESCRIPTOR, null, null);
			methodfieldfw.visitEnd();

			initcachefieldswriters
					.add(getMethodCacheFieldAssignInstructionWriter(methodfieldname, thisclassinternalname, mr));

			if (mr.properties.getRedirectMethod() != null) {
				MethodVisitor mw = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, key.name, key.descriptor,
						null, null);
				mw.visitCode();

				//XXX we might create the array which has the proxy as the first element instead of just the parameters
				writeCallInvokerMethodReturnInstructions(mw, thisclassinternalname, methodfieldname, mr, key,
						"invokeRedirectInternal", false, markerclassinternalname);

				mw.visitMaxs(0, 0);
				mw.visitEnd();

				if (!forbidden) {
					MethodVisitor snrmw = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
							"0rmi_nonredirect$" + key.name, key.descriptor, null, null);
					snrmw.visitCode();

					snrmw.visitFieldInsn(Opcodes.GETSTATIC, thisclassinternalname, methodfieldname,
							METHODTRANSFERROPERTIES_DESCRIPTOR);
					snrmw.visitVarInsn(Opcodes.ALOAD, 0);
					writeLoadArgumentsArrayInstructions(snrmw, key.argTypes);
					String methoddescriptor;
					if (collectstatistics) {
						methoddescriptor = "(" + METHODTRANSFERROPERTIES_DESCRIPTOR + REMOTEPROXYOBJECT_DESCRIPTOR
								+ ARRAY_JAVA_LANG_OBJECT_DESCRIPTOR + RMISTATISTICS_DESCRIPTOR + ")"
								+ JAVA_LANG_OBJECT_DESCRIPTOR;
						snrmw.visitFieldInsn(Opcodes.GETSTATIC, markerclassinternalname,
								PROXY_MARKER_RMI_STATISTICS_FIELD_NAME, RMISTATISTICS_DESCRIPTOR);
					} else {
						methoddescriptor = "(" + METHODTRANSFERROPERTIES_DESCRIPTOR + REMOTEPROXYOBJECT_DESCRIPTOR
								+ ARRAY_JAVA_LANG_OBJECT_DESCRIPTOR + ")" + JAVA_LANG_OBJECT_DESCRIPTOR;
					}
					snrmw.visitMethodInsn(Opcodes.INVOKESTATIC, REMOTEPROXYOBJECT_INTERNAL_NAME,
							"callNonRedirectMethodFromStaticDelegate", methoddescriptor, false);
					writeCheckCastReturn(snrmw, mr.getReturnType());

					snrmw.visitMaxs(0, 0);
					snrmw.visitEnd();
				}
				continue;
			}
			if (forbidden) {
				MethodVisitor mrefv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, key.name, key.descriptor,
						null, null);
				mrefv.visitCode();

				mrefv.visitFieldInsn(Opcodes.GETSTATIC, thisclassinternalname, methodfieldname,
						METHODTRANSFERROPERTIES_DESCRIPTOR);
				mrefv.visitMethodInsn(Opcodes.INVOKESTATIC, REMOTEPROXYOBJECT_INTERNAL_NAME,
						"forbiddenThrowableInternal",
						"(" + METHODTRANSFERROPERTIES_DESCRIPTOR + ")" + JAVA_LANG_THROWABLE_DESCRIPTOR, false);
				mrefv.visitInsn(Opcodes.ATHROW);

				mrefv.visitMaxs(0, 0);
				mrefv.visitEnd();
				continue;
			}

			MethodVisitor mw = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, key.name, key.descriptor, null,
					null);
			mw.visitCode();

			Label rmibodystart = new Label();
			Label rmibodyend = new Label();
			Label rmiexceptionhandler = new Label();

			mw.visitTryCatchBlock(rmibodystart, rmibodyend, rmiexceptionhandler,
					REMOTEINVOCATIONRMIFAILUREEXCEPTION_INTERNAL_NAME);
			mw.visitLabel(rmibodystart);

			if (cacheresult) {
				mw.visitVarInsn(Opcodes.ALOAD, 0);
				mw.visitFieldInsn(Opcodes.GETFIELD, thisclassinternalname, cachefieldname, cachehelperfielddescriptor);
				mw.visitInsn(Opcodes.DUP);
				mw.visitVarInsn(Opcodes.ASTORE, 1);
				mw.visitTypeInsn(Opcodes.INSTANCEOF, RMICACHEHELPER_INTERNAL_NAME);
				Label resultreadylabel = new Label();
				mw.visitJumpInsn(Opcodes.IFEQ, resultreadylabel);

				mw.visitVarInsn(Opcodes.ALOAD, 1);
				mw.visitTypeInsn(Opcodes.CHECKCAST, RMICACHEHELPER_INTERNAL_NAME);
				loadCallInvokerMethodInstructionParameters(mw, thisclassinternalname, methodfieldname, key);
				String methoddescriptor;
				if (collectstatistics) {
					mw.visitFieldInsn(Opcodes.GETSTATIC, markerclassinternalname,
							PROXY_MARKER_RMI_STATISTICS_FIELD_NAME, RMISTATISTICS_DESCRIPTOR);
					methoddescriptor = "(" + REMOTEPROXYOBJECT_DESCRIPTOR + METHODTRANSFERROPERTIES_DESCRIPTOR
							+ "[Ljava/lang/Object;" + RMISTATISTICS_DESCRIPTOR + ")Ljava/lang/Object;";
				} else {
					methoddescriptor = "(" + REMOTEPROXYOBJECT_DESCRIPTOR + METHODTRANSFERROPERTIES_DESCRIPTOR
							+ "[Ljava/lang/Object;)Ljava/lang/Object;";
				}
				mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, RMICACHEHELPER_INTERNAL_NAME, "call", methoddescriptor,
						false);
				mw.visitInsn(Opcodes.DUP);
				mw.visitVarInsn(Opcodes.ALOAD, 0);
				mw.visitInsn(Opcodes.SWAP);
				mw.visitFieldInsn(Opcodes.PUTFIELD, thisclassinternalname, cachefieldname, cachehelperfielddescriptor);
				writeObjectReturnInstructions(mw, mr);

				mw.visitLabel(resultreadylabel);
				mw.visitVarInsn(Opcodes.ALOAD, 1);
				writeObjectReturnInstructions(mw, mr);
			} else {
				writeCallInvokerMethodReturnInstructions(mw, thisclassinternalname, methodfieldname, mr, key,
						"callMethodInternal", collectstatistics, markerclassinternalname);
			}

			mw.visitLabel(rmibodyend);
			mw.visitLabel(rmiexceptionhandler);

			int excvarnum = getMethodVariableSlot(mr.key.argTypes);

			mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Throwable.class), "getCause",
					"()" + JAVA_LANG_THROWABLE_DESCRIPTOR, false);

			//used for debugging only
//			mrefv.visitInsn(DUP);
//			mrefv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(Throwable.class), "printStackTrace", "()V", false);

			if (defaultonfailure) {
				mw.visitVarInsn(Opcodes.ASTORE, excvarnum);
				// add exception to suppressed, if default method throws
				Label defcallstart = new Label();
				Label defcallend = new Label();
				Label defcallcatch = new Label();
				mw.visitTryCatchBlock(defcallstart, defcallend, defcallcatch, THROWABLE_INTERNAL_NAME);
				mw.visitLabel(defcallstart);
				writeCallDefaultMethodAndReturn(mr, interfaces, mw);
				mw.visitLabel(defcallend);
				mw.visitLabel(defcallcatch);

				mw.visitInsn(Opcodes.DUP);
				mw.visitVarInsn(Opcodes.ALOAD, excvarnum);
				mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, THROWABLE_INTERNAL_NAME, "addSuppressed",
						"(" + JAVA_LANG_THROWABLE_DESCRIPTOR + ")V", false);
			}

			mw.visitInsn(Opcodes.ATHROW);

			mw.visitMaxs(0, 0);
			mw.visitEnd();
		}

		if (!initcachefieldswriters.isEmpty()) {
			MethodVisitor initcachemethodv = cw.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC,
					INITIALIZE_CACHE_FIELDS_METHOD_NAME, "(" + RMITRANSFERPROPERTIESHOLDER_DESCRIPTOR + ")V", null,
					null);
			initcachemethodv.visitCode();
			for (Consumer<MethodVisitor> writer : initcachefieldswriters) {
				writer.accept(initcachemethodv);
			}
			initcachemethodv.visitInsn(Opcodes.RETURN);
			initcachemethodv.visitMaxs(0, 0);
			initcachemethodv.visitEnd();
		}

		writeProxyConstructorMethod(thisclassinternalname, superclassinternalname, cw, cachehelperfieldnames);

		cw.visitEnd();
		return cw.toByteArray();
	}

	private static final String RMICACHEHELPER_INTERNAL_NAME = Type.getInternalName(RMICacheHelper.class);
	private static final String THROWABLE_INTERNAL_NAME = Type.getInternalName(Throwable.class);
	private static final String RMIRUNTIMEEXCEPTION_INTERNAL_NAME = Type.getInternalName(RMIRuntimeException.class);
	private static final String RMICALLFAILEDEXCEPTION_INTERNAL_NAME = Type
			.getInternalName(RMICallFailedException.class);
	private static final String METHODTRANSFERROPERTIES_DESCRIPTOR = Type.getDescriptor(MethodTransferProperties.class);
	private static final String REMOTEINVOCATIONRMIFAILUREEXCEPTION_INTERNAL_NAME = Type
			.getInternalName(RemoteInvocationRMIFailureException.class);
	private static final String RMITRANSFERPROPERTIESHOLDER_INTERNAL_NAME = Type
			.getInternalName(RMITransferPropertiesHolder.class);
	private static final String RMITRANSFERPROPERTIESHOLDER_DESCRIPTOR = Type
			.getDescriptor(RMITransferPropertiesHolder.class);
	private static final String REMOTEPROXYOBJECT_INTERNAL_NAME = Type.getInternalName(RemoteProxyObject.class);
	private static final String REMOTEPROXYOBJECT_DESCRIPTOR = Type.getDescriptor(RemoteProxyObject.class);
	private static final String RMICACHEHELPER_DESCRIPTOR = Type.getDescriptor(RMICacheHelper.class);
	private static final String JAVA_LANG_OBJECT_DESCRIPTOR = Type.getDescriptor(Object.class);
	private static final String ARRAY_JAVA_LANG_OBJECT_DESCRIPTOR = Type.getDescriptor(Object[].class);
	private static final String RMISTATISTICS_DESCRIPTOR = Type.getDescriptor(RMIStatistics.class);

	private static final String JAVA_LANG_THROWABLE_DESCRIPTOR = Type.getDescriptor(Throwable.class);
	private static final String JAVA_LANG_OBJECT_INTERNAL_NAME = Type.getInternalName(Object.class);
	private static final String JAVA_LANG_CHARACTER_INTERNAL_NAME = Type.getInternalName(Character.class);
	private static final String JAVA_LANG_DOUBLE_INTERNAL_NAME = Type.getInternalName(Double.class);
	private static final String JAVA_LANG_FLOAT_INTERNAL_NAME = Type.getInternalName(Float.class);
	private static final String JAVA_LANG_LONG_INTERNAL_NAME = Type.getInternalName(Long.class);
	private static final String JAVA_LANG_INTEGER_INTERNAL_NAME = Type.getInternalName(Integer.class);
	private static final String JAVA_LANG_SHORT_INTERNAL_NAME = Type.getInternalName(Short.class);
	private static final String JAVA_LANG_BYTE_INTERNAL_NAME = Type.getInternalName(Byte.class);
	private static final String JAVA_LANG_BOOLEAN_INTERNAL_NAME = Type.getInternalName(Boolean.class);
	private static final String JAVA_LANG_CLASS_DESCRIPTOR = Type.getDescriptor(Class.class);
	private static final String JAVA_LANG_CLASS_INTERNAL_NAME = Type.getInternalName(Class.class);
	private static final String JAVA_LANG_SYSTEM_INTERNAL_NAME = Type.getInternalName(System.class);

	static final String INITIALIZE_CACHE_FIELDS_METHOD_NAME = "0rmi_initCacheFields";

	private static final Method METHOD_OBJECT_HASHCODE;
	private static final Method METHOD_OBJECT_TOSTRING;
	static {
		try {
			METHOD_OBJECT_HASHCODE = Object.class.getMethod("hashCode");
			METHOD_OBJECT_TOSTRING = Object.class.getMethod("toString");
		} catch (NoSuchMethodException e) {
			throw new AssertionError(e);
		}
	}

	private static class MethodKey {
		public final String name;
		public final Class<?>[] argTypes;

		public final transient String descriptor;
		public final transient Class<?> returnType;

		public MethodKey(Method m) {
			this.name = m.getName();
			this.descriptor = Type.getMethodDescriptor(m);
			this.argTypes = m.getParameterTypes();
			this.returnType = m.getReturnType();
		}

		public boolean isReturnTypeOrAnyArgumentNonPublic() {
			if (isNonPublic(returnType)) {
				return true;
			}
			for (Class<?> argc : argTypes) {
				if (isNonPublic(argc)) {
					return true;
				}
			}
			return false;
		}

		private static boolean isNonPublic(Class<?> c) {
			if (c.isPrimitive()) {
				return false;
			}
			return !Modifier.isPublic(c.getModifiers());
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(argTypes);
			result = prime * result + ((name == null) ? 0 : name.hashCode());
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
			MethodKey other = (MethodKey) obj;
			if (!Arrays.equals(argTypes, other.argTypes)) {
				return false;
			}
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}
	}

	private static class MethodRef {
		public final MethodKey key;

		public transient MethodTransferProperties properties;

		public MethodRef(Method m, RMITransferPropertiesHolder rmiproperties) {
			this.properties = rmiproperties.getExecutableProperties(m);
			key = new MethodKey(m);
		}

		public boolean propertiesEqual(MethodRef other) {
			return this.properties.propertiesEquals(other.properties);
		}

		public Class<?> getReturnType() {
			return properties.getReturnType();
		}

		public Method getMethod() {
			return properties.getExecutable();
		}

		@Override
		public String toString() {
			return getMethod().toString();
		}
	}

	private static void loadParameters(Class<?>[] paramtypes, MethodVisitor mw) {
		int idx = 0;
		for (int i = 0; i < paramtypes.length; ++i) {
			//add + 1 to the idx as ref is at var 0
			int size = loadVariable(paramtypes[i], idx + 1, mw);
			idx += size;
		}
	}

	private static int loadVariable(Class<?> t, int index, MethodVisitor mw) {
		if (t == boolean.class) {
			mw.visitVarInsn(Opcodes.ILOAD, index);
			return 1;
		} else if (t == byte.class) {
			mw.visitVarInsn(Opcodes.ILOAD, index);
			return 1;
		} else if (t == short.class) {
			mw.visitVarInsn(Opcodes.ILOAD, index);
			return 1;
		} else if (t == int.class) {
			mw.visitVarInsn(Opcodes.ILOAD, index);
			return 1;
		} else if (t == long.class) {
			mw.visitVarInsn(Opcodes.LLOAD, index);
			return 2;
		} else if (t == float.class) {
			mw.visitVarInsn(Opcodes.FLOAD, index);
			return 1;
		} else if (t == double.class) {
			mw.visitVarInsn(Opcodes.DLOAD, index);
			return 2;
		} else if (t == char.class) {
			mw.visitVarInsn(Opcodes.ILOAD, index);
			return 1;
		} else {
			mw.visitVarInsn(Opcodes.ALOAD, index);
			return 1;
		}
	}

	private static void returnType(MethodVisitor mw, Class<?> t) {
		if (t == void.class) {
			mw.visitInsn(Opcodes.RETURN);
		} else if (t == double.class) {
			mw.visitInsn(Opcodes.DRETURN);
		} else if (t == float.class) {
			mw.visitInsn(Opcodes.FRETURN);
		} else if (t == long.class) {
			mw.visitInsn(Opcodes.LRETURN);
		} else if (t == int.class) {
			mw.visitInsn(Opcodes.IRETURN);
		} else if (t == boolean.class) {
			mw.visitInsn(Opcodes.IRETURN);
		} else if (t == byte.class) {
			mw.visitInsn(Opcodes.IRETURN);
		} else if (t == short.class) {
			mw.visitInsn(Opcodes.IRETURN);
		} else if (t == char.class) {
			mw.visitInsn(Opcodes.IRETURN);
		} else {
			mw.visitInsn(Opcodes.ARETURN);
		}
	}

	private static void addInterfaceMethod(Map<MethodKey, Collection<MethodRef>> map, Method m,
			RMITransferPropertiesHolder rmiproperties) throws RMIProxyCreationFailedException {
		MethodRef mref = new MethodRef(m, rmiproperties);
		map.computeIfAbsent(mref.key, Functionals.arrayListComputer()).add(mref);
	}

	private static MethodRef reduceOverrideMethods(Collection<MethodRef> methodrefs) {
		List<MethodRef> methods = new ArrayList<>(methodrefs);
		for (Iterator<MethodRef> it = methods.iterator(); it.hasNext();) {
			MethodRef mr = it.next();
			for (MethodRef mr2 : methods) {
				if (mr == mr2) {
					continue;
				}
				if (mr.getMethod().getDeclaringClass().isAssignableFrom(mr2.getMethod().getDeclaringClass())) {
					it.remove();
					break;
				}
			}
		}
		MethodRef result;
		if (methods.size() > 1) {
			//there are conflicting methods
			//this can happen if a proxy implements methods with the same signature, present in different super interfaces
			Iterator<MethodRef> it = methods.iterator();
			result = it.next();
			while (it.hasNext()) {
				MethodRef mr = it.next();
				if (!result.propertiesEqual(mr)) {
					throw new RMIProxyCreationFailedException(
							"Different configuration on " + result.getMethod() + " and " + mr.getMethod());
				}
				if (result.properties.isDefaultOnFailure()) {
					//both calls default on failure, and therefore both has default implementation
					throw new RMIProxyCreationFailedException("Conflicting default methods annotated on "
							+ result.getMethod() + " and " + mr.getMethod());
				}
				if (result.getReturnType() != mr.getReturnType()) {
					if (result.getReturnType().isAssignableFrom(mr.getReturnType())) {
						result = mr;
					} else if (mr.getReturnType().isAssignableFrom(result.getReturnType())) {
					} else {
						throw new RMIProxyCreationFailedException(
								"Incompatible return types on " + result.getMethod() + " and " + mr.getMethod());
					}
				}
			}
		} else {
			result = methods.get(0);
		}
		return result;
	}

	private static void collectMethods(Map<MethodKey, Collection<MethodRef>> methods, Class<?> itf,
			RMITransferPropertiesHolder rmiproperties) {
		for (Method itfm : itf.getDeclaredMethods()) {
			int modifiers = itfm.getModifiers();
			if (Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
				continue;
			}
			addInterfaceMethod(methods, itfm, rmiproperties);
		}
		for (Class<?> si : itf.getInterfaces()) {
			collectMethods(methods, si, rmiproperties);
		}
	}

	private static int getMethodVariableSlot(Class<?>... currentstack) {
		int s = 1;
		for (int i = 0; i < currentstack.length; i++) {
			Class<?> c = currentstack[i];
			if (c == long.class || c == double.class) {
				s += 2;
			} else {
				s += 1;
			}
		}
		return s;
	}

	private static void writeCheckCastReturn(MethodVisitor mw, Class<?> returntype) {
		if (returntype == void.class) {
			mw.visitInsn(Opcodes.RETURN);
		} else if (returntype == double.class) {
			mw.visitTypeInsn(Opcodes.CHECKCAST, JAVA_LANG_DOUBLE_INTERNAL_NAME);
			mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JAVA_LANG_DOUBLE_INTERNAL_NAME, "doubleValue", "()D", false);
			mw.visitInsn(Opcodes.DRETURN);
		} else if (returntype == float.class) {
			mw.visitTypeInsn(Opcodes.CHECKCAST, JAVA_LANG_FLOAT_INTERNAL_NAME);
			mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JAVA_LANG_FLOAT_INTERNAL_NAME, "floatValue", "()F", false);
			mw.visitInsn(Opcodes.FRETURN);
		} else if (returntype == long.class) {
			mw.visitTypeInsn(Opcodes.CHECKCAST, JAVA_LANG_LONG_INTERNAL_NAME);
			mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JAVA_LANG_LONG_INTERNAL_NAME, "longValue", "()J", false);
			mw.visitInsn(Opcodes.LRETURN);
		} else if (returntype == int.class) {
			mw.visitTypeInsn(Opcodes.CHECKCAST, JAVA_LANG_INTEGER_INTERNAL_NAME);
			mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JAVA_LANG_INTEGER_INTERNAL_NAME, "intValue", "()I", false);
			mw.visitInsn(Opcodes.IRETURN);
		} else if (returntype == boolean.class) {
			mw.visitTypeInsn(Opcodes.CHECKCAST, JAVA_LANG_BOOLEAN_INTERNAL_NAME);
			mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JAVA_LANG_BOOLEAN_INTERNAL_NAME, "booleanValue", "()Z", false);
			mw.visitInsn(Opcodes.IRETURN);
		} else if (returntype == byte.class) {
			mw.visitTypeInsn(Opcodes.CHECKCAST, JAVA_LANG_BYTE_INTERNAL_NAME);
			mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JAVA_LANG_BYTE_INTERNAL_NAME, "byteValue", "()B", false);
			mw.visitInsn(Opcodes.IRETURN);
		} else if (returntype == short.class) {
			mw.visitTypeInsn(Opcodes.CHECKCAST, JAVA_LANG_SHORT_INTERNAL_NAME);
			mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JAVA_LANG_SHORT_INTERNAL_NAME, "shortValue", "()S", false);
			mw.visitInsn(Opcodes.IRETURN);
		} else if (returntype == char.class) {
			mw.visitTypeInsn(Opcodes.CHECKCAST, JAVA_LANG_CHARACTER_INTERNAL_NAME);
			mw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JAVA_LANG_CHARACTER_INTERNAL_NAME, "charValue", "()C", false);
			mw.visitInsn(Opcodes.IRETURN);
		} else {
			if (returntype != Object.class) {
				mw.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(returntype));
			}
			mw.visitInsn(Opcodes.ARETURN);
		}
	}

	private static void writeProxyConstructorMethod(String thisclassinternalname, String superclassinternalname,
			ClassWriter cw, Set<String> cachehelperfieldnames) {
		MethodVisitor constructorv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/ref/Reference;I)V", null,
				null);
		constructorv.visitCode();
		constructorv.visitVarInsn(Opcodes.ALOAD, 0);
		constructorv.visitVarInsn(Opcodes.ALOAD, 1);
		constructorv.visitVarInsn(Opcodes.ILOAD, 2);
		constructorv.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassinternalname, "<init>",
				"(Ljava/lang/ref/Reference;I)V", false);
		for (String chfname : cachehelperfieldnames) {
			constructorv.visitVarInsn(Opcodes.ALOAD, 0);
			constructorv.visitTypeInsn(Opcodes.NEW, RMICACHEHELPER_INTERNAL_NAME);
			constructorv.visitInsn(Opcodes.DUP);
			constructorv.visitMethodInsn(Opcodes.INVOKESPECIAL, RMICACHEHELPER_INTERNAL_NAME, "<init>", "()V", false);
			constructorv.visitFieldInsn(Opcodes.PUTFIELD, thisclassinternalname, chfname, JAVA_LANG_OBJECT_DESCRIPTOR);
		}
		constructorv.visitInsn(Opcodes.RETURN);
		constructorv.visitMaxs(0, 0);
		constructorv.visitEnd();
	}

	private static void writeCallInvokerMethodReturnInstructions(MethodVisitor mw, String thisclassinternalname,
			String methodfieldname, MethodRef mr, MethodKey key, String callmethodname, boolean collectstatistics,
			String markerclassinternalname) {
		loadCallInvokerMethodInstructionParameters(mw, thisclassinternalname, methodfieldname, key);

		String methoddescriptor;
		if (collectstatistics) {
			methoddescriptor = "(" + REMOTEPROXYOBJECT_DESCRIPTOR + METHODTRANSFERROPERTIES_DESCRIPTOR
					+ "[Ljava/lang/Object;" + RMISTATISTICS_DESCRIPTOR + ")Ljava/lang/Object;";
			mw.visitFieldInsn(Opcodes.GETSTATIC, markerclassinternalname, PROXY_MARKER_RMI_STATISTICS_FIELD_NAME,
					RMISTATISTICS_DESCRIPTOR);
		} else {
			methoddescriptor = "(" + REMOTEPROXYOBJECT_DESCRIPTOR + METHODTRANSFERROPERTIES_DESCRIPTOR
					+ "[Ljava/lang/Object;)Ljava/lang/Object;";
		}
		mw.visitMethodInsn(Opcodes.INVOKESTATIC, REMOTEPROXYOBJECT_INTERNAL_NAME, callmethodname, methoddescriptor,
				false);

		writeObjectReturnInstructions(mw, mr);
	}

	private static void loadCallInvokerMethodInstructionParameters(MethodVisitor mw, String thisclassinternalname,
			String methodfieldname, MethodKey key) {
		mw.visitVarInsn(Opcodes.ALOAD, 0);
		mw.visitFieldInsn(Opcodes.GETSTATIC, thisclassinternalname, methodfieldname,
				METHODTRANSFERROPERTIES_DESCRIPTOR);
		Class<?>[] argtypes = key.argTypes;
		writeLoadArgumentsArrayInstructions(mw, argtypes);
	}

	private static void writeObjectReturnInstructions(MethodVisitor mw, MethodRef mr) {
		writeObjectReturnInstructions(mw, mr.getReturnType());
	}

	private static void writeObjectReturnInstructions(MethodVisitor mw, Class<?> returntype) {
		if (returntype == void.class) {
			mw.visitInsn(Opcodes.RETURN);
		} else {
			writeCheckCastReturn(mw, returntype);
		}
	}

	private static void writeLoadArgumentsArrayInstructions(MethodVisitor mw, Class<?>[] argtypes) {
		if (argtypes.length > 0) {
			mw.visitLdcInsn(argtypes.length);
			mw.visitTypeInsn(Opcodes.ANEWARRAY, JAVA_LANG_OBJECT_INTERNAL_NAME);

			int idx = 0;
			for (int j = 0; j < argtypes.length; j++) {
				mw.visitInsn(Opcodes.DUP);
				mw.visitLdcInsn(j);

				Class<?> t = argtypes[j];
				if (t == boolean.class) {
					mw.visitVarInsn(Opcodes.ILOAD, idx + 1);
					mw.visitMethodInsn(Opcodes.INVOKESTATIC, JAVA_LANG_BOOLEAN_INTERNAL_NAME, "valueOf",
							"(Z)Ljava/lang/Boolean;", false);
					idx += 1;
				} else if (t == byte.class) {
					mw.visitVarInsn(Opcodes.ILOAD, idx + 1);
					mw.visitMethodInsn(Opcodes.INVOKESTATIC, JAVA_LANG_BYTE_INTERNAL_NAME, "valueOf",
							"(B)Ljava/lang/Byte;", false);
					idx += 1;
				} else if (t == short.class) {
					mw.visitVarInsn(Opcodes.ILOAD, idx + 1);
					mw.visitMethodInsn(Opcodes.INVOKESTATIC, JAVA_LANG_SHORT_INTERNAL_NAME, "valueOf",
							"(S)Ljava/lang/Short;", false);
					idx += 1;
				} else if (t == int.class) {
					mw.visitVarInsn(Opcodes.ILOAD, idx + 1);
					mw.visitMethodInsn(Opcodes.INVOKESTATIC, JAVA_LANG_INTEGER_INTERNAL_NAME, "valueOf",
							"(I)Ljava/lang/Integer;", false);
					idx += 1;
				} else if (t == long.class) {
					mw.visitVarInsn(Opcodes.LLOAD, idx + 1);
					mw.visitMethodInsn(Opcodes.INVOKESTATIC, JAVA_LANG_LONG_INTERNAL_NAME, "valueOf",
							"(J)Ljava/lang/Long;", false);
					idx += 2;
				} else if (t == float.class) {
					mw.visitVarInsn(Opcodes.FLOAD, idx + 1);
					mw.visitMethodInsn(Opcodes.INVOKESTATIC, JAVA_LANG_FLOAT_INTERNAL_NAME, "valueOf",
							"(F)Ljava/lang/Float;", false);
					idx += 1;
				} else if (t == double.class) {
					mw.visitVarInsn(Opcodes.DLOAD, idx + 1);
					mw.visitMethodInsn(Opcodes.INVOKESTATIC, JAVA_LANG_DOUBLE_INTERNAL_NAME, "valueOf",
							"(D)Ljava/lang/Double;", false);
					idx += 2;
				} else if (t == char.class) {
					mw.visitVarInsn(Opcodes.ILOAD, idx + 1);
					mw.visitMethodInsn(Opcodes.INVOKESTATIC, JAVA_LANG_CHARACTER_INTERNAL_NAME, "valueOf",
							"(C)Ljava/lang/Character;", false);
					idx += 1;
				} else {
					mw.visitVarInsn(Opcodes.ALOAD, idx + 1);
					idx += 1;
				}

				mw.visitInsn(Opcodes.AASTORE);
			}
		} else {
			mw.visitInsn(Opcodes.ACONST_NULL);
		}
	}

	private static Consumer<MethodVisitor> getMethodCacheFieldAssignInstructionWriter(String methodfieldname,
			String thisclassinternalname, MethodRef mr) {
		MethodKey key = mr.key;
		return mv -> {
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			//TODO if the declaring class of the method is not public, then the calling of the method will throw an IllegalAccessError
			//     this needs to be either disallowed, or fixed. This can happen if the method that specifies the
			//     properties are defined in a superinterface that is package private, although the subinterface is public
			//     this happened when TaskContext just implemented TaskDirectoryPathContext, and it was still package private
			mv.visitLdcInsn(Type.getType(mr.getMethod().getDeclaringClass()));
			mv.visitLdcInsn(key.name);
			mv.visitLdcInsn(key.argTypes.length);
			mv.visitTypeInsn(Opcodes.ANEWARRAY, JAVA_LANG_CLASS_INTERNAL_NAME);
			for (int j = 0; j < key.argTypes.length; j++) {
				mv.visitInsn(Opcodes.DUP);
				mv.visitLdcInsn(j);

				Class<?> argtype = key.argTypes[j];
				loadClassTypeConstant(argtype, mv);

				mv.visitInsn(Opcodes.AASTORE);
			}

			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JAVA_LANG_CLASS_INTERNAL_NAME, "getMethod",
					"(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, RMITRANSFERPROPERTIESHOLDER_INTERNAL_NAME,
					"getExecutableProperties", "(Ljava/lang/reflect/Method;)" + METHODTRANSFERROPERTIES_DESCRIPTOR,
					false);
			mv.visitFieldInsn(Opcodes.PUTSTATIC, thisclassinternalname, methodfieldname,
					METHODTRANSFERROPERTIES_DESCRIPTOR);
		};
	}

	private static void loadClassTypeConstant(Class<?> argtype, MethodVisitor clinitv) {
		if (argtype == byte.class) {
			clinitv.visitFieldInsn(Opcodes.GETSTATIC, JAVA_LANG_BYTE_INTERNAL_NAME, "TYPE", JAVA_LANG_CLASS_DESCRIPTOR);
		} else if (argtype == short.class) {
			clinitv.visitFieldInsn(Opcodes.GETSTATIC, JAVA_LANG_SHORT_INTERNAL_NAME, "TYPE",
					JAVA_LANG_CLASS_DESCRIPTOR);
		} else if (argtype == int.class) {
			clinitv.visitFieldInsn(Opcodes.GETSTATIC, JAVA_LANG_INTEGER_INTERNAL_NAME, "TYPE",
					JAVA_LANG_CLASS_DESCRIPTOR);
		} else if (argtype == long.class) {
			clinitv.visitFieldInsn(Opcodes.GETSTATIC, JAVA_LANG_LONG_INTERNAL_NAME, "TYPE", JAVA_LANG_CLASS_DESCRIPTOR);
		} else if (argtype == float.class) {
			clinitv.visitFieldInsn(Opcodes.GETSTATIC, JAVA_LANG_FLOAT_INTERNAL_NAME, "TYPE",
					JAVA_LANG_CLASS_DESCRIPTOR);
		} else if (argtype == double.class) {
			clinitv.visitFieldInsn(Opcodes.GETSTATIC, JAVA_LANG_DOUBLE_INTERNAL_NAME, "TYPE",
					JAVA_LANG_CLASS_DESCRIPTOR);
		} else if (argtype == boolean.class) {
			clinitv.visitFieldInsn(Opcodes.GETSTATIC, JAVA_LANG_BOOLEAN_INTERNAL_NAME, "TYPE",
					JAVA_LANG_CLASS_DESCRIPTOR);
		} else if (argtype == char.class) {
			clinitv.visitFieldInsn(Opcodes.GETSTATIC, JAVA_LANG_CHARACTER_INTERNAL_NAME, "TYPE",
					JAVA_LANG_CLASS_DESCRIPTOR);
		} else {
			clinitv.visitLdcInsn(Type.getType(argtype));
		}
	}

	private static void writeCallDefaultMethodAndReturn(MethodRef mr, Set<Class<?>> interfaces, MethodVisitor mrefv) {
		MethodKey key = mr.key;
		Class<?> defaultinvokesuperclass = mr.getMethod().getDeclaringClass();
		if (!interfaces.contains(defaultinvokesuperclass)) {
			//the declaring class of the default method to invoke is not a direct superinterface
			for (Class<?> itf : interfaces) {
				if (defaultinvokesuperclass.isAssignableFrom(itf)) {
					defaultinvokesuperclass = itf;
					break;
				}
			}
		}

		mrefv.visitVarInsn(Opcodes.ALOAD, 0);
		loadParameters(key.argTypes, mrefv);
		mrefv.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(defaultinvokesuperclass), key.name,
				Type.getMethodDescriptor(mr.getMethod()), true);
		returnType(mrefv, mr.getReturnType());
	}

	private static String createMethodHolderVariableName(int i) {
		return "0method$" + i;
	}

	private static String createCacheFieldHolderVariableName(int i) {
		return "0cachefield$" + i;
	}

}
