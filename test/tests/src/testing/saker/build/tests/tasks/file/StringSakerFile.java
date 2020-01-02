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
package testing.saker.build.tests.tasks.file;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import saker.build.file.SakerFileBase;
import saker.build.file.content.ContentDescriptor;

public class StringSakerFile extends SakerFileBase {
	private String content;
	private ContentDescriptor contentDescriptor;

	public StringSakerFile(String name, String content, ContentDescriptor contentDescriptor) {
		super(name);
		this.content = content;
		this.contentDescriptor = contentDescriptor;
	}

	@Override
	public ContentDescriptor getContentDescriptor() {
		return contentDescriptor;
	}

	@Override
	public void writeToStreamImpl(OutputStream os) throws IOException {
		os.write(content.getBytes(StandardCharsets.UTF_8));
	}

}