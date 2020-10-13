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
package saker.build.file;

import java.util.Collections;
import java.util.NavigableMap;

final class SimpleSakerDirectory extends SakerDirectoryBase {
	SimpleSakerDirectory(String name) {
		//this directory does not refer to any existing directory, so populating does nothing.
		//we can set the state to already populated
		super(name, PopulateState.populated());
	}

	@Override
	protected NavigableMap<String, SakerFileBase> populateImpl() {
		return Collections.emptyNavigableMap();
	}

	@Override
	protected SakerFileBase populateSingleImpl(String name) {
		return null;
	}
}
