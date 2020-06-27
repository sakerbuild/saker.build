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
		try {
			while (true) {
				String first = order.peekFirst();
				if (first == null) {
					throw new IllegalArgumentException("No more sections.");
				}
				if (first.equals(id)) {
					System.out.println("Reached: " + id);
					order.pollFirst();
					this.notifyAll();
					return;
				}
				if (!order.contains(id)) {
					throw new IllegalArgumentException("No section found: " + id + " in " + order);
				}
				this.wait();
			}
		} catch (InterruptedException e) {
			throw new InterruptedException("Interrupted while waiting for: " + id + " in " + order);
		}
	}

	public synchronized String getNextSection() {
		return order.peekFirst();
	}

	public boolean isAnySectionRemaining() {
		return !order.isEmpty();
	}

	@Override
	public String toString() {
		return "ExecutionOrderer[" + order + "]";
	}

}
