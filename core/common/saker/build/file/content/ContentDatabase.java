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
package saker.build.file.content;

import java.io.IOException;
import java.util.NavigableMap;
import java.util.Set;

import saker.build.file.SecondaryStreamException;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.SakerFileProvider;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.PriorityMultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.function.IORunnable;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeMapWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeSetStringElementWrapper;

/**
 * A content database is responsible for storing expected contents of file system files.
 * <p>
 * It is used to synchronize the in-memory file hierarchy to the disk, and keep the content descriptors for the
 * persisted files.
 * <p>
 * Internally a content database can store other meta-data related to the build executions.
 * <p>
 * Under normal circumstances, tasks should not directly interact with the content database.
 * <p>
 * Clients should not implement this interface.
 */
public interface ContentDatabase {
	/**
	 * Functional interface for executing content updating.
	 * <p>
	 * The instance should store the target location it updates to.
	 */
	@FunctionalInterface
	public interface ContentUpdater {
		/**
		 * Executes the updating of the contents of the file.
		 * 
		 * @throws IOException
		 *             In case of I/O error.
		 */
		public void update() throws IOException;

		/**
		 * Executes the updating of the contents of the file, optionally writing the contents to the argument stream
		 * simultaneously.
		 * <p>
		 * Writing the contents to the argument stream is optional, the implementation should update the file contents
		 * as it would in {@link #update()}.
		 * <p>
		 * The default implementation calls {@link #update()} and returns <code>false</code>.
		 * 
		 * @param os
		 *            The stream to optionally write the contents to.
		 * @return <code>true</code> if the contents were written to the argument stream as well.
		 * @throws IOException
		 *             In case of I/O error.
		 * @throws SecondaryStreamException
		 *             In case of I/O error during writing to the additional stream. The cause of the exception must be
		 *             the originating exception.
		 * @see PriorityMultiplexOutputStream
		 */
		public default boolean updateWithStream(ByteSink os) throws IOException, SecondaryStreamException {
			update();
			return false;
		}
	}

	/**
	 * Synchronizer functional interface returned by the content database to execute content synchronization in a
	 * deferred manner.
	 * <p>
	 * Clients are recommended to call {@link #update()} in the future. Calling {@link #update()} multiple times might
	 * result in multiple synchronization of the contents.
	 */
	@FunctionalInterface
	public interface DeferredSynchronizer {
		/**
		 * Executes the synchronization of the contents.
		 * <p>
		 * The manner of the execution is based on the construction parameters specified when calling the appropriate
		 * {@link ContentDatabase} function.
		 * 
		 * @throws IOException
		 *             In case of I/O error.
		 */
		public void update() throws IOException;
	}

	/**
	 * Represents a handle to the stored contents of a given path.
	 * <p>
	 * The path is based on the passed arguments when retrieving the handle instance.
	 */
	public interface ContentHandle {
		/**
		 * Gets the current content descriptor for the handle.
		 * 
		 * @return The content descriptor.
		 */
		@RMISerialize
		public ContentDescriptor getContent();

		/**
		 * Gets the content database this handle is bound to.
		 * 
		 * @return The content database.
		 */
		@RMICacheResult
		public ContentDatabase getContentDatabase();
	}

	/**
	 * Executes a synchronization for the given content descriptor using an updater.
	 * <p>
	 * If the contents at the given path haven't changed, then the synchronization will be skipped.
	 * 
	 * @param pathkey
	 *            The target path key to the synchronization.
	 * @param content
	 *            The content descriptor of the content to be synchronized to the path.
	 * @param updater
	 *            The updater to execute the synchronization.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public void synchronize(ProviderHolderPathKey pathkey, @RMISerialize ContentDescriptor content,
			ContentUpdater updater) throws IOException;

	/**
	 * Creates a deferred synchronizer for the specified path, if the contents have changed.
	 * 
	 * @param pathkey
	 *            The path key to the to be synchronized file.
	 * @param content
	 *            The expected contents at the path.
	 * @param updater
	 *            The updater to execute the synchronization.
	 * @return A deferred synchronizer, or <code>null</code> if the contents haven't changed.
	 */
	public DeferredSynchronizer synchronizeDeferred(ProviderHolderPathKey pathkey,
			@RMISerialize ContentDescriptor content, ContentUpdater updater);

	/**
	 * Invalidates the stored contents for a given path (and subpaths).
	 * 
	 * @param pathkey
	 *            The path key to invalidate.
	 */
	public void invalidate(PathKey pathkey);

	/**
	 * Invalidates the stored contents for a given path (and subpaths) and returns the contents after the invalidation
	 * for the path.
	 * 
	 * @param pathkey
	 *            The path key to invalidate.
	 * @return The current content descriptor at the given path.
	 */
	@RMISerialize
	public ContentDescriptor invalidateGetContentDescriptor(ProviderHolderPathKey pathkey);

	/**
	 * Discovers a file at the given path and associates the provided contents with it.
	 * 
	 * @param pathkey
	 *            The path key to discover.
	 * @param content
	 *            The contents to associate the discovered file with.
	 * @return A content handle to the disovered file.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public ContentHandle discover(ProviderHolderPathKey pathkey, @RMISerialize ContentDescriptor content)
			throws IOException;

	/**
	 * Writes the contents of the file specified by the argument path key, optionally synchronizing if the contents have
	 * changed.
	 * 
	 * @param pathkey
	 *            The path key to the file.
	 * @param content
	 *            The expected content descriptor.
	 * @param os
	 *            The output to write the contents to.
	 * @param updater
	 *            The synchronization executor.
	 * @throws IOException
	 *             In case of I/O error-
	 */
	public void writeToStreamWithContentOrSynchronize(ProviderHolderPathKey pathkey,
			@RMISerialize ContentDescriptor content, ByteSink os, ContentUpdater updater) throws IOException;

	/**
	 * Opens an input stream to the contents of the specified file, optionally synchronizing first if the contents have
	 * changed.
	 * 
	 * @param pathkey
	 *            The path key to the file.
	 * @param content
	 *            The expected content descriptor.
	 * @param updater
	 *            The synchronization executor.
	 * @return An opened input stream to the contents of the file.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public ByteSource openInputWithContentOrSynchronize(ProviderHolderPathKey pathkey,
			@RMISerialize ContentDescriptor content, ContentUpdater updater) throws IOException;

	/**
	 * Gets the bytes of the specified file, optionally synchronizing first if the contents have changed.
	 * 
	 * @param pathkey
	 *            The path key to the file.
	 * @param content
	 *            The expected content descriptor.
	 * @param updater
	 *            The synchronization executor.
	 * @return The byte contents of the file.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public ByteArrayRegion getBytesWithContentOrSynchronize(ProviderHolderPathKey pathkey,
			@RMISerialize ContentDescriptor content, ContentUpdater updater) throws IOException;

	/**
	 * Gets the content descriptor tracked by the database for the given path key.
	 * 
	 * @param pathkey
	 *            The path key.
	 * @return The content descriptor.
	 */
	@RMISerialize
	public ContentDescriptor getContentDescriptor(ProviderHolderPathKey pathkey);

	/**
	 * Gets the content handle to the given path key.
	 * 
	 * @param pathkey
	 *            The path key.
	 * @return The content handle.
	 */
	public ContentHandle getContentHandle(ProviderHolderPathKey pathkey);

	/**
	 * Creates the directories for the given path.
	 * <p>
	 * Any parent files are overwritten with directories.
	 * 
	 * @param pathkey
	 *            The path key.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public void createDirectoryAtPath(ProviderHolderPathKey pathkey) throws IOException;

	/**
	 * Executes a directory synchronization at the given path.
	 * 
	 * @param pathkey
	 *            The path to the directory.
	 * @param dirsynchronizer
	 *            The operation to execute for synchronization.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public void syncronizeDirectory(ProviderHolderPathKey pathkey, IORunnable dirsynchronizer) throws IOException;

	/**
	 * Deletes the children of the directory specified by the path key if their name is not in the specified set.
	 * 
	 * @param pathkey
	 *            The path key to the directory.
	 * @param keepchildren
	 *            The names of the children to keep.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see SakerFileProvider#deleteChildrenRecursivelyIfNotIn(SakerPath, Set)
	 */
	public void deleteChildrenIfNotIn(ProviderHolderPathKey pathkey,
			@RMIWrap(RMITreeSetStringElementWrapper.class) Set<String> keepchildren) throws IOException;

	/**
	 * Discovers the attributes of the file at the given path key.
	 * 
	 * @param pathkey
	 *            The path key to the file.
	 * @return A container for the discovered attributes and a content handle.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public ContentHandleAttributes discoverFileAttributes(ProviderHolderPathKey pathkey) throws IOException;

	/**
	 * Discovers the attributes of the children of the directory specified by the argument path key.
	 * 
	 * @param directorypathkey
	 *            The directory path key.
	 * @return A map of child names to their discovered attribute handles.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@RMIWrap(RMITreeMapWrapper.class)
	public NavigableMap<String, ContentHandleAttributes> discoverDirectoryChildrenAttributes(
			ProviderHolderPathKey directorypathkey) throws IOException;

	/**
	 * Gets the path configuration this database uses.
	 * <p>
	 * This might be different from the path configuration of the current execution.
	 * 
	 * @return The path configuration.
	 */
	@RMICacheResult
	public ExecutionPathConfiguration getPathConfiguration();

	/**
	 * Container for a {@link ContentHandle} and file attributes pair retrieved from a result of a file discovery.
	 * <p>
	 * The contained content handle is <code>null</code> if the discovered file is a directory.
	 */
	@RMIWrap(ContentHandleAttributes.TransferRMIWrapper.class)
	public static final class ContentHandleAttributes {
		protected final ContentHandle contentHandle;
		protected final FileEntry attributes;

		protected ContentHandleAttributes(ContentHandle contentHandle, FileEntry attributes) {
			this.contentHandle = contentHandle;
			this.attributes = attributes;
		}

		/**
		 * Gets the content handle of the discovered file.
		 * 
		 * @return The content handle, or <code>null</code> if the {@linkplain #getAttributes() file} is a directory.
		 */
		public ContentHandle getContentHandle() {
			return contentHandle;
		}

		/**
		 * Gets the attributes of the discovered file.
		 * 
		 * @return The file attributes.
		 */
		public FileEntry getAttributes() {
			return attributes;
		}

		public static final class TransferRMIWrapper implements RMIWrapper {
			private ContentHandleAttributes obj;

			public TransferRMIWrapper() {
			}

			@Override
			public void writeWrapped(RMIObjectOutput out) throws IOException {
				out.writeRemoteObject(obj.contentHandle);
				out.writeObject(obj.attributes);
			}

			@Override
			public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
				ContentHandle handle = (ContentHandle) in.readObject();
				FileEntry attrs = (FileEntry) in.readObject();
				this.obj = new ContentHandleAttributes(handle, attrs);
			}

			@Override
			public Object resolveWrapped() {
				return obj;
			}

			@Override
			public Object getWrappedObject() {
				throw new UnsupportedOperationException();
			}

		}
	}
}
