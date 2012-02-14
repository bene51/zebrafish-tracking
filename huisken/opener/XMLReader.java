package huisken.opener;

import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XMLReader {
	private final String xmlfile;

	public final int width, height, depth, frames;
	public final double pw, ph, pd, framerate;

	public XMLReader(String xmlfile) throws Exception {
		this.xmlfile = xmlfile;

		//get the factory
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();


		//Using factory get an instance of document builder
		DocumentBuilder db = dbf.newDocumentBuilder();

		//parse using builder to get DOM representation of the XML file
		Document dom = db.parse(xmlfile);

		//get the root elememt
		Element experimentEl = dom.getDocumentElement();

		HashMap<String, String> data = new HashMap<String, String>();
		NodeList nodes = experimentEl.getChildNodes();
		for(int i = 0; i < nodes.getLength(); i++) {
			Node n = nodes.item(i);
			data.put(n.getNodeName(), n.getTextContent());
		}

		this.width  = Integer.parseInt(data.get("Width"));
		this.height = Integer.parseInt(data.get("Height"));
		this.depth  = Integer.parseInt(data.get("NrPlanes"));
		this.frames = Integer.parseInt(data.get("NrFrames"));

		int magObj  = Integer.parseInt(data.get("MagObj"));
		int pixSize = Integer.parseInt(data.get("PixelSize"));
		this.pw     = pixSize / (double)magObj;
		this.ph     = this.pw;
		this.pd     = Double.parseDouble(data.get("dZ"));
		this.framerate = Double.parseDouble(data.get("FrameRate"));
	}

	public static void main(String[] args) throws Exception {
		String xmlfile = "/media/shares/110727_haux/e001.xml";
		new XMLReader(xmlfile);
	}
}
