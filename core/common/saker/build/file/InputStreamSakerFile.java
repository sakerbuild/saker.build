package saker.build.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import saker.build.file.content.ContentDescriptor;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;

public class InputStreamSakerFile extends SakerFileBase {
	public interface InputStreamSupplier {
		public InputStream get() throws IOException;
	}

	private final InputStreamSupplier factory;
	private final ContentDescriptor contentDescriptor;

	public InputStreamSakerFile(String filename, InputStreamSupplier factory, ContentDescriptor contentDescriptor) {
		super(filename);
		this.factory = factory;
		this.contentDescriptor = contentDescriptor;
	}

	@Override
	public InputStream openInputStreamImpl() throws IOException {
		return factory.get();
	}

	@Override
	public void writeToStreamImpl(OutputStream os) throws IOException {
		try (InputStream is = openInputStreamImpl()) {
			StreamUtils.copyStream(is, os);
		}
	}

	@Override
	public ByteArrayRegion getBytesImpl() throws IOException {
		try (InputStream is = openInputStreamImpl();
				UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
			baos.readFrom(is);
			return baos.toByteArrayRegion();
		}
	}

	@Override
	public String getContentImpl() throws IOException {
		try (InputStream is = openInputStreamImpl();
				UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
			baos.readFrom(is);
			return baos.toString();
		}
	}

	@Override
	public int getEfficientOpeningMethods() {
		return OPENING_METHOD_OPENINPUTSTREAM;
	}

	@Override
	public ContentDescriptor getContentDescriptor() {
		return contentDescriptor;
	}

}
