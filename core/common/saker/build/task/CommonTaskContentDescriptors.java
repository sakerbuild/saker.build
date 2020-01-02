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
package saker.build.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.DirectoryContentDescriptor;
import saker.build.file.content.NonExistentContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.task.delta.DeltaType;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * Utility class containing common content descriptors which can be used for reporting file dependencies.
 * <p>
 * The static singleton instances can be used for reporting file dependencies.
 * 
 * @see TaskContext
 * @see TaskContext#reportInputFileDependency(Object, SakerPath, ContentDescriptor)
 * @see TaskContext#reportOutputFileDependency(Object, SakerPath, ContentDescriptor)
 */
@PublicApi
public final class CommonTaskContentDescriptors {
	/**
	 * Content descriptor that expects the associated file or directory to exist, but doesn't specify other requirements
	 * on it.
	 * <p>
	 * If the descriptor to compare is <code>null</code> or equals {@link NonExistentContentDescriptor#INSTANCE}, then
	 * it will return <code>true</code>.
	 */
	public static final ContentDescriptor PRESENT = new FilePresentContentDescriptor();
	/**
	 * Content descriptor that expects the associated file or directory to not exist.
	 * <p>
	 * If the descriptor to compare is non-<code>null</code> and is other than
	 * {@link NonExistentContentDescriptor#INSTANCE}, then it will return <code>true</code>.
	 * 
	 * @see NonExistentContentDescriptor
	 */
	public static final ContentDescriptor NOT_PRESENT = NonExistentContentDescriptor.INSTANCE;

	/**
	 * Content descriptor that expects the associated path to be not a file. That is, it may be a directory, or not
	 * exist.
	 * 
	 * @see DirectoryContentDescriptor
	 * @see NonExistentContentDescriptor
	 */
	public static final ContentDescriptor IS_NOT_FILE = new IsNotFileContentDescriptor();

	/**
	 * Content descriptor that expects the associated path to not be a directory. That is, it may be a file, or not
	 * exist.
	 * 
	 * @see DirectoryContentDescriptor
	 */
	public static final ContentDescriptor IS_NOT_DIRECTORY = new IsNotDirectoryContentDescriptor();

	/**
	 * Content descriptor that expects the associated path to be a file. That is, it must exist, and mustn't be a
	 * directory.
	 * 
	 * @see DirectoryContentDescriptor
	 */
	public static final ContentDescriptor IS_FILE = new IsFileContentDescriptor();

	/**
	 * Content descriptor that expects the associated path to be a directory. That is, it must exist, and must be a
	 * directory.
	 * 
	 * @see DirectoryContentDescriptor
	 */
	public static final ContentDescriptor IS_DIRECTORY = DirectoryContentDescriptor.INSTANCE;

	/**
	 * Content descriptor that signals that the task doesn't care about the modifications made to the associated file.
	 * <p>
	 * The {@link ContentDescriptor#isChanged(ContentDescriptor)} method always returns false.
	 * <p>
	 * This content descriptor can be used when the task wants to avoid {@link DeltaType#INPUT_FILE_ADDITION} deltas to
	 * trigger task reinvocation.
	 */
	public static final ContentDescriptor DONT_CARE = new DontCareContentDescriptor();

	private CommonTaskContentDescriptors() {
		throw new UnsupportedOperationException();
	}

	private static final class FilePresentContentDescriptor implements ContentDescriptor, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public FilePresentContentDescriptor() {
		}

		@Override
		public boolean isChanged(ContentDescriptor o) {
			if (o == null || NonExistentContentDescriptor.INSTANCE.equals(o)) {
				//the contents no longer exist, file not present
				return true;
			}
			return false;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

		@Override
		public String toString() {
			return "FilePresentContentDescriptor";
		}

	}

	private static final class IsNotFileContentDescriptor implements ContentDescriptor, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public IsNotFileContentDescriptor() {
		}

		@Override
		public boolean isChanged(ContentDescriptor o) {
			if (o == null || NonExistentContentDescriptor.INSTANCE.equals(o)) {
				//the contents not exist, file not present
				//    therefore it is not a file
				//    not changed
				return false;
			}
			if (DirectoryContentDescriptor.INSTANCE.equals(o)) {
				//the contents is a directory
				//    which is not a file
				//    not changed
				return false;
			}
			//there are contents, and it doesnt represent a directory
			//consider changed
			return true;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

		@Override
		public String toString() {
			return "IsNotFileContentDescriptor";
		}

	}

	private static final class IsFileContentDescriptor implements ContentDescriptor, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public IsFileContentDescriptor() {
		}

		@Override
		public boolean isChanged(ContentDescriptor o) {
			if (o == null || NonExistentContentDescriptor.INSTANCE.equals(o)) {
				//the contents not exist, file not present
				return true;
			}
			if (DirectoryContentDescriptor.INSTANCE.equals(o)) {
				//the contents is a directory
				//    which is not a file
				//    changed
				return true;
			}
			//the contents exist, and not a directory
			//    unchanged
			return false;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

		@Override
		public String toString() {
			return "IsFileContentDescriptor";
		}

	}

	private static final class IsNotDirectoryContentDescriptor implements ContentDescriptor, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public IsNotDirectoryContentDescriptor() {
		}

		@Override
		public boolean isChanged(ContentDescriptor o) {
			if (DirectoryContentDescriptor.INSTANCE.equals(o)) {
				//the contents represent a directory
				//    as we expect contents which are not a directory, consider it changed 
				return true;
			}
			//the contents doesn't represent a directory, as we expect it.
			//    not changed
			return false;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

		@Override
		public String toString() {
			return "IsNotDirectoryContentDescriptor";
		}

	}

	private static final class DontCareContentDescriptor implements ContentDescriptor, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public DontCareContentDescriptor() {
		}

		@Override
		public boolean isChanged(ContentDescriptor previouscontent) {
			return false;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

		@Override
		public String toString() {
			return "DontCareContentDescriptor";
		}
	}
}
