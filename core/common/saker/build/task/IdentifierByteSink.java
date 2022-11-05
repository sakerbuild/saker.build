package saker.build.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.Lock;

import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;

class IdentifierByteSink<S extends ByteSink> implements ByteSink {
	/**
	 * The real byte sing to write data to, guarded by {@link #realSinkFlushingLock}.
	 */
	protected final S realSink;
	/**
	 * Lock for accessing {@link #realSink}.
	 */
	protected final Lock realSinkFlushingLock;

	private boolean needsIdentifier = true;

	private volatile String stringIdentifier = "";
	private volatile ByteArrayRegion identifier;

	private byte lastLineEnd = 0;

	/**
	 * Lock for generally manipulating <code>this</code>.
	 */
	protected final Lock accessLock = ThreadUtils.newExclusiveLock();

	public IdentifierByteSink(S realSink, Lock streamFlushingLock) {
		this.realSink = realSink;
		this.realSinkFlushingLock = streamFlushingLock;
	}

	public void setIdentifier(String identifier) {
		final Lock accesslock = this.accessLock;
		accesslock.lock();
		try {
			if (identifier == null) {
				this.identifier = null;
				this.stringIdentifier = "";
			} else {
				String enclosedid = "[" + identifier + "]";
				this.stringIdentifier = enclosedid;
				this.identifier = ByteArrayRegion.wrap(enclosedid.getBytes(StandardCharsets.UTF_8));
			}
			finishLastLineLocked();
		} finally {
			accesslock.unlock();
		}
	}

	/**
	 * Gets the string representation of the current output identifier.
	 * <p>
	 * Returns empty string if none, or an identifier enclosed in brackets if present.
	 * 
	 * @return The current identifier.
	 */
	public String getStringIdentifier() {
		return stringIdentifier;
	}

	public void finishLastLine() {
		final Lock accesslock = this.accessLock;
		accesslock.lock();
		try {
			finishLastLineLocked();
		} finally {
			accesslock.unlock();
		}
	}

	/**
	 * Locked on {@link #accessLock}.
	 */
	protected void finishLastLineLocked() {
		if (lastLineEnd == 0 && !needsIdentifier) {
			try {
				final Lock flushlock = this.realSinkFlushingLock;
				flushlock.lock();
				try {
					realSink.write('\n');
				} finally {
					flushlock.unlock();
				}
				lastLineEnd = '\n';
				needsIdentifier = true;
			} catch (IOException e) {
				//ignore exception
			}
		}
	}

	/**
	 * Locked on {@link #accessLock} and on {@link #realSinkFlushingLock} as well.
	 */
	protected void finishLastLineLockedSinkLocked() {
		if (lastLineEnd == 0 && !needsIdentifier) {
			try {
				realSink.write('\n');
				lastLineEnd = '\n';
				needsIdentifier = true;
			} catch (IOException e) {
				//ignore exception
			}
		}
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException {
		if (buf.isEmpty()) {
			return;
		}
		int off = buf.getOffset();
		int len = buf.getLength();
		final Lock accesslock = this.accessLock;
		accesslock.lock();
		try {
			ByteArrayRegion id = identifier;
			if (id == null) {
				final Lock flushlock = this.realSinkFlushingLock;
				flushlock.lock();
				try {
					realSink.write(buf);
				} finally {
					flushlock.unlock();
				}
				byte last = buf.get(off + len - 1);
				needsIdentifier = last == '\n' || last == '\r';
				lastLineEnd = needsIdentifier ? last : 0;
				writtenBytes();
				return;
			}
			//there is an identifier

			byte[] array = buf.getArray();
			final int end = off + len;
			int lastwriteend = off;
			byte b = 0;
			for (int i = off; i < end; i++) {
				b = array[i];
				if (b == '\r' || b == '\n') {
					if (lastLineEnd == 0) {
						//no line ending before, we might expect the other character
						lastLineEnd = b;
					} else {
						//there was a previous line ending character, so a line is finished now
						//the needsidentifier flag stays true, as we need an identifier after a line ending
						int writecount;
						if (b == lastLineEnd) {
							//new line sequences like CR CR ... or LF LF ...
							//do not include this line ending char in the writing
							writecount = i - lastwriteend;
						} else {
							//the other kind of character received among CR LF
							//the line is finished
							lastLineEnd = 0;
							//write all including this line ending char
							writecount = i - lastwriteend + 1;
						}

						if (writecount > 0) {
							final Lock flushlock = this.realSinkFlushingLock;
							flushlock.lock();
							try {
								if (needsIdentifier) {
									realSink.write(id);
								} else {
									needsIdentifier = true;
								}
								realSink.write(ByteArrayRegion.wrap(array, lastwriteend, writecount));
							} finally {
								flushlock.unlock();
							}
							lastwriteend += writecount;
						}
					}
				} else if (lastLineEnd != 0) {
					//new char received after single char line ending
					//write out the new line characters, and set that we need the identifier
					final Lock flushlock = this.realSinkFlushingLock;
					flushlock.lock();
					try {
						if (needsIdentifier) {
							realSink.write(id);
						}
						int outc = i - lastwriteend;
						needsIdentifier = outc > 0;
						realSink.write(ByteArrayRegion.wrap(array, lastwriteend, outc));
					} finally {
						flushlock.unlock();
					}
					lastwriteend = i;

					lastLineEnd = 0;
				}
			}
			if (lastwriteend < end) {
				final Lock flushlock = this.realSinkFlushingLock;
				flushlock.lock();
				try {
					if (needsIdentifier) {
						realSink.write(id);
					}
					realSink.write(ByteArrayRegion.wrap(array, lastwriteend, end - lastwriteend));
				} finally {
					flushlock.unlock();
				}
				needsIdentifier = b == '\n' || b == '\r';
				lastLineEnd = needsIdentifier ? b : 0;
			}
			writtenBytes();
		} finally {
			accesslock.unlock();
		}
	}

	/**
	 * Callback for subclasses when bytes were written to the sink.
	 * <p>
	 * Called when locked on {@link #accessLock}.
	 */
	protected void writtenBytes() {
	}
}