package saker.build.ide.support.properties;

import java.util.Set;

import saker.build.thirdparty.saker.util.ImmutableUtils;

public class PropertiesValidationException extends Exception {
	private static final long serialVersionUID = 1L;

	private Set<PropertiesValidationErrorResult> errors;

	public PropertiesValidationException(Set<PropertiesValidationErrorResult> errors) {
		this.errors = ImmutableUtils.makeImmutableLinkedHashSet(errors);
	}

	public Set<PropertiesValidationErrorResult> getErrors() {
		return errors;
	}
}
