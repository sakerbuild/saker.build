/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package testing.saker.build.flag;

import java.lang.ref.WeakReference;

import testing.saker.api.TaskTestMetric;
import testing.saker.api.TestMetric;

public final class TestFlag {
	private static final TaskTestMetric NULL_METRIC_INSTANCE = new TaskTestMetric() {
	};
	public static final boolean ENABLED = true;
	private static final InheritableThreadLocal<MetricReference> METRIC_THREADLOCAL = new InheritableThreadLocal<>();

	public static void set(TestMetric metric) {
		if (metric == null) {
			METRIC_THREADLOCAL.remove();
		} else {
			MetricReference mr = METRIC_THREADLOCAL.get();
			METRIC_THREADLOCAL.set(new MetricReference(mr, new WeakReference<>(metric)));
		}
	}

	public static TaskTestMetric metric() {
		MetricReference mr = METRIC_THREADLOCAL.get();
		if (mr == null) {
			return NULL_METRIC_INSTANCE;
		}
		TestMetric metric = mr.get();
		if (metric instanceof TaskTestMetric) {
			return (TaskTestMetric) metric;
		}
		return NULL_METRIC_INSTANCE;
	}

	private TestFlag() {
		throw new UnsupportedOperationException();
	}

	private static class MetricReference {
		private MetricReference parent;
		private WeakReference<TestMetric> metric;

		public MetricReference(MetricReference parent, WeakReference<TestMetric> metric) {
			this.parent = parent;
			this.metric = metric;
		}

		public TestMetric get() {
			TestMetric tm = metric.get();
			if (tm != null) {
				return tm;
			}
			if (parent != null) {
				return parent.get();
			}
			return null;
		}
	}
}
