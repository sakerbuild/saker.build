package saker.build.util.cache;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import saker.build.runtime.environment.SakerEnvironment;

/**
 * Unique identifier for a cache entry.
 * <p>
 * Implementations of this interface uniquely identify a cached data entry. That means that if two cache keys
 * {@linkplain #equals(Object) equal} then they compute the same data.
 * <p>
 * Cache entries are separated into two objects. A Resource, and a Data object. The difference between them that any
 * unmanaged resources (e.g. files, network streams) reside in the Resource, and the Data object is pure data computed
 * based on the Resource. The lifetime of the two objects are managed differently. The Data can be garbage collected at
 * any time, while Resource will be closed when the Data is no longer accessible.
 * <p>
 * The Data and the Resource objects must never identity equal, and an {@link InvalidCacheKeyImplementationException}
 * will be thrown if they do. The Resource must not hold a strong reference to the Data. If it does, the Data will not
 * be garbage collectable.
 * <p>
 * As a general rule of thumb, the Data and Resource objects should be immutable and hold no state as they can be
 * accessed from multiple agents at the same time. With careful design of the objects, this restriction is not
 * necessary, but recommended.
 * <p>
 * Implementations can also validate a cache entry, to ensure that always the most up-to-date data is present and
 * recompute it if it's necessary.
 * <p>
 * When a retrieval is requested from the cache, the cache implementation will check if a cache entry for this key
 * already exists. If it does, {@link #validate(Object, Object)} will be called. If it returns <code>false</code>, then
 * the currently allocated resources will be closed, and reallocated using this cache key. If it returns
 * <code>true</code> then the current data will be returned. If the data has been already garbage collected, it will be
 * regenerated using the validated resource.
 * <p>
 * If no cache entry exists for the retrieval, the allocation and generation will proceed without any validation.
 * <p>
 * If the cache key implementation throws an error during allocation or generation, the cache entry will be removed
 * completly from the cache, and any open resources will be closed.
 * <p>
 * The cache implementation uses {@linkplain SoftReference soft} and {linkplain WeakReference weak} references to keep
 * track of the generated Datas. When it detects that they have been garbage collected, then the allocated Resource will
 * be closed sometime in the future via {@link #close(Object, Object)}, and the cache entry will be completly removed.
 * <p>
 * It is possible that the Data is garbage collected, and Resource still resides in the cache. When a new cache entry
 * retrieval is done, it can reuse the still available Resource if possible.
 * <p>
 * Each cache key can specify an expiry timeout for the generated data. The cache implementation will refer to the
 * generated Data using a {@link SoftReference} until the timeout expires, then it will be changed to a
 * {@link WeakReference}. The granularity of the expiry timeout checking is implementation dependent.
 * <p>
 * When a cache entry retrieval is requested, the reference to the returned Data will be refreshed to
 * {@link SoftReference}, and the expiry timeout is restarted.
 * <p>
 * When the cache implementation is closed, all cache entries are closed appropriately.
 * 
 * @param <Data>
 *            The type of the computed data.
 * @param <Resource>
 *            The type of the computed resource.
 * @see SakerEnvironment#getCachedData(CacheKey)
 */
public interface CacheKey<Data, Resource> {
	/**
	 * Allocates the Resource for this cache key.
	 * <p>
	 * The result of the allocation must never be <code>null</code>.
	 * <p>
	 * The allocated Resource can contain unmanaged data which requires explicit closing of the object. (E.g. file
	 * handles, network connections, etc...) Implementations will have an opportunity to close these resources in
	 * {@link #close(Object, Object)}.
	 * <p>
	 * If this method throws an exception, the cache entry will be removed for this cache key. Unclosed resources will
	 * be closed nonetheless.
	 * 
	 * @return The allocated Resource. Non-<code>null</code>.
	 * @throws Exception
	 *             If allocation of the resource failed. This exception is relayed to the caller.
	 */
	public Resource allocate() throws Exception;

	/**
	 * Generates the Data for this cache key based on a previously allocated Resource.
	 * <p>
	 * The result of the generation must never be <code>null</code>. The previously generated Resource must not hold a
	 * strong reference to the returned Data.
	 * <p>
	 * The returned Data should not contain any unmanaged data which requires explicit closing of the object. The
	 * returned Data might be garbage collected any time in the future when all references are released to it.
	 * <p>
	 * If this method throws an exception, the cache entry will be removed for this cache key. Unclosed resources will
	 * be closed nonetheless.
	 * 
	 * @param resource
	 *            The Resource from a previous {@link #allocate()} call.
	 * @return The generated Data.
	 * @throws Exception
	 *             If allocation of the resource failed. This exception is relayed to the caller.
	 */
	public Data generate(Resource resource) throws Exception;

	/**
	 * Validates the cached data if it's still useable.
	 * <p>
	 * The Data parameter might be <code>null</code>, if only the Resource is available for validation. Implementations
	 * should handle that gracefully.
	 * 
	 * @param data
	 *            The generated Data, or <code>null</code> if it is no longer available.
	 * @param resource
	 *            The allocated Resource. Never <code>null</code>.
	 * @return <code>true</code> if the passed arguments are in a valid state for usage.
	 */
	public boolean validate(Data data, Resource resource);

	/**
	 * Gets the expiry milliseconds of the generated cache Data.
	 * <p>
	 * After the expiry milliseconds elapse, the cache implementation will convert the {@link SoftReference soft}
	 * reference pointing to the generated Data to {@link WeakReference weak} reference. It will result in that the Data
	 * will be more easily garbage collected.
	 * <p>
	 * If this method returns negative, it will be normalized to 0 (zero).
	 * 
	 * @return The milliseconds of expiry.
	 */
	public long getExpiry();

	/**
	 * Closes the objects related to this cache key.
	 * <p>
	 * Implementations should close unmanaged objects in the allocated Resource.
	 * <p>
	 * The Data parameter should not play a role in the closing of the resources, but it is passed to this method
	 * nonetheless. If the Data is no longer accessible, <code>null</code> is used.
	 * 
	 * @param data
	 *            The generated Data or <code>null</code> if it's no longer available.
	 * @param resource
	 *            The allocated Resource to close. Never <code>null</code>.
	 * @throws Exception
	 *             In case of closing errors. Exceptions thrown from this method is usually not relayed to anyone, but
	 *             printed to an error stream.
	 */
	public void close(Data data, Resource resource) throws Exception;

	@Override
	public int hashCode();

	/**
	 * Checks if this cache key identifies the same cache entry and computes the same data as the parameter given the
	 * same circumstances.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);
}
