package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;

public abstract class StatelessTaskFactory<R> implements TaskFactory<R>, Externalizable {
	private static final long serialVersionUID = 1L;

	@Override
	public final void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public final void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	@Override
	public final int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public final boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

}
