package saker.build.trace;

import saker.apiextract.api.PublicApi;
import saker.build.task.identifier.TaskIdentifier;
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
	 * Classifies the current task as a frontend task for a given worker task.
	 * <p>
	 * A frontend tasks is considered to be one that parses the input parameters and configuration, and starts a worker
	 * task that actually performs the build task operations.
	 * <p>
	 * A task can classify itself as frontend for multiple worker tasks.
	 * <p>
	 * In general, the build trace may hide frontend tasks by default to provide more meaningful information to the
	 * user.
	 * 
	 * @param workertaskid
	 *            The task identifier of the worker task.
	 */
	public static void classifyFrontendTask(TaskIdentifier workertaskid) {
		if (workertaskid == null) {
			return;
		}
		try {
			InternalTaskBuildTrace tt = getTaskTrace();
			tt.classifyFrontendTask(workertaskid);
		} catch (Exception e) {
			// no exceptions!
		}
	}

	/**
	 * Sets the title of the currently running task.
	 * <p>
	 * If the current task is an inner task, the title will be set for the inner task instead of the enclosing task.
	 * 
	 * @param title
	 *            The title.
	 */
	public static void setTitle(String title) {
		if (title == null) {
			return;
		}
		try {
			InternalTaskBuildTrace tt = getTaskTrace();
			tt.setTitle(title);
		} catch (Exception e) {
			// no exceptions!
		}
	}

	/**
	 * Sets the label of the currently running task in the timeline view.
	 * <p>
	 * If the current task is an inner task, the label will be set for the inner task instead of the enclosing task.
	 * <p>
	 * The timeline label should be more concise than the {@linkplain #setTitle(String) title} of the task as they can
	 * be displayed more tightly in the view.
	 * 
	 * @param label
	 *            The timeline label.
	 */
	public static void setTimelineLabel(String label) {
		if (label == null) {
			return;
		}
		try {
			InternalTaskBuildTrace tt = getTaskTrace();
			tt.setTimelineLabel(label);
		} catch (Exception e) {
			// no exceptions!
		}
	}
}
