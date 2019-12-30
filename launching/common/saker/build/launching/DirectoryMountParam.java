package saker.build.launching;

import java.util.Iterator;

import saker.build.daemon.files.DaemonPath;
import saker.build.file.path.SakerPath;
import sipka.cmdline.api.Converter;

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
	public static DirectoryMountParam parse(Iterator<? extends String> args) {
		return new DirectoryMountParam(DaemonPath.valueOf(args.next()), SakerPath.normalizeRoot(args.next()));
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