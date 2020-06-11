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
package saker.build.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.PriorityMultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.StreamUtils;

public abstract class SakerPathFileBase extends SakerFileBase {
	protected final SakerFileProvider fileProvider;
	protected final SakerPath realPath;

	public SakerPathFileBase(String name, SakerFileProvider fileProvider, SakerPath realPath) {
		super(name);
		this.fileProvider = fileProvider;
		this.realPath = realPath;
	}

	public SakerPathFileBase(String name, ProviderHolderPathKey pathkey) {
		this(name, pathkey.getFileProvider(), pathkey.getPath());
	}

	public SakerPath getRealPath() {
		return realPath;
	}

	@Override
	public void writeToStreamImpl(OutputStream os) throws IOException {
		fileProvider.writeTo(realPath, ByteSink.valueOf(os));
	}

	@Override
	public ByteArrayRegion getBytesImpl() throws IOException {
		return fileProvider.getAllBytes(realPath);
	}

	@Override
	public String getContentImpl() throws IOException {
		return getBytesImpl().toString();
	}

	@Override
	public InputStream openInputStreamImpl() throws IOException {
		return ByteSource.toInputStream(openByteSourceImpl());
	}

	@Override
	public ByteSource openByteSourceImpl() throws IOException {
		return fileProvider.openInput(realPath);
	}

	@Override
	public void synchronizeImpl(ProviderHolderPathKey pathkey) throws IOException {
		if (SakerPathFiles.isSamePaths(fileProvider, realPath, pathkey)) {
			//no synchronization required as the file is already at the path
			return;
		}
		SakerFileProvider targetfp = pathkey.getFileProvider();
		SakerPath targetpath = pathkey.getPath();
		Set<PosixFilePermission> posixpermissions = getPosixFilePermissions();
		targetfp.ensureWriteRequest(targetpath, FileEntry.TYPE_FILE,
				SakerFileProvider.OPERATION_FLAG_DELETE_INTERMEDIATE_FILES);
		//XXX try direct copy if the file providers are the same
		try (ByteSink out = targetfp.openOutput(targetpath)) {
			fileProvider.writeTo(realPath, out);
		}
		if (posixpermissions != null) {
			//TODO set this in a single call when writing or opening the contents
			targetfp.setPosixFilePermissions(targetpath, posixpermissions);
		}
	}

	@Override
	public boolean synchronizeImpl(ProviderHolderPathKey pathkey, ByteSink additionalwritestream)
			throws SecondaryStreamException, IOException {
		if (SakerPathFiles.isSamePaths(fileProvider, realPath, pathkey)) {
			//no synchronization required as the file is already at the path
			return false;
		}
		Objects.requireNonNull(additionalwritestream, "stream");

		SakerFileProvider targetfp = pathkey.getFileProvider();
		SakerPath targetpath = pathkey.getPath();
		Set<PosixFilePermission> posixpermissions = getPosixFilePermissions();
		targetfp.ensureWriteRequest(targetpath, FileEntry.TYPE_FILE,
				SakerFileProvider.OPERATION_FLAG_DELETE_INTERMEDIATE_FILES);

		try (ByteSink fpoutput = targetfp.openOutput(targetpath);
				PriorityMultiplexOutputStream os = new PriorityMultiplexOutputStream(ByteSink.toOutputStream(fpoutput),
						StreamUtils.closeProtectedOutputStream(ByteSink.toOutputStream(additionalwritestream)))) {
			fileProvider.writeTo(realPath, os);
			IOException sec = os.getSecondaryException();
			if (sec != null) {
				throw new SecondaryStreamException(sec);
			}
		}
		if (posixpermissions != null) {
			//TODO set this in a single call when writing or opening the contents
			targetfp.setPosixFilePermissions(targetpath, posixpermissions);
		}
		return true;
	}

}
