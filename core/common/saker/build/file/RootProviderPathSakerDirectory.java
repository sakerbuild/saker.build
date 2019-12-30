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
