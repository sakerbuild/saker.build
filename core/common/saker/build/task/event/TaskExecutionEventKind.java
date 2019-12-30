package saker.build.task.event;

public enum TaskExecutionEventKind {
	/**
	 * @see TaskIdTaskEvent
	 */
	START_TASK,
	/**
	 * @see TaskIdTaskEvent
	 */
	WAITED_TASK,
	/**
	 * @see TaskIdTaskEvent
	 */
	FINISH_RETRIEVED_TASK,

	;
}
