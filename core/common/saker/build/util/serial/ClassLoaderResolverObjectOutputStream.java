package saker.build.util.serial;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;

final class ClassLoaderResolverObjectOutputStream extends ObjectOutputStream {
	private ClassLoaderResolver registry;

	public ClassLoaderResolverObjectOutputStream(ClassLoaderResolver registry, OutputStream out) throws IOException {
		super(out);
		this.registry = registry;
	}

	@Override
	protected void annotateClass(Class<?> cl) throws IOException {
		String id = registry.getClassLoaderIdentifier(cl.getClassLoader());
		if (id == null) {
			id = "";
		}
		writeUTF(id);
	}

	@Override
	protected void annotateProxyClass(Class<?> cl) throws IOException {
		String id = registry.getClassLoaderIdentifier(cl.getClassLoader());
		if (id == null) {
			id = "";
		}
		writeUTF(id);
	}
}