package saker.build.thirdparty.saker.util.classloader;

import java.util.Objects;

/**
 * {@link ClassLoaderResolver} implementation that is capable of resolving a specific classloader.
 * <p>
 * This class is constructed with an identifier-classloader pair. If the implementation is asked to get the identifier,
 * or retrieve the classloader, the associated value for the mentioned pair will be returned.
 */
public class SingleClassLoaderResolver implements ClassLoaderResolver {
	private final String identifier;
	private final ClassLoader classLoader;

	/**
	 * Creates a new instance for the given identifier and classloader.
	 * 
	 * @param identifier
	 *            The classloader identifier.
	 * @param classLoader
	 *            The classloader.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public SingleClassLoaderResolver(String identifier, ClassLoader classLoader) throws NullPointerException {
		Objects.requireNonNull(identifier, "identifier");
		Objects.requireNonNull(classLoader, "classLoader");
		this.identifier = identifier;
		this.classLoader = classLoader;
	}

	/**
	 * Gets the classloader identifier that this resolver returns.
	 * 
	 * @return The identifier.
	 */
	public String getIdentifier() {
		return identifier;
	}

	/**
	 * Gets the classloader that this resolver handles.
	 * 
	 * @return The classloader.
	 */
	public ClassLoader getClassLoader() {
		return classLoader;
	}

	@Override
	public String getClassLoaderIdentifier(ClassLoader classloader) {
		if (this.classLoader == classloader) {
			return identifier;
		}
		return null;
	}

	@Override
	public ClassLoader getClassLoaderForIdentifier(String identifier) {
		if (this.identifier.equals(identifier)) {
			return classLoader;
		}
		return null;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (identifier != null ? "identifier=" + identifier + ", " : "")
				+ (classLoader != null ? "classLoader=" + classLoader : "") + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + System.identityHashCode(classLoader);
		result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
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
		SingleClassLoaderResolver other = (SingleClassLoaderResolver) obj;
		if (classLoader != other.classLoader) {
			return false;
		}
		if (identifier == null) {
			if (other.identifier != null)
				return false;
		} else if (!identifier.equals(other.identifier))
			return false;
		return true;
	}

}
