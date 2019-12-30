package testing.saker.build.tests.file;

import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class SakerSubCollectionUtilsTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		TreeMap<SakerPath, Object> map = new TreeMap<>();
		map.put(SakerPath.valueOf("/"), 0);
		map.put(SakerPath.valueOf("/temo"), 0);
		map.put(SakerPath.valueOf("/temo/sub"), 0);
		map.put(SakerPath.valueOf("/temp"), 0);
		map.put(SakerPath.valueOf("/temp/sub"), 0);
		map.put(SakerPath.valueOf("/tempacc"), 0);
		map.put(SakerPath.valueOf("/temr"), 0);
		map.put(SakerPath.valueOf("/temr/sub"), 0);
		test(SakerPath.valueOf("/temp"), map);

		map.clear();
		map.put(SakerPath.valueOf("c:"), 0);
		map.put(SakerPath.valueOf("c:/dip"), 0);
		map.put(SakerPath.valueOf("c:/dip/sub"), 0);
		map.put(SakerPath.valueOf("c:/dir"), 0);
		map.put(SakerPath.valueOf("c:/dir/sub"), 0);
		map.put(SakerPath.valueOf("c:/dis"), 0);
		map.put(SakerPath.valueOf("c:/dis/sub"), 0);
		test(SakerPath.valueOf("c:/dir"), map);
	}

	private static void test(SakerPath dir, TreeMap<SakerPath, Object> map) {
		NavigableSet<SakerPath> res = SakerPathFiles.getPathSubMapDirectoryChildren(map, dir, true).navigableKeySet();
		for (SakerPath s : res) {
			if (!s.startsWith(dir)) {
				throw new AssertionError(s + " - " + dir + " in " + map.navigableKeySet());
			}
		}
		if (map.containsKey(dir) && !res.contains(dir)) {
			throw new AssertionError("Dir is not included: " + dir + " in " + map + " for " + res);
		}

		res = SakerPathFiles.getPathSubMapDirectoryChildren(map, dir, false).navigableKeySet();
		for (SakerPath s : res) {
			if (!s.startsWith(dir)) {
				throw new AssertionError(s + " - " + dir + " in " + map.navigableKeySet());
			}
		}
		if (map.containsKey(dir) && res.contains(dir)) {
			throw new AssertionError("Dir is included: " + dir + " in " + map + " for " + res);
		}

		res = SakerPathFiles.getPathSubSetDirectoryChildren(map.navigableKeySet(), dir, true);
		for (SakerPath s : res) {
			if (!s.startsWith(dir)) {
				throw new AssertionError(s + " - " + dir + " in " + map.navigableKeySet());
			}
		}
		if (map.containsKey(dir) && !res.contains(dir)) {
			throw new AssertionError("Dir is not included: " + dir + " in " + map + " for " + res);
		}

		res = SakerPathFiles.getPathSubSetDirectoryChildren(map.navigableKeySet(), dir, false);
		for (SakerPath s : res) {
			if (!s.startsWith(dir)) {
				throw new AssertionError(s + " - " + dir + " in " + map.navigableKeySet());
			}
		}
		if (map.containsKey(dir) && res.contains(dir)) {
			throw new AssertionError("Dir is included: " + dir + " in " + map + " for " + res);
		}
	}

}
