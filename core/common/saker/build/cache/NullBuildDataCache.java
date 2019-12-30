package saker.build.cache;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.StreamUtils;

public class NullBuildDataCache implements BuildDataCache {
	private static final class NullDataPublisher implements DataPublisher {
		@Override
		public void close(boolean successful) throws IOException {
		}

		@Override
		public void putField(String key, ByteArrayRegion data) {
		}

		@Override
		public ByteSink writeField(String key) {
			return StreamUtils.nullByteSink();
		}

	}

	public static final BuildDataCache INSTANCE = new NullBuildDataCache();

	@Override
	public DataPublisher publish(int hashcode, ByteArrayRegion key) {
		return new NullDataPublisher();
	}

	@Override
	public Collection<? extends DataEntry> lookup(int hashcode, Supplier<? extends ByteArrayRegion> keysupplier) {
		return Collections.emptySet();
	}

}
