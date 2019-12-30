package saker.build.thirdparty.saker.util.classloader;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;

/**
 * {@link ClassLoaderResolver} implementation that handles multiple resolvers.
 * <p>
 * Classloader resolvers can be registered in the registry, so when a resolve method is called, the registry can iterate
 * over the registered resolvers and call them in order to fulfill the resolve request.
 * <p>
 * The resolvers registered in the registry are <b>weakly referenced</b>, which means that when a resolver is added, the
 * caller should keep a strong reference to the added resolver, else the resolver may be garbage collected prematurely,
 * and the registration is automatically removed. Weak referencing ensures that the classloaders can be garbage
 * collected, and more error-tolerant in regards of forgetting to unregister resolvers.
 * <p>
 * The resolvers are registered with an additional identifier in the registry, which is arbitrary, but should make
 * attempt to uniquely identify the resolver itself. Only one resolver can be registered with a specific identifier.
 * <p>
 * The registry can be constructed with a default resolver, which is going to be called when no registered resolvers
 * succeeded to match a resolve request.
 * <p>
 * This class is thread safe, registering and unregistering can be done concurrently from multiple threads.
 */
public class ClassLoaderResolverRegistry implements ClassLoaderResolver {
	private static final char ID_SEPARATOR_CHARACTER = '\0';

	/**
	 * Maps resolver IDs to references of {@link ClassLoaderResolver}. The references might be weak, soft, or strong.
	 */
	private final Map<String, Reference<? extends ClassLoaderResolver>> resolvers = new ConcurrentSkipListMap<>();

	private final ClassLoaderResolver defaultResolver;

	/**
	 * Creates a new instance without a default resolver.
	 */
	public ClassLoaderResolverRegistry() {
		this.defaultResolver = null;
	}

	/**
	 * Creates a new instance with the argument default classloader resolver.
	 * 
	 * @param defaultResolver
	 *            The default classloader resolver or <code>null</code> to not use one.
	 */
	public ClassLoaderResolverRegistry(ClassLoaderResolver defaultResolver) {
		this.defaultResolver = defaultResolver;
	}

	/**
	 * Registers the specified classloader resolver with the given identifier in this registry.
	 * <p>
	 * <b>Callers must keep a strong reference to the registered resolver.</b>
	 * <p>
	 * The resolver identifiers must not be empty or <code>null</code>, and must not contain the <code>'\0'</code>
	 * character. It is recommended to not contain any special characters in it, to ensure future compatibility.
	 * <p>
	 * If a resolver is already registered with the same identifier, this method doesn't overwrite it, but throws an
	 * exception. This is another reason to attempt to use unique identifier in the usage context of the registry.
	 * 
	 * @param resolverid
	 *            The identifier for the registered resolver.
	 * @param resolver
	 *            The resolver to register.
	 * @throws IllegalArgumentException
	 *             If the identifier is <code>null</code> or empty. If the identifier contains an illegal character that
	 *             is reserved by the registry implementation. If a classloader resolver is already registered with the
	 *             same resolver identifier.
	 */
	public void register(String resolverid, ClassLoaderResolver resolver) throws IllegalArgumentException {
		if (ObjectUtils.isNullOrEmpty(resolverid)) {
			throw new IllegalArgumentException("Invalid resolver id: " + StringUtils.toStringQuoted(resolverid));
		}
		int sepidx = resolverid.indexOf(ID_SEPARATOR_CHARACTER);
		if (sepidx >= 0) {
			throw new IllegalArgumentException("Resolver id contains illegal character: '\\0' at index: " + sepidx);
		}
		registerImpl(resolverid, resolver, new WeakReference<>(resolver));
	}

	/**
	 * Unregisters a previously registered classloader resolver, if found.
	 * <p>
	 * This method is a no-op if the argument resolver is not registered with the given identifier.
	 * 
	 * @param resolverid
	 *            The identifier used during registration.
	 * @param resolver
	 *            The resolver to unregister.
	 * @see #register(String, ClassLoaderResolver)
	 */
	public void unregister(String resolverid, ClassLoaderResolver resolver) {
		Reference<? extends ClassLoaderResolver> ref = resolvers.get(resolverid);
		if (ref == null) {
			return;
		}
		ClassLoaderResolver gotresolver = ref.get();
		if (gotresolver == null) {
			resolvers.remove(resolverid, ref);
			return;
		}
		if (gotresolver != resolver) {
			return;
		}
		resolvers.remove(resolverid, ref);
	}

	private void registerImpl(String resolverid, ClassLoaderResolver resolver,
			Reference<? extends ClassLoaderResolver> reference) {
		while (true) {
			Reference<? extends ClassLoaderResolver> prev = resolvers.putIfAbsent(resolverid, reference);
			if (prev == null) {
				break;
			}
			ClassLoaderResolver pget = prev.get();
			if (pget != null) {
				if (pget == resolver) {
					break;
				}
				throw new IllegalArgumentException("Different resolver is already registered with id: " + resolverid
						+ " as " + resolver + " and " + pget);
			}
			boolean success = resolvers.replace(resolverid, prev, reference);
			if (success) {
				break;
			}
			//try replacing again
		}
	}

	@Override
	public String getClassLoaderIdentifier(ClassLoader cl) {
		for (Iterator<? extends Entry<String, ? extends Reference<? extends ClassLoaderResolver>>> it = resolvers
				.entrySet().iterator(); it.hasNext();) {
			Entry<String, ? extends Reference<? extends ClassLoaderResolver>> entry = it.next();
			ClassLoaderResolver resolver = entry.getValue().get();
			if (resolver == null) {
				it.remove();
				continue;
			}
			String id = resolver.getClassLoaderIdentifier(cl);
			if (id != null) {
				return entry.getKey() + ID_SEPARATOR_CHARACTER + id;
			}
		}
		if (defaultResolver != null) {
			String defaultresolverid = defaultResolver.getClassLoaderIdentifier(cl);
			if (defaultresolverid != null) {
				return ID_SEPARATOR_CHARACTER + defaultresolverid;
			}
		}
		return null;
	}

	@Override
	public ClassLoader getClassLoaderForIdentifier(String id) {
		if (id == null) {
			return null;
		}
		int idx = id.indexOf(ID_SEPARATOR_CHARACTER);
		if (idx < 0) {
			throw new IllegalArgumentException("Invalid id: " + id);
		}
		if (idx == 0) {
			if (defaultResolver != null) {
				String clid = id.substring(1);
				return defaultResolver.getClassLoaderForIdentifier(clid);
			}
			return null;
		}
		String resolverid = id.substring(0, idx);
		ClassLoaderResolver clresolver = ObjectUtils.getReference(resolvers.get(resolverid));
		if (clresolver == null) {
			//resolver not found, fallback to null
			return null;
		}
		String clid = id.substring(idx + 1);
		ClassLoader cl = clresolver.getClassLoaderForIdentifier(clid);
		if (cl == null) {
			//classloader not found, fallback to null
			return null;
		}
		return cl;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ (defaultResolver != null ? "defaultResolver=" + defaultResolver + ", " : "")
				+ (resolvers != null ? "classLoaders=" + resolvers : "") + "]";
	}
}
