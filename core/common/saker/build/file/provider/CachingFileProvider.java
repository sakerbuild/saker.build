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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;

/**
 * File provider implementation that caches the file contents to a local file.
 * 
 * @deprecated Currently not used, but may be in the future.
 */
@Deprecated
public class CachingFileProvider implements SakerFileProvider {
	//TODO validate this class implementation, with open options, and copy options

	private RemoteCacheFile cacheFile;
	private SakerFileProvider subject;
	private RootFileProviderKey subjectKey;

	public CachingFileProvider(RemoteCacheFile cacheFile, SakerFileProvider subject, RootFileProviderKey subjectKey) {
		this.cacheFile = cacheFile;
		this.subject = subject;
		this.subjectKey = subjectKey;
	}

	@Override
	public NavigableSet<String> getRoots() throws IOException {
		return subject.getRoots();
	}

	@Override
	public NavigableMap<String, ? extends FileEntry> getDirectoryEntries(SakerPath path) throws IOException {
		return subject.getDirectoryEntries(path);
	}

	@Override
	public NavigableMap<SakerPath, ? extends FileEntry> getDirectoryEntriesRecursively(SakerPath path)
			throws IOException {
		return subject.getDirectoryEntriesRecursively(path);
	}

	@Override
	public FileEntry getFileAttributes(SakerPath path, LinkOption... linkoptions)
			throws IOException, FileNotFoundException {
		//TODO invalidate the cache if the attributes don't match?
		return subject.getFileAttributes(path, linkoptions);
	}

	@Override
	public void setLastModifiedMillis(SakerPath path, long millis) throws IOException {
		//TODO update the cache too if succeeded
		cacheFile.invalidate(path);
		subject.setLastModifiedMillis(path, millis);
	}

	@Override
	public void createDirectories(SakerPath path) throws IOException {
		subject.createDirectories(path);
	}

	@Override
	public void deleteRecursively(SakerPath path) throws IOException {
		subject.deleteRecursively(path);
		cacheFile.invalidate(path);
	}

	@Override
	public void delete(SakerPath path) throws IOException, DirectoryNotEmptyException {
		subject.delete(path);
		cacheFile.invalidate(path);
	}

	@Override
	public ByteSource openInput(SakerPath path, OpenOption... openoptions) throws IOException, FileNotFoundException {
		//TODO use cache
		return subject.openInput(path, openoptions);
//		return cacheFile.openInput(path, openoptions);
	}

	@Override
	public ByteSink openOutput(SakerPath path, OpenOption... openoptions) throws IOException {
		ByteSink result = subject.openOutput(path, openoptions);
		cacheFile.invalidate(path);
		return result;
	}

	@Override
	public int ensureWriteRequest(SakerPath path, int filetype, int opflag) throws IOException {
		return subject.ensureWriteRequest(path, filetype, opflag);
	}

	@Override
	public void clearDirectoryRecursively(SakerPath path) throws IOException {
		subject.clearDirectoryRecursively(path);
		cacheFile.invalidateEverythingInDirectory(path);
	}

	@Override
	public boolean isChanged(SakerPath path, long size, long modificationmillis, LinkOption... linkoptions) {
		return subject.isChanged(path, size, modificationmillis, linkoptions);
	}

	@Override
	public NavigableSet<String> deleteChildrenRecursivelyIfNotIn(SakerPath path, Set<String> childfilenames) throws IOException {
		NavigableSet<String> result = subject.deleteChildrenRecursivelyIfNotIn(path, childfilenames);
		cacheFile.invalidateFilesInDirectory(path, result);
		return result;
	}

	@Override
	public int deleteRecursivelyIfNotFileType(SakerPath path, int filetype) throws IOException {
		int result = subject.deleteRecursivelyIfNotFileType(path, filetype);
		if (result == FileEntry.TYPE_NULL) {
			cacheFile.invalidate(path);
		}
		return result;
	}

	@Override
	public NavigableSet<String> getDirectoryEntryNames(SakerPath path) throws IOException {
		return subject.getDirectoryEntryNames(path);
	}

	@Override
	public NavigableSet<String> getSubDirectoryNames(SakerPath path) throws IOException {
		return subject.getSubDirectoryNames(path);
	}

	@Override
	public long writeTo(SakerPath path, ByteSink out, OpenOption... openoptions) throws IOException {
		return subject.writeTo(path, out, openoptions);
	}

	@Override
	public ByteArrayRegion getAllBytes(SakerPath path, OpenOption... openoptions) throws IOException {
		//TODO use cache
		return subject.getAllBytes(path, openoptions);
//		return cacheFile.getAllBytes(path, openoptions);
	}

	@Override
	public void moveFile(SakerPath source, SakerPath target, CopyOption... copyoptions) throws IOException {
		cacheFile.invalidate(source);
		cacheFile.invalidate(target);
		subject.moveFile(source, target, copyoptions);
	}

	@Override
	public void setFileBytes(SakerPath path, ByteArrayRegion data, OpenOption... openoptions) throws IOException {
		subject.setFileBytes(path, data, openoptions);
		cacheFile.invalidate(path);
	}

	@Override
	public long writeToFile(ByteSource is, SakerPath path, OpenOption... openoptions) throws IOException {
		long result = subject.writeToFile(is, path, openoptions);
		cacheFile.invalidate(path);
		return result;
	}

	@Override
	public FileEventListener.ListenerToken addFileEventListener(SakerPath directory, FileEventListener listener)
			throws IOException {
		//TODO use a proxy listener, to listen for changes and invalidate the cache appropriately
		return subject.addFileEventListener(directory, listener);
	}

	@Override
	public void removeFileEventListeners(Iterable<? extends FileEventListener.ListenerToken> listeners) {
		subject.removeFileEventListeners(listeners);
	}

	@Override
	public FileProviderKey getProviderKey() {
		return subjectKey;
	}

	@Override
	public SakerFileLock createLockFile(SakerPath path) throws IOException {
		return subject.createLockFile(path);
	}

	@Override
	public SakerFileProvider getWrappedProvider() {
		return subject.getWrappedProvider();
	}

	@Override
	public SakerPath resolveWrappedPath(SakerPath path) {
		return subject.resolveWrappedPath(path);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + subjectKey + "]";
	}

}
