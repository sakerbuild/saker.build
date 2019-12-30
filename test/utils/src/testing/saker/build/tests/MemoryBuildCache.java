package testing.saker.build.tests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;

import saker.build.cache.BuildDataCache;
import saker.build.cache.BuildDataCache.DataEntry.FieldEntry;
import saker.build.thirdparty.saker.util.ConcurrentAppendAccumulator;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.cache.CacheFieldNotFoundException;

public class MemoryBuildCache implements BuildDataCache {
	private Map<Integer, ConcurrentAppendAccumulator<MemoryDataEntry>> entries = new ConcurrentSkipListMap<>();

	@Override
	public DataPublisher publish(int hashcode, ByteArrayRegion key) {
		return new DataPublisher() {
			private ConcurrentSkipListMap<String, MemoryFieldEntry> fields = new ConcurrentSkipListMap<>();

			@Override
			public void putField(String key, ByteArrayRegion data) {
				fields.put(key, new MemoryFieldEntry(data));
			}

			@Override
			public ByteSink writeField(String key) {
				return new UnsyncByteArrayOutputStream() {
					@Override
					public void close() {
						super.close();
						fields.put(key, new MemoryFieldEntry(toByteArrayRegion()));
					}
				};
			}

			@Override
			public void close(boolean successful) throws IOException {
				if (successful) {
					entries.computeIfAbsent(hashcode, x -> new ConcurrentAppendAccumulator<>())
							.add(new MemoryDataEntry(key, fields));
				}
			}
		};
	}

	@Override
	public Collection<? extends DataEntry> lookup(int hashcode, Supplier<? extends ByteArrayRegion> keysupplier) {
		List<DataEntry> result = new ArrayList<>();
		ConcurrentAppendAccumulator<MemoryDataEntry> dataentries = this.entries.get(hashcode);
		if (dataentries != null && !dataentries.isEmpty()) {
			ByteArrayRegion key = keysupplier.get();
			for (MemoryDataEntry mementry : dataentries) {
				if (!key.regionEquals(mementry.getKey())) {
					continue;
				}
				result.add(mementry);
			}
		}
		return result;
	}

	public void clear() {
		entries.clear();
	}

	private static class MemoryFieldEntry implements FieldEntry {
		private ByteArrayRegion data;

		public MemoryFieldEntry(ByteArrayRegion data) {
			this.data = data;
		}

		@Override
		public long size() {
			return data.getLength();
		}

		@Override
		public ByteArrayRegion getData() throws CacheFieldNotFoundException {
			return data;
		}

	}

	private static class MemoryDataEntry implements DataEntry {
		private ByteArrayRegion key;
		private Map<String, MemoryFieldEntry> fields;

		public MemoryDataEntry(ByteArrayRegion key, Map<String, MemoryFieldEntry> fields) {
			this.key = key;
			this.fields = fields;
		}

		@Override
		public FieldEntry getField(String key) throws CacheFieldNotFoundException {
			MemoryFieldEntry res = fields.get(key);
			if (res == null) {
				throw new CacheFieldNotFoundException(key);
			}
			return res;
		}

		public ByteArrayRegion getKey() {
			return key;
		}

	}
}
