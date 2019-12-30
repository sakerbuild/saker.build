package testing.saker.build.tests;

public abstract class CollectingMetricEnvironmentTestCase extends EnvironmentTestCase {

	@Override
	protected CollectingTestMetric createMetric() {
		return new CollectingTestMetric();
	}

	@Override
	protected CollectingTestMetric getMetric() {
		return (CollectingTestMetric) super.getMetric();
	}

}
