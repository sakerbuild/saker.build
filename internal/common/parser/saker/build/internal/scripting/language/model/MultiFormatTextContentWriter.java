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
package saker.build.internal.scripting.language.model;

import java.util.Map;
import java.util.TreeMap;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.MultiFormattedTextContent;

public class MultiFormatTextContentWriter {
	private StringBuilder plaintext;
	private StringBuilder html;
	private StringBuilder markdown;

	public MultiFormatTextContentWriter() {
		plaintext = new StringBuilder();
		html = new StringBuilder();
		markdown = new StringBuilder();
	}

	public MultiFormatTextContentWriter paragraph(String contents) {
		plaintext.append(contents);
		plaintext.append('\n');

		markdown.append(contents);
		markdown.append('\n');
		markdown.append('\n');

		html.append("<p>");
		html.append(contents);
		html.append("</p>");
		return this;
	}

	public FormattedTextContent build() {
		Map<String, String> formats = new TreeMap<>();
		formats.put(FormattedTextContent.FORMAT_PLAINTEXT, plaintext.toString());
		formats.put(FormattedTextContent.FORMAT_HTML, html.toString());
		formats.put(FormattedTextContent.FORMAT_MARKDOWN, markdown.toString());
		return MultiFormattedTextContent.create(formats);
	}
}
