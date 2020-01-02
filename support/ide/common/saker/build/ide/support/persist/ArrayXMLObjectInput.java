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
import java.util.LinkedList;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ArrayXMLObjectInput implements StructuredArrayObjectInput {
	private final int length;
	private final LinkedList<Element> children;

	public ArrayXMLObjectInput(Element element) {
		NodeList childnodes = element.getChildNodes();
		int len = childnodes.getLength();
		this.children = new LinkedList<>();
		for (int i = 0; i < len; i++) {
			Node n = childnodes.item(i);
			if (n.getNodeType() == Node.ELEMENT_NODE) {
				children.add((Element) n);
			}
		}
		length = children.size();
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public int length() {
		return length;
	}

	@Override
	public StructuredDataType getNextDataType() {
		Element first = children.peekFirst();
		if (first == null) {
			return null;
		}
		switch (first.getLocalName()) {
			case "val": {
				return StructuredDataType.LITERAL;
			}
			case "array": {
				return StructuredDataType.ARRAY;
			}
			case "obj": {
				return StructuredDataType.OBJECT;
			}
			default: {
				return null;
			}
		}
	}

	@Override
	public String readString() throws IOException {
		return getNextObject(StructuredDataType.LITERAL).toString();
	}

	@Override
	public Boolean readBoolean() throws IOException {
		return Boolean.valueOf(readString());
	}

	@Override
	public Character readChar() throws IOException {
		String str = readString();
		if (str.length() != 1) {
			throw new DataFormatException(StructuredDataType.LITERAL, str);
		}
		return str.charAt(0);
	}

	@Override
	public Byte readByte() throws IOException {
		return Byte.valueOf(readString());
	}

	@Override
	public Short readShort() throws IOException {
		return Short.valueOf(readString());
	}

	@Override
	public Integer readInt() throws IOException {
		return Integer.valueOf(readString());
	}

	@Override
	public Long readLong() throws IOException {
		return Long.valueOf(readString());
	}

	@Override
	public Float readFloat() throws IOException {
		return Float.valueOf(readString());
	}

	@Override
	public Double readDouble() throws IOException {
		return Double.valueOf(readString());
	}

	@Override
	public StructuredObjectInput readObject() throws IOException {
		return (StructuredObjectInput) getNextObject(StructuredDataType.OBJECT);
	}

	@Override
	public StructuredArrayObjectInput readArray() throws IOException {
		return (StructuredArrayObjectInput) getNextObject(StructuredDataType.ARRAY);
	}

	private Object getNextObject(StructuredDataType expectedtype) {
		Element first = children.removeFirst();
		if (!XMLStructuredWriter.SERALIZATION_NAMESPACE_URI.equals(first.getNamespaceURI())) {
			throw new DataFormatException(
					"Invalid XML element namespace: " + first.getNamespaceURI() + ":" + first.getLocalName());
		}
		switch (first.getLocalName()) {
			case "val": {
				String result = first.getTextContent();
				if (expectedtype != StructuredDataType.LITERAL) {
					throw new DataFormatException(StructuredDataType.LITERAL, result);
				}
				return result;
			}
			case "array": {
				ArrayXMLObjectInput result = new ArrayXMLObjectInput(first);
				if (expectedtype != StructuredDataType.ARRAY) {
					throw new DataFormatException(StructuredDataType.ARRAY, result);
				}
				return result;
			}
			case "obj": {
				ObjectXMLObjectInput result = new ObjectXMLObjectInput(first);
				if (expectedtype != StructuredDataType.OBJECT) {
					throw new DataFormatException(StructuredDataType.OBJECT, result);
				}
				return result;
			}
			default: {
				throw new DataFormatException("Invalid XML element local name: " + first.getLocalName());
			}
		}
	}

}
