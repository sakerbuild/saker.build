package saker.build.daemon;

import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.ref.Token;

public interface DaemonOutputController {
	public interface StreamToken extends Token {
		public void removeStream();
	}

	public StreamToken addStandardOutput(ByteSink stdout);

	public StreamToken addStandardError(ByteSink stderr);
}
