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
package saker.build.file.provider;

import java.io.Closeable;
import java.io.IOException;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMIExceptionRethrow;
import saker.build.thirdparty.saker.util.io.RemoteIOException;

public interface SakerFileLock extends Closeable {
	@RMIExceptionRethrow(RemoteIOException.class)
	public void lock() throws IOException, IllegalStateException;

	@RMIExceptionRethrow(RemoteIOException.class)
	public boolean tryLock() throws IOException, IllegalStateException;

	@RMIExceptionRethrow(RemoteIOException.class)
	public void release() throws IOException, IllegalStateException;
}
