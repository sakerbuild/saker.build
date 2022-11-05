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

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.NoSuchAlgorithmException;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEventListener.ListenerToken;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;

public class ForwardingSakerFileProvider implements SakerFileProvider {
	protected SakerFileProvider subject;

	public ForwardingSakerFileProvider(SakerFileProvider subject) {
		this.subject = subject;
	}

	@Override
	public NavigableSet<String> getRoots() throws IOException {
		return subject.getRoots();
	}

	@Override
	public FileProviderKey getProviderKey() {
		return subject.getProviderKey();
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
	public FileEntry getFileAttributes(SakerPath path, LinkOption... linkoptions) throws IOException {
		return subject.getFileAttributes(path, linkoptions);
	}

	@Override
	public void setLastModifiedMillis(SakerPath path, long millis) throws IOException {
		subject.setLastModifiedMillis(path, millis);
	}

	@Override
	public void createDirectories(SakerPath path) throws IOException, FileAlreadyExistsException {
		subject.createDirectories(path);
	}

	@Override
	public void deleteRecursively(SakerPath path) throws IOException {
		subject.deleteRecursively(path);
	}

	@Override
	public void delete(SakerPath path) throws IOException, DirectoryNotEmptyException {
		subject.delete(path);
	}

	@Override
	public ByteSource openInput(SakerPath path, OpenOption... openoptions) throws IOException {
		return subject.openInput(path, openoptions);
	}

	@Override
	public ByteSink openOutput(SakerPath path, OpenOption... openoptions) throws IOException {
		return subject.openOutput(path, openoptions);
	}

	@Override
	public int ensureWriteRequest(SakerPath path, int filetype, int operationflag)
			throws IOException, IllegalArgumentException {
		return subject.ensureWriteRequest(path, filetype, operationflag);
	}

	@Override
	public void moveFile(SakerPath source, SakerPath target, CopyOption... copyoptions) throws IOException {
		subject.moveFile(source, target, copyoptions);
	}

	@Override
	public ListenerToken addFileEventListener(SakerPath directory, FileEventListener listener) throws IOException {
		return subject.addFileEventListener(directory, listener);
	}

	@Override
	public Entry<String, ? extends FileEntry> getDirectoryEntryIfSingle(SakerPath path) throws IOException {
		return subject.getDirectoryEntryIfSingle(path);
	}

	@Override
	public boolean setPosixFilePermissions(SakerPath path, Set<PosixFilePermission> permissions)
			throws NullPointerException, IOException {
		return subject.setPosixFilePermissions(path, permissions);
	}

	@Override
	public boolean modifyPosixFilePermissions(SakerPath path, Set<PosixFilePermission> addpermissions,
			Set<PosixFilePermission> removepermissions) throws NullPointerException, IOException {
		return subject.modifyPosixFilePermissions(path, addpermissions, removepermissions);
	}

	@Override
	public Set<PosixFilePermission> getPosixFilePermissions(SakerPath path) throws NullPointerException, IOException {
		return subject.getPosixFilePermissions(path);
	}

	@Override
	public void clearDirectoryRecursively(SakerPath path) throws IOException {
		subject.clearDirectoryRecursively(path);
	}

	@Override
	public boolean isChanged(SakerPath path, long size, long modificationmillis, LinkOption... linkoptions) {
		return subject.isChanged(path, size, modificationmillis, linkoptions);
	}

	@Override
	public NavigableSet<String> deleteChildrenRecursivelyIfNotIn(SakerPath path, Set<String> childfilenames)
			throws IOException, PartiallyDeletedChildrenException {
		return subject.deleteChildrenRecursivelyIfNotIn(path, childfilenames);
	}

	@Override
	public int deleteRecursivelyIfNotFileType(SakerPath path, int filetype) throws IOException {
		return subject.deleteRecursivelyIfNotFileType(path, filetype);
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
		return subject.getAllBytes(path, openoptions);
	}

	@Override
	public void setFileBytes(SakerPath path, ByteArrayRegion data, OpenOption... openoptions) throws IOException {
		subject.setFileBytes(path, data, openoptions);
	}

	@Override
	public long writeToFile(ByteSource is, SakerPath path, OpenOption... openoptions) throws IOException {
		return subject.writeToFile(is, path, openoptions);
	}

	@Override
	public ByteSink ensureWriteOpenOutput(SakerPath path, int operationflag, OpenOption... openoptions)
			throws IOException, NullPointerException {
		return subject.ensureWriteOpenOutput(path, operationflag, openoptions);
	}

	@Override
	public FileHashResult hash(SakerPath path, String algorithm, OpenOption... openoptions)
			throws NoSuchAlgorithmException, IOException {
		return subject.hash(path, algorithm, openoptions);
	}

	@Override
	public void removeFileEventListeners(Iterable<? extends ListenerToken> listeners) throws IllegalArgumentException {
		subject.removeFileEventListeners(listeners);
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
	public SakerPath resolveWrappedPath(SakerPath path) throws UnsupportedOperationException, IllegalArgumentException {
		return subject.resolveWrappedPath(path);
	}

}
