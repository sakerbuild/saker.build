package saker.build.file;

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
	};
}