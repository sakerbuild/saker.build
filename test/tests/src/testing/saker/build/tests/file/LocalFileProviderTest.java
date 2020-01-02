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
package testing.saker.build.tests.file;

import java.io.IOException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Map;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerFileProvider;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.EnvironmentTestCase;

@SakerTest
public class LocalFileProviderTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		Path workingdir = EnvironmentTestCase.getTestingBaseWorkingDirectory()
				.resolve(getClass().getName().replace('.', '/'));
		Path empty = workingdir.resolve("empty");
		LocalFileProvider fp = LocalFileProvider.getInstance();
		fp.createDirectories(empty);

		assertTrue(fp.getFileAttributes(empty).isDirectory());
		assertEmpty(fp.getDirectoryEntriesRecursively(empty));

		assertMap(fp.getDirectoryEntries(workingdir.resolve("filed"))).containsKey("file.txt").noRemaining();
		assertMap(fp.getDirectoryEntries(workingdir.resolve("filed").resolve("x").resolve("..")))
				.containsKey("file.txt").noRemaining();
		assertMap(fp.getDirectoryEntriesRecursively(workingdir.resolve("filed")))
				.containsKey(SakerPath.valueOf("file.txt")).noRemaining();
		assertMap(fp.getDirectoryEntriesRecursively(workingdir.resolve("filed").resolve("x").resolve("..")))
				.containsKey(SakerPath.valueOf("file.txt")).noRemaining();

		assertException(NotDirectoryException.class,
				() -> fp.getDirectoryEntries(workingdir.resolve("filed/file.txt")));
		assertException(NotDirectoryException.class,
				() -> fp.getDirectoryEntriesRecursively(workingdir.resolve("filed/file.txt")));

		Path dir = workingdir.resolve("dir");
		fp.createDirectories(dir);
		fp.clearDirectoryRecursively(dir);
		assertTrue(fp.getFileAttributes(dir).isDirectory());
		assertEmpty(fp.getDirectoryEntries(dir));

		Path dirfile = dir.resolve("file");
		fp.writeToFile(new UnsyncByteArrayInputStream("123".getBytes()), dirfile);
		assertTrue(fp.getFileAttributes(dirfile).isRegularFile());

		Path dirfilesub = dirfile.resolve("sub");
		assertException(IOException.class, () -> fp.ensureWriteRequest(dirfilesub, FileEntry.TYPE_DIRECTORY,
				SakerFileProvider.OPERATION_FLAG_NONE));

		fp.ensureWriteRequest(dirfilesub, FileEntry.TYPE_DIRECTORY,
				SakerFileProvider.OPERATION_FLAG_DELETE_INTERMEDIATE_FILES);
		assertTrue(fp.getFileAttributes(dirfile).isDirectory());
	}

}
