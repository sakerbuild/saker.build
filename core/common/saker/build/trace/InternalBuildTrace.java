package saker.build.trace;

import java.io.Closeable;
import java.io.IOException;

public interface InternalBuildTrace extends Closeable {
	public static final InternalBuildTrace NULL_INSTANCE = new InternalBuildTrace() {
	};

	public static InternalBuildTrace current() {
		return InternalBuildTraceImpl.current();
	}

	@Override
	public default void close() throws IOException {
	}
}
