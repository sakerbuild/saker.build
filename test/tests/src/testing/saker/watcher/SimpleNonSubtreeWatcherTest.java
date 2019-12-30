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
public class SimpleNonSubtreeWatcherTest extends AbstractWatcherTestCase {

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
		fp.writeToFile(new UnsyncByteArrayInputStream("content".getBytes()), dirpath.resolve("file.txt"));

		waitForFileName(modfilenames, "file.txt");

		token.removeListener();

		//ensure that the watchers are uninstalled if there are no more listeners
		assertEmpty(metric.getWatchedPaths());

		setWatcherMetric(null);
	}

}
