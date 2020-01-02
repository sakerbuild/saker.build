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
import java.util.Map;

import saker.build.file.content.ContentDatabase;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SakerPath.Builder;

class RemovedMarkerSakerDirectory extends SakerDirectoryBase {
	public static final RemovedMarkerSakerDirectory INSTANCE = new RemovedMarkerSakerDirectory();

	RemovedMarkerSakerDirectory() {
		super(null, (Void) null);
		populatedState = POPULATED_STATE_POPULATED;
		internal_setParent(this, this);
	}

	@Override
	protected Map<String, SakerFileBase> populateImpl() {
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
