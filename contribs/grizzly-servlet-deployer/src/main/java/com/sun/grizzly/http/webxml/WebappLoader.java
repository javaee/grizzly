package com.sun.grizzly.http.webxml;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.grizzly.http.webxml.parser.IJAXBWebXmlParser;
import com.sun.grizzly.http.webxml.parser.helper.WebXmlHelper;
import com.sun.grizzly.http.webxml.schema.WebApp;


/**
 * This class allow you to load a web.xml into a WebApp object. WebappLoader support web-app 2.2, 2.3, 2.4, 2.5 and 3.0.
 * WebappLoader will load the right Parser and populate the info from the web.xml.  WebApp class is a generic WebApp object,
 * that doesn't depends on the web.xml version.
 * 
 * To load a web.xml is simple.
 * 
 *  <pre>
 *  <code>
 *  WebApp webapp = webappLoader.load("./target/classes/samples/web-2.2.xml");
 *  </code>
 *  </pre>
 *  
 *  You can check the content of the WebApp object with <code>webapp.toString()</code>.  The output will be in xml.
 * 
 * @author Sebastien Dionne
 *
 */
public class WebappLoader {

	/**
     * Default Logger.
     */
    protected static Logger logger = Logger.getLogger("webappLogger");
    
	public static final String WEB_APP_DEFAULT = "WEB_APP_DEFAULT";
	public static final String WEB_APP_2_2 = "web-app_2_2.dtd";
	public static final String WEB_APP_2_3 = "web-app_2_3.dtd";
	public static final String WEB_APP_2_4 = "web-app_2_4.xsd";
	public static final String WEB_APP_2_5 = "web-app_2_5.xsd";
	public static final String WEB_APP_3_0 = "web-app_3_0.xsd";
	
	private String defaultVersion = WEB_APP_DEFAULT;
	
	private static final Map<String, String> webAppMap = new HashMap<String, String>();
	
	static {
		webAppMap.put(WEB_APP_DEFAULT, "com.sun.grizzly.http.webxml.parser.JAXBWebXml_2_3Parser");
		webAppMap.put(WEB_APP_2_2, "com.sun.grizzly.http.webxml.parser.JAXBWebXml_2_2Parser");
		webAppMap.put(WEB_APP_2_3, "com.sun.grizzly.http.webxml.parser.JAXBWebXml_2_3Parser");
		webAppMap.put(WEB_APP_2_4, "com.sun.grizzly.http.webxml.parser.JAXBWebXml_2_4Parser");
		webAppMap.put(WEB_APP_2_5, "com.sun.grizzly.http.webxml.parser.JAXBWebXml_2_5Parser");
		webAppMap.put(WEB_APP_3_0, "com.sun.grizzly.http.webxml.parser.JAXBWebXml_3_0Parser");
	}
	
	/**
	 * 
	 * @return the default parser version
	 */
	public String getDefaultVersion(){
		return defaultVersion;
	}
	
	/**
	 * 
	 * @return list of the webapp version supported
	 */
	public static Collection<String> getAvailableVersion(){
		return webAppMap.values();
	}
	
	/**
	 * 
	 * @param version web.xml parser that will be used
	 * @throws Exception if the version is not found, Use the Constants.
	 */
	public void setDefaultVersion(String version) throws Exception {
		
		if(!webAppMap.containsKey(version)){
			throw new Exception("Version not found");
		}
		defaultVersion = version;
	}
	
	/**
	 * 
	 * @param webxml the web.xml file that will be loaded
	 * @return the WebApp loaded
	 * @throws Exception any exceptions will be thrown here if there is a problem parsing the file
	 */
	public WebApp load(String webxml) throws Exception {
		
		// load the xml file to get it's schema version
		WebXmlHelper helper = new WebXmlHelper();
		
		helper.getInfo(webxml);
		
		String schemaVersion = helper.getVersion();
		
		// version found
		if(schemaVersion==null){
			schemaVersion = WEB_APP_DEFAULT;
		} 
		
		if(logger.isLoggable(Level.FINEST)){
			logger.log(Level.FINEST, "Version found=" + schemaVersion);
		}
		
		WebApp webapp = extractWebXmlInfo(schemaVersion, webxml);
		
		return webapp;
	}
	
	/**
	 * 
	 * @param schemaVersion
	 * @param webxml
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	protected WebApp extractWebXmlInfo(String schemaVersion, String webxml) throws Exception {
		
		Class clazz = Class.forName(webAppMap.get(schemaVersion));
		IJAXBWebXmlParser parser = (IJAXBWebXmlParser)clazz.newInstance();
		
		WebApp webapp = parser.parse(webxml);
		
		return webapp;
        
	}

}
