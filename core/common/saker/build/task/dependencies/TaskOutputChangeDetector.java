package saker.build.task.dependencies;

import java.io.Externalizable;

import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

/**
 * Strategy interface for detecting changes related to task outputs.
 * <p>
 * Implementations detect changes between two different task outputs in relation to the used data of them.
 * <p>
 * This interface is used to determine during incremental builds if a task needs to be rerun based on its input tasks.
 * <br>
 * If a task <b>A</b> depends on task <b>B</b> and a change detector was specified previously, then it will be invoked
 * to detect any changes between different outputs of the input task <b>B</b>.
 * <p>
 * Tasks which have their input tasks' outputs changed will be rerun in case of incremental builds.
 * <p>
 * This interface can be used to partially depend on tasks which can significantly improve build performance.
 * <p>
 * Example: <br>
 * Task <b>A</b> depends on task <b>B</b>. <br>
 * Task <b>B</b> returns a complex output with fields named <b>X</b> and <b>Y</b>. <br>
 * Task <b>A</b> only utilizes the value of the field <b>Y</b> to properly execute. In this case task <b>A</b> can
 * specify a change detector which compares the expected <b>Y</b> value to the current one specified by the
 * {@link #isChanged(Object)} parameter. <br>
 * If task <b>B</b> is rerun, and the field <b>Y</b> does not change, then task <b>A</b> will not be rerun, as its input
 * task <b>B</b> is not considered to be changed in relation to task <b>A</b>.
 * <p>
 * It is strongly recommended that implementations implement the {@link Externalizable} interface. <br>
 * It is recommended that implementations implement {@link #hashCode()} and {@link #equals(Object)}.
 * 
 * @see EqualityTaskOutputChangeDetector
 * @see CommonTaskOutputChangeDetector
 */
public interface TaskOutputChangeDetector {
	/**
	 * Checks if the parameter task output should be considered as changed.
	 * <p>
	 * If this methods throws a {@link RuntimeException} then it is an implementation error, however the caller can
	 * interpret it as if it returned <code>true</code>.
	 * 
	 * @param taskoutput
	 *            The task output to detect changes on.
	 * @return <code>true</code> if it is considered to be changed.
	 */
	public boolean isChanged(Object taskoutput);

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
