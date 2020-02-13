package saker.build.trace;

import saker.apiextract.api.PublicApi;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.trace.InternalBuildTrace.InternalTaskBuildTrace;
import saker.build.trace.InternalBuildTrace.NullInternalBuildTrace;

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
 * 
 * @since saker.build 0.8.6
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
			return NullInternalBuildTrace.INSTANCE;
		}
	}

	private static InternalTaskBuildTrace getTaskTrace() {
		return InternalTaskBuildTrace.current();
	}

	/**
	 * Sets the display information of the currently running task or inner task.
	 * <p>
	 * The method will set the label that is displayed in the timeline view of the build trace, and the title that is
	 * displayed in other locations.
	 * <p>
	 * It is generally recommended that tht timeline label is short to be able to fit in a timeline block. The title may
	 * be longer, but should not be used to convey all information related to the task.
	 * <p>
	 * The titles should be unique enough for the user to differentiate different tasks of a kind, but not too long to
	 * avoid crowding the UI.
	 * <p>
	 * If the current task is an inner task, the display informations will be set for that instead of the enclosing
	 * task.
	 * 
	 * @param timelinelabel
	 *            The label for the timeline view or <code>null</code>.
	 * @param title
	 *            The title of the task or <code>null</code>.
	 */
	public static void setDisplayInformation(String timelinelabel, String title) {
		if (ObjectUtils.isNullOrEmpty(timelinelabel) && ObjectUtils.isNullOrEmpty(title)) {
			return;
		}
		try {
			InternalTaskBuildTrace tt = getTaskTrace();
			tt.setDisplayInformation(timelinelabel, title);
		} catch (Exception e) {
			// no exceptions!
		}
	}
}
