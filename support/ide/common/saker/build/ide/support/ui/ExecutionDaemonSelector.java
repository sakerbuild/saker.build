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
package saker.build.ide.support.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import saker.build.ide.support.properties.DaemonConnectionIDEProperty;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;

public class ExecutionDaemonSelector {
	public static final String LABEL_IN_PROCESS = "In-process (default)";

	private List<String> labels;
	private List<DaemonConnectionIDEProperty> properties;
	private List<String> connectionNames;
	private int executionDaemonIndex = -1;

	public ExecutionDaemonSelector(IDEProjectProperties properties) {
		reset(properties);
	}

	public ExecutionDaemonSelector(Iterable<? extends DaemonConnectionIDEProperty> daemonconnections,
			String executiondaemonconnectionname) {
		reset(daemonconnections, executiondaemonconnectionname);
	}

	public final void reset(Iterable<? extends DaemonConnectionIDEProperty> daemonconnections,
			String executiondaemonconnectionname) {
		init(daemonconnections, executiondaemonconnectionname);
	}

	public final void reset(IDEProjectProperties properties) {
		if (properties == null) {
			init(Collections.emptySet(), null);
			return;
		}
		init(properties.getConnections(), properties.getExecutionDaemonConnectionName());
	}

	private void init(Iterable<? extends DaemonConnectionIDEProperty> daemonconnections,
			String executiondaemonconnectionname) {
		labels = new ArrayList<>();
		connectionNames = new ArrayList<>();
		properties = ObjectUtils.newArrayList(daemonconnections);

		properties.sort(ExecutionDaemonSelector::compareDaemonConnectionIDEProperties);
		for (DaemonConnectionIDEProperty connprop : properties) {
			String connname = connprop.getConnectionName();
			if (ObjectUtils.isNullOrEmpty(connname)) {
				//ignore
				continue;
			}
			connectionNames.add(connname);
			labels.add(connname + " @" + connprop.getNetAddress());
		}
		connectionNames.add(0, null);
		properties.add(0, null);
		labels.add(0, LABEL_IN_PROCESS);
		executionDaemonIndex = connectionNames.indexOf(executiondaemonconnectionname);
		if (executionDaemonIndex < 0) {
			//not found, add a pseudo element at the end
			executionDaemonIndex = labels.size();
			properties.add(new DaemonConnectionIDEProperty(null, executiondaemonconnectionname, false));
			connectionNames.add(executiondaemonconnectionname);
			if (executiondaemonconnectionname == null) {
				labels.add("<null>");
			} else {
				labels.add(executiondaemonconnectionname + " @<not-found>");
			}
		}
	}

	public String getSelectedExecutionDaemonName() throws IndexOutOfBoundsException {
		return connectionNames.get(executionDaemonIndex);
	}

	public DaemonConnectionIDEProperty getPropertyAtIndex(int index) throws IndexOutOfBoundsException {
		return properties.get(index);
	}

	public void setExecutionDaemonIndex(int executionDaemonIndex) {
		this.executionDaemonIndex = executionDaemonIndex;
	}

	public int getExecutionDaemonIndex() {
		return executionDaemonIndex;
	}

	public List<String> getLabels() {
		return labels;
	}

	public static int compareDaemonConnectionIDEProperties(DaemonConnectionIDEProperty l,
			DaemonConnectionIDEProperty r) {
		int cmp = StringUtils.compareStringsNullFirst(l.getConnectionName(), r.getConnectionName());
		if (cmp != 0) {
			return cmp;
		}
		cmp = StringUtils.compareStringsNullFirst(l.getNetAddress(), r.getNetAddress());
		if (cmp != 0) {
			return cmp;
		}
		return 0;
	}
}
