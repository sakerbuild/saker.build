package testing.saker.build.tests;

import java.util.LinkedList;
import java.util.Objects;

public class ExecutionOrderer {
	private LinkedList<String> order = new LinkedList<>();

	public ExecutionOrderer() {
	}

	public ExecutionOrderer(ExecutionOrderer copy) {
		Objects.requireNonNull(copy, "copy");
		synchronized (copy) {
			//need to synchronize on copy as the elements are modified in a synchronized block
			this.order = new LinkedList<>(copy.order);
		}
	}

	public void addSection(String id) {
		Objects.requireNonNull(id, "id");
		this.order.add(id);
	}

	public synchronized void enter(String id) throws InterruptedException {
		while (true) {
			String first = order.peekFirst();
			if (first == null) {
				throw new IllegalArgumentException("No more sections.");
			}
			if (first.equals(id)) {
				order.pollFirst();
				this.notifyAll();
				return;
			}
			if (!order.contains(id)) {
				throw new IllegalArgumentException("No section found: " + id + " in " + order);
			}
			this.wait();
		}
	}

	public synchronized String getNextSection() {
		return order.peekFirst();
	}

	public boolean isAnySectionRemaining() {
		return !order.isEmpty();
	}

}
