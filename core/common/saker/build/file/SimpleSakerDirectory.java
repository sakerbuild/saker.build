package saker.build.file;

import java.util.Collections;
import java.util.Map;

final class SimpleSakerDirectory extends SakerDirectoryBase {
	SimpleSakerDirectory(String name) {
		super(name);
		//this directory does not refer to any existing directory, so populating does nothing.
		//we can set the state to already populated
		populatedState = POPULATED_STATE_POPULATED;
	}

	@Override
	protected Map<String, SakerFileBase> populateImpl() {
		return Collections.emptyNavigableMap();
	}

	@Override
	protected SakerFileBase populateSingleImpl(String name) {
		return null;
	}
}
