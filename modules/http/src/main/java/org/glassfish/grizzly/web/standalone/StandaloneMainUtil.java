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
package org.glassfish.grizzly.web.standalone;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Level;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.web.WebFilter;
import org.glassfish.grizzly.web.WebFilterConfig;
import org.glassfish.grizzly.web.container.Adapter;
import org.glassfish.grizzly.web.container.util.ExpandJar;

/**
 * Abstract class that can be extended when Main/Launcher class are required. 
 * Invoking the {@link #start} method of this class will properly parse the command
 * line, set the appropriate {@link Classloader} and if a war/jar is passed as
 * argument, explode it and happens its WEB-INF/classes to the context Classloader
 * and finally start a configured instance of {@link WebFilter}
 *
 * @author Jeanfrancois Arcand
 */
public abstract class StandaloneMainUtil {

    private static int port = 8080;
     private TCPNIOTransport transport;


    public StandaloneMainUtil() {
    }

    /**
     * Configure and start a {@link WebFilter}
     * @param args the command line arguments.
     */
    public void start(String args[]) throws Exception {
        WebFilter webFilter = createWebFilter(args);

        transport = TransportFactory.getInstance().createTCPTransport();
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(webFilter);
        try {
            webFilter.initialize();
            transport.bind(port);
            transport.start();
        } catch (Exception ex) {
            WebFilter.logger().log(Level.WARNING,"",ex);
        }        
    }

    /**
     * Stop {@link WebFilter}
     */
    public void stop() throws Exception {
        if (transport!= null) transport.stop();
    }

    /**
     * Create a single {@link WebFilter} and configure it using the
     * command line passed arguments. This method will invoke {@link #parseOptions},
     * then {@link #parseApplicationLocation}, {@link #appendWarContentToClassPath} and 
     * finally {@link #configureAdapter}
     * @param args The command line arguments.
     * @return An instance of ready to start WebFilter
     */
    public WebFilter createWebFilter(String args[]) throws Exception {
        if (args.length == 0) {
            printHelpAndExit();
        }
 
        // parse options
        parseOptions(args);
        String appliPath = parseApplicationLocation(args);
        appliPath = appendWarContentToClassPath(appliPath);

        WebFilterConfig webConfig = new WebFilterConfig();
        webConfig.setAdapter(configureAdapter(webConfig));
        webConfig.setDisplayConfiguration(true);
        webConfig.setWebAppRootPath(appliPath);
 
        WebFilter webFilter = new WebFilter("MyHtttpServer", webConfig);
        webConfig.setInterceptor(new StaticHandler(webFilter));

        return webFilter;
    }

    /**
     * Make available the content of a War file to the current Thread Context 
     * Classloader.
     * @return the exploded war file location.
     */
    public String appendWarContentToClassPath(String appliPath) throws MalformedURLException, IOException {

        String path;
        File file = null;
        URL appRoot = null;
        URL classesURL = null;
        if (appliPath != null &&
                (appliPath.endsWith(".war") || appliPath.endsWith(".jar"))) {
            file = new File(appliPath);
            appRoot = new URL("jar:file:" +
                    file.getCanonicalPath() + "!/");
            classesURL = new URL("jar:file:" +
                    file.getCanonicalPath() + "!/WEB-INF/classes/");
            path = ExpandJar.expand(appRoot);
        } else {
            path = appliPath;
            classesURL = new URL("file://" + path + "WEB-INF/classes/");
            appRoot = new URL("file://" + path);
        }

        String absolutePath = new File(path).getAbsolutePath();
        WebFilter.logger().info("Servicing resources from: " + absolutePath);
        URL[] urls = null;
        File libFiles = new File(absolutePath + File.separator + "WEB-INF" + File.separator + "lib");
        int arraySize = (appRoot == null ? 1 : 2);

        //Must be a better way because that sucks!
        String separator = (System.getProperty("os.name").toLowerCase().startsWith("win") ? "/" : "//");

        if (libFiles.exists() && libFiles.isDirectory()) {
            urls = new URL[libFiles.listFiles().length + arraySize];
            for (int i = 0; i < libFiles.listFiles().length; i++) {
                urls[i] = new URL("jar:file:" + separator + libFiles.listFiles()[i].toString().replace('\\', '/') + "!/");
            }
        } else {
            urls = new URL[arraySize];
        }

        urls[urls.length - 1] = classesURL;
        urls[urls.length - 2] = appRoot;
        ClassLoader urlClassloader = new URLClassLoader(urls,
                Thread.currentThread().getContextClassLoader());
        Thread.currentThread().setContextClassLoader(urlClassloader);
        return path;
    }


    /**
     * Set the port the {@link WebFilter} will listen.
     * @param num
     */
    public void setPort(String num) {
        try {
            port = Integer.parseInt(num);
        } catch (NumberFormatException e) {
            System.err.println("Illegal port number -- " + num);
            printHelpAndExit();
        }

    }

    /**
     * This method will be invoked when unexpected arguments are passed
     * to the {@link #createWebFilter}.
     */
    public abstract void printHelpAndExit();

    /**
     * Validate the command line options.
     * @param args the command line arguments.
     * @return true if the options are well formed.
     */
    public abstract boolean parseOptions(String[] args);

    /**
     * Configure the {@link WebFilterConfig#setAdapter}
     * @param st
     * #return an instance of an Adapter.
     */
    public abstract Adapter configureAdapter(WebFilterConfig wc);

    /**
     * Parse the current command line, and return the location of the 
     * war/jar/static resource location
     * file passed as argument.
     * @param args the command line arguments.
     * @return the application path, or null if not defined.
     */
    public abstract String parseApplicationLocation(String[] args);
}
