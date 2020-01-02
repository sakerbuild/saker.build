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

import saker.build.file.content.ContentDatabase;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SakerPath.Builder;
import saker.build.file.provider.SakerFileProvider;

class RootProviderPathSakerDirectory extends ProviderPathSakerDirectory {
	/* default */ RootProviderPathSakerDirectory(ContentDatabase db, String name, SakerFileProvider fileProvider,
			SakerPath realPath) {
		super(db, name, fileProvider, realPath, (Void) null);
	}

	@Override
	Builder createSakerPathBuilder() {
		return SakerPath.builder(this.name);
	}
}
