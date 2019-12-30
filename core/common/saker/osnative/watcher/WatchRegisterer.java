package saker.osnative.watcher;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

@FunctionalInterface
public interface WatchRegisterer {
	public static final WatchEvent.Modifier[] EMPTY_MODIFIER_ARRAY = {};

	public WatchKey register(Path path, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
			throws IOException;

	public default WatchKey register(Path path, WatchEvent.Kind<?>... events) throws IOException {
		return register(path, events, EMPTY_MODIFIER_ARRAY);
	}

	public static WatchRegisterer of(WatchService service) {
		return (p, e, m) -> p.register(service, e, m);
	}
}