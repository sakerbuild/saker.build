package testing.saker.watcher;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import saker.build.file.provider.FileEventListener.ListenerToken;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.file.provider.LocalFileProvider;
import testing.saker.SakerTest;

@SakerTest
public class MultiWatcherTest extends AbstractWatcherTestCase {

	@Override
	public void runTestImpl(Map<String, String> parameters) throws Throwable {
		WatcherTestCaseTestMetric metric = new WatcherTestCaseTestMetric();
		metric.setSubtreeWatchingEnabled(false);
		setWatcherMetric(metric);

		Path dirpath = getWatcherTestWorkingLocalDirectory();
		LocalFileProvider fp = LocalFileProvider.getInstance();
		fp.createDirectories(dirpath);
		fp.clearDirectoryRecursively(dirpath);
		Set<String> modfilenames1 = new ConcurrentSkipListSet<>();
		ListenerToken token1 = fp.addFileEventListener(dirpath, new FileNameAdderFileEventListener(modfilenames1));
		Set<String> modfilenames2 = new ConcurrentSkipListSet<>();
		ListenerToken token2 = fp.addFileEventListener(dirpath, new FileNameAdderFileEventListener(modfilenames2));
		fp.writeToFile(new UnsyncByteArrayInputStream("content".getBytes()), dirpath.resolve("file.txt"));

		waitForFileName(modfilenames1, "file.txt");
		assertNotEmpty(metric.getWatchedPaths());
		waitForFileName(modfilenames2, "file.txt");

		token1.removeListener();
		assertNotEmpty(metric.getWatchedPaths());
		token2.removeListener();

		//ensure that the watchers are uninstalled if there are no more listeners
		assertEmpty(metric.getWatchedPaths());

		setWatcherMetric(null);
	}

}
