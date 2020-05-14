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

import java.util.NavigableSet;

enum CommonDirectoryVisitPredicate implements DirectoryVisitPredicate {
	EVERYTHING {
		@Override
		public DirectoryVisitPredicate directoryVisitor(String name, SakerDirectory directory) {
			return this;
		}

		@Override
		public boolean visitFile(String name, SakerFile file) {
			return true;
		}

		@Override
		public boolean visitDirectory(String name, SakerDirectory directory) {
			return true;
		}
	},
	NOTHING {
		@Override
		public DirectoryVisitPredicate directoryVisitor(String name, SakerDirectory directory) {
			return null;
		}

		@Override
		public boolean visitFile(String name, SakerFile file) {
			return false;
		}

		@Override
		public boolean visitDirectory(String name, SakerDirectory directory) {
			return false;
		}
	},
	CHILDREN {
		@Override
		public DirectoryVisitPredicate directoryVisitor(String name, SakerDirectory directory) {
			return null;
		}

		@Override
		public boolean visitFile(String name, SakerFile file) {
			return true;
		}

		@Override
		public boolean visitDirectory(String name, SakerDirectory directory) {
			return true;
		}
	},
	CHILD_DIRECTORIES {
		@Override
		public DirectoryVisitPredicate directoryVisitor(String name, SakerDirectory directory) {
			return null;
		}

		@Override
		public boolean visitFile(String name, SakerFile file) {
			return false;
		}

		@Override
		public boolean visitDirectory(String name, SakerDirectory directory) {
			return true;
		}
	},
	CHILD_FILES {
		@Override
		public DirectoryVisitPredicate directoryVisitor(String name, SakerDirectory directory) {
			return null;
		}

		@Override
		public boolean visitFile(String name, SakerFile file) {
			return true;
		}

		@Override
		public boolean visitDirectory(String name, SakerDirectory directory) {
			return false;
		}
	},
	SUBDIRECTORIES {
		@Override
		public DirectoryVisitPredicate directoryVisitor(String name, SakerDirectory directory) {
			return this;
		}

		@Override
		public boolean visitFile(String name, SakerFile file) {
			return false;
		}

		@Override
		public boolean visitDirectory(String name, SakerDirectory directory) {
			return true;
		}
	},
	SUBFILES {
		@Override
		public DirectoryVisitPredicate directoryVisitor(String name, SakerDirectory directory) {
			return this;
		}

		@Override
		public boolean visitFile(String name, SakerFile file) {
			return true;
		}

		@Override
		public boolean visitDirectory(String name, SakerDirectory directory) {
			return false;
		}
	},
	SYNCHRONIZE_NOTHING {
		@Override
		public DirectoryVisitPredicate directoryVisitor(String name, SakerDirectory directory) {
			return null;
		}

		@Override
		public boolean visitFile(String name, SakerFile file) {
			return false;
		}

		@Override
		public boolean visitDirectory(String name, SakerDirectory directory) {
			return false;
		}

		@Override
		public NavigableSet<String> getSynchronizeFilesToKeep() {
			return null;
		}
	},

	;
}