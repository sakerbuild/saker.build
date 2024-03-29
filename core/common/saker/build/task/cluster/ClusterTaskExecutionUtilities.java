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
package saker.build.task.cluster;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;

import saker.build.exception.InvalidFileTypeException;
import saker.build.exception.InvalidPathFormatException;
import saker.build.exception.TaskIdentifierExceptionView;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDatabase;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.task.InternalTaskContext;
import saker.build.task.InternalTaskContext.PathSakerFileContents;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionManager;
import saker.build.task.TaskExecutionParameters;
import saker.build.task.TaskExecutionUtilities;
import saker.build.task.TaskFactory;
import saker.build.task.TaskFuture;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.exception.TaskIdentifierConflictException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.IOUtils;

class ClusterTaskExecutionUtilities implements TaskExecutionUtilities {
	private final TaskExecutionUtilities utils;
	private final ClusterTaskContext clusterTaskContext;

	public ClusterTaskExecutionUtilities(TaskExecutionUtilities utils, ClusterTaskContext clusterTaskContext) {
		this.utils = utils;
		this.clusterTaskContext = clusterTaskContext;
	}

	@Override
	public void reportInputFileDependency(Object tag, SakerFile file) {
		this.utils.reportInputFileDependency(tag, file);
	}

	@Override
	public void reportInputFileDependency(Object tag, Iterable<? extends SakerFile> files) {
		this.utils.reportInputFileDependency(tag, files);
	}

	@Override
	public void reportInputFileDependency(Object tag,
			NavigableMap<SakerPath, ? extends ContentDescriptor> pathcontents) {
		this.utils.reportInputFileDependency(tag, pathcontents);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFiles(FileCollectionStrategy fileadditiondependency) {
		return this.utils.collectFiles(fileadditiondependency);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFilesReportInputFileAndAdditionDependency(Object tag,
			FileCollectionStrategy fileadditiondependency) throws NullPointerException {
		return this.utils.collectFilesReportInputFileAndAdditionDependency(tag, fileadditiondependency);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFilesReportInputFileAndAdditionDependency(Object tag,
			Iterable<? extends FileCollectionStrategy> fileadditiondependencies) throws NullPointerException {
		return this.utils.collectFilesReportInputFileAndAdditionDependency(tag, fileadditiondependencies);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFilesReportAdditionDependency(Object tag,
			FileCollectionStrategy fileadditiondependency) throws NullPointerException {
		return this.utils.collectFilesReportAdditionDependency(tag, fileadditiondependency);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFilesReportAdditionDependency(Object tag,
			Iterable<? extends FileCollectionStrategy> fileadditiondependencies) throws NullPointerException {
		return this.utils.collectFilesReportAdditionDependency(tag, fileadditiondependencies);
	}

	@Override
	public void reportOutputFileDependency(Object tag, SakerFile file) {
		this.utils.reportOutputFileDependency(tag, file);
	}

	@Override
	public void reportOutputFileDependency(Object tag, Iterable<? extends SakerFile> files) {
		this.utils.reportOutputFileDependency(tag, files);
	}

	@Override
	public void reportOutputFileDependency(Object tag,
			NavigableMap<SakerPath, ? extends ContentDescriptor> pathcontents) {
		this.utils.reportOutputFileDependency(tag, pathcontents);
	}

	@Override
	public <T> T getReportEnvironmentDependency(EnvironmentProperty<T> property) {
		Objects.requireNonNull(property, "property");
		T val = clusterTaskContext.getExecutionContext().getEnvironment().getEnvironmentPropertyCurrentValue(property);
		clusterTaskContext.reportEnvironmentDependency(property, val);
		return val;
	}

	@Override
	public <T> T getReportExecutionDependency(ExecutionProperty<T> property) throws NullPointerException {
		return this.utils.getReportExecutionDependency(property);
	}

	@Override
	public <R> TaskFuture<R> runTaskFuture(TaskIdentifier taskid, TaskFactory<R> taskfactory,
			TaskExecutionParameters parameters) throws TaskIdentifierConflictException {
		TaskExecutionManager.requireCalledOnTaskThread(clusterTaskContext, false);
		TaskFuture<R> future = ((InternalTaskContext) clusterTaskContext.realTaskContext)
				.internalRunTaskFutureOnTaskThread(taskid, taskfactory, parameters);
		return new ClusterTaskFuture<>(taskid, future, clusterTaskContext);
	}

	@Override
	public <R> R runTaskResult(TaskIdentifier taskid, TaskFactory<R> taskfactory, TaskExecutionParameters parameters)
			throws TaskIdentifierConflictException {
		TaskExecutionManager.requireCalledOnTaskThread(clusterTaskContext, false);
		return ((InternalTaskContext) clusterTaskContext.realTaskContext).internalRunTaskResultOnTaskThread(taskid,
				taskfactory, parameters);
	}

	@Override
	public TaskContext getTaskContext() {
		return clusterTaskContext;
	}

	@Override
	public SakerFile resolveAtPath(SakerPath path) {
		return this.utils.resolveAtPath(path);
	}

	@Override
	public SakerFile resolveFileAtPath(SakerPath path) throws NullPointerException {
		return this.utils.resolveFileAtPath(path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtPath(SakerPath path) {
		return this.utils.resolveDirectoryAtPath(path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtPathCreate(SakerPath path) {
		return this.utils.resolveDirectoryAtPathCreate(path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtPathCreateIfAbsent(SakerPath path) {
		return this.utils.resolveDirectoryAtPathCreateIfAbsent(path);
	}

	@Override
	public SakerFile resolveAtPath(SakerDirectory basedir, SakerPath path) throws NullPointerException {
		return this.utils.resolveAtPath(basedir, path);
	}

	@Override
	public SakerFile resolveFileAtPath(SakerDirectory basedir, SakerPath path) throws NullPointerException {
		return this.utils.resolveFileAtPath(basedir, path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtPath(SakerDirectory basedir, SakerPath path) throws NullPointerException {
		return this.utils.resolveDirectoryAtPath(basedir, path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtPathCreate(SakerDirectory basedir, SakerPath path)
			throws NullPointerException {
		return this.utils.resolveDirectoryAtPathCreate(basedir, path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtPathCreateIfAbsent(SakerDirectory basedir, SakerPath path)
			throws NullPointerException {
		return this.utils.resolveDirectoryAtPathCreateIfAbsent(basedir, path);
	}

	@Override
	public SakerFile resolveAtRelativePath(SakerDirectory basedir, SakerPath path) {
		return this.utils.resolveAtRelativePath(basedir, path);
	}

	@Override
	public SakerFile resolveFileAtRelativePath(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return this.utils.resolveFileAtRelativePath(basedir, path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtRelativePath(SakerDirectory basedir, SakerPath path) {
		return this.utils.resolveDirectoryAtRelativePath(basedir, path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtRelativePathCreate(SakerDirectory basedir, SakerPath path) {
		return this.utils.resolveDirectoryAtRelativePathCreate(basedir, path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtRelativePathCreateIfAbsent(SakerDirectory basedir, SakerPath path) {
		return this.utils.resolveDirectoryAtRelativePathCreateIfAbsent(basedir, path);
	}

	@Override
	public SakerFile resolveAtAbsolutePath(SakerPath path) throws NullPointerException, InvalidPathFormatException {
		return this.utils.resolveAtAbsolutePath(path);
	}

	@Override
	public SakerFile resolveFileAtAbsolutePath(SakerPath path) throws NullPointerException, InvalidPathFormatException {
		return this.utils.resolveFileAtAbsolutePath(path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtAbsolutePath(SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return this.utils.resolveDirectoryAtAbsolutePath(path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtAbsolutePathCreate(SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return this.utils.resolveDirectoryAtAbsolutePathCreate(path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtAbsolutePathCreateIfAbsent(SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return this.utils.resolveDirectoryAtAbsolutePathCreateIfAbsent(path);
	}

	@Override
	public SakerFile resolveAtRelativePathNames(SakerDirectory basedir, Iterable<? extends String> names) {
		return this.utils.resolveAtRelativePathNames(basedir, names);
	}

	@Override
	public SakerFile resolveFileAtRelativePathNames(SakerDirectory basedir, Iterable<? extends String> names)
			throws NullPointerException, InvalidPathFormatException {
		return this.utils.resolveFileAtRelativePathNames(basedir, names);
	}

	@Override
	public SakerDirectory resolveDirectoryAtRelativePathNames(SakerDirectory basedir,
			Iterable<? extends String> names) {
		return this.utils.resolveDirectoryAtRelativePathNames(basedir, names);
	}

	@Override
	public SakerDirectory resolveDirectoryAtRelativePathNamesCreate(SakerDirectory basedir,
			Iterable<? extends String> names) {
		return this.utils.resolveDirectoryAtRelativePathNamesCreate(basedir, names);
	}

	@Override
	public SakerDirectory resolveDirectoryAtRelativePathNamesCreateIfAbsent(SakerDirectory basedir,
			Iterable<? extends String> names) {
		return this.utils.resolveDirectoryAtRelativePathNamesCreateIfAbsent(basedir, names);
	}

	@Override
	public SakerFile addFile(SakerDirectory directory, SakerFile file) {
		return this.utils.addFile(directory, file);
	}

	@Override
	public SakerFile addFileIfAbsent(SakerDirectory directory, SakerFile file) throws NullPointerException {
		return this.utils.addFileIfAbsent(directory, file);
	}

	//TODO the file content resolution methods shouldn't take the efficient opening options into account

	@Override
	public void writeTo(SakerFile file, OutputStream os) throws IOException {
		Objects.requireNonNull(file, "file");
		Objects.requireNonNull(os, "output");
		ContentDatabase clusterdb = clusterTaskContext.getClusterContentDatabase();
		if (clusterdb != null) {
			SakerPathFiles.writeTo(file, os, clusterdb);
		} else {
			file.writeTo(os);
		}
	}

	@Override
	public void writeTo(SakerFile file, ByteSink os) throws IOException, NullPointerException {
		Objects.requireNonNull(file, "file");
		Objects.requireNonNull(os, "output");
		ContentDatabase clusterdb = clusterTaskContext.getClusterContentDatabase();
		if (clusterdb != null) {
			SakerPathFiles.writeTo(file, ByteSink.toOutputStream(os), clusterdb);
		} else {
			file.writeTo(os);
		}
	}

	@Override
	public InputStream openInputStream(SakerFile file) throws IOException {
		Objects.requireNonNull(file, "file");
		ContentDatabase clusterdb = clusterTaskContext.getClusterContentDatabase();
		if (clusterdb != null) {
			return SakerPathFiles.openInputStream(file, clusterdb);
		}
		return file.openInputStream();
	}

	@Override
	public ByteSource openByteSource(SakerFile file) throws IOException, NullPointerException {
		Objects.requireNonNull(file, "file");
		ContentDatabase clusterdb = clusterTaskContext.getClusterContentDatabase();
		if (clusterdb != null) {
			return ByteSource.valueOf(SakerPathFiles.openInputStream(file, clusterdb));
		}
		return file.openByteSource();
	}

	@Override
	public ByteArrayRegion getBytes(SakerFile file) throws IOException {
		Objects.requireNonNull(file, "file");
		ContentDatabase clusterdb = clusterTaskContext.getClusterContentDatabase();
		if (clusterdb != null) {
			return SakerPathFiles.getBytes(file, clusterdb);
		}
		return file.getBytes();
	}

	@Override
	public String getContent(SakerFile file) throws IOException {
		Objects.requireNonNull(file, "file");
		ContentDatabase clusterdb = clusterTaskContext.getClusterContentDatabase();
		if (clusterdb != null) {
			return SakerPathFiles.getContent(file, clusterdb);
		}
		return file.getContent();
	}

	@Override
	public SakerFile createProviderPathFile(String name, ProviderHolderPathKey pathkey)
			throws NullPointerException, IOException {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(pathkey, "path key");
		if (LocalFileProvider.getProviderKeyStatic().equals(pathkey.getFileProviderKey())) {
			FileEntry attrs = pathkey.getFileProvider().getFileAttributes(pathkey.getPath());
			return clusterTaskContext.internalCreateProviderPathFile(name, pathkey, attrs.isDirectory());
		}
		return utils.createProviderPathFile(name, pathkey);
	}

	@Override
	public SakerFile createProviderPathFile(String name, ProviderHolderPathKey pathkey,
			ContentDescriptor currentpathcontentdescriptor) throws IOException {
		return utils.createProviderPathFile(name, pathkey, currentpathcontentdescriptor);
	}

	@Override
	public SakerFile createProviderPathFileWithPosixFilePermissions(String name, ProviderHolderPathKey pathkey,
			ContentDescriptor currentpathcontentdescriptor, Set<PosixFilePermission> permissions)
			throws IOException, NullPointerException, InvalidFileTypeException {
		return utils.createProviderPathFileWithPosixFilePermissions(name, pathkey, currentpathcontentdescriptor,
				permissions);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> toPathFileMap(Iterable<? extends SakerFile> files) {
		return utils.toPathFileMap(files);
	}

	@Override
	public NavigableMap<SakerPath, ContentDescriptor> toPathContentMap(Iterable<? extends SakerFile> files) {
		return utils.toPathContentMap(files);
	}

	@Override
	public NavigableMap<String, ContentDescriptor> getChildrenFileContents(SakerDirectory dir) {
		return utils.getChildrenFileContents(dir);
	}

	@Override
	public void invalidate(Iterable<? extends PathKey> pathkeys) {
		clusterTaskContext.invalidateInClusterDatabase(pathkeys);
		this.utils.invalidate(pathkeys);
	}

	@Override
	public void invalidateWithPosixFilePermissions(ProviderHolderPathKey pathkey)
			throws NullPointerException, IOException {
		IOException s = null;
		try {
			clusterTaskContext.invalidateWithPosixFilePermissionsInClusterDatabase(pathkey);
		} catch (IOException e) {
			s = e;
		}
		try {
			this.utils.invalidateWithPosixFilePermissions(pathkey);
		} catch (IOException e) {
			IOUtils.addExc(e, s);
			throw e;
		}
		IOUtils.throwExc(s);
	}

	@Override
	public void reportIgnoredException(Throwable e) {
		if (e == null) {
			//we need to instantiate the NPE on the caller side, and not on the receiver side to preserve stacktrace
			e = new NullPointerException("ignored exception");
		}
		clusterTaskContext.reportIgnoredException(TaskIdentifierExceptionView.create(e));
	}

	@Override
	public ContentDescriptor synchronize(ProviderHolderPathKey source, ProviderHolderPathKey target, int syncflag)
			throws IOException {
		ContentDescriptor result = utils.synchronize(source, target, syncflag);
		clusterTaskContext.invalidateInClusterDatabase(target);
		return result;
	}

	@Override
	public void addSynchronizeInvalidatedProviderPathFileToDirectory(SakerDirectory directory,
			ProviderHolderPathKey pathkey, String filename) throws IOException, NullPointerException {
		Objects.requireNonNull(directory, "directory");
		Objects.requireNonNull(pathkey, "path key");
		Objects.requireNonNull(filename, "file name");

		clusterTaskContext.invalidateInClusterDatabase(pathkey);

		if (LocalFileProvider.getProviderKeyStatic().equals(pathkey.getFileProviderKey())) {
			FileEntry attrs = pathkey.getFileProvider().getFileAttributes(pathkey.getPath());
			clusterTaskContext.internalAddSynchronizeInvalidatedProviderPathFileToDirectory(directory, pathkey,
					filename, attrs.isDirectory());
			return;
		}

		utils.addSynchronizeInvalidatedProviderPathFileToDirectory(directory, pathkey, filename);
	}

	@Override
	public Path mirrorDirectoryAtPath(SakerPath path, DirectoryVisitPredicate synchpredicate)
			throws IOException, InvalidFileTypeException, FileNotFoundException, NullPointerException {
		Objects.requireNonNull(path, "path");
		PathSakerFileContents pathsakerfilecontents = ((InternalTaskContext) clusterTaskContext.realTaskContext)
				.internalGetPathSakerFileContents(path);
		if (pathsakerfilecontents == null) {
			throw new FileNotFoundException(path.toString());
		}
		SakerFile file = pathsakerfilecontents.getFile();
		if (!(file instanceof SakerDirectory)) {
			throw new InvalidFileTypeException("Not a directory at path: " + path);
		}
		return clusterTaskContext.getExecutionContext().mirror(pathsakerfilecontents.getPath(), file, synchpredicate,
				pathsakerfilecontents.getContents());
	}

	@Override
	public Path mirrorFileAtPath(SakerPath path)
			throws IOException, InvalidFileTypeException, FileNotFoundException, NullPointerException {
		Objects.requireNonNull(path, "path");
		PathSakerFileContents pathsakerfilecontents = ((InternalTaskContext) clusterTaskContext.realTaskContext)
				.internalGetPathSakerFileContents(path);
		if (pathsakerfilecontents == null) {
			throw new FileNotFoundException(path.toString());
		}
		SakerFile file = pathsakerfilecontents.getFile();
		if (file instanceof SakerDirectory) {
			throw new InvalidFileTypeException("File is a directory at path: " + path);
		}
		return clusterTaskContext.getExecutionContext().mirror(pathsakerfilecontents.getPath(), file, null,
				pathsakerfilecontents.getContents());
	}

	@Override
	public MirroredFileContents mirrorFileAtPathContents(SakerPath path)
			throws IOException, InvalidFileTypeException, FileNotFoundException, NullPointerException {
		Objects.requireNonNull(path, "path");
		PathSakerFileContents pathsakerfilecontents = ((InternalTaskContext) clusterTaskContext.realTaskContext)
				.internalGetPathSakerFileContents(path);
		if (pathsakerfilecontents == null) {
			throw new FileNotFoundException(path.toString());
		}
		SakerFile file = pathsakerfilecontents.getFile();
		if (file instanceof SakerDirectory) {
			throw new InvalidFileTypeException("File is a directory at path: " + path);
		}
		ContentDescriptor contents = pathsakerfilecontents.getContents();
		Path mirroredpath = clusterTaskContext.getExecutionContext().mirror(pathsakerfilecontents.getPath(), file, null,
				contents);
		return new MirroredFileContents(mirroredpath, contents);
	}
}
