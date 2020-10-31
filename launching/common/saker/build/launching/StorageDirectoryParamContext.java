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
package saker.build.launching;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.runtime.InvalidArgumentValueException;

public class StorageDirectoryParamContext {
	public static final String PARAM_NAME_STORAGE_DIRECTORY = "-storage-directory";
	public static final String PARAM_NAME_STORAGE_DIR = "-storage-dir";
	public static final String PARAM_NAME_SD = "-sd";

	/**
	 * <pre>
	 * Specifies the storage directory that the build environment can use
	 * to store its files and various data.
	 * </pre>
	 */
	@Parameter({ PARAM_NAME_STORAGE_DIRECTORY, PARAM_NAME_STORAGE_DIR, PARAM_NAME_SD })
	public SakerPath storageDirectory;

	public SakerPath getStorageDirectory() {
		return getStorageDirectoryOrDefault(this.storageDirectory);
	}

	public Path getStorageDirectoryPath() throws InvalidArgumentValueException {
		try {
			return LocalFileProvider.toRealPath(getStorageDirectory());
		} catch (InvalidPathFormatException | InvalidPathException e) {
			throw new InvalidArgumentValueException(e, PARAM_NAME_STORAGE_DIRECTORY);
		}
	}

	public static SakerPath getStorageDirectoryOrDefault(SakerPath storagedirectory) {
		if (storagedirectory == null) {
			storagedirectory = SakerPath.valueOf(SakerEnvironmentImpl.getDefaultStorageDirectory());
		}
		return LaunchingUtils.absolutize(storagedirectory);
	}
}
