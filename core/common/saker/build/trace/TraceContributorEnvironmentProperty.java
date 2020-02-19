package saker.build.trace;

import saker.apiextract.api.PublicApi;
import saker.build.exception.PropertyComputationFailedException;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;

/**
 * Extension interface for {@link EnvironmentProperty} to signal that the property contributes build trace information.
 * <p>
 * This interface can be optionally implemented by {@linkplain EnvironmentProperty environment properties} to signal
 * that they contribute meaningful information to the build trace.
 * <p>
 * As environment property values can be cached for subsequent builds in the same build environment, this interface can
 * be used to report build trace information even if the environment property is not calculated again.
 * <p>
 * You should not report build trace information in the {@link EnvironmentProperty#getCurrentValue(SakerEnvironment)}
 * function, but rather implement {@link #contributeBuildTraceInformation(Object, PropertyComputationFailedException)}
 * to record build trace information.
 * 
 * @param <T>
 *            The type of the environment property.
 * @see BuildTrace
 * @see BuildTrace#VALUE_CATEGORY_ENVIRONMENT
 * @since 0.8.9
 */
@PublicApi
public interface TraceContributorEnvironmentProperty<T> extends EnvironmentProperty<T> {
	/**
	 * Instructs the environment property to contribute build trace information.
	 * <p>
	 * The implementers of this method can use the {@link BuildTrace} interface to report information that should be
	 * recorded in the build trace.
	 * <p>
	 * The default implementation does nothing.
	 * 
	 * @param propertyvalue
	 *            The computed value of the environment property. <code>null</code> if it threw an exception.
	 * @param thrownexception
	 *            The exception that was thrown by the environment property wrapped in a
	 *            {@link PropertyComputationFailedException}. Use the
	 *            {@link PropertyComputationFailedException#getCause() getCause()} method to retrieve the actually
	 *            thrown exception. <br>
	 *            If the property computation completed successfully, this argument is <code>null</code>.
	 */
	public default void contributeBuildTraceInformation(T propertyvalue,
			PropertyComputationFailedException thrownexception) {
		//do nothing by default
	}
}
