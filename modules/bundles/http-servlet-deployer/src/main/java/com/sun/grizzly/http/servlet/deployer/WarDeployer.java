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

import com.sun.grizzly.http.deployer.DeployException;
import com.sun.grizzly.http.deployer.FromURIDeployer;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.http.servlet.deployer.annotation.AnnotationParser;
import com.sun.grizzly.http.webxml.WebappLoader;
import com.sun.grizzly.http.webxml.schema.*;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.util.ClassLoaderUtil;
import com.sun.grizzly.util.ExpandJar;

import javax.servlet.Filter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link FromURIDeployer} for War files.
 *
 * @author Hubert Iwaniuk
 * @since Sep 28, 2009
 */
public class WarDeployer extends FromURIDeployer<WarDeployable, WarDeploymentConfiguration> {
    private static Logger logger = Logger.getLogger("com.sun.grizzly.http.servlet.deployer.WarDeployer");

    private static final String JAVA_IO_TMPDIR = "java.io.tmpdir";
    private static final String EMPTY_SERVLET_PATH = "";
    private static final String ROOT = "/";

    /**
     * {@inheritDoc}
     */
    protected Map<GrizzlyAdapter, Set<String>> convert(final WarDeployable toDeploy, final WarDeploymentConfiguration configuration) throws DeployException {

        String root = toDeploy.location;
        if (root != null) {
            root = GrizzlyWebServerDeployer.fixPath(root);
        }

        WebApp webApp;
        if (configuration.webDefault != null) {
            webApp = toDeploy.webApp.mergeWith(configuration.webDefault);
        } else {
            webApp = toDeploy.webApp;
        }
        URLClassLoader loader = new URLClassLoader(
                toDeploy.webAppCL != null ? toDeploy.webAppCL.getURLs() : new URL[0],
                configuration.serverLibLoader);
        ClassLoader prevCL = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            return createDeployments(root, webApp, configuration.ctx, loader);
        } finally {
            Thread.currentThread().setContextClassLoader(prevCL);
        }
    }

    /**
     * {@inheritDoc}
     */
    protected WarDeployable fromURI(URI uri, WarDeploymentConfiguration configuration) throws DeployException {
        WarDeployable result;
        if ("file".equals(uri.getScheme())) {
            File warFile = new File(uri);
            if (warFile.exists()) {
                String explodedLocation;
                URLClassLoader warCL;
                if (warFile.isFile()) {
                    try {
                        String fileLocation = warFile.getAbsolutePath().replace('\\', '/');
                        explodedLocation = explodeWarFile(new URI("jar:file:" + fileLocation + "!/"));
                        warCL = createWarCL(fileLocation, null);
                        // TODO if someone can tell me why directory based behavior is not working, beers are on me.
                        // warCL = createWarCL(explodedLocation, configuration.serverLibLoader);
                    } catch (URISyntaxException e) {
                        throw new DeployException("Error.", e);
                    }
                } else {
                    explodedLocation = warFile.getAbsolutePath();
                    warCL = createWarCL(explodedLocation, null);
                }
                WebApp webApp = parseWebXml(uri, explodedLocation);

                // if metadata-complete skip annotations
                if (!webApp.getMetadataComplete()) {
                    logger.fine("Will append Annotations to the WebApp");
                    try {
                        AnnotationParser parser = new AnnotationParser();
                        WebApp webAppAnot = parser.parseAnnotation(warCL);
                        webApp.mergeWithAnnotations(webAppAnot);

                    } catch (Throwable t) {
                        logger.warning("Unable to load annotations : " + t.getMessage());
                    }
                } else {
                    logger.info("Skipping Annotation for this URI : " + uri);
                }

                result = new WarDeployable(webApp, explodedLocation, warCL);
            } else {
                throw new DeployException("War file does not exists: " + uri);
            }
        } else {
            throw new DeployException("Unsupported schema: " + uri.getScheme());
        }
        return result;
    }

    private Map<GrizzlyAdapter, Set<String>> createDeployments(String root, WebApp webApp, String context, ClassLoader webAppCL) {
        boolean blankContextServletPathFound = false;
        boolean defaultContextServletPathFound = false;

        List<String> aliasesUsed = new ArrayList<String>();
        Map<GrizzlyAdapter, Set<String>> result = new HashMap<GrizzlyAdapter, Set<String>>();

        for (Map.Entry<ServletAdapter, List<String>> adapterAliases :
                WebAppAdapter.getServletAdaptersToAlises(webApp, context).entrySet()) {

            ServletAdapter sa = adapterAliases.getKey();
            sa.setClassLoader(webAppCL);

            // set context params
            for (ContextParam element : webApp.contextParam) {
                sa.addContextParameter(element.paramName, element.paramValue);
            }

            // set Filters for this context if there are some
            for (com.sun.grizzly.http.webxml.schema.Filter filterItem : webApp.filter) {

                // we had the filter if the url-pattern is for this context
                // we need to get the right filter-mapping form the name
                for (FilterMapping filterMapping : webApp.filterMapping) {

                    //we need to find in the filterMapping is for this filter
                    if (filterItem.filterName.equalsIgnoreCase(filterMapping.filterName)) {
                        Filter filter = (Filter) ClassLoaderUtil.load(filterItem.filterClass, webAppCL);

                        Map<String, String> initParamsMap = new HashMap<String, String>();
                        for (InitParam param : filterItem.initParam) {
                            initParamsMap.put(param.paramName, param.paramValue);
                        }
                        sa.addFilter(filter, filterItem.filterName, initParamsMap);
                    }
                }
            }

            // set Listeners
            for (Listener element : webApp.listener) {
                sa.addServletListener(element.listenerClass);
            }

            //set root Folder
            sa.addRootFolder(root);

            // create the alias array from the list of urlPattern
            String alias[] = WebAppAdapter.getAlias(sa, adapterAliases.getValue());

            // need to be disabled for JSP
            sa.setHandleStaticResources(false);

            // enabled it if not / or /*
            for (String item : alias) {
                if (item.endsWith(ROOT) || item.endsWith("/*")) {
                    sa.setHandleStaticResources(true);
                }
            }

            // keep trace of mapping
            List<String> stringList = Arrays.asList(alias);
            aliasesUsed.addAll(stringList);

            result.put(sa, new HashSet<String>(stringList));

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
            result.putAll(createAndInstallServletAdapter(root, context, ROOT));
        }

        // pour les jsp dans le root du context
        if (!blankContextServletPathFound && !aliasesUsed.contains(context + ROOT)) {
            logger.log(Level.FINEST, "Adding a ServletAdapter to handle root path");
            result.putAll(createAndInstallServletAdapter(root, context, EMPTY_SERVLET_PATH));
        }
        return result;
    }

    private Map<GrizzlyAdapter, Set<String>> createAndInstallServletAdapter(
            final String rootFolder, final String context, final String tmpPath) {
        Map<GrizzlyAdapter, Set<String>> result = new HashMap<GrizzlyAdapter, Set<String>>(1);

        ServletAdapter sa = new ServletAdapter();
        sa.setContextPath(context);
        sa.setServletPath(tmpPath);
        sa.setHandleStaticResources(true);
        sa.addRootFolder(rootFolder);

        result.put(sa, Collections.singleton(context + ROOT));
        return result;
    }


    private static URLClassLoader createWarCL(String explodedLocation, URLClassLoader serverLibLoader) throws DeployException {
        URLClassLoader warCL;
        String oldTmp = System.getProperty(JAVA_IO_TMPDIR);
        System.setProperty(JAVA_IO_TMPDIR, new File("work").getAbsolutePath());
        try {
            warCL = ClassLoaderUtil.createURLClassLoader(explodedLocation, serverLibLoader);
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "War class path contains:");
                for (URL url : warCL.getURLs()) {
                    logger.log(Level.FINEST, url.toString());
                }
            }
        } catch (IOException e) {
            throw new DeployException(String.format("Error while loading libs from '%s'.", explodedLocation), e);
        } finally {
            System.setProperty(JAVA_IO_TMPDIR, oldTmp);
        }
        return warCL;
    }

    private static WebApp parseWebXml(URI uri, String explodedLocation) throws DeployException {
        WebApp webApp;
        try {
            webApp = WebappLoader.load(explodedLocation + GrizzlyWebServerDeployer.WEB_XML_PATH);
        } catch (Exception e) {
            throw new DeployException(String.format("Error parsing web.xml for '%s'.", uri), e);
        }
        return webApp;
    }

    private static String explodeWarFile(URI uri) throws DeployException {
        String explodedLocation = null;
        String oldTmp = System.getProperty(JAVA_IO_TMPDIR);
        System.setProperty(JAVA_IO_TMPDIR, new File("work").getAbsolutePath());
        try {
            explodedLocation = ExpandJar.expand(uri.toURL());
        } catch (IOException e) {
            throw new DeployException(String.format("Error extracting contents of war file '%s'.", uri), e);
        } finally {
            System.setProperty(JAVA_IO_TMPDIR, oldTmp);
        }
        return explodedLocation;
    }
}
