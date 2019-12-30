package testing.saker.build.flag;

import java.lang.ref.WeakReference;

import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.api.TaskTestMetric;
import testing.saker.api.TestMetric;

public final class TestFlag {
	private static final TaskTestMetric NULL_METRIC_INSTANCE = new TaskTestMetric() {
	};
	public static final boolean ENABLED = true;
	private static final InheritableThreadLocal<WeakReference<TestMetric>> METRIC_THREADLOCAL = new InheritableThreadLocal<>();

	public static void set(TestMetric metric) {
		if (metric == null) {
			METRIC_THREADLOCAL.remove();
		} else {
			METRIC_THREADLOCAL.set(new WeakReference<>(metric));
		}
	}

	public static TaskTestMetric metric() {
		TestMetric metric = ObjectUtils.getReference(METRIC_THREADLOCAL.get());
		if (metric instanceof TaskTestMetric) {
			return (TaskTestMetric) metric;
		}
		return NULL_METRIC_INSTANCE;
	}

	private TestFlag() {
		throw new UnsupportedOperationException();
	}
}
