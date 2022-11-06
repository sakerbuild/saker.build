package saker.build.task;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.trace.InternalBuildTrace.InternalTaskBuildTrace;

class InnerTaskContext extends InternalForwardingTaskContext {
	private static final AtomicReferenceFieldUpdater<InnerTaskContext, Throwable[]> ARFU_abortExceptions = AtomicReferenceFieldUpdater
			.newUpdater(InnerTaskContext.class, Throwable[].class, "abortExceptions");

	private final InternalTaskBuildTrace buildTrace;

	private volatile Throwable[] abortExceptions;

	private InnerTaskContext(TaskContext taskContext, InternalTaskBuildTrace buildTrace) throws NullPointerException {
		super(taskContext);
		this.buildTrace = buildTrace;
	}

	public static InnerTaskContext startInnerTask(TaskContext taskContext, TaskFactory<?> innertaskfactory) {
		return new InnerTaskContext(taskContext, ((InternalTaskContext) taskContext).internalGetBuildTrace()
				.startInnerTask(taskContext.getExecutionContext(), innertaskfactory));
	}

	public Throwable[] getAbortExceptions() {
		return abortExceptions;
	}

	@Override
	public InternalTaskBuildTrace internalGetBuildTrace() {
		return buildTrace;
	}

	@Override
	public void abortExecution(Throwable cause) throws NullPointerException {
		Objects.requireNonNull(cause, "cause");
		while (true) {
			Throwable[] excs = this.abortExceptions;
			Throwable[] narray;
			if (excs == null) {
				narray = new Throwable[] { cause };
			} else {
				narray = ArrayUtils.appended(excs, cause);
			}
			if (ARFU_abortExceptions.compareAndSet(this, excs, narray)) {
				break;
			}
			//try again
		}
	}
}