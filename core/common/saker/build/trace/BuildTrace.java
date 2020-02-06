package saker.build.trace;

import saker.apiextract.api.PublicApi;

/**
 * This is the client API class for reporting build trace information.
 * <p>
 * Clients can use the static methods in this class to report build trace related events and related information during
 * the build execution. These methods will record the events and handle them accordingly in an internal manner.
 * <p>
 * If the build execution is not set up to produce a build trace, the methods will return instantly. The methods will
 * attempt to return as fast as possible to avoid delaying the build itself. This may result in the build trace events
 * being cached, buffered, or otherwise reported in an implementation dependent manner.
 * <p>
 * The methods of this class will <b>never</b> throw any exceptions.
 */
@PublicApi
public final class BuildTrace {
	private BuildTrace() {
		throw new UnsupportedOperationException();
	}

	private static InternalBuildTrace getTrace() {
		try {
			return InternalBuildTrace.current();
		} catch (Exception e) {
			// this should never happen, but handle just in case as we may not throw
			return InternalBuildTrace.NULL_INSTANCE;
		}
	}
}
