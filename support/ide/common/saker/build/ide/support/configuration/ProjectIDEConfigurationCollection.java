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
package saker.build.ide.support.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import saker.build.ide.configuration.IDEConfiguration;
import saker.build.ide.configuration.SimpleIDEConfiguration;
import saker.build.thirdparty.saker.util.ImmutableUtils;

public class ProjectIDEConfigurationCollection {
	private List<SimpleIDEConfiguration> configs = Collections.emptyList();

	public ProjectIDEConfigurationCollection() {
	}

	public ProjectIDEConfigurationCollection(Collection<? extends IDEConfiguration> configs) {
		if (configs.isEmpty()) {
			this.configs = Collections.emptyList();
			return;
		}
		List<SimpleIDEConfiguration> nlist = new ArrayList<>();
		for (IDEConfiguration idec : configs) {
			if (idec == null) {
				continue;
			}
			nlist.add(new SimpleIDEConfiguration(idec));
		}
		Collections.sort(nlist, (l, r) -> {
			int cmp;
			cmp = l.getIdentifier().compareTo(r.getIdentifier());
			if (cmp != 0) {
				return cmp;
			}
			cmp = l.getType().compareTo(r.getType());
			if (cmp != 0) {
				return cmp;
			}
			return 0;
		});
		removeDuplicateConfigurations(nlist);
		this.configs = ImmutableUtils.unmodifiableList(nlist);
	}

	private static void removeDuplicateConfigurations(List<SimpleIDEConfiguration> nlist) {
		Iterator<SimpleIDEConfiguration> it = nlist.iterator();
		if (!it.hasNext()) {
			return;
		}
		SimpleIDEConfiguration previdec = it.next();
		while (it.hasNext()) {
			SimpleIDEConfiguration idec = it.next();
			if (previdec.equals(idec)) {
				//remove the duplicate
				it.remove();
				continue;
			}
			previdec = idec;
		}
	}

	public Collection<? extends IDEConfiguration> getConfigurations() {
		return configs;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((configs == null) ? 0 : configs.hashCode());
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
		ProjectIDEConfigurationCollection other = (ProjectIDEConfigurationCollection) obj;
		if (configs == null) {
			if (other.configs != null)
				return false;
		} else if (!configs.equals(other.configs))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[configs=" + configs + "]";
	}

}
