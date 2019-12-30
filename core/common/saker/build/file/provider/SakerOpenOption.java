package saker.build.file.provider;

import java.nio.file.OpenOption;

import saker.apiextract.api.PublicApi;

/**
 * {@link OpenOption} enumeration that can be used with {@link SakerFileProvider SakerFileProviders}.
 * <p>
 * The support of these options are implementation dependent, however, when these options are passed to methods, they
 * are required to gracefully handle it.
 * <p>
 * Currently this enumeration defines no enums.
 */
@PublicApi
public enum SakerOpenOption implements OpenOption {
	//plan to add an option that allows to use a non-touching file writing, as in FileUtils.writeStreamEqualityCheckTo
}
