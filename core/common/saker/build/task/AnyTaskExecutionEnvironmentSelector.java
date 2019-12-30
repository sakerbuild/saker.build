package saker.build.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;

import saker.apiextract.api.PublicApi;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * {@link TaskExecutionEnvironmentSelector} implementation for allowing a task to use any build environment.
 * 
 * @see #INSTANCE
 */
@PublicApi
public final class AnyTaskExecutionEnvironmentSelector implements TaskExecutionEnvironmentSelector, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The singleton instance.
	 */
	public static final AnyTaskExecutionEnvironmentSelector INSTANCE = new AnyTaskExecutionEnvironmentSelector();

	private static final EnvironmentSelectionResult EMPTY_SELECTION_RESULT = new EnvironmentSelectionResult(
			Collections.emptyMap());

	/**
	 * For {@link Externalizable}.
	 * 
	 * @see #INSTANCE
	 */
	public AnyTaskExecutionEnvironmentSelector() {
	}

	@Override
	public EnvironmentSelectionResult isSuitableExecutionEnvironment(SakerEnvironment environment) {
		return EMPTY_SELECTION_RESULT;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	@Override
	public int hashCode() {
		return getClass().getName().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
