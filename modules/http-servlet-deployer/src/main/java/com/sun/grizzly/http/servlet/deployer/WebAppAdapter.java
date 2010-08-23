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

package com.sun.grizzly.http.servlet.deployer;

import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.http.webxml.schema.*;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.util.ClassLoaderUtil;

import javax.servlet.Servlet;
import javax.servlet.Filter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link com.sun.grizzly.http.webxml.schema.WebApp} Adapter.
 *
 * @author Sebastien Dionne
 * @author Hubert Iwaniuk
 * @since Aug 25, 2009
 */
public class WebAppAdapter extends ServletAdapter {

    private static Logger logger = Logger.getLogger("WebAppAdapter");
    private static final String EMPTY_SERVLET_PATH = "";

    private static final String ROOT = "/";

    private Map<GrizzlyAdapter, Set<String>> toRegister = new HashMap<GrizzlyAdapter, Set<String>>();
    private WebApp webApp;
    private ClassLoader webAppCL;
    private String root;
    private String context;
    
    /**
     * Blank constructor
     */
    public WebAppAdapter(){
    	
    }

    /**
     * Default constructor, takes care of setting up adapter.
     *
     * @param root       Root folder, for serving static resources
     * @param context    Context to be deployed to.
     * @param webApp     Web application to be run by this adapter.
     * @param webAppCL   Web application class loader.
     * @param webdefault Default web application.
     */
    public WebAppAdapter(String root, String context, final WebApp webApp, ClassLoader webAppCL, WebApp webdefault) {
        this.root = root;
        this.context = context;
        if (webdefault != null) {
            this.webApp = webApp.mergeWith(webdefault);
        } else {
            this.webApp = webApp;
        }
        this.webAppCL = webAppCL;
        ClassLoader prevCL = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(webAppCL);
        try {
            initialize();
        } finally {
            Thread.currentThread().setContextClassLoader(prevCL);
        }
    }

    private void initialize() {

        boolean blankContextServletPathFound = false;
        boolean defaultContextServletPathFound = false;

        List<String> aliasesUsed = new ArrayList<String>();

        for (Map.Entry<ServletAdapter, List<String>> adapterAliases :
                getServletAdaptersToAlises(webApp, context).entrySet()) {
            ServletAdapter sa = adapterAliases.getKey();
            sa.setClassLoader(webAppCL);

            // set context params
            setContextParams(webApp, sa);

            // set Filters for this context if there are some
            setFilters(webApp, sa);

            // set Listeners
            setListeners(webApp, sa);

            //set root Folder
            sa.addRootFolder(root);

            // create the alias array from the list of urlPattern
            String alias[] = getAlias(sa, adapterAliases.getValue());

            // need to be disabled for JSP
            sa.setHandleStaticResources(false);

            // enabled it if not / or /*
            for (String item : alias) {
                if (item.endsWith(ROOT) || item.endsWith("/*")) {
                    sa.setHandleStaticResources(true);
                }
            }

            if (logger.isLoggable(Level.FINEST)) {
                logServletAdapterConfigurationWithAliases(sa, alias);
            }

            // keep trace of mapping
            List<String> stringList = Arrays.asList(alias);
            aliasesUsed.addAll(stringList);

            toRegister.put(sa, new HashSet<String>(stringList));

            if (ROOT.equals(sa.getServletPath())) {
                defaultContextServletPathFound = true;
            }

            if (EMPTY_SERVLET_PATH.equals(sa.getServletPath())) {
                blankContextServletPathFound = true;
            }
        }

        // we need one servlet that will handle "/"
        if (!defaultContextServletPathFound) {
            logger.log(Level.FINEST, "Adding a ServletAdapter to handle / path");
            createAndInstallServletAdapter(root, context, ROOT);
        }

        // pour les jsp dans le root du context
        if (!blankContextServletPathFound && !aliasesUsed.contains(context + ROOT)) {
            logger.log(Level.FINEST, "Adding a ServletAdapter to handle root path");
            createAndInstallServletAdapter(root, context, EMPTY_SERVLET_PATH);
        }
    }

    private void createAndInstallServletAdapter(
            final String rootFolder, final String context, final String tmpPath) {
        ServletAdapter sa = new ServletAdapter();

        sa.setContextPath(context);
        sa.setServletPath(tmpPath);
        sa.setHandleStaticResources(true);
        sa.addRootFolder(rootFolder);

        logServletAdapterConfiguration(context, sa);

        toRegister.put(sa, Collections.singleton(context + ROOT));
    }

    private static void logServletAdapterConfiguration(final String ctx, final ServletAdapter sa) {
        if (logger.isLoggable(Level.FINEST)) {
            debugServletAdapterConfiguration(sa, ctx + ROOT);
        }
    }

    public void service(final GrizzlyRequest request, final GrizzlyResponse response) {
        ClassLoader prevCL = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(webAppCL);
        try {
            // TODO here we should take care of welcome-file
        } finally {
            Thread.currentThread().setContextClassLoader(prevCL);
        }
    }

    /**
     * @param webApp  Contains the info about the web.xml
     * @param context context of the application
     * @return a list of ServletAdapter with the UrlPattern for each Servlet.
     */
    public Map<ServletAdapter, List<String>> getServletAdaptersToAlises(WebApp webApp, String context) {

        Map<ServletAdapter, List<String>> servletAdapterMap;

        if (webApp == null || webApp.getServletMapping() == null || webApp.getServletMapping().isEmpty()) {
            ServletAdapter sa = new ServletAdapter();

            sa.setContextPath(context);
            sa.setServletPath(EMPTY_SERVLET_PATH);

            servletAdapterMap = new HashMap<ServletAdapter, List<String>>(1);
            servletAdapterMap.put(sa, Arrays.asList(ROOT));
        } else {
            // if we have servletMapping

            final List<ServletMapping> mappings = webApp.getServletMapping();
            servletAdapterMap = new HashMap<ServletAdapter, List<String>>(mappings.size());
            for (ServletMapping servletMapping : mappings) {

                List<String> urlPatternList = servletMapping.getUrlPattern();

                //TODO support multiple urlPattern ???  for that the SA need to have multiple servletpath ?
                // ex  : urlPattern = /xxx/1.y
                //       urlPattern = /xxx/yyy/1.z
                //       the servlet path will not be the same.. do we create 2 SA ?

                // WE WILL GET ONLY THE FIRST urlPattern
                String urlPattern;
                //if empty, assume "/" as context
                if (urlPatternList == null || urlPatternList.isEmpty()) {
                    urlPattern = ROOT;
                } else {
                    urlPattern = urlPatternList.get(0);
                }

                // get the context path. The urlPattern could be "", "/",
                // "/bla/xyz/..."
                // just to be sure we don't obtain two slash "//"

                if (!urlPattern.startsWith(ROOT)) {
                    urlPattern = String.format("%s%s", ROOT, urlPattern);
                }
                String servletUrlPattern = urlPattern;

                if (servletUrlPattern.indexOf("//") > -1) {
                    servletUrlPattern = servletUrlPattern.replaceAll("//", ROOT);
                }

                ServletAdapter sa = createServletAdapter(context, servletUrlPattern);

                // Set the Servlet
                setServlet(webApp, sa, servletMapping);

                servletAdapterMap.put(sa, Arrays.asList(urlPattern));
            }
        }

        return servletAdapterMap;
    }

    protected static void setServlet(WebApp webApp, ServletAdapter sa, ServletMapping servletMapping) {
        //we need to get the servlet according to the servletMapping
        for (com.sun.grizzly.http.webxml.schema.Servlet servletItem : webApp.getServlet()) {

            if (servletItem.getServletName().equalsIgnoreCase(servletMapping.getServletName())) {
                sa.setServletInstance((Servlet) ClassLoaderUtil.load(servletItem.getServletClass()));

                List<InitParam> initParamsList = servletItem.getInitParam();

                if (initParamsList != null && !initParamsList.isEmpty()) {
                    for (InitParam element : initParamsList) {
                        sa.addInitParameter(element.getParamName(), element.getParamValue());
                    }
                }
                
                // load-on-startup
                
                int loadOnStartup = -1;
                
                try {
                	loadOnStartup = Integer.parseInt(servletItem.loadOnStartup);
                } catch (Exception e){
                	
                }
                
                if(loadOnStartup!=-1){
                	sa.setProperty(ServletAdapter.LOAD_ON_STARTUP, Boolean.TRUE);
                }
                
                break;
            }
        }
    }

    /**
     * @param path Path to convert.
     * @return Converted path.
     */
    public static String getServletPath(String path) {

        String result;
        if (path == null) {
            result = ROOT;
        } else {

            // need to replace "\" and "\\" by "/"
            result = path.replaceAll("\\\\", ROOT);

            // the path need to start by "/"
            if (!result.startsWith(ROOT)) {
                result = ROOT + result;
            }

            // we could have multiples options
            // /servlet
            // /servlet/
            // /servlet/subpath
            // /servlet/subpath/
            // /servlet/* or /servlet/*.x
            // all theses must return /servlet

            // remove the trailing "/"
            if (result.endsWith(ROOT) && result.length() > 1) {
                result = result.substring(0, result.length() - 1);
            } else if (!result.endsWith(ROOT)) {

                // find the last "/"
                int index = result.lastIndexOf(ROOT);

                // find if we have a wildcard "*" or a extension "."
                if (result.lastIndexOf('*') > index || result.lastIndexOf('.') > index) {

                    // do we have something like : /a.cdcdcd or /* or /*.abc
                    if (index == 0) {
                        result = ROOT;
                    } else if (index > 0) {
                        //remove the urlpattern
                        if (index < result.length()) {
                            result = result.substring(0, index);
                        }
                    }
                }
            }
        }
        return result;
    }

    protected ServletAdapter createServletAdapter(
            final String context, final String servletUrlPattern) {
        ServletAdapter sa = new ServletAdapter();
        sa.setContextPath(context);

        // be sure not the get the extension mapping
        // like /blabla/*.jsp
        sa.setServletPath(getServletPath(servletUrlPattern));
        return sa;
    }

    protected static void setFilters(WebApp webApp, ServletAdapter sa) {

        if (webApp == null || sa == null) {
            return;
        }

        // Add the Filters
        List<com.sun.grizzly.http.webxml.schema.Filter> filterList = webApp.getFilter();

        List<FilterMapping> filterMappingList = webApp.getFilterMapping();

        if (filterList != null && !filterList.isEmpty()) {
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

    protected static void setListeners(WebApp webApp, ServletAdapter sa) {
        if (webApp == null || sa == null) {
            return;
        }

        // Add the Listener
        List<Listener> listeners = webApp.getListener();

        if (listeners != null) {
            for (Listener element : listeners) {
                sa.addServletListener(element.getListenerClass());
            }
        }
    }

    protected static void setContextParams(WebApp webApp, ServletAdapter sa) {
        if(webApp == null || sa == null) {
            return;
        }

        // Add the context param
        List<ContextParam> contextParmas = webApp.getContextParam();

        if(contextParmas != null) {
            for(ContextParam element : contextParmas) {
                sa.addContextParameter(element.getParamName(), element.getParamValue());
            }
        }
    }

    /**
     * @param sa      ServletAdapter
     * @param aliases contains the list of UrlPattern for this ServletAdapter
     * @return the alias list for this ServletAdapter
     */
    public static String[] getAlias(ServletAdapter sa, Collection<String> aliases) {

        List<String> aliasList;
        if (sa == null || aliases == null) {
            // Default context
            aliasList = Arrays.asList(ROOT);
        } else {

            aliasList = new ArrayList<String>(aliases.size());
            for (String urlPattern : aliases) {

                String mapping = EMPTY_SERVLET_PATH;

                if (!sa.getServletPath().equals(urlPattern) && urlPattern.indexOf(sa.getServletPath()) > -1) {
                    mapping = urlPattern.substring(urlPattern.indexOf(sa.getServletPath()) + sa.getServletPath().length());
                }

                // the alias is the context + servletPath + mapping
                String aliasTmp = String.format("%s%s%s", sa.getContextPath(), sa.getServletPath(), mapping);

                if (aliasTmp.indexOf("//") > -1) {
                    aliasTmp = aliasTmp.replaceAll("//", ROOT);
                }
                aliasList.add(aliasTmp);
            }
        }
        return aliasList.toArray(new String[aliasList.size()]);
    }

    static void logServletAdapterConfigurationWithAliases(
            final ServletAdapter sa, final String[] aliases) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('[');
        for (String item : aliases) {
            sb.append(item).append(',');
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(']');
        debugServletAdapterConfiguration(sa, sb.toString());
    }

    static void debugServletAdapterConfiguration(
            final ServletAdapter sa, final String alias) {
        logger.log(Level.FINEST, "sa context=" + sa.getContextPath());
        logger.log(Level.FINEST, "sa servletPath=" + sa.getServletPath());
        logger.log(Level.FINEST, "sa alias=" + alias);
        logger.log(Level.FINEST, "sa rootFolders=" + sa.getRootFolders());
    }

    public Map<GrizzlyAdapter, Set<String>> getToRegister() {
        return toRegister;
    }
}
