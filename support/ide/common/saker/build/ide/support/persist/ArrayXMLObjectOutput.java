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
package saker.build.ide.support.persist;

import java.io.IOException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ArrayXMLObjectOutput implements StructuredArrayObjectOutput {
	private Document doc;
	private Node parentElement;
	private Element endAppendElement;
	private Element arrayElem;

	public ArrayXMLObjectOutput(Document doc, Node parentElement, Element appendelement, Element arrayElem) {
		this.doc = doc;
		this.parentElement = parentElement;
		this.endAppendElement = appendelement;
		this.arrayElem = arrayElem;
	}

	@Override
	public void write(String value) throws IOException {
		checkClosed();
		Element elem = doc.createElementNS(XMLStructuredWriter.SERALIZATION_NAMESPACE_URI,
				XMLStructuredWriter.SERIALIZATION_NAMESPACE + ":val");
		elem.appendChild(doc.createTextNode(value));
		arrayElem.appendChild(elem);
	}

	@Override
	public void write(boolean value) throws IOException {
		checkClosed();
		write(Boolean.toString(value));
	}

	@Override
	public void write(char value) throws IOException {
		checkClosed();
		write(Character.toString(value));
	}

	@Override
	public void write(byte value) throws IOException {
		checkClosed();
		write(Byte.toString(value));
	}

	@Override
	public void write(short value) throws IOException {
		checkClosed();
		write(Short.toString(value));
	}

	@Override
	public void write(int value) throws IOException {
		checkClosed();
		write(Integer.toString(value));
	}

	@Override
	public void write(long value) throws IOException {
		checkClosed();
		write(Long.toString(value));
	}

	@Override
	public void write(float value) throws IOException {
		checkClosed();
		write(Float.toString(value));
	}

	@Override
	public void write(double value) throws IOException {
		checkClosed();
		write(Double.toString(value));
	}

	@Override
	public StructuredObjectOutput writeObject() throws IOException {
		checkClosed();
		Element objelem = doc.createElementNS(XMLStructuredWriter.SERALIZATION_NAMESPACE_URI,
				XMLStructuredWriter.SERIALIZATION_NAMESPACE + ":obj");
		return new ObjectXMLObjectOutput(doc, arrayElem, objelem);
	}

	@Override
	public StructuredArrayObjectOutput writeArray() throws IOException {
		checkClosed();
		Element narrayElem = doc.createElementNS(XMLStructuredWriter.SERALIZATION_NAMESPACE_URI,
				XMLStructuredWriter.SERIALIZATION_NAMESPACE + ":array");
		return new ArrayXMLObjectOutput(doc, this.arrayElem, narrayElem, narrayElem);
	}

	@Override
	public void close() throws IOException {
		if (endAppendElement == null) {
			return;
		}
		parentElement.appendChild(endAppendElement);
		endAppendElement = null;
	}

	private void checkClosed() throws IOException {
		if (endAppendElement == null) {
			throw new IOException("closed.");
		}
	}
}
