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

import java.util.Map;

import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredMapTaskResult;

/**
 * Interface representing the result of a build target task.
 * <p>
 * Build target tasks can have outputs which are explicitly named. This interface provides access to the outputs of a
 * build target by mapping them to their respective task identifiers.
 * <p>
 * This interface is similar in functionality to {@link StructuredMapTaskResult}, but is specifically declared for build
 * target task results.
 * 
 * @see SimpleBuildTargetTaskResult
 */
public interface BuildTargetTaskResult {
	/**
	 * Gets the build target results.
	 * 
	 * @return An unmodifiable map of build target result names mapped to their task identifiers.
	 */
	public Map<String, TaskIdentifier> getTaskResultIdentifiers();
}
