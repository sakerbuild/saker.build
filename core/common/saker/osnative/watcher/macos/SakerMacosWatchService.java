package saker.osnative.watcher.macos;

import java.io.IOException;

import saker.osnative.watcher.base.SakerNativeWatchKey;
import saker.osnative.watcher.base.SakerWatchService;

public final class SakerMacosWatchService extends SakerWatchService {
	public SakerMacosWatchService() throws IOException {
		super(openNativeWatcher());
	}

	@Override
	protected int getUseKeyConfigFlag(int flags) {
		// for macOS, every file watcher is installed as a file tree watcher
		// as we don't want to create multiple keys for tree and non-tree watching,
		// we set the flag here so only one native key is created
		return flags | SakerNativeWatchKey.FLAG_QUERY_FILE_TREE;
	}

	@Override
	protected void closeWatcher(long nativeservice) {
		CloseWatcher_native(nativeservice);
	}

	@Override
	protected long createKeyObject(long nativeservice, String path, int flags, SakerNativeWatchKey key)
			throws IOException {
		return CreateKeyObject_native(nativeservice, path, flags, key);
	}

	@Override
	protected void closeKey(long nativeservice, long nativekey) {
		CloseKey_native(nativeservice, nativekey);
	}

	@Override
	protected void pollKey(long nativeservice, long nativekey) {
		PollKey_native(nativeservice, nativekey);
	}

	@Override
	protected boolean keyIsValid(long nativeservice, long nativekey) {
		return KeyIsValid_native(nativeservice, nativekey);
	}

	private static long openNativeWatcher() throws IOException {
		long ptr = OpenWatcher_native();
		if (ptr == 0) {
			throw new IOException("Failed to open watch service.");
		}
		return ptr;
	}

	//called by native code
	private static void notifyEvent(SakerNativeWatchKey key, int eventflag, String path) {
		dispatchEvent(key, eventflag, path);
	}

	private static native long OpenWatcher_native() throws IOException;

	private static native void CloseWatcher_native(long nativeservice);

	private static native long CreateKeyObject_native(long nativeservice, String path, int flags,
			SakerNativeWatchKey key) throws IOException;

	private static native void CloseKey_native(long nativeservice, long nativekey);

	private static native void PollKey_native(long nativeservice, long nativekey);

	private static native boolean KeyIsValid_native(long nativeservice, long nativekey);
}
