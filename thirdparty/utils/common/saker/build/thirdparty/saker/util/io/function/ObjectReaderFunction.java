package saker.build.thirdparty.saker.util.io.function;

import java.io.IOException;
import java.io.ObjectInput;

import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Functional interface for reading objects from an arbitrary object input.
 * <p>
 * This functional interface is the abstraction over the {@link ObjectInput#readObject()} method. Users of this
 * interface may customize how a specific object is read from an input.
 * <p>
 * The type of the object input stream is based on the context of the object reading. Most likely will be
 * {@link ObjectInput} or similar classes.
 * 
 * @param <IN>
 *            The type of the object input.
 * @param <R>
 *            The result type of the object reading.
 * @see SerialUtils
 */
@FunctionalInterface
public interface ObjectReaderFunction<IN, R> {
	/**
	 * Applies the reading function to the argument object input.
	 * 
	 * @param in
	 *            The object input.
	 * @return The result of the object reading.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws ClassNotFoundException
	 *             If a class was not found during the reading operation.
	 */
	public R apply(IN in) throws IOException, ClassNotFoundException;
}
