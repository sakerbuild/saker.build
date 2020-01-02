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
package saker.build.daemon.files;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;

/**
 * Represents a path that optionally resides on an external location specified by a client name.
 * <p>
 * A daemon path is used to indentify files by a path and a client name. The format of daemon paths are the following:
 * 
 * <pre>
 * [clientname:/]?[absolute-path]
 * </pre>
 * 
 * The path can be optionally prefixed by a client name ending with <code>":/"</code> and an absolute path follows. The
 * absolute path must be parseable by the {@link SakerPath} class.
 * <p>
 * Paths that start with the <code>"/"</code> drive will need to have double slash after the client name.
 * <p>
 * Examples:
 * <ul>
 * <li><code>/path/without/client</code></li>
 * <li><code>c:/path/without/client</code></li>
 * <li><code>lin://home/user</code> means the path <code>/home/user</code> on the client <code>lin</code></li>
 * <li><code>win:/c:/users/user</code> means the path <code>c:/users/user</code> on the client <code>win</code></li>
 * </ul>
 * <p>
 * Client names are considered to be context dependent based on use-cases where daemon paths are used.
 * <p>
 * There are no format-wise requirements for client names, unless specified by the called {@link #valueOf} method.
 */
public class DaemonPath implements Externalizable {
	private static final long serialVersionUID = 1L;

	//format is [clientid:/][absolute-path]
	//    linux paths require double slash
	//examples:
	//    c:/local/path
	//    /home/user
	//    win:/c:/path/on/client/win
	//    win:/c:/
	//    linux://path/on/linux

	private String clientName;
	private SakerPath path;

	/**
	 * For {@link Externalizable}.
	 */
	public DaemonPath() {
	}

	private DaemonPath(String clientName, SakerPath path) {
		this.clientName = clientName;
		this.path = path;
	}

	/**
	 * Gets the client name.
	 * 
	 * @return The client name.
	 */
	public String getClientName() {
		return clientName;
	}

	/**
	 * Gets the absolute path part.
	 * 
	 * @return The path part.
	 */
	public SakerPath getPath() {
		return path;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(clientName);
		path.writeExternal(out);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		clientName = (String) in.readObject();
		path = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((clientName == null) ? 0 : clientName.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
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
		DaemonPath other = (DaemonPath) obj;
		if (clientName == null) {
			if (other.clientName != null)
				return false;
		} else if (!clientName.equals(other.clientName))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}

	@Override
	public String toString() {
		if (clientName == null) {
			return path.toString();
		}
		StringBuilder sb = new StringBuilder();
		sb.append(clientName);
		sb.append(":/");
		sb.append(path);
		return sb.toString();
	}

	/**
	 * Converts the parameter to a daemon path.
	 * 
	 * @param path
	 *            The absolute path to convert.
	 * @return The converted path.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 */
	public static DaemonPath valueOf(SakerPath path) throws InvalidPathFormatException {
		return create(null, path);
	}

	/**
	 * Converts the parameter name and path to a daemon path.
	 * 
	 * @param clientname
	 *            The client name. (might be <code>null</code>)
	 * @param path
	 *            The absolute path to convert.
	 * @return The converted path.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 */
	public static DaemonPath valueOf(String clientname, SakerPath path) throws InvalidPathFormatException {
		return create(clientname, path);
	}

	/**
	 * Parses the parameter and converts it to a daemon path.
	 * <p>
	 * The client name must not contain the <code>':'</code> character. End of the client name is determined by the
	 * first index of this character in the input.
	 * <p>
	 * The client name might be omitted in the input.
	 * 
	 * @param path
	 *            The input path.
	 * @return The parsed path.
	 * @throws InvalidPathFormatException
	 *             If the parameter is not a valid input format.
	 */
	public static DaemonPath valueOf(String path) throws InvalidPathFormatException {
		int idx = path.indexOf(':');
		if (idx < 0) {
			//no ':' in path
			return create(null, SakerPath.valueOf(path));
		}
		if (idx + 1 >= path.length()) {
			//first ':' is at the end of path, might be single drive
			return create(null, SakerPath.valueOf(path));
		}
		if (!SakerPath.isSlashCharacter(path.charAt(idx + 1))) {
			throw new InvalidPathFormatException(
					"Path separator '/' is expected after ':' at index " + (idx + 1) + " in: " + path);
		}
		int nextidx = path.indexOf(':', idx + 1);
		String clientpath;
		if (nextidx < 0) {
			//only a single drive separator (':') found in the path
			//check if the path starts with a double slash in format of client://path
			//this means that the path starts with the slash root
			if (idx + 2 < path.length()) {
				if (SakerPath.isSlashCharacter(path.charAt(idx + 2))) {
					return create(path.substring(0, idx), SakerPath.valueOf(path.substring(idx + 2)));
				}
			}
			return create(null, SakerPath.valueOf(path));
		}
		//there is a drive separator at nextidx
		//do not include the slash after the client identifier in the clientpath
		clientpath = path.substring(idx + 2);
		SakerPath dpath = SakerPath.valueOf(clientpath);
		if (dpath.isRelative()) {
			throw new InvalidPathFormatException("Path is relative: " + path);
		}
		String client = path.substring(0, idx);
		return create(client, dpath);
	}

	private static DaemonPath create(String clientName, SakerPath path) {
		SakerPathFiles.requireAbsolutePath(path);
		return new DaemonPath(clientName, path);
	}

}
