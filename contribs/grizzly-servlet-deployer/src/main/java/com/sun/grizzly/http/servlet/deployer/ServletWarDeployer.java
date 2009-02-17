/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
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
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;

import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.comet.CometAsyncFilter;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.http.servlet.xsd.FilterType;
import com.sun.grizzly.http.servlet.xsd.ListenerType;
import com.sun.grizzly.http.servlet.xsd.ParamValueType;
import com.sun.grizzly.http.servlet.xsd.ServletMappingType;
import com.sun.grizzly.http.servlet.xsd.ServletType;
import com.sun.grizzly.http.servlet.xsd.UrlPatternType;
import com.sun.grizzly.http.servlet.xsd.WebAppType;
import com.sun.grizzly.standalone.servlet.ServletLauncher;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.util.ClassLoaderUtil;

/**
 * Basic startup class used when Grizzly standalone is used
 * The web schemas from Glassfish are used to parse web.xml.
 * (glassfish/appserv-commons/schemas)
 * 
 * Based on the example from JF-Arcard : Hudson-on-grizzly
 * 
 * example : java com.sun.grizzly.servlet.deployer.ServletWarDeployer -p 8080 -a hudson.war -c /hudson
 * 
 * https://grizzly.dev.java.net/issues/show_bug.cgi?id=363
 * @author Sebastien Dionne
 */
public class ServletWarDeployer extends ServletLauncher {

    public static final String DEFAULT_CONTEXT = "/";
    private String context = null;

    /**
     * 
     * @return the contextPath
     */
    public String getContext() {
        return context;
    }

    /**
     * Set the contextPath for the war.
     * @param context context path
     */
    public void setContext(String context) {
        if (!context.startsWith("/")) {
            this.context = "/" + context;
        } else {
            this.context = context;
        }
    }

    public static void main(String args[]) throws Exception {
        ServletWarDeployer sl = new ServletWarDeployer();
        sl.start(args);
    }

    @Override
    public Adapter configureAdapter(SelectorThread st) {
        ServletAdapter adapter = new ServletAdapter();
        String warPath = SelectorThread.getWebAppRootPath();

        return initServletAdapter(adapter, warPath);
    }

    /**
     * Init the ServletAdapter from the web.xml if found.  If web.xml is not found, 
     * the ServletAdapter will be initiated manually.
     * 
     * @param adapter ServletAdapter
     * @param warPath the war path
     * @return ServletAdapter
     */
    private ServletAdapter initServletAdapter(ServletAdapter adapter, String warPath) {

        // check if we can populate the adapter from the web.xml
        File webxmlFile = new File(warPath + File.separator + "WEB-INF" + File.separator + "web.xml");

        if (webxmlFile.exists()) {
            try {
                populate(adapter, warPath, webxmlFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            populateManually(adapter, warPath);
        }

        return adapter;
    }

    /**
     * Initialization from warPath/WEB-INF/web.xml .
     * 
     *  
     * @param adapter ServletAdapter
     * @param warPath warpath
     * @param webxmlFile File on web.xml
     * @throws Exception
     */
    private void populate(ServletAdapter adapter, String warPath, File webxmlFile) throws Exception {

        adapter.setRootFolder(warPath);
        adapter.setHandleStaticResources(true);

        // the package name was generated by the ant task.
        JAXBContext jc = JAXBContext.newInstance("com.sun.grizzly.http.servlet.xsd");

        // create an Unmarshaller
        Unmarshaller u = jc.createUnmarshaller();

        JAXBElement root = (JAXBElement) u.unmarshal(new FileInputStream(webxmlFile));

        WebAppType webApp = (WebAppType) root.getValue();

        List<Object> itemList = webApp.getDescriptionAndDisplayNameAndIcon();

        // need to find something nicer
        Map<String, List<Object>> itemMap = null;

        if (itemList != null) {
            itemMap = new HashMap<String, List<Object>>();
            // convert it to a Map, will be easier to retrieve values
            for (Iterator iterator = itemList.iterator(); iterator.hasNext();) {
                Object object = (Object) iterator.next();

                List<Object> list = null;
                String key = object.getClass().getSimpleName();
                if (itemMap.containsKey(key)) {
                    list = itemMap.get(key);
                } else {
                    list = new ArrayList();
                    itemMap.put(key, list);
                }
                list.add(object);
            }
        } else {
            // error handling when list is null ...
            throw new Exception("invalid");
        }

        if (!itemMap.containsKey("ServletType")) {
            throw new Exception("invalid");
        }
        
        // Set the Servlet //why just one ?
        ServletMappingType servletMapping = (ServletMappingType)itemMap.get("ServletMappingType").get(0);
        
        UrlPatternType urlPattern = servletMapping.getUrlPattern();
        
        // set context.  if the user pass a context, we will use this one.  If null we will use the one from
        // web.xml
        if(context==null && urlPattern!=null){
        	adapter.setContextPath(urlPattern.getValue());
        } else {
        	adapter.setContextPath(DEFAULT_CONTEXT);
        }
        
        // Set the Servlet //why just one ?
        ServletType servletType = (ServletType) itemMap.get("ServletType").get(0);

        Servlet servlet = (Servlet) ClassLoaderUtil.load(servletType.getServletClass().getValue());
        adapter.setServletInstance(servlet);

        List<ParamValueType> initParams = servletType.getInitParam();

        if (initParams != null) {
            for (Object element : initParams) {
                ParamValueType paramValueType = (ParamValueType) element;
                adapter.addInitParameter(paramValueType.getParamName().getValue(), paramValueType.getParamValue().getValue());
            }
        }

        // Add the Filters
        List filters = itemMap.get("FilterType");

        if (filters != null) {
            for (Object element : filters) {
                FilterType filterType = (FilterType) element;

                Filter filter = (Filter) ClassLoaderUtil.load(filterType.getFilterClass().getValue());
                adapter.addFilter(filter, filterType.getFilterName().getValue(), null);
            }
        }

        // Add the Listener
        List listeners = itemMap.get("ListenerType");

        if (listeners != null) {
            for (Object element : listeners) {
                ListenerType listenerType = (ListenerType) element;
                adapter.addServletListener(listenerType.getListenerClass().getValue());
            }
        }
        
    }

    /**
     * Default initialization when web.xml is not found in warPath/WEB-INF/
     * @param adapter ServletAdapter
     * @param warPath the war path
     */
    private void populateManually(ServletAdapter adapter, String warPath) {
        adapter.setRootFolder(warPath);
        adapter.setHandleStaticResources(true);

        adapter.setContextPath("context");

        // Set the Servlet
        Servlet servlet = (Servlet) ClassLoaderUtil.load("org.kohsuke.stapler.Stapler");
        adapter.setServletInstance(servlet);

        // Add the Filter
        Filter filter = (Filter) ClassLoaderUtil.load("hudson.security.HudsonFilter");
        adapter.addFilter(filter, "authentication-filter", null);

        adapter.addInitParameter("default-encoding", "UTF-8");

        // Add the Filter
        filter = (Filter) ClassLoaderUtil.load("hudson.util.PluginServletFilter");
        adapter.addFilter(filter, "plugins-filter", null);

        // Add the Listener
        adapter.addServletListener("hudson.WebAppMain");
    }

    @Override
    public SelectorThread createSelectorThread(String[] args) throws Exception{
        SelectorThread st = super.createSelectorThread(args);
        st.setEnableAsyncExecution(true);
        AsyncHandler asyncHandler = new DefaultAsyncHandler();
        asyncHandler.addAsyncFilter(new CometAsyncFilter()); 
        st.setAsyncHandler(asyncHandler);
        return st;
    }  

    @Override
    public boolean parseOptions(String[] args) {
        // parse options
        for (int i = 0; i < args.length - 1; i++) {
            String arg = args[i];

            if ("-h".equals(arg) || "--help".equals(arg)) {
                printHelpAndExit();
            } else if ("-a".equals(arg)) {
                i++;
                applicationLoc = args[i];
            } else if (arg.startsWith("--application=")) {
                applicationLoc = arg.substring("--application=".length(), arg.length());
            } else if ("-p".equals(arg)) {
                i++;
                setPort(args[i]);
            } else if (arg.startsWith("--port=")) {
                String num = arg.substring("--port=".length(), arg.length());
                setPort(num);
            } else if ("-c".equals(arg)) {
                i++;
                setContext(args[i]);
            } else if (arg.startsWith("--context=")) {
            	// optional
                String num = arg.substring("--context=".length(), arg.length());
                setContext(num);
            }
        }

        if (applicationLoc == null) {
            System.err.println("Illegal War|Jar file or folder location.");
            printHelpAndExit();
        }
        return true;
    }
}
