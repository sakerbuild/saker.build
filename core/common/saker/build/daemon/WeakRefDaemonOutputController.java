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
package saker.build.daemon;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.AsyncOutputStream;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.ref.WeakReferencedToken;
import saker.build.util.config.JVMSynchronizationObjects;

public class WeakRefDaemonOutputController implements DaemonOutputController {
	private static final class WeakRefStreamToken extends WeakReferencedToken<AsyncOutputStream>
			implements StreamToken {
		private final WeakRefMultiplexOutputStream owner;

		public WeakRefStreamToken(WeakRefMultiplexOutputStream owner, WeakReference<AsyncOutputStream> weakref) {
			super(weakref);
			this.owner = owner;
		}

		@Override
		public void removeStream() {
			owner.removeStream(objectWeakRef);
			objectStrongRef.exit();
		}
	}

	private static final class CompoundToken implements StreamToken {
		private Collection<StreamToken> tokens;

		public CompoundToken(Collection<StreamToken> tokens) {
			this.tokens = tokens;
		}

		public CompoundToken(StreamToken... tokens) {
			this(ImmutableUtils.asUnmodifiableArrayList(tokens));
		}

		@Override
		public void removeStream() {
			for (StreamToken t : tokens) {
				try {
					t.removeStream();
				} catch (Exception e) {
				}
			}
		}
	}

	private static class WeakRefMultiplexOutputStream extends OutputStream {
		private final Collection<WeakReference<AsyncOutputStream>> streams = ConcurrentHashMap.newKeySet();
		private final ReentrantLock lock = new ReentrantLock();

		public StreamToken addStream(OutputStream os) {
			lock.lock();
			try {
				//keep strong reference on stack until it is actually added
				AsyncOutputStream thestream = new AsyncOutputStream(os);
				WeakReference<AsyncOutputStream> weakref = new WeakReference<>(thestream);
				streams.add(weakref);
				return new WeakRefStreamToken(this, weakref);
			} finally {
				lock.unlock();
			}
		}

		private void removeStream(WeakReference<? extends AsyncOutputStream> streamref) {
			streams.remove(streamref);
		}

		@Override
		public void write(int b) {
			if (streams.isEmpty()) {
				return;
			}
			lock.lock();
			try {
				for (Iterator<? extends Reference<? extends OutputStream>> it = streams.iterator(); it.hasNext();) {
					Reference<? extends OutputStream> s = it.next();
					OutputStream os = s.get();
					if (os == null) {
						it.remove();
						continue;
					}
					try {
						os.write(b);
					} catch (Exception e) {
						it.remove();
					}
				}
			} finally {
				lock.unlock();
			}
		}

		@Override
		public void write(byte[] b, int off, int len) {
			if (streams.isEmpty()) {
				return;
			}
			lock.lock();
			try {
				byte[] copy = null;
				for (Iterator<? extends Reference<? extends OutputStream>> it = streams.iterator(); it.hasNext();) {
					Reference<? extends OutputStream> s = it.next();
					OutputStream os = s.get();
					if (os == null) {
						it.remove();
						continue;
					}
					try {
						if (copy == null) {
							copy = Arrays.copyOfRange(b, off, off + len);
						}
						os.write(copy);
					} catch (Exception e) {
						it.remove();
					}
				}
			} finally {
				lock.unlock();
			}
		}

		@Override
		public void flush() {
			if (streams.isEmpty()) {
				return;
			}
			lock.lock();
			try {
				for (Iterator<? extends Reference<? extends OutputStream>> it = streams.iterator(); it.hasNext();) {
					Reference<? extends OutputStream> s = it.next();
					OutputStream os = s.get();
					if (os == null) {
						it.remove();
						continue;
					}
					try {
						os.flush();
					} catch (Exception e) {
						it.remove();
					}
				}
			} finally {
				lock.unlock();
			}
		}

		@Override
		public void close() {
			if (streams.isEmpty()) {
				return;
			}
			//the async streams will shut themselves down
			streams.clear();
		}

	}

	private final WeakRefMultiplexOutputStream stdOutStream = new WeakRefMultiplexOutputStream();
	private final WeakRefMultiplexOutputStream stdErrStream = new WeakRefMultiplexOutputStream();

	public WeakRefDaemonOutputController() {
	}

	public OutputStream getStdOutStream() {
		return stdOutStream;
	}

	public OutputStream getStdErrStream() {
		return stdErrStream;
	}

	public StreamToken replaceStandardIOAndAttach() {
		PrintStream out;
		PrintStream err;
		PrintStream noutstream = new PrintStream(stdOutStream);
		PrintStream nerrstream = new PrintStream(stdErrStream);
		synchronized (JVMSynchronizationObjects.getStandardIOLock()) {
			out = System.out;
			err = System.err;
			System.setOut(noutstream);
			System.setErr(nerrstream);
		}
		return new CompoundToken(addStandardOutput(ByteSink.valueOf(out)), addStandardError(ByteSink.valueOf(err)));
	}

	public void replaceStandardIO() {
		PrintStream noutstream = new PrintStream(stdOutStream);
		PrintStream nerrstream = new PrintStream(stdErrStream);
		synchronized (JVMSynchronizationObjects.getStandardIOLock()) {
			System.setOut(noutstream);
			System.setErr(nerrstream);
		}
	}

	@Override
	public StreamToken addStandardError(ByteSink stderr) {
		return stdErrStream.addStream(ByteSink.toOutputStream(stderr));
	}

	@Override
	public StreamToken addStandardOutput(ByteSink stdout) {
		return stdOutStream.addStream(ByteSink.toOutputStream(stdout));
	}
}