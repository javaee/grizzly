package com.sun.grizzly.http.webxml.parser.helper;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.sun.grizzly.http.webxml.parser.handler.SAXWebXmlHandler;


public class WebXmlHelper {

	private boolean dtdFound = false;
	private boolean xsdFound = false;
	private String version = null;
	
	public boolean isDtdFound() {
		return dtdFound;
	}

	public void setDtdFound(boolean dtdFound) {
		this.dtdFound = dtdFound;
	}

	public boolean isXsdFound() {
		return xsdFound;
	}

	public void setXsdFound(boolean xsdFound) {
		this.xsdFound = xsdFound;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}
	
	public void getInfo(String file) throws Exception {

		SAXWebXmlHandler handler = new SAXWebXmlHandler();
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser p = factory.newSAXParser();

		p.parse(file, handler);
		
		version = handler.getVersion();
		dtdFound = handler.getDtdFound();
		xsdFound = handler.getXsdFound();
		
	}


}
