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
package saker.build.scripting.model.info;

import java.util.Set;

import saker.build.scripting.model.FormattedTextContent;

/**
 * Provides information about a parameter specific to a task.
 * <p>
 * Underlying data in this interface can be lazy loaded similarly to {@link ExternalScriptInformationProvider}.
 * 
 * @see SimpleTaskParameterInformation
 */
public interface TaskParameterInformation extends InformationHolder {
	/**
	 * Gets the task information this parameter corresponds to.
	 * 
	 * @return The task information.
	 */
	public TaskInformation getTask();

	/**
	 * Gets the name of the parameter.
	 * <p>
	 * A specific value <code>"*"</code> means that the parameter doesn't have an explicit name, but will handle
	 * arbitrarily named parameters.
	 * 
	 * @return The name of the parameter or <code>"*"</code> if the parameter is not explicitly named.
	 */
	public String getParameterName();

	/**
	 * Gets if the parameter is required to be specified by the user.
	 * 
	 * @return <code>true</code> if required.
	 */
	public default boolean isRequired() {
		return false;
	}

	/**
	 * Gets the alias names of this parameter.
	 * <p>
	 * Alias names mean that a parameter can be specified using multiple names, the task implementation will handle that
	 * appropriately.
	 * 
	 * @return The name aliases or <code>null</code> if not available or still loading.
	 */
	public default Set<String> getAliases() {
		return null;
	}

	/**
	 * Gets documentational information about this parameter.
	 * 
	 * @return The information about the parameter or <code>null</code> if not available or still loading.
	 */
	@Override
	public default FormattedTextContent getInformation() {
		return null;
	}

	/**
	 * Gets the type information of the parameter.
	 * 
	 * @return The type of the parameter or <code>null</code> if not available or still loading.
	 */
	public default TypeInformation getTypeInformation() {
		return null;
	}

	/**
	 * Gets if the parameter is deprecated.
	 * 
	 * @return <code>true</code> if the parameter is deprecated.
	 */
	public default boolean isDeprecated() {
		return false;
	}
}