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

import saker.build.file.content.ContentDatabase;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SakerPath.Builder;

class MarkerSakerDirectory extends SakerDirectoryBase {
	public static final SakerDirectoryBase REMOVED_FROM_PARENT = new MarkerSakerDirectory();
	public static final SakerDirectoryBase POPULATED_NOT_PRESENT = new MarkerSakerDirectory();

	static {
		//we can't set this in the constructor as that would cause out-of-order initialization of REMOVED_FROM_PARENT
		internal_setParent(REMOVED_FROM_PARENT, REMOVED_FROM_PARENT);
		internal_setParent(POPULATED_NOT_PRESENT, REMOVED_FROM_PARENT);
	}
	
	MarkerSakerDirectory() {
		super(null, (Void) null);
		populatedState = POPULATED_STATE_POPULATED;
	}

	@Override
	protected NavigableMap<String, SakerFileBase> populateImpl() {
		return Collections.emptyNavigableMap();
	}

	@Override
	protected SakerFileBase populateSingleImpl(String name) {
		return null;
	}

	@Override
	Builder createSakerPathBuilder() {
		return SakerPath.builder();
	}

	@Override
	ContentDatabase getContentDatabase() {
		return null;
	}
}
