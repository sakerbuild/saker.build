package saker.build.file.content;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import saker.apiextract.api.PublicApi;
import saker.build.file.content.HashContentDescriptor.MessageDigestOutputStream;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.SakerFileProvider;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;

/**
 * Enumeration of common content descriptor supplier implementations.
 */
@PublicApi
public enum CommonContentDescriptorSupplier implements ContentDescriptorSupplier {
	/**
	 * Creates content descriptors based on the attributes of the subject file.
	 */
	FILE_ATTRIBUTES {
		@Override
		public ContentDescriptor get(ProviderHolderPathKey pathkey) throws IOException {
			SakerFileProvider fileprovider = pathkey.getFileProvider();
			SakerPath path = pathkey.getPath();
			return FileAttributesContentDescriptor.create(pathkey, fileprovider.getFileAttributes(path));
		}

		@Override
		public ContentDescriptor getUsingFileAttributes(ProviderHolderPathKey pathkey, BasicFileAttributes attrs)
				throws IOException {
			return FileAttributesContentDescriptor.create(pathkey, attrs);
		}
	},
	/**
	 * Creates content descriptors based on the contents of the subject file by hashing them using MD5 algorithm.
	 */
	HASH_MD5 {
		@Override
		public ContentDescriptor get(ProviderHolderPathKey pathkey) throws IOException {
			SakerFileProvider fileprovider = pathkey.getFileProvider();
			SakerPath path = pathkey.getPath();
			try {
				return HashContentDescriptor.createWithHash(fileprovider.hash(path, "MD5"));
			} catch (AccessDeniedException e) {
				//failed to open the file, it might be due to it being a directory
				FileEntry attrs;
				try {
					attrs = fileprovider.getFileAttributes(path);
				} catch (IOException attre) {
					//failed to retrieve the attributes to the file
					e.addSuppressed(attre);
					throw e;
				}
				if (attrs.isDirectory()) {
					return DirectoryContentDescriptor.INSTANCE;
				}
				throw e;
			} catch (NoSuchAlgorithmException e) {
				throw new AssertionError(e);
			}
		}

		@Override
		public ContentDescriptor getUsingFileAttributes(ProviderHolderPathKey pathkey, BasicFileAttributes attrs)
				throws IOException {
			if (attrs.isDirectory()) {
				return DirectoryContentDescriptor.INSTANCE;
			}
			SakerFileProvider fileprovider = pathkey.getFileProvider();
			SakerPath path = pathkey.getPath();
			try {
				return HashContentDescriptor.createWithHash(fileprovider.hash(path, "MD5"));
			} catch (NoSuchAlgorithmException e) {
				throw new AssertionError(e);
			}
		}

		@Override
		public ContentDescriptor getUsingFileContent(ProviderHolderPathKey pathkey, ByteArrayRegion bytes,
				BasicFileAttributes attrs) throws IOException {
			return HashContentDescriptor.hash(bytes, getDigest());
		}

		@Override
		public ByteSink getCalculatingOutput() {
			return new HashContentDescriptor.MessageDigestOutputStream(getDigest());
		}

		@Override
		public ContentDescriptor getCalculatedOutput(ProviderHolderPathKey pathkey, ByteSink calculatingoutput)
				throws IOException {
			HashContentDescriptor.MessageDigestOutputStream os = (MessageDigestOutputStream) calculatingoutput;
			return HashContentDescriptor.createWithHash(os.digestWithCount());
		}

		private MessageDigest getDigest() throws AssertionError {
			try {
				return MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				throw new AssertionError(e);
			}
		}
	};
}
