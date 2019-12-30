package saker.build.thirdparty.saker.util.io;

public class ProcessDestroyer {
	private ProcessDestroyer() {
		throw new UnsupportedOperationException();
	}

	public static void destroyProcessAndPossiblyChildren(Process p) {
		ProcessHandle handle = p.toHandle();
		destroyProcessAndPossiblyChildren(handle);
	}

	private static void destroyProcessAndPossiblyChildren(ProcessHandle handle) {
		destroyHandle(handle);

		handle.children().forEach(ProcessDestroyer::destroyProcessAndPossiblyChildren);
	}

	private static void destroyHandle(ProcessHandle handle) {
		if (!handle.isAlive()) {
			return;
		}
		boolean destroyed = false;
		if (handle.supportsNormalTermination()) {
			destroyed = handle.destroy();
			if (!destroyed) {
				handle.destroyForcibly();
			}
		} else {
			handle.destroyForcibly();
		}
	}
}
