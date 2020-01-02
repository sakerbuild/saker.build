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
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import saker.build.file.content.ContentDatabase;
import saker.build.file.content.ContentDatabase.ContentHandleAttributes;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;

public class ProviderPathSakerDirectory extends SakerDirectoryBase {
	private final ContentDatabase contentDatabase;
	private final SakerFileProvider fileProvider;
	private final SakerPath realPath;

	public ProviderPathSakerDirectory(ContentDatabase db, SakerFileProvider fileProvider, SakerPath realPath) {
		this(db, realPath.getFileName(), fileProvider, realPath);
	}

	public ProviderPathSakerDirectory(ContentDatabase db, String name, SakerFileProvider fileProvider,
			SakerPath realPath) {
		super(name);
		this.contentDatabase = db;
		this.fileProvider = fileProvider;
		this.realPath = realPath;
	}

	/* default */ ProviderPathSakerDirectory(ContentDatabase db, String name, SakerFileProvider fileProvider,
			SakerPath realPath, Void placeholder) {
		super(name, placeholder);
		this.contentDatabase = db;
		this.fileProvider = fileProvider;
		this.realPath = realPath;
	}

	public static SakerDirectory createRoot(ContentDatabase db, SakerFileProvider fileProvider, SakerPath path) {
		String root = path.getRoot();
		if (root == null || path.getNameCount() > 0) {
			throw new IllegalArgumentException("Given path is not a root path: " + path);
		}
		return new RootProviderPathSakerDirectory(db, root, fileProvider, path);
	}

	@Override
	ContentDatabase getContentDatabase() {
		return contentDatabase;
	}

	public SakerFileProvider getFileProvider() {
		return fileProvider;
	}

	public SakerPath getRealSakerPath() {
		return realPath;
	}

	public boolean isAnyPopulated() {
		return populatedState != POPULATED_STATE_UNPOPULATED;
	}

	public void clearPopulated() {
		this.populatedState = POPULATED_STATE_UNPOPULATED;
	}

	public ConcurrentNavigableMap<String, SakerFileBase> getTrackedFilesMap() {
		return super.getTrackedFiles();
	}

	public synchronized NavigableMap<String, ? extends BasicFileAttributes> repair() {
		ConcurrentNavigableMap<String, SakerFileBase> thistrackedfiles = getTrackedFiles();

		ConcurrentSkipListMap<String, SakerFileBase> trackedfiles = new ConcurrentSkipListMap<>();
		NavigableMap<String, ? extends FileEntry> entriesbyname;
		try {
			entriesbyname = fileProvider.getDirectoryEntries(realPath);
			for (Entry<String, ? extends FileEntry> entry : entriesbyname.entrySet()) {
				String fname = entry.getKey();
				FileEntry attrs = entry.getValue();
				SakerPath filepath = realPath.resolve(fname);
				if (attrs.isDirectory()) {
					ProviderPathSakerDirectory nfile = new ProviderPathSakerDirectory(contentDatabase, fname,
							fileProvider, filepath);
					SakerFileBase presentfile = thistrackedfiles.get(fname);
					if (presentfile != null) {
						if (presentfile.getClass() == ProviderPathSakerDirectory.class) {
							ProviderPathSakerDirectory prevmpd = (ProviderPathSakerDirectory) presentfile;
							nfile.trackedFiles = prevmpd.trackedFiles;
							nfile.populatedState = prevmpd.populatedState;
						}
					}
					SakerFileBase.internal_setParent(nfile, this);
					trackedfiles.put(fname, nfile);
				} else {
					ProviderPathSakerFile nfile = new ProviderPathSakerFile(fileProvider, filepath,
							contentDatabase.getContentHandle(SakerPathFiles.getPathKey(fileProvider, filepath)));
					SakerFileBase.internal_setParent(nfile, this);
					trackedfiles.put(fname, nfile);
				}
			}
			this.trackedFiles = trackedfiles;
			return entriesbyname;
		} catch (IOException e) {
			e.printStackTrace();
			this.trackedFiles = trackedfiles;
			return null;
		}
	}

	@Override
	protected Map<String, SakerFileBase> populateImpl() {
		try {
			NavigableMap<String, ContentHandleAttributes> direntries = contentDatabase
					.discoverDirectoryChildrenAttributes(SakerPathFiles.getPathKey(fileProvider, realPath));
			if (direntries.isEmpty()) {
				return Collections.emptyNavigableMap();
			}
			NavigableMap<String, SakerFileBase> result = new TreeMap<>();
			for (Entry<String, ContentHandleAttributes> entry : direntries.entrySet()) {
				String entryname = entry.getKey();
				ContentHandleAttributes discoveryhandle = entry.getValue();
				FileEntry attrs = discoveryhandle.getAttributes();
				SakerFileBase file;
				SakerPath entryrealpath = realPath.resolve(entryname);
				if (attrs.isDirectory()) {
					file = new ProviderPathSakerDirectory(contentDatabase, entryname, fileProvider, entryrealpath);
				} else {
					file = new ProviderPathSakerFile(fileProvider, entryrealpath, discoveryhandle.getContentHandle());
				}
				result.put(entryname, file);
			}
			return result;
		} catch (NoSuchFileException ignored) {
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Collections.emptyNavigableMap();
	}

	@Override
	protected SakerFileBase populateSingleImpl(String name) {
		SakerFileBase result = null;
		SakerPath path = realPath.resolve(name);
		try {
			ContentHandleAttributes discoverhandle = contentDatabase
					.discoverFileAttributes(SakerPathFiles.getPathKey(fileProvider, path));
			FileEntry attrs = discoverhandle.getAttributes();
			if (attrs.isDirectory()) {
				result = new ProviderPathSakerDirectory(contentDatabase, name, fileProvider, path);
			} else {
				result = new ProviderPathSakerFile(fileProvider, path, discoverhandle.getContentHandle());
			}
			return result;
		} catch (IOException | InvalidPathException e) {
			return null;
		}
	}

	public final void addPopulatedDirectory(String filename) {
		SakerPath realpath = this.realPath.resolve(filename);
		ProviderPathSakerDirectory file = new ProviderPathSakerDirectory(this.contentDatabase, fileProvider, realpath);
		SakerFileBase.internal_setParent(file, this);
		getTrackedFiles().put(file.getName(), file);
	}

	public final void addPopulatedFile(String filename) {
		SakerPath realpath = this.realPath.resolve(filename);
		ProviderPathSakerFile file = new ProviderPathSakerFile(fileProvider, realpath,
				contentDatabase.getContentHandle(SakerPathFiles.getPathKey(fileProvider, realpath)));
		SakerFileBase.internal_setParent(file, this);
		getTrackedFiles().put(file.getName(), file);
	}
}
