package saker.build.util.data.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import saker.apiextract.api.PublicApi;

/**
 * Container annotation for the repeatable annotation of {@link ConverterConfiguration}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
@PublicApi
public @interface ConverterConfigurationHolder {
	/**
	 * The repeated annotations.
	 * 
	 * @return The annotations.
	 */
	ConverterConfiguration[] value();
}
