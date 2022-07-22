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
package saker.build.ide.support.properties;

import java.util.Map.Entry;
import java.util.Set;

public interface IDEPluginProperties {
	public String getStorageDirectory();

	public Set<? extends Entry<String, String>> getUserParameters();

	public String getExceptionFormat();

	public String getPort();

	public String getActsAsServer();

	/**
	 * Gets the keystore path that the plugin daemon environment should use for creating a secure SSL connection.
	 * 
	 * @return The path of the key store on the local machine or <code>null</code> if none.
	 */
	public String getKeyStorePath();

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
