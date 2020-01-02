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
import java.util.LinkedHashSet;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ObjectXMLObjectInput implements StructuredObjectInput {
	private Element element;

	public ObjectXMLObjectInput(Element element) {
		this.element = element;
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public Set<String> getFields() {
		NodeList fieldnodes = element.getChildNodes();

		Set<String> result = new LinkedHashSet<>();
		int len = fieldnodes.getLength();
		for (int i = 0; i < len; i++) {
			Node item = fieldnodes.item(i);
			if (item.getNamespaceURI() != null || item.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			result.add(((Element) item).getLocalName());
		}
		return result;
	}

	@Override
	public String readString(String name) throws IOException {
		NodeList fieldnodes = element.getChildNodes();
		int len = fieldnodes.getLength();
		for (int i = 0; i < len; i++) {
			Node item = fieldnodes.item(i);
			if (item.getNamespaceURI() != null || item.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			if (name.equals(item.getLocalName())) {
				return ((Element) item).getTextContent();
			}
		}

		return null;
	}

	@Override
	public Boolean readBoolean(String name) throws IOException {
		String s = readString(name);
		if (s == null) {
			return null;
		}
		if (s.equalsIgnoreCase("true")) {
			return true;
		}
		if (s.equalsIgnoreCase("false")) {
			return false;
		}
		throw new DataFormatException(StructuredDataType.LITERAL, s);
	}

	@Override
	public Character readChar(String name) throws IOException {
		String s = readString(name);
		if (s == null) {
			return null;
		}
		if (s.length() != 1) {
			throw new DataFormatException(StructuredDataType.LITERAL, s);
		}
		return s.charAt(0);
	}

	@Override
	public Byte readByte(String name) throws IOException {
		String s = readString(name);
		if (s == null) {
			return null;
		}
		try {
			return Byte.valueOf(s);
		} catch (NumberFormatException e) {
			throw new DataFormatException(StructuredDataType.LITERAL, s, e);
		}
	}

	@Override
	public Short readShort(String name) throws IOException {
		String s = readString(name);
		if (s == null) {
			return null;
		}
		try {
			return Short.valueOf(s);
		} catch (NumberFormatException e) {
			throw new DataFormatException(StructuredDataType.LITERAL, s, e);
		}
	}

	@Override
	public Integer readInt(String name) throws IOException {
		String s = readString(name);
		if (s == null) {
			return null;
		}
		try {
			return Integer.valueOf(s);
		} catch (NumberFormatException e) {
			throw new DataFormatException(StructuredDataType.LITERAL, s, e);
		}
	}

	@Override
	public Long readLong(String name) throws IOException {
		String s = readString(name);
		if (s == null) {
			return null;
		}
		try {
			return Long.valueOf(s);
		} catch (NumberFormatException e) {
			throw new DataFormatException(StructuredDataType.LITERAL, s, e);
		}
	}

	@Override
	public Float readFloat(String name) throws IOException {
		String s = readString(name);
		if (s == null) {
			return null;
		}
		try {
			return Float.valueOf(s);
		} catch (NumberFormatException e) {
			throw new DataFormatException(StructuredDataType.LITERAL, s, e);
		}
	}

	@Override
	public Double readDouble(String name) throws IOException {
		String s = readString(name);
		if (s == null) {
			return null;
		}
		try {
			return Double.valueOf(s);
		} catch (NumberFormatException e) {
			throw new DataFormatException(StructuredDataType.LITERAL, s, e);
		}
	}

	@Override
	public StructuredObjectInput readObject(String name) throws IOException {
		Element node = getFieldElement(name);
		if (node == null) {
			return null;
		}
		return new ObjectXMLObjectInput(node);
	}

	private Element getFieldElement(String name) throws IOException {
		NodeList fieldnodes = element.getChildNodes();
		int len = fieldnodes.getLength();
		for (int i = 0; i < len; i++) {
			Node item = fieldnodes.item(i);
			if (item.getNamespaceURI() != null || item.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			if (name.equals(item.getLocalName())) {
				return ((Element) item);
			}
		}
		return null;
	}

	@Override
	public StructuredArrayObjectInput readArray(String name) throws IOException {
		Element n = getFieldElement(name);
		if (n == null) {
			return null;
		}
		NodeList fieldnodes = n.getChildNodes();
		int len = fieldnodes.getLength();
		for (int i = 0; i < len; i++) {
			Node item = fieldnodes.item(i);
			if (!XMLStructuredWriter.SERALIZATION_NAMESPACE_URI.equals(item.getNamespaceURI())
					|| item.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			if ("array".equals(item.getLocalName())) {
				Element arrayelem = (Element) item;
				return new ArrayXMLObjectInput(arrayelem);
			}
		}
		return null;
	}

}
