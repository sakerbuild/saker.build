package saker.build.util.data;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import saker.build.util.data.annotation.ConverterConfiguration;

/**
 * Interface for specializing type conversion.
 * <p>
 * Implementations are provided with a {@linkplain ConversionContext conversion context}, and an object, to execute
 * conversion to the target type.
 * <p>
 * This interface is usually used with conjunction of the {@link ConverterConfiguration} interface. It can be used to
 * specialize the conversion mechanism of {@link DataConverterUtils}.
 * <p>
 * Implementations of this interface should have a public no-arg constructor.
 * <p>
 * The {@link DataConverter} classes are stateless, meaning, if they are called with the same arguments they will return
 * the same results. They may be cached to avoid construction of multiple objects.
 * 
 * @see ConverterConfiguration
 */
public interface DataConverter {
	/**
	 * Converts the argument object to the target type.
	 * <p>
	 * Implementations are required to either correctly convert the object to the target type, or throw a
	 * {@link ConversionFailedException}.
	 * <p>
	 * The target type may be any reflection type that is possible, but it is usually an instance of {@link Class}, or
	 * the generic {@link ParameterizedType}.
	 * <p>
	 * The conversion context can be used to access support data for the conversion. It allows the client to access the
	 * {@linkplain ConversionContext#getBaseClassLoader() classloader},
	 * {@linkplain ConversionContext#getTaskResultResolver() task result resolver}, and
	 * {@linkplain ConversionContext#genericChildContext(int) generic converters} if need be.
	 * <p>
	 * The caller may or may not do any type checking to ensure that the returned value is actually an instance of the
	 * target type. Clients should always ensure and test for their correct implementation.
	 * <p>
	 * When implementations of this method delegate conversion of generic elements, they should call
	 * {@link ConversionContext#genericChildContext(int)} and use the returned conversion context for converting the
	 * elements.
	 * 
	 * @param conversioncontext
	 *            The conversion context.
	 * @param value
	 *            The object to convert.
	 * @param targettype
	 *            The target type of the conversion.
	 * @return The converted object.
	 * @throws ConversionFailedException
	 *             If the conversion to the target type fails.
	 * @see DataConverterUtils#convertDefault(ConversionContext, Object, Type)
	 */
	public Object convert(ConversionContext conversioncontext, Object value, Type targettype)
			throws ConversionFailedException;
}
