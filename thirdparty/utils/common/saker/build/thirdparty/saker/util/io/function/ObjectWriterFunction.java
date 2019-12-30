package saker.build.thirdparty.saker.util.io.function;

import java.io.IOException;
import java.io.ObjectOutput;

/**
 * Functional interface for writing objects to an arbitrary object output.
 * <p>
 * This functional interface represents a custom serializing function that can be applied for a given object output
 * stream and object.
 * <p>
 * This interfaces is very similar to {@link IOBiConsumer}, but is defined as the counterpart of
 * {@link ObjectReaderFunction}.
 * <p>
 * The type of the object output stream is based on the context of the object writing. Most likely will be
 * {@link ObjectOutput} or similar classes.
 * 
 * @param <OUT>
 *            The type of the object output.
 * @param <T>
 *            The type of the serialized object.
 */
@FunctionalInterface
public interface ObjectWriterFunction<OUT, T> {
	/**
	 * Applies this serializing function to the argument output and object.
	 * 
	 * @param out
	 *            The object output.
	 * @param object
	 *            The object to serialize.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the object is <code>null</code>, and this function doesn't allow <code>null</code>s.
	 */
	public void apply(OUT out, T object) throws IOException, NullPointerException;
}
