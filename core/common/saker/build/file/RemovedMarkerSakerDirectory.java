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
