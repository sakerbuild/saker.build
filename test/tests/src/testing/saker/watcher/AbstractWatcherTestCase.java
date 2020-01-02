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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import saker.build.file.provider.FileEventListener;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.osnative.NativeLibs;
import saker.osnative.watcher.NativeWatcherService;
import testing.saker.SakerTestCase;
import testing.saker.api.TaskTestMetric;
import testing.saker.build.flag.TestFlag;
import testing.saker.build.tests.EnvironmentTestCase;

public abstract class AbstractWatcherTestCase extends SakerTestCase {
	private static final WatcherTestCaseTestMetric baseMainMetric = new WatcherTestCaseTestMetric();
	private static final MainWatcherMetric metric = new MainWatcherMetric(baseMainMetric);

	static {
		TestFlag.set(metric);
		try {
			NativeLibs.init(EnvironmentTestCase.getTestingBaseBuildDirectory().resolve("native.watcher.test.case"));
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public final void runTest(Map<String, String> parameters) throws Throwable {
		TestFlag.set(metric);

		assertEmpty(baseMainMetric.getWatchedPaths());
		assertNonNull(NativeWatcherService.getNativeImplementationClass());

		runTestImpl(parameters);

		setWatcherMetric(null);
	}

	protected abstract void runTestImpl(Map<String, String> parameters) throws Throwable;

	protected WatcherTestCaseTestMetric getWatcherMetric() {
		return metric.metric;
	}

	protected void setWatcherMetric(WatcherTestCaseTestMetric metric) {
		AbstractWatcherTestCase.metric.setMetric(ObjectUtils.nullDefault(metric, baseMainMetric));
	}

	protected Path getWatcherTestWorkingLocalDirectory() {
		Path dirpath = EnvironmentTestCase.getTestingBaseBuildDirectory()
				.resolve(getClass().getName().replace(".", "/"));
		return dirpath;
	}

	protected static class MainWatcherMetric implements TaskTestMetric {
		protected WatcherTestCaseTestMetric metric;

		public MainWatcherMetric(WatcherTestCaseTestMetric metric) {
			this.metric = metric;
		}

		public synchronized void setMetric(WatcherTestCaseTestMetric metric) {
			WatcherTestCaseTestMetric prevmetric = this.metric;
			assertEmpty(prevmetric.getWatchedPaths());
			assertEmpty(metric.getWatchedPaths());
			this.metric = metric;
			assertEmpty(prevmetric.getWatchedPaths());
		}

		public synchronized WatcherTestCaseTestMetric getMetric() {
			return metric;
		}

		@Override
		public boolean isNativeWatcherEnabled() {
			return true;
		}

		@Override
		public synchronized boolean isSubtreeWatchingEnabled() {
			return metric.isSubtreeWatchingEnabled();
		}

		@Override
		public synchronized void pathWatchingRegistered(Path path, Kind<?>[] events, Modifier... modifiers) {
			metric.pathWatchingRegistered(path, events, modifiers);
		}

		@Override
		public synchronized void pathWatchingCancelled(Path path) {
			metric.pathWatchingCancelled(path);
		}
	}

	protected static class WatcherTestCaseTestMetric implements TaskTestMetric {
		private Set<Path> watchedPaths = new ConcurrentSkipListSet<>();
		private boolean subtreeWatchingEnabled = true;

		public Set<Path> getWatchedPaths() {
			return ImmutableUtils.makeImmutableNavigableSet(watchedPaths);
		}

		public void setSubtreeWatchingEnabled(boolean subtreeWatchingEnabled) {
			this.subtreeWatchingEnabled = subtreeWatchingEnabled;
		}

		@Override
		public boolean isNativeWatcherEnabled() {
			return true;
		}

		@Override
		public boolean isSubtreeWatchingEnabled() {
			return subtreeWatchingEnabled;
		}

		@Override
		public synchronized void pathWatchingRegistered(Path path, Kind<?>[] events, Modifier... modifiers) {
			if (!watchedPaths.add(path)) {
				fail("Already present: " + path);
			}
		}

		@Override
		public synchronized void pathWatchingCancelled(Path path) {
			if (!watchedPaths.remove(path)) {
				fail("Not present: " + path);
			}
		}
	}

	protected static boolean waitForFileName(Set<String> modfilenames, String filename) throws InterruptedException {
		for (int i = 0; i < 10; i++) {
			if (modfilenames.contains(filename)) {
				return true;
			}
			Thread.sleep(100);
		}
		return false;
	}

	protected static final class FileNameAdderFileEventListener implements FileEventListener {
		private final Set<String> fileNamesSet;

		FileNameAdderFileEventListener(Set<String> modfilenames) {
			this.fileNamesSet = modfilenames;
		}

		@Override
		public void changed(String filename) {
			fileNamesSet.add(filename);
		}
	}

}
