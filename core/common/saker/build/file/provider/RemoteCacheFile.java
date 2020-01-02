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
package saker.build.file.provider;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.UnsyncBufferedInputStream;

/**
 * Class for caching files and their contents.
 * 
 * @deprecated Currently not used, but may be in the future.
 */
@Deprecated
public class RemoteCacheFile implements Closeable {
	//thread safety:
	//    region modifications are synchronized on RemoteCacheFile.this
	//    this includes the modifications and to the corresponding collections

	//    file provider calls should be invoked when no locks are entered

	private static final long BLOCK_LENGTH = 4 * 1024;

	private static final long OFFSET_REGIONLENGTH = 1;
	private static final long OFFSET_SIZE = OFFSET_REGIONLENGTH + 8;
	private static final long OFFSET_LASTMODIFIED = OFFSET_SIZE + 8;
	@SuppressWarnings("unused")
	private static final long OFFSET_PATH = OFFSET_LASTMODIFIED + 8;

	private static long calculateRegionLength(byte[] pathbytes, long size) {
		long metalength = 1 + 8 + 8 + 8 + 4 + pathbytes.length;
		long region = metalength + size;

		long mod = region % BLOCK_LENGTH;
		if (mod > 0) {
			region = region + BLOCK_LENGTH - mod;
		}
		return region;
	}

	private static class PersistingWeakReference extends WeakReference<PersistingInputStream> {
		private ContentRegion region;

		public PersistingWeakReference(PersistingInputStream referent, ReferenceQueue<? super PersistingInputStream> q,
				ContentRegion region) {
			super(referent, q);
			this.region = region;
		}

	}

	private static class ContentRegion implements Comparable<ContentRegion> {
		private final long position;

		/**
		 * True if the region is free to use.
		 */
		private boolean free;
		/**
		 * The total length of this cache region.
		 */
		private long regionLength;
		/**
		 * The size of the file in this region.
		 */
		private long size;
		/**
		 * The last modified date of the file in this region.
		 */
		private long lastModified;

		private transient long metadataLength;

		private transient PersistingWeakReference persistingStream;

		private transient final Set<Object> readingObjects = ObjectUtils.newSetFromMap(new WeakHashMap<>());

		public static ContentRegion newFreeRegion(long offset, long regionlength) {
			System.out.println("RemoteCacheFile.ContentRegion.newFreeRegion() " + offset + " - " + regionlength);
			ContentRegion result = new ContentRegion(offset);
			result.free = true;
			result.regionLength = regionlength;
			return result;
		}

		public static ContentRegion newDataRegion(long offset, long regionlength) {
			System.out.println("RemoteCacheFile.ContentRegion.newDataRegion() " + offset + " - " + regionlength);
			ContentRegion result = new ContentRegion(offset);
			result.free = false;
			result.regionLength = regionlength;
			return result;
		}

		private ContentRegion(long position) {
			this.position = position;
		}

		public long getEndOffset() {
			return position + regionLength;
		}

		public long getDataCapacity() {
			return regionLength - metadataLength;
		}

		public long getDataStartOffset() {
			return position + metadataLength;
		}

		public synchronized boolean isRecycleable() {
			return free && (persistingStream == null || persistingStream.get() == null) && readingObjects.isEmpty();
		}

		public synchronized void addReadingObject(Object reader) {
			readingObjects.add(reader);
		}

		public synchronized void removeReadingObject(Object reader) {
			readingObjects.remove(reader);
		}

		public synchronized boolean clearPersistingStreamReference(PersistingInputStream inputstream) {
			Reference<PersistingInputStream> ref = persistingStream;
			if (ref != null && ref.get() == inputstream) {
				persistingStream = null;
				ref.clear();
				return true;
			}
			return false;
		}

		public boolean isChanged(FileEntry attributes) {
			return this.size != attributes.size() || this.lastModified != attributes.getLastModifiedMillis();
		}

		@Override
		public int compareTo(ContentRegion o) {
			return Long.compare(position, o.position);
		}

		@Override
		public String toString() {
			return "ContentRegion [position=" + position + ", free=" + free + ", regionLength=" + regionLength
					+ ", size=" + size + "]";
		}

	}

	private class PersistingInputStream extends InputStream {
		private final SakerPath path;
		private final InputStream is;
		private final ContentRegion region;
		private int counter = 0;

		public PersistingInputStream(InputStream is, ContentRegion region, SakerPath path) {
			this.path = path;
			this.is = is;
			this.region = region;
			synchronized (fileAccessLock) {
				try {
					writeRegionHeaderLocked(region, path);
				} catch (IOException e) {
					counter = -1;
					failedToPersistData(region);
				}
			}
		}

		@Override
		public int read() throws IOException {
			byte[] singlebuffer = { -1 };
			int res = read(singlebuffer, 0, 1);
			if (res <= 0) {
				return -1;
			}
			return singlebuffer[0];
		}

		@Override
		public int read(byte[] b) throws IOException {
			return this.read(b, 0, b.length);
		}

		@Override
		public synchronized int read(byte[] b, int off, int len) throws IOException {
			int rc = is.read(b, off, len);
			if (rc > 0) {
				if (counter >= 0) {
					if (counter + rc > region.getDataCapacity()) {
						//region overflow
						counter = -1;
						failedToPersistData(region);
					} else {
						try {
							synchronized (fileAccessLock) {
								writeFile.seek(region.getDataStartOffset() + counter);
								writeFile.write(b, off, rc);
							}
							counter += rc;
						} catch (IOException e) {
							counter = -1;
							failedToPersistData(region);
						}
					}
				}
			} else {
				//end of input, file was read fully
				fullyPersistedFile(region, this);
			}
			return rc;
		}

		@Override
		public void close() throws IOException {
			persitingStreamClosed(region, this);
			is.close();
		}

	}

	private class CacheFileInputStream extends InputStream {
		private volatile RandomAccessFile readAccessFile;
		private final ContentRegion region;
		private long remaining;

		public CacheFileInputStream(ContentRegion region) throws IOException {
			region.addReadingObject(this);
			this.region = region;
			this.readAccessFile = getReaderCacheFileLocked(this);
			this.readAccessFile.seek(region.getDataStartOffset());
			this.remaining = region.size;
		}

		@Override
		public synchronized int read() throws IOException {
			if (remaining <= 0) {
				return -1;
			}
			--remaining;
			if (remaining <= 0) {
				releaseReaderCacheFile(this, readAccessFile);
				readAccessFile = null;
				region.removeReadingObject(this);
			}
			return readImpl();
		}

		@Override
		public int read(byte[] b) throws IOException {
			return read(b, 0, b.length);
		}

		@Override
		public synchronized long skip(long n) throws IOException {
			if (n >= remaining) {
				n = remaining;
				remaining = 0;
				releaseReaderCacheFile(this, readAccessFile);
				readAccessFile = null;
				region.removeReadingObject(this);
				return n;
			}
			remaining -= n;
			return n;
		}

		@Override
		public synchronized int read(byte[] b, int off, int len) throws IOException {
			if (len > remaining) {
				len = (int) remaining;
			}
			if (len <= 0) {
				return -1;
			}
			int result = readImpl(b, off, len);
			if (result > 0) {
				remaining -= result;
				if (remaining <= 0) {
					releaseReaderCacheFile(this, readAccessFile);
					readAccessFile = null;
					region.removeReadingObject(this);
				}
			}
			return result;
		}

		private int readImpl() throws IOException {
			RandomAccessFile file = readAccessFile;
			if (file == null) {
				return -1;
			}
			return file.read();
		}

		private int readImpl(byte[] b, int off, int len) throws IOException {
			RandomAccessFile file = readAccessFile;
			if (file == null) {
				return -1;
			}
			return file.read(b, off, len);
		}

		@Override
		public synchronized void close() {
			remaining = 0;
			releaseReaderCacheFile(this, readAccessFile);
			readAccessFile = null;
			region.removeReadingObject(this);
		}
	}

	private static class UsedReaderCacheFile {
		private final RandomAccessFile cacheFile;
		private Reference<?> owner;

		public UsedReaderCacheFile(RandomAccessFile cacheFile, Object owner) {
			this.cacheFile = cacheFile;
			this.owner = new WeakReference<>(owner);
		}

		public boolean isAbandoned() {
			return owner.get() == null;
		}

		public void changeOwner(Object owner) {
			this.owner = new WeakReference<>(owner);
		}
	}

	private final SakerFileProvider fileProvider;

	private final NavigableMap<SakerPath, ContentRegion> regions = new ConcurrentSkipListMap<>();
	private final NavigableMap<Long, Set<ContentRegion>> freeRegionSizeToRegionMap = new TreeMap<>();
	private final NavigableMap<Long, ContentRegion> regionsByOffsetMap = new TreeMap<>();

	private final Object fileAccessLock = new Object();
	private final File fileName;
	private final RandomAccessFile writeFile;
	private final FileLock lock;

	private final ConcurrentPrependAccumulator<RandomAccessFile> readerFilesCache = new ConcurrentPrependAccumulator<>();
	private final ConcurrentPrependAccumulator<UsedReaderCacheFile> usedCacheFiles = new ConcurrentPrependAccumulator<>();

	private final ReferenceQueue<PersistingInputStream> persistingReferencesQueue = new ReferenceQueue<>();

	public RemoteCacheFile(Path cachefile, SakerFileProvider provider) throws IOException {
		System.out.println("RemoteCacheFile.RemoteCacheFile() " + cachefile);
		this.fileProvider = provider;
		this.fileName = cachefile.toFile();
		this.writeFile = new RandomAccessFile(fileName, "rw");
		this.lock = this.writeFile.getChannel().tryLock(Long.MAX_VALUE - 1, 1, true);
		if (lock == null) {
			IOUtils.closePrint(this.writeFile);
			//XXX display a suggestion about adding the cache directory attribute?
			throw new IOException("Failed to lock cache file: " + cachefile);
		}
		try {
			while (true) {
				long offset = this.writeFile.getFilePointer();

				boolean free = this.writeFile.readBoolean();
				long regionlength = this.writeFile.readLong();

				if (free) {
					//deleted file, empty region
					addFreeRegionLoading(offset, regionlength);
					this.writeFile.seek(offset + regionlength);
					System.out
							.println("RemoteCacheFile.RemoteCacheFile() free region " + offset + " - " + regionlength);
					continue;
				}
				long size = this.writeFile.readLong();
				long lastmod = this.writeFile.readLong();

				int pathlen = this.writeFile.readInt();
				byte[] pathdata = new byte[pathlen];
				this.writeFile.readFully(pathdata);

				long datastartptr = this.writeFile.getFilePointer();
				SakerPath path = SakerPath.valueOf(new String(pathdata));
				FileEntry attrs;
				try {
					attrs = provider.getFileAttributes(path);
				} catch (IOException e) {
					attrs = null;
					System.out.println("RemoteCacheFile.RemoteCacheFile() failed to read file attributes " + path);
				}
				if (attrs == null || attrs.getSize() != size || attrs.getLastModifiedMillis() != lastmod) {
					//do not add entry to list
					this.writeFile.seek(offset);
					//change to free
					this.writeFile.writeBoolean(true);
					this.writeFile.seek(offset + regionlength);

					addFreeRegionLoading(offset, regionlength);
					System.out.println("RemoteCacheFile.RemoteCacheFile() attributes changed " + path + " - " + offset
							+ " - " + regionlength);
					continue;
				}
				ContentRegion entry = ContentRegion.newDataRegion(offset, regionlength);
				entry.lastModified = lastmod;
				entry.size = size;
				entry.metadataLength = datastartptr - offset;
				regions.put(path, entry);
				regionsByOffsetMap.put(offset, entry);

				this.writeFile.seek(offset + regionlength);

				System.out.println("RemoteCacheFile.RemoteCacheFile() cached file: " + path + " with size: " + size);
			}
		} catch (EOFException e) {
		}
		//truncate the file
		long lastoffset = getLengthOffsetLocked();
		if (this.writeFile.length() > lastoffset) {
			System.out.println("RemoteCacheFile.RemoteCacheFile() truncate to: " + lastoffset);
			this.writeFile.setLength(lastoffset);
		}
	}

	private void writeRegionHeaderLocked(ContentRegion region, SakerPath path) throws IOException {
		writeRegionHeaderLocked(region, path.toString().getBytes(StandardCharsets.UTF_8));
	}

	private void writeRegionHeaderLocked(ContentRegion region, byte[] pathbytes) throws IOException {
		synchronized (this.fileAccessLock) {
			this.writeFile.seek(region.position);
			this.writeFile.writeBoolean(region.free);
			this.writeFile.writeLong(region.regionLength);
			this.writeFile.writeLong(region.size);
			this.writeFile.writeLong(region.lastModified);
			this.writeFile.writeInt(pathbytes.length);
			this.writeFile.write(pathbytes);
			region.metadataLength = this.writeFile.getFilePointer() - region.position;
		}
	}

	private void returnCacheFileForBytes(RandomAccessFile file) {
		readerFilesCache.add(file);
	}

	private RandomAccessFile getReaderCacheFileForBytes() throws FileNotFoundException {
		RandomAccessFile got = readerFilesCache.take();
		if (got != null) {
			return got;
		}
		for (Iterator<UsedReaderCacheFile> it = usedCacheFiles.iterator(); it.hasNext();) {
			UsedReaderCacheFile used = it.next();
			synchronized (used) {
				if (used.isAbandoned()) {
					it.remove();
					return used.cacheFile;
				}
			}
		}
		return new RandomAccessFile(fileName, "r");
	}

	private RandomAccessFile getReaderCacheFileLocked(CacheFileInputStream owner) throws FileNotFoundException {
		RandomAccessFile got = readerFilesCache.take();
		if (got != null) {
			usedCacheFiles.add(new UsedReaderCacheFile(got, owner));
			return got;
		}
		for (UsedReaderCacheFile used : usedCacheFiles) {
			synchronized (used) {
				if (used.isAbandoned()) {
					used.changeOwner(owner);
					return used.cacheFile;
				}
			}
		}
		RandomAccessFile file = new RandomAccessFile(fileName, "r");
		usedCacheFiles.add(new UsedReaderCacheFile(file, owner));
		return file;
	}

	private void releaseReaderCacheFile(CacheFileInputStream owner, RandomAccessFile readAccessFile) {
		for (Iterator<UsedReaderCacheFile> it = usedCacheFiles.iterator(); it.hasNext();) {
			UsedReaderCacheFile cf = it.next();
			if (cf.owner.get() == owner) {
				it.remove();
				break;
			}
		}
		readerFilesCache.add(readAccessFile);
	}

	private void addFreeRegionLoading(long offset, long regionlength) {
		ContentRegion region = ContentRegion.newFreeRegion(offset, regionlength);
		putFreeRegionLocked(region);
		Entry<Long, ContentRegion> preventry = regionsByOffsetMap.lowerEntry(offset);
		if (preventry != null) {
			ContentRegion prevregion = preventry.getValue();
			if (prevregion.free) {
				//prev region is free
				//just extend it, and be done
				prevregion.regionLength += regionlength;
				return;
			}
		}
		regionsByOffsetMap.put(offset, region);
	}

	private void flushAbandonedPersistingReferences() {
		while (true) {
			PersistingWeakReference ref = (PersistingWeakReference) persistingReferencesQueue.poll();
			if (ref == null) {
				return;
			}
			failedToPersistData(ref.region);
		}
	}

	private ContentRegion getOrAllocateEntryForPathLocked(SakerPath path, long datasize) {
		flushAbandonedPersistingReferences();

		byte[] pathbytes = path.toString().getBytes(StandardCharsets.UTF_8);
		long regionlength = calculateRegionLength(pathbytes, datasize);

		SortedMap<Long, Set<ContentRegion>> tail = freeRegionSizeToRegionMap.tailMap(regionlength);
		for (Set<ContentRegion> regionsset : tail.values()) {
			for (Iterator<ContentRegion> it = regionsset.iterator(); it.hasNext();) {
				ContentRegion region = it.next();
				if (!region.isRecycleable()) {
					continue;
				}
				//found a region that can be used
				it.remove();
				region.free = false;
				if (region.regionLength != regionlength) {
					//we need to split the region
					long newnewentrylen = region.regionLength - regionlength;
					long newentryoffset = region.position + regionlength;
					try {
						synchronized (fileAccessLock) {
							writeFile.seek(region.position);
							writeFile.writeBoolean(true);
							writeFile.writeLong(regionlength);
							writeFile.seek(newentryoffset);
							writeFile.writeBoolean(true);
							writeFile.writeLong(newnewentrylen);
						}
					} catch (IOException e) {
						// TODO: handle exception
						e.printStackTrace();
					}
					addFreeRegionLocked(ContentRegion.newFreeRegion(newentryoffset, newnewentrylen));
					region.regionLength = regionlength;
				}
				return region;
			}
		}
		//allocate a region, as there is no free region
		long currentlen = getLengthOffsetLocked();
		return ContentRegion.newFreeRegion(currentlen, regionlength);
	}

	public ByteSource openInput(SakerPath path) throws IOException {
		ContentRegion region = regions.get(path);
		FileEntry attributes;
		try {
			attributes = fileProvider.getFileAttributes(path);
		} catch (IOException e) {
			if (region != null) {
				synchronized (this) {
					removeEntryAndWriteFileImplLocked(path, region);
				}
			}
			throw e;
		}
		return openInput(path, attributes, region);
	}

	private ByteSource openInput(SakerPath path, FileEntry attributes, ContentRegion region)
			throws FileNotFoundException, IOException {
		if (region == null) {
			//allocate new entry with region size
			return openInputAndAllocateNewRegion(path, attributes);
		}

		boolean changed;
		synchronized (this) {
			region.addReadingObject(this);
			changed = region.isChanged(attributes);
			if (changed) {
				//file was modified
				removeEntryAndWriteFileImplLocked(path, region);
			}
		}
		try {
			if (changed) {
				return openInputAndAllocateNewRegion(path, attributes);
			}
			try {
				return ByteSource.valueOf(new UnsyncBufferedInputStream(new CacheFileInputStream(region)));
			} catch (IOException e) {
				e.printStackTrace();
				return fileProvider.openInput(path);
			}
		} finally {
			region.removeReadingObject(this);
		}
	}

	private ByteSource openInputAndAllocateNewRegion(SakerPath path, FileEntry attributes)
			throws IOException, FileNotFoundException {
		System.out.println("RemoteCacheFile.openInputAndAllocateNewRegionImpl() call fileprovider " + path);
		ByteSource input = fileProvider.openInput(path);

		ContentRegion allocentry;
		synchronized (this) {
			allocentry = getOrAllocateEntryForPathLocked(path, attributes.size());
			allocentry.addReadingObject(this);
			try {
				//XXX simplify this chain
				PersistingInputStream persistingstream = new PersistingInputStream(ByteSource.toInputStream(input),
						allocentry, path);
				allocentry.persistingStream = new PersistingWeakReference(persistingstream, persistingReferencesQueue,
						allocentry);
				allocentry.lastModified = attributes.getLastModifiedMillis();
				allocentry.free = false;
				return ByteSource.valueOf(new UnsyncBufferedInputStream(persistingstream));
			} finally {
				allocentry.removeReadingObject(this);
			}
		}
	}

	public ByteArrayRegion getAllBytes(SakerPath path) throws IOException {
		ContentRegion region = regions.get(path);
		FileEntry attributes;
		try {
			attributes = fileProvider.getFileAttributes(path);
		} catch (IOException e) {
			if (region != null) {
				synchronized (this) {
					removeEntryAndWriteFileImplLocked(path, region);
				}
			}
			throw e;
		}
		return getAllBytes(path, attributes, region);
	}

	private ByteArrayRegion getAllBytes(SakerPath path, FileEntry attributes, ContentRegion region) throws IOException {
		if (region == null) {
			return getAllBytesAndAllocateNewRegion(path, attributes);
		}
		boolean changed;
		synchronized (this) {
			region.addReadingObject(this);
			changed = region.isChanged(attributes);
			if (changed) {
				//file was modified
				removeEntryAndWriteFileImplLocked(path, region);
			}
		}
		try {
			if (changed) {
				return getAllBytesAndAllocateNewRegion(path, attributes);
			}
			try {
				return getCachedRegionBytes(region);
			} catch (IOException e) {
				e.printStackTrace();
				return fileProvider.getAllBytes(path);
			}
		} finally {
			region.removeReadingObject(this);
		}
	}

	private ByteArrayRegion getAllBytesAndAllocateNewRegion(SakerPath path, FileEntry attributes) throws IOException {
		//XXX we should have a method that retrieves both the bytes and the attributes atomically
		System.out.println("RemoteCacheFile.getAllBytesAndAllocateNewRegionImpl() call fileprovider " + path);
		ByteArrayRegion allbytes = fileProvider.getAllBytes(path);
		int size = allbytes.getLength();

		synchronized (this) {
			ContentRegion allocentry = getOrAllocateEntryForPathLocked(path, size);
			allocentry.lastModified = attributes.getLastModifiedMillis();
			try {
				synchronized (this.fileAccessLock) {
					this.writeFile.seek(allocentry.getDataStartOffset());
					fullyPersistedRegionLocked(allocentry, path, size);
					allbytes.writeTo(this.writeFile);
				}
			} catch (IOException e) {
				e.printStackTrace();
				//failed to write the data to the file
				if (allocentry.getEndOffset() != getLengthOffsetLocked()) {
					failedToPersistDataLocked(allocentry);
				}
			}
			return allbytes;
		}
	}

	private ByteArrayRegion getCachedRegionBytes(ContentRegion region) throws IOException {
		byte[] data = new byte[(int) region.size];
		RandomAccessFile file = getReaderCacheFileForBytes();
		try {
			file.seek(region.getDataStartOffset());
			file.readFully(data);
		} finally {
			returnCacheFileForBytes(file);
		}
		return ByteArrayRegion.wrap(data);
	}

	private void removeEntryAndWriteFileImplLocked(SakerPath path, ContentRegion entry) {
		boolean removed = regions.remove(path, entry);
		if (removed) {
			invalidateEntryAndWriteFileLocked(entry);
		}
	}

	private void invalidateEntryAndWriteFileLocked(ContentRegion entry) {
		synchronized (fileAccessLock) {
			//invalidate file data
			try {
				this.writeFile.seek(entry.position);
				this.writeFile.writeBoolean(true);
			} catch (IOException e2) {
				e2.printStackTrace();
			}
		}
		failedToPersistDataLocked(entry);
	}

	public void invalidate(SakerPath path) {
		synchronized (this) {
			ContentRegion entry = regions.remove(path);
			if (entry == null) {
				return;
			}
			invalidateEntryAndWriteFileLocked(entry);
		}
	}

	public void invalidateEverythingInDirectory(SakerPath directory) {
		synchronized (this) {
			SortedMap<SakerPath, ContentRegion> tail = regions.tailMap(directory, false);
			for (Iterator<Entry<SakerPath, ContentRegion>> it = tail.entrySet().iterator(); it.hasNext();) {
				Entry<SakerPath, ContentRegion> entry = it.next();
				if (!entry.getKey().startsWith(directory)) {
					return;
				}
				it.remove();
				invalidateEntryAndWriteFileLocked(entry.getValue());
			}
		}
	}

	public void invalidateFilesInDirectory(SakerPath directory, Set<String> filenames) {
		int nc = directory.getNameCount();
		synchronized (this) {
			SortedMap<SakerPath, ContentRegion> tail = regions.tailMap(directory, false);
			for (Iterator<Entry<SakerPath, ContentRegion>> it = tail.entrySet().iterator(); it.hasNext();) {
				Entry<SakerPath, ContentRegion> entry = it.next();
				SakerPath p = entry.getKey();
				if (!p.startsWith(directory)) {
					return;
				}
				if (p.getNameCount() > nc) {
					String childfname = p.getName(nc);
					if (filenames.contains(childfname)) {
						it.remove();
						invalidateEntryAndWriteFileLocked(entry.getValue());
					}
				}
			}
		}
	}

	private long getLengthOffsetLocked() {
		Entry<Long, ContentRegion> last = regionsByOffsetMap.lastEntry();
		if (last == null) {
			return 0;
		}
		ContentRegion lastregion = last.getValue();
		return lastregion.position + lastregion.regionLength;
	}

	private void addFreeRegionLocked(ContentRegion region) {
		region.free = true;
		Entry<Long, ContentRegion> preventry = regionsByOffsetMap.lowerEntry(region.regionLength);
		Entry<Long, ContentRegion> nextentry = regionsByOffsetMap.higherEntry(region.regionLength);
		boolean prevfree = preventry != null && preventry.getValue().free;
		boolean nextfree = nextentry != null && nextentry.getValue().free;
		if (prevfree) {
			ContentRegion prevregion = preventry.getValue();
			if (nextfree) {
				//both prev and next are free, join them all
				ContentRegion nextregion = nextentry.getValue();
				increaseFreeRegionLengthLocked(prevregion, region.regionLength + nextregion.regionLength);
				regionsByOffsetMap.remove(nextregion.regionLength, nextregion);
				return;
			}
			//only the previous one is free
			//extend it and we're good
			increaseFreeRegionLengthLocked(prevregion, region.regionLength);
			return;
		}
		if (nextfree) {
			//only the next region is free, not the previous one
			ContentRegion nextregion = nextentry.getValue();
			freeRegionSizeToRegionMap.get(nextregion.regionLength).remove(nextregion);
			regionsByOffsetMap.remove(nextregion.regionLength, nextregion);
			region.regionLength += nextregion.regionLength;
			putFreeRegionLocked(region);
			return;
		}
		//none of the surrounding regions are free
		putFreeRegionLocked(region);
		regionsByOffsetMap.put(region.regionLength, region);
	}

	private void increaseFreeRegionLengthLocked(ContentRegion region, long length) {
		freeRegionSizeToRegionMap.get(region.regionLength).remove(region);
		region.regionLength += length;
		putFreeRegionLocked(region);
	}

	private void putFreeRegionLocked(ContentRegion region) {
		freeRegionSizeToRegionMap.computeIfAbsent(region.regionLength, key -> new TreeSet<>()).add(region);
	}

	private void fullyPersistedFile(ContentRegion entry, PersistingInputStream inputstream) {
		if (entry.clearPersistingStreamReference(inputstream)) {
			SakerPath path = inputstream.path;
			long size = inputstream.counter;
			synchronized (this) {
				fullyPersistedRegionLocked(entry, path, size);
			}
		}
		//else should not occurr
	}

	private void fullyPersistedRegionLocked(ContentRegion entry, SakerPath path, long size) {
		try {
			entry.free = false;
			entry.size = size;
			writeRegionHeaderLocked(entry, path);
			regions.put(path, entry);
			regionsByOffsetMap.put(entry.position, entry);
		} catch (IOException e) {
			e.printStackTrace();
			failedToPersistDataLocked(entry);
		}
	}

	private void failedToPersistData(ContentRegion entry) {
		//move the region back to the free pool
		synchronized (this) {
			failedToPersistDataLocked(entry);
		}
	}

	private void failedToPersistDataLocked(ContentRegion entry) {
		addFreeRegionLocked(entry);
	}

	private void persitingStreamClosed(ContentRegion entry, PersistingInputStream inputstream) {
		if (entry.clearPersistingStreamReference(inputstream)) {
			//the file wasnt fully read, so we treat this as a failure
			failedToPersistData(entry);
		}
	}

	@Override
	public void close() throws IOException {
		synchronized (this) {
			synchronized (fileAccessLock) {
				IOUtils.close(lock, writeFile);
			}
			regions.clear();
			regionsByOffsetMap.clear();
			freeRegionSizeToRegionMap.clear();
		}
	}

}
