package testing.saker.build.tests.tasks.cluster;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Objects;

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.util.property.UserParameterEnvironmentProperty;
import testing.saker.build.tests.EnvironmentTestCase;

public class TestClusterNameExecutionEnvironmentSelector implements TaskExecutionEnvironmentSelector, Externalizable {
	private static final long serialVersionUID = 1L;

	private String clusterName;

	/**
	 * For {@link Externalizable}.
	 */
	public TestClusterNameExecutionEnvironmentSelector() {
	}

	public TestClusterNameExecutionEnvironmentSelector(String clusterName) {
		Objects.requireNonNull(clusterName);
		this.clusterName = clusterName;
	}

	@Override
	public EnvironmentSelectionResult isSuitableExecutionEnvironment(SakerEnvironment environment) {
		UserParameterEnvironmentProperty prop = new UserParameterEnvironmentProperty(
				EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM);
		String val = environment.getEnvironmentPropertyCurrentValue(prop);
		if (clusterName.equals(val)) {
			return new EnvironmentSelectionResult(Collections.singletonMap(prop, val));
		}
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(clusterName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		clusterName = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((clusterName == null) ? 0 : clusterName.hashCode());
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
		TestClusterNameExecutionEnvironmentSelector other = (TestClusterNameExecutionEnvironmentSelector) obj;
		if (clusterName == null) {
			if (other.clusterName != null)
				return false;
		} else if (!clusterName.equals(other.clusterName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "TestClusterNameExecutionEnvironmentSelector[clusterName=" + clusterName + "]";
	}

}
