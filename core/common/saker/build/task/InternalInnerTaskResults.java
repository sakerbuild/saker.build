package saker.build.task;

public interface InternalInnerTaskResults<R> {
	public InnerTaskResultHolder<R> internalGetNextOnTaskThread() throws InterruptedException;
}
