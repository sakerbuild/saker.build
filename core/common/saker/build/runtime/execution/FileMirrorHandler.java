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
import java.nio.file.Path;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

import saker.build.exception.FileMirroringUnavailableException;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.SynchronizingContentUpdater;
import saker.build.file.content.ContentDatabase;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.InternalExecutionContext.FilePathContents;
import saker.build.runtime.params.ExecutionPathConfiguration;

public class FileMirrorHandler {
	//Design note: some of the methods pass the mirror directory path as an argument. It is not necessary but is to make sure that the nullability of it is checked before the call.

	private static final Pattern PATTERN_ROOT_CHARS_REPLACER = Pattern.compile("[://\\\\]");

	private final LocalFileProvider localFiles = LocalFileProvider.getInstance();
	private final Path mirrorDirectoryPath;
	private final ExecutionPathConfiguration pathConfiguration;
	private final NavigableMap<String, String> realRootsToNormalizedRootsMap = new TreeMap<>();
	private final NavigableMap<String, String> normalizedRootsToRealRootsMap = new TreeMap<>();

	private final ContentDatabase contentDatabase;
	private final ExecutionContext executionContext;

	public FileMirrorHandler(Path mirrorDirectoryPath, ExecutionPathConfiguration pathconfig,
			ContentDatabase contentdatabase, ExecutionContext executioncontext) {
		Objects.requireNonNull(pathconfig, "pathConfiguration");
		Objects.requireNonNull(contentdatabase, "contentdatabase");

		this.contentDatabase = contentdatabase;
		this.mirrorDirectoryPath = mirrorDirectoryPath;
		this.pathConfiguration = pathconfig;
		this.executionContext = executioncontext;
		for (String rootname : pathconfig.getRootNames()) {
			String normalized = normalizeRootName(rootname);
			realRootsToNormalizedRootsMap.put(rootname, normalized);
			normalizedRootsToRealRootsMap.put(normalized, rootname);
		}
	}

	public static String normalizeRootName(String rootname) {
		return PATTERN_ROOT_CHARS_REPLACER.matcher(rootname).replaceAll("_");
	}

	public Path getMirrorDirectory() {
		return mirrorDirectoryPath;
	}

	public Path mirror(SakerFile file) throws IOException {
		return mirror(file, DirectoryVisitPredicate.everything());
	}

	public Path mirror(SakerFile file, DirectoryVisitPredicate synchpredicate) throws IOException {
		FilePathContents filepathcontents = ((InternalExecutionContext) executionContext)
				.internalGetFilePathContents(file);
		SakerPath filepath = SakerPathFiles.requireAbsolutePath(filepathcontents.getPath());
		return mirror(filepath, file, synchpredicate, filepathcontents.getContents());
	}

	public Path mirror(SakerPath filepath, SakerFile file, DirectoryVisitPredicate synchpredicate,
			ContentDescriptor filecontents) throws IOException {
		Path localfilepath = pathConfiguration.toLocalPath(filepath);
		if (localfilepath == null) {
			return executeMirroring(filepath, file, synchpredicate, requireMirrorPath(), filecontents);
		}
		ProviderHolderPathKey localfilepathkey = localFiles.getPathKey(localfilepath);
		synchronizeToPath(file, synchpredicate, localfilepathkey, filecontents);
		return localfilepath;
	}

	public Path toMirrorPath(SakerPath path) throws FileMirroringUnavailableException {
		SakerPathFiles.requireAbsolutePath(path);
		Path ppath = pathConfiguration.toLocalPath(path);
		if (ppath != null) {
			return ppath;
		}
		return getMirrorPathFor(path, requireMirrorPath());
	}

	public SakerPath toUnmirrorPath(Path path) {
		SakerPathFiles.requireAbsolutePath(path);
		return mirroredPathToSakerPath(path);
	}

	private Path requireMirrorPath() throws FileMirroringUnavailableException {
		Path path = mirrorDirectoryPath;
		if (path == null) {
			throw new FileMirroringUnavailableException("Mirror directory not specified.");
		}
		return path;
	}

	private Path executeMirroring(SakerPath filesakerpath, SakerFile file, DirectoryVisitPredicate synchpredicate,
			Path mirrorDirectoryPath, ContentDescriptor filecontents) throws IOException {
		Path mirrorpath = getMirrorPathFor(filesakerpath, mirrorDirectoryPath);
		LocalFileProvider localfiles = LocalFileProvider.getInstance();
		ProviderHolderPathKey mirrorpathkey = localfiles.getPathKey(mirrorpath);
		synchronizeToPath(file, synchpredicate, mirrorpathkey, filecontents);
		return mirrorpath;
	}

	private Path getMirrorPathFor(SakerPath filepath, Path mirrorDirectoryPath) throws IllegalArgumentException {
		String rootstr = realRootsToNormalizedRootsMap.get(filepath.getRoot());
		if (rootstr == null) {
			throw new InvalidPathFormatException(
					"Unknown root directory: " + filepath + " available: " + realRootsToNormalizedRootsMap.keySet());
		}
		Path mirrorpath = mirrorDirectoryPath.resolve(rootstr);
		if (filepath.getNameCount() > 0) {
			mirrorpath = mirrorpath.resolve(filepath.toStringFromRoot());
		}
		return mirrorpath;
	}

	private SakerPath mirroredPathToSakerPath(Path path) {
		SakerPath execpath = pathConfiguration.toExecutionPath(path);
		if (execpath != null) {
			return execpath;
		}
		if (mirrorDirectoryPath == null) {
			return null;
		}
		if (!path.startsWith(mirrorDirectoryPath)) {
			return null;
		}
		SakerPath relativized = SakerPath.valueOf(mirrorDirectoryPath.relativize(path));
		String normalizedroot = relativized.getName(0);
		String realroot = normalizedRootsToRealRootsMap.get(normalizedroot);
		if (realroot == null) {
			return null;
		}
		return relativized.subPath(realroot, 1);
	}

	private void synchronizeToPath(SakerFile file, DirectoryVisitPredicate synchpredicate,
			ProviderHolderPathKey pathkey, ContentDescriptor filecontents) throws IOException {
		if (file instanceof SakerDirectory) {
			SakerDirectory dir = (SakerDirectory) file;
			SakerPathFiles.synchronizeDirectory(dir, pathkey, synchpredicate, contentDatabase);
		} else {
			contentDatabase.synchronize(pathkey, filecontents, new SynchronizingContentUpdater(file, pathkey));
		}
	}
}
