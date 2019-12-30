package saker.build.cache;

import java.io.IOException;
import java.util.Collection;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIArrayListRemoteElementWrapper;

public interface BuildDataCache {
	public interface DataPublisher {
		public void putField(String key, ByteArrayRegion data);

		public ByteSink writeField(String key);

		public void close(boolean successful) throws IOException;
	}

	public interface DataEntry {
		public FieldEntry getField(String key) throws CacheFieldNotFoundException;

		public default ByteArrayRegion getFieldBytes(String key) throws CacheFieldNotFoundException {
			return getField(key).getData();
		}

		public default ByteSource openFieldInput(String key) throws CacheFieldNotFoundException {
			return new UnsyncByteArrayInputStream(getFieldBytes(key));
		}

		public default long writeFieldTo(String key, ByteSink os) throws CacheFieldNotFoundException, IOException {
			ByteArrayRegion data = getFieldBytes(key);
			os.write(data);
			return data.getLength();
		}

		public interface FieldEntry {
			public long size();

			public ByteArrayRegion getData();

			public default ByteSource openDataInput() {
				return new UnsyncByteArrayInputStream(getData());
			}

			public default long writeDataTo(ByteSink os) throws IOException {
				ByteArrayRegion data = getData();
				os.write(data);
				return data.getLength();
			}
		}
	}

	public DataPublisher publish(int hashcode, ByteArrayRegion key);

	@RMIWrap(RMIArrayListRemoteElementWrapper.class)
	public Collection<? extends DataEntry> lookup(int hashcode, Supplier<? extends ByteArrayRegion> keysupplier);
}
