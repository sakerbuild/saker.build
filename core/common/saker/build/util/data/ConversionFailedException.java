package saker.build.util.data;

import java.lang.reflect.Type;

import saker.build.thirdparty.saker.util.ReflectTypes;

/**
 * Thrown when a conversion from one type to another fails.
 * 
 * @see DataConverterUtils
 * @see ConversionContext
 */
public class ConversionFailedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance with the object to convert, and the conversion target type.
	 * <p>
	 * A message describing the values, target type, and the classloaders of them will be generated.
	 * 
	 * @param value
	 *            The object to convert.
	 * @param targettype
	 *            The target type of the conversion.
	 */
	public ConversionFailedException(Object value, Type targettype) {
		super("Failed to convert " + value.getClass().getName() + ": " + " to " + targettype + " Classloaders: ("
				+ value.getClass().getClassLoader() + ") -> (" + ReflectTypes.getClassLoadersFromType(targettype)
				+ ")");
	}

	/**
	 * Creates a new instance with a message, the object to convert, and the conversion target type.
	 * <p>
	 * A message describing the values, target type, and the classloaders of them will be added to the argument message.
	 * 
	 * @param message
	 *            The message to add to the exception.
	 * @param value
	 *            The object to convert.
	 * @param targettype
	 *            The target type of the conversion.
	 */
	public ConversionFailedException(String message, Object value, Type targettype) {
		super(message + " (Failed to convert " + value.getClass().getName() + ": " + " to " + targettype
				+ " Classloaders: (" + value.getClass().getClassLoader() + ") -> ("
				+ ReflectTypes.getClassLoadersFromType(targettype) + "))");
	}

	/**
	 * @see RuntimeException#RuntimeException(Throwable)
	 */
	public ConversionFailedException(Throwable cause) {
		super(cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	public ConversionFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	public ConversionFailedException(String message) {
		super(message);
	}

}
