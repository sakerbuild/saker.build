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

import java.nio.file.Path;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import sipka.cmdline.api.Parameter;

public class SakerJarLocatorParamContext {
	/**
	 * <pre>
	 * Specifies the location of the build system runtime.
	 * 
	 * The build system requires its distribution JAR for proper operation,
	 * as it may be necessary for some tasks to start new processes 
	 * to do their work.
	 * 
	 * Under normal circumstances the build system can locate the appropriate
	 * JAR location based on the classpath of the current process. If it fails,
	 * an exception will be thrown and you might need to specify this if required.
	 * 
	 * The path will be resolved against the local file system, relative paths
	 * are resolved against the working directory of the process.
	 * 
	 * (If you ever encounter a bug in automatic resolution, please file
	 * an issue at https://github.com/sakerbuild/saker.build/issues)
	 * </pre>
	 */
	@Parameter("-saker-jar")
	public SakerPath sakerJar;

	public SakerPath getSakerJar() {
		if (sakerJar == null) {
			sakerJar = LaunchingUtils.searchForSakerJarInClassPath();
		}
		if (sakerJar == null) {
			throw new IllegalArgumentException("Saker JAR not found.");
		}
		if (sakerJar.isRelative()) {
			sakerJar = LaunchingUtils.absolutize(sakerJar);
		}
		return sakerJar;
	}

	public Path getSakerJarPath() {
		return LocalFileProvider.toRealPath(getSakerJar());
	}

	public boolean isJarSpecified() {
		return sakerJar != null;
	}
}
