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
package com.sun.grizzly.util;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Level;

/**
 * Simple Classloader utility.
 *
 * @author Jeanfrancois Arcand
 */
public class ClassLoaderUtil {
    
    /**
     * Create a class loader that can load classes from the specified
     * file directory. The file directory must contains .jar ot .zip
     *
     * @param file a directory
     * @param ClassLoader the parent classloader, or null if none.
     * @return A Classloader that can load classes from a directory that
     *         contains jar and zip files.
     */
    public final static ClassLoader createClassloader(File libDir, ClassLoader cl)
            throws IOException{
        URLClassLoader urlClassloader = null;
        if (libDir.exists()){
            if (libDir.isDirectory()){
                String[] jars = libDir.list(new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        if (name.endsWith(".jar") || name.endsWith(".zip")){
                            return true;
                        } else {
                            return false;
                        }
                        
                    }
                });
                URL[] urls = new URL[jars.length];
                for (int i=0; i < jars.length; i++){
                    String path = new File(libDir.getName() 
                        + File.separator + jars[i]).getCanonicalFile().toURL()
                            .toString();
                    urls[i] = new URL(path);
                }
                urlClassloader = new URLClassLoader(urls,cl);
            }
        }
        return urlClassloader;  
    }
    
    /**
     * Construct a {@link URLClassloader} based on a canonial file location.
     * @param dirPath a canonial path location
     * @return a {@link URLClassLoader}
     */
    public static URLClassLoader createURLClassLoader(String dirPath) throws MalformedURLException
            , IOException{
        
        String path;
        File file = null;
        URL appRoot = null;
        URL classesURL = null;
        
        if (!dirPath.endsWith(File.separator)){
            dirPath += File.separator;
        }
        
        if (dirPath != null && 
                (dirPath.endsWith(".war") || dirPath.endsWith(".jar"))) {
            file = new File(dirPath);
            appRoot = new URL("jar:file:" +
                    file.getCanonicalPath() + "!/");
            classesURL = new URL("jar:file:" +
                    file.getCanonicalPath() + "!/WEB-INF/classes/");
            path = ExpandJar.expand(appRoot);
        } else {
            path = dirPath;
            classesURL = new URL("file://" + path + "WEB-INF/classes/");
            appRoot = new URL("file://" + path);
        }

	String absolutePath =  new File(path).getAbsolutePath();
        URL[] urls = null;        
        File libFiles = new File(absolutePath + File.separator + "WEB-INF"+ File.separator + "lib");
        int arraySize = (appRoot == null ? 1:2);

        //Must be a better way because that sucks!
        String separator = (System.getProperty("os.name")
                .toLowerCase().startsWith("win")? "/" : "//");

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
        URLClassLoader urlClassloader = new URLClassLoader(urls,
                Thread.currentThread().getContextClassLoader());
        return urlClassloader;
    }
        
    /**
     * Load a class using the current {link Thread#getContextClassLoader}
     * @param clazzName The name of the class you want to load.
     * @return an instance of clazzname
     */
    public static Object load(String clazzName) {    
        return load(clazzName,Thread.currentThread().getContextClassLoader());
    }
 
    /**
     * Load a class using the provided {@link Classloader}
     * @param clazzName The name of the class you want to load.
     * @param classLoader A classloader to use for loading a class.
     * @return an instance of clazzname 
     */
    public static Object load(String clazzName, ClassLoader classLoader) {
        Class className = null;
        try {
            className = Class.forName(clazzName, true, classLoader);
            return className.newInstance();
        } catch (Throwable t) {
            LoggerUtils.getLogger().log(Level.SEVERE,"Unable to load class " 
                    + clazzName,t);
        }

        return null;
    }   
}
