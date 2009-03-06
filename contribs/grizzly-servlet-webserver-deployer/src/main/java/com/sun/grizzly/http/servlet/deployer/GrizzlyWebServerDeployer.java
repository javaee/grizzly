package com.sun.grizzly.http.servlet.deployer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.http.servlet.xsd.FilterMappingType;
import com.sun.grizzly.http.servlet.xsd.FilterType;
import com.sun.grizzly.http.servlet.xsd.ListenerType;
import com.sun.grizzly.http.servlet.xsd.ParamValueType;
import com.sun.grizzly.http.servlet.xsd.ServletMappingType;
import com.sun.grizzly.http.servlet.xsd.ServletType;
import com.sun.grizzly.http.servlet.xsd.UrlPatternType;
import com.sun.grizzly.http.servlet.xsd.WebAppType;
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
	public static final String DEFAULT_CONTEXT = "/";

	private GrizzlyWebServer ws = null;
	private String location;
	private String webxmlPath;
	private int port = 8080;

	public void init(String args[]) throws MalformedURLException, IOException, Exception {
		if (args.length == 0) {
			printHelpAndExit();
		}

		// parse options
		parseOptions(args);

	}

	public void findApplications(String location) throws Exception {

		// #1
		if (location.endsWith(".war")) {
			// webxmlPath + File.separator + "WEB-INF" + File.separator +
			// "web.xml"
			webxmlPath = appendWarContentToClassPath(location);
			deploy(getContext(webxmlPath), webxmlPath + File.separator + "WEB-INF" + File.separator + "web.xml");
			return;
		}

		// #2
		if (location.endsWith(".xml")) {
			// how do we handle the classpath ?
			// the classpath could be mandatory, and we use java -classpath xxx
			// ...
			// or try to find a folder lib ?
			deploy(getContext("/"), location);
			return;
		}

		// #3-#4

		// remove ending slash if any
		// we need to check if the location is not "/"
		if (location.endsWith("/") && location.length() > 1) {
			location = location.substring(0, location.length() - 1);
		}

		// we try to look of the file's list

		File folder = new File(location);

		if (!folder.exists() || !folder.isDirectory()) {
			return;
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
			return;
		}

		for (File file : files) {

			// we keep trace of war deployed so we don't deploy the expanded war
			// if found.
			//TODO

			if (file.getName().endsWith(".war")) {
				webxmlPath = appendWarContentToClassPath(file.getPath());
				deploy(getContext(webxmlPath), webxmlPath + File.separator + "WEB-INF" + File.separator + "web.xml");
			} else {
				/*
				 * we could have these cases
				 * 
				 * folder contains multiple expanded war
				 * 
				 * shit when the war is found and expanded war.. argggg
				 * 
				 * folder/webapp1 folder/webapp2 folder/servlet1
				 * 
				 * 
				 * folder/index.html folder/WEB-INF
				 */

				// #4 : this folder in a expanded war
				File webxmlFile = new File(location + File.separator + "WEB-INF" + File.separator + "web.xml");

				if(webxmlFile.exists()){
					webxmlPath = appendWarContentToClassPath(location + File.separator);
					deploy(getContext(location), webxmlPath);
				} else {

					// #2 : this folder contains a servlet
					File webxmlFile2 = new File(location + File.separator + "web.xml");

					if (webxmlFile2.exists()) {
						// same problem with the classpath.. how do we handle
						// this one..see #2
						webxmlPath = appendWarContentToClassPath(location + File.separator);
						deploy(getContext(location), webxmlPath);
					} else {

						// this folder contains multiple war or webapps
						File webapp = new File(file.getPath() + File.separator + "WEB-INF" + File.separator + "web.xml");

						if (webapp.exists()) {
							// same problem with the classpath.. how do we
							// handle this one..see #2
							webxmlPath = appendWarContentToClassPath(file.getPath());
							deploy(getContext(file.getPath() + File.separator), webapp.getPath());
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

	public void deploy(String context, String path) throws Exception {
		System.out.println("deployed application path=" + path);

		// extract the items from the web.xml
		Map<String, List<Object>> itemMap = extractWebxmlInfo(path);

		if (itemMap == null || !itemMap.containsKey("ServletType")) {
			throw new Exception("invalid");
		}

		for (int i = 0; i < itemMap.get("ServletMappingType").size(); i++) {
			ServletMappingType servletMapping = (ServletMappingType) itemMap.get("ServletMappingType").get(i);

			ServletAdapter sa = new ServletAdapter();

			UrlPatternType urlPattern = servletMapping.getUrlPattern();

			// get the context path. The urlPattern could be "", "/",
			// "/bla/xyz/..."
			// just to be sure we don't obtain two slash "//"
			String contextPath = null;

			if (!urlPattern.getValue().startsWith("/")) {
				contextPath = context + "/" + urlPattern.getValue();
			} else {
				contextPath = context + urlPattern.getValue();
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
			setServlet(sa, itemMap, i);

			// set Filters for this context if there are some
			setFilters(sa, itemMap, contextPath);

			// set Listeners
			setListeners(sa, itemMap);

			System.out.println("sa context=" + sa.getContextPath());
			System.out.println("sa servletPath=" + sa.getServletPath());
			System.out.println("sa alias=" + contextPath);
			ws.addGrizzlyAdapter(sa, new String[] { contextPath });
		}
	}

	public void printHelpAndExit() {
		System.err.println("Usage: " + GrizzlyWebServerDeployer.class.getCanonicalName() + " [options] Servlet_Classname");
		System.err.println();
		System.err.println("    -p, --port=port                  Runs Servlet on the specified port.");
		System.err.println("                                     Default: 8080");
		System.err.println("    -a, --application=application path      The Servlet folder or jar or war location.");
		System.err.println("                                     Default: .");
		System.err.println("    -h, --help                       Show this help message.");
		System.exit(1);
	}

	protected void setWarFilename(String filename) {
		location = filename;
	}

	protected String getWarFilename() {
		return location;
	}

	protected void setPort(int port) {
		this.port = port;
	}

	protected int getPort() {
		return port;
	}

	public boolean parseOptions(String[] args) {
		// parse options
		for (int i = 0; i < args.length - 1; i++) {
			String arg = args[i];

			if ("-h".equals(arg) || "--help".equals(arg)) {
				printHelpAndExit();
			} else if ("-a".equals(arg)) {
				i++;
				setWarFilename(args[i]);
			} else if (arg.startsWith("--application=")) {
				setWarFilename(arg.substring("--application=".length(), arg.length()));
			} else if ("-p".equals(arg)) {
				i++;
				setPort(Integer.parseInt(args[i]));
			} else if (arg.startsWith("--port=")) {
				String num = arg.substring("--port=".length(), arg.length());
				setPort(Integer.parseInt(num));
			}
		}

		if (getWarFilename() == null) {
			System.err.println("Illegal War|Jar file or folder location.");
			printHelpAndExit();
		}
		return true;
	}

	protected void setServlet(ServletAdapter sa, Map<String, List<Object>> map, int index) {
		// Set the Servlet
		ServletType servletType = (ServletType) map.get("ServletType").get(index);

		Servlet servlet = (Servlet) ClassLoaderUtil.load(servletType.getServletClass().getValue());
		sa.setServletInstance(servlet);

		List<ParamValueType> initParams = servletType.getInitParam();

		if (initParams != null) {
			for (Object element : initParams) {
				ParamValueType paramValueType = (ParamValueType) element;
				sa.addInitParameter(paramValueType.getParamName().getValue(), paramValueType.getParamValue().getValue());
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected void setListeners(ServletAdapter sa, Map<String, List<Object>> map) {
		// Add the Listener
		List listeners = map.get("ListenerType");

		if (listeners != null) {
			for (Object element : listeners) {
				ListenerType listenerType = (ListenerType) element;
				sa.addServletListener(listenerType.getListenerClass().getValue());
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected void setFilters(ServletAdapter sa, Map<String, List<Object>> map, String context) {
		// Add the Filters
		List filters = map.get("FilterType");

		List filtersMapping = map.get("FilterMappingType");

		if (filters != null) {
			for (Object element : filters) {
				FilterType filterType = (FilterType) element;

				// we had the filter if the url-pattern is for this context
				// we need to get the right filter-mapping form the name
				for (Object obj : filtersMapping) {
					FilterMappingType filterMapping = (FilterMappingType) obj;

					Filter filter = (Filter) ClassLoaderUtil.load(filterType.getFilterClass().getValue());

					// initParams
					List<ParamValueType> initParams = filterType.getInitParam();

					Map<String, String> initParamsMap = new HashMap<String, String>();
					if (initParams != null) {
						for (Object param : initParams) {
							ParamValueType paramValueType = (ParamValueType) param;
							initParamsMap.put(paramValueType.getParamName().getValue(), paramValueType.getParamValue().getValue());
						}
					}

					sa.addFilter(filter, filterType.getFilterName().getValue(), initParamsMap);
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
	 * Classloader.
	 * 
	 * @return the exploded war file location.
	 * 
	 *         Do we add all the classes from all the webapps in the Thread
	 *         context ? or generate a classpath by webapp ?
	 */
	public String appendWarContentToClassPath(String appliPath) throws MalformedURLException, IOException {

		String path;
		File file = null;
		URL appRoot = null;
		URL classesURL = null;

		boolean isLibrary = false;

		if (appliPath != null && (appliPath.endsWith(".war") || appliPath.endsWith(".jar"))) {
			file = new File(appliPath);
			appRoot = new URL("jar:file:" + file.getCanonicalPath() + "!/");
			classesURL = new URL("jar:file:" + file.getCanonicalPath() + "!/WEB-INF/classes/");
			path = ExpandJar.expand(appRoot);
			isLibrary = true;
		} else {
			path = appliPath;
			classesURL = new URL("file://" + path + "WEB-INF/classes/");
			appRoot = new URL("file://" + path);
			isLibrary = false;
		}

		String absolutePath = new File(path).getAbsolutePath();
		URL[] urls = null;
		File libFiles = new File(absolutePath + File.separator + "WEB-INF" + File.separator + "lib");
		int arraySize = (appRoot == null ? 1 : 2);

		// Must be a better way because that sucks!
		String separator = (System.getProperty("os.name").toLowerCase().startsWith("win") ? "/" : "//");

		if (libFiles.exists() && libFiles.isDirectory()) {
			urls = new URL[libFiles.listFiles().length + arraySize];
			for (int i = 0; i < libFiles.listFiles().length; i++) {
				urls[i] = new URL("jar:file:" + separator + libFiles.listFiles()[i].toString().replace('\\', '/') + "!/");
			}
		} else {
			urls = new URL[arraySize];
		}

		/*
		 * // we must add the jars that are in the root folder // and add the
		 * folders in the classpath if(!isLibrary){ libFiles = new
		 * File(absolutePath + File.separator + "WEB-INF");
		 * 
		 * File[] jars = libFiles.listFiles(new FilenameFilter(){ public boolean
		 * accept(File dir, String name) { if (name.endsWith(".jar")){ return
		 * true; } else { return false; }
		 * 
		 * } });
		 * 
		 * URL[] tempUrls = new URL[jars.length + urls.length];
		 * 
		 * for (int i = 0; i < jars.length; i++) {
		 * 
		 * tempUrls[i] = new URL("jar:file:" + separator +
		 * jars[i].toString().replace('\\','/') + "!/");
		 * 
		 * } System.arraycopy(urls, 0, tempUrls, jars.length, urls.length);
		 * 
		 * urls = tempUrls; }
		 */
		urls[urls.length - 1] = classesURL;
		urls[urls.length - 2] = appRoot;
		ClassLoader urlClassloader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
		Thread.currentThread().setContextClassLoader(urlClassloader);
		return path;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, List<Object>> extractWebxmlInfo(String webxml) throws Exception {

		// not compatible with
		/*
		 * <web-app xmlns="http://java.sun.com/xml/ns/javaee"
		 * xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		 * xsi:schemaLocation=
		 * "http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd"
		 * version="3.0">
		 */

		// check if we can populate the adapter from the web.xml
		// File webxmlFile = new File(webxmlPath + File.separator + "WEB-INF" +
		// File.separator + "web.xml");
		File webxmlFile = new File(webxml);

		// the package name was generated by the ant task.
		// I think we need to load the right version depends on web-app tag
		JAXBContext jc = JAXBContext.newInstance("com.sun.grizzly.http.servlet.xsd");

		// create an Unmarshaller
		Unmarshaller u = jc.createUnmarshaller();

		JAXBElement root = (JAXBElement) u.unmarshal(new FileInputStream(webxmlFile));

		WebAppType webApp = (WebAppType) root.getValue();

		List<Object> itemList = webApp.getDescriptionAndDisplayNameAndIcon();

		return getItemMap(itemList);
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

			if (location != null) {
				System.out.println("location=" + location);

				findApplications(location);

			}

			ws.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void stop() {

		if (ws != null) {
			ws.stop();
		}

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
