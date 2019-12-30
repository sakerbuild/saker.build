package saker.build.runtime.execution;

import java.io.Closeable;
import java.io.IOException;

import saker.build.runtime.classpath.ClassPathLoadManager.ClassPathLock;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.io.IOUtils;

public final class ScriptAccessorClassPathResource implements Closeable {
	protected ClassPathLock lock;
	protected MultiDataClassLoader classLoader;

	public ScriptAccessorClassPathResource(ClassPathLock lock, MultiDataClassLoader classLoader) {
		this.lock = lock;
		this.classLoader = classLoader;
	}

	public MultiDataClassLoader getClassLoader() {
		return classLoader;
	}

	public ClassPathLock getClassPathLock() {
		return lock;
	}

	@Override
	public void close() throws IOException {
		IOException exc = null;
		if (classLoader != null) {
			exc = IOUtils.closeExc(exc, classLoader.getDatasFinders());
		}
		exc = IOUtils.closeExc(exc, lock);
		IOUtils.throwExc(exc);
	}
}