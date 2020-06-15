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
package saker.build.task.utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.AnyTaskExecutionEnvironmentSelector;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public final class MultiTaskExecutionEnvironmentSelector implements TaskExecutionEnvironmentSelector, Externalizable {
	private static final long serialVersionUID = 1L;

	private Set<TaskExecutionEnvironmentSelector> selectors;

	/**
	 * For {@link Externalizable}.
	 */
	public MultiTaskExecutionEnvironmentSelector() {
	}

	public MultiTaskExecutionEnvironmentSelector(Set<TaskExecutionEnvironmentSelector> selectors) {
		this.selectors = selectors;
	}

	public static TaskExecutionEnvironmentSelector get(Iterable<? extends TaskExecutionEnvironmentSelector> selectors)
			throws NullPointerException {
		Objects.requireNonNull(selectors, "environment selectors");
		return getImplFromSelectors(selectors);
	}

	public static TaskExecutionEnvironmentSelector get(TaskExecutionEnvironmentSelector... selectors)
			throws NullPointerException {
		Objects.requireNonNull(selectors, "environment selectors");
		if (selectors.length == 0) {
			return AnyTaskExecutionEnvironmentSelector.INSTANCE;
		}
		if (selectors.length == 1) {
			return Objects.requireNonNull(selectors[0], "environment selector");
		}
		return getImplFromSelectors(ImmutableUtils.asUnmodifiableArrayList(selectors));
	}

	private static TaskExecutionEnvironmentSelector getImplFromSelectors(
			Iterable<? extends TaskExecutionEnvironmentSelector> selectors) {
		Iterator<? extends TaskExecutionEnvironmentSelector> it = selectors.iterator();
		if (!it.hasNext()) {
			return AnyTaskExecutionEnvironmentSelector.INSTANCE;
		}
		TaskExecutionEnvironmentSelector s = it.next();
		Objects.requireNonNull(s, "environment selector");
		if (!it.hasNext()) {
			return s;
		}
		Set<TaskExecutionEnvironmentSelector> filtered = new LinkedHashSet<>();
		filtered.add(s);
		do {
			s = it.next();
			Objects.requireNonNull(s, "environment selector");
			if (AnyTaskExecutionEnvironmentSelector.INSTANCE.equals(s)) {
				continue;
			}
			filtered.add(s);
		} while (it.hasNext());
		if (filtered.isEmpty()) {
			return AnyTaskExecutionEnvironmentSelector.INSTANCE;
		}
		if (filtered.size() == 1) {
			return filtered.iterator().next();
		}
		return new MultiTaskExecutionEnvironmentSelector(filtered);
	}

	@Override
	public EnvironmentSelectionResult isSuitableExecutionEnvironment(SakerEnvironment environment) {
		Object[] selectorarray = selectors.toArray();
		for (int i = 0; i < selectorarray.length; i++) {
			EnvironmentSelectionResult selres = ((TaskExecutionEnvironmentSelector) selectorarray[i])
					.isSuitableExecutionEnvironment(environment);
			selectorarray[i] = selres;
		}
		//all elements in the array are EnvironmentSelectionResult so the cast should be safe
		@SuppressWarnings("unchecked")
		EnvironmentSelectionResult result = new EnvironmentSelectionResult(
				(Iterable<? extends EnvironmentSelectionResult>) (Iterable<?>) ImmutableUtils
						.asUnmodifiableArrayList(selectorarray));
		return result;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, selectors);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		selectors = SerialUtils.readExternalImmutableLinkedHashSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((selectors == null) ? 0 : selectors.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MultiTaskExecutionEnvironmentSelector other = (MultiTaskExecutionEnvironmentSelector) obj;
		if (selectors == null) {
			if (other.selectors != null)
				return false;
		} else if (!selectors.equals(other.selectors))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (selectors != null ? "selectors=" + selectors : "") + "]";
	}

}
