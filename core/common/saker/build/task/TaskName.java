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
package saker.build.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;

import saker.build.runtime.repository.BuildRepository;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Represents a name that can be used to identify and locate tasks from external sources.
 * <p>
 * A task name consists of a lowercase name string and arbitrary amount of lowercase qualifiers. <br>
 * Name parts are in the format matching the following regular expression: <br>
 * 
 * <pre>
 * [a-z_0-9]+(\.[a-z_0-9]+)*
 * </pre>
 * 
 * With plain words: A dot separated non-empty parts consisting of lowercase latin ABC characters, numbers or
 * underscore. Qualifiers parts are in the format matching the following regular expression: <br>
 * 
 * <pre>
 * [a-z0-9_.]+
 * </pre>
 * 
 * With plain words: A non-empty string consisting of lowercase latin ABC characters, numbers, underscore or dot.
 * <p>
 * Qualifiers are stored in a sorted manner ordered by their natural order. Task names are comparable.
 * <p>
 * Each task name have an unique string representation in the following format:
 * 
 * <pre>
 * task.name[-qualifier]*
 * </pre>
 * 
 * Examples: <br>
 * 
 * <pre>
 * simple.task.name
 * task.with.qualifier-q1
 * multi.qualifiers-q1-q2-q3
 * </pre>
 * 
 * @see BuildRepository#lookupTask(TaskName)
 */
public final class TaskName implements Comparable<TaskName>, Externalizable {
	private static final long serialVersionUID = 1L;
	private static final Pattern PATTERN_DASH_SPLIT = Pattern.compile("[-]+");
	private static final Pattern PATTERN_QUALIFIER = Pattern.compile("[a-zA-Z0-9_.]+");
	private static final Pattern PATTERN_NAME_PART = Pattern.compile("[a-zA-Z_0-9]+(\\.[a-zA-Z_0-9]+)*");
	/**
	 * A regular expression for matching valid task name representations.
	 * <p>
	 * The name part is checked in a case-insensitive manner.
	 */
	public static final Pattern PATTERN_TASK_NAME = Pattern
			.compile("[a-zA-Z_0-9]+(\\.[a-zA-Z_0-9]+)*(-[a-zA-Z0-9_.]+)*");

	private String name;
	private NavigableSet<String> qualifiers;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskName() {
	}

	private TaskName(String name, NavigableSet<String> qualifiers) {
		this.name = name;
		this.qualifiers = qualifiers;
	}

	/**
	 * Converts the parameter string to a task name.
	 * <p>
	 * See {@link TaskName} documentation for the expected format. The input will be converted to lowercase.
	 * <p>
	 * Multiple consecutive <code>'-'</code> will be collapsed, empty qualifiers will not be included.
	 * 
	 * @param input
	 *            The input string.
	 * @return The parsed task name.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the input does not match the task name format.
	 */
	public static TaskName valueOf(String input) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(input, "input");
		if (!PATTERN_TASK_NAME.matcher(input).matches()) {
			throw new IllegalArgumentException("Invalid input: \"" + input + "\"");
		}
		input = input.toLowerCase(Locale.ENGLISH);
		if (input.contains("-")) {
			String[] split = PATTERN_DASH_SPLIT.split(input);
			return new TaskName(split[0], ImmutableUtils
					.makeImmutableNavigableSet(ImmutableUtils.unmodifiableArrayList(split, 1, split.length)));
		}
		return new TaskName(input, Collections.emptyNavigableSet());
	}

	/**
	 * Converts the parameters to a task name.
	 * <p>
	 * The input will be converted to lowercase.
	 * <p>
	 * The parameters are checked if they match the expected format.
	 * 
	 * @param name
	 *            The name of the task.
	 * @param qualifiers
	 *            The qualifiers of the task. Duplicates will be removed.
	 * @return The parsed task name.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the input does not match the task name format.
	 */
	public static TaskName valueOf(String name, Collection<String> qualifiers)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(name, "name");
		if (ObjectUtils.isNullOrEmpty(qualifiers)) {
			return valueOf(name);
		}
		if (isInvalidInput(name, qualifiers.iterator())) {
			throw new IllegalArgumentException("Invalid input: \"" + toString(name, qualifiers) + "\"");
		}
		name = name.toLowerCase(Locale.ENGLISH);

		NavigableSet<String> lowerqualifiers = new TreeSet<>();
		for (String q : qualifiers) {
			lowerqualifiers.add(q.toLowerCase(Locale.ENGLISH));
		}
		return new TaskName(name, ImmutableUtils.unmodifiableNavigableSet(lowerqualifiers));
	}

	/**
	 * Converts the parameters to a task name.
	 * <p>
	 * The input will be converted to lowercase.
	 * <p>
	 * The parameters are checked if they match the expected format.
	 * 
	 * @param name
	 *            The name of the task.
	 * @param qualifiers
	 *            The qualifiers of the task. Duplicates will be removed.
	 * @return The parsed task name.
	 * @throws IllegalArgumentException
	 *             If the input does not match the task name format.
	 */
	public static TaskName valueOf(String name, String... qualifiers) throws IllegalArgumentException {
		return valueOf(name, ImmutableUtils.asUnmodifiableArrayList(qualifiers));
	}

	/**
	 * Gets the name for this task name.
	 * 
	 * @return The name.
	 */
	public final String getName() {
		return name;
	}

	/**
	 * Gets the qualifiers of this task name.
	 * 
	 * @return The qualifiers. (unmodifiable)
	 */
	public final NavigableSet<String> getTaskQualifiers() {
		return qualifiers;
	}

	/**
	 * Checks if the task name has any qualifiers defined for it.
	 * 
	 * @return <code>true</code> if there are at least one qualifier.
	 * @see #getTaskQualifiers()
	 */
	public boolean hasAnyQualifiers() {
		return !this.qualifiers.isEmpty();
	}

	/**
	 * Gets a task name that has the same {@linkplain #getName() name} as this, but has no
	 * {@linkplain #getTaskQualifiers() qualifiers}.
	 * <p>
	 * If this task name already has no qualifiers, it is simply returned.
	 * 
	 * @return The task name without qualifiers.
	 */
	public TaskName withoutQualifiers() {
		if (this.qualifiers.isEmpty()) {
			return this;
		}
		return new TaskName(this.name, Collections.emptyNavigableSet());
	}

	/**
	 * Converts this task name to a string representation.
	 * <p>
	 * The return value passed to {@link #valueOf(String)} will be {@link #equals(Object) equal} to <code>this</code>.
	 * <p>
	 * See {@link TaskName} documentation for the resulting format.
	 * 
	 * @return The task name converted to a string.
	 */
	@Override
	public String toString() {
		return toString(name, qualifiers);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((qualifiers == null) ? 0 : qualifiers.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	/**
	 * Check if this task name equals the parameter.
	 * <p>
	 * Two task names equal if they have the same name and have the same qualifiers.
	 * <p>
	 * This method only returns <code>true</code> if {@link #compareTo(TaskName)} returns 0.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TaskName other = (TaskName) obj;
		return compareTo(other) == 0;
	}

	/**
	 * Compares this task name to the parameter.
	 * <p>
	 * Task names are ordered by name first, then each qualifier is compared to the corresponding parameter qualifiers
	 * at the same position.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public int compareTo(TaskName o) {
		int mcmp = name.compareTo(o.name);
		if (mcmp != 0) {
			return mcmp;
		}
		return ObjectUtils.compareOrderedSets(qualifiers, o.qualifiers);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(name);
		SerialUtils.writeExternalCollection(out, qualifiers);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		name = (String) in.readObject();
		qualifiers = SerialUtils.readExternalSortedImmutableNavigableSet(in);
	}

	private static String toString(String name, Collection<String> qualifiers) {
		if (qualifiers.isEmpty()) {
			return name;
		}
		return name + "-" + StringUtils.toStringJoin("-", qualifiers);
	}

	private static boolean isInvalidInput(String name, Iterator<String> qualifiers) {
		if (!PATTERN_NAME_PART.matcher(name).matches()) {
			return true;
		}
		while (qualifiers.hasNext()) {
			String q = qualifiers.next();
			if (!isValidQualifier(q)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the argument string is a valid qualifier for a task name.
	 * 
	 * @param qualifier
	 *            The qualifier to check.
	 * @return <code>true</code> if the qualifier has a valid format described by this class.
	 */
	public static boolean isValidQualifier(String qualifier) {
		return qualifier != null && PATTERN_QUALIFIER.matcher(qualifier).matches();
	}

	/**
	 * Gets a submap for the argument that contains only the entries that have the same {@linkplain #getName() name} as
	 * the parameter task name.
	 * <p>
	 * The result map is a submap that only contains entries for which the {@linkplain #getName() name} equal to the
	 * name of the argument name.
	 * 
	 * @param map
	 *            The map.
	 * @param taskname
	 *            The task name to filter the entries for.
	 * @return The submap.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static <V> NavigableMap<TaskName, V> getTaskNameSubMap(NavigableMap<TaskName, V> map, TaskName taskname)
			throws NullPointerException {
		Objects.requireNonNull(map, "map");
		Objects.requireNonNull(taskname, "task name");
		TaskName first = taskname.withoutQualifiers();
		TaskName next = new TaskName(StringUtils.nextInNaturalOrder(first.name), Collections.emptyNavigableSet());
		return map.subMap(first, true, next, false);
	}

}
