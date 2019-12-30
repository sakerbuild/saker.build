package testing.saker.build.tests.tasks.file;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import saker.build.file.SakerFileBase;
import saker.build.file.content.ContentDescriptor;

public class StringSakerFile extends SakerFileBase {
	private String content;
	private ContentDescriptor contentDescriptor;

	public StringSakerFile(String name, String content, ContentDescriptor contentDescriptor) {
		super(name);
		this.content = content;
		this.contentDescriptor = contentDescriptor;
	}

	@Override
	public ContentDescriptor getContentDescriptor() {
		return contentDescriptor;
	}

	@Override
	public void writeToStreamImpl(OutputStream os) throws IOException {
		os.write(content.getBytes(StandardCharsets.UTF_8));
	}

}