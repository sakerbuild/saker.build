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
import java.util.List;

import saker.build.ide.support.properties.IDEPluginProperties;
import saker.build.runtime.execution.SakerLog.CommonExceptionFormat;

public class ExceptionFormatSelector {
	private List<String> labels;
	private List<String> formats;
	private int selectedIndex = -1;

	public ExceptionFormatSelector(IDEPluginProperties properties) {
		reset(properties);
	}

	public void reset(IDEPluginProperties properties) {
		if (properties == null) {
			init(null);
			return;
		}
		init(properties.getExceptionFormat());
	}

	private void init(String selected) {
		labels = new ArrayList<>();
		formats = new ArrayList<>();
		labels.add("Default");
		formats.add(null);

		labels.add("Full");
		formats.add(CommonExceptionFormat.FULL.name());
		labels.add("Reduced");
		formats.add(CommonExceptionFormat.REDUCED.name());
		labels.add("Compact");
		formats.add(CommonExceptionFormat.COMPACT.name());
		labels.add("Script trace only");
		formats.add(CommonExceptionFormat.SCRIPT_ONLY.name());
		labels.add("Java trace only");
		formats.add(CommonExceptionFormat.JAVA_TRACE.name());

		selectedIndex = formats.indexOf(selected);
		if (selectedIndex < 0) {
			labels.add("<" + selected + ">");
			formats.add(selected);
		}
	}

	public List<String> getLabels() {
		return labels;
	}

	public int getSelectedIndex() {
		return selectedIndex;
	}

	public void setSelectedIndex(int selectedIndex) {
		this.selectedIndex = selectedIndex;
	}

	public String getFormatNameAt(int index) {
		return formats.get(index);
	}

	public String getSelectedFormat() {
		return formats.get(selectedIndex);
	}
}
