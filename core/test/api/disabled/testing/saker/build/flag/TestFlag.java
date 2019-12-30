package testing.saker.build.flag;

import testing.saker.api.TaskTestMetric;
import testing.saker.api.TestMetric;

public final class TestFlag {
	public static final boolean ENABLED = false;

	public static TaskTestMetric metric() {
		return null;
	}

	private TestFlag() {
		throw new UnsupportedOperationException();
	}
}
