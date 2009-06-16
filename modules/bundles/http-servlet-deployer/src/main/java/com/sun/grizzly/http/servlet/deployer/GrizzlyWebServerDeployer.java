/**
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER. *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved. *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */
package com.sun.grizzly.http.servlet.deployer;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.Servlet;

import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.comet.CometAsyncFilter;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.http.embed.GrizzlyWebServer.PROTOCOL;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.http.servlet.deployer.comparator.WarFileComparator;
import com.sun.grizzly.http.webxml.WebappLoader;
import com.sun.grizzly.http.webxml.schema.FilterMapping;
import com.sun.grizzly.http.webxml.schema.InitParam;
import com.sun.grizzly.http.webxml.schema.Listener;
import com.sun.grizzly.http.webxml.schema.ServletMapping;
import com.sun.grizzly.http.webxml.schema.WebApp;
import com.sun.grizzly.util.ClassLoaderUtil;
import com.sun.grizzly.util.ExpandJar;

/*
 * We have 4 cases : 
 * 
 * #1 - war
 * #2 - web.xml
 * #3 - folder that contains at least one war
 * #4 - folder of a deployed war (will use the /WEB-INF/web.xml)
 * 
 * if #3 find war, it will deployed it, if not, will try #4 if it found nothing, #2
 */
public class GrizzlyWebServerDeployer {

    protected static Logger logger = Logger.getLogger("GrizzlyWebServerDeployerLogger");
    public static final String DEFAULT_CONTEXT = "/";
    public static final String WEB_XML_PATH = "WEB-INF" + File.separator + "web.xml";
    
    private GrizzlyWebServer ws = null;
    
    private String locations;
    private String webxmlPath;
    private String libraryPath;
    private String forcedContext;
    
    private boolean waitToStart = false;
    private boolean cometEnabled = false;
    private boolean forceWarDeployment = false;
    private boolean ajpEnabled = false;
    
    private List<String> deployedApplicationList = null;
    
    private int port = 8080;

    public void init(String args[]) throws MalformedURLException, IOException, Exception {
        if (args.length == 0) {
            printHelpAndExit();
        }

        // parse options
        parseOptions(args);
        
        deployedApplicationList = new ArrayList<String>();

    }
    
    private void deployWar(String location) throws Exception {
        webxmlPath = appendWarContentToClassPath(location);
        deploy(webxmlPath, getContext(webxmlPath), webxmlPath + WEB_XML_PATH);
    }
    
    private void deployServlet(String location) throws Exception{
    	deployServlet(location, null);
    }
    
    private void deployServlet(String location, String context) throws Exception {
    	if(context==null){
    		context = getContext("/");
    	}
    	webxmlPath = appendWarContentToClassPath(null);
    	deploy(null, context, location);
    }
    
    private void deployExpandedWar(String location) throws Exception {
        webxmlPath = appendWarContentToClassPath(location);
        deploy(webxmlPath, getContext(webxmlPath), webxmlPath + WEB_XML_PATH);
    }
    
    private Map<String, File> getFile(String location){
    	// remove ending slash if any
        // we need to check if the location is not "/"
        if (location.endsWith("/") && location.length() > 1) {
            location = location.substring(0, location.length() - 1);
        }

        // we try to look of the file's list

        File folder = new File(location);

        if (!folder.exists() || !folder.isDirectory()) {
            return null;
        }

        // we only want folders that contains WEB-INF or war files
        File files[] = folder.listFiles(new FilenameFilter() {

            public boolean accept(File dir, String name) {

                if (name.endsWith(".war")) {
                    return true;
                } else {

                    // we can have 2 options. First, this folder contains a
                    // WEB-INF
                    // Second : contains only folder possibly webapps

                    // Option1
                    if (name.equals("WEB-INF")) {
                        return true;
                    } else {
                        // Option2
                        File file = new File(dir + File.separator + name + File.separator + "WEB-INF");

                        if ((file.exists() && file.isDirectory())) {
                            return true;
                        }
                    }

                    return false;
                }

            }
        });

        // do we have something to deploy
        if (files == null || files.length == 0) {
            return null;
        }

        // sort list.  We want expanded folder first followed by war file.
        Arrays.sort(files, new WarFileComparator());
        

        // filter the list.
        Map<String, File> fileList = new HashMap<String, File>();
        for (File file : files) {
        	
        	// add folders
        	if(file.isDirectory()){
        		fileList.put(file.getName(), file);
        	} else if(file.getName().endsWith(".war") && !forceWarDeployment){
        		String name = file.getName().substring(0,file.getName().length()-".war".length());
        		if(fileList.containsKey(name)){
        			logger.log(Level.INFO, "War file skipped");
        		} else {
        			fileList.put(name, file);
        		}
        		
        	} else if(file.getName().endsWith(".war") && forceWarDeployment){
        		
        		String name = file.getName().substring(0,file.getName().length()-".war".length());
        		
        		// we must remove the folder from the list if found
        		if(fileList.containsKey(name)){
        			fileList.remove(name);
        		}
        		fileList.put(name, file);
        	} else {
        		fileList.put(file.getName(), file);
        	}
        	
        }
        
        return fileList;
    }

    public void deployApplications(String locations) throws Exception {

    	if(locations!=null && locations.length()>0){
	        String[] location = locations.split(File.pathSeparator);
	        
	        for (int i = 0; i < location.length; i++) {
				deployApplication(location[i]);
			}
    	}
    }
    
    private void deployApplication(String location) throws Exception{
    	// #1
        if (location.endsWith(".war")) {
            deployWar(location);
            return;
        }

        // #2
        if (location.endsWith(".xml")) {
        	// use the forcedContext if set
        	deployServlet(location, getForcedContext());
            return;
        }

        // #3-#4 
        //obtain the list of potential war to deploy
        Map<String, File> fileList = getFile(location);
        
        for (File file : fileList.values()) {
        	
            if (file.getName().endsWith(".war")) {
            	deployWar(file.getPath());
            } else {
                /*
                 * we could have these cases
                 *
                 * folder contains multiple expanded war or servlet
                 * 
                 * classes/
				 * jmaki-comet/
				 * jmaki-comet2.war
				 * web.xml
                 * 
                 * In this case, we have 1 web.xml (servlet), 1 expanded war and 1 war file
                 * 
                 * The 3 of them will be loaded. 
                 */

                // #4 : this folder in a expanded war
                File webxmlFile = new File(location + File.separator + WEB_XML_PATH);

                if (webxmlFile.exists()) {
                	deployExpandedWar(location + File.separator);
                } else {

                    // #2 : this folder contains a servlet
                    File webxmlFile2 = new File(location + File.separator + "web.xml");

                    if (webxmlFile2.exists()) {
                        // this one..see #2
                    	deployServlet(webxmlFile2.getPath());
                    } else {

                        // this folder contains multiple war or webapps
                        File webapp = new File(file.getPath() + File.separator + WEB_XML_PATH);

                        if (webapp.exists()) {
                        	deployExpandedWar(file.getPath() + File.separator);
                        }

                    }
                }
            }

        }
    }

    /**
     * Return the context that will be used to deploy the application
     * @param path  : file path where the application is
     * @return the context
     */
    public String getContext(String path) {

        if (path == null || path.trim().length()==0) {
            return DEFAULT_CONTEXT;
        }

        // need to replace "/" and "\\" par File.separator
        // that will fix the case on Windows when user enter c:/... instead of
        // c:\\
        
        path = path.replaceAll("[/\\\\]+", "\\" + "/");
        path = path.replaceAll("\\\\", "\\" + "/");
        
        // remove the trailing File.separator
        if(path.endsWith("/") && path.length()>1){
        	path = path.substring(0,path.length()-1);
        }
        
        int lastIndex = path.lastIndexOf("/");

        if (lastIndex > 0) {
        	path = DEFAULT_CONTEXT + path.substring(lastIndex + 1);
        } else if(lastIndex==-1){
        	// need to add the default_context
        	path = DEFAULT_CONTEXT + path;
        }

        return path;
    }
    
    /**
     * 
     * @param path
     * @return
     */
    public String getServletPath(String path) {

        if (path == null) {
            return DEFAULT_CONTEXT;
        }
        
        // need to replace "\" and "\\" by "/"
        path = path.replaceAll("\\\\", "/");
        
        // the path need to start by "/"
        if(!path.startsWith("/")){
        	path = DEFAULT_CONTEXT + path;
        }
        
        // we could have multiples options
        // /servlet
        // /servlet/
        // /servlet/subpath
        // /servlet/subpath/
        // /servlet/* or /servlet/*.x
        // all theses must return /servlet
        
        // remove the trailing "/"
        if(path.endsWith("/") && path.length()>1){
        	path = path.substring(0,path.length()-1);
        } else if(!path.endsWith("/")){
        	
        	// find the last "/"
        	int index = path.lastIndexOf("/");
        	
        	// find if we have a wildcard "*" or a extension "."
        	if(path.lastIndexOf("*")>index || path.lastIndexOf(".")>index){
            	
        		// do we have something like : /a.cdcdcd or /* or /*.abc
        		if(index==0){
        			return "/";
        		} else {
	            	//remove the urlpattern
	            	if(index<path.length()){
	            		path = path.substring(0,index);
	            	}
        		}
        	} 
        	
        }

        return path;
    }
    
    /**
     * 
     * @param webApp  Contains the info about the web.xml
     * @param context context of the application
     * @return a list of ServletAdapter with the UrlPattern for each Servlet.
     */
    private ConcurrentMap<ServletAdapter, List<String>> getServletAdapterList(WebApp webApp, String context){
    	
    	ConcurrentMap<ServletAdapter, List<String>> servletAdapterMap = new ConcurrentHashMap<ServletAdapter, List<String>>();
    	
    	// validate if we have servletMapping
    	if(webApp.getServletMapping()==null || webApp.getServletMapping().isEmpty()){
    		ServletAdapter sa = new ServletAdapter();
    		
    		sa.setContextPath(context);
    		sa.setServletPath("");
    		
    		List<String> aliasList = new ArrayList<String>();
    		aliasList.add(DEFAULT_CONTEXT);
    		servletAdapterMap.put(sa, aliasList);
    	} else {

	    	for (ServletMapping servletMapping : webApp.getServletMapping()) {
	            ServletAdapter sa = new ServletAdapter();
	    	
		    	List<String> urlPatternList = servletMapping.getUrlPattern();
		
		        //TODO support multiple urlPattern ???  for that the SA need to have multiple servletpath ?
		    	// ex  : urlPattern = /xxx/1.y
		    	//       urlPattern = /xxx/yyy/1.z
		    	//       the servlet path will not be the same.. do we create 2 SA ?
		
		        // WE WILL GET ONLY THE FIRST urlPattern
		        String urlPattern = null;
		        //if empty, assume "/" as context
		        if (urlPatternList == null || urlPatternList.size() == 0) {
		            urlPattern = DEFAULT_CONTEXT;
		        } else {
		            urlPattern = urlPatternList.get(0);
		        }
		        
		        // get the context path. The urlPattern could be "", "/",
		        // "/bla/xyz/..."
		        // just to be sure we don't obtain two slash "//"
		        String servletUrlPattern = null;
		
		        if (!urlPattern.startsWith("/")) {
		        	urlPattern = "/" + urlPattern;
		        } 
		        
		        servletUrlPattern = urlPattern;
		
		        if (servletUrlPattern.indexOf("//") > -1) {
		        	servletUrlPattern = servletUrlPattern.replaceAll("//", "/");
		        }
		        
		        sa.setContextPath(context);
		        
		        // be sure not the get the extension mapping
	            // like /blabla/*.jsp
		        sa.setServletPath(getServletPath(servletUrlPattern));
		        
		        // Set the Servlet
	            setServlet(webApp, sa, servletMapping);
	            
	            List<String> aliasList = new ArrayList<String>();
	            aliasList.add(urlPattern);
	    		servletAdapterMap.put(sa, aliasList);
	    	}
    	}
        
        return servletAdapterMap;
    }

    public void deploy(String rootFolder, String context, String path) throws Exception {
    	
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Will deploy application path=" + path);
        }
        
        // extract the items from the web.xml
        WebApp webApp = extractWebxmlInfo(path);

        if (webApp == null) {
            throw new Exception("invalid");
        }

        // obtain a servletAdapter list
        ConcurrentMap<ServletAdapter, List<String>> servletAdapterList = getServletAdapterList(webApp, context);
        
        
        boolean defaultContextServletPathFound = false;
        
        for (ServletAdapter sa : servletAdapterList.keySet()) {
        	
            // set Filters for this context if there are some
            setFilters(webApp, sa);

            // set Listeners
            setListeners(webApp, sa);
            
            //set root Folder
            if(rootFolder!=null){
            	rootFolder = rootFolder.replaceAll("[/\\\\]+", "\\" + "/");
            	rootFolder = rootFolder.replaceAll("\\\\", "\\" + "/");
            	sa.setRootFolder(rootFolder);
            }
            
            sa.setHandleStaticResources(true);
            
            // create the alias array from the list of urlPattern
            String alias[] = getAlias(sa, servletAdapterList.get(sa));
            
            if(alias==null){
            	alias = new String[]{DEFAULT_CONTEXT};
            }
            
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "sa context=" + sa.getContextPath());
                logger.log(Level.FINEST, "sa servletPath=" + sa.getServletPath());
                
                StringBuffer sb = new StringBuffer();
                sb.append("[");
                for (String item : alias) {
					sb.append(item).append(",");
				}
                sb.deleteCharAt(sb.length()-1);
                sb.append("]");
                logger.log(Level.FINEST, "sa alias=" + sb.toString());
                logger.log(Level.FINEST, "sa rootFolder=" + sa.getRootFolder());
            }
            
			SelectorThread.setWebAppRootPath(rootFolder);
            
			// keep trace of deployed application
			deployedApplicationList.add(context);
			
            ws.addGrizzlyAdapter(sa, alias);
            
            if(DEFAULT_CONTEXT.equals(sa.getServletPath())){
            	defaultContextServletPathFound=true;
            }
            
		}
        
        // we need one servlet that will handle "/"
		if(!defaultContextServletPathFound){
			logger.log(Level.FINEST, "Adding a ServletAdapter to handle / path");
			
			ServletAdapter sa2 = new ServletAdapter();
    		
    		sa2.setContextPath(context);
    		sa2.setServletPath("/");
    		sa2.setHandleStaticResources(true);
			sa2.setRootFolder(rootFolder);
    		
    		ws.addGrizzlyAdapter(sa2, new String[]{context+DEFAULT_CONTEXT});
		}
        
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "deployed application path=" + path);
        }
        
    }
    
    /**
     * 
     * @param sa ServletAdapter
     * @param aliases contains the list of UrlPattern for this ServletAdapter
     * @return the alias list for this ServletAdapter
     */
    public String[] getAlias(ServletAdapter sa, Collection<String> aliases){
    	
    	if(sa==null || aliases==null){
    		return null;
    	}
    	
    	List<String> aliasList = new ArrayList<String>();
    	
        for (String urlPattern : aliases) {
        	
        	String mapping = "";
        	
        	if(!sa.getServletPath().equals(urlPattern)){
        		mapping = urlPattern.substring(urlPattern.indexOf(sa.getServletPath()));
        	}
        	
        	// the alias is the context + servletPath + mapping
        	String aliasTmp = sa.getContextPath() + sa.getServletPath() + mapping; 
        	
            if (aliasTmp.indexOf("//") > -1) {
            	aliasTmp = aliasTmp.replaceAll("//", "/");
	        }
            
            aliasList.add(aliasTmp);
            
            /*
            // if the urlPattern is "/", we want to map the default "" too
            if(urlPattern.equals("/")){
            	aliasList.add("");
            }
            */
            
		}
        
        String[] array = new String[aliasList.size()];
        
        return aliasList.toArray(array);
    }

    public void setLocations(String filename) {
        locations = filename;
    }

    public String getLocations() {
        return locations;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }
    
    public String getLibraryPath() {
		return libraryPath;
	}

    public void setLibraryPath(String path) {
		this.libraryPath = path;
	}

	public boolean getCometEnabled() {
		return cometEnabled;
	}

	public void setCometEnabled(boolean enabled) {
		this.cometEnabled = enabled;
	}
	
	public boolean getAjpEnabled() {
		return ajpEnabled;
	}

	public void setAjpEnabled(boolean enabled) {
		this.ajpEnabled = enabled;
	}
	
	public boolean getForceWarDeployment() {
		return forceWarDeployment;
	}

	public void setForceWarDeployment(boolean forceWarDeployment) {
		this.forceWarDeployment = forceWarDeployment;
	}
	
	public List<String> getDeployedApplicationList() {
		return deployedApplicationList;
	}

	public void setDeployedApplicationList(List<String> deployedApplicationList) {
		this.deployedApplicationList = deployedApplicationList;
	}

	public void printHelpAndExit() {
		System.err.println();
        System.err.println("Usage: " + GrizzlyWebServerDeployer.class.getCanonicalName());
        System.err.println();
        System.err.println("  --application=[path]*       Application(s) path(s).");
        System.err.println("  --port=[port]               Runs Servlet on the specified port.");
        System.err.println("  --context=[context]         Force the context for a servlet.");
        System.err.println("  --dontstart=[true/false]    Won't start the server.");
        System.err.println("  --libraryPath=[path]        Add a libraries folder to the classpath.");
        System.err.println("  --cometEnabled              Starts the AsyncFilter for Comet");
        System.err.println("  --forceWar                  Force war's deployment over a expanded folder.");
        System.err.println("  --ajpEnabled                Enable mod_jk.");
        System.err.println("  --help                      Show this help message.");
        System.err.println("  --longhelp                  Show detailled help message.");
        System.err.println();
        System.err.println("  * are mandatory");
        System.exit(1);
    }
	
	public void printLongHelpAndExit(){
		System.err.println();
        System.err.println("Usage: " + GrizzlyWebServerDeployer.class.getCanonicalName());
        System.err.println();
        System.err.println("  -a, --application=[path]*   Application(s) path(s).");
        System.err.println();
        System.err.println("                              Application(s) deployed can be :");
        System.err.println("                              Servlet(s), war(s) and expanded war folder(s).");
        System.err.println("                              To deploy multiple applications");
        System.err.println("                              use File.pathSeparator"); 
        System.err.println();
        System.err.println("                              Example : -a /app.war:/servlet/web.xml:/warfolder/");
        System.err.println();
        System.err.println("  -p, --port=[port]           Runs Servlet on the specified port.");
        System.err.println("                              Default: 8080");
        System.err.println();
        System.err.println("  -c, --context=[context]     Force the context for a servlet.");
        System.err.println("                              Only valid for servlet deployed using"); 
        System.err.println("                              -a [path]/[filename].xml");
        System.err.println();
        System.err.println("  --dontstart=[true/false]    Won't start the server.");
        System.err.println("                              You will need to call the start method.");
        System.err.println("                              Useful for Unit testing.");
        System.err.println("                              Default : false");
        System.err.println();
        System.err.println("  --libraryPath=[path]        Add a libraries folder to the classpath.");
        System.err.println("                              You can append multiple folders using");
        System.err.println("                              File.pathSeparator");
        System.err.println();
        System.err.println("                              Example : --libraryPath=/libs:/common_libs");
        System.err.println();
        System.err.println("  --cometEnabled=[true/false] Starts the AsyncFilter for Comet.");
        System.err.println("                              You need to active this for comet applications.");
        System.err.println("                              Default : false");
        System.err.println();
        System.err.println("  --forceWar=[true/false]     Force war's deployment over a expanded folder.");
        System.err.println("                              Will deploy the war instead of the folder.");
        System.err.println("                              Default : false");
        System.err.println();
        System.err.println("  --ajpEnabled=[true/false]   Enable mod_jk.");
        System.err.println("                              Default : false");
        System.err.println();
        System.err.println("  Default values will be applied if invalid values are passed.");
        System.err.println();
        System.err.println("  * are mandatory");
        System.exit(1);
	}
	
	public boolean parseOptions(String[] args) {
        // parse options
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if ("-h".equals(arg) || "--help".equals(arg)) {
                printHelpAndExit();
            } else if("--longhelp".equals(arg)){
            	printLongHelpAndExit();
            } else if ("-a".equals(arg)) {
                i++;
                if(i<args.length){
                	setLocations(args[i]);
                }
            } else if (arg.startsWith("--application=")) {
                setLocations(arg.substring("--application=".length(), arg.length()));
            } else if ("-p".equals(arg)) {
                i++;
                if(i<args.length){
                	setPort(Integer.parseInt(args[i]));
                }
            } else if (arg.startsWith("--port=")) {
                String num = arg.substring("--port=".length(), arg.length());
                setPort(Integer.parseInt(num));
            } else if ("-c".equals(arg)) {
                i++;
                if(i<args.length){
                	setForcedContext(args[i]);
                }
            } else if (arg.startsWith("--context=")) {
            	setForcedContext(arg.substring("--context=".length(), arg.length()));
            } else if (arg.startsWith("--dontstart=")) {
                setWaitToStart(Boolean.parseBoolean(arg.substring("--dontstart=".length(), arg.length())));
            } else if (arg.startsWith("--libraryPath=")) {
                String value = arg.substring("--libraryPath=".length(), arg.length());
                setLibraryPath(value);
            } else if (arg.startsWith("--cometEnabled=")) {
            	setCometEnabled(Boolean.parseBoolean(arg.substring("--cometEnabled=".length(), arg.length())));
            } else if(arg.startsWith("--forceWar")){
            	setForceWarDeployment(Boolean.parseBoolean(arg.substring("--forceWar=".length(), arg.length())));
            } else if(arg.startsWith("--ajpEnabled")){
            	setAjpEnabled(Boolean.parseBoolean(arg.substring("--ajpEnabled=".length(), arg.length())));
            }
            
        }

        if (getLocations() == null) {
            System.err.println("Illegal War|Jar file or folder location.");
            printHelpAndExit();
        }
        return true;
    }

    protected void setServlet(WebApp webApp, ServletAdapter sa, ServletMapping servletMapping) {
        // Set the Servlet
        List<com.sun.grizzly.http.webxml.schema.Servlet> servletList = webApp.getServlet();

        //we need to get the servlet according to the servletMapping
        for (com.sun.grizzly.http.webxml.schema.Servlet servletItem : servletList) {

            if (servletItem.getServletName().equalsIgnoreCase(servletMapping.getServletName())) {
                Servlet servlet = (Servlet) ClassLoaderUtil.load(servletItem.getServletClass());
                sa.setServletInstance(servlet);

                List<InitParam> initParamsList = servletItem.getInitParam();

                if (initParamsList != null && initParamsList.size() > 0) {
                    for (InitParam element : initParamsList) {
                        sa.addInitParameter(element.getParamName(), element.getParamValue());
                    }
                }
                break;
            }

        }

    }

    protected void setListeners(WebApp webApp, ServletAdapter sa) {
        // Add the Listener
        List<Listener> listeners = webApp.getListener();

        if (listeners != null) {
            for (Listener element : listeners) {
                sa.addServletListener(element.getListenerClass());
            }
        }
    }

    protected void setFilters(WebApp webApp, ServletAdapter sa) {
        // Add the Filters
        List<com.sun.grizzly.http.webxml.schema.Filter> filterList = webApp.getFilter();

        List<FilterMapping> filterMappingList = webApp.getFilterMapping();

        if (filterList != null && filterList.size() > 0) {
            for (com.sun.grizzly.http.webxml.schema.Filter filterItem : filterList) {

                // we had the filter if the url-pattern is for this context
                // we need to get the right filter-mapping form the name
                for (FilterMapping filterMapping : filterMappingList) {

                    //we need to find in the filterMapping is for this filter
                    if (filterItem.getFilterName().equalsIgnoreCase(filterMapping.getFilterName())) {
                        Filter filter = (Filter) ClassLoaderUtil.load(filterItem.getFilterClass());

                        // initParams
                        List<InitParam> initParamList = filterItem.getInitParam();

                        Map<String, String> initParamsMap = new HashMap<String, String>();
                        if (initParamList != null) {
                            for (InitParam param : initParamList) {
                                initParamsMap.put(param.getParamName(), param.getParamValue());
                            }
                        }

                        sa.addFilter(filter, filterItem.getFilterName(), initParamsMap);

                    }
                }

            }
        }


    }

    protected Map<String, List<Object>> getItemMap(List<Object> itemList) throws Exception {
        // need to find something nicer
        Map<String, List<Object>> itemMap = null;

        if (itemList != null) {
            itemMap = new HashMap<String, List<Object>>();
            // convert it to a Map, will be easier to retrieve values
            for (Object object : itemList) {
                List<Object> list = null;
                String key = object.getClass().getSimpleName();
                if (itemMap.containsKey(key)) {
                    list = itemMap.get(key);
                } else {
                    list = new ArrayList<Object>();
                    itemMap.put(key, list);
                }
                list.add(object);
            }
        } else {
            // error handling when list is null ...
            throw new Exception("invalid");
        }

        return itemMap;
    }

    /**
     * Make available the content of a War file to the current Thread Context
     * ClassLoader.
     *
     * @return the exploded war file location.
     *
     */
    public String appendWarContentToClassPath(String appliPath) throws MalformedURLException, IOException {

        String path = null;
        File file = null;
        URL appRoot = null;
        URL classesURL = null;
        
        // Must be a better way because that sucks!
        String separator = (System.getProperty("os.name").toLowerCase().startsWith("win") ? "/" : "//");

        List<URL> classpathList = new ArrayList<URL>();
        
        if (appliPath != null && (appliPath.endsWith(".war") || appliPath.endsWith(".jar"))) {
            file = new File(appliPath);
            appRoot = new URL("jar:file:" + file.getCanonicalPath() + "!/");
            classesURL = new URL("jar:file:" + file.getCanonicalPath() + "!/WEB-INF/classes/");
            path = ExpandJar.expand(appRoot);
        } else if(appliPath != null){
            path = appliPath;
            classesURL = new URL("file:///" + path + "WEB-INF/classes/");
            appRoot = new URL("file:///" + path);
        }

        if(appliPath != null){
	        String absolutePath = new File(path).getAbsolutePath();
	        File libFiles = new File(absolutePath + File.separator + "WEB-INF" + File.separator + "lib");
	
	        if (libFiles.exists() && libFiles.isDirectory()) {
	            for (int i = 0; i < libFiles.listFiles().length; i++) {
	            	classpathList.add(new URL("jar:file:" + separator + libFiles.listFiles()[i].toString().replace('\\', '/') + "!/"));
	            }
	        } 
        }
        
        if(libraryPath!=null){
        	
        	// look if we have multiple folder
        	String[] array = libraryPath.split(File.pathSeparator);
        	
        	if(array!=null && array.length>0){
        		
        		for (int i = 0; i < array.length; i++) {
					File libFolder = new File(array[i]);
	            	
	            	if (libFolder.exists() && libFolder.isDirectory()) {
	                    for (int k = 0; k < libFolder.listFiles().length; k++) {
	                    	classpathList.add(new URL("jar:file:" + separator + libFolder.listFiles()[k].toString().replace('\\', '/') + "!/"));
	                    }
	                }
				}
        		
        	}
        	 
        }
        
        if(appliPath != null){
        	classpathList.add(appRoot);
        	classpathList.add(classesURL);
        }
        
        if(logger.isLoggable(Level.FINEST)){
        	for (Iterator<URL> iterator = classpathList.iterator(); iterator.hasNext();) {
				URL url = iterator.next();
				logger.log(Level.FINEST, "Classpath contains=" + url);
			}
        }
       
        URL urls[] = new URL[classpathList.size()];
        
        ClassLoader urlClassloader = new URLClassLoader(classpathList.toArray(urls), Thread.currentThread().getContextClassLoader());
        Thread.currentThread().setContextClassLoader(urlClassloader);
        
        //be sure to that the path ends by File.separator
        if(path!=null && !path.endsWith(File.separator)){
        	path = path + File.separator;
        }
        
        return path;
    }

    public WebApp extractWebxmlInfo(String webxml) throws Exception {

        WebappLoader webappLoader = new WebappLoader();
        WebApp webApp = webappLoader.load(webxml);

        return webApp;
    }

    public void launch() {

        try {

            ws = new GrizzlyWebServer(port);

            // need to find which case is it : 1-4.

            /*
             * #1 - /temp/hudson.war #2 - /temp/web.xml or any .xml
             * web-sample.xml #3 - /temp or /temp/ #4 - /temp/hudson or
             * /temp/hudson/
             *
             * if #3 find war, it will deployed it, if not, will try #4 if it
             * found nothing, try web.xml
             */

            if (locations != null) {

                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Application(s) Found = " + locations);
                }

                deployApplications(locations);

            }
            
            // comet
            if(cometEnabled){
                SelectorThread st = ws.getSelectorThread();
                
                AsyncHandler asyncHandler = new DefaultAsyncHandler();
                asyncHandler.addAsyncFilter(new CometAsyncFilter());
                st.setAsyncHandler(asyncHandler);
                
                st.setEnableAsyncExecution(true);
            }
            
            if(ajpEnabled){
            	ws.enableProtocol(PROTOCOL.AJP);
            }
            
            // don't start the server is true: useful for unittest
            if(!waitToStart){
            	ws.start();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {

        if (ws != null) {
            ws.stop();
        }

    }
    
    public void start() throws IOException {

        if (ws != null) {
            ws.start();
        }

    }
    
    private void setWaitToStart(boolean dontStart){
    	waitToStart = dontStart;
    }
    
    private String getForcedContext() {
		return forcedContext;
	}

	private void setForcedContext(String forcedContext) {
		this.forcedContext = forcedContext;
	}

	/**
     * @param args
     */
    public static void main(String[] args) {

        GrizzlyWebServerDeployer ws = new GrizzlyWebServerDeployer();

        try {

            /*
             * We have 4 cases :
             *
             * #1 - war #2 - web.xml #3 - folder that contains at least one war
             * #4 - folder of a deployed war (will use the /WEB-INF/web.xml)
             */
        	
            // ready to launch
            ws.init(args);
            ws.launch();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
