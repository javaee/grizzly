/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.standalone;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.util.ClassLoaderUtil;
import com.sun.grizzly.util.ExpandJar;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;

/**
 * Abstract class that can be extended when Main/Launcher class are required. 
 * Invoking the {@link #start} method of this class will properly parse the command
 * line, set the appropriate {@link ClassLoader} and if a war/jar is passed as
 * argument, explode it and happens its WEB-INF/classes to the context Classloader
 * and finally start a configured instance of {@link SelectorThread}
 *
 * @author Jeanfrancois Arcand
 */
public abstract class StandaloneMainUtil {

    /**
     * System property for the {@link SelectorThread} value.
     */
    public static final String SELECTOR_THREAD = "com.sun.grizzly.selectorThread";
    public static final String ADAPTER = "com.sun.grizzly.adapterClass";
    
    private int port = 8080;
    private InetAddress address;
    private long t1 = 0L;

    public StandaloneMainUtil() {
        try {
            address = InetAddress.getLocalHost();
        } catch (UnknownHostException uhe) {
            throw new IllegalStateException(uhe);
        }
    }
    
    
    /**
     * Configure and start a {@link SelectorThread}
     * @param args the command line arguments.
     */
    public void start(String args[]) throws Exception{
        SelectorThread st = createSelectorThread(args);
        startSelectorThread(st);
    }
    
    
    /**
     * Stop {@link SelectorThread}
     */
    public void stop() throws Exception{
        SelectorThread.getSelector(address, port).stopEndpoint();
    }   

    
    /**
     * Create a single {@link SelectorThread} and configure it using the 
     * command line passed arguments. This method will invoke {@link #parseOptions},
     * then {@link #parseApplicationLocation}, {@link #appendWarContentToClassPath} and 
     * finally {@link #configureAdapter}
     * @param args The command line arguments.
     * @return An instance of ready to start SelectorThread
     */
    public SelectorThread createSelectorThread(String args[]) throws Exception {        
        if (args.length == 0) {
            printHelpAndExit();
        }
        t1 = System.currentTimeMillis();

        // parse options
        parseOptions(args);
        String appliPath = parseApplicationLocation(args);
        appliPath = appendWarContentToClassPath(appliPath);

        final SelectorThread st;
        String selectorThreadClassname = System.getProperty(SELECTOR_THREAD);
        if (selectorThreadClassname != null) {
            st = (SelectorThread) ClassLoaderUtil.load(selectorThreadClassname);
        } else {
            st = new SelectorThread() {
                @Override
                public void listen() throws InstantiationException, IOException {
                    super.listen();
                    logger.info("Server started in " 
                            + (System.currentTimeMillis() - t1) + " milliseconds.");
                }
            };
        }
        st.setAlgorithmClassName(StaticStreamAlgorithm.class.getName());
        st.setPort(port);
        st.setWebAppRootPath(appliPath);

        st.setAdapter(configureAdapter(st));
        return st;
    }

       
    
    /**
     * Make available the content of a War file to the current Thread Context 
     * Classloader.
     * @return the exploded war file location.
     */
    public String appendWarContentToClassPath(String appliPath) throws IOException{
        
        String path;
        File file;
        URL appRoot;
        URL classesURL;
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

	String absolutePath =  new File(path).getAbsolutePath();
        SelectorThread.logger().info("Servicing resources from: " + absolutePath);
        URL[] urls;
        File libFiles = new File(absolutePath + File.separator + "WEB-INF"+ File.separator + "lib");
        int arraySize = appRoot == null ? 1:2;

        //Must be a better way because that sucks!
        String separator = System.getProperty("os.name")
                .toLowerCase().startsWith("win")? "/" : "//";

        if (libFiles.exists() && libFiles.isDirectory()){
            urls = new URL[libFiles.listFiles().length + arraySize];
            for (int i=0; i < libFiles.listFiles().length; i++){
                urls[i] = new URL("jar:file:" + separator + libFiles.listFiles()[i].toString().replace('\\','/') + "!/");  
            }
        } else {
            urls = new URL[arraySize];
        }
         
        urls[urls.length -1] = classesURL;
        urls[urls.length -2] = appRoot;
        ClassLoader urlClassloader = new URLClassLoader(urls,
                Thread.currentThread().getContextClassLoader());
        Thread.currentThread().setContextClassLoader(urlClassloader);
        return path;
    }

    
    /**
     * Start a SelectorThread.
     * @param st
     */
    public void startSelectorThread(final SelectorThread st) throws IOException,
            InstantiationException {
        st.setDisplayConfiguration(true);        
        st.listen();
    }

    
    /**
     * Set the port the {@link SelectorThread} will listen.
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
     * to the {@link #createSelectorThread}.
     */
    public abstract void printHelpAndExit();

    
    /**
     * Validate the command line options.
     * @param args the command line arguments.
     * @return true if the options are well formed.
     */
    public abstract boolean parseOptions(String[] args);

            
    /**
     * Configure the {@link SelectorThread#setAdapter}
     * @param st
     * #return an instance of an Adapter.
     */
    public abstract Adapter configureAdapter(SelectorThread st);

    
    /**
     * Parse the current command line, and return the location of the 
     * war/jar/static resource location
     * file passed as argument.
     * @param args the command line arguments.
     * @return the application path, or null if not defined.
     */
    public abstract String parseApplicationLocation(String[] args);
    
}
