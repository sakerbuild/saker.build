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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class SakerPathTest extends SakerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		testRootOnlys();
		testAbsolutes();
		testRelatives();
		testOrdering();
		testPathNormalization();
		testIllegalPaths();
		testRelativize();
		testFileName();
		testResolutions();
		testHasNonForwardRelatives();
	}

	private static void testHasNonForwardRelatives() {
		assertTrue(SakerPathFiles.hasNonForwardRelative(pathSet("..")));
		assertTrue(SakerPathFiles.hasNonForwardRelative(pathSet("/asd")));
		assertTrue(SakerPathFiles.hasNonForwardRelative(pathSet("rel/path", "/asd")));
		assertTrue(SakerPathFiles.hasNonForwardRelative(pathSet("rel/path", "../asd")));
		assertTrue(SakerPathFiles.hasNonForwardRelative(pathSet("rel/path", "../asd", "/abs")));
		assertTrue(SakerPathFiles.hasNonForwardRelative(pathSet("../asd", "/abs")));
		assertTrue(SakerPathFiles.hasNonForwardRelative(pathSet("../asd")));

		assertFalse(SakerPathFiles.hasNonForwardRelative(pathSet()));
		assertFalse(SakerPathFiles.hasNonForwardRelative(pathSet("rel/path", "asd")));
		assertFalse(SakerPathFiles.hasNonForwardRelative(pathSet("rel/path")));

		assertTrue(SakerPathFiles.isOnlyForwardRelatives(pathSet()));
		assertTrue(SakerPathFiles.isOnlyForwardRelatives(pathSet("asd")));
		assertTrue(SakerPathFiles.isOnlyForwardRelatives(pathSet("asd", "rel/path")));

		assertFalse(SakerPathFiles.isOnlyForwardRelatives(pathSet("/asd")));
		assertFalse(SakerPathFiles.isOnlyForwardRelatives(pathSet("../asd")));
		assertFalse(SakerPathFiles.isOnlyForwardRelatives(pathSet("../asd", "/asd")));
		assertFalse(SakerPathFiles.isOnlyForwardRelatives(pathSet("rel/path", "../asd", "/asd")));
		assertFalse(SakerPathFiles.isOnlyForwardRelatives(pathSet("rel/path", "/asd")));
	}

	private static void testResolutions() {
		assertException(InvalidPathFormatException.class, () -> path("/home").resolve(path("../../file")));
		assertException(InvalidPathFormatException.class, () -> path("/").resolveSibling("wd:"));
	}

	private static NavigableSet<SakerPath> pathSet(String... path) {
		NavigableSet<SakerPath> result = new TreeSet<>();
		for (String p : path) {
			result.add(SakerPath.valueOf(p));
		}
		return result;
	}

	private static SakerPath path(String path) {
		SakerPath result = SakerPath.valueOf(path);
		createdPathAssertions(result);
		return result;
	}

	private static void createdPathAssertions(SakerPath result) throws AssertionError {
		assertTrue(result.compareTo(result.nextSiblingPathInNaturalOrder()) < 0);
		assertTrue(result.compareTo(result.nextSubPathInNaturalOrder()) < 0);
	}

	private static SakerPath createPath(Path path) {
		SakerPath result = SakerPath.valueOf(path);
		createdPathAssertions(result);
		return result;
	}

	private static void testFileName() throws Throwable {
		assertEquals(path("..").getFileName(), null);
		assertEquals(path("../file").getFileName(), "file");
		assertEquals(path("/file").getFileName(), "file");
		assertEquals(path("/").getFileName(), null);
		assertEquals(path("c:").getFileName(), null);
	}

	private static void testRelativize() throws Throwable {
		SakerPath homeuser = path("/home/user");
		SakerPath home = path("/home");
		SakerPath homejohn = path("/home/john");
		SakerPath winusers = path("c:/users");

		SakerPath dirrel = path("dir/rel");
		SakerPath dir = path("dir");
		SakerPath dirother = path("dir/other");
		SakerPath dotdir = path("../dir");
		SakerPath dotdotdir = path("../../dir");

		assertRelativize(homeuser, homeuser, SakerPath.EMPTY);
		assertRelativize(homeuser, home, path(".."));
		assertRelativize(home, homeuser, path("user"));
		assertRelativize(homejohn, homeuser, path("../user"));
		assertException(IllegalArgumentException.class, () -> winusers.relativize(home));

		assertRelativize(dirrel, dirrel, SakerPath.EMPTY);
		assertRelativize(dir, dirrel, path("rel"));
		assertRelativize(dirrel, dir, path(".."));
		assertRelativize(dirrel, dirother, path("../other"));
		assertRelativize(dir, dotdir, path("../../dir"));
		assertRelativize(dotdir, dotdotdir, path("../../dir"));
		assertRelativize(dotdotdir, dotdotdir, SakerPath.EMPTY);
		assertException(IllegalArgumentException.class, () -> dotdir.relativize(dir));
	}

	private static void assertRelativize(SakerPath first, SakerPath second, SakerPath expected) {
		assertEquals(first.relativize(second), expected);
		assertEquals(first.resolve(expected), second);
		assertTrue(first.resolve(first.relativize(second)).equals(second));
	}

	private static void testIllegalPaths() throws Throwable {
		assertException(IllegalArgumentException.class, () -> SakerPath.valueOf(":"));
		assertException(IllegalArgumentException.class, () -> SakerPath.valueOf(":/"));
		assertException(IllegalArgumentException.class, () -> SakerPath.valueOf(":/:"));
		assertException(IllegalArgumentException.class, () -> SakerPath.valueOf("aasd:asd"));
		assertException(IllegalArgumentException.class, () -> SakerPath.valueOf("aasd:asd/"));
		assertException(IllegalArgumentException.class, () -> SakerPath.valueOf("/:"));
		assertException(IllegalArgumentException.class, () -> SakerPath.valueOf("/asd:"));
		assertException(IllegalArgumentException.class, () -> SakerPath.valueOf("/:/asd"));
		assertException(IllegalArgumentException.class, () -> SakerPath.valueOf("a b:/"));
		assertException(IllegalArgumentException.class, () -> SakerPath.valueOf("a_b:/"));
		assertException(IllegalArgumentException.class, () -> SakerPath.valueOf("_:/"));

		assertException(IllegalArgumentException.class,
				() -> SakerPath.valueOf("some/path").append(SakerPath.valueOf("..")));
	}

	private static void testPathNormalization() throws Throwable {
		Path base = Paths.get(".").toAbsolutePath().normalize();
		SakerPath basempath = createPath(base);

		assertEquals(createPath(base.resolve("")), basempath.resolve(""));
		assertEquals(createPath(base.resolve(".")), basempath);
		assertEquals(createPath(base.resolve("dir/..")), basempath);
	}

	private static void testOrdering() {
		SakerPath dir = path("C:/dir");
		SakerPath dirsub = path("C:/dir/sub");
		SakerPath dirafter = dirsub.nextSiblingPathInNaturalOrder();

		TreeSet<SakerPath> set = ObjectUtils.newTreeSet(dir, dirsub, dirafter);

		assertEquals(new ArrayList<>(set), ImmutableUtils.asUnmodifiableArrayList(dir, dirsub, dirafter));
		assertEquals(set.subSet(dir, false, dirafter, false), ObjectUtils.newTreeSet(dirsub));
	}

	private static void testAbsolutes() {
		SakerPath userdir = path("/home/user");
		SakerPath homedir = path("/home");
		SakerPath seconduserdir = path("/home/seconduser");
		SakerPath root = path("/");
		path("c:");
		path("C:/");

		assertEquals(userdir.subPath(homedir.getRoot(), 0, 1), homedir);
		assertEquals(userdir.subPath(null, 0, 1), path("home"));

		assertEquals(userdir.resolveSibling(seconduserdir.getFileName()), seconduserdir);

		assertEquals(userdir.getParent(), homedir);
		assertEquals(homedir.getParent(), root);
		assertEquals(root.getParent(), null);

		assertEquals(userdir.resolve(".."), homedir);
		assertEquals(userdir.resolve("./.."), homedir);
		assertEquals(userdir.resolve("../."), homedir);
		assertEquals(userdir.resolve("../.."), root);
		assertEquals(userdir.resolve(".././.."), root);
		assertEquals(userdir.resolve("."), userdir);
		assertEquals(userdir.resolve("./."), userdir);

		assertEquals(root.relativize(homedir), path("home"));
		assertEquals(root.relativize(userdir), path("home/user"));
		assertEquals(userdir.relativize(homedir), path(".."));
		assertEquals(userdir.relativize(seconduserdir), path("../seconduser"));

		assertEquals(userdir, SakerPath.valueOf(homedir, "user"));
		assertEquals(userdir, SakerPath.valueOf(root, "home/user"));
	}

	private static void testRelatives() {
		SakerPath userdir = path("home/user");
		SakerPath homedir = path("home");
		SakerPath seconduserdir = path("home/seconduser");
		SakerPath root = path("/");

		assertEquals(homedir.getParent(), path(""));
		assertEquals(userdir.getParent(), homedir);
		assertEquals(seconduserdir.getParent(), homedir);
		assertEquals(homedir.resolve(".."), path(""));
		assertEquals(userdir.resolve(".."), homedir);
		assertEquals(seconduserdir.resolve(".."), homedir);
		assertEquals(homedir.resolve("..", ".."), path(".."));

		assertEquals(userdir.resolveSibling(seconduserdir.getFileName()), seconduserdir);

		assertEquals(homedir.resolve(userdir), path("home/home/user"));
		assertEquals(root.resolve(userdir), path("/home/user"));

		assertTrue(path("").isForwardRelative());
		assertTrue(path(".").isForwardRelative());
		assertTrue(path("dir/../otherdir").isForwardRelative());
		assertTrue(path("x/y").isForwardRelative());
		assertTrue(path("x/..").isForwardRelative());

		assertTrue(!path("../x").isForwardRelative());
		assertTrue(!path("..").isForwardRelative());
		assertTrue(!path("a/../..").isForwardRelative());
		assertTrue(!path("../a/..").isForwardRelative());

	}

	private static void testRootOnlys() {
		assertRoots(path("/"), "/");
		assertRoots(path("///"), "/");
		assertRoots(path("\\"), "/");
		assertRoots(path("\\\\\\"), "/");

		assertRoots(path("C:/"), "c:");
		assertRoots(path("C:///"), "c:");

		assertRoots(path("C:\\"), "c:");
		assertRoots(path("C:\\\\\\"), "c:");

		assertRoots(path("MULTI:/"), "multi:");
		assertRoots(path("MULTI:///"), "multi:");
	}

	private static void assertRoots(SakerPath croot, String rootstr) {
		assertEquals(croot.getRoot(), rootstr);
		assertEquals(croot.getNameCount(), 0);
		assertEquals(croot.getFileName(), null);
	}

}
