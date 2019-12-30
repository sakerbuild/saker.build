package saker.build.file;

import saker.build.file.content.ContentDatabase;
import saker.build.file.content.ContentDatabase.ContentHandle;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.NullContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.file.provider.SakerFileProvider;

public class ProviderPathSakerFile extends SakerPathFileBase {
	protected final ContentHandle contentHandle;

	public ProviderPathSakerFile(String name, ProviderHolderPathKey pathkey, ContentHandle contentHandle) {
		super(name, pathkey);
		this.contentHandle = contentHandle;
	}

	public ProviderPathSakerFile(ProviderHolderPathKey pathkey, ContentHandle contentHandle) {
		super(pathkey.getPath().getFileName(), pathkey);
		this.contentHandle = contentHandle;
	}

	public ProviderPathSakerFile(String name, ProviderHolderPathKey pathkey, ContentDatabase contentdb) {
		super(name, pathkey);
		this.contentHandle = contentdb.getContentHandle(pathkey);
	}

	public ProviderPathSakerFile(ProviderHolderPathKey pathkey, ContentDatabase contentdb) {
		super(pathkey.getPath().getFileName(), pathkey);
		this.contentHandle = contentdb.getContentHandle(pathkey);
	}

	public ProviderPathSakerFile(String name, SakerFileProvider fileProvider, SakerPath realPath,
			ContentHandle contentHandle) {
		super(name, fileProvider, realPath);
		this.contentHandle = contentHandle;
	}

	public ProviderPathSakerFile(SakerFileProvider fileProvider, SakerPath realPath, ContentHandle contentHandle) {
		this(realPath.getFileName(), fileProvider, realPath, contentHandle);
	}

	public ProviderPathSakerFile(SakerFileProvider fileProvider, SakerPath realPath, ContentDatabase contentdatabase) {
		this(fileProvider, realPath,
				contentdatabase.getContentHandle(new SimpleProviderHolderPathKey(fileProvider, realPath)));
	}

	public ProviderPathSakerFile(String name, SakerFileProvider fileProvider, SakerPath realPath,
			ContentDatabase contentdatabase) {
		this(name, fileProvider, realPath,
				contentdatabase.getContentHandle(new SimpleProviderHolderPathKey(fileProvider, realPath)));
	}

	@Override
	public ContentDescriptor getContentDescriptor() {
		ContentDescriptor result = contentHandle.getContent();
		if (result == null) {
			return NullContentDescriptor.getInstance();
		}
		return result;
	}

	@Override
	ContentDatabase getContentDatabase() {
		return contentHandle.getContentDatabase();
	}
}
