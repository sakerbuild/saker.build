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
package saker.build.runtime.project;

import java.io.Closeable;
import java.io.IOException;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMIDefaultOnFailure;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;

public interface ProjectCacheHandle extends Closeable {
	public void reset();

	public void clean();

	@RMIForbidden
	@RMIDefaultOnFailure
	public default SakerProjectCache toProject() {
		return null;
	}

	@Override
	public default void close() throws IOException {
	}
}
