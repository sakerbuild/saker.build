package testing.saker.build.tests.tasks.file;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.SakerFileBase;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.PosixFilePermissionsDelegateContentDescriptor;
import saker.build.file.content.SerializableContentDescriptor;

public class PosixAttributedStringSakerFile extends SakerFileBase {
	private Set<PosixFilePermission> permissions;
	private String content;

	public PosixAttributedStringSakerFile(String name, Set<PosixFilePermission> permissions, String content)
			throws NullPointerException, InvalidPathFormatException {
		super(name);
		this.permissions = permissions;
		this.content = content;
	}

	@Override
	public void writeToStreamImpl(OutputStream os) throws IOException {
		os.write(content.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public ContentDescriptor getContentDescriptor() {
		return PosixFilePermissionsDelegateContentDescriptor.get(new SerializableContentDescriptor(content),
				permissions);
	}

	@Override
	public Set<PosixFilePermission> getPosixFilePermissions() {
		return permissions;
	}
}