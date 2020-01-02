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
package testing.saker.build.tests;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ObjectUtils;

public abstract class VariablesMetricEnvironmentTestCase extends CollectingMetricEnvironmentTestCase {
	protected Map<String, ?> getTaskVariables() {
		return Collections.emptyNavigableMap();
	}

	@Override
	protected final CollectingTestMetric createMetric() {
		CollectingTestMetric metric = createMetricImpl();
		Map<String, ?> variables = getTaskVariables();
		if (!variables.isEmpty()) {
			Map<TaskName, TaskFactory<?>> injectedTaskFactories = ObjectUtils
					.newTreeMap(metric.getInjectedTaskFactories());
			for (Entry<String, ?> entry : variables.entrySet()) {
				String varname = entry.getKey();
				injectedTaskFactories.put(TaskName.valueOf(varname), new LiteralTaskFactory(entry.getValue()));
			}
			metric.setInjectedTaskFactories(injectedTaskFactories);
		}
		return metric;
	}

	protected CollectingTestMetric createMetricImpl() {
		return super.createMetric();
	}

}
