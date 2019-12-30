package saker.build.util.property;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * {@link ExecutionProperty} for determining if reporting of IDE configurations are expected from the tasks.
 * 
 * @see #INSTANCE
 */
@PublicApi
public final class IDEConfigurationRequiredExecutionProperty implements ExecutionProperty<Boolean>, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The singleton instance.
	 */
	public static final IDEConfigurationRequiredExecutionProperty INSTANCE = new IDEConfigurationRequiredExecutionProperty();

	/**
	 * For {@link Externalizable}.
	 * 
	 * @see #INSTANCE
	 */
	public IDEConfigurationRequiredExecutionProperty() {
	}

	@Override
	public Boolean getCurrentValue(ExecutionContext executioncontext) {
		return executioncontext.isIDEConfigurationRequired();
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
		return getClass().getSimpleName() + "[]";
	}
}
