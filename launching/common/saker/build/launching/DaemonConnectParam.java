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
package saker.build.launching;

import java.util.Iterator;
import java.util.Objects;

import sipka.cmdline.api.Converter;
import sipka.cmdline.runtime.MissingArgumentException;

@Converter(method = "parse")
public class DaemonConnectParam {
	public final DaemonAddressParam address;
	public final String name;

	public DaemonConnectParam(DaemonAddressParam address, String name) {
		this.address = address;
		this.name = name;
	}

	/**
	 * @param args
	 * @return
	 * @cmd-format &lt;address> &lt;name>
	 */
	public static DaemonConnectParam parse(String argname, Iterator<? extends String> args) {
		Objects.requireNonNull(args, "iterator");
		DaemonAddressParam addressparam = DaemonAddressParam.parse(argname, args);
		if (!args.hasNext()) {
			throw new MissingArgumentException("Name argument is missing.", argname);
		}
		String name = args.next();
		return new DaemonConnectParam(addressparam, name);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((address == null) ? 0 : address.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		DaemonConnectParam other = (DaemonConnectParam) obj;
		if (address == null) {
			if (other.address != null)
				return false;
		} else if (!address.equals(other.address))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

}
