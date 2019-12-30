package saker.build.thirdparty.saker.rmi.connection;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

class RequestHandler implements Closeable {
	public static final int NO_REQUEST_ID = 0;

	public static final class Request implements Closeable {
		private static final class State {
			public boolean closed;
			public Object response;
			public Thread waitingThread;

			public State() {
			}

			public State setResponse(State s, Object response) {
				if (s.response != null) {
					throw new IllegalStateException("Previous response wasn't processed yet.");
				}
				this.closed = s.closed;
				this.response = response;
				this.waitingThread = s.waitingThread;
				return this;
			}

			public State close(State s) {
				this.response = s.response;
				this.waitingThread = s.waitingThread;
				this.closed = true;
				return this;
			}

			public State takeResponse(State s, Thread currentthread) {
				this.response = null;
				this.closed = s.closed;
				if (s.response != null) {
					this.waitingThread = null;
				} else {
					this.waitingThread = currentthread;
				}
				return this;
			}

		}

		private final RequestHandler owner;
		private final int requestId;

		public Request(RequestHandler owner, int requestId) {
			this.owner = owner;
			this.requestId = requestId;
		}

		public int getRequestId() {
			return requestId;
		}

		@SuppressWarnings("unchecked")
		public <T> T waitInstanceOfResponse(Class<T> clazz) throws IOException {
			Object resp = waitResponse();
			if (!clazz.isInstance(resp)) {
				throw new IOException("Invalid response for request (" + resp.getClass() + "): " + resp
						+ " expected instance of: " + clazz);
			}
			return (T) resp;
		}

		@SuppressWarnings("unchecked")
		public <T> T waitInstanceOfResponseInterruptible(Class<T> clazz) throws IOException, InterruptedException {
			Object resp = waitResponseInterruptible();
			if (!clazz.isInstance(resp)) {
				throw new IOException("Invalid response for request (" + resp.getClass() + "): " + resp
						+ " expected instance of: " + clazz);
			}
			return (T) resp;
		}

		private static final AtomicReferenceFieldUpdater<RequestHandler.Request, State> ARFU_state = AtomicReferenceFieldUpdater
				.newUpdater(RequestHandler.Request.class, State.class, "state");

		private static final State INITIAL_STATE = new State();

		@SuppressWarnings("unused")
		private volatile State state = INITIAL_STATE;

		public Object waitResponseInterruptible() throws IOException, InterruptedException {
			//waiting thread has been set, and we have no response
			Thread currentthread = Thread.currentThread();
			while (true) {
				State ns = new State();
				State prevs = ARFU_state.getAndUpdate(this, s -> ns.takeResponse(s, currentthread));
				if (prevs.response != null) {
					return prevs.response;
				}
				if (prevs.closed) {
					throw new IOException("Failed to receive response for request ID: " + requestId + ", closed.");
				}
				if (Thread.interrupted()) {
					throw new InterruptedException();
				}
				LockSupport.park();
			}
		}

		public Object waitResponse() throws IOException {
			//waiting thread has been set, and we have no response
			boolean interrupted = false;
			Thread currentthread = Thread.currentThread();
			try {
				while (true) {
					State ns = new State();
					State prevs = ARFU_state.getAndUpdate(this, s -> ns.takeResponse(s, currentthread));
					if (prevs.response != null) {
						return prevs.response;
					}
					if (prevs.closed) {
						throw new IOException("Failed to receive response for request ID: " + requestId + ", closed.");
					}
					LockSupport.park();
					if (Thread.interrupted()) {
						interrupted = true;
					}
				}
			} finally {
				if (interrupted) {
					currentthread.interrupt();
				}
			}
		}

		@Override
		public void close() {
			wakeClose();
			owner.requests.remove(requestId, this);
		}

		private void wakeClose() {
			State prevs = ARFU_state.getAndUpdate(this, new State()::close);
			wakeUpState(prevs);
		}

		private void setResponse(Object response) {
			State ns = new State();
			State prevs = ARFU_state.getAndUpdate(this, s -> ns.setResponse(s, response));
			wakeUpState(prevs);
		}

		private static void wakeUpState(State prevs) {
			LockSupport.unpark(prevs.waitingThread);
		}

		@Override
		public int hashCode() {
			return requestId;
		}
	}

	private static final AtomicIntegerFieldUpdater<RequestHandler> ARFU_requestIdCounter = AtomicIntegerFieldUpdater
			.newUpdater(RequestHandler.class, "requestIdCounter");
	@SuppressWarnings("unused")
	private volatile int requestIdCounter;

	protected final ConcurrentNavigableMap<Integer, Request> requests = new ConcurrentSkipListMap<>();

	public RequestHandler() {
	}

	public Request newRequest() {
		int id = ARFU_requestIdCounter.incrementAndGet(this);
		Request result = new Request(this, id);
		requests.put(id, result);
		return result;
	}

	public void addResponse(int requestid, Object response) {
		Request req = requests.get(requestid);
		if (req != null) {
			req.setResponse(response);
		}
	}

	@Override
	public void close() throws IOException {
		for (Iterator<Request> it = requests.values().iterator(); it.hasNext();) {
			Request r = it.next();
			r.wakeClose();
			it.remove();
		}
	}
}
