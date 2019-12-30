package saker.build.thirdparty.saker.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

class Reflector {
	private Reflector() {
		throw new UnsupportedOperationException();
	}

	static MethodHandle getDefaultMethodHandle(Method method, Class<?> declaringClass)
			throws ReflectiveOperationException {
		Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);
		constructor.setAccessible(true);

		return constructor.newInstance(declaringClass).unreflectSpecial(method, declaringClass);
	}

}
