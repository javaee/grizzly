package com.sun.grizzly.http.webxml.parser;

public interface IJAXBWebXmlParser {
	
	/**
	 * Will parse the web.xml and load the content into a WebApp object
	 * @param webxml the web.xml to parse
	 * @return WebApp populated
	 * @throws Exception
	 */
	com.sun.grizzly.http.webxml.schema.WebApp parse(String webxml) throws Exception;
	
}
