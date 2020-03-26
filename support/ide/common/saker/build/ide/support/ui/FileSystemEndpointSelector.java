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
import java.util.Set;

import saker.build.ide.support.SakerIDEProject;
import saker.build.ide.support.properties.DaemonConnectionIDEProperty;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class FileSystemEndpointSelector {
	public static final String LABEL_LOCAL_FILE_SYSTEM = "Local file system";
	public static final String LABEL_PROJECT_RELATIVE = "Project relative";

	private List<String> labels;
	private List<String> endpointNames;
	private int selectedIndex;

	public FileSystemEndpointSelector(IDEProjectProperties properties, String selectedendpoint) {
		reset(properties, selectedendpoint);
	}

	public FileSystemEndpointSelector(Iterable<? extends DaemonConnectionIDEProperty> connections,
			String selectedendpoint) {
		reset(connections, selectedendpoint);
	}

	public final void reset(IDEProjectProperties properties, String endpoint) {
		Set<? extends DaemonConnectionIDEProperty> connections = properties == null ? Collections.emptySet()
				: properties.getConnections();
		init(connections, endpoint);
	}

	public void reset(Iterable<? extends DaemonConnectionIDEProperty> connections, String selectedendpoint) {
		init(connections, selectedendpoint);
	}

	private void init(Iterable<? extends DaemonConnectionIDEProperty> connections, String selectedendpoint) {
		labels = new ArrayList<>();
		endpointNames = new ArrayList<>();

		List<? extends DaemonConnectionIDEProperty> properties = ObjectUtils.newArrayList(connections);
		properties.sort(ExecutionDaemonSelector::compareDaemonConnectionIDEProperties);

		for (DaemonConnectionIDEProperty connprop : properties) {
			String connname = connprop.getConnectionName();
			if (ObjectUtils.isNullOrEmpty(connname)) {
				//ignore
				continue;
			}
			endpointNames.add(connname);
			labels.add(connname + " @" + connprop.getNetAddress());
		}
		endpointNames.add(0, SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE);
		endpointNames.add(1, SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM);
		labels.add(0, LABEL_PROJECT_RELATIVE);
		labels.add(1, LABEL_LOCAL_FILE_SYSTEM);

		selectedIndex = endpointNames.indexOf(selectedendpoint);
		if (selectedIndex < 0) {
			selectedIndex = labels.size();
			endpointNames.add(selectedendpoint);
			if (selectedendpoint == null) {
				labels.add("<null>");
			} else {
				labels.add(selectedendpoint + " @<not-found>");
			}
		}
	}

	public int selectEndpoint(String endpointname) {
		int idx = endpointNames.indexOf(endpointname);
		if (idx < 0) {
			throw new IllegalArgumentException("Unknown endpoint: " + endpointname);
		}
		this.selectedIndex = idx;
		return idx;
	}

	public String getSelectedEndpointName() throws IndexOutOfBoundsException {
		return endpointNames.get(selectedIndex);
	}

	public void setSelectedIndex(int selectedIndex) {
		this.selectedIndex = selectedIndex;
	}

	public int getSelectedIndex() {
		return selectedIndex;
	}

	public List<String> getLabels() {
		return labels;
	}
}
