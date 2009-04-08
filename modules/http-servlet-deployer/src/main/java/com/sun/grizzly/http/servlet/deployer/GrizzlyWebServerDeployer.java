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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.Servlet;

import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.comet.CometAsyncFilter;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.embed.GrizzlyWebServer;
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
    private GrizzlyWebServer ws = null;
    
    private String locations;
    private String webxmlPath;
    private String libraryPath;
    private String forcedContext;
    
    private boolean waitToStart = false;
    private boolean startAdapter = false;
    private boolean cometEnabled = false;
    private boolean forceWarDeployment = false;
    
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
    
    private void deployWar(String location) throws Exception{
        webxmlPath = appendWarContentToClassPath(location);
        deploy(webxmlPath, getContext(webxmlPath), webxmlPath + File.separator + "WEB-INF" + File.separator + "web.xml");
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
    
    private void deployExpandedWar(String location) throws Exception{
        webxmlPath = appendWarContentToClassPath(location);
        deploy(webxmlPath, getContext(webxmlPath), webxmlPath + File.separator + "WEB-INF" + File.separator + "web.xml");
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

    public void findApplications(String locations) throws Exception {

    	if(locations!=null && locations.length()>0){
	        String[] location = locations.split(File.pathSeparator);
	        
	        for (int i = 0; i < location.length; i++) {
				findApplication(location[i]);
			}
    	}
    }
    
    private void findApplication(String location) throws Exception{
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
                File webxmlFile = new File(location + File.separator + "WEB-INF" + File.separator + "web.xml");

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
                        File webapp = new File(file.getPath() + File.separator + "WEB-INF" + File.separator + "web.xml");

                        if (webapp.exists()) {
                        	deployExpandedWar(file.getPath() + File.separator);
                        }

                    }
                }
            }

        }
    }

    public String getContext(String path) {

        if (path == null) {
            return DEFAULT_CONTEXT;
        }

        String context = DEFAULT_CONTEXT;

        // need to replace "/" and "\\" par File.separator
        // that will fix the case on Windows when user enter c:/... instead of
        // c:\\
        path = path.replaceAll("[/\\\\]+", "\\" + File.separator);
        path = path.replaceAll("\\\\", "\\" + File.separator);

        int lastIndex = path.lastIndexOf(File.separator);

        if (lastIndex > 0) {
            context = "/" + path.substring(lastIndex + 1);
        }

        return context;
    }

    public void deploy(String rootFolder, String context, String path) throws Exception {
    	
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "deployed application path=" + path);
        }

        // extract the items from the web.xml
        WebApp webApp = extractWebxmlInfo(path);

        if (webApp == null || webApp.getServlet() == null || webApp.getServlet().size() == 0) {
            throw new Exception("invalid");
        }


        for (ServletMapping servletMapping : webApp.getServletMapping()) {
            ServletAdapter sa = new ServletAdapter();

            List<String> urlPatternList = servletMapping.getUrlPattern();

            //TODO support multiple urlPattern ???

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
            String contextPath = null;

            if (!urlPattern.startsWith("/")) {
                contextPath = context + "/" + urlPattern;
            } else {
                contextPath = context + urlPattern;
            }

            if (contextPath.indexOf("//") > -1) {
                contextPath = contextPath.replaceAll("//", "/");
            }

            // set context. if the user pass a context, we will use this one. If
            // null we will use the one from
            // web.xml
            if (urlPattern != null) {
                sa.setContextPath(context);
                // be sure not the get the extension mapping
                // like /blabla/*.jsp
                sa.setServletPath(getContext(contextPath));
            } else {
                // we need at least one context.. need to find which one to set
                // to default !!!sa.setContextPath(DEFAULT_CONTEXT);
            }

            // Set the Servlet
            setServlet(webApp, sa, servletMapping);

            // set Filters for this context if there are some
            setFilters(webApp, sa, contextPath);

            // set Listeners
            setListeners(webApp, sa);
            
            //set root Folder
            if(rootFolder!=null){
            	rootFolder = rootFolder.replaceAll("[/\\\\]+", "\\" + "/");
            	rootFolder = rootFolder.replaceAll("\\\\", "\\" + "/");
            	sa.setRootFolder(rootFolder);
            }
            
            sa.setHandleStaticResources(true);
            
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "sa context=" + sa.getContextPath());
                logger.log(Level.FINEST, "sa servletPath=" + sa.getServletPath());
                logger.log(Level.FINEST, "sa alias=" + contextPath);
                logger.log(Level.FINEST, "sa rootFolder=" + sa.getRootFolder());
            }
            
			SelectorThread.setWebAppRootPath(rootFolder);
            
			//keep trace of deployed application
			deployedApplicationList.add(context);
			
            ws.addGrizzlyAdapter(sa, new String[]{contextPath});
            
        }
        
    }

    protected void setLocations(String filename) {
        locations = filename;
    }

    protected String getLocations() {
        return locations;
    }

    protected void setPort(int port) {
        this.port = port;
    }

    protected int getPort() {
        return port;
    }
    
    protected String getLibraryPath() {
		return libraryPath;
	}

    protected void setLibraryPath(String path) {
		this.libraryPath = path;
	}

	public boolean getStartAdapter() {
		return startAdapter;
	}

	public void setStartAdapter(boolean enabled) {
		this.startAdapter = enabled;
	}

	public boolean getCometEnabled() {
		return cometEnabled;
	}

	public void setCometEnabled(boolean enabled) {
		this.cometEnabled = enabled;
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
        System.err.println("Usage: " + GrizzlyWebServerDeployer.class.getCanonicalName());
        System.err.println();
        System.err.println("    -p, --port=port                  Runs Servlet on the specified port.");
        System.err.println("                                     Default: 8080");
        System.err.println("    -a, --application=application path      The Servlet folder or jar or war location.  Can append multiple application.  Separator = File.pathSeparator");
        System.err.println("    -c, --context=context            The context that will be use for servlet or warfile");
        System.err.println("    --dontstart=                     Default: false, Will not start the server until the start method is called.  Useful for Unit test");
        System.err.println("    --libraryPath                    Add a libraries folder to the classpath.  Can append multiple folder.  Separator = File.pathSeparator");
        System.err.println("    --startAdapter                   Will start all the servlets");
        System.err.println("    --cometEnabled               Will start the AsyncFilter for Comet");
        System.err.println("    --forceWarDeployment             Will force deployment of a war file over a expanded folder");
        System.err.println("    -h, --help                       Show this help message.");
        System.exit(1);
    }
	
	public boolean parseOptions(String[] args) {
        // parse options
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if ("-h".equals(arg) || "--help".equals(arg)) {
                printHelpAndExit();
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
                String waitToStart = arg.substring("--dontstart=".length(), arg.length());
                setWaitToStart(waitToStart);
            } else if (arg.startsWith("--libraryPath=")) {
                String value = arg.substring("--libraryPath=".length(), arg.length());
                setLibraryPath(value);
            } else if (arg.startsWith("--startAdapter")) {
                setStartAdapter(true);
            } else if (arg.startsWith("--cometEnabled")) {
            	setCometEnabled(true);
            } else if(arg.startsWith("--forceWarDeployment")){
            	setForceWarDeployment(true);
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

    protected void setFilters(WebApp webApp, ServletAdapter sa, String context) {
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
            classesURL = new URL("file://" + path + "WEB-INF/classes/");
            appRoot = new URL("file://" + path);
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
        
        return path;
    }

    protected WebApp extractWebxmlInfo(String webxml) throws Exception {

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

                findApplications(locations);

            }
            
            // comet
            if(cometEnabled){
                SelectorThread st = ws.getSelectorThread();
                
                AsyncHandler asyncHandler = new DefaultAsyncHandler();
                asyncHandler.addAsyncFilter(new CometAsyncFilter());
                st.setAsyncHandler(asyncHandler);
                
                st.setEnableAsyncExecution(true);
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
    
    private void setWaitToStart(String dontStart){
    	if(dontStart!=null && dontStart.equalsIgnoreCase("true")){
    		waitToStart = true;
    	}
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
