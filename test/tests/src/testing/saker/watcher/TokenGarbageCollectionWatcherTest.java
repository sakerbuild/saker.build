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

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import saker.build.file.provider.FileEventListener.ListenerToken;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.file.provider.LocalFileProvider;
import testing.saker.SakerTest;

@SakerTest
public class TokenGarbageCollectionWatcherTest extends AbstractWatcherTestCase {

	@Override
	public void runTestImpl(Map<String, String> parameters) throws Throwable {
		WatcherTestCaseTestMetric metric = new WatcherTestCaseTestMetric();
		setWatcherMetric(metric);

		Path dirpath = getWatcherTestWorkingLocalDirectory();
		LocalFileProvider fp = LocalFileProvider.getInstance();
		fp.createDirectories(dirpath);
		fp.clearDirectoryRecursively(dirpath);
		Set<String> modfilenames = new ConcurrentSkipListSet<>();
		ListenerToken token = fp.addFileEventListener(dirpath, new FileNameAdderFileEventListener(modfilenames));
		ReferenceQueue<Object> refqueue = new ReferenceQueue<>();
		PhantomReference<?> phantomref = new PhantomReference<>(token, refqueue);

		fp.writeToFile(new UnsyncByteArrayInputStream("content".getBytes()), dirpath.resolve("file.txt"));

		waitForFileName(modfilenames, "file.txt");
		token = null;

		assertTrue(waitForReferenceEnqueue(refqueue));
		assertTrue(waitEmptyMetricWatchedPaths());

		//consume the reference so it's not gc'd prematurely
		System.out.println("TokenGarbageCollectionWatcherTest.runTest() " + phantomref);

		setWatcherMetric(null);
	}

	private boolean waitEmptyMetricWatchedPaths() throws InterruptedException {
		for (int i = 0; i < 10; i++) {
			if (getWatcherMetric().getWatchedPaths().isEmpty()) {
				return true;
			}
			System.gc();
			if (getWatcherMetric().getWatchedPaths().isEmpty()) {
				return true;
			}
			Thread.sleep(100);
			System.gc();
		}
		return false;
	}

	private static boolean waitForReferenceEnqueue(ReferenceQueue<Object> refqueue) throws Exception {
		for (int i = 0; i < 10; i++) {
			Reference<? extends Object> r = refqueue.poll();
			if (r != null) {
				return true;
			}
			System.gc();
			r = refqueue.poll();
			if (r != null) {
				return true;
			}
			Thread.sleep(100);
			System.gc();
		}
		return false;
	}

}
