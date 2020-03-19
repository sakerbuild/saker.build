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
package saker.build.ide.support;

import java.util.Set;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.ProviderMountIDEProperty;
import saker.build.ide.support.properties.ScriptConfigurationIDEProperty;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class SakerIDESupportUtils {
	private SakerIDESupportUtils() {
		throw new UnsupportedOperationException();
	}

	public static ProviderMountIDEProperty getMountPropertyForPath(SakerPath path, IDEProjectProperties properties) {
		if (properties == null || path == null) {
			return null;
		}
		return getMountPropertyForPath(path, properties.getMounts());
	}

	public static ProviderMountIDEProperty getMountPropertyForPath(SakerPath path,
			Iterable<? extends ProviderMountIDEProperty> mounts) {
		if (path == null) {
			return null;
		}
		if (ObjectUtils.isNullOrEmpty(mounts)) {
			return null;
		}
		String pathroot = path.getRoot();
		if (pathroot == null) {
			return null;
		}
		for (ProviderMountIDEProperty prop : mounts) {
			if (pathroot.equals(prop.getRoot())) {
				return prop;
			}
		}
		return null;
	}

	public static boolean isScriptModellingConfigurationAppliesTo(SakerPath execpath, IDEProjectProperties properties) {
		Set<? extends ScriptConfigurationIDEProperty> scriptconfigs = properties.getScriptConfigurations();
		if (ObjectUtils.isNullOrEmpty(scriptconfigs)) {
			return false;
		}

		if (execpath == null) {
			return false;
		}
		for (ScriptConfigurationIDEProperty scprop : scriptconfigs) {
			String wcstr = scprop.getScriptsWildcard();
			if (ObjectUtils.isNullOrEmpty(wcstr)) {
				continue;
			}
			WildcardPath scriptwc;
			try {
				scriptwc = WildcardPath.valueOf(wcstr);
			} catch (IllegalArgumentException e) {
				return false;
			}
			if (scriptwc.includes(execpath)) {
				Set<String> exclusions = properties.getScriptModellingExclusions();
				if (!ObjectUtils.isNullOrEmpty(exclusions)) {
					for (String excl : exclusions) {
						WildcardPath exclwc;
						try {
							exclwc = WildcardPath.valueOf(excl);
						} catch (IllegalArgumentException e) {
							continue;
						}
						if (exclwc.includes(execpath)) {
							return false;
						}
					}
				}
				return true;
			}
		}
		return false;
	}

	public static SakerPath projectPathToExecutionPath(IDEProjectProperties ideprops, SakerPath projectlocalpath,
			SakerPath path) {
		if (path.isRelative()) {
			try {
				path = projectlocalpath.resolve(path);
			} catch (IllegalArgumentException e) {
				//if somewhy we fail to resolve the path. E.g. the path contains too many ".." at start
				return null;
			}
		}
		Set<? extends ProviderMountIDEProperty> mounts = ideprops.getMounts();
		if (ObjectUtils.isNullOrEmpty(mounts)) {
			return null;
		}
		for (ProviderMountIDEProperty mountprop : mounts) {
			String rootstr = mountprop.getRoot();
			String mountpathstr = mountprop.getMountPath();
			String clientname = mountprop.getMountClientName();
			if (ObjectUtils.isNullOrEmpty(rootstr) || ObjectUtils.isNullOrEmpty(mountpathstr)
					|| ObjectUtils.isNullOrEmpty(clientname)) {
				continue;
			}
			String root;
			SakerPath mountpath;
			try {
				root = SakerPath.normalizeRoot(rootstr);
				mountpath = SakerPath.valueOf(mountpathstr);
			} catch (IllegalArgumentException e) {
				//invalid configuration, failed to parse
				continue;
			}
			if (SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(clientname)) {
				//the mount path is resolved against the project directory
				clientname = SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM;
				mountpath = projectlocalpath.resolve(mountpath.replaceRoot(null));
				//continue with testing local 
			}
			if (SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(clientname)) {
				int commonnamecount = path.getCommonNameCount(mountpath);
				if (commonnamecount >= 0) {
					return path.subPath(commonnamecount).replaceRoot(root);
				}
			}
		}
		return null;
	}

	public static SakerPath executionPathToProjectRelativePath(IDEProjectProperties properties,
			SakerPath projectsakerpath, SakerPath executionsakerpath) {
		if (executionsakerpath == null) {
			return null;
		}
		if (executionsakerpath.isRelative()) {
			SakerPath propworkdir = properties == null ? null : tryParsePath(properties.getWorkingDirectory());
			if (propworkdir == null || propworkdir.isRelative()) {
				return null;
			}
			executionsakerpath = propworkdir.resolve(executionsakerpath);
		}
		//the path to resolve is an absolute execution path

		ProviderMountIDEProperty mountprop = SakerIDESupportUtils.getMountPropertyForPath(executionsakerpath,
				properties);
		if (mountprop == null) {
			return null;
		}
		String mountclientname = mountprop.getMountClientName();
		//if mountclientname == null then we fail with null
		if (SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(mountclientname)) {
			//the mounting is project relative
			SakerPath mountedpath = tryParsePath(mountprop.getMountPath());
			if (mountedpath == null) {
				return null;
			}
			if (projectsakerpath != null) {
				SakerPath mountedfullpath = projectsakerpath.resolve(mountedpath.replaceRoot(null));
				executionsakerpath = mountedfullpath.resolve(executionsakerpath.replaceRoot(null));
				if (executionsakerpath.startsWith(projectsakerpath)) {
					return projectsakerpath.relativize(executionsakerpath);
				}
			}
			return null;
		}
		if (SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(mountclientname)) {
			//the mount is on the local filesystem which is where the project resides
			SakerPath mountedpath = tryParsePath(mountprop.getMountPath());
			if (mountedpath == null) {
				return null;
			}
			if (projectsakerpath != null) {
				executionsakerpath = mountedpath.resolve(executionsakerpath.replaceRoot(null));
				if (executionsakerpath.startsWith(projectsakerpath)) {
					return projectsakerpath.relativize(executionsakerpath);
				}
			}
			return null;
		}
		//the mount is made through a daemon connection, cannot determine the file system association
		return null;
	}

	public static SakerPath tryParsePath(String path) {
		if (path == null) {
			return null;
		}
		try {
			return SakerPath.valueOf(path);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
