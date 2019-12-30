package saker.build.util.serial;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Proxy;

import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;

final class ClassLoaderResolverObjectInputStream extends ObjectInputStream {
	private ClassLoaderResolver registry;

	public ClassLoaderResolverObjectInputStream(ClassLoaderResolver registry, InputStream in) throws IOException {
		super(in);
		this.registry = registry;
	}

	@Override
	protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
		String id = readUTF();
		if (id.isEmpty()) {
			id = null;
		}
		return Class.forName(desc.getName(), false, registry.getClassLoaderForIdentifier(id));
	}

	@Override
	@SuppressWarnings("deprecation")
	//getProxyClass is deprecated on JDK9+, however there is no alternative, and is not for removal
	protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
		String id = readUTF();
		if (id.isEmpty()) {
			id = null;
		}
		ClassLoader classloader = registry.getClassLoaderForIdentifier(id);
		Class<?>[] classes = new Class<?>[interfaces.length];
		for (int i = 0; i < interfaces.length; i++) {
			classes[i] = Class.forName(interfaces[i], false, classloader);
		}
		return Proxy.getProxyClass(classloader, classes);
	}
}