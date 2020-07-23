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

import saker.build.daemon.files.DaemonPath;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import sipka.cmdline.api.Converter;
import sipka.cmdline.runtime.InvalidArgumentFormatException;
import sipka.cmdline.runtime.MissingArgumentException;
import sipka.cmdline.runtime.ParseUtil;

@Converter(method = "parse")
class DirectoryMountParam {
	public final DaemonPath path;
	public final String root;

	public DirectoryMountParam(DaemonPath path, String root) {
		this.path = path;
		this.root = root;
	}

	/**
	 * @cmd-format &lt;mount-path> &lt;root-name>
	 */
	public static DirectoryMountParam parse(String argname, Iterator<? extends String> args) {
		String narg = ParseUtil.requireNextArgument(argname, args);
		DaemonPath path;
		try {
			path = DaemonPath.valueOf(narg);
		} catch (IllegalArgumentException e) {
			throw new InvalidArgumentFormatException(e, argname);
		}
		if (!args.hasNext()) {
			throw new MissingArgumentException("Root name argument is missing.", argname);
		}
		String rootstr = args.next();
		String root;
		try {
			root = SakerPath.normalizeRoot(rootstr);
		} catch (InvalidPathFormatException e) {
			throw new InvalidArgumentFormatException("Invalid root format: " + rootstr, e, argname);
		}
		return new DirectoryMountParam(path, root);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		result = prime * result + ((root == null) ? 0 : root.hashCode());
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
		DirectoryMountParam other = (DirectoryMountParam) obj;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		if (root == null) {
			if (other.root != null)
				return false;
		} else if (!root.equals(other.root))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return path + " -> " + root;
	}
}