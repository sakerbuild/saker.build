package saker.build.thirdparty.saker.util.classloader;

public class ClassLoaderAccessor {
	public static ClassLoader getPlatformClassLoaderParent() {
		//represents the boot classloader
		return ClassLoader.getPlatformClassLoader();
	}
}
