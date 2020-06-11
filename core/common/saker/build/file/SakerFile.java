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
import java.util.Set;

import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.task.TaskExecutionUtilities;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.PriorityMultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIInputStreamWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIOutputStreamWrapper;
import saker.build.util.rmi.EnumSetRMIWrapper;

/**
 * Interface for file representation in the build system.
 * <p>
 * The build system maintains an in-memory representation of the file system that the tasks should use. The tasks should
 * compute their outputs and use this file representation hierarchy to store the file outputs. The purpose of this
 * representation is to increase performance by reducing I/O load, and appropriate caching (other optimizations may
 * apply as well).
 * <p>
 * During a lifetime of a file it may have only one parent. In its initial state it is constructed without any parent
 * directory. It can be attached to a parent via {@link SakerDirectory#add(SakerFile)} or similar methods. They can be
 * removed from its parent after they've been attached via {@link #remove()}, but once they have been removed, they
 * cannot be reattached to another directory. If one needs to move a file, the {@link DelegateSakerFile} should be used,
 * and the original file removed. Note that directories cannot be moved in this way.
 * <p>
 * The contents of files might be stored anywhere. In memory, on disk, over the network, etc... <br>
 * It is important that each file has a location where it resides in the build system. This location is specified by
 * {@link #getSakerPath()}. <br>
 * This location can be mapped to a file system storage location where it should actually reside if the in-memory
 * representation didn't exist. As the outputs of tasks are collected in the in-memory hierarchy, it is required to
 * synchronize the files to their corresponding location. This process is called synchronization.
 * <p>
 * During synchronization the contents of the file will be persisted to the appropriate location in the file system. The
 * target location is specified by the execution configuration and the path of the file. The synchronization process
 * includes checking if the current file residing at the location has the same contents as the in-memory file. If they
 * match, then no I/O will be done. If the contents differ then the contents of this file will be written out to the
 * target location. Synchronizing multiple times while the contents of the disk is unchanged by external agents, should
 * be a no-op.
 * <p>
 * During synchronization, the {@link ContentDescriptor} of the file is used to determine if the file has changed.
 * <p>
 * This interface modifies the behaviour of the content methods specified by {@link FileHandle}. Calling these methods
 * may implicitly synchronize the contents of the file, and will retrieve the contents in the most efficient way.
 * <p>
 * Implicit synchronization can be avoided by the caller by invoking the introduced content retrieval methods with the
 * <code>Impl</code> suffix. <br>
 * Implicit synchronization can be also avoided by the subclass by overriding {@link #getEfficientOpeningMethods()}
 * method. <br>
 * Implicit synchronization does not occur for files that aren't attached to a parent. I.e. if {@link #getSakerPath()}
 * returns a relative path at the time of calling when retrieving the content, the implicit synchronization is not
 * employed.
 * <p>
 * The base directories for working with files are available from the exection and task contexts.
 * <p>
 * Instances of this interface can be checked if they represent a directory by using the
 * <code>(file instanceof {@link SakerDirectory})</code> expression on them.
 * <p>
 * When designing tasks for remote execution, and accessing contents of a file it is strongly recommended to use the
 * utility methods in {@link TaskExecutionUtilities}, instead of calling it directly on this interface. Doing so can
 * result in increased performance, as the build runtime can employ caching and improve overall network performance.
 * <br>
 * It is recommended to make files RMI-transferrable when executing remote tasks, by overriding
 * {@link #getRemoteExecutionRMIWrapper()}. Implementations should not directly declare RMI transfer properties for the
 * classes themselves. (I.e. do not RMI annotate the class, but only override {@link #getRemoteExecutionRMIWrapper()})
 * <p>
 * Clients must not directly implement this interface, but extend the {@link SakerFileBase} abstract class. Implementing
 * this interface directly can and will result in runtime errors.
 * 
 * @see SakerDirectory
 */
public interface SakerFile extends FileHandle {
	/**
	 * Flag for {@link #getEfficientOpeningMethods()} for signaling that the writing to streams are efficient.
	 * 
	 * @see #writeToStreamImpl(OutputStream)
	 */
	public static final int OPENING_METHOD_WRITETOSTREAM = 1 << 0;
	/**
	 * Flag for {@link #getEfficientOpeningMethods()} for signaling that opening streams are efficient.
	 * 
	 * @see #openInputStreamImpl()
	 * @see #openByteSourceImpl()
	 */
	public static final int OPENING_METHOD_OPENINPUTSTREAM = 1 << 1;
	/**
	 * Flag for {@link #getEfficientOpeningMethods()} for signaling that getting raw byte contents is efficient.
	 * 
	 * @see #getBytesImpl()
	 */
	public static final int OPENING_METHOD_GETBYTES = 1 << 2;
	/**
	 * Flag for {@link #getEfficientOpeningMethods()} for signaling that getting string contents is efficient.
	 * 
	 * @see #getContentImpl()
	 */
	public static final int OPENING_METHOD_GETCONTENTS = 1 << 3;

	/**
	 * Flag for {@link #getEfficientOpeningMethods()} for signalling that no opening methods are efficient.
	 */
	public static final int OPENING_METHODS_NONE = 0;
	/**
	 * Flag for {@link #getEfficientOpeningMethods()} for signalling that all opening methods are efficient.
	 * <p>
	 * Using this flag will result in no implicit synchronization.
	 */
	public static final int OPENING_METHODS_ALL = 0b1111;

	/**
	 * Gets the path of this file.
	 * <p>
	 * The path of this file is based on its parent path, appended with its name. If the file has no parent, then a
	 * relative path will be returned with a single path name which is the name of this file.
	 * <p>
	 * The path identifies the synchronization location of this file based on the current path configuration.
	 * 
	 * @return The path of this file.
	 */
	public SakerPath getSakerPath();

	/**
	 * Gets the parent of this file.
	 * <p>
	 * The parent of a file can change during the lifetime of an object.
	 * 
	 * @return The parent of this file.
	 */
	public SakerDirectory getParent();

	/**
	 * Removes this file from its parent.
	 * <p>
	 * Calling this is a no-op if the file has no current parent.
	 */
	public void remove();

	/**
	 * Gets the content descriptor of this file.
	 * <p>
	 * See {@link ContentDescriptor}. Content descriptors are used to determine if the file contents need to be
	 * persisted to the file system.
	 * 
	 * @return The content descriptor. Never <code>null</code>.
	 */
	@RMISerialize
	public ContentDescriptor getContentDescriptor();

	/**
	 * Gets the efficient opening methods flag of this file.
	 * <p>
	 * An opening method is considered (performance-wise) efficient if it generally takes less resources (time and
	 * memory) to call the appropriate content method instead of trying to employ caching to the disk.
	 * <p>
	 * If an opening method is reported as efficient, then the implicit synchronizations will not take place specified
	 * by the documentation of {@link SakerFile} interface.
	 * <p>
	 * If an opening method is <b>not</b> reported as efficient, then calling content retrieval methods which do not end
	 * with <code>Impl</code> will check if the file system already has the contents of this file persisted, and will
	 * read the contents from there if it has. If not, then the contents will be synchronized with to the disk, and the
	 * contents will be retrieved in the most efficient manner. (This manner depends on the nature of the opening
	 * method.)
	 * <p>
	 * The default implementation returns {@link #OPENING_METHODS_NONE}.
	 * 
	 * @return The efficient opening methods.
	 * @see #OPENING_METHOD_WRITETOSTREAM
	 * @see #OPENING_METHOD_OPENINPUTSTREAM
	 * @see #OPENING_METHOD_GETBYTES
	 * @see #OPENING_METHOD_GETCONTENTS
	 */
	@RMICacheResult
	public default int getEfficientOpeningMethods() {
		return OPENING_METHODS_NONE;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This method implicitly synchronizes the contents of the file, unless {@link #getEfficientOpeningMethods()}
	 * reports otherwise.
	 * <p>
	 * It is recommended to use {@link TaskExecutionUtilities#writeTo(SakerFile, OutputStream)} instead of calling this
	 * method directly.
	 * 
	 * @see #OPENING_METHOD_WRITETOSTREAM
	 */
	@Override
	public void writeTo(@RMIWrap(RMIOutputStreamWrapper.class) OutputStream os)
			throws IOException, NullPointerException;

	/**
	 * {@inheritDoc}
	 * <p>
	 * This method implicitly synchronizes the contents of the file, unless {@link #getEfficientOpeningMethods()}
	 * reports otherwise.
	 * <p>
	 * It is recommended to use {@link TaskExecutionUtilities#writeTo(SakerFile, ByteSink)} instead of calling this
	 * method directly.
	 * 
	 * @see #OPENING_METHOD_WRITETOSTREAM
	 */
	@Override
	public default void writeTo(ByteSink sink) throws IOException, NullPointerException {
		FileHandle.super.writeTo(sink);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This method implicitly synchronizes the contents of the file, unless {@link #getEfficientOpeningMethods()}
	 * reports otherwise.
	 * <p>
	 * It is recommended to use {@link TaskExecutionUtilities#openInputStream(SakerFile)} instead of calling this method
	 * directly.
	 * 
	 * @see #OPENING_METHOD_OPENINPUTSTREAM
	 */
	@Override
	@RMIWrap(RMIInputStreamWrapper.class)
	public default InputStream openInputStream() throws IOException {
		return FileHandle.super.openInputStream();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This method implicitly synchronizes the contents of the file, unless {@link #getEfficientOpeningMethods()}
	 * reports otherwise.
	 * <p>
	 * It is recommended to use {@link TaskExecutionUtilities#openByteSource(SakerFile)} instead of calling this method
	 * directly.
	 * 
	 * @see #OPENING_METHOD_OPENINPUTSTREAM
	 */
	@Override
	public default ByteSource openByteSource() throws IOException {
		return FileHandle.super.openByteSource();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This method implicitly synchronizes the contents of the file, unless {@link #getEfficientOpeningMethods()}
	 * reports otherwise.
	 * <p>
	 * It is recommended to use {@link TaskExecutionUtilities#getBytes(SakerFile)} instead of calling this method
	 * directly.
	 * 
	 * @see #OPENING_METHOD_GETBYTES
	 */
	@Override
	public default ByteArrayRegion getBytes() throws IOException {
		return FileHandle.super.getBytes();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This method implicitly synchronizes the contents of the file, unless {@link #getEfficientOpeningMethods()}
	 * reports otherwise.
	 * <p>
	 * It is recommended to use {@link TaskExecutionUtilities#getContent(SakerFile)} instead of calling this method
	 * directly.
	 * 
	 * @see #OPENING_METHOD_GETCONTENTS
	 */
	@Override
	public default String getContent() throws IOException {
		return FileHandle.super.getContent();
	}

	/**
	 * Synchronizes the contents of this file with the file system.
	 * <p>
	 * The synchronization algorithm is described in the documentation of {@link SakerFile} interface.
	 * 
	 * @throws IOException
	 *             In case of I/O error or if the file doesn't have a parent.
	 */
	public void synchronize() throws IOException;

	/**
	 * Synchronizes the contents of this file to the target file system location.
	 * <p>
	 * The synchronization algorithm is described in the documentation of {@link SakerFile} interface.
	 * <p>
	 * File implementations generally should not override this method, but
	 * {@link #synchronizeImpl(ProviderHolderPathKey)}.
	 * 
	 * @param pathkey
	 *            The target location of the synchronization.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the target location is <code>null</code>.
	 */
	public void synchronize(ProviderHolderPathKey pathkey) throws IOException, NullPointerException;

	/**
	 * Synchronizing implementation for persisting the contents of this file to the target file system location.
	 * <p>
	 * This method will not check if the contents of the disk have been changes in relation to this file, but will
	 * always persist it to the given location.
	 * <p>
	 * Subclasses should implement this method to persist its contents to the location specified by the parameter.
	 * <p>
	 * If subclasses override this method, they must override {@link #synchronizeImpl(ProviderHolderPathKey, ByteSink)}
	 * as well. (They can just simply call {@link #synchronizeImpl(ProviderHolderPathKey)} and return
	 * <code>false</code>.)
	 * 
	 * @param pathkey
	 *            The target location of the synchronization.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the target location is <code>null</code>.
	 */
	public void synchronizeImpl(ProviderHolderPathKey pathkey) throws IOException, NullPointerException;

	/**
	 * Overloaded synchronizing method with additional output stream to write the contents to.
	 * <p>
	 * This method will not check if the contents of the disk have been changes in relation to this file, but will
	 * always persist it to the given location.
	 * <p>
	 * This method exists for performance optimization. Subclasses should override this method and attempt to
	 * concurrently persist the contents of the file to the target location and write the contents to the additional
	 * stream.
	 * <p>
	 * Subclasses must not throw an {@link IOException} if the writing to the additional stream failed, but rethrow them
	 * as a {@link SecondaryStreamException}. If a {@link SecondaryStreamException} is thrown, the synchronization is
	 * going to be considered as successful, and only the writing to the secondary stream is considered as failure. If
	 * an {@link IOException} is thrown, both stream writings is considered to be failed.
	 * <p>
	 * The implementations are not required to handle concurrent writing. This method should return <code>true</code> if
	 * it was able to concurrently synchronize and write the contents to the additional stream. The
	 * {@link PriorityMultiplexOutputStream} utility class can help implementations in implementing this functionality.
	 * <p>
	 * Overriding this method will improve the overall synchronization performance.
	 * <p>
	 * If subclasses override this method, they must override {@link #synchronizeImpl(ProviderHolderPathKey)} as well.
	 * 
	 * @param pathkey
	 *            The target location of the synchronization.
	 * @param additionalwritestream
	 *            The additional stream to write the contents to.
	 * @return <code>true</code> if the contents of the file was successfully written to the additional stream.
	 * @throws IOException
	 *             In case of I/O error during synchronization.
	 * @throws SecondaryStreamException
	 *             In case of I/O error during writing to the additional stream.
	 * @throws NullPointerException
	 *             If any of the parameters are <code>null</code>.
	 * @see ByteSink#toOutputStream(ByteSink)
	 * @see PriorityMultiplexOutputStream
	 */
	public boolean synchronizeImpl(ProviderHolderPathKey pathkey, ByteSink additionalwritestream)
			throws SecondaryStreamException, IOException, NullPointerException;

	/**
	 * Writes the contents of this file without implicit synchronization.
	 * <p>
	 * See {@link #writeTo(OutputStream)}.
	 * <p>
	 * To call this method using a {@link ByteSink}, use {@link ByteSink#toOutputStream(ByteSink)} to create a delegate
	 * object.
	 * <p>
	 * Implementations can use {@link ByteSink#valueOf(OutputStream)} to convert the argument to a byte sink if
	 * required.
	 * 
	 * @param os
	 *            The output stream.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the stream is <code>null</code>.
	 */
	public void writeToStreamImpl(@RMIWrap(RMIOutputStreamWrapper.class) OutputStream os)
			throws IOException, NullPointerException;

	/**
	 * Opens an input stream to the contents of this file without implicit synchronization.
	 * <p>
	 * See {@link #openInputStream()}.
	 * 
	 * @return The opened input stream.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@RMIWrap(RMIInputStreamWrapper.class)
	public default InputStream openInputStreamImpl() throws IOException {
		try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
			writeToStreamImpl(baos);
			return new UnsyncByteArrayInputStream(baos.toByteArrayRegion());
		}
	}

	/**
	 * Opens a byte source to the contents of this file without implicit synchronization.
	 * <p>
	 * See {@link #openByteSource()}.
	 * <p>
	 * If subclasses override this method, they must override {@link #openInputStreamImpl()} as well. (Simply returning
	 * <code>ByteSource.toInputStream(openByteSourceImpl())</code> is fine.)
	 * 
	 * @return The opened byte input.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public default ByteSource openByteSourceImpl() throws IOException {
		return ByteSource.valueOf(openInputStreamImpl());
	}

	/**
	 * Gets the raw byte contents of this file without implicit synchronization.
	 * <p>
	 * See {@link #getBytes()}.
	 * 
	 * @return The byte contents of this file.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public default ByteArrayRegion getBytesImpl() throws IOException {
		try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
			writeToStreamImpl(baos);
			return baos.toByteArrayRegion();
		}
	}

	/**
	 * Gets the string contents of this file without implicit synchronization.
	 * <p>
	 * See {@link #getContent()}.
	 * 
	 * @return The string contents.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public default String getContentImpl() throws IOException {
		return getBytesImpl().toString();
	}

	/**
	 * Gets the {@link RMIWrapper} class ot use when transferring instances during remote execution.
	 * <p>
	 * When designing tasks for remote execution, it is important to keep in mind that the in-memory file hierarchy only
	 * exists on the coordinator machine. In order to handle files from a remote cluster endpoint it is necessary to
	 * transfer files between computers. By default files are transferred based on the called method.
	 * <p>
	 * In order to reduce network calls it is recommended to override this method to customize how the files will be
	 * transferred over the network.
	 * <p>
	 * This method is used during an RMI call if the object is specified to be transferred using
	 * {@link RemoteExecutionSakerFileRMIWrapper}.
	 * 
	 * @return The RMI wrapper class, <code>null</code> if this is not supported.
	 * @see RemoteExecutionSakerFileRMIWrapper
	 * @see SakerDirectory#add(SakerFile)
	 * @see TaskExecutionUtilities#addFile(SakerDirectory, SakerFile)
	 */
	public default Class<? extends RMIWrapper> getRemoteExecutionRMIWrapper() {
		return null;
	}

	//since saker.build 0.8.13
	@RMIWrap(EnumSetRMIWrapper.class)
	public default Set<PosixFilePermission> getPosixFilePermissions() {
		return null;
	}
}
