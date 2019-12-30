package saker.build.runtime.execution;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import saker.build.runtime.execution.ExecutionContextImpl.StandardIOLock;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;

final class StandardIOPromptHandler implements BuildUserPromptHandler {
	private ExecutionContextImpl executionContext;
	private ByteSink out;
	private ByteSource in;

	public StandardIOPromptHandler(ExecutionContextImpl executionContext, ByteSink out, ByteSource in) {
		this.executionContext = executionContext;
		this.out = out;
		this.in = in;
	}

	@Override
	@SuppressWarnings("try")
	public int prompt(String title, String message, List<String> options) {
		if (options.isEmpty()) {
			throw new IllegalArgumentException("Empty options");
		}
		try (StandardIOLock iolock = executionContext.acquireStdIOLock()) {
			if (title != null) {
				out.write(ByteArrayRegion.wrap(title.getBytes(StandardCharsets.UTF_8)));
				out.write('\n');
			}
			if (message != null) {
				out.write(ByteArrayRegion.wrap(message.getBytes(StandardCharsets.UTF_8)));
				out.write('\n');
			}
			int i = 1;
			for (String opt : options) {
				out.write(ByteArrayRegion.wrap((i + ") " + opt + "\n").getBytes(StandardCharsets.UTF_8)));
				++i;
			}
			int response = readInt(in);
			if (response > 0) {
				return response - 1;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
		}
		return -1;
	}

	private static int readInt(ByteSource in) throws IOException {
		try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
			while (baos.size() == 0) {
				int read = -1;
				for (; (read = in.read()) >= 0;) {
					if (read >= '0' && read <= '9') {
						baos.write(read);
					} else {
						if (baos.size() > 0) {
							break;
						}
					}
				}
				if (read < 0) {
					throw new EOFException("End of input.");
				}
				try {
					return Integer.parseInt(baos.toString());
				} catch (NumberFormatException ignored) {
					// continue loop
					baos.reset();
				}
			}
		}
		return -1;
	}
}