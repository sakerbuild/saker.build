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
package saker.build.thirdparty.saker.rmi.connection;

import java.io.Closeable;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMIResourceUnavailableException;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ObjectUtils;

class RequestHandler {
	public static final int NO_REQUEST_ID = 0;

	public static final class Request implements Closeable {
		private static final class State {
			public Function<? super Request, ? extends RMIRuntimeException> closedExceptionCreator;
			public Object response;
			public Thread waitingThread;

			public State() {
			}

			public State setResponse(State s, Object response) {
				if (s.response != null) {
					throw new IllegalStateException("Previous response wasn't processed yet.");
				}
				this.closedExceptionCreator = s.closedExceptionCreator;
				this.response = response;
				this.waitingThread = s.waitingThread;
				return this;
			}

			public State close(State s,
					Function<? super Request, ? extends RMIRuntimeException> closedExceptionCreator) {
				this.response = s.response;
				this.waitingThread = s.waitingThread;
				this.closedExceptionCreator = closedExceptionCreator;
				return this;
			}

			public State takeResponse(State s, Thread currentthread) {
				this.response = null;
				this.closedExceptionCreator = s.closedExceptionCreator;
				if (s.response != null) {
					this.waitingThread = null;
				} else {
					this.waitingThread = currentthread;
				}
				return this;
			}

			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("State[closedExceptionCreator=");
				builder.append(closedExceptionCreator);
				builder.append(", response=");
				builder.append(response);
				builder.append(", waitingThread=");
				builder.append(waitingThread);
				builder.append("]");
				return builder.toString();
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
		public <T> T waitInstanceOfResponse(Class<T> clazz) throws RMICallFailedException {
			Object resp = waitResponse();
			if (!clazz.isInstance(resp)) {
				throw new RMICallFailedException("Invalid response for request (" + ObjectUtils.classNameOf(resp)
						+ "): " + resp + " expected instance of: " + clazz);
			}
			return (T) resp;
		}

		@SuppressWarnings("unchecked")
		public <T> T waitInstanceOfResponseInterruptible(Class<T> clazz)
				throws RMICallFailedException, InterruptedException {
			Object resp = waitResponseInterruptible();
			if (!clazz.isInstance(resp)) {
				throw new RMICallFailedException("Invalid response for request (" + ObjectUtils.classNameOf(resp)
						+ "): " + resp + " expected instance of: " + clazz);
			}
			return (T) resp;
		}

		private static final AtomicReferenceFieldUpdater<RequestHandler.Request, State> ARFU_state = AtomicReferenceFieldUpdater
				.newUpdater(RequestHandler.Request.class, State.class, "state");

		private static final State INITIAL_STATE = new State();

		@SuppressWarnings("unused")
		private volatile State state = INITIAL_STATE;

		public Object waitResponseInterruptible() throws InterruptedException {
			//waiting thread has been set, and we have no response
			Thread currentthread = Thread.currentThread();
			while (true) {
				State ns = new State();
				State prevs = ARFU_state.getAndUpdate(this, s -> ns.takeResponse(s, currentthread));
				if (prevs.response != null) {
					return prevs.response;
				}
				if (prevs.closedExceptionCreator != null) {
					throw prevs.closedExceptionCreator.apply(this);
				}
				if (Thread.interrupted()) {
					throw new InterruptedException();
				}
				LockSupport.park();
			}
		}

		public Object waitResponse() {
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
					if (prevs.closedExceptionCreator != null) {
						throw prevs.closedExceptionCreator.apply(this);
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
			wakeClose(Request::createExceptionClosedHandler);
			owner.requests.remove(requestId, this);
		}

		private void wakeClose(Function<? super Request, ? extends RMIRuntimeException> closedExceptionCreator) {
			State nstate = new State();
			State prevs;
			while (true) {
				prevs = this.state;
				nstate.close(prevs, closedExceptionCreator);
				if (ARFU_state.compareAndSet(this, prevs, nstate)) {
					break;
				}
				//try again
			}
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

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("Request[");
			builder.append("owner=");
			builder.append(owner);
			builder.append(", requestId=");
			builder.append(requestId);
			builder.append(", state=");
			builder.append(state);
			builder.append("]");
			return builder.toString();
		}

		public static RMIRuntimeException createExceptionClosedHandler(Request handler) {
			return new RMIResourceUnavailableException(
					"Failed to receive response for request ID: " + handler.requestId + ", closed.");
		}

		public static RMIRuntimeException createExceptionClosedByCaller(Request handler) {
			return new RMICallFailedException(
					"Failed to receive response for request ID: " + handler.requestId + ", closed by caller.");
		}
	}

	protected final ConcurrentNavigableMap<Integer, Request> requests = new ConcurrentSkipListMap<>();
	private final RMIConnection connection;

	public RequestHandler(RMIConnection connection) {
		this.connection = connection;
	}

	public Request newRequest() {
		int id = connection.getNextRequestId();
		Request result = new Request(this, id);
		requests.put(id, result);
		return result;
	}

	public boolean addResponse(int requestid, Object response) {
		Request req = requests.get(requestid);
		if (req == null) {
			return false;
		}
		req.setResponse(response);
		return true;
	}

	public void close(Function<? super Request, ? extends RMIRuntimeException> closedExceptionCreator) {
		while (true) {
			Entry<Integer, Request> entry = requests.pollFirstEntry();
			if (entry == null) {
				break;
			}
			entry.getValue().wakeClose(closedExceptionCreator);
		}
	}
}
