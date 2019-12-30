package saker.build.ide.support.persist;

import java.io.IOException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class ObjectXMLObjectOutput implements StructuredObjectOutput {
	private Document doc;
	private Node parentElement;
	private Element element;

	public ObjectXMLObjectOutput(Document doc, Node parentElement, Element element) {
		this.doc = doc;
		this.parentElement = parentElement;
		this.element = element;
	}

	@Override
	public void writeField(String name) throws IOException, DuplicateObjectFieldException {
		checkClosedHasField(name);
		Element elem = doc.createElement(name);
		//no text node
		element.appendChild(elem);
	}

	@Override
	public void writeField(String name, String value) throws IOException {
		checkClosedHasField(name);

		Element elem = doc.createElement(name);
		elem.appendChild(doc.createTextNode(value));
		element.appendChild(elem);
	}

	@Override
	public void writeField(String name, boolean value) throws IOException {
		checkClosedHasField(name);

		writeField(name, Boolean.toString(value));
	}

	@Override
	public void writeField(String name, char value) throws IOException {
		checkClosedHasField(name);
		writeField(name, Character.toString(value));
	}

	@Override
	public void writeField(String name, byte value) throws IOException {
		checkClosedHasField(name);
		writeField(name, Byte.toString(value));
	}

	@Override
	public void writeField(String name, short value) throws IOException {
		checkClosedHasField(name);
		writeField(name, Short.toString(value));
	}

	@Override
	public void writeField(String name, int value) throws IOException {
		checkClosedHasField(name);
		writeField(name, Integer.toString(value));
	}

	@Override
	public void writeField(String name, long value) throws IOException {
		checkClosedHasField(name);
		writeField(name, Long.toString(value));
	}

	@Override
	public void writeField(String name, float value) throws IOException {
		checkClosedHasField(name);
		writeField(name, Float.toString(value));
	}

	@Override
	public void writeField(String name, double value) throws IOException {
		checkClosedHasField(name);
		writeField(name, Double.toString(value));
	}

	@Override
	public StructuredObjectOutput writeObject(String name) throws IOException {
		checkClosedHasField(name);
		Element nelem = doc.createElement(name);
		return new ObjectXMLObjectOutput(doc, element, nelem);
	}

	@Override
	public StructuredArrayObjectOutput writeArray(String name) throws IOException {
		checkClosedHasField(name);
		Element nelem = doc.createElement(name);
		Element arrayElem = doc.createElementNS(XMLStructuredWriter.SERALIZATION_NAMESPACE_URI,
				XMLStructuredWriter.SERIALIZATION_NAMESPACE + ":array");
		nelem.appendChild(arrayElem);
		return new ArrayXMLObjectOutput(doc, element, nelem, arrayElem);
	}

	@Override
	public void close() throws IOException {
		if (element == null) {
			return;
		}
		parentElement.appendChild(element);
		element = null;
	}

	private void checkClosedHasField(String name) throws IOException {
		Element elem = element;
		if (elem == null) {
			throw new IOException("closed.");
		}
		NodeList children = elem.getChildNodes();
		int clen = children.getLength();
		for (int i = 0; i < clen; i++) {
			Node c = children.item(i);
			if (c.getNamespaceURI() == null && name.equals(c.getNodeName())) {
				throw new DuplicateObjectFieldException("Already has field with name: " + name);
			}
		}
	}

}
