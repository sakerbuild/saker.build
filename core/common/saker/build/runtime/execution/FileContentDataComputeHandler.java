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
package saker.build.runtime.execution;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;

import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.util.function.Functionals;

public class FileContentDataComputeHandler {
	private final ConcurrentNavigableMap<SakerPath, ConcurrentHashMap<FileDataComputer<?>, ComputedFileData>> fileComputedDatas = new ConcurrentSkipListMap<>();
	private final ConcurrentHashMap<ComputedFileDataKey, Object> fileComputeLocks = new ConcurrentHashMap<>();

	public FileContentDataComputeHandler() {
	}

	public <T> T computeFileContentData(SakerFile file, FileDataComputer<T> computer) throws IOException {
		Objects.requireNonNull(file, "file");
		Objects.requireNonNull(computer, "computer");
		SakerPath filepath = file.getSakerPath();
		if (filepath.isRelative()) {
			//file path is not absolute, we cannot cache the computed data. 
			//    just compute it
			T result = callCompute(file, computer);
			return result;
		}
		ConcurrentHashMap<FileDataComputer<?>, ComputedFileData> computermap = fileComputedDatas
				.computeIfAbsent(filepath, Functionals.concurrentHashMapComputer());
		ContentDescriptor contentdescriptor = file.getContentDescriptor();
		ComputedFileData presentcomputed = computermap.get(computer);
		if (presentcomputed != null) {
			@SuppressWarnings("unchecked")
			T got = (T) presentcomputed.getIfUpToDate(contentdescriptor, computer);
			if (got != null) {
				return got;
			}
		}
		synchronized (fileComputeLocks.computeIfAbsent(new ComputedFileDataKey(filepath, computer),
				Functionals.objectComputer())) {
			presentcomputed = computermap.get(computer);
			if (presentcomputed != null) {
				@SuppressWarnings("unchecked")
				T got = (T) presentcomputed.getIfUpToDate(contentdescriptor, computer);
				if (got != null) {
					return got;
				}
			}
			T result = callCompute(file, computer);
			computermap.put(computer, new ComputedFileData(contentdescriptor, computer, result));
			return result;
		}
	}

	private static <T> T callCompute(SakerFile file, FileDataComputer<T> computer) throws IOException {
		T result = computer.compute(file);
		if (result == null) {
			throw new NullPointerException("FileDataComputer computed null: " + computer);
		}
		return result;
	}

	public void invalidate(SakerPath path) {
		SakerPathFiles.getPathSubMapDirectoryChildren(fileComputedDatas, path, true).clear();
	}

	//TODO take the files themselves into account so datas which are changed can be removed
	public void cacheify() {
		for (ConcurrentHashMap<FileDataComputer<?>, ComputedFileData> datas : fileComputedDatas.values()) {
			for (Iterator<ComputedFileData> it = datas.values().iterator(); it.hasNext();) {
				ComputedFileData computed = it.next();
				if (!computed.cacheify()) {
					it.remove();
				}
			}
		}
	}

	private static class ComputedFileDataKey {
		private SakerPath path;
		private FileDataComputer<?> computer;

		public ComputedFileDataKey(SakerPath path, FileDataComputer<?> computer) {
			this.path = path;
			this.computer = computer;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((computer == null) ? 0 : computer.hashCode());
			result = prime * result + ((path == null) ? 0 : path.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ComputedFileDataKey other = (ComputedFileDataKey) obj;
			if (computer == null) {
				if (other.computer != null)
					return false;
			} else if (!computer.equals(other.computer))
				return false;
			if (path == null) {
				if (other.path != null)
					return false;
			} else if (!path.equals(other.path))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (path != null ? "path=" + path + ", " : "")
					+ (computer != null ? "computer=" + computer : "") + "]";
		}

	}

	private static class AutoStrongSoftReference extends SoftReference<Object> implements Supplier<Object> {
		private Object strongRef;

		public AutoStrongSoftReference(Object referent) {
			super(referent);
			this.strongRef = referent;
		}

		@Override
		public Object get() {
			Object result = super.get();
			this.strongRef = result;
			return result;
		}

		public void makeSoft() {
			this.strongRef = null;
		}

		public boolean isSoft() {
			return this.strongRef == null;
		}

		public Object getWithoutStrongify() {
			return super.get();
		}
	}

	private static class ComputedFileData {
		private ContentDescriptor contentDescriptor;
		private FileDataComputer<?> computer;
		private AutoStrongSoftReference computedData;

		public <T> ComputedFileData(ContentDescriptor contentDescriptor, FileDataComputer<T> computer, T computedData) {
			this.contentDescriptor = contentDescriptor;
			this.computer = computer;
			this.computedData = new AutoStrongSoftReference(computedData);
		}

		protected Object getIfUpToDate(ContentDescriptor contentdescriptor, FileDataComputer<?> computer) {
			if (computer.equals(this.computer) && Objects.equals(this.contentDescriptor, contentdescriptor)) {
				return computedData.get();
			}
			return null;
		}

		//returns true if it holds any data
		protected boolean cacheify() {
			AutoStrongSoftReference ref = computedData;
			if (ref.isSoft()) {
				Object got = ref.getWithoutStrongify();
				if (got == null) {
					return false;
				}
				return true;
			}
			ref.makeSoft();
			return true;
		}
	}
}
