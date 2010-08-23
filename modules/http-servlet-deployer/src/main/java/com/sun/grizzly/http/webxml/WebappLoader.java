/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.grizzly.http.webxml;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.grizzly.http.servlet.deployer.comparator.ServletLoadOnStartupComparator;
import com.sun.grizzly.http.webxml.parser.IJAXBWebXmlParser;
import com.sun.grizzly.http.webxml.parser.helper.WebXmlHelper;
import com.sun.grizzly.http.webxml.schema.Servlet;
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
		webAppMap.put("2.2", "com.sun.grizzly.http.webxml.parser.JAXBWebXml_2_2Parser");
		webAppMap.put("2.3", "com.sun.grizzly.http.webxml.parser.JAXBWebXml_2_3Parser");
		webAppMap.put("2.4", "com.sun.grizzly.http.webxml.parser.JAXBWebXml_2_4Parser");
		webAppMap.put("2.5", "com.sun.grizzly.http.webxml.parser.JAXBWebXml_2_5Parser");
		webAppMap.put("3.0", "com.sun.grizzly.http.webxml.parser.JAXBWebXml_3_0Parser");
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
	public static WebApp load(String webxml) throws Exception {
		
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

		WebApp webApp = extractWebXmlInfo(schemaVersion, webxml);
		
		List<Servlet> servletList = webApp.getServlet();
		
		// sort the servlet by load-on-startup values
		if(servletList!=null && !servletList.isEmpty()){
			
			Collections.sort(servletList, new ServletLoadOnStartupComparator()); 
			
			webApp.setServlet(servletList);
		}
		
		return webApp;
	}
	
	/**
	 * 
	 * @param schemaVersion
	 * @param webxml
	 * @return WebApp populated from the web.xml file
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static WebApp extractWebXmlInfo(String schemaVersion, String webxml) throws Exception {

            IJAXBWebXmlParser parser = (IJAXBWebXmlParser)
                ((Class) Class.forName(webAppMap.get(schemaVersion))).newInstance();

            return parser.parse(webxml);
        
	}

}
