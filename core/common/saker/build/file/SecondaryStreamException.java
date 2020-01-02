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
package saker.build.file;

import java.io.IOException;

import saker.build.file.content.ContentDatabase;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.thirdparty.saker.util.io.ByteSink;

/**
 * Exception class for rethrowing exceptions thrown by additional stream writings.
 * 
 * @see SakerFile#synchronizeImpl(ProviderHolderPathKey, ByteSink)
 * @see ContentDatabase.ContentUpdater#updateWithStream(ByteSink)
 */
public class SecondaryStreamException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see IOException#IOException(String, Throwable)
	 */
	public SecondaryStreamException(String message, IOException cause) {
		super(message, cause);
	}

	/**
	 * @see IOException#IOException(Throwable)
	 */
	public SecondaryStreamException(IOException cause) {
		super(cause);
	}

	@Override
	public IOException getCause() {
		return (IOException) super.getCause();
	}
}