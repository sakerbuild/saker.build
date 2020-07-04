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
package testing.saker.watcher;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import saker.build.file.provider.FileEventListener.ListenerToken;
import saker.build.file.provider.LocalFileProvider;
import testing.saker.SakerTest;

@SakerTest
public class DirCreateWatcherTest extends AbstractWatcherTestCase {

	@Override
	public void runTestImpl(Map<String, String> parameters) throws Throwable {
		WatcherTestCaseTestMetric metric = new WatcherTestCaseTestMetric();
		metric.setSubtreeWatchingEnabled(false);
		setWatcherMetric(metric);

		Path dirpath = getWatcherTestWorkingLocalDirectory();
		LocalFileProvider fp = LocalFileProvider.getInstance();
		fp.createDirectories(dirpath);
		fp.clearDirectoryRecursively(dirpath);
		Set<String> modfilenames = new ConcurrentSkipListSet<>();
		ListenerToken token = fp.addFileEventListener(dirpath, new FileNameAdderFileEventListener(modfilenames));
		try {
			fp.createDirectories(dirpath.resolve("mydir/subdir"));

			assertTrue(waitForFileName(modfilenames, "mydir"), () -> modfilenames.toString());
			//subdir not present
			assertEquals(modfilenames, setOf("mydir"));
		} finally {
			token.removeListener();
		}

		//ensure that the watchers are uninstalled if there are no more listeners
		assertEmpty(metric.getWatchedPaths());

		setWatcherMetric(null);
	}

}
