package saker.build.thirdparty.saker.rmi.connection;

class RMIProxyClassLoader extends ClassLoader implements RMIClassDefiner {
	public RMIProxyClassLoader(ClassLoader parent) {
		super(parent);
	}

	@Override
	public Class<?> defineClass(String name, byte[] bytes) {
		return defineClass(name, bytes, 0, bytes.length);
	}
}