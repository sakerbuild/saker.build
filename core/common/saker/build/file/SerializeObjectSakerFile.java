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
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import saker.build.file.content.ContentDescriptor;
import saker.build.thirdparty.saker.util.io.StreamUtils;

public class SerializeObjectSakerFile extends SakerFileBase {
	private final Object obj;
	private final ContentDescriptor content;

	public SerializeObjectSakerFile(String name, Object obj, ContentDescriptor content) {
		super(name);
		this.obj = obj;
		this.content = content;
	}

	@Override
	public void writeToStreamImpl(OutputStream os) throws IOException {
		try (ObjectOutputStream oos = new ObjectOutputStream(StreamUtils.closeProtectedOutputStream(os))) {
			oos.writeObject(obj);
		}
	}

	@Override
	public int getEfficientOpeningMethods() {
		return OPENING_METHOD_WRITETOSTREAM;
	}

	@Override
	public ContentDescriptor getContentDescriptor() {
		return content;
	}

}
