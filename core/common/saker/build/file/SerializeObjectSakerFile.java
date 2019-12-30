package saker.build.file;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import saker.build.file.content.ContentDescriptor;
import saker.build.thirdparty.saker.util.io.StreamUtils;

public class SerializeObjectSakerFile extends SakerFileBase {
	private final Object obj;
	private final ContentDescriptor content;

	public SerializeObjectSakerFile(String name, Object obj, ContentDescriptor content) {
		super(name);
		this.obj = obj;
		this.content = content;
	}

	@Override
	public void writeToStreamImpl(OutputStream os) throws IOException {
		try (ObjectOutputStream oos = new ObjectOutputStream(StreamUtils.closeProtectedOutputStream(os))) {
			oos.writeObject(obj);
		}
	}

	@Override
	public int getEfficientOpeningMethods() {
		return OPENING_METHOD_WRITETOSTREAM;
	}

	@Override
	public ContentDescriptor getContentDescriptor() {
		return content;
	}

}
