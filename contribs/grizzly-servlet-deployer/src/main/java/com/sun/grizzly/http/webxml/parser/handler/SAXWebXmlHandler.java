package com.sun.grizzly.http.webxml.parser.handler;

import java.io.IOException;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class SAXWebXmlHandler extends DefaultHandler {

	private boolean dtdFound = false;
	private boolean xsdFound = false;
	private String version = null;
	
	public void reset(){
		dtdFound = false;
		xsdFound = false;
		version = null;
	}
	
	public void startElement(String namespaceURI, String localName, String qName, Attributes atts) {

		if(dtdFound){
			return;
		}
		
		if (qName.toUpperCase().equals("WEB-APP")) {
			
			String value = atts.getValue("xsi:schemaLocation");
			
			if(value!=null){
				xsdFound = true;
				int index = value.lastIndexOf("/");
				if(index>0){
					version = value.substring(index+1);
				}
			}
			
		}
	}

	@Override
	public InputSource resolveEntity(String publicId, String systemId) throws IOException, SAXException {
		// DTD
		if(systemId.toUpperCase().endsWith(".DTD")){
			dtdFound = true;
			
			int index = systemId.lastIndexOf("/");
			if(index>0){
				version = systemId.substring(index+1);
			}
		}
		
		return null;
	}
	
	public boolean getDtdFound() {
		return dtdFound;
	}

	public void setDtdFound(boolean dtdFound) {
		this.dtdFound = dtdFound;
	}

	public boolean getXsdFound() {
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

}
