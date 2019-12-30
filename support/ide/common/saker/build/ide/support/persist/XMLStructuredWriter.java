package saker.build.ide.support.persist;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class XMLStructuredWriter implements Closeable {
	static final String SERALIZATION_NAMESPACE_URI = "saker://serialization/types";
	static final String SERIALIZATION_NAMESPACE = "s";

	private Document doc;

	private Object rootOutput;
	private StreamResult streamResult;

	public XMLStructuredWriter(Writer writer) throws IOException {
		this(new StreamResult(writer));
	}

	public XMLStructuredWriter(OutputStream os) throws IOException {
		this(new StreamResult(os));
	}

	public XMLStructuredWriter(File file) throws IOException {
		this(new StreamResult(file));
	}

	private XMLStructuredWriter(StreamResult streamResult) throws IOException {
		this.streamResult = streamResult;
		try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			docFactory.setNamespaceAware(true);
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

			doc = docBuilder.newDocument();

		} catch (ParserConfigurationException e) {
			throw new IOException(e);
		}
	}

	public StructuredArrayObjectOutput writeArray() throws IOException, IllegalStateException {
		checkClosed();
		if (rootOutput != null) {
			throw new IllegalStateException("Already has root element");
		}
		Element rootElement = doc.createElementNS(XMLStructuredWriter.SERALIZATION_NAMESPACE_URI,
				XMLStructuredWriter.SERIALIZATION_NAMESPACE + ":array");
		rootElement.setAttribute("xmlns:" + SERIALIZATION_NAMESPACE, SERALIZATION_NAMESPACE_URI);
		ArrayXMLObjectOutput result = new ArrayXMLObjectOutput(doc, doc, rootElement, rootElement);
		rootOutput = result;
		return result;
	}

	public StructuredObjectOutput writeObject(String name) throws IOException, IllegalStateException {
		checkClosed();
		if (rootOutput != null) {
			throw new IllegalStateException("Already has root element");
		}
		Element rootElement = doc.createElement(name);
		rootElement.setAttribute("xmlns:" + SERIALIZATION_NAMESPACE, SERALIZATION_NAMESPACE_URI);
		ObjectXMLObjectOutput result = new ObjectXMLObjectOutput(doc, doc, rootElement);
		rootOutput = result;
		return result;
	}

	@Override
	public void close() throws IOException {
		if (doc == null) {
			//already closed
			return;
		}
		try {
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			transformerFactory.setAttribute("indent-number", 2);
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(doc);
			doc = null;

			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

			transformer.transform(source, streamResult);
		} catch (TransformerException e) {
			throw new IOException(e);
		}
	}

	private void checkClosed() throws IOException {
		if (doc == null) {
			throw new IOException("closed.");
		}
	}

}
