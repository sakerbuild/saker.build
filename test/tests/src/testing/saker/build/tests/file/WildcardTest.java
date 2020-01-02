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

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.MemoryFileProvider;

@SakerTest
public class WildcardTest extends SakerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		testIncludes();
		testFinishable();

		MemoryFileProvider files = new MemoryFileProvider(setOf("wd:", "bd:", "/"), UUID.randomUUID());

		SakerPath wd = SakerPath.valueOf("wd:");
		SakerPath bd = SakerPath.valueOf("bd:");
		SakerPath slash = SakerPath.valueOf("/");

		SakerPath wdroottxt = SakerPath.valueOf("wd:/root.txt");
		SakerPath wdfolder = SakerPath.valueOf("wd:/folder");
		SakerPath wdfolderroottxt = SakerPath.valueOf("wd:/folder/folderroot.txt");
		SakerPath wdsubfolder = SakerPath.valueOf("wd:/folder/subfolder");
		SakerPath wdsubfolderroottxt = SakerPath.valueOf("wd:/folder/subfolder/subfolderroot.txt");

		SakerPath sslashtxt = SakerPath.valueOf("/slash.txt");

		SakerPath bdroottxt = SakerPath.valueOf("bd:/root.txt");

		files.putFile(wdroottxt, "root");
		files.putFile(bdroottxt, "root");
		files.putFile(wdfolderroottxt, "folderroot");
		files.putFile(wdsubfolderroottxt, "subfolderroot");

		files.putFile(sslashtxt, "stxt");

		assertEquals(WildcardPath.valueOf("*").getFiles(files).keySet(), setOf());
		assertEquals(WildcardPath.valueOf("**").getFiles(files).keySet(), setOf());
		assertEquals(WildcardPath.valueOf("*:/").getFiles(files).keySet(), setOf(wd, bd));
		assertEquals(WildcardPath.valueOf("wd:/").getFiles(files).keySet(), setOf(wd));
		assertEquals(WildcardPath.valueOf("wd:/*").getFiles(files).keySet(), setOf(wdfolder, wdroottxt));
		assertEquals(WildcardPath.valueOf("*d:/").getFiles(files).keySet(), setOf(wd, bd));
		assertEquals(WildcardPath.valueOf("*d:/*").getFiles(files).keySet(), setOf(wdfolder, wdroottxt, bdroottxt));
		assertEquals(WildcardPath.valueOf("*:/*").getFiles(files).keySet(), setOf(wdfolder, wdroottxt, bdroottxt));
		assertEquals(WildcardPath.valueOf("/").getFiles(files).keySet(), setOf(slash));
		assertEquals(WildcardPath.valueOf("/**").getFiles(files).keySet(), setOf(slash, sslashtxt));
		assertEquals(WildcardPath.valueOf("/**/*").getFiles(files).keySet(), setOf(sslashtxt));

		assertEquals(WildcardPath.valueOf("**/*.txt").getFiles(files, wd).keySet(),
				setOf(wdroottxt, wdfolderroottxt, wdsubfolderroottxt));
		assertEquals(WildcardPath.valueOf("**/*folder*/**").getFiles(files, wd).keySet(),
				setOf(wdfolder, wdsubfolder, wdfolderroottxt, wdsubfolderroottxt));
		assertEquals(WildcardPath.valueOf("**/*folder/*").getFiles(files, wd).keySet(),
				setOf(wdsubfolder, wdfolderroottxt, wdsubfolderroottxt));
		assertEquals(WildcardPath.valueOf("*/*.txt").getFiles(files, wd).keySet(), setOf(wdfolderroottxt));
		assertEquals(WildcardPath.valueOf("*d:/**/*.txt").getFiles(files, wd).keySet(),
				setOf(bdroottxt, wdroottxt, wdfolderroottxt, wdsubfolderroottxt));
		assertEquals(WildcardPath.valueOf("folder/**").getFiles(files, wd).keySet(),
				setOf(wdfolder, wdfolderroottxt, wdsubfolder, wdsubfolderroottxt));

		assertEquals(WildcardPath.valueOf("").getFiles(files, wd).keySet(), setOf(wd));

		MemoryFileProvider fp = new MemoryFileProvider(Collections.singleton("/"), UUID.randomUUID());
		fp.putFile(SakerPath.valueOf("/file.txt"), ObjectUtils.EMPTY_BYTE_ARRAY);
		fp.putFile(SakerPath.valueOf("/file2.txt"), ObjectUtils.EMPTY_BYTE_ARRAY);
		fp.putFile(SakerPath.valueOf("/dir/file.txt"), ObjectUtils.EMPTY_BYTE_ARRAY);
		fp.putFile(SakerPath.valueOf("/dir/file2.txt"), ObjectUtils.EMPTY_BYTE_ARRAY);
		assertFalse(
				SakerPathFiles.hasRelativePath(WildcardPath.valueOf("file.txt").getFiles(fp, SakerPath.PATH_SLASH)));
		assertFalse(
				SakerPathFiles.hasRelativePath(WildcardPath.valueOf("file*.txt").getFiles(fp, SakerPath.PATH_SLASH)));
		assertFalse(
				SakerPathFiles.hasRelativePath(WildcardPath.valueOf("/file.txt").getFiles(fp, SakerPath.PATH_SLASH)));
		assertFalse(SakerPathFiles
				.hasRelativePath(WildcardPath.valueOf("/**/file*.txt").getFiles(fp, SakerPath.PATH_SLASH)));

	}

	private static void assertIncludes(String wildcard, String path) throws AssertionError {
		assertTrue(WildcardPath.valueOf(wildcard).includes(path));
		assertTrue(WildcardPath.valueOf(wildcard).includes(SakerPath.valueOf(path)));
	}

	private static void assertFinishable(String wildcard, String path) throws AssertionError {
		assertTrue(WildcardPath.valueOf(wildcard).finishable(SakerPath.valueOf(path)));
	}

	private static void assertNotFinishable(String wildcard, String path) throws AssertionError {
		assertFalse(WildcardPath.valueOf(wildcard).finishable(SakerPath.valueOf(path)));
	}

	private static void testIncludes() throws AssertionError {
		assertIncludes("test/*Test", "test/TestFileAccessTest");
		assertIncludes("test/*Test", "test/MainTest");
		assertIncludes("test/*Test", "test/MainTestNameTest");

		assertFalse(WildcardPath.valueOf("test/*Test").includes("test/MainTestXXX"));
		assertFalse(WildcardPath.valueOf("test/*Test").includes("test/MainTestXXXTestYYY"));

		assertIncludes("test/Test", "test/Test");
		assertIncludes("/test/Test", "/test/Test");
		assertIncludes("/", "/");
		assertFalse(WildcardPath.valueOf("/").includes(SakerPath.valueOf("/home")));
		assertFalse(WildcardPath.valueOf("/test/Test").includes(SakerPath.valueOf("test/Test")));

		assertIncludes("**", "test/Test");
		assertIncludes("**", "/test/Test");
		assertIncludes("**", "/");
		assertIncludes("**", "bd:/");
		assertIncludes("**", "bd:/folder");

		assertIncludes("dir/**", "dir/a");
		assertIncludes("dir/**", "dir");
		assertFalse(WildcardPath.valueOf("dir/**/*").includes(SakerPath.valueOf("dir")));

		assertIncludes("**/*folder*/**", "folder");

		assertIncludes("*abcy*", "abc_x_abcy");
		assertIncludes("x*y", "xy");
		assertIncludes("x*y", "xay");
		assertIncludes("x*y", "xyy");
		assertIncludes("x*y", "xyyxy");
		assertFalse(WildcardPath.valueOf("x*y").includes(SakerPath.valueOf("xyyx")));

		assertIncludes("*:/folder", "a:/folder");
		assertFalse(WildcardPath.valueOf("*:/folder").includes(SakerPath.valueOf("/folder")));
	}

	private static void testFinishable() throws AssertionError {
		assertFinishable("a", "");
		assertNotFinishable("a", "a");
		assertNotFinishable("a", "x");

		assertFinishable("x/a", "x");
		assertNotFinishable("x/a", "a");

		assertFinishable("*/a", "a");
		assertFinishable("*/a", "x");
		assertNotFinishable("*/a", "a/a");
		assertNotFinishable("*/a", "a/b");
		assertNotFinishable("*/a", "x/a");

		assertFinishable("a/*", "");
		assertFinishable("a/*", "a");
		assertNotFinishable("a/*", "a/b");

		assertFinishable("a/**", "a");
		assertFinishable("a/**", "a/b");
		assertFinishable("a/**", "a/b/c");
		assertNotFinishable("a/**", "x");

		assertFinishable("**/a", "");
		assertFinishable("**/a", "x");
		assertFinishable("**/a", "a");
		assertFinishable("**/a", "a/b/c/a");
		assertFinishable("**/a", "a/a");
		assertFinishable("**/a", "a/b/c");

		assertFinishable("a/**/b", "");
		assertFinishable("a/**/b", "a");
		assertFinishable("a/**/b", "a/b");
		assertFinishable("a/**/b", "a/c/b");
		assertFinishable("a/**/b", "a/b/c");
		assertNotFinishable("a/**/b", "x");
		assertNotFinishable("a/**/b", "x/a");
		assertNotFinishable("a/**/b", "x/a/b");

		assertFinishable("/", "");
		assertNotFinishable("/", "/");
		assertNotFinishable("/", "c:/");
		assertNotFinishable("/", "x");
	}
}
