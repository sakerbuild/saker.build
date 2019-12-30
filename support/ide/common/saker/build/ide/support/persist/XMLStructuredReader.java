package saker.build.ide.support.persist;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class XMLStructuredReader {
	private Document doc;
	private Element rootElement;

	public XMLStructuredReader(InputStream input) throws IOException {
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			dbFactory.setNamespaceAware(true);
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(input);
			rootElement = doc.getDocumentElement();
			rootElement.normalize();

		} catch (SAXException | ParserConfigurationException e) {
			throw new IOException(e);
		}
	}

	public StructuredObjectInput readObject(String name) throws IOException {
		if (!rootElement.getNodeName().equals(name)) {
			return null;
		}
		return new ObjectXMLObjectInput(rootElement);
	}

	public StructuredArrayObjectInput readArray() throws IOException {
		if (!rootElement.getLocalName().equals("array")
				|| !XMLStructuredWriter.SERALIZATION_NAMESPACE_URI.equals(rootElement.getNamespaceURI())) {
			return null;
		}
		return new ArrayXMLObjectInput(rootElement);
	}
}
