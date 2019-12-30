package saker.build.util.data.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import saker.apiextract.api.PublicApi;
import saker.build.util.data.DataConverter;
import saker.build.util.data.DataConverterUtils;
import saker.build.util.data.GenericArgumentLocation;

/**
 * Repeatable annotation for fields and methods to specialize the conversion mechanism for a given location.
 * <p>
 * This annotation is used by {@link DataConverterUtils} to determine the converter to use when converting an object to
 * a target type. The {@link DataConverter DataConverters} specified in {@link #value()} will be used for converting an
 * object.
 * <p>
 * As generic types can be annotated with this annotation, the annotation can specify a {@link #genericArgumentIndex()}
 * to specialize the associated location for this converter configuration.
 * <p>
 * Example:
 * 
 * <pre>
 * {@literal @}ConverterConfiguration(MyConverter.class)
 * {@literal @}ConverterConfiguration(value = MyMapConverter.class, genericArgumentIndex = { 0 })
 * {@literal @}ConverterConfiguration(value = MyIntConverter.class, genericArgumentIndex = { 0, 0 })
 * {@literal @}ConverterConfiguration(value = MyStringConverter.class, genericArgumentIndex = { 0, 1})
 * public List&lt;Map&lt;Integer, String&gt;&gt; field;
 * </pre>
 * 
 * In the above example, when an arbitrary value is being converted for the specified field, the following rules apply
 * (given that the default conversion mechanism is used):
 * <ul>
 * <li><code>MyConverter</code> will be used to convert the intial value to a <code>List</code>.</li>
 * <li><code>MyMapConverter</code> will be used to convert the elements of the list to a <code>Map</code>.</li>
 * <li><code>MyIntConverter</code> will be used to convert the keys of the <code>Map</code> to an
 * <code>Integer</code>.</li>
 * <li><code>MyStringConverter</code> will be used to convert the values of the <code>Map</code> to a
 * <code>String</code></li>
 * </ul>
 * <p>
 * For each type annotated with this annotation, the generic argument indices should be unique for each annotation. If
 * multiple annotations are defined with the same generic argument locations, the implementation for conversion might
 * throw an exception, or handle the collision in an implementation dependent manner. It is not required to warn about
 * conflicting generic argument indices.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
@Repeatable(ConverterConfigurationHolder.class)
@PublicApi
public @interface ConverterConfiguration {
	/**
	 * The data converters to use to convert the value at the location specified by {@link #genericArgumentIndex()}.
	 * <p>
	 * A list of converters can be specified, and they will be tried to execute the conversion in the specified order if
	 * the previous one fails.
	 * 
	 * @return A list of converters.
	 */
	Class<? extends DataConverter>[] value() default {};

	/**
	 * Specifies the generic argument location of the associated converter configuration.
	 * <p>
	 * This array of indices specify the location on which the converter should be applied. If the array is empty, the
	 * converter will be applied to the outermost type. If it has one element, it will be applied to the first generic
	 * argument of the outermost type. If it has n elments, it will be applied to the <code>nth</code> outermost type,
	 * with the index path specified by the array.
	 * <p>
	 * See the documentation of this class for example.
	 * 
	 * @return The generic argument indices.
	 * @see GenericArgumentLocation
	 */
	int[] genericArgumentIndex() default {};
}
