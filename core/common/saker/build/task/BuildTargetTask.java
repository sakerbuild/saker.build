package saker.build.task;

/**
 * Task interface for build target tasks.
 * <p>
 * This interface extends {@link ParameterizableTask} therefore build targets always handle parameters.
 */
public interface BuildTargetTask extends ParameterizableTask<BuildTargetTaskResult> {
}
