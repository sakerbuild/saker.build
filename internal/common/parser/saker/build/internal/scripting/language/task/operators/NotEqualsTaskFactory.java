package saker.build.internal.scripting.language.task.operators;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;

import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SimpleSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskFuture;
import saker.build.task.identifier.TaskIdentifier;

public class NotEqualsTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	private SakerTaskFactory left;
	private SakerTaskFactory right;

	/**
	 * For {@link Externalizable}.
	 */
	public NotEqualsTaskFactory() {
	}

	public NotEqualsTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		TaskIdentifier lefttaskid = left.createSubTaskIdentifier(thistaskid);
		TaskIdentifier righttaskid = right.createSubTaskIdentifier(thistaskid);

		TaskFuture<SakerTaskResult> leftfut = taskcontext.getTaskUtilities().startTaskFuture(lefttaskid, left);
		TaskFuture<SakerTaskResult> rightfut = taskcontext.getTaskUtilities().startTaskFuture(righttaskid, right);

		SakerTaskResult lefttaskres = leftfut.get();
		SakerTaskResult righttaskres = rightfut.get();
		return new SimpleSakerTaskResult<>(!EqualsTaskFactory.testEquality(taskcontext, lefttaskres, righttaskres));
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(left);
		out.writeObject(right);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		left = (SakerTaskFactory) in.readObject();
		right = (SakerTaskFactory) in.readObject();
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new NotEqualsTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		SakerLiteralTaskFactory lc = left.tryConstantize();
		if (lc == null) {
			return null;
		}
		SakerLiteralTaskFactory rc = right.tryConstantize();
		if (rc == null) {
			return null;
		}
		return new SakerLiteralTaskFactory(!EqualsTaskFactory.testEquality(lc.getValue(), rc.getValue()));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((left == null) ? 0 : left.hashCode());
		result = prime * result + ((right == null) ? 0 : right.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NotEqualsTaskFactory other = (NotEqualsTaskFactory) obj;
		if (left == null) {
			if (other.left != null)
				return false;
		} else if (!left.equals(other.left))
			return false;
		if (right == null) {
			if (other.right != null)
				return false;
		} else if (!right.equals(other.right))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(eq:(" + left + " == " + right + "))";
	}

}
