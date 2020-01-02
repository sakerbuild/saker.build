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
