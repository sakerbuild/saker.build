package saker.build.thirdparty.saker.util.classloader;

/**
 * Interface for providing functionality of identifying a {@link ClassLoader} based on a string identifier.
 * <p>
 * Implementations of this class are responsible for generating an unique identifier for a given classloader and later
 * retrieving the associated classloader if available. The generated identifiers should be stable, meaning that even
 * after restarting the JVM they should generated the same identifier given the same circumstances.
 * <p>
 * This class is mainly used when objects are being serialized. During serialization, a classloader identifier is
 * written to the stream, so the serialized class can be correctly located when the stream is being read.
 * <p>
 * Implementations should strive to generate unique identifiers for a specific classloader instance. If the
 * implementations of a class loaded by a given classloader is changed, the generated identifier should change as well.
 * This ensures that only the class with the same implementation is loaded during deserialization.
 * 
 * @see SingleClassLoaderResolver
 * @see ClassLoaderResolverRegistry
 */
public interface ClassLoaderResolver {
	/**
	 * Gets the classloader identifier for the argument classloader.
	 * <p>
	 * Implementations may not handle all classloaders, but only the ones they know about. If the implementation doesn't
	 * recognize a given classloader, it should return <code>null</code> from this method. Returning <code>null</code>
	 * means that the serializing stream can look at other resolvers for an appropriate identifier.
	 * <p>
	 * Implementations of this method should not throw any exceptions.
	 * 
	 * @param classloader
	 *            The classloader to get the identifier for.
	 * @return The classloader identifier, or <code>null</code> if this resolver doesn't recognize the argument
	 *             classloader.
	 */
	public String getClassLoaderIdentifier(ClassLoader classloader);

	/**
	 * Looks up a classloader for a given classloader identifier.
	 * <p>
	 * Implementations can look up a classloader for a given identifier. They should examine the identifier, and based
	 * on its format look for a matching classloader. If the identifier is not recognized by this resolver, return
	 * <code>null</code>, so the deserializing stream can ask other resolvers to identify the classloader.
	 * <p>
	 * It is possible, that the resolver recognizes the format of the identifier, but doesn't find a classloader for it.
	 * This might be the case when the underlying implementation for a class has been changed, and therefore the
	 * generated classloader identifier for it as well. In this case the resolver should return <code>null</code>, to
	 * signal that the classloader is not found for the identifier.
	 * <p>
	 * Implementations of this method should not throw any exceptions.
	 * 
	 * @param identifier
	 *            The classloader identifier.
	 * @return The found classloader or <code>null</code> if this resolver didn't find an associated classloader for
	 *             it.
	 */
	public ClassLoader getClassLoaderForIdentifier(String identifier);
}
