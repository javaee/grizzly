/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.util;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

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
}
