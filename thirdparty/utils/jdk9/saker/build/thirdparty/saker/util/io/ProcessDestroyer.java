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
package saker.build.thirdparty.saker.util.io;

import saker.build.thirdparty.saker.util.io.ProcessDestroyer;

public class ProcessDestroyer {
	private ProcessDestroyer() {
		throw new UnsupportedOperationException();
	}

	public static void destroyProcessAndPossiblyChildren(Process p) {
		ProcessHandle handle = p.toHandle();
		destroyProcessAndPossiblyChildren(handle);
	}

	private static void destroyProcessAndPossiblyChildren(ProcessHandle handle) {
		destroyHandle(handle);

		handle.children().forEach(ProcessDestroyer::destroyProcessAndPossiblyChildren);
	}

	private static void destroyHandle(ProcessHandle handle) {
		if (!handle.isAlive()) {
			return;
		}
		boolean destroyed = false;
		if (handle.supportsNormalTermination()) {
			destroyed = handle.destroy();
			if (!destroyed) {
				handle.destroyForcibly();
			}
		} else {
			handle.destroyForcibly();
		}
	}
}
